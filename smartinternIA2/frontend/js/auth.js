const TOKEN_KEY  = 'smartintern_token';
const ROLE_KEY   = 'smartintern_role';
const NOM_KEY    = 'smartintern_nom';
const PRENOM_KEY = 'smartintern_prenom';
const ID_KEY     = 'smartintern_userId';

function saveSession(data) {
  localStorage.setItem('smartintern_token',  data.token);
  // Le backend renvoie role sans préfixe ("ADMIN") — on normalise ici
  const role = data.role
    ? (data.role.startsWith('ROLE_') ? data.role : 'ROLE_' + data.role)
    : '';
  localStorage.setItem('smartintern_role',   role);
  // Compatibilité backend : firstName/lastName OU prenom/nom
  localStorage.setItem('smartintern_nom',    data.lastName  || data.nom    || '');
  localStorage.setItem('smartintern_prenom', data.firstName || data.prenom || '');
  localStorage.setItem('smartintern_userId', data.id);
  if (data.firstLogin !== undefined) {
    localStorage.setItem('smartintern_firstLogin', data.firstLogin ? 'true' : 'false');
  }
}

function isFirstLogin() {
  return localStorage.getItem('smartintern_firstLogin') === 'true';
}

function getToken()   { return localStorage.getItem(TOKEN_KEY);  }
function getRole()    { return localStorage.getItem(ROLE_KEY);   }
function getNom()     { return localStorage.getItem(NOM_KEY);    }
function getPrenom()  { return localStorage.getItem(PRENOM_KEY); }
function getUserId()  { return localStorage.getItem(ID_KEY);     }
function isLoggedIn() { return !!getToken(); }

function logout() {
  localStorage.clear();
  window.location.href = 'login.html';
}

function requireAuth() {
  if (!isLoggedIn()) {
    window.location.href = '../pages/login.html';
  }
}


function redirectByRole(role) {
  // Rediriger vers la page de premier login si applicable
  if (isFirstLogin()) {
    localStorage.setItem('smartintern_firstLogin', 'false');
    const currentPath = window.location.pathname;
    window.location.href = currentPath.includes('/pages/')
      ? 'first-login.html'
      : 'pages/first-login.html';
    return;
  }

  const routes = {
    'ROLE_ETUDIANT':             'etudiant-dashboard.html',
    'ROLE_ENTREPRISE':           'entreprise-dashboard.html',
    'ROLE_ADMIN':                'admin-dashboard.html',
    'ROLE_ENCADRANT_ENTREPRISE': 'encadrant-entreprise-dashboard.html',
    'ROLE_ENCADRANT_ACADEMIQUE': 'encadrant-academique-dashboard.html',
  };

  // Normalise : accepte "ADMIN" ou "ROLE_ADMIN"
  const normalized = role
    ? (role.startsWith('ROLE_') ? role : 'ROLE_' + role)
    : '';

  const page = routes[normalized];
  if (!page) {
    console.warn('redirectByRole: rôle inconnu →', role);
    window.location.href = 'login.html';
    return;
  }

  const currentPath = window.location.pathname;
  if (currentPath.includes('/pages/')) {
    window.location.href = page;
  } else {
    window.location.href = 'pages/' + page;
  }
}