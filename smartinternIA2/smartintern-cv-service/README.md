# SmartIntern AI — CV Extraction & Document Service

lancer une un processus : venv\Scripts\uvicorn.exe main:app --host 0.0.0.0 --port 8000 --reload
Microservice Python FastAPI pour la plateforme SmartIntern AI.
Développé dans le cadre du PFA — ITEAM University.

---

## Vue d'ensemble

Ce microservice gère deux fonctionnalités principales :

1. **Extraction et scoring de CV** — Analyse automatique des CVs (PDF, DOCX, Images) via IA Claude et calcul du score de complétude.
2. **Génération automatique de documents** — Création de modèles de documents, génération PDF personnalisée avec QR code de vérification.

---

## Architecture

```
smartintern-cv-service/
├── main.py                  # Serveur FastAPI + tous les endpoints
├── document_service.py      # Moteur de génération de documents
├── requirements.txt         # Dépendances Python
├── .env.example             # Template de configuration
├── .env                     # Configuration locale (à créer)
├── start.sh                 # Script de démarrage
└── uploads/
    ├── modeles/             # Fichiers modèles (PDF/DOCX)
    ├── headers/             # Images d'en-tête (PNG/JPG)
    ├── footers/             # Images de pied de page (PNG/JPG)
    ├── scripts/             # Scripts Python générés par IA
    └── documents/           # Documents PDF générés
```

---

## Prérequis système

### Python
- Python 3.10 ou supérieur

### Dépendances système (à installer manuellement)

**Tesseract OCR** (pour les PDFs scannés et images) :
```bash
# Ubuntu / Debian
sudo apt-get install tesseract-ocr tesseract-ocr-fra tesseract-ocr-ara

# Windows
# Télécharger depuis : https://github.com/UB-Mannheim/tesseract/wiki
# Ajouter au PATH après installation
```

**Pandoc** (pour la conversion DOCX) :
```bash
# Ubuntu / Debian
sudo apt-get install pandoc

# Windows
# Télécharger depuis : https://pandoc.org/installing.html
```

---

## Installation

### 1. Cloner ou décompresser le projet

```bash
cd smartintern-cv-service
```

### 2. Créer un environnement virtuel (recommandé)

```bash
python -m venv venv

# Linux / Mac
source venv/bin/activate

# Windows
venv\Scripts\activate
```

### 3. Installer les dépendances Python

```bash
pip install -r requirements.txt
```

Sur Windows si erreur de permissions :
```bash
pip install -r requirements.txt --break-system-packages
```

### 4. Configurer l'environnement

```bash
cp .env.example .env
```

Ouvrez `.env` et renseignez :

```env
# Clé API Anthropic (OBLIGATOIRE)
ANTHROPIC_API_KEY=sk-ant-votre-cle-ici

# URL de base du service (pour les QR codes)
BASE_URL=http://localhost:8000

# Clé secrète pour les signatures HMAC
SIGNATURE_SECRET=ChangezCetteValeurEnProduction2024
```

Obtenez votre clé API sur : https://console.anthropic.com

### 5. Démarrer le service

```bash
# Linux / Mac
./start.sh

# Ou directement
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

Le service est accessible sur : http://localhost:8000

**Documentation interactive Swagger** : http://localhost:8000/docs

---

## Endpoints

### Santé

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `GET` | `/health` | Statut du service et configuration |

### CV — Extraction et Scoring

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `POST` | `/extract` | Extraction + structuration IA + score de complétude |
| `POST` | `/extract/text-only` | Extraction texte brut sans IA |
| `POST` | `/score` | Score d'un CV déjà structuré |

### Documents — Modèles

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `POST` | `/modeles/creer` | Créer un modèle (upload header/footer/modèle + analyse IA) |
| `GET` | `/modeles/{id}/champs` | Champs dynamiques d'un modèle |
| `GET` | `/types-documents` | Liste des types de documents disponibles |

### Documents — Génération

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `POST` | `/documents/generer` | Générer un PDF personnalisé |
| `POST` | `/documents/regenerer` | Régénérer (nouvelle version) |
| `GET` | `/documents/telecharger/{uuid}` | Télécharger le PDF généré |

### Documents — Vérification QR

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `GET` | `/documents/verifier/{uuid}` | Vérifier authenticité (lien QR code, public) |
| `POST` | `/documents/verifier` | Vérifier depuis le backend Java |

---

## Formats de données

### Réponse `/extract` (camelCase — compatible Java/Jackson)

```json
{
  "success": true,
  "cvStandardise": {
    "profil": {
      "nom": "Dupont",
      "prenom": "Jean",
      "email": "jean.dupont@example.com",
      "telephone": "+216 XX XXX XXX",
      "adresse": "Tunis, Tunisie",
      "titreProfessionnel": "Étudiant en Génie Logiciel",
      "resume": "Étudiant en 3ème année..."
    },
    "experiences": [
      {
        "poste": "Développeur Full Stack",
        "entreprise": "TechCorp",
        "dateDebut": "Juin 2023",
        "dateFin": "Août 2023",
        "description": "Développement d'une application web...",
        "lieu": "Tunis"
      }
    ],
    "formations": [...],
    "competences": {
      "techniques": ["Java", "Spring Boot", "Python"],
      "softSkills": ["Travail d'équipe", "Communication"],
      "outils": ["Git", "IntelliJ", "Docker"],
      "autres": []
    },
    "langues": [
      {"langue": "Français", "niveau": "Natif"},
      {"langue": "Anglais", "niveau": "Avancé"}
    ],
    "certifications": ["AWS Cloud Practitioner"],
    "projets": ["Application de gestion de stages"],
    "interets": ["Open source", "IA"],
    "scoreCompletude": 78.5
  },
  "texteBrut": "...",
  "nbPages": 2,
  "formatDetecte": "pdf",
  "methodeExtraction": "pdf_text",
  "message": "CV extrait, structuré et scoré avec succès",
  "score": {
    "scoreGlobal": 78.5,
    "scoreSur100": 78.5,
    "niveau": "BON",
    "details": {
      "profil": {"score": 20, "max": 25, "pourcentage": 80, "manquant": ["Adresse"], "complet": false},
      "formations": {"score": 18, "max": 20, "pourcentage": 90, "manquant": [], "complet": true},
      "experiences": {"score": 16, "max": 20, "pourcentage": 80, "manquant": [], "complet": true},
      "competences": {"score": 16, "max": 20, "pourcentage": 80, "manquant": [], "complet": true},
      "langues": {"score": 5, "max": 5, "pourcentage": 100, "manquant": [], "complet": true},
      "bonus": {"score": 3.5, "max": 10, "pourcentage": 35, "manquant": ["Projets"], "complet": false}
    },
    "recommandations": [
      "Indiquez votre ville ou région",
      "Décrivez vos projets personnels ou académiques"
    ],
    "sectionsCompletes": 4,
    "sectionsTotales": 6
  }
}
```

### Requête `/modeles/creer` (multipart/form-data)

```
POST /modeles/creer
Content-Type: multipart/form-data

modele_id         : "uuid-du-modele"
nom_modele        : "Convention de Stage ITEAM"
type_document     : "convention_stage"
duree_validite_jours : 365
fichier_modele    : [fichier PDF ou DOCX]
fichier_header    : [image PNG/JPG - optionnel]
fichier_footer    : [image PNG/JPG - optionnel]
```

### Requête `/documents/generer` (JSON)

```json
{
  "modeleId": "uuid-du-modele",
  "cheminScript": "uploads/scripts/uuid_script.py",
  "cheminHeader": "uploads/headers/uuid_header.png",
  "cheminFooter": "uploads/footers/uuid_footer.png",
  "typeDocument": "convention_stage",
  "donneesProfil": {
    "nom": "Dupont",
    "prenom": "Jean",
    "email": "jean.dupont@etudiant.fr",
    "filiere": "Génie Logiciel",
    "classe": "3ING",
    "nom_entreprise": "TechCorp Tunisia",
    "adresse_entreprise": "Tunis, Tunisie",
    "date_debut_stage": "01/06/2024",
    "date_fin_stage": "31/08/2024",
    "encadrant_entreprise": "M. Ahmed Ben Ali",
    "encadrant_academique": "Mme. Sonia Trabelsi"
  },
  "nomEtablissement": "ITEAM University",
  "dureeValiditeJours": 365
}
```

### Réponse `/documents/generer`

```json
{
  "success": true,
  "message": "Document généré avec succès",
  "document": {
    "docUuid": "550e8400-e29b-41d4-a716-446655440000",
    "modeleId": "uuid-du-modele",
    "typeDocument": "convention_stage",
    "cheminFichier": "uploads/documents/550e8400-....pdf",
    "urlFichier": "http://localhost:8000/documents/telecharger/550e8400-...",
    "urlVerification": "http://localhost:8000/documents/verifier/550e8400-...",
    "dateGeneration": "2024-04-23T14:30:00",
    "dateExpiration": "2025-04-23T14:30:00",
    "signature": "a3f8b2c1d4e5...",
    "statut": "VALIDE",
    "tailleOctets": 245760,
    "nomEtablissement": "ITEAM University"
  }
}
```

---

## Barème de scoring CV

| Section | Points | Critères principaux |
|---------|--------|---------------------|
| Profil de base | 25 | Nom+prénom(5), Email(4), Tél(3), Titre(5), Résumé(5), Localisation(3) |
| Formations | 20 | ≥1 formation(10), Spécialité(3), Dates(3), Lieu(2), Bonus≥2(2) |
| Expériences | 20 | ≥1 expérience(8), Descriptions détaillées(6), Dates(3), Lieu(1), Bonus≥2(2) |
| Compétences | 20 | Techniques≥5(8), Outils≥3(5), Soft skills≥3(5), Diversité≥10(2) |
| Langues | 5 | ≥1 langue(3), Niveau renseigné(2) |
| Bonus | 10 | Certifications(3), Projets(4), Intérêts(3) |

| Niveau | Score |
|--------|-------|
| INSUFFISANT | < 40% |
| MOYEN | 40–65% |
| BON | 65–85% |
| EXCELLENT | ≥ 85% |

---

## Sécurité des documents

### QR Code
Chaque document généré contient un QR code avec :
- URL de vérification unique
- UUID du document
- Type de document
- Établissement
- Dates de génération et d'expiration
- Signature HMAC partielle

### Signature HMAC-SHA256
Chaque document est signé avec une clé secrète (configurée dans `.env`).
La vérification compare la signature stockée avec une recalculée à la volée.

### Expiration automatique
Après la date d'expiration, le QR code retourne statut `EXPIRE` (HTTP 410).

---

## Intégration avec le backend Java Spring Boot

### Configuration `application.properties`

```properties
cv.service.url=http://localhost:8000
```

### Service Java `CvExtractionService.java`

Le service Java appelle le microservice via `RestTemplate`.
Les réponses JSON en camelCase sont automatiquement désérialisées
par Jackson sans configuration supplémentaire.

Champs mappés :
| Python (snake_case) | JSON (camelCase) | Java field |
|---------------------|------------------|------------|
| `cv_standardise` | `cvStandardise` | `cvStandardise` |
| `texte_brut` | `texteBrut` | `texteBrut` |
| `nb_pages` | `nbPages` | `nbPages` |
| `score_completude` | `scoreCompletude` | `scoreCompletude` |
| `soft_skills` | `softSkills` | `softSkills` |
| `score_global` | `scoreGlobal` | `scoreGlobal` |
| `score_sur_100` | `scoreSur100` | `scoreSur100` |
| `sections_completes` | `sectionsCompletes` | `sectionsCompletes` |

---

## Tests rapides avec curl

### Santé du service
```bash
curl http://localhost:8000/health
```

### Extraire un CV
```bash
curl -X POST http://localhost:8000/extract \
  -F "file=@mon_cv.pdf"
```

### Scorer un CV structuré
```bash
curl -X POST http://localhost:8000/score \
  -H "Content-Type: application/json" \
  -d '{"profil": {"nom": "Dupont", "prenom": "Jean", "email": "j@j.fr"}, "experiences": [], "formations": [], "competences": {"techniques": [], "softSkills": [], "outils": [], "autres": []}, "langues": [], "certifications": [], "projets": [], "interets": []}'
```

### Créer un modèle de document
```bash
curl -X POST http://localhost:8000/modeles/creer \
  -F "modele_id=mon-modele-uuid" \
  -F "nom_modele=Convention de Stage" \
  -F "type_document=convention_stage" \
  -F "duree_validite_jours=365" \
  -F "fichier_modele=@convention_modele.pdf" \
  -F "fichier_header=@header.png" \
  -F "fichier_footer=@footer.png"
```

### Générer un document
```bash
curl -X POST http://localhost:8000/documents/generer \
  -H "Content-Type: application/json" \
  -d '{
    "modeleId": "mon-modele-uuid",
    "cheminScript": "uploads/scripts/mon-modele-uuid_script.py",
    "typeDocument": "convention_stage",
    "donneesProfil": {"nom": "Dupont", "prenom": "Jean"},
    "nomEtablissement": "ITEAM University",
    "dureeValiditeJours": 365
  }'
```

### Vérifier un document
```bash
curl "http://localhost:8000/documents/verifier/550e8400-e29b-41d4-a716-446655440000?date_generation=2024-04-23T14:30:00&date_expiration=2025-04-23T14:30:00&signature=abc123"
```

---

## Variables d'environnement

| Variable | Obligatoire | Défaut | Description |
|----------|-------------|--------|-------------|
| `ANTHROPIC_API_KEY` | ✅ Oui | — | Clé API Claude (console.anthropic.com) |
| `CV_SERVICE_PORT` | Non | `8000` | Port d'écoute |
| `CV_SERVICE_HOST` | Non | `0.0.0.0` | Hôte d'écoute |
| `BASE_URL` | Non | `http://localhost:8000` | URL publique (pour QR codes) |
| `SIGNATURE_SECRET` | Non | valeur par défaut | Clé HMAC signature documents |

---

## Dépendances Python

| Bibliothèque | Version | Usage |
|-------------|---------|-------|
| `fastapi` | 0.115.0 | Framework API |
| `uvicorn` | 0.30.6 | Serveur ASGI |
| `anthropic` | ≥0.50.0 | IA Claude (extraction + génération) |
| `pdfplumber` | 0.11.4 | Extraction PDF texte natif |
| `pymupdf` | 1.24.9 | OCR PDF scanné + manipulation PDF |
| `pypdf` | 4.3.1 | Lecture métadonnées PDF |
| `pytesseract` | 0.3.13 | OCR images (Tesseract) |
| `Pillow` | 10.4.0 | Traitement images |
| `python-docx` | 1.1.2 | Lecture fichiers DOCX |
| `reportlab` | 4.2.2 | Génération PDF |
| `qrcode[pil]` | 7.4.2 | Génération QR codes |
| `cryptography` | 42.0.8 | Signature HMAC |
| `python-dotenv` | 1.0.1 | Chargement fichier .env |
| `pydantic` | 2.8.2 | Validation données |

---

## Entités Java correspondantes

```
TypeDocument     ←→  /types-documents
ModeleDocument   ←→  /modeles/creer
Document         ←→  /documents/generer (métadonnées)
DocumentGenere   ←→  /documents/generer (résultat complet)
CVStandardise    ←→  /extract (cvStandardise dans la réponse)
Profil           ←→  cvStandardise.profil
Experience       ←→  cvStandardise.experiences[]
Formation        ←→  cvStandardise.formations[]
Competence       ←→  cvStandardise.competences
Langue           ←→  cvStandardise.langues[]
```

---

## Auteurs

Projet PFA — SmartIntern AI
ITEAM University — 2024/2025

