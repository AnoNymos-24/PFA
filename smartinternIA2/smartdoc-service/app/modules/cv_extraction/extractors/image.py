"""
Extracteur image — OCR via Tesseract (fra + eng).
"""

import logging
import pytesseract
from PIL import Image

logger = logging.getLogger("smartdoc.cv.image")


def extraire_depuis_image(chemin: str) -> tuple[str, int, str]:
    """
    Extrait le texte d'une image (JPG, PNG, WEBP) via Tesseract.
    Upscale automatique si la résolution est trop faible (< 1000 px de large).

    Returns:
        (texte, 1, "image_ocr")
    """
    img = Image.open(chemin)
    w, h = img.size

    if w < 1000:
        scale = 1000 / w
        img = img.resize((int(w * scale), int(h * scale)), Image.LANCZOS)
        logger.info(f"Image upscalée : {w}×{h} → {img.size}")

    if img.mode not in ("RGB", "L"):
        img = img.convert("RGB")

    texte = pytesseract.image_to_string(img, lang="fra+eng")
    logger.info(f"Image OCR OK ({len(texte)} chars)")
    return texte.strip(), 1, "image_ocr"
