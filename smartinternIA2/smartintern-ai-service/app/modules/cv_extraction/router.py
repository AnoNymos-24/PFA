"""
Routes FastAPI — Module d'extraction de CV.
Préfixe : /cv
"""

from fastapi import APIRouter, File, UploadFile

from app.modules.cv_extraction.service import traiter_cv, calculer_score
from app.models.cv import CvStandardise

router = APIRouter(prefix="/cv", tags=["📄 Extraction CV"])


@router.post(
    "/extract",
    summary="Extraction complète (texte + analyse IA + score)",
)
async def extraire_cv(file: UploadFile = File(...)):
    """
    Extrait et structure un CV avec l'intelligence artificielle.

    **Formats acceptés** : `PDF`, `JPG`, `PNG`, `WEBP`, `DOCX`, `DOC`

    | Format        | Méthode d'extraction             |
    |---------------|----------------------------------|
    | PDF natif     | pdfplumber (texte natif)         |
    | PDF scanné    | PyMuPDF + Tesseract OCR          |
    | Image         | Tesseract OCR (fra + eng)        |
    | DOCX / DOC    | pandoc → fallback python-docx    |

    Retourne les données structurées + score de complétude.
    Réponse en **camelCase** compatible Spring Boot / Jackson.
    """
    return await traiter_cv(file, include_ai=True)


@router.post(
    "/extract/text-only",
    summary="Extraction texte brut uniquement (sans IA)",
)
async def extraire_texte_seulement(file: UploadFile = File(...)):
    """
    Extrait uniquement le texte brut du CV sans analyse IA.
    Utile pour prévisualiser le contenu ou déboguer l'extraction.
    """
    return await traiter_cv(file, include_ai=False)


@router.post(
    "/score",
    summary="Calculer le score de complétude d'un CV déjà structuré",
)
async def scorer_cv(cv: CvStandardise):
    """
    Calcule le score de complétude d'un CV déjà structuré (100 pts / 6 sections).
    Utilisé quand l'étudiant modifie son profil manuellement depuis l'interface.
    """
    from app.modules.cv_extraction.service import calculer_score
    score = calculer_score(cv)
    return score.model_dump(by_alias=True)


@router.get(
    "/formats",
    summary="Formats de fichiers acceptés",
)
async def formats_acceptes():
    """Liste les formats de fichiers acceptés par le service d'extraction."""
    return {
        "formats": ["pdf", "pdf_ocr", "jpg", "jpeg", "png", "webp", "docx", "doc"],
        "taille_max_mb": 10,
        "methodes_extraction": {
            "pdf_natif": "pdfplumber",
            "pdf_scanne": "PyMuPDF + Tesseract OCR",
            "image": "Tesseract OCR (fra+eng)",
            "docx": "pandoc → python-docx",
        },
    }
