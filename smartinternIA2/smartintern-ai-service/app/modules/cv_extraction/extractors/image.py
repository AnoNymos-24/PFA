"""
Extracteur image — OCR via Tesseract (fra + eng).
"""

import logging

import pytesseract
from PIL import Image

logger = logging.getLogger("smartintern.cv.extractors.image")


def extraire_depuis_image(chemin: str) -> tuple[str, int, str]:
    """
    Extrait le texte d'une image (JPG, PNG, WEBP) via Tesseract OCR.

    L'image est redimensionnée à 1000px de large minimum pour améliorer la précision OCR.

    Returns:
        (texte, nb_pages=1, méthode)
    """
    img = Image.open(chemin)
    w, h = img.size

    # Redimensionner si trop petite
    if w < 1000:
        nouveau_w = 1000
        nouveau_h = int(h * 1000 / w)
        img = img.resize((nouveau_w, nouveau_h), Image.LANCZOS)

    # Convertir en RGB si nécessaire
    if img.mode not in ("RGB", "L"):
        img = img.convert("RGB")

    texte = pytesseract.image_to_string(img, lang="fra+eng")
    logger.info(f"OCR image OK ({len(texte)} chars)")

    return texte.strip(), 1, "image_ocr"
