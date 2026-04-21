"""
SmartIntern AI — CV Extraction Microservice
FastAPI + pdfplumber + PyMuPDF + pytesseract + pandoc + Claude AI

Supporte : PDF (texte + scanné), Images (JPG/PNG), DOCX/DOC
Extrait et structure les informations clés quel que soit le design du CV.
"""

import os
import json
import tempfile
import subprocess
import logging
from typing import Optional
from pathlib import Path

import pdfplumber
import fitz  # PyMuPDF
import pytesseract
from PIL import Image
import anthropic
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# ── Logging ────────────────────────────────────────────────────────────────
logging.basicConfig(level=logging.INFO, format="%(levelname)s │ %(name)s │ %(message)s")
logger = logging.getLogger("cv-service")

# ── FastAPI ────────────────────────────────────────────────────────────────
app = FastAPI(
    title="SmartIntern AI — CV Extraction Service",
    description="Microservice d'extraction et de standardisation des CVs (PDF, Image, DOCX)",
    version="1.1.0",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Anthropic ──────────────────────────────────────────────────────────────
client = anthropic.Anthropic(api_key=os.environ.get("ANTHROPIC_API_KEY", ""))

# ── Types acceptés ─────────────────────────────────────────────────────────
ACCEPTED_TYPES = {
    "application/pdf":           "pdf",
    "image/jpeg":                "image",
    "image/jpg":                 "image",
    "image/png":                 "image",
    "image/webp":                "image",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "docx",
    "application/msword":        "docx",
    "application/octet-stream":  "auto",  # déterminé par extension
}
ACCEPTED_EXTENSIONS = {".pdf", ".jpg", ".jpeg", ".png", ".webp", ".docx", ".doc"}

# ── Pydantic models ────────────────────────────────────────────────────────

class Profil(BaseModel):
    nom: Optional[str] = None
    prenom: Optional[str] = None
    email: Optional[str] = None
    telephone: Optional[str] = None
    adresse: Optional[str] = None
    nationalite: Optional[str] = None
    date_naissance: Optional[str] = None
    titre_professionnel: Optional[str] = None
    resume: Optional[str] = None

class Experience(BaseModel):
    poste: str
    entreprise: str
    periode: Optional[str] = None
    date_debut: Optional[str] = None
    date_fin: Optional[str] = None
    description: Optional[str] = None
    lieu: Optional[str] = None

class Formation(BaseModel):
    diplome: str
    etablissement: str
    periode: Optional[str] = None
    date_debut: Optional[str] = None
    date_fin: Optional[str] = None
    specialite: Optional[str] = None
    lieu: Optional[str] = None

class Competences(BaseModel):
    techniques: list[str] = Field(default_factory=list)
    soft_skills: list[str] = Field(default_factory=list)
    outils: list[str] = Field(default_factory=list)
    autres: list[str] = Field(default_factory=list)

class Langue(BaseModel):
    langue: str
    niveau: Optional[str] = None

class CvStandardise(BaseModel):
    profil: Profil
    experiences: list[Experience] = Field(default_factory=list)
    formations: list[Formation] = Field(default_factory=list)
    competences: Competences = Field(default_factory=Competences)
    langues: list[Langue] = Field(default_factory=list)
    certifications: list[str] = Field(default_factory=list)
    projets: list[str] = Field(default_factory=list)
    interets: list[str] = Field(default_factory=list)

class CvResponse(BaseModel):
    success: bool
    cv_standardise: Optional[CvStandardise] = None
    texte_brut: Optional[str] = None
    nb_pages: int = 0
    format_detecte: str = ""
    methode_extraction: str = ""
    message: str = ""


# ══════════════════════════════════════════════════════════════════════════
# EXTRACTEURS PAR FORMAT
# ══════════════════════════════════════════════════════════════════════════

def extraire_depuis_pdf(chemin: str) -> tuple[str, int, str]:
    """
    PDF : essaie d'abord pdfplumber (layout-aware).
    Si le texte est trop court → PDF scanné → fallback OCR via PyMuPDF.
    Retourne (texte, nb_pages, methode)
    """
    texte_pages = []
    nb_pages = 0

    # ── Tentative 1 : extraction texte natif ──────────────────────────────
    try:
        with pdfplumber.open(chemin) as pdf:
            nb_pages = len(pdf.pages)
            for page in pdf.pages:
                t = page.extract_text(layout=True) or page.extract_text() or ""
                texte_pages.append(t)

        texte = "\n\n--- PAGE ---\n\n".join(texte_pages).strip()

        if len(texte) >= 200:
            logger.info(f"PDF texte natif OK ({len(texte)} chars, {nb_pages} pages)")
            return texte, nb_pages, "pdf_text"

    except Exception as e:
        logger.warning(f"pdfplumber erreur: {e}")

    # ── Tentative 2 : OCR (PDF scanné) ────────────────────────────────────
    logger.info("PDF scanné détecté → OCR par page")
    doc = fitz.open(chemin)
    nb_pages = len(doc)
    texte_pages_ocr = []

    for i, page in enumerate(doc):
        # Rasteriser la page à 200 DPI pour un bon OCR
        mat = fitz.Matrix(200 / 72, 200 / 72)
        pix = page.get_pixmap(matrix=mat, colorspace=fitz.csRGB)

        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp_img:
            pix.save(tmp_img.name)
            img = Image.open(tmp_img.name)
            texte_ocr = pytesseract.image_to_string(img, lang="fra+eng")
            texte_pages_ocr.append(texte_ocr)
            os.unlink(tmp_img.name)

    texte = "\n\n--- PAGE ---\n\n".join(texte_pages_ocr).strip()
    logger.info(f"PDF OCR OK ({len(texte)} chars)")
    return texte, nb_pages, "pdf_ocr"


def extraire_depuis_image(chemin: str) -> tuple[str, int, str]:
    """
    Image JPG/PNG/WEBP : OCR avec Tesseract (français + anglais).
    Retourne (texte, 1, 'image_ocr')
    """
    img = Image.open(chemin)

    # Amélioration qualité si image trop petite
    w, h = img.size
    if w < 1000:
        scale = 1000 / w
        img = img.resize((int(w * scale), int(h * scale)), Image.LANCZOS)
        logger.info(f"Image redimensionnée: {img.size}")

    # Convertir en RGB si nécessaire
    if img.mode not in ("RGB", "L"):
        img = img.convert("RGB")

    texte = pytesseract.image_to_string(img, lang="fra+eng")
    logger.info(f"Image OCR OK ({len(texte)} chars)")
    return texte.strip(), 1, "image_ocr"


def extraire_depuis_docx(chemin: str) -> tuple[str, int, str]:
    """
    DOCX/DOC : conversion via pandoc → markdown puis texte brut.
    Retourne (texte, 1, 'docx_pandoc')
    """
    try:
        result = subprocess.run(
            ["pandoc", chemin, "-t", "plain", "--wrap=none"],
            capture_output=True, text=True, timeout=30
        )
        if result.returncode == 0 and result.stdout.strip():
            logger.info(f"DOCX pandoc OK ({len(result.stdout)} chars)")
            return result.stdout.strip(), 1, "docx_pandoc"
    except Exception as e:
        logger.warning(f"pandoc erreur: {e}")

    # Fallback python-docx
    try:
        from docx import Document
        doc = Document(chemin)
        paragraphes = [p.text for p in doc.paragraphs if p.text.strip()]
        texte = "\n".join(paragraphes)
        logger.info(f"DOCX python-docx fallback OK ({len(texte)} chars)")
        return texte, 1, "docx_python"
    except Exception as e:
        logger.error(f"python-docx erreur: {e}")
        raise HTTPException(status_code=500, detail=f"Impossible de lire le fichier DOCX: {e}")


def detecter_format(filename: str, content_type: str) -> str:
    """Détermine le format du fichier à partir de l'extension + content-type."""
    ext = Path(filename or "").suffix.lower()
    if ext in (".jpg", ".jpeg", ".png", ".webp"):
        return "image"
    if ext == ".pdf":
        return "pdf"
    if ext in (".docx", ".doc"):
        return "docx"
    # Fallback content-type
    return ACCEPTED_TYPES.get(content_type, "")


def extraire_texte(chemin: str, format_fichier: str) -> tuple[str, int, str]:
    """Dispatch vers l'extracteur approprié."""
    if format_fichier == "pdf":
        return extraire_depuis_pdf(chemin)
    elif format_fichier == "image":
        return extraire_depuis_image(chemin)
    elif format_fichier == "docx":
        return extraire_depuis_docx(chemin)
    else:
        raise HTTPException(status_code=400, detail=f"Format non supporté: {format_fichier}")


# ══════════════════════════════════════════════════════════════════════════
# ANALYSE IA
# ══════════════════════════════════════════════════════════════════════════

SYSTEM_PROMPT = """Tu es un expert en analyse de CVs multilingues (français, anglais, arabe).
Ton rôle est d'extraire et structurer les informations d'un CV en JSON standardisé.

RÈGLES :
1. Le texte peut être désordonné (OCR, multi-colonnes) — raisonne sur le sens, pas sur la mise en page
2. Distingue bien : formations académiques VS certifications/formations courtes
3. Compétences TECHNIQUES (langages, frameworks, logiciels) ≠ SOFT SKILLS ≠ OUTILS
4. Ne jamais inventer une information absente → mettre null
5. Dates : standardise en format lisible ("Janvier 2020 - Mars 2023", "2019 - 2021", etc.)
6. Si un résumé/objectif est absent mais déductible, synthétise en 2-3 phrases
7. Réponds UNIQUEMENT avec un JSON valide, sans markdown ni explication

Structure attendue :
{
  "profil": {
    "nom": null, "prenom": null, "email": null, "telephone": null,
    "adresse": null, "nationalite": null, "date_naissance": null,
    "titre_professionnel": null, "resume": null
  },
  "experiences": [
    {"poste": "", "entreprise": "", "periode": null, "date_debut": null,
     "date_fin": null, "description": null, "lieu": null}
  ],
  "formations": [
    {"diplome": "", "etablissement": "", "periode": null, "date_debut": null,
     "date_fin": null, "specialite": null, "lieu": null}
  ],
  "competences": {
    "techniques": [], "soft_skills": [], "outils": [], "autres": []
  },
  "langues": [{"langue": "", "niveau": null}],
  "certifications": [],
  "projets": [],
  "interets": []
}"""


def analyser_cv_avec_ia(texte_brut: str) -> CvStandardise:
    """Envoie le texte à Claude pour structuration JSON."""
    try:
        response = client.messages.create(
            model="claude-sonnet-4-20250514",
            max_tokens=4000,
            system=SYSTEM_PROMPT,
            messages=[{
                "role": "user",
                "content": f"Voici le texte extrait d'un CV (peut être désordonné) :\n\n{texte_brut}\n\nStructure ce CV en JSON."
            }]
        )

        texte_reponse = response.content[0].text.strip()

        # Nettoyer les balises markdown si présentes
        if "```" in texte_reponse:
            texte_reponse = texte_reponse.split("```")[1]
            if texte_reponse.startswith("json"):
                texte_reponse = texte_reponse[4:]

        data = json.loads(texte_reponse.strip())
        return CvStandardise(**data)

    except json.JSONDecodeError as e:
        logger.error(f"JSON parse erreur: {e}")
        raise HTTPException(status_code=500, detail="Erreur structuration données CV")
    except Exception as e:
        logger.error(f"Claude API erreur: {e}")
        raise HTTPException(status_code=500, detail=f"Erreur analyse IA: {str(e)}")


# ══════════════════════════════════════════════════════════════════════════
# ENDPOINTS
# ══════════════════════════════════════════════════════════════════════════

@app.get("/health")
async def health_check():
    return {
        "status": "ok",
        "service": "cv-extraction",
        "version": "1.1.0",
        "formats_supportes": ["pdf", "pdf_scanne_ocr", "jpg", "png", "webp", "docx", "doc"]
    }


@app.post("/extract", response_model=CvResponse)
async def extraire_cv(file: UploadFile = File(...)):
    """
    Endpoint principal d'extraction CV.

    Formats acceptés : PDF, JPG, PNG, WEBP, DOCX, DOC
    - PDF natif     → extraction texte directe (pdfplumber)
    - PDF scanné    → OCR automatique (PyMuPDF + Tesseract)
    - Image         → OCR (Tesseract fra+eng)
    - DOCX/DOC      → conversion pandoc

    Retourne les données structurées : profil, expériences, formations,
    compétences, langues, certifications, projets, intérêts.
    """
    filename = file.filename or ""
    content_type = file.content_type or ""

    # Détection format
    format_fichier = detecter_format(filename, content_type)
    if not format_fichier or format_fichier == "auto":
        ext = Path(filename).suffix.lower()
        if ext not in ACCEPTED_EXTENSIONS:
            raise HTTPException(
                status_code=400,
                detail=f"Format non supporté. Acceptés : PDF, JPG, PNG, WEBP, DOCX, DOC"
            )
        format_fichier = detecter_format(filename, "")

    # Lecture contenu
    contenu = await file.read()
    if len(contenu) > 10 * 1024 * 1024:  # 10MB max
        raise HTTPException(status_code=400, detail="Fichier trop volumineux (max 10MB)")

    logger.info(f"Traitement: {filename} | format={format_fichier} | {len(contenu)} bytes")

    # Sauvegarde temporaire avec bonne extension
    ext = Path(filename).suffix or (".pdf" if format_fichier == "pdf" else ".tmp")
    with tempfile.NamedTemporaryFile(delete=False, suffix=ext) as tmp:
        tmp.write(contenu)
        chemin_tmp = tmp.name

    try:
        # 1. Extraction texte
        texte_brut, nb_pages, methode = extraire_texte(chemin_tmp, format_fichier)

        if len(texte_brut.strip()) < 50:
            return CvResponse(
                success=False,
                nb_pages=nb_pages,
                format_detecte=format_fichier,
                methode_extraction=methode,
                message="Impossible d'extraire le texte (fichier corrompu ou protégé)"
            )

        # 2. Analyse IA
        cv_standardise = analyser_cv_avec_ia(texte_brut)

        return CvResponse(
            success=True,
            cv_standardise=cv_standardise,
            texte_brut=texte_brut,
            nb_pages=nb_pages,
            format_detecte=format_fichier,
            methode_extraction=methode,
            message="CV extrait et structuré avec succès"
        )

    finally:
        if os.path.exists(chemin_tmp):
            os.unlink(chemin_tmp)


@app.post("/extract/text-only", response_model=CvResponse)
async def extraire_texte_seulement(file: UploadFile = File(...)):
    """Extraction texte brut uniquement, sans analyse IA."""
    filename = file.filename or ""
    format_fichier = detecter_format(filename, file.content_type or "")

    contenu = await file.read()
    ext = Path(filename).suffix or ".tmp"
    with tempfile.NamedTemporaryFile(delete=False, suffix=ext) as tmp:
        tmp.write(contenu)
        chemin_tmp = tmp.name

    try:
        texte_brut, nb_pages, methode = extraire_texte(chemin_tmp, format_fichier)
        return CvResponse(
            success=True,
            texte_brut=texte_brut,
            nb_pages=nb_pages,
            format_detecte=format_fichier,
            methode_extraction=methode,
            message="Texte extrait avec succès"
        )
    finally:
        if os.path.exists(chemin_tmp):
            os.unlink(chemin_tmp)
