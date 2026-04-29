# SmartIntern AI — Documentation Technique

**Projet :** Plateforme de gestion intelligente des stages universitaires  
**Établissement :** ITEAM University  
**Type :** Projet de Fin d'Année (PFA)  
**Stack :** Java Spring Boot 3.2.5 + Python FastAPI + HTML/CSS/JS + MySQL  
**Dépôt :** https://github.com/AnoNymos-24/PFA.git

---

## Table des matières

1. [Présentation Générale](#1-présentation-générale)
2. [Architecture](#2-architecture)
3. [Démarrage Rapide](#3-démarrage-rapide)
4. [API Reference](#4-api-reference)
5. [Frontend — Pages & Fonctions](#5-frontend--pages--fonctions)
6. [État d'Implémentation](#6-état-dimplémentation)

---

## 1. Présentation Générale

SmartIntern AI centralise et automatise l'ensemble du cycle de vie d'un stage : de la recherche d'offre jusqu'à la génération des documents officiels, en passant par la candidature, le suivi et l'évaluation.

### Acteurs

| Acteur | Rôle | Endpoint prefix |
|--------|------|----------------|
| **Étudiant** | Profil, CV, candidatures, stages, documents | `/api/etudiant/` |
| **Entreprise** | Offres, candidatures, stagiaires | `/api/entreprise/` |
| **Admin** | Validation, stats, modèles de documents | `/api/admin/` |
| **EncadrantAcademique** | Suivi académique | `/api/encadrant-academique/` |
| **EncadrantEntreprise** | Suivi entreprise | `/api/encadrant-entreprise/` |

---

## 2. Architecture

```
smartinternIA2/
├── backend/                    # Spring Boot 3.2.5 (port 8081)
│   ├── src/main/java/com/smartintern/backend/
│   │   ├── config/             # AppConfig (@EnableAsync, CORS, RestTemplate, Executor)
│   │   │                       # SecurityConfig (JWT stateless, BCrypt)
│   │   ├── controller/         # AuthController, OtpController, CvController,
│   │   │                       # OffreStageController, CandidatureController,
│   │   │                       # StageController, DocumentController
│   │   ├── dto/                # AuthDto, OffreStageDto, CandidatureDto, StageDto,
│   │   │                       # ModeleDocumentDto
│   │   ├── entity/             # User, Etudiant, Entreprise, OffreStage, Candidature,
│   │   │                       # Stage, CvStandardise, Profil, Experience, Formation,
│   │   │                       # Competence, Langue, Document, DocumentGenere,
│   │   │                       # ModeleDocument, TypeDocument
│   │   ├── repository/         # Spring Data JPA repositories
│   │   ├── security/           # JwtUtils, JwtFilter, UserDetailsService
│   │   └── service/            # AuthService, CvExtractionService, AsyncCvService,
│   │                           # CvStandardiseService, OffreStageService,
│   │                           # CandidatureService, StageService,
│   │                           # DocumentService, ModeleDocumentService, EmailService
│   └── src/main/resources/
│       └── application.properties
├── smartintern-cv-service/     # Python FastAPI (port 8000)
│   ├── main.py                 # Endpoints: /extract-cv, /modeles/creer, /documents/generer
│   └── .env                    # OPENROUTER_API_KEY, OPENROUTER_MODEL
└── frontend/                   # HTML/CSS/JS vanilla
    ├── js/
    │   ├── auth.js             # Session, JWT, redirectByRole
    │   └── api.js              # Toutes les fonctions fetch vers le backend
    └── pages/
        ├── login.html
        ├── register.html
        ├── verify-email.html
        ├── first-login.html
        ├── etudiant-dashboard.html
        ├── entreprise-dashboard.html
        ├── admin-dashboard.html
        ├── encadrant-academique-dashboard.html
        └── encadrant-entreprise-dashboard.html
```

### Flux CV (asynchrone)

```
POST /api/etudiant/cv  →  202 Accepted  →  extraction IA en background
                                         ↓
GET /api/etudiant/cv/statut  →  {statut: "EN_COURS" | "EXTRAIT" | "ERREUR"}
                                         ↓ (poll toutes les 3s)
GET /api/etudiant/cv  →  {hasCv, filename, statutExtraction, cvData, scoreCompletude}
```

### Variables d'environnement requises

```bash
# backend/.env
JWT_SECRET=<secret HS256 min 32 chars>
DB_PASSWORD=<mysql password>
GMAIL_APP_PASSWORD=<google app password>
CV_SERVICE_URL=http://localhost:8000

# smartintern-cv-service/.env
OPENROUTER_API_KEY=<your key>
OPENROUTER_MODEL=anthropic/claude-sonnet-4-5
```

---

## 3. Démarrage Rapide

```bash
# 1. Base de données
mysql -u root -p < schema.sql

# 2. Microservice Python (port 8000)
cd smartintern-cv-service
pip install -r requirements.txt
cp env.example .env   # remplir les clés
uvicorn main:app --reload

# 3. Backend Spring Boot (port 8081)
cd backend
cp .env.example .env  # remplir les secrets
mvn spring-boot:run

# 4. Frontend
# Ouvrir frontend/pages/login.html dans un navigateur
# ou utiliser Live Server (VS Code)
```

---

## 4. API Reference

### Auth (`/api/auth/`)

| Méthode | Endpoint | Body | Auth | Description |
|---------|----------|------|------|-------------|
| POST | `/register` | `{firstName, lastName, email, password, role, ...extra}` | — | Inscription |
| POST | `/login` | `{email, password}` | — | Connexion → `{token, id, firstName, lastName, role, statut}` |
| POST | `/verify-otp` | `{email, code}` | — | Vérification OTP → `{token, id, firstName, lastName, role, statut}` |
| POST | `/resend-otp` | `{email}` | — | Renvoyer code OTP |
| POST | `/forgot-password` | `{email}` | — | Demande reset mot de passe |
| POST | `/reset-password` | `{email, code, newPassword}` | — | Reset mot de passe |

> **Note :** La réponse de `/verify-otp` inclut le token JWT. Le frontend appelle `saveSession(data)` pour persister la session.

> **Champs de réponse :** `firstName` (prénom), `lastName` (nom), `role` sans préfixe (`"ETUDIANT"`, `"ENTREPRISE"`, etc.). `auth.js` normalise automatiquement en `ROLE_*`.

### Utilisateur (`/api/users/`)

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/me` | JWT | Profil courant |
| PUT | `/me` | JWT | Mise à jour profil |
| PUT | `/change-password` | JWT | `{oldPassword, newPassword}` |

### Offres de Stage

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/api/etudiant/offres` | JWT | Liste paginée (`?page=0&size=20&sortBy=datePublication&sortDir=desc`) |
| GET | `/api/etudiant/offres/search` | JWT | Recherche (`?domaine=&localisation=&typeStage=&niveauRequis=`) |
| GET | `/api/entreprise/offres` | JWT | Offres de mon entreprise |
| POST | `/api/entreprise/offres` | JWT | Créer une offre |
| PUT | `/api/entreprise/offres/{id}` | JWT | Modifier une offre |
| DELETE | `/api/entreprise/offres/{id}` | JWT | Supprimer une offre |
| GET | `/api/admin/offres/en-attente` | JWT | Offres en attente de validation |
| PATCH | `/api/admin/offres/{id}/valider` | JWT | `{approuve: true/false}` |

### CV (`/api/etudiant/cv`)

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| POST | `/api/etudiant/cv` | JWT | Upload PDF (multipart `file`) → **202** + extraction async |
| GET | `/api/etudiant/cv` | JWT | `{hasCv, filename, statutExtraction, cvData, scoreCompletude}` |
| GET | `/api/etudiant/cv/statut` | JWT | `{statut: "EN_COURS"\|"EXTRAIT"\|"ERREUR", pret: bool}` |
| POST | `/api/etudiant/cv/reanalyse` | JWT | Ré-analyse du CV existant → **202** |

### Candidatures

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| POST | `/api/etudiant/candidatures` | JWT | `{offreId, lettreMotivation}` |
| GET | `/api/etudiant/candidatures` | JWT | Mes candidatures |
| GET | `/api/entreprise/offres/{offreId}/candidatures` | JWT | Candidatures d'une offre |
| PATCH | `/api/entreprise/candidatures/{id}/decision` | JWT | `{statut, commentaire}` |

### Stages

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/api/etudiant/stages` | JWT | Mes stages |
| GET | `/api/encadrant-academique/stages` | JWT | Stages encadrés académiquement |
| GET | `/api/encadrant-entreprise/stages` | JWT | Stages encadrés côté entreprise |
| POST | `/api/admin/stages/depuis-candidature/{candidatureId}` | JWT | Créer stage depuis candidature acceptée |
| PATCH | `/api/admin/stages/{id}/encadrants` | JWT | Assigner les encadrants |

### Documents

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/api/etudiant/documents` | JWT | Mes documents générés |
| POST | `/api/documents/generer` | JWT | `{modeleId, donneesProfil, nomEtablissement}` |
| POST | `/api/documents/{uuid}/regenerer` | JWT | `{donneesProfil, nomEtablissement}` |
| GET | `/api/documents/{uuid}/telecharger` | JWT | Télécharger PDF |
| GET | `/api/documents/{uuid}/verifier` | — | Vérification d'authenticité (public) |

### Modèles (Admin)

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/api/admin/modeles` | JWT | `?statut=ACTIF\|ARCHIVE` (optionnel) |
| POST | `/api/admin/modeles` | JWT | Multipart: `request` + `fichierModele` + `fichierHeader?` + `fichierFooter?` |
| GET | `/api/admin/modeles/{id}` | JWT | Détail modèle |
| PATCH | `/api/admin/modeles/{id}` | JWT | Mise à jour |
| DELETE | `/api/admin/modeles/{id}` | JWT | Archiver |
| GET | `/api/admin/types-documents` | JWT | Liste des types |
| POST | `/api/admin/types-documents` | JWT | `{nom, code, description}` |

### Administration

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/api/admin/stats` | JWT | Statistiques globales |
| GET | `/api/admin/users` | JWT | Tous les utilisateurs |
| GET | `/api/admin/users/role/{role}` | JWT | Par rôle |
| PUT | `/api/admin/users/{id}/validate` | JWT | Valider |
| PUT | `/api/admin/users/{id}/disable` | JWT | Désactiver |
| PUT | `/api/admin/users/{id}/enable` | JWT | Activer |
| GET | `/api/admin/etudiants` | JWT | Tous les étudiants |
| GET | `/api/admin/etudiants/risque` | JWT | Étudiants à risque (IA) |
| GET | `/api/admin/entreprises` | JWT | Toutes les entreprises |
| PUT | `/api/admin/entreprises/{id}/validate` | JWT | Valider une entreprise |
| GET | `/api/admin/stages` | JWT | Tous les stages |

---

## 5. Frontend — Pages & Fonctions

### `js/auth.js`

| Fonction | Description |
|----------|-------------|
| `saveSession(data)` | Persiste token, rôle normalisé (`ROLE_*`), nom/prénom (depuis `firstName`/`lastName`), id |
| `getToken/getRole/getNom/getPrenom/getUserId` | Lecture localStorage |
| `isLoggedIn()` | Vérifie présence du token |
| `logout()` | Vide localStorage → login.html |
| `requireAuth()` | Redirige vers login si non connecté |
| `redirectByRole(role)` | Redirige vers le dashboard selon le rôle (accepte `"ETUDIANT"` ou `"ROLE_ETUDIANT"`) |

### `js/api.js` — Fonctions principales

```javascript
// Auth
apiLogin(email, password)
apiRegister(firstName, lastName, email, password, role, extra)
apiVerifyOtp(email, code)      // alias: apiVerifyEmail
apiResendOtp(email)             // alias: apiResendCode
apiForgotPassword(email)
apiResetPassword(email, code, newPassword)
apiChangePassword(oldPassword, newPassword)

// CV
apiUploadCv(file)               // → 202, puis poll statut
apiGetCvInfo()                  // {hasCv, filename, statutExtraction, scoreCompletude}
apiGetCvStatut()                // {statut: "EN_COURS"|"EXTRAIT"|"ERREUR", pret}
apiReanalyseCv()                // → 202

// Offres
apiGetOffres({page, size, sortBy, sortDir})   // étudiant
apiRechercherOffres({domaine, localisation, typeStage, niveauRequis})
apiMesOffres()                  // entreprise
apiPublierOffre(data)
apiModifierOffre(id, data)
apiSupprimerOffre(id)
apiCloturerOffre(id)

// Candidatures
apiPostuler(offreId, lettreMotivation)
apiGetCandidatures()            // étudiant
apiGetCandidaturesParOffre(offreId)  // entreprise, par offre
apiGetCandidaturesRecues()      // entreprise, toutes (agrégation frontend)
apiDeciderCandidature(id, statut, commentaire)

// Stages
apiGetStage()                   // étudiant
apiGetStagesEncadrantAcademique()
apiGetStagesEncadrantEntreprise()

// Documents
apiGetMesDocuments()
apiGenererDocument(modeleId, donneesProfil, nomEtablissement)
apiTelechargerDocument(uuid)    // → Blob
apiVerifierDocument(uuid)       // public

// Admin
apiGetAdminStats()
apiGetAllUsers() / apiGetUsersByRole(role)
apiValidateUser(id) / apiDisableUser(id) / apiEnableUser(id)
apiGetOffresEnAttente() / apiValiderOffre(id, approuve)
apiGetAllEtudiants() / apiGetEtudiantsARisque()
apiGetAllEntreprises() / apiValidateEntreprise(id)
apiCreerStageDepuisCandidature(candidatureId)
apiGetModeles() / apiCreerModele(formData) / apiUpdateModele(id, data)
```

---

## 6. État d'Implémentation

### Backend ✅ Implémenté

- Authentification JWT + OTP email (inscription, connexion, vérification, reset)
- Gestion des offres de stage (CRUD entreprise, listing paginé étudiant, validation admin)
- Upload CV + extraction IA **asynchrone** (ThreadPoolTaskExecutor, @Async, polling statut)
- Structuration CV en base (CvStandardise, Profil, Experience, Formation, Competence, Langue)
- Candidatures (postuler, décision entreprise)
- Stages (création depuis candidature, assignation encadrants)
- Génération documents via microservice Python (conventions, attestations)
- Gestion des modèles de documents (admin)
- Secrets externalisés via variables d'environnement

### Microservice Python ✅ Implémenté

- Extraction CV (OpenRouter / Claude Sonnet) : `/extract-cv`
- Création modèles : `/modeles/creer`
- Génération documents : `/documents/generer`, `/documents/regenerer`

### Frontend ✅ Connecté

- `auth.js` : mapping correct `firstName`/`lastName`/`id` → localStorage, normalisation rôles
- `api.js` : tous les endpoints corrigés avec les bons prefixes de rôle
- `verify-email.html` : appelle `saveSession()` après vérification OTP
- `register.html` : envoie `firstName`/`lastName` dans l'ordre correct
- `etudiant-dashboard.html` : upload CV async + polling statut

### Non encore implémenté

- Rapports de stage (RapportController manquant côté backend)
- Endpoint entreprise pour lister tous ses stagiaires (actuellement stub)
- Notifications en temps réel (retournent liste vide)
- Signing de conventions (endpoints frontend commentés)
- Dashboard statistiques admin (endpoint `/admin/stats` non implémenté)
