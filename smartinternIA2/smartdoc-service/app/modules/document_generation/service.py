"""
Service de génération de documents — orchestre template, remplissage et conversion.
"""

import uuid
import logging
from pathlib import Path

from fastapi import HTTPException

from app.config import settings
from app.models.document import (
    GenerateDocumentRequest,
    GenerateDocumentResponse,
    GeneratedDocumentInfo,
    OutputFormat,
)
from app.modules.document_templates.service import obtenir_modele
from app.utils.files import ensure_dir
from .generators import remplir_template_docx, convertir_docx_en_pdf, pdf_disponible

logger = logging.getLogger("smartdoc.docgen.service")


async def generer_document(request: GenerateDocumentRequest) -> GenerateDocumentResponse:
    """
    Pipeline complet de génération d'un document :

      1. Chargement du modèle (vérification existence + fichier .docx)
      2. Génération DOCX (remplacement [champs] + insertion QR si demandé)
      3. Conversion PDF via LibreOffice (si output_format = pdf)
      4. Construction de la réponse avec URL de téléchargement

    Returns:
        GenerateDocumentResponse avec métadonnées du document créé.
    """
    # ── 1. Chargement du modèle ───────────────────────────────────────────
    modele = obtenir_modele(request.id_modele_document)
    if not modele:
        raise HTTPException(
            status_code=404,
            detail=f"Modèle {request.id_modele_document} introuvable.",
        )

    chemin_template = Path(modele.fichier_local)
    if not chemin_template.exists():
        raise HTTPException(
            status_code=404,
            detail=(
                f"Fichier template manquant sur le serveur : {chemin_template.name}. "
                "Le modèle doit être recréé."
            ),
        )

    # ── 2. Génération DOCX ────────────────────────────────────────────────
    doc_id    = str(uuid.uuid4())[:8]
    output_dir = ensure_dir(settings.OUTPUT_DIR)

    base_nom = (
        Path(request.filename).stem
        if request.filename
        else f"modele_{request.id_modele_document}"
    )
    nom_docx     = f"{doc_id}_{base_nom}.docx"
    chemin_docx  = output_dir / nom_docx

    # Données QR code
    qr_data = request.qr_data if (modele.a_zone_qrcode and request.qr_data) else None

    try:
        champs_non_remplis = remplir_template_docx(
            chemin_template=str(chemin_template),
            data=request.data,
            chemin_sortie=chemin_docx,
            qr_data=qr_data,
            qr_taille_cm=modele.qrcode_taille_cm,
        )
    except Exception as exc:
        logger.error(f"Erreur remplissage template : {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Erreur de génération : {exc}")

    # ── 3. Conversion PDF (si demandé) ────────────────────────────────────
    if request.output_format == OutputFormat.PDF:
        if not pdf_disponible():
            raise HTTPException(
                status_code=501,
                detail=(
                    "La génération PDF nécessite LibreOffice installé sur le serveur. "
                    "Installez LibreOffice ou utilisez le format DOCX."
                ),
            )
        try:
            chemin_final = convertir_docx_en_pdf(chemin_docx, output_dir)
            chemin_docx.unlink(missing_ok=True)       # Supprimer le DOCX intermédiaire
            fmt_final  = "pdf"
            nom_final  = chemin_final.name
        except RuntimeError as exc:
            raise HTTPException(status_code=500, detail=str(exc))
    else:
        chemin_final = chemin_docx
        fmt_final    = "docx"
        nom_final    = nom_docx

    # ── 4. Réponse ────────────────────────────────────────────────────────
    taille = chemin_final.stat().st_size

    msg = f"Document généré avec succès (modèle {request.id_modele_document})."
    if champs_non_remplis:
        msg += f" Attention : {len(champs_non_remplis)} champ(s) non rempli(s) : {champs_non_remplis}."

    return GenerateDocumentResponse(
        success=True,
        document=GeneratedDocumentInfo(
            document_id=doc_id,
            filename=nom_final,
            format=fmt_final,
            size_bytes=taille,
            download_url=f"/api/documents/download/{nom_final}",
        ),
        champs_non_remplis=champs_non_remplis,
        message=msg,
    )
