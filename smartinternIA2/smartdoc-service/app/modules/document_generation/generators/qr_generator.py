"""
Générateur de QR codes — produit une image PNG à partir d'une chaîne de données.
"""

import logging
import os
import tempfile
from io import BytesIO

import qrcode
import qrcode.constants

logger = logging.getLogger("smartdoc.docgen.qr")


def generer_qrcode_buffer(data: str) -> BytesIO:
    """
    Génère un QR code et retourne l'image PNG dans un buffer BytesIO.

    Args:
        data: Texte ou URL à encoder dans le QR code.

    Returns:
        BytesIO positionné au début de l'image PNG.
    """
    qr = qrcode.QRCode(
        version=None,                               # Taille automatique
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=10,
        border=2,
    )
    qr.add_data(data)
    qr.make(fit=True)

    img = qr.make_image(fill_color="black", back_color="white")
    buf = BytesIO()
    img.save(buf, format="PNG")
    buf.seek(0)
    return buf


def generer_qrcode_fichier_temp(data: str) -> str:
    """
    Génère un QR code et le sauvegarde dans un fichier temporaire PNG.

    Returns:
        Chemin du fichier temporaire (à supprimer après usage avec cleanup_temp_file).
    """
    buf = generer_qrcode_buffer(data)
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
        tmp.write(buf.read())
        chemin = tmp.name
    logger.debug(f"QR code généré : {chemin}")
    return chemin
