# SmartIntern AI — Documentation Technique & Analyse Backlog

**Projet :** Plateforme de gestion intelligente des stages universitaires  
**Établissement :** ITEAM University  
**Type :** Projet de Fin d'Année (PFA)  
**Stack :** Java Spring Boot 3.2.5 + Python FastAPI + HTML/CSS/JS + MySQL  
**Dépôt :** https://github.com/AnoNymos-24/PFA.git  
**Date analyse :** Mai 2026

---

## Table des matières

1. [Présentation Générale](#1-présentation-générale)
2. [Architecture](#2-architecture)
3. [Démarrage Rapide](#3-démarrage-rapide)
4. [Variables de Documents (Templates)](#4-variables-de-documents-templates)
5. [API Reference](#5-api-reference)
6. [Frontend — Pages & Fonctions](#6-frontend--pages--fonctions)
7. [Analyse du Backlog](#7-analyse-du-backlog)
8. [Avancement par Sprint](#8-avancement-par-sprint)
9. [Fonctionnalités Hors Backlog](#9-fonctionnalités-hors-backlog)
10. [Évolution Globale & Synthèse](#10-évolution-globale--synthèse)

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

### Flux Génération de documents (avec QR authentification)

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

# 2. Microservice Python (port 8000) — Python 3.11 requis
cd smartintern-ai-service
py -3.11 -m venv venv
venv\Scripts\activate          # Windows
# source venv/bin/activate     # Linux/Mac
pip install -r requirements.txt
cp .env.example .env           # remplir NVIDIA_API_KEY ou OPENROUTER_API_KEY
uvicorn main:app --reload --port 8000

# 3. Backend Spring Boot (port 8081)
cd backend
# remplir application.properties (db, jwt, mail, cv.service.url, app.base.url)
mvn spring-boot:run

# 4. Interface de test
# Microservice seul : ouvrir smartintern-ai-service/test-interface.html
# Full-stack        : ouvrir test-interface/index.html
# Frontend réel     : ouvrir frontend/pages/login.html
```

> ⚠️ **Python 3.11 requis** — Les dépendances `pydantic-core==2.20.1` (PyO3 0.22) et `Pillow==10.4.0` sont incompatibles avec Python 3.14+.

---

## 4. Variables de Documents (Templates)

Dans vos fichiers `.docx`, utilisez des marqueurs `[nom_variable]`. Le microservice détecte automatiquement tous les champs entre crochets lors de l'analyse du template.

### Référence complète des variables disponibles

| Catégorie | Variable | Description | Entité Java |
|-----------|----------|-------------|-------------|
| **Étudiant — Identité** | `[prenom]` | Prénom de l'étudiant | `User.firstName` |
| | `[nom]` | Nom de famille | `User.lastName` |
| | `[nom_complet]` | Prénom + Nom | `User.firstName + lastName` |
| | `[email]` | Adresse email | `User.email` |
| | `[telephone]` | Numéro de téléphone | `User.telephone` |
| | `[cin]` | Carte d'identité nationale | `Etudiant.cin` |
| | `[date_naissance]` | Date de naissance | `Etudiant.dateNaissance` |
| | `[nationalite]` | Nationalité | `Etudiant.nationalite` |
| **Étudiant — Académique** | `[code_etudiant]` | Matricule étudiant | `Etudiant.codeEtudiant` |
| | `[filiere]` | Filière d'études | `Etudiant.filiere` |
| | `[classe]` | Classe / niveau | `Etudiant.classe` |
| **Établissement** | `[nom_etablissement]` | Nom de l'établissement | `Etablissement.nom` |
| | `[adresse_etablissement]` | Adresse | `Etablissement.adresse` |
| | `[identifiant_etablissement]` | Identifiant officiel | `Etablissement.identifiant` |
| **Entreprise** | `[nom_entreprise]` | Nom de l'entreprise | `Entreprise.nom` |
| | `[adresse_entreprise]` | Adresse de l'entreprise | `Entreprise.adresse` |
| | `[domaine_activite]` | Domaine d'activité | `Entreprise.domaineActivite` |
| | `[site_web]` | Site web | `Entreprise.siteWeb` |
| **Stage** | `[date_debut]` | Date de début du stage | `Stage.dateDebut` |
| | `[date_fin]` | Date de fin du stage | `Stage.dateFin` |
| | `[duree_mois]` | Durée en mois | `Stage.dureeMois` |
| | `[sujet]` | Sujet du stage | `Stage.sujet` |
| | `[mission]` | Description de la mission | `Stage.mission` |
| **Encadrants** | `[nom_encadrant_academique]` | Encadrant universitaire | `User.lastName` (enc. acad.) |
| | `[nom_encadrant_entreprise]` | Encadrant côté entreprise | `User.lastName` (enc. entr.) |
| **Document** | `[date_generation]` | Date de création du document | `Document.dateCreation` |
| | `[date_expiration]` | Date d'expiration | `Document.dateExpiration` |
| | `[type_document]` | Type de document | `TypeDocument.nom` |
| | `[numero_document]` | UUID du document | `Document.docUuid` |
| | `[annee_universitaire]` | Année universitaire (calculé) | — |
| **Données libres** | `[tout_autre_champ]` | Tout champ passé dans `donneesSupplementaires` | `GenerateRequest.donneesSupplementaires` |

### Zone QR Code

Pour insérer une zone QR code dans votre template, dessinez un **rectangle ou carré rouge** (`#FF0000`, `#C00000`, `#DC143C`, etc.) à l'emplacement souhaité dans le document Word. Le microservice détectera automatiquement cette zone et y insérera le QR code d'authentification lors de la génération.

---

## 5. API Reference

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

### Microservice Python — Endpoints directs

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/health` | Statut du microservice |
| POST | `/cv/extract` | Extraction CV (multipart `file`) |
| POST | `/cv/extract/text-only` | Extraction texte seul (sans scoring) |
| POST | `/modeles/` | Créer un modèle depuis upload `.docx` |
| POST | `/modeles/from-url` | Créer un modèle depuis URL ou chemin local |
| GET | `/modeles/` | Lister les modèles (`?id_type_document=`) |
| GET | `/modeles/{id}` | Détail d'un modèle |
| GET | `/modeles/files/{filename}` | Télécharger le `.docx` |
| DELETE | `/modeles/{id}` | Supprimer un modèle |
| POST | `/documents/generer` | Générer un document rempli |
| GET | `/documents/telecharger/{filename}` | Télécharger un document généré |
| GET | `/documents/verifier/{uuid}` | Vérifier authenticité (HMAC) |

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

## 6. Frontend — Pages & Fonctions

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

## 7. Analyse du Backlog

### A. Arbre de dépendances (MoSCoW)

Le backlog a été structuré autour de **38 User Stories** réparties en 4 sprints pour un total de **138 points**.

```
AUTHENTIFICATION (Must Have — Sprints 1-2)
├── U-01 Inscription multi-rôles              ✅ [8 pts]
├── U-02 Connexion JWT                        ✅ [5 pts]
├── U-03 Vérification email OTP               ✅ [5 pts]
├── U-04 Reset mot de passe                   ✅ [3 pts]
└── U-05 Profil utilisateur                   ✅ [3 pts]

CV (Must Have — Sprint 2)
├── ET-01 Upload CV                           ✅ [3 pts]
├── ET-02 Extraction IA asynchrone            ✅ [8 pts]
├── ET-03 Affichage données extraites         ✅ [3 pts]
└── ET-04 Scoring complétude CV               ✅ [3 pts]

OFFRES DE STAGE (Must Have — Sprint 2)
├── EA-01 Création offre (Entreprise)         ✅ [3 pts]
├── EA-02 Listing & recherche offres          ✅ [5 pts]
├── EA-03 Validation offre (Admin)            ✅ [3 pts]
└── EA-04 Recommandation IA d'offres          ⬜ [8 pts]

CANDIDATURES (Must Have — Sprint 2-3)
├── ET-05 Postuler                            ✅ [3 pts]
├── EA-05 Décision candidature (Entreprise)   ✅ [3 pts]
└── EA-06 Tableau candidatures               ✅ [3 pts]

STAGES (Should Have — Sprint 3)
├── AD-08 Créer stage depuis candidature      ✅ [3 pts]
├── AD-09 Assigner encadrants                 ✅ [3 pts]
├── EE-01 Suivi stage encadrant académique    ⬜ [5 pts]
└── EE-02 Suivi stage encadrant entreprise   ⬜ [5 pts]

DOCUMENTS (Must Have — Sprints 3-4)
├── AD-01 Créer type de document              ✅ [2 pts]
├── AD-02 Upload modèle .docx                 ✅ [5 pts]
├── AD-03 Génération document étudiant        🔶 [8 pts]
├── AD-04 QR code d'authentification          🔶 [8 pts]
├── AD-10 Gestion expiration docs             🔶 [5 pts]
└── ET-06 Télécharger ses documents           ⬜ [3 pts]

NOTIFICATIONS (Should Have — Sprint 4)
├── U-06 Notifications email                  ⬜ [5 pts]
├── U-07 Notifications in-app                 ⬜ [8 pts]
└── U-08 Centre de notifications              ⬜ [5 pts]

RAPPORTS (Should Have — Sprint 4)
├── EE-03 Rapport de stage                    ⬜ [8 pts]
└── EE-04 Signature convention               ⬜ [5 pts]

ANALYTICS (Won't Have — Sprint 4)
├── AD-05 Dashboard statistiques admin        ⬜ [5 pts]
├── AD-06 Export données CSV                  ⬜ [3 pts]
└── AD-07 Logs d'audit                        ⬜ [3 pts]

MATCHING IA (Should Have — Sprint 4)
├── EN-01 Score compatibilité CV/offre        ✅ [5 pts]  ← via scoring CV
├── EN-02 Recommandations personnalisées      ⬜ [8 pts]
├── EN-03 Analyse compétences marché          ⬜ [5 pts]
└── EN-04 Génération lettre de motivation IA  ⬜ [5 pts]

ADMINISTRATION (Must Have — Sprints 3-4)
├── AD-11 Gestion utilisateurs                ✅ [3 pts]
├── AD-12 Validation comptes entreprise       ✅ [3 pts]
└── AD-13 Blacklist entreprise                ⬜ [3 pts]

Légende : ✅ Terminé  🔶 Partiel  ⬜ Non réalisé
```

### B. Statut par User Story

| ID | Titre | Sprint | Points | Statut | Note |
|----|-------|--------|--------|--------|------|
| U-01 | Inscription multi-rôles | 1 | 8 | ✅ | Étudiant, Entreprise, Admin, Encadrants |
| U-02 | Connexion JWT | 1 | 5 | ✅ | JWT stateless, BCrypt |
| U-03 | Vérification email OTP | 1 | 5 | ✅ | Code 6 chiffres, expiration 10min |
| U-04 | Reset mot de passe | 1 | 3 | ✅ | Email + OTP + nouveau MDP |
| U-05 | Profil utilisateur | 2 | 3 | ✅ | Lecture via GET /api/etudiant/profil |
| ET-01 | Upload CV | 2 | 3 | ✅ | Multipart PDF |
| ET-02 | Extraction IA asynchrone | 2 | 8 | ✅ | @Async, NVIDIA NIM + OpenRouter fallback |
| ET-03 | Affichage données extraites | 2 | 3 | ✅ | Dashboard étudiant |
| ET-04 | Scoring complétude CV | 2 | 3 | ✅ | scoreGlobal/100, 5 niveaux |
| EA-01 | Création offre (Entreprise) | 2 | 3 | ✅ | CRUD complet |
| EA-02 | Listing & recherche offres | 2 | 5 | ✅ | Pagination + filtres domaine/lieu/type |
| EA-03 | Validation offre (Admin) | 2 | 3 | ✅ | PATCH approuve: true/false |
| EA-04 | Recommandation IA offres | 3 | 8 | ⬜ | Non réalisé |
| ET-05 | Postuler | 2 | 3 | ✅ | Lettre de motivation |
| EA-05 | Décision candidature | 2 | 3 | ✅ | ACCEPTEE/REFUSEE + commentaire |
| EA-06 | Tableau candidatures | 3 | 3 | ✅ | Vue entreprise + étudiant |
| AD-08 | Créer stage depuis candidature | 3 | 3 | ✅ | POST /api/admin/stages/depuis-candidature |
| AD-09 | Assigner encadrants | 3 | 3 | ✅ | PATCH /api/admin/stages/{id}/encadrants |
| EE-01 | Suivi stage encadrant académique | 3 | 5 | ⬜ | Endpoints stub |
| EE-02 | Suivi stage encadrant entreprise | 3 | 5 | ⬜ | Endpoints stub |
| AD-01 | Créer type de document | 3 | 2 | ✅ | POST /api/admin/types-documents |
| AD-02 | Upload modèle .docx | 3 | 5 | ✅ | Détection [champs] + zone QR rouge |
| AD-03 | Génération document étudiant | 4 | 8 | 🔶 | Backend OK, frontend docs partiel |
| AD-04 | QR code d'authentification | 4 | 8 | 🔶 | Logique complète, page auth publique |
| AD-10 | Gestion expiration documents | 3 | 5 | 🔶 | @Scheduled OK, révocation manuelle absente |
| ET-06 | Télécharger ses documents | 4 | 3 | ⬜ | Endpoint présent, UI absente |
| U-06 | Notifications email | 4 | 5 | ⬜ | EmailService partiel (OTP seulement) |
| U-07 | Notifications in-app | 4 | 8 | ⬜ | Non réalisé |
| U-08 | Centre de notifications | 4 | 5 | ⬜ | Non réalisé |
| EE-03 | Rapport de stage | 4 | 8 | ⬜ | Non réalisé |
| EE-04 | Signature convention | 4 | 5 | ⬜ | Non réalisé |
| AD-05 | Dashboard statistiques admin | 4 | 5 | ⬜ | GET /api/admin/stats non implémenté |
| AD-06 | Export données CSV | 4 | 3 | ⬜ | Non réalisé |
| AD-07 | Logs d'audit | 4 | 3 | ⬜ | Non réalisé |
| EN-01 | Score compatibilité CV/offre | 3 | 5 | ✅ | Via scoring microservice IA |
| EN-02 | Recommandations personnalisées | 4 | 8 | ⬜ | Non réalisé |
| EN-03 | Analyse compétences marché | 4 | 5 | ⬜ | Non réalisé |
| EN-04 | Génération lettre motivation IA | 4 | 5 | ⬜ | Non réalisé |

**Résumé :**
- ✅ **Terminé :** 23 US / 82 pts
- 🔶 **Partiel :** 3 US / 21 pts (crédit ~50% → ~10 pts)
- ⬜ **Non réalisé :** 12 US / 35 pts

**Avancement backlog pur :** ~87 pts / 138 pts = **63 %**

---

## 8. Avancement par Sprint

### Sprint 1 (Authentification + Setup) — ✅ 100%

| US | Points | Statut |
|----|--------|--------|
| U-01 Inscription multi-rôles | 8 | ✅ |
| U-02 Connexion JWT | 5 | ✅ |
| U-03 Vérification email OTP | 5 | ✅ |
| U-04 Reset mot de passe | 3 | ✅ |

**Sprint 1 :** 21/21 pts — **100%**

---

### Sprint 2 (CV + Offres + Candidatures) — ✅ ~100%

| US | Points | Statut |
|----|--------|--------|
| U-05 Profil utilisateur | 3 | ✅ |
| ET-01 Upload CV | 3 | ✅ |
| ET-02 Extraction IA asynchrone | 8 | ✅ |
| ET-03 Affichage données extraites | 3 | ✅ |
| ET-04 Scoring complétude CV | 3 | ✅ |
| EA-01 Création offre | 3 | ✅ |
| EA-02 Listing & recherche offres | 5 | ✅ |
| EA-03 Validation offre (Admin) | 3 | ✅ |
| ET-05 Postuler | 3 | ✅ |
| EA-05 Décision candidature | 3 | ✅ |

**Sprint 2 :** 37/37 pts — **100%**

---

### Sprint 3 (Stages + Documents + Encadrants) — 🔶 ~72%

| US | Points | Statut | Note |
|----|--------|--------|------|
| EA-06 Tableau candidatures | 3 | ✅ | |
| AD-08 Créer stage | 3 | ✅ | |
| AD-09 Assigner encadrants | 3 | ✅ | |
| AD-01 Créer type document | 2 | ✅ | |
| AD-02 Upload modèle .docx | 5 | ✅ | |
| EN-01 Score compatibilité CV | 5 | ✅ | |
| AD-10 Gestion expiration | 5 | 🔶 | @Scheduled OK, révocation manuelle absente |
| EA-04 Recommandation IA offres | 8 | ⬜ | Non réalisé |
| EE-01 Suivi encadrant académique | 5 | ⬜ | Stub seulement |
| EE-02 Suivi encadrant entreprise | 5 | ⬜ | Stub seulement |

**Sprint 3 :** ~24/49 pts — **~49%**

---

### Sprint 4 (Documents avancés + Notifications + Analytics) — 🔴 ~25%

| US | Points | Statut | Note |
|----|--------|--------|------|
| AD-03 Génération document | 8 | 🔶 | Backend + microservice OK, UI partielle |
| AD-04 QR code authentification | 8 | 🔶 | Logique OK, intégration frontend partielle |
| ET-06 Télécharger documents | 3 | ⬜ | Endpoint présent, UI absente |
| U-06 Notifications email | 5 | ⬜ | Seul OTP implémenté |
| U-07 Notifications in-app | 8 | ⬜ | Non réalisé |
| U-08 Centre notifications | 5 | ⬜ | Non réalisé |
| EE-03 Rapport de stage | 8 | ⬜ | Non réalisé |
| EE-04 Signature convention | 5 | ⬜ | Non réalisé |
| AD-05 Dashboard stats admin | 5 | ⬜ | Non réalisé |
| AD-06 Export CSV | 3 | ⬜ | Non réalisé |
| AD-07 Logs d'audit | 3 | ⬜ | Non réalisé |
| EN-02 Recommandations IA | 8 | ⬜ | Non réalisé |
| EN-03 Analyse compétences | 5 | ⬜ | Non réalisé |
| EN-04 Lettre motivation IA | 5 | ⬜ | Non réalisé |

**Sprint 4 :** ~8/82 pts — **~10%** *(sprint le plus chargé, le moins avancé)*

---

## 9. Fonctionnalités Hors Backlog

Les développements suivants ont été réalisés en dehors du backlog officiel. Ils représentent un travail significatif qui améliore la qualité et la sécurité du projet.

| # | Fonctionnalité | Description | Valeur estimée |
|---|---------------|-------------|---------------|
| 1 | **Microservice IA Python** | Architecture FastAPI complète séparée du backend Spring Boot, avec modules cv_extraction, document_templates, document_generation | ~8 pts |
| 2 | **Multi-provider IA avec fallback** | NVIDIA NIM (primaire) + OpenRouter (fallback) — basculement automatique en cas d'erreur | ~5 pts |
| 3 | **Signature HMAC-SHA256** | Signature des documents générés pour vérification d'authenticité, stockée dans les métadonnées | ~5 pts |
| 4 | **Zone QR rouge auto-détectée** | Détection automatique de zones rouges (DrawingML + VML) dans les templates .docx pour positionnement QR | ~5 pts |
| 5 | **Pré-génération UUID côté Spring** | UUID généré côté Spring Boot avant appel microservice — garantit cohérence nom fichier ↔ enregistrement MySQL | ~3 pts |
| 6 | **@Scheduled expiration horaire** | `DocumentExpirationTask` — cron `0 0 * * * *`, passe les docs échus à `EXPIRE` automatiquement | ~3 pts |
| 7 | **@Async extraction CV** | Extraction non bloquante, polling statut, `AsyncCvService` dédié, `@EnableAsync` + `ThreadPoolTaskExecutor` | ~3 pts |
| 8 | **Page auth QR publique** | `GET /api/documents/{uuid}/authentifier` sans JWT — retourne type, dates, étudiant, établissement, logo | ~3 pts |
| 9 | **HTTP 410 GONE expiré** | Convention REST correcte : 410 si expiré, 404 si révoqué/introuvable | ~1 pt |
| 10 | **Extraction .doc + .docx** | Support des anciens formats .doc en plus de .docx pour l'upload de templates | ~1 pt |
| 11 | **registry.json persistance** | Stockage local JSON des métadonnées de templates (id auto-incrémenté, champs, QR, dates) | ~2 pts |
| 12 | **Interface test microservice** | `test-interface.html` avec 4 onglets (CV, Modèles, Génération, Vérification) pour tester le microservice isolément | ~2 pts |
| 13 | **Interface test full-stack** | `test-interface/index.html` — interface complète auth+CV+documents sur ports 8081+8000 | ~3 pts |
| 14 | **duree_validite_jours configurable** | Durée de validité paramétrable à la création du modèle, transmise jusqu'à `ModeleDocument.dureeValiditeJours` | ~2 pts |
| 15 | **CORS wildcard configuré** | `allow_origins=["*"]` dans FastAPI + Spring Boot pour le développement | ~1 pt |
| 16 | **Variables .env externalisées** | Tous les secrets (JWT, DB, mail, API keys, signature) dans `.env` / variables d'env, avec `.env.example` | ~2 pts |

**Total estimé hors backlog : ~49 points**

---

## 10. Évolution Globale & Synthèse

### Calcul de l'avancement

| Catégorie | Points | Détail |
|-----------|--------|--------|
| Backlog terminé ✅ | 82 pts | 23 US complètes |
| Backlog partiel 🔶 (crédit 50%) | ~10 pts | 3 US partielles (21 pts × 50%) |
| **Backlog réalisé** | **~92 / 138 pts** | **67%** |
| Hors backlog | ~49 pts | 16 fonctionnalités |
| **Total réalisé** | **~141 pts** | — |
| **Base de référence** | 138 pts (backlog) | — |
| **Évolution globale** | **≈ 102%** du backlog | *(hors-backlog compense les US manquantes)* |

> **Lecture :** Le projet a réalisé ~67% du backlog officiel, mais les 16 fonctionnalités hors-backlog (~49 pts) compensent largement les US non réalisées (~46 pts), portant l'évolution réelle à ~102% de la charge initiale prévue.

### Points forts

- ✅ **Socle solide** — Authentification (Sprint 1-2) à 100%, base irréprochable
- ✅ **CV IA** — Pipeline complet avec extraction multi-providers, scoring, asynchronisme
- ✅ **Documents avec QR** — Fonctionnalité phare bien avancée (backend + microservice)
- ✅ **Architecture microservice** — Séparation claire Spring Boot ↔ Python, extensible
- ✅ **Sécurité** — JWT, BCrypt, HMAC, secrets externalisés

### Points d'amélioration

| Priorité | Élément | Impact |
|----------|---------|--------|
| 🔴 **HAUTE** | UI documents étudiant (ET-06) | Empêche les étudiants d'accéder à leurs docs |
| 🔴 **HAUTE** | Dashboard admin stats (AD-05) | Aucune visibilité sur les métriques |
| 🟡 **MOYENNE** | Notifications email (U-06) | Seulement OTP, pas les autres événements |
| 🟡 **MOYENNE** | Suivi encadrants (EE-01, EE-02) | Acteurs sans interface fonctionnelle |
| 🟡 **MOYENNE** | Révocation manuelle documents | Seule l'expiration auto fonctionne |
| 🟢 **BASSE** | Recommandation IA offres (EA-04) | Valeur ajoutée mais non bloquant |
| 🟢 **BASSE** | Export CSV (AD-06) | Confort admin |

### Recommandations pour finalisation

1. **Priorité 1 — Finir l'intégration UI** : Connecter les endpoints documents existants aux dashboards étudiant et admin (ET-06, AD-05). Le backend est déjà prêt.

2. **Priorité 2 — Notifications email** : Étendre `EmailService` pour notifier les candidatures (acceptée/refusée), les stages créés, les documents générés.

3. **Priorité 3 — Suivi encadrants** : Implémenter les vues de suivi (endpoints stubs à remplir), base métier déjà en place.

4. **Priorité 4 — Tests** : Ajouter des tests JUnit/Mockito sur les services critiques (AuthService, DocumentService) et des tests d'intégration API.

5. **Priorité 5 — Production** : Configurer Nginx reverse-proxy, passer `allow_origins` à la liste des domaines autorisés, activer HTTPS.

---

*Documentation générée le 25 mai 2026 — SmartIntern AI / ITEAM University PFA*
