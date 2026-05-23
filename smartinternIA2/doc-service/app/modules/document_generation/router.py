"""
Routes FastAPI — Module de génération de documents.
Préfixe : /documents
"""

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse, JSONResponse

from app.models.document import DemandeGenerationDocument, DemandeRegenerationDocument
from app.modules.document_generation.service import (
    generer_document,
    regenerer_document,
    verifier_document,
)
from config import settings

router = APIRouter(prefix="/documents", tags=["📝 Génération de documents"])


@router.post(
    "/generer",
    summary="Générer un document PDF personnalisé",
)
async def generer(demande: DemandeGenerationDocument):
    """
    Génère un document PDF personnalisé pour un utilisateur.

    **Appelé par le backend Java** avec les données profil de l'utilisateur.
    Le backend Java est responsable de persister le `DocumentGenere` en MySQL.

    **Corps JSON attendu :**
    ```json
    {
      "modeleId": "uuid-du-modele",
      "cheminScript": "uploads/scripts/uuid_script.py",
      "cheminHeader": "uploads/headers/uuid_header.png",
      "typeDocument": "convention_stage",
      "donneesProfil": {
        "nom": "Dupont", "prenom": "Jean",
        "filiere": "Informatique",
        "nom_entreprise": "TechCorp",
        "date_debut_stage": "2025-06-01",
        "date_fin_stage": "2025-08-31"
      },
      "nomEtablissement": "ITEAM University",
      "dureeValiditeJours": 365
    }
    ```
    """
    try:
        resultat = generer_document(
            modele_id=demande.modele_id,
            chemin_script=demande.chemin_script,
            chemin_header=demande.chemin_header,
            chemin_footer=demande.chemin_footer,
            type_document=demande.type_document,
            donnees_profil=demande.donnees_profil,
            nom_etablissement=demande.nom_etablissement,
            duree_validite_jours=demande.duree_validite_jours,
        )
        return {"success": True, "message": "Document généré avec succès", "document": resultat}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Erreur génération document : {str(e)}")


@router.post(
    "/regenerer",
    summary="Régénérer un document existant (nouvelle version)",
)
async def regenerer(demande: DemandeRegenerationDocument):
    """
    Régénère un document existant.
    Conserve la référence au document original (`doc_uuid_original`).
    """
    try:
        resultat = regenerer_document(
            doc_uuid_original=demande.doc_uuid_original,
            modele_id=demande.modele_id,
            chemin_script=demande.chemin_script,
            chemin_header=demande.chemin_header,
            chemin_footer=demande.chemin_footer,
            type_document=demande.type_document,
            donnees_profil=demande.donnees_profil,
            nom_etablissement=demande.nom_etablissement,
            duree_validite_jours=demande.duree_validite_jours,
        )
        return {"success": True, "message": "Document régénéré", "document": resultat}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Erreur régénération : {str(e)}")


@router.get(
    "/telecharger/{doc_uuid}",
    summary="Télécharger le PDF d'un document généré",
)
async def telecharger(doc_uuid: str):
    """
    Télécharge le PDF d'un document généré.
    Endpoint appelé par le frontend ou le backend Java.
    """
    chemin = settings.DOCUMENTS_DIR / f"{doc_uuid}.pdf"
    if not chemin.exists():
        raise HTTPException(status_code=404, detail=f"Document {doc_uuid} introuvable")

    return FileResponse(
        str(chemin),
        media_type="application/pdf",
        filename=f"document_{doc_uuid[:8]}.pdf",
        headers={"Content-Disposition": f'attachment; filename="document_{doc_uuid[:8]}.pdf"'},
    )


@router.get(
    "/verifier/{doc_uuid}",
    summary="Vérifier l'authenticité d'un document (depuis QR code)",
)
async def verifier_get(
    doc_uuid: str,
    date_generation: str  = "",
    date_expiration: str  = "",
    signature: str        = "",
    type_document: str    = "",
    nom_etablissement: str = "",
):
    """
    Endpoint de vérification scanné depuis le QR code.
    **Accessible publiquement** (pas d'authentification requise).

    Retourne :
    - `VALIDE` : document authentique et non expiré
    - `EXPIRE` : document valide mais périmé
    - `FALSIFIE` : signature HMAC invalide
    - `INTROUVABLE` : fichier absent du serveur
    """
    resultat = verifier_document(
        doc_uuid=doc_uuid,
        date_generation=date_generation,
        date_expiration=date_expiration,
        signature=signature,
        type_document=type_document,
        nom_etablissement=nom_etablissement,
    )
    code = 200
    if resultat["statut"] == "EXPIRE":      code = 410
    elif resultat["statut"] in ("FALSIFIE", "INTROUVABLE"): code = 404
    return JSONResponse(content=resultat, status_code=code)


@router.post(
    "/verifier",
    summary="Vérifier un document par POST (depuis le backend Java)",
)
async def verifier_post(donnees: dict):
    """
    Vérification par POST — même logique que GET, accepte un JSON complet.
    Utilisé par le backend Java après scan QR.
    """
    resultat = verifier_document(
        doc_uuid=donnees.get("docUuid", ""),
        date_generation=donnees.get("dateGeneration", ""),
        date_expiration=donnees.get("dateExpiration", ""),
        signature=donnees.get("signature", ""),
        type_document=donnees.get("typeDocument", ""),
        nom_etablissement=donnees.get("nomEtablissement", ""),
    )
    return resultat
