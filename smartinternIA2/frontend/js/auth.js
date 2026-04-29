const TOKEN_KEY  = 'smartintern_token';
const ROLE_KEY   = 'smartintern_role';
const NOM_KEY    = 'smartintern_nom';
const PRENOM_KEY = 'smartintern_prenom';
const ID_KEY     = 'smartintern_userId';

function saveSession(data) {
  localStorage.setItem(TOKEN_KEY,  data.token);
  // Backend renvoie role sans préfixe (ex: "ETUDIANT"), on normalise avec ROLE_
  const role = data.role
    ? (data.role.startsWith('ROLE_') ? data.role : 'ROLE_' + data.role)
    : '';
  localStorage.setItem(ROLE_KEY,   role);
  // Backend renvoie firstName/lastName, on stocke sous nom/prenom
  localStorage.setItem(NOM_KEY,    data.lastName  || data.nom    || '');
  localStorage.setItem(PRENOM_KEY, data.firstName || data.prenom || '');
  localStorage.setItem(ID_KEY,     data.id        || data.userId || '');
  localStorage.setItem('smartintern_firstLogin', data.firstLogin || 'false');
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

function isFirstLogin() {
  return localStorage.getItem('smartintern_firstLogin') === 'true';
}

function redirectByRole(role) {
  // Normalise le rôle (backend peut renvoyer "ETUDIANT" ou "ROLE_ETUDIANT")
  const normalized = role && !role.startsWith('ROLE_') ? 'ROLE_' + role : role;

  const routes = {
    'ROLE_ETUDIANT':             'etudiant-dashboard.html',
    'ROLE_ENTREPRISE':           'entreprise-dashboard.html',
    'ROLE_ADMIN':                'admin-dashboard.html',
    'ROLE_ENCADRANT_ENTREPRISE': 'encadrant-entreprise-dashboard.html',
    'ROLE_ENCADRANT_ACADEMIQUE': 'encadrant-academique-dashboard.html',
  };

  const page = routes[normalized];
  if (!page) {
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