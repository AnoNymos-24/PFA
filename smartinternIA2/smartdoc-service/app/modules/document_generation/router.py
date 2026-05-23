"""
Routes FastAPI — Module de génération de documents.
Préfixe : /api/documents
"""

from pathlib import Path
from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse

from app.config import settings
from app.models.document import GenerateDocumentRequest, GenerateDocumentResponse
from .service import generer_document
from .generators import pdf_disponible

router = APIRouter(prefix="/documents", tags=["📝 Génération de documents"])


@router.post(
    "/generate",
    response_model=GenerateDocumentResponse,
    summary="Générer un document depuis un modèle",
)
async def generate_document(request: GenerateDocumentRequest):
    """
    Génère un document (DOCX ou PDF) à partir d'un modèle enregistré.

    **Corps de la requête :**
    ```json
    {
      "id_modele_document": 1,
      "output_format": "pdf",
      "qr_data": "https://smartintern.ai/stage/123",
      "filename": "convention_dupont_jean",
      "data": {
        "nom_etudiant": "Dupont",
        "prenom_etudiant": "Jean",
        "date_debut": "01/06/2025",
        "entreprise": "Acme Corp"
      }
    }
    ```

    **Règles :**
    - Les clés de `data` doivent correspondre exactement aux marqueurs `[champ]` du template.
    - Si un champ est absent de `data`, le marqueur `[champ]` reste tel quel dans le document.
    - `qr_data` est ignoré si le template ne contient pas de zone rouge.
    - `output_format: "pdf"` nécessite LibreOffice installé sur le serveur.
    """
    return await generer_document(request)


@router.get(
    "/download/{filename}",
    summary="Télécharger un document généré",
)
async def download_document(filename: str):
    """
    Télécharge un document précédemment généré par son nom de fichier.
    Le nom est retourné dans la réponse de `/generate`.
    """
    if ".." in filename or "/" in filename or "\\" in filename:
        raise HTTPException(status_code=400, detail="Nom de fichier invalide.")

    chemin = settings.OUTPUT_DIR / filename
    if not chemin.exists():
        raise HTTPException(
            status_code=404,
            detail=f"Document '{filename}' introuvable ou expiré.",
        )

    media_type = (
        "application/pdf"
        if filename.endswith(".pdf")
        else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )
    return FileResponse(path=str(chemin), media_type=media_type, filename=filename)


@router.get(
    "/capabilities",
    summary="Capacités de génération disponibles",
)
async def get_capabilities():
    """Indique les formats disponibles selon l'environnement serveur."""
    return {
        "formats_disponibles": {
            "docx": True,
            "pdf":  pdf_disponible(),
        },
        "note_pdf": (
            "PDF disponible (LibreOffice détecté)."
            if pdf_disponible()
            else "PDF indisponible — LibreOffice non installé. Utilisez DOCX."
        ),
    }
