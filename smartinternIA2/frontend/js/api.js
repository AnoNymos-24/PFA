const BASE_URL = 'http://localhost:8081/api';

function authHeaders() {
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${getToken()}`
  };
}

async function handleResponse(res) {
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || `Erreur ${res.status}`);
  return data;
}

// ── AUTH ──────────────────────────────────────────────────────────────────────

async function apiLogin(email, password) {
  const res = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  return handleResponse(res);
}

// firstName = prénom, lastName = nom (mapping backend)
async function apiRegister(firstName, lastName, email, password, role, extra = {}) {
  const res = await fetch(`${BASE_URL}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ firstName, lastName, email, password, role, ...extra })
  });
  return handleResponse(res);
}

async function apiVerifyOtp(email, code) {
  const res = await fetch(`${BASE_URL}/auth/verify-otp`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, code })
  });
  return handleResponse(res);
}

// Alias pour compatibilité
const apiVerifyEmail = apiVerifyOtp;

async function apiResendOtp(email) {
  const res = await fetch(`${BASE_URL}/auth/resend-otp`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });
  return handleResponse(res);
}

// Alias pour compatibilité
const apiResendCode = apiResendOtp;

async function apiForgotPassword(email) {
  const res = await fetch(`${BASE_URL}/auth/forgot-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });
  return handleResponse(res);
}

async function apiResetPassword(email, code, newPassword) {
  const res = await fetch(`${BASE_URL}/auth/reset-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, code, newPassword })
  });
  return handleResponse(res);
}

async function apiChangePassword(oldPassword, newPassword) {
  const res = await fetch(`${BASE_URL}/users/change-password`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ oldPassword, newPassword })
  });
  return handleResponse(res);
}

// ── UTILISATEUR ───────────────────────────────────────────────────────────────

async function apiGetMe() {
  const res = await fetch(`${BASE_URL}/users/me`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiUpdateProfile(data) {
  const res = await fetch(`${BASE_URL}/users/me`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data)
  });
  return handleResponse(res);
}

const apiUpdateEntrepriseProfile = apiUpdateProfile;

// ── OFFRES (étudiant) ─────────────────────────────────────────────────────────

async function apiGetOffres({ page = 0, size = 20, sortBy = 'datePublication', sortDir = 'desc' } = {}) {
  const params = new URLSearchParams({ page, size, sortBy, sortDir });
  const res = await fetch(`${BASE_URL}/etudiant/offres?${params}`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiRechercherOffres({ domaine, localisation, typeStage, niveauRequis } = {}) {
  const params = new URLSearchParams();
  if (domaine)      params.append('domaine', domaine);
  if (localisation) params.append('localisation', localisation);
  if (typeStage)    params.append('typeStage', typeStage);
  if (niveauRequis) params.append('niveauRequis', niveauRequis);
  const res = await fetch(`${BASE_URL}/etudiant/offres/search?${params}`, { headers: authHeaders() });
  return handleResponse(res);
}

// Alias pour les anciens appels simples
async function apiFiltrerOffres(domaine, localisation, typeStage, niveauRequis) {
  return apiRechercherOffres({ domaine, localisation, typeStage, niveauRequis });
}

// ── OFFRES (entreprise) ───────────────────────────────────────────────────────

async function apiMesOffres() {
  const res = await fetch(`${BASE_URL}/entreprise/offres`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiPublierOffre(data) {
  const res = await fetch(`${BASE_URL}/entreprise/offres`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data)
  });
  return handleResponse(res);
}

async function apiModifierOffre(id, data) {
  const res = await fetch(`${BASE_URL}/entreprise/offres/${id}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data)
  });
  return handleResponse(res);
}

async function apiSupprimerOffre(id) {
  const res = await fetch(`${BASE_URL}/entreprise/offres/${id}`, {
    method: 'DELETE',
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Clôture = mise à jour du statut via PUT
async function apiCloturerOffre(id) {
  return apiModifierOffre(id, { statut: 'CLOTUREE' });
}

// ── OFFRES (admin) ────────────────────────────────────────────────────────────

async function apiGetOffresEnAttente() {
  const res = await fetch(`${BASE_URL}/admin/offres/en-attente`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiValiderOffre(id, approuve) {
  const res = await fetch(`${BASE_URL}/admin/offres/${id}/valider`, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify({ approuve })
  });
  return handleResponse(res);
}

// ── CV (étudiant) ─────────────────────────────────────────────────────────────

async function apiUploadCv(file) {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(`${BASE_URL}/etudiant/cv`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${getToken()}` },
    body: formData
  });
  return handleResponse(res);
}

async function apiGetCvInfo() {
  const res = await fetch(`${BASE_URL}/etudiant/cv`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetCvStatut() {
  const res = await fetch(`${BASE_URL}/etudiant/cv/statut`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiReanalyseCv() {
  const res = await fetch(`${BASE_URL}/etudiant/cv/reanalyse`, {
    method: 'POST',
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Téléchargement direct (retourne blob)
async function apiDownloadCv() {
  const res = await fetch(`${BASE_URL}/etudiant/cv/download`, {
    headers: { 'Authorization': `Bearer ${getToken()}` }
  });
  if (!res.ok) throw new Error('Erreur téléchargement CV');
  return res.blob();
}

// ── CANDIDATURES ──────────────────────────────────────────────────────────────

async function apiPostuler(offreId, lettreMotivation = '') {
  const res = await fetch(`${BASE_URL}/etudiant/candidatures`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ offreId, lettreMotivation })
  });
  return handleResponse(res);
}

async function apiGetCandidatures() {
  const res = await fetch(`${BASE_URL}/etudiant/candidatures`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetCandidaturesParOffre(offreId) {
  const res = await fetch(`${BASE_URL}/entreprise/offres/${offreId}/candidatures`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Récupère TOUTES les candidatures pour l'entreprise connectée (agrégation frontend)
async function apiGetCandidaturesRecues() {
  const offres = await apiMesOffres();
  if (!offres || offres.length === 0) return [];
  const all = await Promise.all(
    offres.map(o => apiGetCandidaturesParOffre(o.id).catch(() => []))
  );
  return all.flat();
}

async function apiDeciderCandidature(id, statut, commentaire = '') {
  const res = await fetch(`${BASE_URL}/entreprise/candidatures/${id}/decision`, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify({ statut, commentaire })
  });
  return handleResponse(res);
}

// Alias pour compatibilité (action = statut)
async function apiRepondreCandidature(id, action, commentaire = '') {
  return apiDeciderCandidature(id, action, commentaire);
}

// ── STAGES ────────────────────────────────────────────────────────────────────

async function apiGetStage() {
  const res = await fetch(`${BASE_URL}/etudiant/stages`, { headers: authHeaders() });
  return handleResponse(res);
}

// Entreprise : liste des stagiaires (via stages admin — stub jusqu'à implémentation backend)
async function apiGetStagiaires() {
  const res = await fetch(`${BASE_URL}/admin/stages`, { headers: authHeaders() });
  return handleResponse(res).catch(() => []);
}

async function apiGetStagesEncadrantAcademique() {
  const res = await fetch(`${BASE_URL}/encadrant-academique/stages`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetStagesEncadrantEntreprise() {
  const res = await fetch(`${BASE_URL}/encadrant-entreprise/stages`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiCreerStageDepuisCandidature(candidatureId) {
  const res = await fetch(`${BASE_URL}/admin/stages/depuis-candidature/${candidatureId}`, {
    method: 'POST',
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiAssignerEncadrants(stageId, data) {
  const res = await fetch(`${BASE_URL}/admin/stages/${stageId}/encadrants`, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify(data)
  });
  return handleResponse(res);
}

// ── DOCUMENTS ─────────────────────────────────────────────────────────────────

async function apiGetMesDocuments() {
  const res = await fetch(`${BASE_URL}/etudiant/documents`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGenererDocument(modeleId, donneesProfil = {}, nomEtablissement = 'SmartIntern') {
  const res = await fetch(`${BASE_URL}/documents/generer`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ modeleId, donneesProfil, nomEtablissement })
  });
  return handleResponse(res);
}

async function apiRegenererDocument(uuid, donneesProfil = {}, nomEtablissement = 'SmartIntern') {
  const res = await fetch(`${BASE_URL}/documents/${uuid}/regenerer`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ donneesProfil, nomEtablissement })
  });
  return handleResponse(res);
}

async function apiTelechargerDocument(uuid) {
  const res = await fetch(`${BASE_URL}/documents/${uuid}/telecharger`, {
    headers: { 'Authorization': `Bearer ${getToken()}` }
  });
  if (!res.ok) throw new Error('Erreur téléchargement document');
  return res.blob();
}

async function apiVerifierDocument(uuid) {
  const res = await fetch(`${BASE_URL}/documents/${uuid}/verifier`);
  return handleResponse(res);
}

// ── MODÈLES (admin) ───────────────────────────────────────────────────────────

async function apiGetModeles(statut = null) {
  const url = statut
    ? `${BASE_URL}/admin/modeles?statut=${encodeURIComponent(statut)}`
    : `${BASE_URL}/admin/modeles`;
  const res = await fetch(url, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiGetModele(id) {
  const res = await fetch(`${BASE_URL}/admin/modeles/${id}`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiCreerModele(formData) {
  const res = await fetch(`${BASE_URL}/admin/modeles`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${getToken()}` },
    body: formData
  });
  return handleResponse(res);
}

async function apiUpdateModele(id, data) {
  const res = await fetch(`${BASE_URL}/admin/modeles/${id}`, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify(data)
  });
  return handleResponse(res);
}

async function apiArchiverModele(id) {
  const res = await fetch(`${BASE_URL}/admin/modeles/${id}`, {
    method: 'DELETE',
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiGetTypesDocuments() {
  const res = await fetch(`${BASE_URL}/admin/types-documents`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiCreerTypeDocument(nom, code, description) {
  const res = await fetch(`${BASE_URL}/admin/types-documents`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ nom, code, description })
  });
  return handleResponse(res);
}

// ── ADMINISTRATION ────────────────────────────────────────────────────────────

async function apiGetAdminStats() {
  const res = await fetch(`${BASE_URL}/admin/stats`, { headers: authHeaders() });
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
  const res = await fetch(`${BASE_URL}/admin/users/${userId}/validate`, {
    method: 'PUT',
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiDisableUser(userId) {
  const res = await fetch(`${BASE_URL}/admin/users/${userId}/disable`, {
    method: 'PUT',
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiEnableUser(userId) {
  const res = await fetch(`${BASE_URL}/admin/users/${userId}/enable`, {
    method: 'PUT',
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiGetAllStages() {
  const res = await fetch(`${BASE_URL}/admin/stages`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiValidateStage(stageId) {
  const res = await fetch(`${BASE_URL}/admin/stages/${stageId}/validate`, {
    method: 'PUT',
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

async function apiGetEtudiantsARisque() {
  const res = await fetch(`${BASE_URL}/admin/etudiants/risque`, { headers: authHeaders() });
  return handleResponse(res);
}

async function apiValidateEntreprise(entrepriseId) {
  const res = await fetch(`${BASE_URL}/admin/entreprises/${entrepriseId}/validate`, {
    method: 'PUT',
    headers: authHeaders()
  });
  return handleResponse(res);
}

// ── RAPPORTS (endpoints non encore implémentés côté backend — stubs) ──────────

async function apiDeposerRapport(stageId, type, semaine, titre, contenu) {
  const res = await fetch(`${BASE_URL}/rapports/deposer`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ stageId, type, semaine, titre, contenu })
  });
  return handleResponse(res);
}

async function apiGetRapports() { return []; }
async function apiGetRapportsRecus() { return []; }
async function apiGetRapportsAValider() { return []; }

async function apiValiderRapport(id, commentaire = '') {
  const res = await fetch(`${BASE_URL}/rapports/${id}/valider`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ commentaire })
  });
  return handleResponse(res);
}

async function apiCommenterRapport(id, commentaire) {
  const res = await fetch(`${BASE_URL}/rapports/${id}/commenter`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ commentaire })
  });
  return handleResponse(res);
}

// ── NOTIFICATIONS ─────────────────────────────────────────────────────────────

async function apiGetNotifications() { return []; }
async function apiMarkNotificationsRead() { return {}; }
async function apiMarkAllNotificationsRead() { return {}; }
