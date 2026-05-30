# SmartIntern AI — Documentation Technique & Analyse Backlog

**Projet :** Plateforme de gestion intelligente des stages universitaires  
**Établissement :** ITEAM University  
**Type :** Projet de Fin d'Année (PFA)  
**Stack :** Java Spring Boot 3.2.5 + Python FastAPI + HTML/CSS/JS + MySQL  
**Dépôt :** https://github.com/AnoNymos-24/PFA.git  
**Branche active :** `feature/version6` (fusion version5 + travail personnel)  
**Date analyse :** 28 mai 2026

---

## Table des matières

1. [Présentation Générale](#1-présentation-générale)
2. [Architecture](#2-architecture)
3. [Démarrage Rapide](#3-démarrage-rapide)
4. [Variables de Documents (Templates)](#4-variables-de-documents-templates)
5. [API Reference](#5-api-reference)
   - Auth, CV, Offres, Candidatures, Stages, Rapports, Notifications, Administration
   - Cahier des charges, Sprints, Tâches ← **NEW v6**
   - Logs d'activité & Sessions ← **NEW v6**
   - Matching & Recommandations ← **NEW v6**
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
| **Étudiant** | Profil, CV, candidatures, stages, rapports, documents | `/api/etudiant/` |
| **Entreprise** | Offres, candidatures, stagiaires, convention | `/api/entreprise/` |
| **Admin** | Validation, stats, modèles de documents, utilisateurs | `/api/admin/` |
| **EncadrantAcademique** | Suivi académique, validation rapports | `/api/encadrant-academique/` |
| **EncadrantEntreprise** | Suivi entreprise, convention, mission | `/api/encadrant-entreprise/` |

---

## 2. Architecture

```
smartinternIA2/
├── backend/                         # Spring Boot 3.2.5 (port 8081)
│   ├── src/main/java/com/smartintern/backend/
│   │   ├── config/                  # AppConfig, SecurityConfig, GlobalExceptionHandler
│   │   ├── controller/
│   │   │   ├── AuthController            # /api/auth/* (register, login, logout)
│   │   │   ├── CvController              # /api/etudiant/cv (upload PDF/IMG, statut, reanalyse)
│   │   │   ├── OffreStageController      # /api/etudiant|entreprise|admin/offres
│   │   │   ├── CandidatureController     # /api/etudiant|entreprise/candidatures
│   │   │   ├── StageController           # /api/admin|etudiant|encadrant-*/stages
│   │   │   ├── RapportStageController    # /api/etudiant|encadrant-academique/rapports ← v5
│   │   │   ├── DocumentController        # /api/etudiant|admin/documents + /api/documents/{uuid}
│   │   │   ├── AdminController           # /api/admin/users, demandes-stage ← v5
│   │   │   ├── NotificationController    # /api/notifications/* ← v5
│   │   │   ├── OtpController             # /api/auth/verify-otp, reset-password
│   │   │   ├── LogActiviteController     # /api/admin/logs (AL-01..AL-03) ← v6
│   │   │   ├── SessionConnexionController# /api/admin/sessions (SC-01..SC-03) ← v6
│   │   │   ├── CahierDesChargesController# /api/encadrant-entreprise|etudiant/cahier-des-charges ← v6
│   │   │   ├── SprintController          # /api/encadrant-entreprise|etudiant/sprints ← v6
│   │   │   ├── TacheController           # /api/encadrant-entreprise|etudiant/taches ← v6
│   │   │   ├── MatchingEtudiantController  # /api/etudiant/recommandations ← v6
│   │   │   └── MatchingEntrepriseController# /api/entreprise/recommandations ← v6
│   │   ├── entity/
│   │   │   ├── User, Etudiant, Entreprise, Etablissement, EncadrantAcademique, EncadrantEntreprise
│   │   │   ├── OffreStage, Candidature, DemandeStage, Stage
│   │   │   ├── CvStandardise, Profil, Experience, Formation, Competence, Langue
│   │   │   ├── RapportStage        # HEBDOMADAIRE|FINAL / BROUILLON|SOUMIS|VALIDE|REJETE ← v5
│   │   │   ├── Notification        # type, message, lu, createdAt ← v5
│   │   │   ├── Document, DocumentGenere, ModeleDocument, TypeDocument
│   │   │   ├── LogActivite         # audit trail toutes actions utilisateurs ← v6
│   │   │   ├── SessionConnexion    # sessions IP/device/durée ← v6
│   │   │   ├── CahierDesCharges    # BROUILLON → VALIDE (prérequis sprint) ← v6
│   │   │   ├── Sprint              # PLANIFIE → EN_COURS(auto) → CLOTURE ← v6
│   │   │   └── Tache               # A_FAIRE → EN_COURS → TERMINEE → VALIDEE/REFUSEE ← v6
│   │   ├── security/
│   │   │   ├── JwtUtils, JwtAuthFilter
│   │   │   └── JwtBlacklist        # Blacklist mémoire pour logout ← v5
│   │   └── service/
│   │       ├── AuthService, OtpService, EmailService
│   │       ├── CvExtractionService, AsyncCvService (@Async), CvStandardiseService
│   │       ├── OffreStageService, CandidatureService, StageService ← enrichi
│   │       ├── MatchingService      # scoring 5 composantes + persistScores() ← v6
│   │       ├── RapportStageService  # CRUD rapports + validation encadrant ← v5
│   │       ├── NotificationService  # getMes, marquerLue, marquerToutesLues ← v5
│   │       ├── AdminService         # gestion users + demandes de stage ← v5
│   │       ├── DocumentService, ModeleDocumentService
│   │       ├── DocumentExpirationTask (@Scheduled, cron horaire)
│   │       ├── LogActiviteService   # TransactionTemplate fire-and-forget ← v6
│   │       ├── SessionConnexionService # ouvrirSession/fermerSession ← v6
│   │       ├── CahierDesChargesService # upload multipart + validation ← v6
│   │       ├── SprintService        # CRUD + transition EN_COURS automatique ← v6
│   │       └── TacheService         # machine à états + validation étudiant/encadrant ← v6
│   └── src/main/resources/application.properties
├── smartintern-ai-service/          # Python FastAPI (port 8000)
│   ├── main.py
│   ├── app/modules/
│   │   ├── cv_extraction/           # /cv/extract, /cv/extract/text-only
│   │   ├── document_templates/      # /modeles/*
│   │   └── document_generation/     # /documents/*
│   ├── test-interface.html          # Interface de test microservice (4 onglets)
│   └── .env                         # NVIDIA_API_KEY, OPENROUTER_API_KEY
├── test-interface/
│   └── index.html                   # Interface de test full-stack
└── frontend/                        # HTML/CSS/JS vanilla
    ├── js/auth.js, api.js
    └── pages/login, register, verify-email, etudiant|entreprise|admin-dashboard
```

### Flux CV (asynchrone)

```
POST /api/etudiant/cv  →  202 Accepted  →  extraction IA en background (@Async)
                                         ↓
GET /api/etudiant/cv/statut  →  {statut: "EN_COURS" | "EXTRAIT" | "ERREUR"}
                                         ↓ (poll toutes les 3s)
GET /api/etudiant/cv  →  {hasCv, filename, statutExtraction, cvData, scoreCompletude}
```

### Flux Rapports de stage (NEW — v5)

```
POST /api/etudiant/stages/{id}/rapports  {type: "HEBDOMADAIRE"|"FINAL", titre, contenu}
  → Statut BROUILLON
PATCH /api/etudiant/rapports/{id}/soumettre
  → Statut SOUMIS
PATCH /api/encadrant-academique/rapports/{id}/valider
  → Statut VALIDE
PATCH /api/encadrant-academique/rapports/{id}/commenter  {commentaire: "..."}
  → Statut REJETE
```

### Flux CDC → Sprint → Tâche (v6 — Mission C)

> **Prérequis** : le stage doit avoir un Cahier des Charges au statut `VALIDE` avant de pouvoir créer le premier Sprint.

```
POST /api/encadrant-entreprise/cahier-des-charges/stage/{id}   multipart(file,titre) → CDC BROUILLON
POST /api/encadrant-entreprise/cahier-des-charges/{id}/valider  → CDC VALIDE (prérequis sprint déverrouillé)
                  ↓
POST /api/encadrant-entreprise/sprints/stage/{id}               → Sprint PLANIFIE
POST /api/encadrant-entreprise/taches/sprint/{id}               → Tâche A_FAIRE
  (1ère tâche démarrée → Sprint passe automatiquement EN_COURS)
                  ↓
POST /api/etudiant/taches/{id}/demarrer                         → EN_COURS
POST /api/etudiant/taches/{id}/terminer                         → TERMINEE_PAR_ETUDIANT
POST /api/encadrant-entreprise/taches/{id}/valider              → VALIDEE ✅
  ou
POST /api/encadrant-entreprise/taches/{id}/refuser              → REFUSEE
POST /api/etudiant/taches/{id}/reprendre                        → EN_COURS (cycle recommence)
                  ↓
POST /api/encadrant-entreprise/sprints/{id}/cloturer            → CLOTURE
```

### Flux Audit Trail (v6 — Mission B)

```
[Toute action utilisateur] → LogActiviteService.loguer() [TransactionTemplate REQUIRES_NEW]
                          ↓ (fire-and-forget, jamais de throw vers l'appelant)
                          → LogActivite persisté en DB (action, entiteCible, entiteId, IP, device)
                          → SessionConnexion ouverte au login, fermée au logout/expiration

GET /api/admin/logs                              → audit trail paginé
GET /api/admin/logs/user/{userId}               → logs par utilisateur
GET /api/admin/logs/entite/{entiteCible}/{id}   → logs par entité
GET /api/admin/sessions/actives                 → sessions en cours (temps-réel)
```

### Flux Génération de documents (avec QR authentification)

```
POST /api/etudiant/documents/generer  →  docUuid pré-généré par Spring Boot
  → Microservice remplit .docx + insère QR (zone rouge)
  → Spring Boot persiste Document + DocumentGenere

GET /api/documents/{uuid}/authentifier (PUBLIC)
  → HTTP 200 si valide / 410 si expiré / 404 si révoqué

DocumentExpirationTask (@Scheduled cron="0 0 * * * *")
  → Toutes les heures, passe les docs échus au statut EXPIRE
```

### Variables d'environnement requises

```bash
# backend/application.properties
JWT_SECRET=<secret HS256 min 32 chars>
DB_PASSWORD=<mysql password>
GMAIL_APP_PASSWORD=<google app password>
CV_SERVICE_URL=http://localhost:8000
APP_BASE_URL=http://localhost:8081

# smartintern-ai-service/.env
NVIDIA_API_KEY=nvapi-...
OPENROUTER_API_KEY=sk-or-...
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
pip install -r requirements.txt
cp .env.example .env
uvicorn main:app --reload --port 8000

# 3. Backend Spring Boot (port 8081)
cd backend
mvn spring-boot:run

# 4. Interfaces de test
# Microservice : ouvrir smartintern-ai-service/test-interface.html
# Full-stack   : ouvrir test-interface/index.html
# Frontend     : ouvrir frontend/pages/login.html
```

> ⚠️ **Python 3.11 requis** — pydantic-core 2.20.1 (PyO3 0.22) et Pillow 10.4.0 sont incompatibles avec Python 3.14+.

---

## 4. Variables de Documents (Templates)

Dans vos fichiers `.docx`, utilisez des marqueurs `[nom_variable]`. Le microservice détecte automatiquement tous les champs entre crochets lors de l'analyse.

| Catégorie | Variable | Description |
|-----------|----------|-------------|
| **Étudiant — Identité** | `[prenom]` | Prénom |
| | `[nom]` | Nom de famille |
| | `[nom_complet]` | Prénom + Nom |
| | `[email]` | Adresse email |
| | `[telephone]` | Téléphone |
| | `[cin]` | Carte d'identité nationale |
| | `[date_naissance]` | Date de naissance |
| | `[nationalite]` | Nationalité |
| **Étudiant — Académique** | `[code_etudiant]` | Matricule |
| | `[filiere]` | Filière d'études |
| | `[classe]` | Classe / niveau |
| **Établissement** | `[nom_etablissement]` | Nom de l'établissement |
| | `[adresse_etablissement]` | Adresse |
| | `[identifiant_etablissement]` | Identifiant officiel |
| **Entreprise** | `[nom_entreprise]` | Nom de l'entreprise |
| | `[adresse_entreprise]` | Adresse |
| | `[domaine_activite]` | Domaine d'activité |
| | `[site_web]` | Site web |
| **Stage** | `[date_debut]` | Date de début |
| | `[date_fin]` | Date de fin |
| | `[duree_mois]` | Durée en mois |
| | `[sujet]` | Sujet du stage |
| | `[mission]` | Description de la mission |
| **Encadrants** | `[nom_encadrant_academique]` | Encadrant universitaire |
| | `[nom_encadrant_entreprise]` | Encadrant côté entreprise |
| **Document** | `[date_generation]` | Date de création |
| | `[date_expiration]` | Date d'expiration |
| | `[type_document]` | Type de document |
| | `[numero_document]` | UUID du document |
| **Libre** | `[tout_autre_champ]` | Passé via `donneesSupplementaires` |

> **Zone QR Code :** dessinez un **rectangle rouge** (`#FF0000`) dans le `.docx` — le microservice le remplace automatiquement par le QR d'authentification.

---

## 5. API Reference

### Auth (`/api/auth/`)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/register` | Inscription multi-rôles |
| POST | `/login` | Connexion → `{token, id, firstName, lastName, role}` |
| POST | `/verify-otp` | Vérification OTP |
| POST | `/resend-otp` | Renvoyer code OTP |
| POST | `/forgot-password` | Demande reset |
| POST | `/reset-password` | Reset avec OTP |
| POST | `/logout` | Blackliste le token JWT ← **NEW v5** |

### CV (`/api/etudiant/cv`)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/etudiant/cv` | Upload PDF/JPG/PNG → **202** + extraction async |
| GET | `/api/etudiant/cv` | `{hasCv, statutExtraction, cvData, scoreCompletude}` |
| GET | `/api/etudiant/cv/statut` | `{statut, pret}` |
| POST | `/api/etudiant/cv/reanalyse` | Ré-analyse → **202** |

### Offres de Stage

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/etudiant/offres` | Liste paginée |
| GET | `/api/etudiant/offres/search` | Filtres domaine/lieu/type/niveau |
| GET | `/api/etudiant/offres/{id}` | Détail offre |
| POST | `/api/entreprise/offres` | Créer une offre |
| PUT | `/api/entreprise/offres/{id}` | Modifier (repasse EN_ATTENTE) |
| DELETE | `/api/entreprise/offres/{id}` | Supprimer |
| GET | `/api/entreprise/offres` | Mes offres |
| GET | `/api/admin/offres/en-attente` | Offres à valider |
| PATCH | `/api/admin/offres/{id}/valider` | `{approuve: true/false}` |

### Candidatures

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/etudiant/candidatures` | `{offreId, lettreMotivation}` |
| GET | `/api/etudiant/candidatures` | Mes candidatures |
| GET | `/api/entreprise/offres/{id}/candidatures` | Candidatures reçues |
| GET | `/api/entreprise/candidatures/{id}/etudiant` | Profil complet candidat ← **NEW v5** |
| PATCH | `/api/entreprise/candidatures/{id}/decision` | `{statut, commentaire}` |

### Stages

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/etudiant/stages` | Mes stages |
| GET | `/api/encadrant-academique/stages` | Stages encadrés (académique) |
| GET | `/api/encadrant-entreprise/stages` | Stages encadrés (entreprise) |
| POST | `/api/admin/stages/depuis-candidature/{id}` | Créer stage depuis candidature |
| PATCH | `/api/admin/stages/{id}/encadrants` | Assigner encadrants + dates + mission |
| PATCH | `/api/entreprise/stages/{id}/signer-convention` | Signature entreprise ← **NEW v5** |
| PATCH | `/api/encadrant-entreprise/stages/{id}/signer-convention` | Signature encadrant ← **NEW v5** |
| PATCH | `/api/encadrant-entreprise/stages/{id}/mission` | Définir mission `{mission}` ← **NEW v5** |

### Rapports de stage ← **NEW v5**

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/etudiant/stages/{id}/rapports` | Créer rapport `{type, titre, contenu, semaineDebut}` |
| GET | `/api/etudiant/stages/{id}/rapports` | Mes rapports pour ce stage |
| PATCH | `/api/etudiant/rapports/{id}/soumettre` | Soumettre (BROUILLON → SOUMIS) |
| GET | `/api/encadrant-academique/stages/{id}/rapports` | Rapports à valider |
| PATCH | `/api/encadrant-academique/rapports/{id}/valider` | Valider (SOUMIS → VALIDE) |
| PATCH | `/api/encadrant-academique/rapports/{id}/commenter` | Rejeter `{commentaire}` |

### Notifications ← **NEW v5**

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/notifications` | Mes notifications (tri date desc) |
| GET | `/api/notifications/non-lues` | `{nonLues: N}` |
| PATCH | `/api/notifications/{id}/lire` | Marquer une notification lue |
| PATCH | `/api/notifications/lire-tout` | Tout marquer lu |

### Administration ← **enrichi v5**

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/admin/users` | Tous les utilisateurs |
| GET | `/api/admin/users/role/{role}` | Par rôle |
| GET | `/api/admin/users/{id}` | Détail utilisateur |
| PATCH | `/api/admin/users/{id}/role` | Changer le rôle |
| PATCH | `/api/admin/users/{id}/statut` | Changer le statut (ACTIF/SUSPENDU/…) |
| POST | `/api/admin/demandes-stage` | Créer une demande de stage |
| GET | `/api/admin/demandes-stage` | Lister toutes les demandes |

### Documents

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/etudiant/documents/generer` | Génère son document (unicité par type) |
| POST | `/api/admin/documents/generer` | Génère pour n'importe quel user |
| GET | `/api/etudiant/documents` | Historique documents |
| GET | `/api/documents/{uuid}/authentifier` | **PUBLIC** — auth QR (410 expiré, 404 révoqué) |
| GET | `/api/documents/{uuid}/telecharger` | Télécharger DOCX/PDF |

### Modèles de documents (Admin)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/admin/modeles` | Liste (`?statut=ACTIF|ARCHIVE`) |
| POST | `/api/admin/modeles` | Multipart: `request` (JSON) + `fichierModele` (.docx) |
| GET | `/api/admin/modeles/{id}` | Détail modèle |
| PATCH | `/api/admin/modeles/{id}` | Mise à jour |
| DELETE | `/api/admin/modeles/{id}` | Archiver |
| GET | `/api/admin/types-documents` | Types de documents |
| POST | `/api/admin/types-documents` | `{nom, code, description}` |

### Cahier des charges ← **NEW v6**

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/encadrant-entreprise/cahier-des-charges/stage/{id}` | Upload CDC multipart `(file, titre)` |
| POST | `/api/encadrant-entreprise/cahier-des-charges/{id}/valider` | Valider CDC → VALIDE |
| GET | `/api/encadrant-entreprise/cahier-des-charges/stage/{id}` | Détail CDC (encadrant) |
| GET | `/api/etudiant/cahier-des-charges/stage/{id}` | Détail CDC (étudiant, lecture seule) |

### Sprints ← **NEW v6**

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/encadrant-entreprise/sprints/stage/{id}` | Créer sprint (nécessite CDC VALIDE) |
| PUT | `/api/encadrant-entreprise/sprints/{id}` | Modifier sprint |
| DELETE | `/api/encadrant-entreprise/sprints/{id}` | Soft-delete sprint |
| POST | `/api/encadrant-entreprise/sprints/{id}/cloturer` | Clôturer `{observation}` → CLOTURE |
| GET | `/api/encadrant-entreprise/sprints/stage/{id}` | Lister sprints d'un stage |
| GET | `/api/etudiant/sprints/stage/{id}` | Lister sprints (étudiant, lecture seule) |

### Tâches ← **NEW v6**

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/encadrant-entreprise/taches/sprint/{id}` | Créer tâche `{titre, description, dateDebutPrevue, dateFinPrevue}` |
| PUT | `/api/encadrant-entreprise/taches/{id}` | Modifier tâche |
| DELETE | `/api/encadrant-entreprise/taches/{id}` | Soft-delete tâche |
| POST | `/api/encadrant-entreprise/taches/{id}/valider` | Valider → VALIDEE `{observation, note}` |
| POST | `/api/encadrant-entreprise/taches/{id}/refuser` | Refuser → REFUSEE `{observation}` |
| GET | `/api/encadrant-entreprise/taches/sprint/{id}` | Lister tâches d'un sprint (encadrant) |
| POST | `/api/etudiant/taches/{id}/demarrer` | Démarrer → EN_COURS (auto: Sprint passe EN_COURS) |
| POST | `/api/etudiant/taches/{id}/terminer` | Terminer → TERMINEE_PAR_ETUDIANT `{noteEtudiant}` |
| POST | `/api/etudiant/taches/{id}/reprendre` | Reprendre tâche refusée → EN_COURS |
| GET | `/api/etudiant/taches/sprint/{id}` | Lister tâches (étudiant, lecture seule) |

### Logs d'activité & Sessions (Admin) ← **NEW v6**

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/admin/logs` | Audit trail global paginé `?page=0&size=20` |
| GET | `/api/admin/logs/user/{userId}` | Logs d'un utilisateur spécifique |
| GET | `/api/admin/logs/entite/{entiteCible}/{entiteId}` | Logs filtrés par entité |
| GET | `/api/admin/sessions` | Historique sessions paginé |
| GET | `/api/admin/sessions/actives` | Sessions actives en temps-réel |
| GET | `/api/admin/sessions/user/{userId}` | Sessions d'un utilisateur |

### Matching & Recommandations ← **NEW v6**

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/etudiant/recommandations/offres/{etudiantId}?limit=10` | Offres recommandées par score IA |
| GET | `/api/entreprise/recommandations/candidats/{offreId}?limit=10` | Candidats classés par score matching |

### Microservice Python — Endpoints directs (port 8000)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/health` | Statut microservice |
| POST | `/cv/extract` | Extraction CV multipart |
| POST | `/modeles/` | Créer modèle depuis `.docx` |
| GET | `/modeles/` | Lister modèles |
| DELETE | `/modeles/{id}` | Supprimer modèle |
| POST | `/documents/generer` | Générer document rempli |
| GET | `/documents/telecharger/{filename}` | Télécharger |

---

## 6. Frontend — Pages & Fonctions

### Pages disponibles

| Page | Description | Statut |
|------|-------------|--------|
| `login.html` | Connexion | ✅ |
| `register.html` | Inscription multi-rôles | ✅ |
| `verify-email.html` | Vérification OTP | ✅ |
| `etudiant-dashboard.html` | Dashboard étudiant (CV, offres, candidatures) | ✅ |
| `entreprise-dashboard.html` | Dashboard entreprise | ✅ |
| `admin-dashboard.html` | Dashboard admin | ✅ |

### `js/api.js` — Fonctions principales

```javascript
// Auth
apiLogin(email, password)         apiRegister(...)
apiVerifyOtp(email, code)         apiLogout()           // ← NEW v5
apiForgotPassword(email)          apiResetPassword(...)

// CV
apiUploadCv(file)                 apiGetCvInfo()
apiGetCvStatut()                  apiReanalyseCv()

// Offres
apiGetOffres({page, size})        apiRechercherOffres({domaine, localisation, typeStage})
apiMesOffres()                    apiPublierOffre(data)
apiModifierOffre(id, data)        apiSupprimerOffre(id)

// Candidatures
apiPostuler(offreId, lettre)      apiGetCandidatures()
apiGetCandidaturesParOffre(id)    apiDeciderCandidature(id, statut, commentaire)
apiGetCandidatDetail(id)          // ← NEW v5

// Stages
apiGetStage()                     apiSignerConventionEntreprise(id)   // ← NEW v5
apiGetStagesEncadrantAcademique() apiSignerConventionEncadrant(id)    // ← NEW v5
apiGetStagesEncadrantEntreprise() apiDefinirMission(id, mission)      // ← NEW v5

// Rapports ← NEW v5
apiCreerRapport(stageId, data)    apiSoumettreRapport(id)
apiGetMesRapports(stageId)        apiGetRapportsEncadrant(stageId)
apiValiderRapport(id)             apiCommenterRapport(id, commentaire)

// Notifications ← NEW v5
apiGetNotifications()             apiGetNonLues()
apiMarquerLue(id)                 apiMarquerToutesLues()

// Documents
apiGenererDocument(typeId, data, format)
apiGetMesDocuments()              apiTelechargerDocument(uuid)
apiAuthentifierDocument(uuid)     // public

// Admin — Modèles
apiGetModeles()                   apiCreerModele(formData)
apiGetTypesDocuments()            apiPatchUserRole(id, role)     // ← NEW v5
apiGetAllUsers()                  apiPatchUserStatut(id, statut) // ← NEW v5
```

---

## 7. Analyse du Backlog

> **IDs conformes au backlog PDF (Sprint Planning SmartIntern AI — 138 pts, MoSCoW)**  
> EN = Entreprise · EA = Encadrant Académique · EE = Encadrant Entreprise · ET = Étudiant · AD = Admin · U = Tous acteurs

### Statut global par User Story — version6 (28 mai 2026)

| ID | Titre | Sprint | Statut | Note |
|----|-------|--------|--------|------|
| U-01 | Inscription multi-rôles (5 profils) | 1 | ✅ | |
| U-02 | Connexion JWT stateless | 1 | ✅ | BCrypt + blacklist logout |
| U-03 | Vérification email OTP | 1 | ✅ | 6 chiffres, 10 min |
| U-04 | Reset mot de passe | 1 | ✅ | email + OTP |
| U-05 | Profil utilisateur | 1 | ✅ | |
| ET-01 | Upload CV (PDF/JPG/PNG/WEBP) | 1 | ✅ | |
| ET-03 | Affichage données CV extraites | 1 | ✅ | |
| ET-04 | Scoring complétude CV | 1 | ✅ | scoreGlobal/100, 5 niveaux |
| ET-05 | Postuler à une offre de stage | 1 | ✅ | Anti-doublon inclus |
| EN-01 | Publier une offre de stage | 1 | ✅ | |
| EN-02 | Modifier / supprimer ses offres | 1 | ✅ | Repasse EN_ATTENTE si modifiée |
| EN-05 | Décision candidature (accepter/refuser) | 1 | ✅ | |
| AD-01 | Créer type de document | 1 | ✅ | |
| AD-02 | Upload modèle de document .docx | 1 | ✅ | Détection `[champs]` + zone QR rouge |
| ET-02 | Extraction IA asynchrone CV | 2 | ✅ | `@Async`, NVIDIA + OpenRouter fallback |
| ET-07 | Créer & soumettre rapport de stage | 2 | ✅ | BROUILLON → SOUMIS ← **NEW v5** |
| ET-08 | Consulter retours encadrant sur rapport | 2 | ✅ | VALIDE / REJETE visible ← **NEW v5** |
| EN-03 | Tableau des candidatures reçues | 2 | ✅ | |
| EN-06 | Profil complet candidat (côté Entreprise) | 2 | ✅ | ← **NEW v5** |
| EE-03 | Définir la mission du stagiaire | 2 | ✅ | PATCH `/stages/{id}/mission` ← **NEW v5** |
| EE-04 | Signer la convention de stage | 2 | ✅ | Entreprise + Encadrant ← **NEW v5** |
| EA-01 | Voir stages encadrés (académique) | 2 | ✅ | ← **NEW v5** |
| EA-02 | Valider rapport de stage | 2 | ✅ | SOUMIS → VALIDE ← **NEW v5** |
| EA-03 | Rejeter rapport avec commentaire | 2 | ✅ | SOUMIS → REJETE ← **NEW v5** |
| AD-05 | Dashboard statistiques admin | 2 | ⬜ | `/api/admin/stats` non implémenté |
| EA-04 | Recommandation IA offres | 3 | ⬜ | Non réalisé |
| AD-06 | Export données CSV | 3 | ⬜ | Non réalisé |
| AD-07 | Logs d'audit & sessions connexion | 3 | ✅ | LogActivite + SessionConnexion + 6 endpoints ← **NEW v6** |
| AD-08 | Créer stage depuis candidature acceptée | 3 | ✅ | |
| AD-09 | Assigner encadrants + dates + mission | 3 | ✅ | |
| AD-10 | Gestion expiration documents | 3 | 🔶 | `@Scheduled` ✅, révocation manuelle UI absente |
| EE-01 | Suivi stage (encadrant académique) | 3 | ✅ | Stages + rapports ← **NEW v5** |
| EE-02 | Suivi stage (encadrant entreprise) | 3 | ✅ | Vue stages + convention ← **NEW v5** |
| EE-05 | CDC + Sprints + Tâches (gestion projet) | 4 | ✅ | Machine à états complète, 10 endpoints, 31 tests ← **NEW v6** |
| AD-03 | Génération document officiel | 4 | 🔶 | Backend + microservice ✅, intégration UI partielle |
| AD-04 | QR code d'authentification | 4 | 🔶 | Logique ✅ (HTTP 200/410/404), intégration UI partielle |
| U-06 | Notifications email (événements) | 4 | ⬜ | Seul OTP implémenté |
| U-07 | Notifications in-app | 4 | ✅ | CRUD + marquer lu/non-lu ← **NEW v5** |
| U-08 | Centre de notifications | 4 | ✅ | `/api/notifications/*` ← **NEW v5** |
| EN-04 | Matching & recommandations IA | 4 | 🔶 | Scoring 5 composantes ✅, endpoints recommandation ✅, UI partielle |
| ET-06 | Télécharger ses documents | 4 | ⬜ | Endpoint ✅, UI absente |

**Légende :** ✅ Terminé · 🔶 Partiel · ⬜ Non réalisé · **NEW v5** = fusion version5 · **NEW v6** = développement personnel version6

---

## 8. Avancement par Sprint

### Sprint 1 — Auth + Base + Documents (36 pts) ✅ 100%

| US | Statut | Détails |
|----|--------|---------|
| U-01 Inscription | ✅ | 5 rôles (Étudiant, Entreprise, Admin, EncAcad, EncEntr) |
| U-02 Connexion JWT | ✅ | BCrypt + JWT stateless + JwtBlacklist |
| U-03 OTP email | ✅ | 6 chiffres, expiration 10 min |
| U-04 Reset MDP | ✅ | Email + OTP |
| U-05 Profil | ✅ | |
| ET-01 Upload CV | ✅ | PDF + JPG + PNG + WEBP |
| ET-03 Affichage CV | ✅ | |
| ET-04 Scoring CV | ✅ | 5 niveaux (INCOMPLET → EXCELLENT) |
| ET-05 Postuler | ✅ | Anti-doublon par offre |
| EN-01 Publier offre | ✅ | |
| EN-02 Gérer ses offres | ✅ | |
| EN-05 Décision candidature | ✅ | |
| AD-01 Type document | ✅ | |
| AD-02 Modèle .docx | ✅ | `[champs]` + rectangle QR rouge |
| **+ Logout JWT** | ✅ | `POST /logout` + blacklist en mémoire ← **NEW v5** |

**Sprint 1 : 36/36 pts → 100%**

---

### Sprint 2 — CV IA + Rapports + Encadrants (35 pts) 🟡 ~86%

| US | Statut | Détails |
|----|--------|---------|
| ET-02 Extraction IA async | ✅ | `@Async`, NVIDIA NIM + OpenRouter fallback |
| ET-07 Rapport hebdo/final | ✅ | BROUILLON → SOUMIS ← **NEW v5** |
| ET-08 Retours encadrant | ✅ | VALIDE / REJETE visibles ← **NEW v5** |
| EN-03 Tableau candidatures | ✅ | Filtres, tri date |
| EN-06 Profil candidat complet | ✅ | Détail étudiant pour l'entreprise ← **NEW v5** |
| EE-03 Définir mission | ✅ | PATCH `/stages/{id}/mission` ← **NEW v5** |
| EE-04 Signer convention | ✅ | Entreprise + EncadrantEntreprise ← **NEW v5** |
| EA-01 Suivi stages (acad.) | ✅ | ← **NEW v5** |
| EA-02 Valider rapport | ✅ | SOUMIS → VALIDE ← **NEW v5** |
| EA-03 Rejeter rapport | ✅ | SOUMIS → REJETE + commentaire ← **NEW v5** |
| AD-05 Dashboard stats | ⬜ | Non réalisé |

**Sprint 2 : ~30/35 pts → ~86%**

---

### Sprint 3 — Stages + Matching + Audit (27 pts) 🟡 ~70%

| US | Statut | Détails |
|----|--------|---------|
| EA-04 Recommandation IA offres | ⬜ | Non réalisé |
| AD-06 Export CSV | ⬜ | Non réalisé |
| **AD-07 Logs d'audit** | ✅ | LogActiviteController (3 endpoints) + SessionConnexionController (3 endpoints) ← **NEW v6** |
| AD-08 Créer stage depuis candidature | ✅ | `POST /api/admin/stages/depuis-candidature/{id}` |
| AD-09 Assigner encadrants | ✅ | + dates, sujet, mission |
| AD-10 Expiration documents | 🔶 | `@Scheduled` horaire ✅, révocation manuelle UI absente |
| EE-01 Suivi (enc. académique) | ✅ | Stages + rapports + validation ← **NEW v5** |
| EE-02 Suivi (enc. entreprise) | ✅ | Stages + convention + mission ← **NEW v5** |

**Sprint 3 : ~19/27 pts → ~70%** *(+3 pts AD-07 grâce à v6)*

---

### Sprint 4 — CDC/Sprints/Tâches + Documents + Notifications (41 pts) 🟡 ~66%

| US | Statut | Détails |
|----|--------|---------|
| **EE-05 CDC + Sprints + Tâches** | ✅ | Machine à états CDC/Sprint/Tâche, 10 endpoints, 31 tests unitaires ← **NEW v6** |
| AD-03 Génération document | 🔶 | Backend + microservice ✅, UI partielle |
| AD-04 QR code auth | 🔶 | HTTP 200/410/404 ✅, intégration UI partielle |
| U-06 Notifications email | ⬜ | Seul OTP — pas les événements métier |
| U-07 Notifications in-app | ✅ | CRUD + marquer lu/non-lu ← **NEW v5** |
| U-08 Centre notifications | ✅ | `/api/notifications/*` ← **NEW v5** |
| EN-04 Matching & reco IA | 🔶 | Scoring 5 composantes ✅, endpoints ✅, persistScores() ✅, UI partielle |
| ET-06 Télécharger documents | ⬜ | Endpoint `telecharger/{uuid}` ✅, UI absente |

**Sprint 4 : ~27/41 pts → ~66%** *(+8 pts EE-05 grâce à v6)*

---

## 9. Fonctionnalités Hors Backlog

| # | Fonctionnalité | Origine | Valeur est. |
|---|---------------|---------|------------|
| 1 | Microservice IA Python (FastAPI) — architecture complète | v6 perso | ~8 pts |
| 2 | Multi-provider IA (NVIDIA NIM + OpenRouter fallback) | v6 perso | ~5 pts |
| 3 | Signature HMAC-SHA256 des documents générés | v6 perso | ~5 pts |
| 4 | Détection zone QR rouge (DrawingML + VML) dans .docx | v6 perso | ~5 pts |
| 5 | Pré-génération UUID côté Spring (cohérence fichier↔MySQL) | v6 perso | ~3 pts |
| 6 | `@Scheduled` expiration horaire automatique | v6 perso | ~3 pts |
| 7 | `@Async` extraction CV non bloquante | v6 perso | ~3 pts |
| 8 | Page d'auth QR publique (sans JWT, HTTP 410/404) | v6 perso | ~3 pts |
| 9 | `registry.json` persistance modèles microservice | v6 perso | ~2 pts |
| 10 | Interface test microservice HTML (4 onglets) | v6 perso | ~2 pts |
| 11 | Interface test full-stack (auth+CV+docs) | v6 perso | ~3 pts |
| 12 | `duree_validite_jours` configurable par modèle | v6 perso | ~2 pts |
| 13 | Support .doc + .docx pour upload templates | v6 perso | ~1 pt |
| 14 | Secrets externalisés `.env` / variables d'env Spring | v6 perso | ~2 pts |
| 15 | `README.md` complet — analyse backlog + variables templates | v6 perso | ~2 pts |
| 16 | **`POST /api/auth/logout` + JwtBlacklist (mémoire)** | v5 | ~2 pts |
| 17 | **AdminController complet** (rôles, statuts, demandes stage) | v5 | ~3 pts |
| 18 | **Profil complet candidat** pour entreprise (EN-06) | v5 | ~3 pts |
| 19 | **DemandeStage** — entité + service + CRUD admin | v5 | ~3 pts |
| 20 | **TransactionTemplate** pattern pour audit fire-and-forget | v6 | ~3 pts |
| 21 | **31 tests unitaires** — SprintServiceTest (14) + TacheServiceTest (16) + BackendApplicationTests (1) | v6 | ~5 pts |
| 22 | **Matching IA** — scoring 5 composantes + `persistScores()` + 2 controllers recommandation | v6 | ~5 pts |

**Total estimé hors backlog : ~73 pts (22 fonctionnalités)**

---

## 10. Évolution Globale & Synthèse

### Calcul d'avancement — version6 (28 mai 2026)

| Catégorie | Avant v6 (v5 seul) | v6 actuel | Gain v6 |
|-----------|--------------------|-----------|---------|
| US terminées ✅ | 26 US | **32 US** | +6 (AD-07, EE-05, ET-07, ET-08, EN-06, EE-04) |
| US partielles 🔶 | 3 US | **4 US** | +1 (EN-04 matching) |
| US non réalisées ⬜ | 12 US | **5 US** | −7 |
| **Backlog accompli** | **~75%** | **~83%** (~115/138 pts) | **+8%** |
| Hors backlog | 19 feat / ~62 pts | **22 feat / ~73 pts** | +3 feat |

### Avancement par Sprint (PDF backlog — 138 pts total)

```
Sprint 1  ████████████████████  100%  (36/36 pts)   Auth + Base + Documents
Sprint 2  █████████████████░░░   86%  (~30/35 pts)  CV IA + Rapports + Encadrants
Sprint 3  ██████████████░░░░░░   70%  (~19/27 pts)  Stages + Matching + Audit
Sprint 4  █████████████░░░░░░░   66%  (~27/41 pts)  CDC/Sprint/Tâche + Docs + Notifs

Backlog global  ████████████████░░░░   83%  (~112/138 pts)
```

### Ce qui reste à faire (~23 pts de backlog non couverts)

| Priorité | User Story | Pts | Impact |
|----------|-----------|-----|--------|
| 🔴 HAUTE | **ET-06** — Connecter UI au téléchargement documents | ~3 | Utilisateurs bloqués sans UI |
| 🔴 HAUTE | **AD-05** — Dashboard statistiques admin | ~5 | Zéro visibilité sur les métriques |
| 🟡 MOY. | **U-06** — Notifications email (candidature, stage, rapport) | ~5 | EmailService partiel (seul OTP) |
| 🟡 MOY. | **AD-03/AD-04** — Finaliser intégration UI génération + QR | — | Backend 100% prêt |
| 🟡 MOY. | **EN-04** — Connecter recommandations IA à l'UI | ~5 | Endpoints prêts, affichage manquant |
| 🟢 BASSE | **EA-04** — Recommandation IA offres (côté entreprise) | ~8 | Valeur IA, périmètre large |
| 🟢 BASSE | **AD-06** — Export CSV | ~3 | Confort admin |

### Points forts du projet

- ✅ **Socle d'authentification** irréprochable (JWT, OTP, Logout, Blacklist)
- ✅ **Pipeline CV IA** complet et robuste (async, multi-formats, multi-providers)
- ✅ **Cycle de vie stage** complet : offre → candidature → stage → CDC → Sprint → Tâche → rapports → convention
- ✅ **Audit trail** — toutes les actions persistées (LogActivite) + sessions connexion temps-réel
- ✅ **Notifications in-app** fonctionnelles (création, lecture, compteur)
- ✅ **Génération documents avec QR** — fonctionnalité phare opérationnelle
- ✅ **Architecture microservice** — séparation claire Spring Boot ↔ Python
- ✅ **31 tests unitaires** — Sprint + Tâche machine à états entièrement couverte

### Recommandations prioritaires

1. **UI téléchargement documents** (ET-06) — backend 100% prêt, 1-2h de JavaScript
2. **Dashboard stats admin** (AD-05) — implémenter `GET /api/admin/stats` (CompteRenderer + agrégats JPA)
3. **Notifications email** (U-06) — étendre `EmailService` aux événements métier (candidature acceptée, stage créé, rapport validé)
4. **Recommandations IA dans l'UI** (EN-04) — les endpoints `/recommandations/offres` et `/candidats` sont prêts
5. **Config production** — CORS restreint, HTTPS, Nginx reverse-proxy, profil Spring `prod`

---

*Documentation mise à jour le 28 mai 2026 — `feature/version6` (fusion version5 + développement personnel v6)*  
*Backlog PDF : 41 US · 138 pts · Must Have 110 pts · **83% accompli (32 US complètes, 4 partielles, 5 restantes)***
