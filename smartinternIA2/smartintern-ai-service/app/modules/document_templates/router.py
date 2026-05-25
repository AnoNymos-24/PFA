"""
Routes FastAPI — Module de gestion des modèles de documents Word.
Préfixe : /modeles

Endpoints :
  POST   /modeles/            — Créer un modèle depuis un fichier .docx uploadé
  POST   /modeles/from-url    — Créer un modèle depuis une URL ou chemin local
  GET    /modeles/            — Lister tous les modèles (filtre optionnel : id_type_document)
  GET    /modeles/{id}        — Détail d'un modèle
  GET    /modeles/files/{fn}  — Télécharger le fichier .docx d'un modèle
  DELETE /modeles/{id}        — Supprimer un modèle
"""

from typing import Optional

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse

from app.models.document import (
    CreateTemplateFromURLRequest,
    CreateTemplateResponse,
    TemplateDetailResponse,
    TemplateListResponse,
)
from app.modules.document_templates.service import (
    creer_modele_depuis_upload,
    creer_modele_depuis_url,
    lister_modeles,
    obtenir_modele,
    supprimer_modele,
)
from config import settings

router = APIRouter(prefix="/modeles", tags=["📋 Modèles de documents"])


# ══════════════════════════════════════════════════════════════════════════
# CRÉATION DE MODÈLE
# ══════════════════════════════════════════════════════════════════════════

@router.post(
    "/",
    response_model=CreateTemplateResponse,
    status_code=201,
    summary="Créer un modèle depuis un fichier .docx uploadé",
)
async def creer_depuis_upload(
    fichier: UploadFile = File(..., description="Fichier Word (.docx) servant de modèle"),
    id_type_document: int = Form(..., description="Identifiant du type de document"),
    duree_validite_jours: int = Form(365, description="Durée de validité des documents générés (en jours, défaut 365)"),
):
    """
    Crée un modèle de document depuis un fichier Word uploadé.

    **Pipeline :**
    1. Validation de l'extension (.docx obligatoire)
    2. Attribution d'un `id_modele_document` auto-incrémenté
    3. Sauvegarde → `templates_storage/template_{id}.docx`
    4. Extraction des marqueurs `[champ à remplir]` (corps + tableaux + en-têtes/pieds)
    5. Détection de la zone rouge QR (DrawingML + VML)
    6. Persistance dans `registry.json`

    **Réponse :**
    - `id_modele_document`    : identifiant unique auto-généré
    - `url_fichier_modele`    : URL d'accès au fichier stocké
    - `champs_detectes`       : liste des champs `[...]` trouvés dans le document
    - `a_zone_qrcode`         : True si un carré rouge a été détecté
    - `duree_validite_jours`  : durée de validité transmise au Spring Boot
    """
    try:
        record = await creer_modele_depuis_upload(fichier, id_type_document, duree_validite_jours)
        return CreateTemplateResponse(
            success=True,
            id_modele_document=record.id_modele_document,
            url_fichier_modele=record.url_fichier_modele,
            id_type_document=record.id_type_document,
            champs_detectes=record.champs_detectes,
            a_zone_qrcode=record.a_zone_qrcode,
            duree_validite_jours=record.duree_validite_jours,
            message=(
                f"Modèle #{record.id_modele_document} créé — "
                f"{len(record.champs_detectes)} champ(s) détecté(s), "
                f"validité : {record.duree_validite_jours}j, "
                f"zone QR : {'oui' if record.a_zone_qrcode else 'non'}"
            ),
        )
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Erreur interne : {exc}")


@router.post(
    "/from-url",
    response_model=CreateTemplateResponse,
    status_code=201,
    summary="Créer un modèle depuis une URL ou un chemin local",
)
async def creer_depuis_url(corps: CreateTemplateFromURLRequest):
    """
    Crée un modèle depuis :
    - une URL HTTP/HTTPS (`https://...`) → le fichier est téléchargé
    - un chemin local absolu             → le fichier est copié

    Même pipeline d'analyse que l'upload direct.
    """
    try:
        record = await creer_modele_depuis_url(
            corps.url_fichier_modele,
            corps.id_type_document,
            corps.duree_validite_jours,
        )
        return CreateTemplateResponse(
            success=True,
            id_modele_document=record.id_modele_document,
            url_fichier_modele=record.url_fichier_modele,
            id_type_document=record.id_type_document,
            champs_detectes=record.champs_detectes,
            a_zone_qrcode=record.a_zone_qrcode,
            duree_validite_jours=record.duree_validite_jours,
            message=(
                f"Modèle #{record.id_modele_document} créé depuis URL — "
                f"{len(record.champs_detectes)} champ(s) détecté(s), "
                f"validité : {record.duree_validite_jours}j"
            ),
        )
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Erreur interne : {exc}")


# ══════════════════════════════════════════════════════════════════════════
# CONSULTATION
# ══════════════════════════════════════════════════════════════════════════

@router.get(
    "/",
    response_model=TemplateListResponse,
    summary="Lister les modèles de documents",
)
def lister(id_type_document: Optional[int] = None):
    """
    Retourne la liste de tous les modèles enregistrés.

    **Filtre optionnel :** `?id_type_document=2` pour restreindre aux modèles
    d'un type de document donné.
    """
    modeles = lister_modeles(id_type_document)
    return TemplateListResponse(total=len(modeles), templates=modeles)


@router.get(
    "/files/{filename}",
    summary="Télécharger le fichier .docx d'un modèle",
    response_class=FileResponse,
)
def telecharger_fichier(filename: str):
    """
    Télécharge le fichier Word brut d'un modèle.
    Le nom de fichier est de la forme `template_{id}.docx`.
    """
    chemin = settings.TEMPLATES_STORAGE_DIR / filename
    if not chemin.exists() or not filename.endswith(".docx"):
        raise HTTPException(
            status_code=404,
            detail=f"Fichier modèle '{filename}' introuvable",
        )
    return FileResponse(
        str(chemin),
        media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        filename=filename,
    )


@router.get(
    "/{id_modele}",
    response_model=TemplateDetailResponse,
    summary="Détail d'un modèle",
)
def detail_modele(id_modele: int):
    """Retourne les métadonnées complètes d'un modèle (champs, QR, dates…)."""
    record = obtenir_modele(id_modele)
    if record is None:
        return TemplateDetailResponse(
            success=False,
            template=None,
            message=f"Modèle #{id_modele} introuvable",
        )
    return TemplateDetailResponse(success=True, template=record, message="")


# ══════════════════════════════════════════════════════════════════════════
# SUPPRESSION
# ══════════════════════════════════════════════════════════════════════════

@router.delete(
    "/{id_modele}",
    summary="Supprimer un modèle",
    status_code=200,
)
def supprimer(id_modele: int):
    """
    Supprime un modèle et son fichier `.docx` associé.
    Retourne 404 si le modèle n'existe pas.
    """
    supprime = supprimer_modele(id_modele)
    if not supprime:
        raise HTTPException(
            status_code=404,
            detail=f"Modèle #{id_modele} introuvable — rien à supprimer",
        )
    return {
        "success": True,
        "message": f"Modèle #{id_modele} supprimé avec succès",
    }
