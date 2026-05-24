# SmartIntern AI — Service IA (Microservice FastAPI)

**Démarrage :** `venv\Scripts\uvicorn.exe main:app --host 0.0.0.0 --port 8000 --reload`

Microservice Python FastAPI de SmartIntern AI.
Développé dans le cadre du PFA — ITEAM University.

---

## Vue d'ensemble

Ce microservice gère trois modules :

1. **Extraction et scoring de CV** — Analyse de CVs (PDF, DOCX, Images) via IA multi-provider (NVIDIA NIM + OpenRouter fallback) et calcul du score de complétude.
2. **Gestion des modèles de documents** — Upload de fichiers Word (.docx), détection automatique des champs `[champ]` et des zones QR rouges, registre JSON auto-incrémenté.
3. **Génération de documents** — Remplissage des marqueurs `[champ]`, insertion QR code dans la zone rouge, export DOCX (et PDF optionnel via LibreOffice), vérification HMAC.

---

## Architecture

```
smartintern-ai-service/
├── main.py                          # Serveur FastAPI + route /health
├── config.py                        # Settings (ports, chemins, clés API)
├── requirements.txt                 # Dépendances Python
├── .env.example                     # Template de configuration
├── .env                             # Configuration locale (à créer)
├── test-interface.html              # Interface de test HTML (port 8000)
├── app/
│   ├── core/
│   │   └── ai_client.py             # Client IA multi-provider (NVIDIA + OpenRouter)
│   ├── models/
│   │   └── document.py              # Modèles Pydantic partagés
│   └── modules/
│       ├── cv_extraction/
│       │   ├── router.py            # Routes : /cv/extract, /cv/extract/text-only
│       │   └── service.py           # Extraction OCR + structuration IA
│       ├── document_templates/
│       │   ├── router.py            # Routes : /modeles/*
│       │   └── service.py           # Gestion registry.json + détection champs
│       └── document_generation/
│           ├── router.py            # Routes : /documents/*
│           └── service.py           # python-docx + QR code + HMAC
└── templates_storage/
    ├── registry.json                # Registre des modèles (auto-incrémenté)
    └── template_{id}.docx           # Fichiers Word des modèles
```

---

## Prérequis système

### Python
- Python 3.10 ou supérieur

### Dépendances système optionnelles

**Tesseract OCR** (pour les PDFs scannés et images) :
```bash
# Ubuntu / Debian
sudo apt-get install tesseract-ocr tesseract-ocr-fra

# Windows
# Télécharger depuis : https://github.com/UB-Mannheim/tesseract/wiki
# Ajouter au PATH après installation
```

**LibreOffice** (pour la conversion DOCX → PDF) :
```bash
# Ubuntu / Debian
sudo apt-get install libreoffice

# Windows
# Télécharger depuis : https://www.libreoffice.org
```

---

## Installation

```bash
# 1. Se placer dans le répertoire
cd smartintern-ai-service

# 2. Créer l'environnement virtuel
python -m venv venv

# Windows
venv\Scripts\activate

# Linux / Mac
source venv/bin/activate

# 3. Installer les dépendances
pip install -r requirements.txt

# 4. Configurer l'environnement
cp .env.example .env
```

Ouvrez `.env` et renseignez vos clés :

```env
# Provider IA primaire (NVIDIA NIM)
NVIDIA_API_KEY=nvapi-votre-cle-ici
NVIDIA_MODEL=meta/llama-3.1-70b-instruct

# Provider IA fallback (OpenRouter)
OPENROUTER_API_KEY=sk-or-votre-cle-ici
OPENROUTER_MODEL=meta-llama/llama-3.1-8b-instruct:free

# Clé secrète pour les signatures HMAC des documents
SIGNATURE_SECRET=ChangezCetteValeurEnProduction2024

# URL de base du microservice (utilisée dans les download_url)
BASE_URL=http://localhost:8000
```

```bash
# 5. Démarrer le service
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
# ou Windows :
venv\Scripts\uvicorn.exe main:app --host 0.0.0.0 --port 8000 --reload
```

Service accessible sur : **http://localhost:8000**  
Documentation Swagger interactive : **http://localhost:8000/docs**

---

## Endpoints

### Santé

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `GET` | `/health` | Statut du service, providers IA actifs, modules |

### CV — Extraction et Scoring (préfixe `/cv`)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `POST` | `/cv/extract` | Extraction + structuration IA + score de complétude |
| `POST` | `/cv/extract/text-only` | Extraction texte brut sans IA |

### Modèles de documents (préfixe `/modeles`)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `POST` | `/modeles/` | Créer un modèle depuis un fichier .docx uploadé |
| `POST` | `/modeles/from-url` | Créer un modèle depuis une URL HTTP/HTTPS ou chemin local |
| `GET` | `/modeles/` | Lister tous les modèles (`?id_type_document=N` pour filtrer) |
| `GET` | `/modeles/{id}` | Détail d'un modèle (champs, QR, dates) |
| `GET` | `/modeles/files/{filename}` | Télécharger le fichier .docx d'un modèle |
| `DELETE` | `/modeles/{id}` | Supprimer un modèle et son fichier |

### Génération de documents (préfixe `/documents`)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `POST` | `/documents/generer` | Générer un document depuis un modèle Word |
| `GET` | `/documents/telecharger/{filename}` | Télécharger un document généré (DOCX ou PDF) |
| `GET` | `/documents/verifier/{doc_uuid}` | Vérifier authenticité HMAC (public, depuis QR) |
| `POST` | `/documents/verifier` | Vérifier par POST (depuis backend Java) |

---

## Formats de données

### POST `/cv/extract` — Réponse (camelCase — compatible Java/Jackson)

```json
{
  "success": true,
  "cvStandardise": {
    "profil": {
      "nom": "Dupont", "prenom": "Jean",
      "email": "jean.dupont@example.com",
      "telephone": "+216 XX XXX XXX",
      "titreProfessionnel": "Étudiant en Génie Logiciel"
    },
    "experiences": [
      { "poste": "Dev Full Stack", "entreprise": "TechCorp", "periode": "2023" }
    ],
    "formations": [...],
    "competences": {
      "techniques": ["Java", "Python"],
      "softSkills": ["Travail d'équipe"],
      "outils": ["Git", "Docker"],
      "autres": []
    },
    "langues": [{"langue": "Français", "niveau": "Natif"}],
    "certifications": [], "projets": [], "interets": []
  },
  "score": {
    "scoreSur100": 78.5,
    "niveau": "BON",
    "details": { "profil": {"pourcentage": 80}, "formations": {"pourcentage": 90}, ... },
    "recommandations": ["Indiquez votre adresse"]
  },
  "nbPages": 2,
  "methodeExtraction": "pdf_text"
}
```

### POST `/modeles/` — Requête (multipart/form-data)

| Champ | Type | Obligatoire | Description |
|-------|------|-------------|-------------|
| `fichier` | File (.docx) | ✅ Oui | Fichier Word servant de modèle |
| `id_type_document` | int | ✅ Oui | ID du type de document (correspond à `TypeDocument.id` Spring Boot) |

### POST `/modeles/` — Réponse

```json
{
  "success": true,
  "id_modele_document": 3,
  "url_fichier_modele": "/modeles/files/template_3.docx",
  "id_type_document": 1,
  "champs_detectes": ["nom_etudiant", "prenom_etudiant", "date_debut", "nom_entreprise"],
  "a_zone_qrcode": true,
  "message": "Modèle #3 créé — 4 champ(s) détecté(s), zone QR : oui"
}
```

### GET `/modeles/{id}` — Réponse

```json
{
  "success": true,
  "template": {
    "id_modele_document": 3,
    "id_type_document": 1,
    "url_fichier_modele": "/modeles/files/template_3.docx",
    "champs_detectes": ["nom_etudiant", "date_debut"],
    "a_zone_qrcode": true,
    "date_creation": "2025-06-01T10:00:00"
  },
  "message": ""
}
```

### POST `/documents/generer` — Requête (JSON)

```json
{
  "id_modele_document": 3,
  "data": {
    "nom_etudiant": "Jean Dupont",
    "prenom_etudiant": "Jean",
    "date_debut": "2025-06-01",
    "nom_entreprise": "TechCorp SAS",
    "encadrant": "M. Ben Ali"
  },
  "qr_data": "https://smartintern.ai/api/documents/550e8400-e29b-41d4-a716-446655440000/authentifier",
  "output_format": "docx",
  "doc_id": "550e8400-e29b-41d4-a716-446655440000",
  "filename": "convention_jean_dupont"
}
```

> **`doc_id`** : UUID pré-généré par Spring Boot pour que le nom du fichier corresponde à l'URL d'authentification.  
> **`qr_data`** : URL publique du lien d'authentification Spring Boot ; encodée dans le QR code inséré dans la zone rouge.

### POST `/documents/generer` — Réponse

```json
{
  "success": true,
  "document": {
    "document_id": "550e8400-e29b-41d4-a716-446655440000",
    "filename": "550e8400-e29b-41d4-a716-446655440000.docx",
    "format": "docx",
    "size_bytes": 54321,
    "download_url": "/documents/telecharger/550e8400-e29b-41d4-a716-446655440000.docx"
  },
  "champs_non_remplis": [],
  "message": "Document généré avec succès (DOCX, 53 KB)"
}
```

---

## Architecture du flux — Génération de documents

```
Spring Boot                              Microservice Python
──────────────────────────────────────   ──────────────────────────────────
1. Étudiant POST /api/etudiant/documents/generer
2. Spring Boot pré-génère docUuid = UUID.randomUUID()
3. authUrl = "{baseUrl}/api/documents/{docUuid}/authentifier"
4. POST /documents/generer ─────────────────────────────────────────►
   { id_modele_document, data, qr_data=authUrl, doc_id=docUuid }
                                         5. Charger template_{id}.docx
                                         6. Remplacer [champ] ← data{}
                                         7. Générer QR PNG(qr_data)
                                         8. Insérer QR dans zone rouge
                                         9. Sauver {docUuid}.docx
◄───────────────────────────────────────10. { document_id, download_url }
11. Persister Document + DocumentGenere en MySQL
12. Retourner { docUuid, urlAuthentification, urlTelechargement }
```

---

## Sécurité des documents

### Lien d'authentification (nouveau flux — recommandé)
Le QR code encode l'URL publique Spring Boot :
```
https://smartintern.ai/api/documents/{uuid}/authentifier
```
Cette page retourne les informations du document (type, dates, étudiant, établissement, logo).
Le lien est **automatiquement désactivé** quand le document expire (HTTP 410 Gone).

### Signature HMAC-SHA256 (flux legacy — vérification autonome)
Chaque document est signé lors de la génération.
`GET /documents/verifier/{uuid}` recalcule la signature et retourne :
- `VALIDE` — document authentique et non expiré
- `EXPIRE` — document valide mais périmé (HTTP 410)
- `FALSIFIE` — signature HMAC invalide (HTTP 404)
- `INTROUVABLE` — fichier absent du serveur (HTTP 404)

---

## Intégration avec Spring Boot

### `application.properties`

```properties
cv.service.url=http://localhost:8000
app.base.url=${APP_BASE_URL:http://localhost:8081}
```

### Appels depuis `ModeleDocumentService.java`

```
POST /modeles/                (multipart: fichier + id_type_document)
→ Stocke id_modele_document dans ModeleDocument.idMicroservice
```

### Appels depuis `DocumentService.java`

```
POST /documents/generer       (JSON: id_modele_document, data, qr_data, doc_id)
→ Retourne document.download_url pour persistance dans DocumentGenere
```

### Correspondance champs JSON ↔ Java (camelCase)

| Python (snake_case) | JSON (camelCase) | Java field |
|---------------------|------------------|------------|
| `cv_standardise` | `cvStandardise` | `cvStandardise` |
| `texte_brut` | `texteBrut` | `texteBrut` |
| `nb_pages` | `nbPages` | `nbPages` |
| `score_sur_100` | `scoreSur100` | `scoreSur100` |
| `soft_skills` | `softSkills` | `softSkills` |

---

## Tests rapides avec curl

### Santé du service
```bash
curl http://localhost:8000/health
```

### Extraire un CV
```bash
curl -X POST http://localhost:8000/cv/extract \
  -F "file=@mon_cv.pdf"
```

### Créer un modèle de document
```bash
curl -X POST http://localhost:8000/modeles/ \
  -F "fichier=@convention_modele.docx" \
  -F "id_type_document=1"
```

### Lister les modèles
```bash
curl http://localhost:8000/modeles/
# Filtrer par type :
curl "http://localhost:8000/modeles/?id_type_document=1"
```

### Générer un document
```bash
curl -X POST http://localhost:8000/documents/generer \
  -H "Content-Type: application/json" \
  -d '{
    "id_modele_document": 1,
    "data": {
      "nom": "Dupont",
      "prenom": "Jean",
      "date_debut": "2025-06-01",
      "nom_entreprise": "TechCorp"
    },
    "qr_data": "https://smartintern.ai/api/documents/abc123/authentifier",
    "output_format": "docx"
  }'
```

### Vérifier un document (HMAC legacy)
```bash
curl "http://localhost:8000/documents/verifier/550e8400-e29b-41d4-a716-446655440000"
```

---

## Variables d'environnement

| Variable | Obligatoire | Défaut | Description |
|----------|-------------|--------|-------------|
| `NVIDIA_API_KEY` | Recommandé | — | Clé API NVIDIA NIM (provider primaire) |
| `OPENROUTER_API_KEY` | Recommandé | — | Clé API OpenRouter (fallback automatique) |
| `NVIDIA_MODEL` | Non | `meta/llama-3.1-70b-instruct` | Modèle NVIDIA NIM |
| `OPENROUTER_MODEL` | Non | `meta-llama/llama-3.1-8b-instruct:free` | Modèle OpenRouter |
| `SIGNATURE_SECRET` | Non | valeur par défaut | Clé HMAC signature documents |
| `BASE_URL` | Non | `http://localhost:8000` | URL publique (pour download_url) |
| `SERVICE_PORT` | Non | `8000` | Port d'écoute |

> Si aucune clé IA n'est configurée, l'extraction CV fonctionne en mode texte brut (OCR/PDF) sans structuration IA.

---

## Dépendances Python principales

| Bibliothèque | Usage |
|-------------|-------|
| `fastapi` + `uvicorn` | Framework API + serveur ASGI |
| `python-docx` | Lecture/écriture fichiers Word (.docx) |
| `qrcode[pil]` | Génération QR codes PNG |
| `Pillow` | Traitement images |
| `pdfplumber` | Extraction texte PDF natif |
| `pymupdf` | OCR PDF scanné |
| `pytesseract` | OCR images |
| `httpx` | Appels HTTP vers providers IA |
| `pydantic` | Validation données |
| `python-dotenv` | Chargement `.env` |

---

## Entités Java correspondantes

```
TypeDocument     ←→  id_type_document dans POST /modeles/
ModeleDocument   ←→  id_modele_document retourné par POST /modeles/
Document         ←→  Métadonnées persistées après POST /documents/generer
DocumentGenere   ←→  Fichier généré, lié à Document
CvStandardise    ←→  cvStandardise dans la réponse de POST /cv/extract
```

---

## Auteurs

Projet PFA — SmartIntern AI  
ITEAM University — 2024/2025
