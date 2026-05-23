"""
Extracteur PDF — texte natif (pdfplumber) avec fallback OCR (PyMuPDF + Tesseract).
"""

import logging
import os
import tempfile

import fitz
import pdfplumber
import pytesseract
from PIL import Image

logger = logging.getLogger("smartintern.cv.extractors.pdf")


def extraire_depuis_pdf(chemin: str) -> tuple[str, int, str]:
    """
    Extrait le texte d'un PDF.

    Stratégie :
      1. pdfplumber  → texte natif (rapide, précis)
      2. PyMuPDF + Tesseract OCR → si PDF scanné / texte insuffisant

    Returns:
        (texte, nb_pages, méthode)
    """
    # ── Tentative texte natif ──────────────────────────────────────────────
    texte_pages, nb_pages = [], 0
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
        logger.warning(f"pdfplumber erreur : {e}")

    # ── Fallback OCR ───────────────────────────────────────────────────────
    logger.info("PDF scanné détecté → OCR")
    doc = fitz.open(chemin)
    nb_pages = len(doc)
    texte_pages_ocr = []

    for page in doc:
        mat = fitz.Matrix(200 / 72, 200 / 72)   # 200 DPI
        pix = page.get_pixmap(matrix=mat, colorspace=fitz.csRGB)

        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp_img:
            tmp_path = tmp_img.name
        pix.save(tmp_path)

        try:
            with Image.open(tmp_path) as img:
                texte_pages_ocr.append(pytesseract.image_to_string(img, lang="fra+eng"))
        finally:
            try:
                os.unlink(tmp_path)
            except OSError:
                pass

    texte = "\n\n--- PAGE ---\n\n".join(texte_pages_ocr).strip()
    return texte, nb_pages, "pdf_ocr"
