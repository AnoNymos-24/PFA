// ===== CONFIG =====
const API_URL = 'http://localhost:8081/api';

// ===== NAVIGATION =====
function showPage(pageId) {
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  document.getElementById('page-' + pageId).classList.add('active');
  window.scrollTo(0, 0);
}

function switchTab(tab) {
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  document.getElementById('tab-' + tab).classList.add('active');

  if (tab === 'login') {
    document.getElementById('form-login').classList.remove('hidden');
    document.getElementById('form-register').classList.add('hidden');
  } else {
    document.getElementById('form-login').classList.add('hidden');
    document.getElementById('form-register').classList.remove('hidden');
  }
  hideAlert();
}

// ===== ROLE SELECT =====
function selectRole(el) {
  document.querySelectorAll('.role-opt').forEach(r => r.classList.remove('selected'));
  el.classList.add('selected');
  updateFormFields(el.dataset.role);
}

function updateFormFields(role) {
  const formRow = document.querySelector('.form-row');
  const emailGroup = document.getElementById('reg-email').parentElement;
 removeExtraField()
  if (role === 'ENTREPRISE') {
    formRow.innerHTML = `
      <div class="form-group" style="grid-column: span 2;">
        <label>Nom de l'entreprise</label>
        <input type="text" id="reg-firstname" placeholder="Ex: TechCorp Tunisia" required>
        <input type="hidden" id="reg-lastname" value="Entreprise">
      </div>
    `;
    // Ajouter champ secteur
    if (!document.getElementById('reg-secteur')) {
      const div = document.createElement('div');
      div.className = 'form-group';
      div.id = 'group-secteur';
      div.innerHTML = `
        <label>Secteur d'activité</label>
        <select id="reg-secteur" style="width:100%;padding:10px 12px;border:1px solid #d0d0cc;border-radius:8px;font-size:14px;font-family:inherit;color:#1a1a1a;background:#fff;outline:none;">
          <option value="">Choisir un secteur</option>
          <option>Informatique / Tech</option>
          <option>Finance / Banque</option>
          <option>Industrie</option>
          <option>Santé</option>
          <option>Commerce</option>
          <option>Autre</option>
        </select>
      `;
      emailGroup.before(div);
    }
  } else if (role === 'ENCADRANT_ACADEMIQUE') {
    formRow.innerHTML = `
      <div class="form-group">
        <label>Prénom</label>
        <input type="text" id="reg-firstname" placeholder="Prénom" required>
      </div>
      <div class="form-group">
        <label>Nom</label>
        <input type="text" id="reg-lastname" placeholder="Nom" required>
      </div>
    `;
    removeExtraField();
    if (!document.getElementById('reg-etablissement')) {
      const div = document.createElement('div');
      div.className = 'form-group';
      div.id = 'group-secteur';
      div.innerHTML = `
        <label>Établissement universitaire</label>
        <input type="text" id="reg-etablissement" placeholder="Ex: Université de Tunis">
      `;
      emailGroup.before(div);
    }
  } else if (role === 'ENCADRANT_ENTREPRISE') {
    formRow.innerHTML = `
      <div class="form-group">
        <label>Prénom</label>
        <input type="text" id="reg-firstname" placeholder="Prénom" required>
      </div>
      <div class="form-group">
        <label>Nom</label>
        <input type="text" id="reg-lastname" placeholder="Nom" required>
      </div>
    `;
    removeExtraField();
    if (!document.getElementById('reg-entreprise-enc')) {
      const div = document.createElement('div');
      div.className = 'form-group';
      div.id = 'group-secteur';
      div.innerHTML = `
        <label>Nom de l'entreprise</label>
        <input type="text" id="reg-entreprise-enc" placeholder="Ex: TechCorp Tunisia">
      `;
      emailGroup.before(div);
    }
  } else {
    
    // ETUDIANT — formulaire par défaut
    formRow.innerHTML = `
      <div class="form-group">
        <label>Prénom</label>
        <input type="text" id="reg-firstname" placeholder="Prénom" required>
      </div>
      <div class="form-group">
        <label>Nom</label>
        <input type="text" id="reg-lastname" placeholder="Nom" required>
      </div>
    `;
    removeExtraField();
    if (!document.getElementById('reg-universite')) {
      const div = document.createElement('div');
      div.className = 'form-group';
      div.id = 'group-secteur';
      div.innerHTML = `
        <label>Université / École</label>
        <input type="text" id="reg-universite" placeholder="Ex: ISET Tunis">
      `;
      emailGroup.before(div);
    }
  }
}

function removeExtraField() {
  const extra = document.getElementById('group-secteur');
  if (extra) extra.remove();
}
function getSelectedRole() {
  const selected = document.querySelector('.role-opt.selected');
  return selected ? selected.dataset.role : 'ETUDIANT';
}

// ===== ALERT =====
function showAlert(message, type) {
  const alert = document.getElementById('alert');
  alert.textContent = message;
  alert.className = 'alert ' + type;
}

function hideAlert() {
  const alert = document.getElementById('alert');
  alert.className = 'alert hidden';
  alert.textContent = '';
}

// ===== LOADING STATE =====
function setLoading(btnId, loading) {
  const btn = document.getElementById(btnId);
  btn.disabled = loading;
  btn.textContent = loading ? 'Chargement...' : (btnId === 'btn-login' ? 'Se connecter' : 'Créer mon compte');
}

// ===== LOGIN =====
async function handleLogin(event) {
  event.preventDefault();
  hideAlert();
  setLoading('btn-login', true);

  const email = document.getElementById('login-email').value.trim();
  const password = document.getElementById('login-password').value;

  try {
    const response = await fetch(`${API_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });

    const data = await response.json();

    if (!response.ok) {
      showAlert(data.message || 'Email ou mot de passe incorrect', 'error');
      return;
    }

    // Sauvegarde JWT + infos user
    localStorage.setItem('token', data.token);
    localStorage.setItem('user', JSON.stringify(data));

    showAlert('Connexion réussie ! Redirection...', 'success');
    setTimeout(() => loadDashboard(data), 800);

  } catch (error) {
    showAlert('Erreur de connexion au serveur. Vérifiez que le backend est démarré.', 'error');
  } finally {
    setLoading('btn-login', false);
  }
}

// ===== REGISTER =====
async function handleRegister(event) {
  event.preventDefault();
  hideAlert();
  setLoading('btn-register', true);

  const firstName = document.getElementById('reg-firstname').value.trim();
  const lastName = document.getElementById('reg-lastname').value.trim();
  const email = document.getElementById('reg-email').value.trim();
  const password = document.getElementById('reg-password').value;
  const role = getSelectedRole();

  try {
    const response = await fetch(`${API_URL}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ firstName, lastName, email, password, role })
    });

    const data = await response.json();

    if (!response.ok) {
      showAlert(data.message || 'Erreur lors de l\'inscription', 'error');
      return;
    }

    // Si OTP requis → afficher l'écran de vérification
    if (data.type === 'OTP_REQUIRED') {
      showOtpScreen(data.email);
      return;
    }

    // Sinon connexion directe
    localStorage.setItem('token', data.token);
    localStorage.setItem('user', JSON.stringify(data));
    showAlert('Compte créé avec succès !', 'success');
    setTimeout(() => loadDashboard(data), 800);

  } catch (error) {
    showAlert('Erreur de connexion au serveur. Vérifiez que le backend est démarré.', 'error');
  } finally {
    setLoading('btn-register', false);
  }
}

// ===== DASHBOARD =====
function loadDashboard(user) {
  document.querySelector('.nav-actions').innerHTML = `
  <span style="font-size:14px;font-weight:500;">${user.firstName} ${user.lastName}</span>
  <span class="role-badge">${getRoleLabel(user.role)}</span>
  <button class="btn-outline" onclick="logout()">Déconnexion</button>
`;
  document.getElementById('dashboard-welcome').textContent = 'Bienvenue, ' + user.firstName + ' !';
  document.getElementById('dashboard-sub').textContent = 'Votre espace ' + getRoleLabel(user.role) + ' — SmartIntern AI';

  const cards = getDashboardCards(user.role);
  const container = document.getElementById('dashboard-cards');
  container.innerHTML = cards.map(card => `
    <div class="dash-card">
      <div class="dash-card-icon">${card.icon}</div>
      <div class="dash-card-title">${card.title}</div>
      <div class="dash-card-sub">${card.sub}</div>
    </div>
  `).join('');
  // Section CV pour étudiant
if (user.role === 'ETUDIANT') {
  const cvSection = document.createElement('div');
  cvSection.className = 'cv-section';
  cvSection.innerHTML = `
    <h3>Mon CV</h3>
    <div id="cv-alert" class="alert hidden"></div>
    <div class="cv-upload-box">
      <div class="cv-icon">📄</div>
      <p id="cv-status">Téléversez votre CV au format PDF</p>
      <p id="cv-filename" class="cv-filename"></p>
      <label class="btn-upload" for="cv-input">Choisir un fichier PDF</label>
      <input type="file" id="cv-input" accept=".pdf" style="display:none" onchange="uploadCv(event)">
      <p class="cv-hint">Taille maximale : 5MB</p>
    </div>
  `;
  document.querySelector('.dashboard-content').insertBefore(
    cvSection,
    document.getElementById('dashboard-cards')
  );
}

if (user.role === 'ENTREPRISE') {
  setTimeout(() => loadOffresEntreprise(), 100);
}

  showPage('dashboard');
}

function getRoleLabel(role) {
  const labels = {
    'ETUDIANT': 'Étudiant',
    'ENTREPRISE': 'Entreprise',
    'ADMIN': 'Administrateur',
    'ENCADRANT_ACADEMIQUE': 'Enc. Académique',
    'ENCADRANT_ENTREPRISE': 'Enc. Entreprise'
  };
  return labels[role] || role;
}

function getDashboardCards(role) {
  const cards = {
    'ETUDIANT': [
  { icon: '🔍', title: 'Offres de stage', sub: 'Parcourir et filtrer les offres' },
  { icon: '📄', title: 'Mes candidatures', sub: 'Suivre le statut de mes dossiers' },
  { icon: '📝', title: 'Mes rapports', sub: 'Rédiger mes rapports hebdomadaires' },
  { icon: '👤', title: 'Mon profil', sub: 'Gérer mon CV et mes informations' },
],
    'ENTREPRISE': [
      { icon: '📢', title: 'Mes offres', sub: 'Publier et gérer mes offres de stage' },
      { icon: '👥', title: 'Candidatures', sub: 'Gérer les candidatures reçues' },
      { icon: '🤖', title: 'Matching IA', sub: 'Voir les scores de correspondance' },
      { icon: '📋', title: 'Conventions', sub: 'Gérer les conventions de stage' },
    ],
    'ADMIN': [
      { icon: '👥', title: 'Utilisateurs', sub: 'Gérer les comptes et les rôles' },
      { icon: '📊', title: 'Statistiques', sub: 'Tableau de bord global' },
      { icon: '📄', title: 'Documents', sub: 'Générer les documents officiels' },
      { icon: '⚠️', title: 'Alertes IA', sub: 'Étudiants à risque détectés' },
    ],
    'ENCADRANT_ACADEMIQUE': [
      { icon: '🎓', title: 'Mes étudiants', sub: 'Suivre l\'avancement des stagiaires' },
      { icon: '📝', title: 'Rapports à valider', sub: 'Valider et commenter les rapports' },
      { icon: '✅', title: 'Soutenances', sub: 'Autoriser les soutenances' },
      { icon: '📊', title: 'Suivi', sub: 'Tableau de bord de progression' },
    ],
    'ENCADRANT_ENTREPRISE': [
      { icon: '🎯', title: 'Mission', sub: 'Définir la mission du stagiaire' },
      { icon: '✅', title: 'Tâches', sub: 'Valider les tâches effectuées' },
      { icon: '⭐', title: 'Évaluation', sub: 'Remplir la fiche d\'évaluation' },
      { icon: '📜', title: 'Attestation', sub: 'Générer l\'attestation de stage' },
    ],
  };
  return cards[role] || cards['ETUDIANT'];
}

// ===== LOGOUT =====
function logout() {
   document.querySelector('.nav-actions').innerHTML = `
    <button class="btn-outline" onclick="showPage('auth'); switchTab('login')">Se connecter</button>
    <button class="btn-primary" onclick="showPage('auth'); switchTab('register')">S'inscrire</button>
  `;
  
  showPage('landing');
  window.scrollTo(0, 0);
}

// ===== AUTO-LOGIN si token existant =====
window.addEventListener('DOMContentLoaded', () => {
  const user = localStorage.getItem('user');
  const token = localStorage.getItem('token');
  if (user && token) {
    loadDashboard(JSON.parse(user));
  } else {
    showPage('landing');
  }
});

// ===== CV UPLOAD =====
async function uploadCv(event) {
  const file = event.target.files[0];
  if (!file) return;

  if (file.type !== 'application/pdf') {
    showCvAlert('Seuls les fichiers PDF sont acceptés', 'error');
    return;
  }

  if (file.size > 5 * 1024 * 1024) {
    showCvAlert('Le fichier ne doit pas dépasser 5MB', 'error');
    return;
  }

  const token = localStorage.getItem('token');
  const formData = new FormData();
  formData.append('file', file);

  document.getElementById('cv-status').textContent = 'Téléversement en cours...';

  try {
    const response = await fetch(`${API_URL}/etudiant/cv`, {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + token },
      body: formData
    });

    const data = await response.json();

    if (!response.ok) {
      showCvAlert(data.message || 'Erreur lors du téléversement', 'error');
      return;
    }

    showCvAlert('✅ CV téléversé avec succès !', 'success');
    document.getElementById('cv-filename').textContent = file.name;
    document.getElementById('cv-status').textContent = 'CV actuel :';

  } catch (error) {
    showCvAlert('Erreur de connexion au serveur', 'error');
  }
}

function showCvAlert(message, type) {
  const el = document.getElementById('cv-alert');
  if (!el) return;
  el.textContent = message;
  el.className = 'alert ' + type;
  setTimeout(() => { el.className = 'alert hidden'; }, 4000);
}

// ===== OFFRES =====
async function loadOffresEntreprise() {
  const token = localStorage.getItem('token');
  try {
    const response = await fetch(`${API_URL}/entreprise/offres`, {
      headers: { 'Authorization': 'Bearer ' + token }
    });
    const offres = await response.json();
    renderOffresEntreprise(offres);
  } catch (error) {
    console.error('Erreur chargement offres', error);
  }
}

function renderOffresEntreprise(offres) {
  const container = document.getElementById('dashboard-cards');
  
  // Bouton publier une offre
  const header = document.createElement('div');
  header.style = 'display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;';
  header.innerHTML = `
    <h3 style="font-size:16px;font-weight:600;">Mes offres de stage (${offres.length})</h3>
    <button class="btn-primary" onclick="showOffreForm()">+ Publier une offre</button>
  `;

  // Formulaire publication (caché par défaut)
  const formSection = document.createElement('div');
  formSection.id = 'offre-form-section';
  formSection.style = 'display:none;';
  formSection.innerHTML = `
    <div class="offre-form-card">
      <h3>Publier une offre de stage</h3>
      <div id="offre-alert" class="alert hidden"></div>
      <div class="form-group">
        <label>Titre du poste</label>
        <input type="text" id="offre-titre" placeholder="Ex: Développeur Java Spring Boot">
      </div>
      <div class="form-group">
        <label>Description</label>
        <textarea id="offre-description" rows="4" placeholder="Décrivez le stage, les missions, les compétences requises..." style="width:100%;padding:10px 12px;border:1px solid #d0d0cc;border-radius:8px;font-size:14px;font-family:inherit;resize:vertical;outline:none;"></textarea>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>Ville</label>
          <input type="text" id="offre-ville" placeholder="Ex: Tunis">
        </div>
        <div class="form-group">
          <label>Domaine</label>
          <select id="offre-domaine" style="width:100%;padding:10px 12px;border:1px solid #d0d0cc;border-radius:8px;font-size:14px;font-family:inherit;color:#1a1a1a;background:#fff;outline:none;">
            <option value="">Choisir un domaine</option>
            <option>Informatique</option>
            <option>Finance</option>
            <option>Marketing</option>
            <option>Industrie</option>
            <option>Santé</option>
            <option>Commerce</option>
            <option>Autre</option>
          </select>
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>Durée (mois)</label>
          <input type="number" id="offre-duree" placeholder="Ex: 3" min="1" max="12">
        </div>
        <div class="form-group">
          <label>Date limite</label>
          <input type="date" id="offre-date-limite">
        </div>
      </div>
      <div style="display:flex;gap:10px;margin-top:8px;">
        <button class="btn-submit" onclick="publishOffre()" style="flex:1;">Publier l'offre</button>
        <button class="btn-outline" onclick="hideOffreForm()" style="flex:1;">Annuler</button>
      </div>
    </div>
  `;

  // Liste des offres
  const listeSection = document.createElement('div');
  listeSection.id = 'offres-liste';
  
  if (offres.length === 0) {
    listeSection.innerHTML = `
      <div style="text-align:center;padding:40px;color:#888;background:#fff;border-radius:12px;border:1px dashed #d0d0cc;">
        <div style="font-size:36px;margin-bottom:12px;">📢</div>
        <p style="font-size:15px;font-weight:500;margin-bottom:6px;">Aucune offre publiée</p>
        <p style="font-size:13px;">Cliquez sur "Publier une offre" pour commencer</p>
      </div>
    `;
  } else {
    listeSection.innerHTML = offres.map(o => `
      <div class="offre-card" id="offre-${o.id}">
        <div class="offre-card-header">
          <div>
            <div class="offre-titre">${o.titre}</div>
            <div class="offre-meta">${o.ville} · ${o.domaine} · ${o.dureeMois} mois</div>
          </div>
          <div style="display:flex;gap:8px;align-items:center;">
            <span class="offre-statut ${o.statut === 'ACTIVE' ? 'statut-active' : 'statut-fermee'}">${o.statut === 'ACTIVE' ? 'Active' : 'Fermée'}</span>
            <button class="btn-outline" style="font-size:12px;padding:5px 12px;" onclick="editOffre(${o.id})">Modifier</button>
            <button class="btn-delete" onclick="deleteOffre(${o.id})">Supprimer</button>
          </div>
        </div>
        <div class="offre-desc">${o.description}</div>
        ${o.dateLimite ? `<div class="offre-date">Date limite : ${new Date(o.dateLimite).toLocaleDateString('fr-FR')}</div>` : ''}
      </div>
    `).join('');
  }

  container.innerHTML = '';
  container.appendChild(header);
  container.appendChild(formSection);
  container.appendChild(listeSection);
}

function showOffreForm(offre = null) {
  document.getElementById('offre-form-section').style.display = 'block';
  if (offre) {
    document.getElementById('offre-titre').value = offre.titre;
    document.getElementById('offre-description').value = offre.description;
    document.getElementById('offre-ville').value = offre.ville;
    document.getElementById('offre-domaine').value = offre.domaine;
    document.getElementById('offre-duree').value = offre.dureeMois;
    document.getElementById('offre-date-limite').value = offre.dateLimite || '';
    document.getElementById('offre-form-section').dataset.editId = offre.id;
  } else {
    document.getElementById('offre-form-section').dataset.editId = '';
  }
  document.getElementById('offre-form-section').scrollIntoView({ behavior: 'smooth' });
}

function hideOffreForm() {
  document.getElementById('offre-form-section').style.display = 'none';
}

async function publishOffre() {
  const token = localStorage.getItem('token');
  const editId = document.getElementById('offre-form-section').dataset.editId;

  const data = {
    titre: document.getElementById('offre-titre').value.trim(),
    description: document.getElementById('offre-description').value.trim(),
    ville: document.getElementById('offre-ville').value.trim(),
    domaine: document.getElementById('offre-domaine').value,
    dureeMois: parseInt(document.getElementById('offre-duree').value),
    dateLimite: document.getElementById('offre-date-limite').value || null
  };

  if (!data.titre || !data.description || !data.ville || !data.domaine) {
    document.getElementById('offre-alert').textContent = 'Veuillez remplir tous les champs obligatoires';
    document.getElementById('offre-alert').className = 'alert error';
    return;
  }

  const url = editId
    ? `${API_URL}/entreprise/offres/${editId}`
    : `${API_URL}/entreprise/offres`;
  const method = editId ? 'PUT' : 'POST';

  try {
    const response = await fetch(url, {
      method,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + token
      },
      body: JSON.stringify(data)
    });

    if (!response.ok) {
      document.getElementById('offre-alert').textContent = 'Erreur lors de la publication';
      document.getElementById('offre-alert').className = 'alert error';
      return;
    }

    hideOffreForm();
    loadOffresEntreprise();

  } catch (error) {
    document.getElementById('offre-alert').textContent = 'Erreur de connexion';
    document.getElementById('offre-alert').className = 'alert error';
  }
}

async function deleteOffre(id) {
  if (!confirm('Supprimer cette offre ?')) return;
  const token = localStorage.getItem('token');
  await fetch(`${API_URL}/entreprise/offres/${id}`, {
    method: 'DELETE',
    headers: { 'Authorization': 'Bearer ' + token }
  });
  loadOffresEntreprise();
}

function editOffre(id) {
  const token = localStorage.getItem('token');
  fetch(`${API_URL}/entreprise/offres`, {
    headers: { 'Authorization': 'Bearer ' + token }
  })
  .then(r => r.json())
  .then(offres => {
    const offre = offres.find(o => o.id === id);
    if (offre) showOffreForm(offre);
  });
}
// ===== OTP =====
function showOtpScreen(email) {
  const authCard = document.querySelector('.auth-card');
  authCard.innerHTML = `
    <div class="auth-logo">
      <div class="brand-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
      </div>
      <span>SmartIntern AI</span>
    </div>

    <div style="text-align:center;margin-bottom:24px;">
      <div style="font-size:48px;margin-bottom:12px;">📧</div>
      <h2 style="font-size:20px;font-weight:600;margin-bottom:6px;">Vérifiez votre email</h2>
      <p style="font-size:13px;color:#888;">Un code à 4 chiffres a été envoyé à</p>
      <p style="font-size:14px;font-weight:600;color:#185FA5;">${email}</p>
    </div>

    <div id="otp-alert" class="alert hidden"></div>

    <div class="otp-inputs">
      <input type="text" class="otp-input" maxlength="1" id="otp-0" oninput="otpNext(this, 0)">
      <input type="text" class="otp-input" maxlength="1" id="otp-1" oninput="otpNext(this, 1)">
      <input type="text" class="otp-input" maxlength="1" id="otp-2" oninput="otpNext(this, 2)">
      <input type="text" class="otp-input" maxlength="1" id="otp-3" oninput="otpNext(this, 3)">
    </div>

    <button class="btn-submit" id="btn-verify" onclick="verifyOtp('${email}')" style="margin-top:20px;">
      Vérifier le code
    </button>

    <div style="text-align:center;margin-top:16px;font-size:13px;color:#888;">
      Vous n'avez pas reçu le code ?
      <a id="resend-link" onclick="resendOtp('${email}')" style="color:#185FA5;cursor:pointer;font-weight:500;">Renvoyer</a>
    </div>

    <div id="resend-timer" style="text-align:center;font-size:12px;color:#aaa;margin-top:6px;"></div>
  `;

  // Focus sur le premier champ
  document.getElementById('otp-0').focus();

  // Démarrer le timer 60 secondes
  startResendTimer();
}

function otpNext(input, index) {
  // Accepter uniquement les chiffres
  input.value = input.value.replace(/[^0-9]/g, '');

  if (input.value && index < 3) {
    document.getElementById('otp-' + (index + 1)).focus();
  }

  // Vérifier automatiquement si 4 chiffres saisis
  const code = getOtpCode();
  if (code.length === 4) {
    const email = document.querySelector('.auth-card p + p')?.textContent ||
                  document.querySelectorAll('.auth-card p')[1]?.textContent;
  }
}

function getOtpCode() {
  return ['otp-0','otp-1','otp-2','otp-3']
    .map(id => document.getElementById(id)?.value || '')
    .join('');
}

async function verifyOtp(email) {
  const code = getOtpCode();

  if (code.length !== 4) {
    document.getElementById('otp-alert').textContent = 'Entrez le code à 4 chiffres';
    document.getElementById('otp-alert').className = 'alert error';
    return;
  }

  const btn = document.getElementById('btn-verify');
  btn.disabled = true;
  btn.textContent = 'Vérification...';

  try {
    const response = await fetch(`${API_URL}/auth/verify-otp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, code })
    });

    const data = await response.json();

    if (!response.ok) {
      document.getElementById('otp-alert').textContent = data.message || 'Code incorrect';
      document.getElementById('otp-alert').className = 'alert error';
      // Vider les champs
      ['otp-0','otp-1','otp-2','otp-3'].forEach(id => {
        document.getElementById(id).value = '';
      });
      document.getElementById('otp-0').focus();
      btn.disabled = false;
      btn.textContent = 'Vérifier le code';
      return;
    }

    // Compte vérifié !
    localStorage.setItem('token', data.token);
    localStorage.setItem('user', JSON.stringify(data));
    loadDashboard(data);

  } catch (error) {
    document.getElementById('otp-alert').textContent = 'Erreur de connexion';
    document.getElementById('otp-alert').className = 'alert error';
    btn.disabled = false;
    btn.textContent = 'Vérifier le code';
  }
}

async function resendOtp(email) {
  const link = document.getElementById('resend-link');
  link.style.pointerEvents = 'none';
  link.style.opacity = '0.5';

  try {
    await fetch(`${API_URL}/auth/resend-otp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email })
    });

    document.getElementById('otp-alert').textContent = '✅ Code renvoyé avec succès !';
    document.getElementById('otp-alert').className = 'alert success';
    startResendTimer();

  } catch (error) {
    document.getElementById('otp-alert').textContent = 'Erreur lors du renvoi';
    document.getElementById('otp-alert').className = 'alert error';
  }
}

function startResendTimer() {
  const link = document.getElementById('resend-link');
  const timer = document.getElementById('resend-timer');
  if (!link || !timer) return;

  link.style.pointerEvents = 'none';
  link.style.opacity = '0.4';

  let seconds = 60;
  timer.textContent = `Renvoyer dans ${seconds}s`;

  const interval = setInterval(() => {
    seconds--;
    if (seconds <= 0) {
      clearInterval(interval);
      timer.textContent = '';
      link.style.pointerEvents = 'auto';
      link.style.opacity = '1';
    } else {
      timer.textContent = `Renvoyer dans ${seconds}s`;
    }
  }, 1000);
}
// ===== MOT DE PASSE OUBLIÉ =====
function showForgotPassword() {
  const authCard = document.querySelector('.auth-card');
  authCard.innerHTML = `
    <div class="auth-logo">
      <div class="brand-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
      </div>
      <span>SmartIntern AI</span>
    </div>

    <div style="text-align:center;margin-bottom:24px;">
      <div style="font-size:48px;margin-bottom:12px;">🔐</div>
      <h2 style="font-size:20px;font-weight:600;margin-bottom:6px;">Mot de passe oublié ?</h2>
      <p style="font-size:13px;color:#888;line-height:1.6;">Entrez votre email et nous vous enverrons<br>un code de réinitialisation.</p>
    </div>

    <div id="forgot-alert" class="alert hidden"></div>

    <div class="form-group">
      <label>Adresse email</label>
      <input type="email" id="forgot-email" placeholder="votre@email.com">
    </div>

    <button class="btn-submit" id="btn-forgot" onclick="sendForgotOtp()">
      Envoyer le code
    </button>

    <p class="switch-text" style="margin-top:16px;">
      <a onclick="restoreAuthCard()" style="color:#185FA5;cursor:pointer;font-weight:500;">← Retour à la connexion</a>
    </p>
  `;
}

async function sendForgotOtp() {
  const email = document.getElementById('forgot-email').value.trim();

  if (!email) {
    document.getElementById('forgot-alert').textContent = 'Entrez votre adresse email';
    document.getElementById('forgot-alert').className = 'alert error';
    return;
  }

  const btn = document.getElementById('btn-forgot');
  btn.disabled = true;
  btn.textContent = 'Envoi en cours...';

  try {
    const response = await fetch(`${API_URL}/auth/forgot-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email })
    });

    const data = await response.json();

    // Afficher l'écran de saisie du code
    showResetPasswordScreen(email);

  } catch (error) {
    document.getElementById('forgot-alert').textContent = 'Erreur de connexion au serveur';
    document.getElementById('forgot-alert').className = 'alert error';
    btn.disabled = false;
    btn.textContent = 'Envoyer le code';
  }
}

function showResetPasswordScreen(email) {
  const authCard = document.querySelector('.auth-card');
  authCard.innerHTML = `
    <div class="auth-logo">
      <div class="brand-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
      </div>
      <span>SmartIntern AI</span>
    </div>

    <div style="text-align:center;margin-bottom:24px;">
      <div style="font-size:48px;margin-bottom:12px;">📧</div>
      <h2 style="font-size:20px;font-weight:600;margin-bottom:6px;">Vérifiez votre email</h2>
      <p style="font-size:13px;color:#888;">Code envoyé à</p>
      <p style="font-size:14px;font-weight:600;color:#185FA5;">${email}</p>
    </div>

    <div id="reset-alert" class="alert hidden"></div>

    <div class="otp-inputs" style="margin-bottom:20px;">
      <input type="text" class="otp-input" maxlength="1" id="reset-otp-0" oninput="otpNextReset(this, 0)">
      <input type="text" class="otp-input" maxlength="1" id="reset-otp-1" oninput="otpNextReset(this, 1)">
      <input type="text" class="otp-input" maxlength="1" id="reset-otp-2" oninput="otpNextReset(this, 2)">
      <input type="text" class="otp-input" maxlength="1" id="reset-otp-3" oninput="otpNextReset(this, 3)">
    </div>

    <div class="form-group">
      <label>Nouveau mot de passe</label>
      <input type="password" id="new-password" placeholder="8 caractères minimum" minlength="8">
    </div>

    <div class="form-group">
      <label>Confirmer le mot de passe</label>
      <input type="password" id="confirm-password" placeholder="Répétez le mot de passe">
    </div>

    <button class="btn-submit" id="btn-reset" onclick="resetPassword('${email}')">
      Réinitialiser le mot de passe
    </button>

    <div style="text-align:center;margin-top:16px;font-size:13px;color:#888;">
      Vous n'avez pas reçu le code ?
      <a onclick="resendForgotOtp('${email}')" style="color:#185FA5;cursor:pointer;font-weight:500;">Renvoyer</a>
    </div>

    <p class="switch-text">
      <a onclick="restoreAuthCard()" style="color:#185FA5;cursor:pointer;font-weight:500;">← Retour à la connexion</a>
    </p>
  `;

  document.getElementById('reset-otp-0').focus();
}

function otpNextReset(input, index) {
  input.value = input.value.replace(/[^0-9]/g, '');
  if (input.value && index < 3) {
    document.getElementById('reset-otp-' + (index + 1)).focus();
  }
}

function getResetOtpCode() {
  return ['reset-otp-0','reset-otp-1','reset-otp-2','reset-otp-3']
    .map(id => document.getElementById(id)?.value || '')
    .join('');
}

async function resetPassword(email) {
  const code = getResetOtpCode();
  const newPassword = document.getElementById('new-password').value;
  const confirmPassword = document.getElementById('confirm-password').value;

  if (code.length !== 4) {
    document.getElementById('reset-alert').textContent = 'Entrez le code à 4 chiffres';
    document.getElementById('reset-alert').className = 'alert error';
    return;
  }

  if (!newPassword || newPassword.length < 8) {
    document.getElementById('reset-alert').textContent = 'Le mot de passe doit contenir au moins 8 caractères';
    document.getElementById('reset-alert').className = 'alert error';
    return;
  }

  if (newPassword !== confirmPassword) {
    document.getElementById('reset-alert').textContent = 'Les mots de passe ne correspondent pas';
    document.getElementById('reset-alert').className = 'alert error';
    return;
  }

  const btn = document.getElementById('btn-reset');
  btn.disabled = true;
  btn.textContent = 'Réinitialisation...';

  try {
    const response = await fetch(`${API_URL}/auth/reset-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, code, newPassword })
    });

    const data = await response.json();

    if (!response.ok) {
      document.getElementById('reset-alert').textContent = data.message || 'Code incorrect';
      document.getElementById('reset-alert').className = 'alert error';
      btn.disabled = false;
      btn.textContent = 'Réinitialiser le mot de passe';
      return;
    }

    // Succès → retour connexion
    restoreAuthCard();
    setTimeout(() => showAlert('✅ Mot de passe réinitialisé ! Connectez-vous.', 'success'), 100);

  } catch (error) {
    document.getElementById('reset-alert').textContent = 'Erreur de connexion';
    document.getElementById('reset-alert').className = 'alert error';
    btn.disabled = false;
    btn.textContent = 'Réinitialiser le mot de passe';
  }
}

async function resendForgotOtp(email) {
  await fetch(`${API_URL}/auth/forgot-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });
  document.getElementById('reset-alert').textContent = '✅ Code renvoyé !';
  document.getElementById('reset-alert').className = 'alert success';
}

function restoreAuthCard() {
  const authCard = document.querySelector('.auth-card');
  authCard.innerHTML = `
    <div class="auth-logo">
      <div class="brand-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
      </div>
      <span>SmartIntern AI</span>
    </div>

    <div class="tabs">
      <button class="tab active" id="tab-login" onclick="switchTab('login')">Connexion</button>
      <button class="tab" id="tab-register" onclick="switchTab('register')">Inscription</button>
    </div>

    <div id="alert" class="alert hidden"></div>

    <form id="form-login" class="auth-form" onsubmit="handleLogin(event)">
      <h2 id="form-title">Bon retour !</h2>
      <p id="form-sub">Connectez-vous à votre espace</p>
      <div class="form-group">
        <label>Adresse email</label>
        <input type="email" id="login-email" placeholder="votre@email.com" required>
      </div>
      <div class="form-group">
        <label>Mot de passe</label>
        <input type="password" id="login-password" placeholder="••••••••" required>
      </div>
      <a class="forgot-link" onclick="showForgotPassword()">Mot de passe oublié ?</a>
      <button type="submit" class="btn-submit" id="btn-login">Se connecter</button>
      <p class="switch-text">Pas encore de compte ? <a onclick="switchTab('register')">S'inscrire</a></p>
    </form>

    <form id="form-register" class="auth-form hidden" onsubmit="handleRegister(event)">
      <h2>Créer un compte</h2>
      <p>Rejoignez SmartIntern AI gratuitement</p>
      <div class="role-select">
        <div class="role-opt selected" data-role="ETUDIANT" onclick="selectRole(this)"><span>🎓</span> Étudiant</div>
        <div class="role-opt" data-role="ENTREPRISE" onclick="selectRole(this)"><span>🏢</span> Entreprise</div>
        <div class="role-opt" data-role="ENCADRANT_ACADEMIQUE" onclick="selectRole(this)"><span>📚</span> Enc. académique</div>
        <div class="role-opt" data-role="ENCADRANT_ENTREPRISE" onclick="selectRole(this)"><span>🏭</span> Enc. entreprise</div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>Prénom</label>
          <input type="text" id="reg-firstname" placeholder="Prénom" required>
        </div>
        <div class="form-group">
          <label>Nom</label>
          <input type="text" id="reg-lastname" placeholder="Nom" required>
        </div>
      </div>
      <div class="form-group">
        <label>Adresse email</label>
        <input type="email" id="reg-email" placeholder="votre@email.com" required>
      </div>
      <div class="form-group">
        <label>Mot de passe</label>
        <input type="password" id="reg-password" placeholder="8 caractères minimum" required minlength="8">
      </div>
      <button type="submit" class="btn-submit" id="btn-register">Créer mon compte</button>
      <p class="switch-text">Déjà un compte ? <a onclick="switchTab('login')">Se connecter</a></p>
    </form>
  `;
}