"""
Routes FastAPI — Module de gestion des modèles de documents.
Préfixe : /api/templates
"""

from pathlib import Path
from typing import Optional

from fastapi import APIRouter, File, Form, HTTPException, Query, UploadFile
from fastapi.responses import FileResponse

from app.config import settings
from app.models.document import (
    CreateTemplateResponse,
    TemplateDetailResponse,
    TemplateListResponse,
)
from .service import (
    creer_modele_depuis_upload,
    creer_modele_depuis_url,
    lister_modeles,
    obtenir_modele,
    supprimer_modele,
)

router = APIRouter(prefix="/templates", tags=["📋 Modèles de documents"])


# ── Création ───────────────────────────────────────────────────────────────

@router.post(
    "",
    response_model=CreateTemplateResponse,
    summary="Créer un modèle depuis un fichier Word uploadé",
    status_code=201,
)
async def creer_modele_upload(
    fichier: UploadFile = File(..., description="Fichier Word (.docx) servant de modèle"),
    id_type_document: int = Form(..., description="Identifiant du type de document"),
):
    """
    Crée un nouveau modèle de document à partir d'un fichier Word uploadé.

    **Règles de préparation du fichier modèle :**
    - Les **champs à compléter** sont indiqués par `[nom du champ]`
      *(exemple : `[nom_etudiant]`, `[date_debut]`, `[entreprise]`)*
    - L'**emplacement du QR code** est indiqué par un **carré rouge** dans le document
      *(rectangle rempli ou bordé en rouge — toute nuance de rouge est acceptée)*

    **Données retournées :**
    - `id_modele_document` : identifiant auto-attribué
    - `url_fichier_modele` : URL pour accéder au fichier stocké
    - `champs_detectes`    : liste des [champs] trouvés automatiquement
    - `a_zone_qrcode`      : indique si une zone QR a été détectée
    """
    record = await creer_modele_depuis_upload(fichier, id_type_document)
    nb = len(record.champs_detectes)
    return CreateTemplateResponse(
        success=True,
        id_modele_document=record.id_modele_document,
        url_fichier_modele=record.url_fichier_modele,
        id_type_document=record.id_type_document,
        champs_detectes=record.champs_detectes,
        a_zone_qrcode=record.a_zone_qrcode,
        message=(
            f"Modèle créé avec succès. "
            f"{nb} champ(s) détecté(s)"
            f"{', zone QR code présente' if record.a_zone_qrcode else ''}."
        ),
    )


@router.post(
    "/from-url",
    response_model=CreateTemplateResponse,
    summary="Créer un modèle depuis une URL ou un chemin",
    status_code=201,
)
async def creer_modele_url(
    url_fichier_modele: str = Form(..., description="URL HTTP ou chemin local du fichier .docx"),
    id_type_document: int   = Form(..., description="Identifiant du type de document"),
):
    """
    Variante de création de modèle : le fichier Word est fourni via une URL
    (téléchargement automatique) ou un chemin local sur le serveur.
    """
    record = await creer_modele_depuis_url(url_fichier_modele, id_type_document)
    nb = len(record.champs_detectes)
    return CreateTemplateResponse(
        success=True,
        id_modele_document=record.id_modele_document,
        url_fichier_modele=record.url_fichier_modele,
        id_type_document=record.id_type_document,
        champs_detectes=record.champs_detectes,
        a_zone_qrcode=record.a_zone_qrcode,
        message=(
            f"Modèle créé avec succès. "
            f"{nb} champ(s) détecté(s)"
            f"{', zone QR code présente' if record.a_zone_qrcode else ''}."
        ),
    )


# ── Consultation ───────────────────────────────────────────────────────────

@router.get(
    "",
    response_model=TemplateListResponse,
    summary="Lister les modèles de documents",
)
async def lister_templates(
    id_type_document: Optional[int] = Query(None, description="Filtrer par type de document"),
):
    """
    Retourne tous les modèles enregistrés.
    Filtrez par `id_type_document` pour n'obtenir qu'une catégorie.
    """
    modeles = lister_modeles(id_type_document)
    return TemplateListResponse(total=len(modeles), templates=modeles)


@router.get(
    "/{id_modele}",
    response_model=TemplateDetailResponse,
    summary="Obtenir un modèle par son identifiant",
)
async def detail_modele(id_modele: int):
    """
    Retourne les détails complets d'un modèle :
    champs détectés, présence zone QR, date de création, etc.
    """
    record = obtenir_modele(id_modele)
    if not record:
        raise HTTPException(
            status_code=404,
            detail=f"Modèle {id_modele} introuvable.",
        )
    return TemplateDetailResponse(success=True, template=record)


@router.get(
    "/files/{filename}",
    summary="Télécharger le fichier .docx d'un modèle",
)
async def telecharger_fichier_modele(filename: str):
    """Télécharge le fichier Word source d'un modèle."""
    if ".." in filename or "/" in filename or "\\" in filename:
        raise HTTPException(status_code=400, detail="Nom de fichier invalide.")

    chemin = settings.TEMPLATES_STORAGE_DIR / filename
    if not chemin.exists():
        raise HTTPException(status_code=404, detail=f"Fichier '{filename}' introuvable.")

    return FileResponse(
        path=str(chemin),
        media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        filename=filename,
    )


# ── Suppression ────────────────────────────────────────────────────────────

@router.delete(
    "/{id_modele}",
    summary="Supprimer un modèle",
    status_code=200,
)
async def supprimer_template(id_modele: int):
    """Supprime un modèle et son fichier .docx associé."""
    if not supprimer_modele(id_modele):
        raise HTTPException(status_code=404, detail=f"Modèle {id_modele} introuvable.")
    return {"success": True, "message": f"Modèle {id_modele} supprimé."}
