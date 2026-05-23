"""
Extracteur DOCX — pandoc (primaire) → python-docx (fallback).
"""

import logging
import subprocess

from fastapi import HTTPException

logger = logging.getLogger("smartintern.cv.extractors.docx")


def extraire_depuis_docx(chemin: str) -> tuple[str, int, str]:
    """
    Extrait le texte d'un fichier Word (.docx / .doc).

    Stratégie :
      1. pandoc → conversion plain text (optimal, conserve la structure)
      2. python-docx → lecture directe des paragraphes

    Returns:
        (texte, nb_pages=1, méthode)

    Raises:
        HTTPException 500 si les deux méthodes échouent.
    """
    # ── pandoc ────────────────────────────────────────────────────────────
    try:
        result = subprocess.run(
            ["pandoc", chemin, "-t", "plain", "--wrap=none"],
            capture_output=True, text=True, timeout=30,
        )
        if result.returncode == 0 and result.stdout.strip():
            logger.info("DOCX extrait via pandoc")
            return result.stdout.strip(), 1, "docx_pandoc"
    except Exception as e:
        logger.warning(f"pandoc erreur : {e}")

    # ── python-docx ───────────────────────────────────────────────────────
    try:
        from docx import Document as DocxDoc
        doc = DocxDoc(chemin)
        texte = "\n".join(p.text for p in doc.paragraphs if p.text.strip())
        if texte.strip():
            logger.info("DOCX extrait via python-docx")
            return texte, 1, "docx_python"
    except Exception as e:
        logger.error(f"python-docx erreur : {e}")
        raise HTTPException(status_code=500, detail=f"Impossible de lire le DOCX : {e}")

    raise HTTPException(
        status_code=500,
        detail="Impossible d'extraire le texte du fichier Word.",
    )
