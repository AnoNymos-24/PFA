const TOKEN_KEY  = 'smartintern_token';
const ROLE_KEY   = 'smartintern_role';
const NOM_KEY    = 'smartintern_nom';
const PRENOM_KEY = 'smartintern_prenom';
const ID_KEY     = 'smartintern_userId';

function saveSession(data) {
  localStorage.setItem('smartintern_token',      data.token);
  localStorage.setItem('smartintern_role',       data.role);
  localStorage.setItem('smartintern_nom',        data.nom);
  localStorage.setItem('smartintern_prenom',     data.prenom);
  localStorage.setItem('smartintern_userId',     data.id);
  localStorage.setItem('smartintern_firstLogin', data.firstLogin);
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
  const routes = {
    'ROLE_ETUDIANT':             'etudiant-dashboard.html',
    'ROLE_ENTREPRISE':           'entreprise-dashboard.html',
    'ROLE_ADMIN':                'admin-dashboard.html',
    'ROLE_ENCADRANT_ENTREPRISE': 'encadrant-entreprise-dashboard.html',
    'ROLE_ENCADRANT_ACADEMIQUE': 'encadrant-academique-dashboard.html',
  };

  const page = routes[role];
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