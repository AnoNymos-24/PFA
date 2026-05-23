"""
Extracteur DOCX/DOC — pandoc (prioritaire) puis python-docx (fallback).
"""

import subprocess
import logging
from fastapi import HTTPException

logger = logging.getLogger("smartdoc.cv.docx")


def extraire_depuis_docx(chemin: str) -> tuple[str, int, str]:
    """
    Extrait le texte d'un fichier DOCX ou DOC.

    Stratégie 1 — pandoc → texte brut (meilleure conservation du layout).
    Stratégie 2 — python-docx (fallback si pandoc absent).

    Returns:
        (texte, 1, methode)
    """
    # ── Stratégie 1 : pandoc ──────────────────────────────────────────────
    try:
        result = subprocess.run(
            ["pandoc", chemin, "-t", "plain", "--wrap=none"],
            capture_output=True,
            text=True,
            timeout=30,
        )
        if result.returncode == 0 and result.stdout.strip():
            logger.info(f"DOCX pandoc OK ({len(result.stdout)} chars)")
            return result.stdout.strip(), 1, "docx_pandoc"
    except FileNotFoundError:
        logger.warning("pandoc non trouvé — passage au fallback python-docx")
    except Exception as exc:
        logger.warning(f"pandoc erreur: {exc}")

    # ── Stratégie 2 : python-docx ─────────────────────────────────────────
    try:
        from docx import Document
        doc = Document(chemin)
        texte = "\n".join(p.text for p in doc.paragraphs if p.text.strip())
        logger.info(f"DOCX python-docx OK ({len(texte)} chars)")
        return texte, 1, "docx_python"
    except Exception as exc:
        logger.error(f"python-docx erreur: {exc}")
        raise HTTPException(
            status_code=500,
            detail=f"Impossible de lire le fichier DOCX : {exc}",
        )
