"""
Routes FastAPI — Module de gestion des modèles de documents.
Préfixe : /modeles
"""

from typing import Optional

from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.modules.document_templates.service import creer_modele, obtenir_champs_modele

router = APIRouter(prefix="/modeles", tags=["📋 Modèles de documents"])


@router.post(
    "/creer",
    summary="Créer un nouveau modèle de document",
    status_code=201,
)
async def creer_modele_document(
    modele_id:             str           = Form(...,          description="Identifiant unique du modèle (UUID)"),
    nom_modele:            str           = Form(...,          description="Nom lisible du modèle"),
    type_document:         str           = Form(...,          description="Type de document (convention_stage, etc.)"),
    duree_validite_jours:  int           = Form(default=365,  description="Durée de validité des documents générés"),
    fichier_modele:        UploadFile    = File(...,          description="Fichier PDF ou DOCX servant de modèle"),
    fichier_header:        Optional[UploadFile] = File(default=None, description="Image d'en-tête (PNG/JPG) — optionnel"),
    fichier_footer:        Optional[UploadFile] = File(default=None, description="Image de pied de page (PNG/JPG) — optionnel"),
):
    """
    Crée un nouveau modèle de document avec analyse IA automatique.

    **Processus :**
    1. Sauvegarde header PNG/JPG (optionnel)
    2. Sauvegarde footer PNG/JPG (optionnel)
    3. Sauvegarde modèle PDF ou DOCX
    4. Analyse IA → extraction des champs dynamiques + structure
    5. Génération automatique d'un script Python réutilisable (ReportLab)
    6. Retourne les métadonnées complètes pour persistance Java/MySQL

    **Formats acceptés :**
    - `fichier_modele` : PDF, DOCX, DOC
    - `fichier_header` / `fichier_footer` : PNG, JPG, JPEG
    """
    from app.core.ai_client import ai_client
    if not ai_client:
        raise HTTPException(
            status_code=503,
            detail="Aucune clé API IA configurée. "
                   "Renseignez NVIDIA_API_KEY et/ou OPENROUTER_API_KEY dans .env",
        )

    # ── Validation modèle ──────────────────────────────────────────────────
    nom_fichier_modele = fichier_modele.filename or "modele.pdf"
    from pathlib import Path
    ext = Path(nom_fichier_modele).suffix.lower()
    if ext not in (".pdf", ".docx", ".doc"):
        raise HTTPException(
            status_code=400,
            detail="Modèle : seuls PDF, DOCX, DOC sont acceptés",
        )

    contenu_modele = await fichier_modele.read()
    if len(contenu_modele) > 20 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="Modèle trop volumineux (max 20 MB)")

    # ── Validation header ──────────────────────────────────────────────────
    contenu_header, nom_header = None, None
    if fichier_header and fichier_header.filename:
        ext_h = Path(fichier_header.filename).suffix.lower()
        if ext_h not in (".png", ".jpg", ".jpeg"):
            raise HTTPException(status_code=400, detail="Header : seuls PNG, JPG sont acceptés")
        contenu_header = await fichier_header.read()
        nom_header     = fichier_header.filename

    # ── Validation footer ──────────────────────────────────────────────────
    contenu_footer, nom_footer = None, None
    if fichier_footer and fichier_footer.filename:
        ext_f = Path(fichier_footer.filename).suffix.lower()
        if ext_f not in (".png", ".jpg", ".jpeg"):
            raise HTTPException(status_code=400, detail="Footer : seuls PNG, JPG sont acceptés")
        contenu_footer = await fichier_footer.read()
        nom_footer     = fichier_footer.filename

    try:
        resultat = creer_modele(
            modele_id=modele_id,
            nom_modele=nom_modele,
            type_document=type_document,
            contenu_modele=contenu_modele,
            nom_fichier_modele=nom_fichier_modele,
            contenu_header=contenu_header,
            nom_fichier_header=nom_header,
            contenu_footer=contenu_footer,
            nom_fichier_footer=nom_footer,
            duree_validite_jours=duree_validite_jours,
        )
        return {
            "success": True,
            "message": f"Modèle '{nom_modele}' créé avec succès",
            "modele": resultat,
            "nb_champs_detectes": len(resultat.get("champs_dynamiques", [])),
            "type_detecte": resultat.get("type_document"),
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Erreur création modèle : {str(e)}")


@router.get(
    "/{modele_id}/champs",
    summary="Obtenir les champs dynamiques d'un modèle",
)
async def champs_modele(modele_id: str):
    """
    Retourne les informations d'un modèle (existence du script, chemin).
    Interrogé par le backend Java pour afficher les champs dans le frontend.
    """
    infos = obtenir_champs_modele(modele_id)
    if not infos:
        raise HTTPException(
            status_code=404,
            detail=f"Modèle {modele_id} introuvable",
        )
    return infos


@router.get(
    "/types",
    summary="Lister les types de documents supportés",
)
async def types_documents():
    """Référentiel des types de documents supportés par le service."""
    return {
        "types": [
            {"code": "convention_stage",       "label": "Convention de stage",         "categorie": "stage"},
            {"code": "attestation_stage",       "label": "Attestation de stage",        "categorie": "stage"},
            {"code": "lettre_recommandation",   "label": "Lettre de recommandation",    "categorie": "academique"},
            {"code": "attestation_inscription", "label": "Attestation d'inscription",   "categorie": "academique"},
            {"code": "releve_notes",            "label": "Relevé de notes",             "categorie": "academique"},
            {"code": "demande_stage",           "label": "Demande de stage",            "categorie": "stage"},
            {"code": "rapport_stage",           "label": "Rapport de stage",            "categorie": "stage"},
            {"code": "fiche_evaluation",        "label": "Fiche d'évaluation",          "categorie": "evaluation"},
            {"code": "certificat_formation",    "label": "Certificat de formation",     "categorie": "formation"},
            {"code": "autre",                   "label": "Autre document",              "categorie": "autre"},
        ]
    }
