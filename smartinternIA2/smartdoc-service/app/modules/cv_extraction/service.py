"""
Service d'extraction CV — orchestre détection format, extraction texte et analyse IA.
"""

import logging
from pathlib import Path
from fastapi import HTTPException, UploadFile

from app.models.cv import CvResponse
from app.utils.files import (
    detecter_format,
    save_temp_file,
    cleanup_temp_file,
    ACCEPTED_EXTENSIONS,
)
from app.config import settings
from .extractors import extraire_depuis_pdf, extraire_depuis_image, extraire_depuis_docx
from .ai_parser import analyser_cv_avec_ia

logger = logging.getLogger("smartdoc.cv.service")

MAX_FILE_SIZE = settings.MAX_FILE_SIZE_MB * 1024 * 1024


def _dispatcher(chemin: str, format_fichier: str) -> tuple[str, int, str]:
    """Redirige vers l'extracteur adapté au format détecté."""
    if format_fichier == "pdf":
        return extraire_depuis_pdf(chemin)
    if format_fichier == "image":
        return extraire_depuis_image(chemin)
    if format_fichier == "docx":
        return extraire_depuis_docx(chemin)
    raise HTTPException(
        status_code=400,
        detail=f"Format non supporté : {format_fichier}",
    )


async def traiter_cv(file: UploadFile, include_ai: bool = True) -> CvResponse:
    """
    Pipeline complet de traitement d'un CV :
      1. Validation (format + taille)
      2. Extraction du texte brut (selon le format)
      3. Analyse IA et structuration JSON (optionnel)

    Args:
        file:       Fichier uploadé (PDF, image, DOCX…)
        include_ai: Si True, lance l'analyse Claude après extraction.
    """
    filename = file.filename or ""
    content_type = file.content_type or ""

    # ── Détection du format ───────────────────────────────────────────────
    format_fichier = detecter_format(filename, content_type)
    if not format_fichier or format_fichier == "auto":
        ext = Path(filename).suffix.lower()
        if ext not in ACCEPTED_EXTENSIONS:
            raise HTTPException(
                status_code=400,
                detail="Format non supporté. Acceptés : PDF, JPG, PNG, WEBP, DOCX, DOC.",
            )
        format_fichier = detecter_format(filename, "")

    # ── Validation taille ─────────────────────────────────────────────────
    contenu = await file.read()
    if len(contenu) > MAX_FILE_SIZE:
        raise HTTPException(
            status_code=400,
            detail=f"Fichier trop volumineux (max {settings.MAX_FILE_SIZE_MB} MB).",
        )

    logger.info(f"Traitement : {filename} | format={format_fichier} | {len(contenu)} octets")

    # ── Extraction ────────────────────────────────────────────────────────
    ext = Path(filename).suffix or ".tmp"
    chemin_tmp = save_temp_file(contenu, ext)

    try:
        texte_brut, nb_pages, methode = _dispatcher(chemin_tmp, format_fichier)

        if len(texte_brut.strip()) < 50:
            return CvResponse(
                success=False,
                nb_pages=nb_pages,
                format_detecte=format_fichier,
                methode_extraction=methode,
                message="Impossible d'extraire le texte (fichier corrompu ou protégé).",
            )

        # ── Analyse IA ────────────────────────────────────────────────────
        cv_standardise = analyser_cv_avec_ia(texte_brut) if include_ai else None

        return CvResponse(
            success=True,
            cv_standardise=cv_standardise,
            texte_brut=texte_brut,
            nb_pages=nb_pages,
            format_detecte=format_fichier,
            methode_extraction=methode,
            message=(
                "CV extrait et structuré avec succès."
                if include_ai else
                "Texte extrait avec succès."
            ),
        )

    finally:
        cleanup_temp_file(chemin_tmp)
