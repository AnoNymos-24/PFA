"""
Extracteur PDF — texte natif (pdfplumber) puis OCR (PyMuPDF + Tesseract).
"""

import os
import tempfile
import logging

import pdfplumber
import fitz          # PyMuPDF
import pytesseract
from PIL import Image

logger = logging.getLogger("smartdoc.cv.pdf")


def extraire_depuis_pdf(chemin: str) -> tuple[str, int, str]:
    """
    Extrait le texte d'un fichier PDF.

    Stratégie 1 — texte natif via pdfplumber (layout-aware, rapide).
    Stratégie 2 — OCR page par page via PyMuPDF + Tesseract
                  si le texte natif est insuffisant (PDF scanné).

    Returns:
        (texte, nb_pages, methode)
    """
    nb_pages = 0
    texte_pages: list[str] = []

    # ── Stratégie 1 : texte natif ─────────────────────────────────────────
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

        logger.info(f"Texte natif trop court ({len(texte)} chars) → passage OCR")

    except Exception as exc:
        logger.warning(f"pdfplumber erreur: {exc}")

    # ── Stratégie 2 : OCR (PDF scanné) ────────────────────────────────────
    logger.info("OCR par page (PyMuPDF + Tesseract)")
    doc = fitz.open(chemin)
    nb_pages = len(doc)
    texte_pages_ocr: list[str] = []

    for page in doc:
        # Rasteriser à 200 DPI pour une bonne qualité OCR
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
