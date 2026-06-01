// ══════════════════════════════════════════════════════════════════════════
// SmartIntern AI — api.js
// Client HTTP centralisé — toutes les fonctions d'appel backend
// Base URL : http://localhost:8081/api
// ══════════════════════════════════════════════════════════════════════════

const BASE_URL = 'http://localhost:8081/api';

// ── Helpers ────────────────────────────────────────────────────────────────

function authHeaders() {
  const token = sessionStorage.getItem('smartintern_token');
  return {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + token
  };
}

async function handleResponse(res) {
  if (res.status === 204) return null;

  const text = await res.text();
  if (!text || text.trim() === '') {
    if (!res.ok) throw new Error(`Erreur ${res.status}`);
    return null;
  }

  let data = null;
  try { data = JSON.parse(text); } catch (e) {
    if (!res.ok) throw new Error(text);
    return text;
  }

  if (!res.ok) throw new Error(data?.message || text || `Erreur ${res.status}`);
  return data;
}

function closeModal(id) {
  const el = document.getElementById(id);
  if (el) el.classList.remove('open');
}

function closeModalOutside(e, modalId) {
  if (e.target.id === modalId) closeModal(modalId);
}

// ── AUTH ───────────────────────────────────────────────────────────────────

async function apiLogin(email, password) {
  const res = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  const data = await handleResponse(res);
  // ✅ Sauvegarde dans smartintern_token (clé utilisée partout)
  if (data?.token) {
    sessionStorage.setItem('smartintern_token', data.token);
  }
  return data;
}

/**
 * Inscription — envoie uniquement les champs connus du backend RegisterRequest.
 * @param {string} firstName
 * @param {string} lastName
 * @param {string} email
 * @param {string} password
 * @param {string} role        ex: "ETUDIANT" | "ENTREPRISE"
 * @param {object} extra       champs optionnels : telephone, filiere, classe, codeEtudiant
 */
async function apiRegister(firstName, lastName, email, password, role, extra = {}) {
  // On n'envoie QUE les champs déclarés dans AuthDto.RegisterRequest
  const allowed = ['telephone', 'filiere', 'classe', 'codeEtudiant',
                   'domaine', 'etablissementId', 'entrepriseId'];
  const filtered = {};
  allowed.forEach(k => { if (extra[k] !== undefined && extra[k] !== '') filtered[k] = extra[k]; });

  const res = await fetch(`${BASE_URL}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ firstName, lastName, email, password, role, ...filtered })
  });
  return handleResponse(res);
}

async function apiVerifyEmail(email, code) {
  const res = await fetch(`${BASE_URL}/auth/verify-otp`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, code })
  });
  return handleResponse(res);
}

async function apiResendCode(email) {
  const res = await fetch(`${BASE_URL}/auth/resend-otp`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });
  return handleResponse(res);
}

// ── UTILISATEUR ────────────────────────────────────────────────────────────

async function apiGetMe() {
  const res = await fetch(`${BASE_URL}/users/me`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiUpdateProfile(data) {
  const res = await fetch(`${BASE_URL}/users/me`, {
    method: 'PUT', headers: authHeaders(), body: JSON.stringify(data)
  });
  return handleResponse(res);
}

async function apiUploadPhoto(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = async () => {
      try {
        const result = await apiUpdateProfile({ photoProfil: reader.result });
        resolve(reader.result);
      } catch (e) { reject(e); }
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

async function apiChangePassword(oldPassword, newPassword) {
  const res = await fetch(`${BASE_URL}/users/change-password`, {
    method: 'PUT', headers: authHeaders(),
    body: JSON.stringify({ oldPassword, newPassword })
  });
  return handleResponse(res);
}

// ── OFFRES ─────────────────────────────────────────────────────────────────

async function apiGetOffres() {
  const res = await fetch(`${BASE_URL}/etudiant/offres`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetOffreById(id) {
  const res = await fetch(`${BASE_URL}/etudiant/offres/${id}`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiRechercherOffres(q) {
  const params = new URLSearchParams();
  if (q) params.append('domaine', q);
  const res = await fetch(`${BASE_URL}/etudiant/offres/search?${params}`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiFiltrerOffres(lieu, type, duree, niveau) {
  const params = new URLSearchParams();
  if (lieu)   params.append('localisation', lieu);
  if (type)   params.append('typeStage',    type);
  if (niveau) params.append('niveauRequis', niveau);
  const res = await fetch(`${BASE_URL}/etudiant/offres/search?${params}`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiMesOffres() {
  const res = await fetch(`${BASE_URL}/entreprise/offres`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiPublierOffre(data) {
  const res = await fetch(`${BASE_URL}/entreprise/offres`, {
    method: 'POST', headers: authHeaders(), body: JSON.stringify(data)
  });
  return handleResponse(res);
}

async function apiModifierOffre(id, data) {
  const res = await fetch(`${BASE_URL}/entreprise/offres/${id}`, {
    method: 'PUT', headers: authHeaders(), body: JSON.stringify(data)
  });
  return handleResponse(res);
}

async function apiCloturerOffre(id) {
  const res = await fetch(`${BASE_URL}/entreprise/offres/${id}/fermer`, {
    method: 'PATCH', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiSupprimerOffre(id) {
  const res = await fetch(`${BASE_URL}/entreprise/offres/${id}`, {
    method: 'DELETE', headers: authHeaders()
  });
  return handleResponse(res);
}

// ── CV ─────────────────────────────────────────────────────────────────────

/**
 * Upload d'un CV — multipart/form-data.
 * Backend : POST /api/etudiant/cv
 */
async function apiUploadCv(file) {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(`${BASE_URL}/etudiant/cv`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${sessionStorage.getItem('smartintern_token')}` },
    body: formData
  });
  return handleResponse(res);
}

/**
 * Récupère les informations et le résultat d'analyse du CV de l'étudiant connecté.
 * Backend : GET /api/etudiant/cv
 * @returns {{ fileName, statut, extractedData, ... }}
 */
async function apiGetCvInfo() {
  const res = await fetch(`${BASE_URL}/etudiant/cv`, { headers: authHeaders() });
  return handleResponse(res);
}

/**
 * Récupère le statut de traitement du CV (polling après upload).
 * Backend : GET /api/etudiant/cv/statut
 * @returns {{ statut: 'EN_ATTENTE'|'EN_COURS'|'TRAITE'|'ERREUR', message }}
 */
async function apiGetCvStatut() {
  const res = await fetch(`${BASE_URL}/etudiant/cv/statut`, { headers: authHeaders() });
  return handleResponse(res);
}

/**
 * Téléchargement CV — fonctionnalité non implémentée côté backend.
 * Utilisez apiTelechargerDocument(uuid) pour télécharger un document généré.
 * @deprecated
 */
async function apiDownloadCv() {
  throw new Error('Téléchargement direct du CV non disponible. Utilisez la génération de document.');
}

/**
 * Suppression CV — fonctionnalité non implémentée côté backend.
 * @deprecated
 */
async function apiDeleteCv() {
  throw new Error('Suppression du CV non disponible via l\'API actuelle.');
}

// ── CANDIDATURES ───────────────────────────────────────────────────────────

async function apiPostuler(offreId, lettreMotivation = '') {
  const res = await fetch(`${BASE_URL}/etudiant/candidatures`, {
    method: 'POST', headers: authHeaders(),
    body: JSON.stringify({ offreId, lettreMotivation })
  });
  return handleResponse(res);
}

async function apiGetCandidatures() {
  const res = await fetch(`${BASE_URL}/etudiant/candidatures`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetCandidaturesParOffre(offreId) {
  const res = await fetch(`${BASE_URL}/entreprise/offres/${offreId}/candidatures`, { headers: authHeaders() });
  return handleResponse(res);
}

// Compatibilité: récupère toutes les candidatures reçues pour toutes les offres de l'entreprise
async function apiGetCandidaturesRecues() {
  const offres = await apiMesOffres();
  if (!offres || !offres.length) return [];
  const promises = offres.map(o => apiGetCandidaturesParOffre(o.id).catch(() => []));
  const results = await Promise.all(promises);
  return results.flat();
}

async function apiDeciderCandidature(candidatureId, statut, commentaire = '') {
  const res = await fetch(`${BASE_URL}/entreprise/candidatures/${candidatureId}/decision`, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify({ statut, commentaire })
  });
  return handleResponse(res);
}

// Alias pour compatibilité avec les anciens appels
async function apiChangerStatutCandidature(candidatureId, data) {
  return apiDeciderCandidature(candidatureId, data.statut, data.commentaire);
}

async function apiRepondreCandidature(id, action, commentaire = '') {
  const statut = action === 'accepter' ? 'ACCEPTEE' : 'REFUSEE';
  return apiDeciderCandidature(id, statut, commentaire);
}

// ── STAGES ─────────────────────────────────────────────────────────────────

async function apiGetStage() {
  const res = await fetch(`${BASE_URL}/etudiant/stages`, { headers: authHeaders() });
  return handleResponse(res);
}

// Alias pour obtenir le premier stage actif de l'étudiant
async function apiGetMonStage() {
  const stages = await apiGetStage();
  return Array.isArray(stages) ? (stages[0] || null) : stages;
}

// Pour encadrant entreprise : ses stagiaires assignés
async function apiGetStagiaires() {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/stages`, { headers: authHeaders() });
  return handleResponse(res);
}

// Pour entreprise : tous ses stages (via ses offres)
async function apiGetStagesEntreprise() {
  const res = await fetch(`${BASE_URL}/entreprise/stages`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetConventions() {
  const res = await fetch(`${BASE_URL}/entreprise/stages`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiSignerConventionEntreprise(stageId) {
  const res = await fetch(`${BASE_URL}/entreprise/stages/${stageId}/signer-convention`, {
    method: 'PATCH', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiSignerConventionEncadrant(stageId) {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/stages/${stageId}/signer-convention`, {
    method: 'PATCH', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiDefinirMission(stageId, mission) {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/stages/${stageId}/mission`, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify({ mission })
  });
  return handleResponse(res);
}

async function apiGetStagesEncadrantEntreprise() {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/stages`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetStagesEncadrantAcademique() {
  const res = await fetch(`${BASE_URL}/encadrant-academique/stages`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetStagesSoutenance() {
  const res = await fetch(`${BASE_URL}/encadrant-academique/stages`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiAutoriserSoutenance(stageId, dateSoutenance, commentaire) {
  const res = await fetch(`${BASE_URL}/encadrant-academique/stages/${stageId}/autoriser-soutenance`, {
    method: 'PATCH', headers: authHeaders(),
    body: JSON.stringify({ dateSoutenance, commentaire })
  });
  return handleResponse(res);
}

async function apiAffecterEncadrantEntreprise(stageId, encadrantId) {
  const res = await fetch(`${BASE_URL}/admin/stages/${stageId}/encadrants`, {
    method: 'PATCH', headers: authHeaders(),
    body: JSON.stringify({ encadrantEntrepriseId: encadrantId })
  });
  return handleResponse(res);
}

async function apiAffecterEncadrantAcademique(stageId, encadrantId) {
  const res = await fetch(`${BASE_URL}/admin/stages/${stageId}/encadrants`, {
    method: 'PATCH', headers: authHeaders(),
    body: JSON.stringify({ encadrantAcademiqueId: encadrantId })
  });
  return handleResponse(res);
}

// ── MISSIONS ───────────────────────────────────────────────────────────────

async function apiCreerMission(stageId, titre, description, objectifs, dateEcheance) {
  const res = await fetch(`${BASE_URL}/missions/stage/${stageId}`, {
    method: 'POST', headers: authHeaders(),
    body: JSON.stringify({ titre, description, objectifs, dateEcheance })
  });
  return handleResponse(res);
}

async function apiGetMissionsByStage(stageId) {
  const res = await fetch(`${BASE_URL}/missions/stage/${stageId}`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetMissionsEncadrant() {
  const res = await fetch(`${BASE_URL}/missions/encadrant/mes-missions`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetMesMissions() {
  const res = await fetch(`${BASE_URL}/missions/etudiant/mes-missions`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiDemarrerMission(missionId) {
  const res = await fetch(`${BASE_URL}/missions/${missionId}/demarrer`, {
    method: 'PUT', headers: authHeaders()
  });
  return handleResponse(res);
}

// ── COMPTES RENDUS ─────────────────────────────────────────────────────────

async function apiSoumettreCompteRendu(missionId, contenu, commentaire) {
  const res = await fetch(`${BASE_URL}/missions/${missionId}/compte-rendu`, {
    method: 'POST', headers: authHeaders(),
    body: JSON.stringify({ contenu, commentaire: commentaire || '' })
  });
  return handleResponse(res);
}

async function apiValiderCompteRendu(compteRenduId, commentaire) {
  const res = await fetch(`${BASE_URL}/missions/compte-rendu/${compteRenduId}/valider`, {
    method: 'PUT', headers: authHeaders(),
    body: JSON.stringify({ commentaire })
  });
  return handleResponse(res);
}

async function apiRejeterCompteRendu(compteRenduId, commentaire) {
  const res = await fetch(`${BASE_URL}/missions/compte-rendu/${compteRenduId}/rejeter`, {
    method: 'PUT', headers: authHeaders(),
    body: JSON.stringify({ commentaire })
  });
  return handleResponse(res);
}

async function apiGetMesComptesRendus() {
  const res = await fetch(`${BASE_URL}/missions/etudiant/mes-comptes-rendus`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetComptesRendus(missionId) {
  // ✅ Correction : ajout du préfixe /missions/
  const res = await fetch(`${BASE_URL}/missions/${missionId}/comptes-rendus`, { headers: authHeaders() });
  return handleResponse(res);
}

// ── RAPPORTS ───────────────────────────────────────────────────────────────

async function apiDeposerRapport(stageId, type, semaine, titre, contenu, fichiers = []) {
  const formData = new FormData();
  formData.append('stageId',  stageId);
  formData.append('type',     type);
  formData.append('semaine',  semaine);
  formData.append('titre',    titre);
  formData.append('contenu',  contenu);
  fichiers.forEach(f => formData.append('fichiers', f));
  const res = await fetch(`${BASE_URL}/etudiant/stages/${stageId}/rapports`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${sessionStorage.getItem('smartintern_token')}` },
    body: formData
  });
  return handleResponse(res);
}

async function apiGetRapports() {
  const res = await fetch(`${BASE_URL}/rapports/mes-rapports`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetRapportsRecus() {
  const res = await fetch(`${BASE_URL}/encadrant-academique/stages/rapports`, { headers: authHeaders() });
  return handleResponse(res).catch(() => []);
}

async function apiGetRapportsEncadrantAcademique() {
  const res = await fetch(`${BASE_URL}/encadrant-academique/stages/rapports`, { headers: authHeaders() });
  return handleResponse(res).catch(() => []);
}

async function apiGetRapportsAValider() {
  const res = await fetch(`${BASE_URL}/encadrant-academique/stages/rapports`, { headers: authHeaders() });
  return handleResponse(res).catch(() => []);
}

async function apiSoumettreRapport(rapportId) {
  const res = await fetch(`${BASE_URL}/etudiant/rapports/${rapportId}/soumettre`, {
    method: 'PATCH', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiValiderRapport(id, commentaire = '') {
  const res = await fetch(`${BASE_URL}/encadrant-academique/rapports/${id}/valider`, {
    method: 'PATCH', headers: authHeaders(), body: JSON.stringify({ commentaire })
  });
  return handleResponse(res);
}

async function apiCommenterRapport(id, commentaire) {
  const res = await fetch(`${BASE_URL}/encadrant-academique/rapports/${id}/commenter`, {
    method: 'PATCH', headers: authHeaders(), body: JSON.stringify({ commentaire })
  });
  return handleResponse(res);
}

async function apiRejeterRapport(id, commentaire) {
  return apiCommenterRapport(id, commentaire);
}

// ── NOTIFICATIONS ──────────────────────────────────────────────────────────

async function apiGetNotifications() {
  try {
    const res = await fetch(`${BASE_URL}/notifications`, { headers: authHeaders() });
    return await handleResponse(res);
  } catch (e) { return []; }
}

async function apiMarkAllNotificationsRead() {
  const res = await fetch(`${BASE_URL}/notifications/lire-tout`, {
    method: 'PATCH', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiMarquerNotificationLue(id) {
  const res = await fetch(`${BASE_URL}/notifications/${id}/lire`, {
    method: 'PATCH', headers: authHeaders()
  });
  return handleResponse(res);
}

// ── ADMINISTRATION ─────────────────────────────────────────────────────────

async function apiGetAdminStats() {
  const res = await fetch(`${BASE_URL}/admin/stats`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetStats() {
  const res = await fetch(`${BASE_URL}/stats`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetAllUsers() {
  const res = await fetch(`${BASE_URL}/admin/users`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetUsersByRole(role) {
  const res = await fetch(`${BASE_URL}/admin/users/role/${role}`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiValidateUser(userId) {
  const res = await fetch(`${BASE_URL}/admin/users/${userId}/statut`, {
    method: 'PATCH', headers: authHeaders(), body: JSON.stringify({ statut: 'ACTIF' })
  });
  return handleResponse(res);
}

async function apiDisableUser(userId) {
  const res = await fetch(`${BASE_URL}/admin/users/${userId}/statut`, {
    method: 'PATCH', headers: authHeaders(), body: JSON.stringify({ statut: 'SUSPENDU' })
  });
  return handleResponse(res);
}

async function apiEnableUser(userId) {
  const res = await fetch(`${BASE_URL}/admin/users/${userId}/statut`, {
    method: 'PATCH', headers: authHeaders(), body: JSON.stringify({ statut: 'ACTIF' })
  });
  return handleResponse(res);
}

async function apiGetAllStages() {
  const res = await fetch(`${BASE_URL}/admin/stages`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetStageDetails(stageId) {
  const res = await fetch(`${BASE_URL}/admin/stages/${stageId}`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiValidateStage(stageId) {
  const res = await fetch(`${BASE_URL}/admin/stages/${stageId}/validate`, {
    method: 'PUT', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiGetEtudiantsARisque() {
  const res = await fetch(`${BASE_URL}/admin/risques/etudiants-a-risque`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiAnalyserTousStages() {
  const res = await fetch(`${BASE_URL}/admin/risques/analyser-tous`, {
    method: 'POST', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiAnalyserStage(stageId) {
  const res = await fetch(`${BASE_URL}/admin/risques/analyser/${stageId}`, {
    method: 'POST', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiGetRisqueHistorique(stageId) {
  const res = await fetch(`${BASE_URL}/admin/risques/stage/${stageId}/historique`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiGetAllEtudiants() {
  const res = await fetch(`${BASE_URL}/admin/etudiants`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetAllEntreprises() {
  const res = await fetch(`${BASE_URL}/admin/entreprises`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiValidateEntreprise(entrepriseId) {
  const res = await fetch(`${BASE_URL}/admin/entreprises/${entrepriseId}/validate`, {
    method: 'PUT', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiAdminCreerEncadrant(data) {
  const res = await fetch(`${BASE_URL}/admin/encadrants`, {
    method: 'POST', headers: authHeaders(), body: JSON.stringify(data)
  });
  return handleResponse(res);
}

// ── ENCADRANTS ─────────────────────────────────────────────────────────────

async function apiGetEncadrantsAcademiques() {
  const res = await fetch(`${BASE_URL}/admin/users/role/ENCADRANT_ACADEMIQUE`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetEncadrantsEntreprise() {
  const res = await fetch(`${BASE_URL}/entreprise/encadrants`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiEntrepriseCreerEncadrant(data) {
  const res = await fetch(`${BASE_URL}/encadrants/creer`, {
    method: 'POST', headers: authHeaders(), body: JSON.stringify(data)
  });
  return handleResponse(res);
}

// ── DOCUMENTS ──────────────────────────────────────────────────────────────

/**
 * Génère un document à partir d'un modèle (convention, attestation, rapport…).
 * Backend : POST /api/etudiant/documents/generer
 *
 * @param {number} typeDocumentId        - ID du type de document (Long)
 * @param {Object} [donneesSupp={}]      - Données supplémentaires { clé: valeur } pour les variables du template
 * @param {string} [outputFormat='docx'] - Format de sortie : 'docx' ou 'pdf'
 * @returns {Promise<DocumentGenerationResponse>} - { success, docUuid, urlTelechargement, urlAuthentification, typeDocument, format, statut, dateCreation, dateExpiration, tailleOctets, message }
 */
async function apiGenererDocument(typeDocumentId, donneesSupp = {}, outputFormat = 'docx') {
  const res = await fetch(`${BASE_URL}/etudiant/documents/generer`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ typeDocumentId, donneesSupplementaires: donneesSupp, outputFormat })
  });
  return handleResponse(res);
}

/**
 * Télécharge un document généré sous forme de Blob (DOCX ou PDF).
 * Backend : GET /api/documents/{uuid}/telecharger
 *
 * @param {string} uuid - UUID du document (obtenu depuis apiGenererDocument().docUuid)
 * @returns {Promise<Blob>}
 */
async function apiTelechargerDocument(uuid) {
  const res = await fetch(`${BASE_URL}/documents/${uuid}/telecharger`, {
    headers: { 'Authorization': `Bearer ${sessionStorage.getItem('smartintern_token')}` }
  });
  if (!res.ok) throw new Error(`Erreur téléchargement document ${uuid} : HTTP ${res.status}`);
  return res.blob();
}

/**
 * Récupère l'historique des documents générés par l'étudiant connecté.
 * Backend : GET /api/etudiant/documents
 * @returns {Promise<DocumentGenere[]>}
 */
async function apiGetMesDocuments() {
  const res = await fetch(`${BASE_URL}/etudiant/documents`, { headers: authHeaders() });
  return handleResponse(res);
}

/**
 * Vérifie l'authenticité d'un document (endpoint public, sans JWT).
 * Backend : GET /api/documents/{uuid}/authentifier
 *
 * @param {string} uuid - UUID du document
 * @returns {Promise<DocumentAuthentificationDto>}
 */
async function apiAuthentifierDocument(uuid) {
  const res = await fetch(`${BASE_URL}/documents/${uuid}/authentifier`);
  return handleResponse(res);
}

// ── MESSAGERIE — Contacts, fichiers, export ────────────────────────────────

/**
 * Retourne la liste de tous les utilisateurs actifs disponibles pour la messagerie.
 * Backend : GET /api/messagerie/utilisateurs
 * @returns {Promise<{id, prenom, nom, email, role}[]>}
 */
async function apiGetContactsMessagerie() {
  const res = await fetch(`${BASE_URL}/messagerie/utilisateurs`, { headers: authHeaders() });
  return handleResponse(res);
}

/**
 * Envoie une pièce jointe (image ou document) dans une conversation.
 * Backend : POST /api/messagerie/conversations/{id}/fichiers
 * @param {number} conversationId
 * @param {File}   fichier        - objet File sélectionné par l'utilisateur
 * @returns {Promise<MessageDto>}
 */
async function apiEnvoyerFichierMessage(conversationId, fichier) {
  const formData = new FormData();
  formData.append('fichier', fichier);
  const res = await fetch(`${BASE_URL}/messagerie/conversations/${conversationId}/fichiers`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${sessionStorage.getItem('smartintern_token')}` },
    body: formData
  });
  return handleResponse(res);
}

/**
 * Exporte l'historique d'une conversation sous forme de fichier texte.
 * Backend : GET /api/messagerie/conversations/{id}/export
 * @param {number} conversationId
 * @returns {Promise<Blob>} — blob text/plain à télécharger
 */
async function apiExporterHistoriqueConversation(conversationId) {
  const res = await fetch(
    `${BASE_URL}/messagerie/conversations/${conversationId}/export`,
    { headers: { 'Authorization': `Bearer ${sessionStorage.getItem('smartintern_token')}` } }
  );
  if (!res.ok) throw new Error(`Erreur export historique : HTTP ${res.status}`);
  return res.blob();
}

// ── CAHIER DES CHARGES (CDC) ───────────────────────────────────────────────

async function apiUploadCahierDesCharges(stageId, file, titre) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('titre', titre);
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/cahier-des-charges/stage/${stageId}`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${sessionStorage.getItem('smartintern_token')}` },
    body: formData
  });
  return handleResponse(res);
}

async function apiValiderCahierDesCharges(id) {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/cahier-des-charges/${id}/valider`, {
    method: 'POST', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiGetCahierDesChargesEncadrant(stageId) {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/cahier-des-charges/stage/${stageId}`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiGetCahierDesChargesEtudiant(stageId) {
  const res = await fetch(`${BASE_URL}/etudiant/cahier-des-charges/stage/${stageId}`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

// ── SPRINTS ────────────────────────────────────────────────────────────────

async function apiCreerSprint(stageId, data) {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/sprints/stage/${stageId}`, {
    method: 'POST', headers: authHeaders(), body: JSON.stringify(data)
  });
  return handleResponse(res);
}

async function apiModifierSprint(id, data) {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/sprints/${id}`, {
    method: 'PUT', headers: authHeaders(), body: JSON.stringify(data)
  });
  return handleResponse(res);
}

async function apiSupprimerSprint(id) {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/sprints/${id}`, {
    method: 'DELETE', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiCloturerSprint(id, observation = '') {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/sprints/${id}/cloturer`, {
    method: 'POST', headers: authHeaders(), body: JSON.stringify({ observation })
  });
  return handleResponse(res);
}

async function apiGetSprintsEncadrant(stageId) {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/sprints/stage/${stageId}`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiGetSprintsEtudiant(stageId) {
  const res = await fetch(`${BASE_URL}/etudiant/sprints/stage/${stageId}`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

// ── TÂCHES ─────────────────────────────────────────────────────────────────

async function apiCreerTache(sprintId, data) {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/taches/sprint/${sprintId}`, {
    method: 'POST', headers: authHeaders(), body: JSON.stringify(data)
  });
  return handleResponse(res);
}

async function apiModifierTache(id, data) {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/taches/${id}`, {
    method: 'PUT', headers: authHeaders(), body: JSON.stringify(data)
  });
  return handleResponse(res);
}

async function apiSupprimerTache(id) {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/taches/${id}`, {
    method: 'DELETE', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiValiderTache(id, observation = '', note = null) {
  const body = { observation };
  if (note !== null) body.note = note;
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/taches/${id}/valider`, {
    method: 'POST', headers: authHeaders(), body: JSON.stringify(body)
  });
  return handleResponse(res);
}

async function apiRefuserTache(id, observation) {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/taches/${id}/refuser`, {
    method: 'POST', headers: authHeaders(), body: JSON.stringify({ observation })
  });
  return handleResponse(res);
}

async function apiGetTachesEncadrant(sprintId) {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/taches/sprint/${sprintId}`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiDemarrerTache(id) {
  const res = await fetch(`${BASE_URL}/etudiant/taches/${id}/demarrer`, {
    method: 'POST', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiTerminerTache(id, noteEtudiant = '') {
  const res = await fetch(`${BASE_URL}/etudiant/taches/${id}/terminer`, {
    method: 'POST', headers: authHeaders(),
    body: JSON.stringify({ noteEtudiant })
  });
  return handleResponse(res);
}

async function apiReprendreTache(id) {
  const res = await fetch(`${BASE_URL}/etudiant/taches/${id}/reprendre`, {
    method: 'POST', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiGetTachesEtudiant(sprintId) {
  const res = await fetch(`${BASE_URL}/etudiant/taches/sprint/${sprintId}`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

// ── IA — MATCHING & RECOMMANDATION (v7) ────────────────────────────────────

/**
 * Recommande des offres personnalisées à un étudiant via le moteur IA.
 * @param {number} etudiantId  - ID de l'étudiant
 * @param {number} [limit=10]  - nombre max d'offres retournées
 * @returns {{ success, etudiantId, total, offres: OffreRecommandee[] }}
 */
async function apiRecommandationOffres(etudiantId, limit = 10) {
  const res = await fetch(
    `${BASE_URL}/etudiant/recommandations/offres/${etudiantId}?limit=${limit}`,
    { headers: authHeaders() }
  );
  return handleResponse(res);
}

/**
 * Lance le matching IA pour une offre et retourne le classement des candidats.
 * @param {number} offreId - ID de l'offre de stage
 * @returns {{ success, offreId, total, candidats: CandidatScore[] }}
 */
async function apiMatchingCandidats(offreId) {
  const res = await fetch(
    `${BASE_URL}/entreprise/matching/offre/${offreId}`,
    { headers: authHeaders() }
  );
  return handleResponse(res);
}

// ── MODALE RAPPORT (helpers UI) ────────────────────────────────────────────

function openRapportModal() {
  const m = document.getElementById('modal-rapport');
  if (!m) return;
  m.classList.add('open');
  const err = document.getElementById('rapport-error');
  if (err) err.style.display = 'none';
  ['rapport-titre', 'rapport-contenu'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.value = '';
  });
}

async function confirmerRapport() {
  const btn = document.getElementById('btn-rapport-confirm');
  const err = document.getElementById('rapport-error');
  const success = document.getElementById('rapport-success');

  const titre   = document.getElementById('rapport-titre')?.value.trim();
  const contenu = document.getElementById('rapport-contenu')?.value.trim();
  const type    = document.getElementById('rapport-type')?.value;
  const semaine = document.getElementById('rapport-semaine')?.value;
  const fichiers = document.getElementById('rapport-fichiers')?.files;

  if (!titre || !contenu) {
    if (err) { err.textContent = 'Le titre et le contenu sont obligatoires.'; err.style.display = 'block'; }
    return;
  }

  if (btn) { btn.disabled = true; btn.textContent = 'Envoi...'; }
  if (err) err.style.display = 'none';
  if (success) success.style.display = 'none';

  try {
    const data = await apiDeposerRapport(
      window.currentStageId, type, semaine, titre, contenu,
      fichiers ? Array.from(fichiers) : []
    );
    if (success) { success.textContent = data?.message || '✅ Rapport déposé avec succès !'; success.style.display = 'block'; }
    setTimeout(() => {
      closeModal('modal-rapport');
      if (typeof chargerRapports === 'function') chargerRapports();
    }, 1000);
  } catch (e) {
    if (err) { err.textContent = e.message; err.style.display = 'block'; }
  } finally {
    if (btn) { btn.disabled = false; btn.innerHTML = 'Déposer le rapport'; }
  }
}
