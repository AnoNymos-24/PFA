"""
Utilitaire de génération de QR codes.

Génère un QR code contenant les informations de vérification d'un document
(UUID, dates, signature HMAC partielle, URL de vérification).
"""

import json
import logging

import qrcode
import qrcode.constants

from config import settings

logger = logging.getLogger("smartintern.utils.qr")


def generer_qr_code(
    doc_uuid: str,
    type_document: str,
    nom_etablissement: str,
    date_generation: str,
    date_expiration: str,
    signature: str,
    chemin_sortie: str,
) -> str:
    """
    Génère un QR code PNG contenant les métadonnées de vérification du document.

    Contenu encodé :
      - URL de vérification (endpoint /documents/verifier/{uuid})
      - UUID unique du document
      - Type de document
      - Nom de l'établissement
      - Date de génération / expiration
      - Signature HMAC partielle (16 premiers caractères)

    Returns:
        Chemin du fichier PNG généré.
    """
    url_verification = f"{settings.BASE_URL}/documents/verifier/{doc_uuid}"

    donnees_qr = {
        "url":            url_verification,
        "uuid":           doc_uuid,
        "type":           type_document,
        "etablissement":  nom_etablissement,
        "date_generation": date_generation,
        "date_expiration": date_expiration,
        "signature":      signature[:16] + "...",   # partielle pour alléger le QR
    }

    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_H,
        box_size=8,
        border=2,
    )
    qr.add_data(json.dumps(donnees_qr, ensure_ascii=False))
    qr.make(fit=True)

    img = qr.make_image(fill_color="#1a1a2e", back_color="white").convert("RGB")
    img.save(chemin_sortie, "PNG", dpi=(300, 300))

    logger.info(f"QR code généré : {chemin_sortie}")
    return chemin_sortie
