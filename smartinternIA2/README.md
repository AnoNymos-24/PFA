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
├── backend/                         # Spring Boot 3.2.5 (port 8081)
│   ├── src/main/java/com/smartintern/backend/
│   │   ├── config/                  # AppConfig (RestTemplate, CORS, Executor)
│   │   │                            # SecurityConfig (JWT stateless, BCrypt)
│   │   ├── controller/              # AuthController, CvController,
│   │   │                            # OffreStageController, CandidatureController,
│   │   │                            # StageController, DocumentController
│   │   ├── dto/                     # AuthDto, OffreStageDto, CandidatureDto,
│   │   │                            # StageDto, ModeleDocumentDto, DocumentDto
│   │   ├── entity/                  # User, Etudiant, Entreprise, Etablissement,
│   │   │                            # OffreStage, Candidature, Stage, CvStandardise,
│   │   │                            # Document, DocumentGenere, ModeleDocument, TypeDocument
│   │   ├── repository/              # Spring Data JPA repositories
│   │   ├── security/                # JwtUtils, JwtAuthFilter, UserDetailsServiceImpl
│   │   └── service/                 # AuthService, CvExtractionService, AsyncCvService,
│   │                                # DocumentService, ModeleDocumentService,
│   │                                # DocumentExpirationTask (@Scheduled), EmailService
│   └── src/main/resources/
│       └── application.properties
├── smartintern-ai-service/          # Python FastAPI (port 8000)
│   ├── main.py                      # /health + montage des routers
│   ├── app/modules/
│   │   ├── cv_extraction/           # /cv/extract, /cv/extract/text-only
│   │   ├── document_templates/      # /modeles/*
│   │   └── document_generation/     # /documents/*
│   ├── test-interface.html          # Interface de test microservice
│   └── .env                         # Clés API (NVIDIA_API_KEY, OPENROUTER_API_KEY)
├── test-interface/
│   └── index.html                   # Interface de test full-stack (ports 8081+8000)
└── frontend/                        # HTML/CSS/JS vanilla
    ├── js/
    │   ├── auth.js                  # Session, JWT, redirectByRole
    │   └── api.js                   # Toutes les fonctions fetch vers le backend
    └── pages/
        ├── login.html
        ├── register.html
        ├── verify-email.html
        ├── etudiant-dashboard.html
        ├── entreprise-dashboard.html
        └── admin-dashboard.html
```

### Flux CV (asynchrone)

```
POST /api/etudiant/cv  →  202 Accepted  →  extraction IA en background (@Async)
                                         ↓
GET /api/etudiant/cv/statut  →  {statut: "EN_COURS" | "EXTRAIT" | "ERREUR"}
                                         ↓ (poll toutes les 3s)
GET /api/etudiant/cv  →  {hasCv, filename, statutExtraction, cvData, scoreCompletude}
```

### Flux Génération de documents (nouveau — avec QR auth)

```
POST /api/etudiant/documents/generer (ou /api/admin/documents/generer)
  1. Spring Boot pré-génère docUuid = UUID.randomUUID()
  2. authUrl = "{app.base.url}/api/documents/{docUuid}/authentifier"
  3. Spring Boot appelle le microservice :
     POST /documents/generer { id_modele_document, data{}, qr_data=authUrl, doc_id=docUuid }
  4. Microservice remplit le template Word, insère QR dans la zone rouge
  5. Spring Boot persiste Document + DocumentGenere en MySQL
  6. Réponse : { docUuid, urlAuthentification, urlTelechargement }

Scan QR code → GET /api/documents/{uuid}/authentifier (PUBLIC)
  → Retourne : type, dates, matricule, nom, établissement, logo, statut
  → HTTP 410 si expiré / HTTP 404 si révoqué

Expiration automatique :
  DocumentExpirationTask (@Scheduled cron="0 0 * * * *")
  → Toutes les heures, passe les documents échus au statut EXPIRE
```

### Variables d'environnement requises

```bash
# backend/.env (ou application.properties)
JWT_SECRET=<secret HS256 min 32 chars>
DB_PASSWORD=<mysql password>
GMAIL_APP_PASSWORD=<google app password>
CV_SERVICE_URL=http://localhost:8000
APP_BASE_URL=http://localhost:8081        # URL publique pour les liens d'auth QR

# smartintern-ai-service/.env
NVIDIA_API_KEY=nvapi-...                  # Provider IA primaire
OPENROUTER_API_KEY=sk-or-...             # Provider IA fallback
SIGNATURE_SECRET=ChangezCetteValeurEnProduction
```

---

## 3. Démarrage Rapide

```bash
# 1. Base de données
mysql -u root -p < schema.sql

# 2. Microservice Python (port 8000)
cd smartintern-ai-service
pip install -r requirements.txt
cp .env.example .env   # remplir NVIDIA_API_KEY ou OPENROUTER_API_KEY
uvicorn main:app --reload --port 8000

# 3. Backend Spring Boot (port 8081)
cd backend
# remplir application.properties (db, jwt, mail, cv.service.url, app.base.url)
mvn spring-boot:run

# 4. Interface de test
# Microservice seul : ouvrir smartintern-ai-service/test-interface.html
# Full-stack       : ouvrir test-interface/index.html
# Frontend réel    : ouvrir frontend/pages/login.html
```

---

## 4. API Reference

### Auth (`/api/auth/`)

| Méthode | Endpoint | Body | Auth | Description |
|---------|----------|------|------|-------------|
| POST | `/register` | `{firstName, lastName, email, password, role, ...extra}` | — | Inscription |
| POST | `/login` | `{email, password}` | — | Connexion → `{token, id, firstName, lastName, role, statut}` |
| POST | `/verify-otp` | `{email, code}` | — | Vérification OTP → `{token, ...}` |
| POST | `/resend-otp` | `{email}` | — | Renvoyer code OTP |
| POST | `/forgot-password` | `{email}` | — | Demande reset mot de passe |
| POST | `/reset-password` | `{email, code, newPassword}` | — | Reset mot de passe |

### CV (`/api/etudiant/cv`)

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| POST | `/api/etudiant/cv` | JWT | Upload PDF (multipart `file`) → **202** + extraction async |
| GET | `/api/etudiant/cv` | JWT | `{hasCv, filename, statutExtraction, cvData, scoreCompletude}` |
| GET | `/api/etudiant/cv/statut` | JWT | `{statut: "EN_COURS"\|"EXTRAIT"\|"ERREUR", pret: bool}` |
| POST | `/api/etudiant/cv/reanalyse` | JWT | Ré-analyse du CV existant → **202** |

### Offres de Stage

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/api/etudiant/offres` | JWT | Liste paginée |
| GET | `/api/etudiant/offres/search` | JWT | Recherche (`?domaine=&localisation=&typeStage=`) |
| GET | `/api/entreprise/offres` | JWT | Offres de mon entreprise |
| POST | `/api/entreprise/offres` | JWT | Créer une offre |
| PUT | `/api/entreprise/offres/{id}` | JWT | Modifier une offre |
| DELETE | `/api/entreprise/offres/{id}` | JWT | Supprimer une offre |
| GET | `/api/admin/offres/en-attente` | JWT | Offres en attente de validation |
| PATCH | `/api/admin/offres/{id}/valider` | JWT | `{approuve: true/false}` |

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
| POST | `/api/admin/stages/depuis-candidature/{candidatureId}` | JWT | Créer stage depuis candidature |
| PATCH | `/api/admin/stages/{id}/encadrants` | JWT | Assigner les encadrants |

### Documents

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| POST | `/api/etudiant/documents/generer` | JWT (ETUDIANT) | Génère son propre document (unicité par type) |
| POST | `/api/admin/documents/generer` | JWT (ADMIN) | Génère pour n'importe quel utilisateur (sans limite) |
| GET | `/api/etudiant/documents` | JWT | Historique de ses documents générés |
| GET | `/api/documents/{uuid}/authentifier` | **PUBLIC** | Page d'authentification QR (→ 410 si expiré, 404 si révoqué) |
| GET | `/api/documents/{uuid}/telecharger` | JWT | Télécharger le fichier DOCX ou PDF |

#### Corps — `POST /api/etudiant/documents/generer`
```json
{
  "typeDocumentId": 1,
  "donneesSupplementaires": { "date_debut": "2025-06-01", "sujet": "Dev web" },
  "outputFormat": "docx"
}
```
> Le `userId` est ignoré (auto-résolu depuis le JWT). Contrainte : un étudiant ne peut générer qu'une fois par type de document.

#### Corps — `POST /api/admin/documents/generer`
```json
{
  "typeDocumentId": 1,
  "userId": 42,
  "donneesSupplementaires": { "sujet": "Stage en IA" },
  "outputFormat": "docx"
}
```

#### Réponse `DocumentGenerationResponse`
```json
{
  "success": true,
  "docUuid": "550e8400-e29b-41d4-a716-446655440000",
  "urlTelechargement": "/api/documents/550e8400-.../telecharger",
  "urlAuthentification": "http://localhost:8081/api/documents/550e8400-.../authentifier",
  "typeDocument": "CONVENTION_STAGE",
  "format": "docx",
  "statut": "VALIDE",
  "dateCreation": "2025-06-01T10:00:00",
  "dateExpiration": "2026-06-01T10:00:00",
  "tailleOctets": 54321,
  "message": "Document généré avec succès"
}
```

#### Réponse `GET /api/documents/{uuid}/authentifier` (PUBLIC)
```json
{
  "docUuid": "550e8400-...",
  "typeDocument": "CONVENTION_STAGE",
  "nomTypeDocument": "Convention de stage",
  "dateCreation": "2025-06-01T10:00:00",
  "dateExpiration": "2026-06-01T10:00:00",
  "matricule": "ETU2025001",
  "nomComplet": "Jean Dupont",
  "email": "jean@etudiant.fr",
  "nomEtablissement": "ITEAM University",
  "logoEtablissement": "https://...",
  "statut": "VALIDE",
  "valide": true,
  "expire": false
}
```

### Modèles de documents (Admin)

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/api/admin/modeles` | JWT | `?statut=ACTIF\|ARCHIVE` (optionnel) |
| POST | `/api/admin/modeles` | JWT | Multipart: `request` (JSON part) + `fichierModele` (.docx) |
| GET | `/api/admin/modeles/{id}` | JWT | Détail modèle |
| PATCH | `/api/admin/modeles/{id}` | JWT | Mise à jour (nom, durée validité, dateExpiration) |
| DELETE | `/api/admin/modeles/{id}` | JWT | Archiver le modèle |
| GET | `/api/admin/types-documents` | JWT | Liste des types de documents |
| POST | `/api/admin/types-documents` | JWT | `{nom, code, description}` |
| GET | `/api/admin/modeles/{id}/documents` | JWT | Documents générés depuis ce modèle |

> **Important :** La création d'un modèle (`POST /api/admin/modeles`) appelle automatiquement le microservice `POST /modeles/` pour analyser le fichier .docx et détecter les champs `[champ]` et la zone QR rouge. Le `id_modele_document` retourné est stocké dans `ModeleDocument.idMicroservice`.

### Administration

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/api/admin/stats` | JWT | Statistiques globales |
| GET | `/api/admin/users` | JWT | Tous les utilisateurs |
| GET | `/api/admin/users/role/{role}` | JWT | Par rôle |
| PUT | `/api/admin/users/{id}/validate` | JWT | Valider |
| PUT | `/api/admin/users/{id}/disable` | JWT | Désactiver |
| GET | `/api/admin/etudiants` | JWT | Tous les étudiants |
| GET | `/api/admin/entreprises` | JWT | Toutes les entreprises |
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
| `redirectByRole(role)` | Redirige vers le dashboard selon le rôle |

### `js/api.js` — Fonctions principales

```javascript
// Auth
apiLogin(email, password)
apiRegister(firstName, lastName, email, password, role, extra)
apiVerifyOtp(email, code)
apiResendOtp(email)
apiForgotPassword(email)
apiResetPassword(email, code, newPassword)
apiChangePassword(oldPassword, newPassword)

// CV
apiUploadCv(file)              // → 202, puis poll statut
apiGetCvInfo()                 // {hasCv, filename, statutExtraction, scoreCompletude}
apiGetCvStatut()               // {statut: "EN_COURS"|"EXTRAIT"|"ERREUR", pret}
apiReanalyseCv()               // → 202

// Offres
apiGetOffres({page, size, sortBy, sortDir})
apiRechercherOffres({domaine, localisation, typeStage, niveauRequis})
apiMesOffres()                 // entreprise
apiPublierOffre(data)
apiModifierOffre(id, data)
apiSupprimerOffre(id)

// Candidatures
apiPostuler(offreId, lettreMotivation)
apiGetCandidatures()           // étudiant
apiGetCandidaturesParOffre(offreId)
apiDeciderCandidature(id, statut, commentaire)

// Stages
apiGetStage()                  // étudiant
apiGetStagesEncadrantAcademique()
apiGetStagesEncadrantEntreprise()

// Documents
apiGetMesDocuments()
apiGenererDocument(typeDocumentId, donneesSupplementaires, outputFormat)
apiTelechargerDocument(uuid)   // → Blob téléchargement
apiAuthentifierDocument(uuid)  // public — page d'auth QR

// Admin — Modèles
apiGetModeles()
apiCreerModele(formData)       // multipart: request + fichierModele
apiUpdateModele(id, data)
apiArchiverModele(id)
apiGetTypesDocuments()
```

---

## 6. État d'Implémentation

### Backend ✅ Implémenté

- Authentification JWT + OTP email (inscription, connexion, vérification, reset)
- Upload CV + extraction IA **asynchrone** (`@Async`, `@EnableAsync`, polling statut)
- Structuration CV en base (CvStandardise, Profil, Experience, Formation, Competence, Langue)
- Offres de stage (CRUD entreprise, listing paginé étudiant, validation admin)
- Candidatures (postuler, décision entreprise)
- Stages (création depuis candidature, assignation encadrants)
- **Génération documents** avec QR auth (unicité étudiant, admin illimité)
  - Pré-génération UUID côté Spring Boot → cohérence avec nom de fichier microservice
  - Lien public `{baseUrl}/api/documents/{uuid}/authentifier` encodé dans le QR
  - Retourne type, dates, matricule, nom, établissement, logo
  - HTTP 410 si expiré, HTTP 404 si révoqué
- **Expiration automatique** (`DocumentExpirationTask`, `@Scheduled`, `@EnableScheduling`)
- Gestion des modèles (admin) — délègue au microservice `POST /modeles/`
- Secrets externalisés via variables d'environnement

### Microservice Python ✅ Implémenté

- Extraction CV (NVIDIA NIM + OpenRouter fallback) : `POST /cv/extract`
- Gestion modèles Word : `POST /modeles/`, `GET /modeles/`, `GET /modeles/{id}`, `DELETE /modeles/{id}`
- Génération documents : `POST /documents/generer` (python-docx, QR insertion zone rouge, HMAC)
- Téléchargement : `GET /documents/telecharger/{filename}`
- Vérification : `GET /documents/verifier/{uuid}` (HMAC legacy)

### Frontend ✅ Connecté

- `auth.js` : mapping correct `firstName`/`lastName`/`id` → localStorage, normalisation rôles
- `api.js` : tous les endpoints corrigés avec les bons préfixes de rôle
- `verify-email.html` : appelle `saveSession()` après vérification OTP
- `etudiant-dashboard.html` : upload CV async + polling statut

### Non encore implémenté

- Rapports de stage (controller manquant)
- Notifications en temps réel (retournent liste vide)
- Dashboard statistiques admin (`/admin/stats` non implémenté)
- Signing de conventions (endpoints frontend commentés)
- Endpoint entreprise pour lister tous ses stagiaires (stub)
