const BASE_URL = 'http://localhost:8081/api';
function authHeaders() {
  const token = localStorage.getItem("smartintern_token");
  console.log("TOKEN ENVOYÉ =", token);

  return {
    "Content-Type": "application/json",
    "Authorization": "Bearer " + token
  };
}
function getToken() {
  return localStorage.getItem("smartintern_token");
}
async function apiGetStagiaires() {
  const res = await fetch(`${BASE_URL}/stages/mes-stagiaires`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}
// Dans api.js - corrigez handleResponse
async function handleResponse(res) {
  console.log("📥 handleResponse - Status:", res.status);
  
  // ✅ Ajoutez cette vérification pour 200
  if (res.status === 200) {
    const data = await res.json();
    console.log("✅ handleResponse - Données:", data);
    return data;
  }
  
  const text = await res.text();
  console.log("📥 handleResponse - Texte:", text);
  
  if (!text || text.trim() === '') {
    if (!res.ok) throw new Error(`Erreur ${res.status}`);
    return null;
  }

  let data = null;
  try {
    data = JSON.parse(text);
  } catch (e) {
    if (!res.ok) throw new Error(text);
    return text;
  }

  if (!res.ok) {
    throw new Error(data?.message || text || `Erreur ${res.status}`);
  }

  return data;
}
async function apiLogin(email, password) {
    const res = await fetch(`${BASE_URL}/auth/login`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ email, password })
    });

    const data = await res.json();

    console.log("Réponse login :", data);

    // Sauvegarder le token
    if (data.token) {
        localStorage.setItem("token", data.token);
        console.log("Token sauvegardé ✅");
    } else {
        console.log("Aucun token reçu ❌");
    }

    return data;
}
async function apiRegister(nom, prenom, email, password, role, extra = {}) {
  const res = await fetch(`${BASE_URL}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nom, prenom, email, password, role, ...extra })
  });
  return handleResponse(res);
}

async function apiVerifyEmail(email, code) {
  const res = await fetch(`${BASE_URL}/auth/verify-email`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, code })
  });
  return handleResponse(res);
}

async function apiResendCode(email) {
  const res = await fetch(`${BASE_URL}/auth/resend-code`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
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
// ── OFFRES ────────────────────────────────────────────────────

async function apiGetOffres() {
  const res = await fetch(`${BASE_URL}/offres`);
  return handleResponse(res);
}
async function apiGetOffreById(id) {
  const res = await fetch(`${BASE_URL}/offres/${id}`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiGetOffreDetail(id) {
  try {
    console.log("Appel API pour offre ID:", id);
    const token = getToken();
    
    const response = await fetch(`${BASE_URL}/offres/${id}`, {
      method: 'GET',
      headers: token ? authHeaders() : { 'Content-Type': 'application/json' }
    });
    
    console.log("Réponse status:", response.status);
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    
    const data = await response.json();
    console.log("Données reçues:", data);
    return data;
  } catch(e) {
    console.error("Erreur apiGetOffreDetail:", e);
    throw e;
  }
}

async function apiRechercherOffres(q) {
  const res = await fetch(`${BASE_URL}/offres/recherche?q=${encodeURIComponent(q)}`);
  return handleResponse(res);
}

async function apiFiltrerOffres(lieu, type, duree, niveau) {
  const params = new URLSearchParams();
  if (lieu)   params.append('lieu',   lieu);
  if (type)   params.append('type',   type);
  if (duree)  params.append('duree',  duree);
  if (niveau) params.append('niveau', niveau);
  const res = await fetch(`${BASE_URL}/offres/filtrer?${params}`);
  return handleResponse(res);
}

async function apiMesOffres() {
  const res = await fetch(`${BASE_URL}/offres/mes-offres`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiPublierOffre(data) {
  const res = await fetch(`${BASE_URL}/offres`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data)
  });
  return handleResponse(res);
}

async function apiModifierOffre(id, data) {
  const res = await fetch(`${BASE_URL}/offres/${id}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data)
  });
  return handleResponse(res);
}

async function apiCloturerOffre(id) {
  const res = await fetch(`${BASE_URL}/offres/${id}/cloturer`, {
    method: 'PUT',
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiSupprimerOffre(id) {
  const res = await fetch(`${BASE_URL}/offres/${id}`, {
    method: 'DELETE',
    headers: authHeaders()
  });
  return handleResponse(res);
}
function closeModal(id) {
  document.getElementById(id).classList.remove('open');
}

// ── CV ────────────────────────────────────────────────────────

async function apiUploadCv(file) {
  const formData = new FormData();
  formData.append('file', file);

  const res = await fetch(`${BASE_URL}/cv/upload`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${getToken()}`
      // Ne pas mettre Content-Type ici — le navigateur le gère automatiquement
    },
    body: formData
  });
  return handleResponse(res);
}

async function apiGetCvInfo() {
  const res = await fetch(`${BASE_URL}/cv/info`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiDownloadCv() {
  const res = await fetch(`${BASE_URL}/cv/download`, {
    headers: { 'Authorization': `Bearer ${getToken()}` }
  });
  if (!res.ok) throw new Error('Erreur téléchargement CV');
  return res.blob();
}

async function apiDeleteCv() {
  const res = await fetch(`${BASE_URL}/cv`, {
    method: 'DELETE',
    headers: authHeaders()
  });
  return handleResponse(res);
}
// ==================== ADMINISTRATION ====================

// Récupérer les statistiques globales
async function apiGetAdminStats() {
  const res = await fetch(`${BASE_URL}/admin/stats`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Récupérer tous les utilisateurs
async function apiGetAllUsers() {
  const res = await fetch(`${BASE_URL}/admin/users`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Récupérer les utilisateurs par rôle
async function apiGetUsersByRole(role) {
  const res = await fetch(`${BASE_URL}/admin/users/role/${role}`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Valider un utilisateur (entreprise ou étudiant)
async function apiValidateUser(userId) {
  const res = await fetch(`${BASE_URL}/admin/users/${userId}/validate`, {
    method: 'PUT',
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Désactiver un utilisateur
async function apiDisableUser(userId) {
  const res = await fetch(`${BASE_URL}/admin/users/${userId}/disable`, {
    method: 'PUT',
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Activer un utilisateur
async function apiEnableUser(userId) {
  const res = await fetch(`${BASE_URL}/admin/users/${userId}/enable`, {
    method: 'PUT',
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Récupérer tous les stages
async function apiGetMonStage() {
  const res = await fetch(`${BASE_URL}/stages/mon-stage`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Valider un stage
async function apiValidateStage(stageId) {
  const res = await fetch(`${BASE_URL}/admin/stages/${stageId}/validate`, {
    method: 'PUT',
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Récupérer les étudiants à risque (détection IA)
async function apiGetEtudiantsARisque() {
  const res = await fetch(`${BASE_URL}/admin/etudiants/risque`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Récupérer tous les étudiants
async function apiGetAllEtudiants() {
  const res = await fetch(`${BASE_URL}/admin/etudiants`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Récupérer toutes les entreprises
async function apiGetAllEntreprises() {
  const res = await fetch(`${BASE_URL}/admin/entreprises`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Valider une entreprise
async function apiValidateEntreprise(entrepriseId) {
  const res = await fetch(`${BASE_URL}/admin/entreprises/${entrepriseId}/validate`, {
    method: 'PUT',
    headers: authHeaders()
  });
  return handleResponse(res);
}
// ── UTILISATEUR ───────────────────────────────────────────────
async function apiGetMe() {
  const res = await fetch(`${BASE_URL}/users/me`, {
    headers: authHeaders()
  });
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

async function apiUpdateEntrepriseProfile(data) {
  const res = await fetch(`${BASE_URL}/users/me`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data)
  });
  return handleResponse(res);
}


// ── CANDIDATURES ──────────────────────────────────────────────
// Dans api.js - Ajoutez cette fonction
// CORRECTION IMPORTANTE : Enlever le /api en double
async function apiChangerStatutCandidature(candidatureId, data) {
    const token = getToken();
    if (!token) throw new Error('Non authentifié');

    // CORRECTION : BASE_URL contient déjà /api, donc ne pas ajouter /api ici
    const response = await fetch(`${BASE_URL}/candidatures/${candidatureId}/statut`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
            statut: data.statut,
            commentaire: data.commentaire || '',
            dateEntretien: data.dateEntretien || null,
            lieuEntretien: data.lieuEntretien || null
        })
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `Erreur HTTP ${response.status}`);
    }

    return handleResponse(response);
}

async function apiPostuler(offreId) {

  const file = document.getElementById("cv-file")?.files[0];

  // 1. upload CV si présent
  if (file) {
    const formData = new FormData();
    formData.append("file", file);

    await fetch(`${BASE_URL}/candidatures/upload-cv`, {
      method: "POST",
      headers: {
        Authorization: authHeaders().Authorization
      },
      body: formData
    });
  }

  // 2. postuler normalement
  const res = await fetch(`${BASE_URL}/candidatures/postuler/${offreId}`, {
    method: 'POST',
    headers: authHeaders()
  });

  return handleResponse(res);
}
function closeModalOutside(e, modalId) {
  if (e.target.id === modalId) {
    closeModal(modalId);
  }
}
function closeModal(id) {
  document.getElementById(id).classList.remove('open');
}
async function apiGetCandidatures() {
  const res = await fetch(`${BASE_URL}/candidatures/mes-candidatures`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiGetCandidaturesRecues() {
  const res = await fetch(`${BASE_URL}/candidatures/recues`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiRepondreCandidature(id, action, commentaire = '') {
  const res = await fetch(`${BASE_URL}/candidatures/${id}/repondre`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ action, commentaire })
  });
  return handleResponse(res);
}

// ── STAGES ────────────────────────────────────────────────────

// CORRECTION : Dans api.js, remplacez la fonction apiGetStage
async function apiGetStage() {
  const res = await fetch(`${BASE_URL}/stages/mon-stage`, {
    headers: authHeaders()
  });

  return handleResponse(res);
}


async function apiSignerConventionEntreprise(stageId) {
  const res = await fetch(`${BASE_URL}/stages/${stageId}/signer-convention-entreprise`, {
    method: 'PUT', headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiSignerConventionEncadrant(stageId) {
  const res = await fetch(`${BASE_URL}/stages/${stageId}/signer-convention-encadrant`, {
    method: 'PUT', headers: authHeaders()
  });
  return handleResponse(res);
}

// Fonction API à mettre en dehors de soumettreMission
// Dans api.js - REMPLACEZ la fonction existante
// Dans api.js - assurez-vous que c'est cette version
// Ajoutez cette fonction pour garantir que le token est toujours valide
async function apiDefinirMission(stageId, description, objectifs) {
  const token = localStorage.getItem('smartintern_token');
  
  // Échapper les caractères spéciaux pour le JSON
  const safeDescription = description
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\t/g, '\\t');
    
  const safeObjectifs = objectifs
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\t/g, '\\t');
  
  const body = JSON.stringify({ 
    description: safeDescription,
    objectifs: safeObjectifs 
  });
  
  console.log("Body encodé:", body.substring(0, 200));
  
  const response = await fetch(`${BASE_URL}/stages/${stageId}/definir-mission`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: body
  });
  
  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Erreur ${response.status}: ${error}`);
  }
  
  return response.json();
}
// Fonction soumettreMission corrigée


async function apiGetStats() {
    const res = await fetch(`${BASE_URL}/stats`, {
        headers: authHeaders() // doit inclure Authorization: Bearer <token>
    });
    return handleResponse(res);
}
// ==================== COMPTES RENDUS ====================

async function apiGetMissionsByStage(stageId) {
  const token = localStorage.getItem('smartintern_token');
  const response = await fetch(`${BASE_URL}/missions/stage/${stageId}`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return handleResponse(response);
}



async function apiGetMissionsEncadrant() {
    const token = localStorage.getItem('smartintern_token');
    
    const response = await fetch(`${BASE_URL}/missions/encadrant/mes-missions`, {
        headers: { 'Authorization': `Bearer ${token}` }
    });
    
    return handleResponse(response);
}

async function apiSoumettreCompteRendu(missionId, contenu, commentaire) {
  const token = localStorage.getItem('smartintern_token');
  const response = await fetch(`${BASE_URL}/missions/${missionId}/compte-rendu`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({ contenu, commentaire })
  });
  return handleResponse(response);
}

async function apiValiderCompteRendu(compteRenduId, commentaire) {
  const token = localStorage.getItem('smartintern_token');
  const response = await fetch(`${BASE_URL}/missions/compte-rendu/${compteRenduId}/valider`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({ commentaire })
  });
  return handleResponse(response);
}

async function apiRejeterCompteRendu(compteRenduId, commentaire) {
  const token = localStorage.getItem('smartintern_token');
  const response = await fetch(`${BASE_URL}/missions/compte-rendu/${compteRenduId}/rejeter`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({ commentaire })
  });
  return handleResponse(response);
}
// ── RAPPORTS ──────────────────────────────────────────────────

async function apiDeposerRapport(stageId, type, semaine, titre, contenu, fichiers = []) {
  const formData = new FormData();
  formData.append('stageId', stageId);
  formData.append('type', type);
  formData.append('semaine', semaine);
  formData.append('titre', titre);
  formData.append('contenu', contenu);
  
  fichiers.forEach(fichier => {
    formData.append('fichiers', fichier);
  });

  const res = await fetch(`${BASE_URL}/rapports/deposer`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('smartintern_token')}`
      // Ne mettez PAS 'Content-Type' avec FormData, le navigateur le gère
    },
    body: formData
  });
  
  return handleResponse(res);
}

async function apiGetRapports() {
  console.log('Appel GET /rapports/mes-rapports');
  try {
    const res = await fetch(`${BASE_URL}/rapports/mes-rapports`, {
      headers: authHeaders()
    });
    console.log('Status:', res.status);
    const data = await handleResponse(res);
    console.log('Données reçues:', data);
    return data || [];
  } catch (err) {
    console.error('Erreur détaillée apiGetRapports:', err);
    throw err;
  }
}

async function apiGetRapportsRecus() {
    const token = localStorage.getItem('smartintern_token');
    // ✅ CHANGEMENT: utiliser le bon endpoint
    const response = await fetch(`${BASE_URL}/rapports/recus/encadrant`, {
        headers: { 'Authorization': `Bearer ${token}` }
    });
    return handleResponse(response);
}
async function apiGetRapportsAValider() {
  const res = await fetch(`${BASE_URL}/rapports/a-valider`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

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
function openRapportModal() {
  document.getElementById('modal-rapport').classList.add('open');
  document.getElementById('rapport-error').style.display = 'none';
  document.getElementById('rapport-titre').value = '';
  document.getElementById('rapport-contenu').value = '';
}

async function confirmerRapport() {
  const btn = document.getElementById('btn-rapport-confirm');
  const err = document.getElementById('rapport-error');
  const success = document.getElementById('rapport-success');

  const titre = document.getElementById('rapport-titre').value.trim();
  const contenu = document.getElementById('rapport-contenu').value.trim();
  const type = document.getElementById('rapport-type').value;
  const semaine = document.getElementById('rapport-semaine').value;
  const fichiers = document.getElementById('rapport-fichiers').files;

  if (!titre || !contenu) {
    err.textContent = 'Le titre et le contenu sont obligatoires.';
    err.style.display = 'block';
    return;
  }

  btn.disabled = true;
  btn.textContent = 'Envoi...';
  err.style.display = 'none';
  success.style.display = 'none';

  try {
    const data = await apiDeposerRapport(currentStageId, type, semaine, titre, contenu, Array.from(fichiers));

    success.textContent = data?.message || '✅ Rapport déposé avec succès !';
    success.style.display = 'block';
    
    setTimeout(() => {
      closeModal('modal-rapport');
      chargerRapports();
    }, 1000);

  } catch (e) {
    err.textContent = e.message;
    err.style.display = 'block';
  } finally {
    btn.disabled = false;
    btn.innerHTML = 'Déposer le rapport';
  }
}
// ── NOTIFICATIONS ─────────────────────────────────────────────
async function apiGetNotifications() {
    const res = await fetch(`${BASE_URL}/notifications`, {
        headers: authHeaders()
    });
    try {
        return await handleResponse(res);
    } catch(e) {
        return [];
    }
}

async function apiMarkAllNotificationsRead() {
    const res = await fetch(`${BASE_URL}/notifications/mark-all-read`, {
        method: 'PUT',
        headers: authHeaders()
    });
    return handleResponse(res);
}
// ==================== ENCADRANTS ====================

// Admin — créer un encadrant académique
async function apiAdminCreerEncadrant(data) {
  const res = await fetch(`${BASE_URL}/admin/encadrants`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data)
  });
  return handleResponse(res);
}

// Admin — liste encadrants académiques
async function apiGetEncadrantsAcademiques() {
  const res = await fetch(`${BASE_URL}/admin/users/role/ENCADRANT_ACADEMIQUE`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

// Admin — liste encadrants entreprise
async function apiGetEncadrantsEntreprise() {
    const res = await fetch(`${BASE_URL}/encadrants/entreprise`, {
        headers: authHeaders()
    });
    return handleResponse(res);
}

// Entreprise — créer un encadrant entreprise
async function apiEntrepriseCreerEncadrant(data) {
  const res = await fetch(`${BASE_URL}/encadrants/creer`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data)
  });
  return handleResponse(res);
}
// ==================== CONVENTIONS ====================
async function apiGetConventions() {
    const res = await fetch(`${BASE_URL}/stages/mes-conventions`, {
        headers: authHeaders()
    });
    return handleResponse(res);
}
// ==================== Documents ====================
async function apiGenererDocument(type, stageId) {
  const res = await fetch(`${BASE_URL}/documents/${type}/stage/${stageId}`, {
    headers: { 'Authorization': `Bearer ${getToken()}` }
  });
  if (!res.ok) throw new Error(await res.text());
  return res.blob();
}
// ==================== STAGES ADMIN ====================
async function apiGetAllStages() {
  const res = await fetch(`${BASE_URL}/admin/stages`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}

async function apiAffecterEncadrantEntreprise(stageId, encadrantId) {
  const res = await fetch(`${BASE_URL}/stages/${stageId}/affecter-encadrant-entreprise`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ encadrantId })
  });
  return handleResponse(res);
}

// ==================== AFFECTATION ENCADRANT====================
async function apiGetStagesEncadrantEntreprise() {
    const token = localStorage.getItem('smartintern_token');
    console.log("🔵 Récupération des stages encadrant");
    
    const response = await fetch(`${BASE_URL}/stages/encadrant-entreprise`, {
        headers: { 'Authorization': `Bearer ${token}` }
    });
    
    return handleResponse(response);
}
async function apiAffecterEncadrantEntreprise(stageId, encadrantId) {
  const res = await fetch(`${BASE_URL}/stages/${stageId}/affecter-encadrant-entreprise`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ encadrantId })
  });
  return handleResponse(res);
}

async function apiAffecterEncadrantAcademique(stageId, encadrantId) {
  const res = await fetch(`${BASE_URL}/stages/${stageId}/affecter-encadrant-academique`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ encadrantId })
  });
  return handleResponse(res);
}
// ==================== MISSIONS ====================

async function apiCreerMission(stageId, titre, description, objectifs, dateEcheance) {
    const token = localStorage.getItem('smartintern_token');
    
    const response = await fetch(`${BASE_URL}/missions/stage/${stageId}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ titre, description, objectifs, dateEcheance })
    });
    
    return handleResponse(response);
}

async function apiGetMissionsByStage(stageId) {
    const token = localStorage.getItem('smartintern_token');
    
    const response = await fetch(`${BASE_URL}/missions/stage/${stageId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
    });
    
    return handleResponse(response);
}



// ==================== COMPTES RENDUS ====================

async function apiSoumettreCompteRendu(missionId, contenu, commentaire) {
    const token = localStorage.getItem('smartintern_token');
    
    const response = await fetch(`${BASE_URL}/missions/${missionId}/compte-rendu`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ contenu, commentaire })
    });
    
    return handleResponse(response);
}

async function apiValiderCompteRendu(compteRenduId, commentaire) {
    const token = localStorage.getItem('smartintern_token');
    
    const response = await fetch(`${BASE_URL}/missions/compte-rendu/${compteRenduId}/valider`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ commentaire })
    });
    
    return handleResponse(response);
}

async function apiRejeterCompteRendu(compteRenduId, commentaire) {
    const token = localStorage.getItem('smartintern_token');
    
    const response = await fetch(`${BASE_URL}/missions/compte-rendu/${compteRenduId}/rejeter`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ commentaire })
    });
    
    return handleResponse(response);
}

async function apiGetMesComptesRendus() {
    const token = localStorage.getItem('smartintern_token');
    
    const response = await fetch(`${BASE_URL}/missions/etudiant/mes-comptes-rendus`, {
        headers: { 'Authorization': `Bearer ${token}` }
    });
    
    return handleResponse(response);
}
async function apiRejeterRapport(id, commentaire) {
  const token = localStorage.getItem('smartintern_token');
  const response = await fetch(`${BASE_URL}/rapports/${id}/rejeter`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({ commentaire })
  });
  return handleResponse(response);
}
async function apiGetStageDetails(stageId) {
  const res = await fetch(`${BASE_URL}/admin/stages/${stageId}`, {
    headers: authHeaders()
  });
  return handleResponse(res);
}
// ==================== MISSIONS ÉTUDIANT ====================
// Récupérer les missions de l'étudiant connecté
async function apiGetMesMissions() {
    const token = getToken();
    console.log("Token envoyé:", token ? "Présent" : "MANQUANT ❌");
    
    // ✅ Utilisez BASE_URL au lieu de API_BASE_URL
    const response = await fetch(`${BASE_URL}/missions/etudiant/mes-missions`, {
        method: 'GET',
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
        }
    });
    
    if (!response.ok) {
        throw new Error(`Erreur ${response.status}`);
    }
    
    return await response.json();
}



// Démarrer une mission
async function apiDemarrerMission(missionId) {
    const token = getToken();
    
    // ✅ Utilisez BASE_URL (pas de /api supplémentaire car BASE_URL contient déjà /api)
   const response = await fetch(`${BASE_URL}/missions/${missionId}/demarrer`, {
        method: 'PUT',
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
        }
    });
    
    if (!response.ok) {
        throw new Error(`Erreur ${response.status}`);
    }
    
    return await response.json();
}


// Soumettre un compte-rendu
async function apiSoumettreCompteRendu(missionId, contenu, commentaire) {
    const token = getToken();
    
    // ✅ Utilisez BASE_URL
    const response = await fetch(`${BASE_URL}/missions/${missionId}/compte-rendu`, {
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            contenu: contenu,
            commentaire: commentaire || ""
        })
    });
    
    if (!response.ok) {
        const error = await response.json();
        throw new Error(error.error || `Erreur ${response.status}`);
    }
    
    return await response.json();
}
// Récupérer les comptes-rendus d'une mission
async function apiGetComptesRendus(missionId) {
    const token = getToken();
    
    // ✅ Utilisez BASE_URL
    const response = await fetch(`${BASE_URL}/${missionId}/comptes-rendus`, {
        method: 'GET',
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
        }
    });
    
    if (!response.ok) {
        throw new Error(`Erreur ${response.status}`);
    }
    
    return await response.json();
}

async function apiGetStagesEncadrantAcademique() {
    const token = localStorage.getItem('smartintern_token');
    
    const response = await fetch(`${BASE_URL}/stages/encadrant-academique`, {
        headers: { 'Authorization': `Bearer ${token}` }
    });
    
    return handleResponse(response);
}
async function apiGetRapportsEncadrantAcademique() {
    const token = localStorage.getItem('smartintern_token');
    const response = await fetch(`${BASE_URL}/rapports/recus/encadrant-academique`, {
        headers: { 'Authorization': `Bearer ${token}` }
    });
    return handleResponse(response);
}
async function apiGetStagesSoutenance() {
    const token = localStorage.getItem('smartintern_token');
    const response = await fetch(`${BASE_URL}/stages/soutenances`, {
        headers: { 'Authorization': `Bearer ${token}` }
    });
    return handleResponse(response);
}

async function apiAutoriserSoutenance(stageId, dateSoutenance, commentaire) {
    const token = localStorage.getItem('smartintern_token');
    const response = await fetch(`${BASE_URL}/stages/${stageId}/autoriser-soutenance`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ dateSoutenance, commentaire })
    });
    return handleResponse(response);
}