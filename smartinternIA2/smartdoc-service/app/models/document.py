"""
Modèles Pydantic — modèles de documents et génération.

Flux :
  1. Créer un modèle  →  POST /api/templates/  (upload .docx + id_type_document)
  2. Générer un doc   →  POST /api/documents/generate  (id_modele + data + qr_data)
"""

from typing import Optional, Any
from enum import Enum
from pydantic import BaseModel


# ── Enums ──────────────────────────────────────────────────────────────────

class OutputFormat(str, Enum):
    DOCX = "docx"
    PDF  = "pdf"


# ── Modèle de document (ce qui est enregistré) ─────────────────────────────

class DocumentTemplateRecord(BaseModel):
    """
    Représentation persistée d'un modèle de document.

    Champs fournis par l'utilisateur :
      - url_fichier_modele : URL/chemin du fichier .docx source
      - id_type_document   : identifiant entier du type de document

    Champs calculés automatiquement :
      - id_modele_document : identifiant auto-incrémenté
      - fichier_local      : chemin local de la copie stockée
      - champs_detectes    : liste des [champs] trouvés dans le document
      - a_zone_qrcode      : présence d'une zone carrée rouge (emplacement QR)
      - date_creation      : horodatage ISO 8601
    """
    id_modele_document: int
    url_fichier_modele: str
    id_type_document: int
    fichier_local: str
    champs_detectes: list[str]
    a_zone_qrcode: bool
    qrcode_taille_cm: float = 3.0      # taille de la zone rouge détectée
    date_creation: str


# ── Réponses API templates ─────────────────────────────────────────────────

class CreateTemplateResponse(BaseModel):
    success: bool
    id_modele_document: Optional[int] = None
    url_fichier_modele: Optional[str] = None
    id_type_document: Optional[int] = None
    champs_detectes: list[str] = []
    a_zone_qrcode: bool = False
    message: str = ""


class TemplateListResponse(BaseModel):
    total: int
    templates: list[DocumentTemplateRecord]


class TemplateDetailResponse(BaseModel):
    success: bool
    template: Optional[DocumentTemplateRecord] = None
    message: str = ""


# ── Génération de document ─────────────────────────────────────────────────

class GenerateDocumentRequest(BaseModel):
    """
    Corps de la requête de génération de document.

    - id_modele_document : identifiant du modèle à utiliser
    - data               : dictionnaire {champ: valeur} pour remplacer les [champs]
    - qr_data            : texte/URL à encoder dans le QR code (optionnel)
    - output_format      : "docx" ou "pdf"
    - filename           : nom personnalisé pour le fichier généré (optionnel)
    """
    id_modele_document: int
    data: dict[str, Any]
    qr_data: Optional[str] = None
    output_format: OutputFormat = OutputFormat.PDF
    filename: Optional[str] = None


class GeneratedDocumentInfo(BaseModel):
    document_id: str
    filename: str
    format: str
    size_bytes: int
    download_url: str


class GenerateDocumentResponse(BaseModel):
    success: bool
    document: Optional[GeneratedDocumentInfo] = None
    champs_non_remplis: list[str] = []
    message: str = ""
