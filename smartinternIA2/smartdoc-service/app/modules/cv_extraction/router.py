"""
Routes FastAPI — Module d'extraction de CV.
Préfixe : /api/cv
"""

from fastapi import APIRouter, File, UploadFile

from app.models.cv import CvResponse
from .service import traiter_cv

router = APIRouter(prefix="/cv", tags=["📄 Extraction CV"])


@router.post(
    "/extract",
    response_model=CvResponse,
    summary="Extraction complète (texte + analyse IA)",
)
async def extract_cv(file: UploadFile = File(...)):
    """
    Extrait et structure un CV avec l'intelligence artificielle (Claude).

    **Formats acceptés** : `PDF`, `JPG`, `PNG`, `WEBP`, `DOCX`, `DOC`

    | Format        | Méthode d'extraction            |
    |---------------|---------------------------------|
    | PDF natif     | pdfplumber (texte natif)        |
    | PDF scanné    | PyMuPDF + Tesseract OCR         |
    | Image         | Tesseract OCR (fra + eng)       |
    | DOCX / DOC    | pandoc → fallback python-docx   |

    Retourne les données structurées : profil, expériences, formations,
    compétences, langues, certifications, projets, intérêts.
    """
    return await traiter_cv(file, include_ai=True)


@router.post(
    "/extract/text-only",
    response_model=CvResponse,
    summary="Extraction texte brut uniquement (sans IA)",
)
async def extract_cv_text_only(file: UploadFile = File(...)):
    """
    Extrait uniquement le texte brut du CV, sans passer par l'analyse IA.
    Utile pour prévisualiser le contenu ou déboguer.
    """
    return await traiter_cv(file, include_ai=False)


@router.get("/formats", summary="Formats de fichiers acceptés")
async def get_accepted_formats():
    """Liste les formats de fichiers acceptés par le service d'extraction."""
    return {
        "formats": ["pdf", "pdf_scanne_ocr", "jpg", "jpeg", "png", "webp", "docx", "doc"],
        "taille_max_mb": 10,
    }
