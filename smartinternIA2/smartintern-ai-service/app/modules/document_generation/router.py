"""
Routes FastAPI — Module de génération de documents.
Préfixe : /documents

Endpoints :
  POST  /documents/generer               — Générer un document depuis un modèle
  GET   /documents/telecharger/{fn}      — Télécharger un document généré
  GET   /documents/verifier/{doc_uuid}   — Vérifier l'authenticité (depuis QR)
  POST  /documents/verifier              — Vérifier par POST (depuis backend Java)
"""

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse, JSONResponse

from app.models.document import GenerateDocumentRequest, GenerateDocumentResponse, GeneratedDocumentInfo
from app.modules.document_generation.service import generer_document, verifier_document
from config import settings

router = APIRouter(prefix="/documents", tags=["📝 Génération de documents"])


# ══════════════════════════════════════════════════════════════════════════
# GÉNÉRATION
# ══════════════════════════════════════════════════════════════════════════

@router.post(
    "/generer",
    response_model=GenerateDocumentResponse,
    summary="Générer un document personnalisé depuis un modèle Word",
)
async def generer(demande: GenerateDocumentRequest):
    """
    Génère un document à partir d'un modèle Word enregistré.

    **Corps JSON attendu :**
    ```json
    {
      "id_modele_document": 1,
      "data": {
        "nom_etudiant":    "Jean Dupont",
        "nom_entreprise":  "TechCorp SAS",
        "date_debut":      "2025-06-01",
        "date_fin":        "2025-08-31",
        "nom_tuteur":      "Marie Martin"
      },
      "qr_data":       "https://smartintern.ai/verifier/abc123",
      "output_format": "docx",
      "filename":      "convention_jean_dupont"
    }
    ```

    **Champs `data` :** correspondre exactement aux marqueurs `[champ]` présents
    dans le document modèle (voir `champs_detectes` dans le détail du modèle).

    **`qr_data` :** texte ou URL à encoder dans le QR code. Si le modèle contient
    une zone rouge et que ce champ est fourni, le QR code est automatiquement inséré.

    **`output_format` :** `"docx"` (défaut) ou `"pdf"` (nécessite LibreOffice installé).
    """
    try:
        metadata, chemin = generer_document(
            id_modele_document=demande.id_modele_document,
            data=demande.data,
            qr_data=demande.qr_data,
            output_format=demande.output_format.value,
            filename=demande.filename,
        )
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc))
    except FileNotFoundError as exc:
        raise HTTPException(status_code=422, detail=str(exc))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Erreur interne : {exc}")

    doc_info = GeneratedDocumentInfo(
        document_id=metadata["document_id"],
        filename=metadata["filename"],
        format=metadata["format"],
        size_bytes=metadata["size_bytes"],
        download_url=metadata["download_url"],
    )

    return GenerateDocumentResponse(
        success=True,
        document=doc_info,
        champs_non_remplis=metadata.get("champs_non_remplis", []),
        message=(
            f"Document généré avec succès ({metadata['format'].upper()}, "
            f"{metadata['size_bytes'] // 1024} KB)"
            + (
                f" — {len(metadata['champs_non_remplis'])} champ(s) non rempli(s)"
                if metadata.get("champs_non_remplis")
                else ""
            )
        ),
    )


# ══════════════════════════════════════════════════════════════════════════
# TÉLÉCHARGEMENT
# ══════════════════════════════════════════════════════════════════════════

@router.get(
    "/telecharger/{filename}",
    summary="Télécharger un document généré",
    response_class=FileResponse,
)
async def telecharger(filename: str):
    """
    Télécharge un document généré (DOCX ou PDF).

    Le nom de fichier est de la forme `{uuid}.docx` ou `{uuid}.pdf`,
    tel que retourné dans le champ `download_url` de la réponse de génération.
    """
    chemin = settings.DOCUMENTS_DIR / filename
    if not chemin.exists():
        raise HTTPException(
            status_code=404,
            detail=f"Document '{filename}' introuvable",
        )

    if filename.endswith(".pdf"):
        media_type = "application/pdf"
    elif filename.endswith(".docx"):
        media_type = (
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
    else:
        media_type = "application/octet-stream"

    return FileResponse(
        str(chemin),
        media_type=media_type,
        filename=filename,
    )


# ══════════════════════════════════════════════════════════════════════════
# VÉRIFICATION D'AUTHENTICITÉ (scan QR)
# ══════════════════════════════════════════════════════════════════════════

@router.get(
    "/verifier/{doc_uuid}",
    summary="Vérifier l'authenticité d'un document (depuis QR code)",
)
async def verifier_get(
    doc_uuid:          str,
    date_generation:   str = "",
    date_expiration:   str = "",
    signature:         str = "",
    type_document:     str = "",
    nom_etablissement: str = "",
):
    """
    Endpoint de vérification scanné depuis le QR code.
    **Accessible publiquement** (pas d'authentification requise).

    Retourne le statut :
    - `VALIDE`      — document authentique et non expiré
    - `EXPIRE`      — document valide mais périmé
    - `FALSIFIE`    — signature HMAC invalide
    - `INTROUVABLE` — fichier absent du serveur
    """
    resultat = verifier_document(
        doc_uuid=doc_uuid,
        date_generation=date_generation,
        date_expiration=date_expiration,
        signature=signature,
        type_document=type_document,
        nom_etablissement=nom_etablissement,
    )

    statut = resultat.get("statut", "")
    if statut == "EXPIRE":
        code = 410
    elif statut in ("FALSIFIE", "INTROUVABLE"):
        code = 404
    else:
        code = 200

    return JSONResponse(content=resultat, status_code=code)


@router.post(
    "/verifier",
    summary="Vérifier un document par POST (depuis le backend Java)",
)
async def verifier_post(donnees: dict):
    """
    Vérification par POST — même logique que GET, accepte un JSON complet.
    Utilisé par le backend Java après scan du QR code.

    **Corps JSON attendu (camelCase) :**
    ```json
    {
      "docUuid":          "...",
      "dateGeneration":   "...",
      "dateExpiration":   "...",
      "signature":        "...",
      "typeDocument":     "...",
      "nomEtablissement": "..."
    }
    ```
    """
    return verifier_document(
        doc_uuid=donnees.get("docUuid", ""),
        date_generation=donnees.get("dateGeneration", ""),
        date_expiration=donnees.get("dateExpiration", ""),
        signature=donnees.get("signature", ""),
        type_document=donnees.get("typeDocument", ""),
        nom_etablissement=donnees.get("nomEtablissement", ""),
    )
