"""
Modèles Pydantic — Modèles de documents et génération.

Flux :
  1. Créer un modèle  →  POST /modeles/   (upload .docx + id_type_document)
  2. Générer un doc   →  POST /documents/generer  (id_modele_document + data + qr_data)
"""

from typing import Any, Optional
from enum import Enum
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


# ══════════════════════════════════════════════════════════════════════════
# MODÈLES DE DOCUMENTS (registry.json)
# ══════════════════════════════════════════════════════════════════════════

class DocumentTemplateRecord(BaseModel):
    """
    Enregistrement persisté d'un modèle de document.

    Champs fournis par l'utilisateur :
      - id_type_document   : identifiant entier du type de document
      - url_fichier_modele : URL d'accès au fichier .docx stocké (auto-calculée)

    Champs calculés automatiquement :
      - id_modele_document : identifiant auto-incrémenté
      - fichier_local      : chemin absolu du .docx sur le serveur
      - champs_detectes    : liste des [champs] trouvés dans le document
      - a_zone_qrcode      : True si un carré rouge a été détecté
      - qrcode_taille_cm   : taille en cm de la zone rouge détectée
      - date_creation      : horodatage ISO 8601
    """
    id_modele_document: int
    url_fichier_modele: str
    id_type_document:   int
    fichier_local:      str
    champs_detectes:    list[str]
    a_zone_qrcode:      bool
    qrcode_taille_cm:   float = 3.0
    date_creation:      str


# ── Réponses API modèles ───────────────────────────────────────────────────

class CreateTemplateResponse(BaseModel):
    success:             bool
    id_modele_document:  Optional[int]  = None
    url_fichier_modele:  Optional[str]  = None
    id_type_document:    Optional[int]  = None
    champs_detectes:     list[str]      = []
    a_zone_qrcode:       bool           = False
    message:             str            = ""


class TemplateListResponse(BaseModel):
    total:     int
    templates: list[DocumentTemplateRecord]


class TemplateDetailResponse(BaseModel):
    success:  bool
    template: Optional[DocumentTemplateRecord] = None
    message:  str = ""


# ══════════════════════════════════════════════════════════════════════════
# GÉNÉRATION DE DOCUMENTS
# ══════════════════════════════════════════════════════════════════════════

class OutputFormat(str, Enum):
    DOCX = "docx"
    PDF  = "pdf"


class GenerateDocumentRequest(BaseModel):
    """
    Corps de la requête de génération d'un document.

    - id_modele_document : identifiant du modèle à utiliser
    - data               : dictionnaire {champ: valeur} pour remplacer les [champs]
    - qr_data            : texte/URL à encoder dans le QR code (optionnel)
    - output_format      : "docx" ou "pdf" (PDF nécessite LibreOffice)
    - filename           : nom personnalisé pour le fichier généré (optionnel)
    - doc_id             : UUID pré-généré par le backend (optionnel).
                           Si fourni, le fichier généré porte ce nom ;
                           sinon un UUID est généré localement.
    """
    id_modele_document: int
    data:               dict[str, Any]
    qr_data:            Optional[str]  = None
    output_format:      OutputFormat   = OutputFormat.DOCX
    filename:           Optional[str]  = None
    doc_id:             Optional[str]  = None


class GeneratedDocumentInfo(BaseModel):
    document_id:  str
    filename:     str
    format:       str
    size_bytes:   int
    download_url: str


class GenerateDocumentResponse(BaseModel):
    success:             bool
    document:            Optional[GeneratedDocumentInfo] = None
    champs_non_remplis:  list[str]                       = []
    message:             str                             = ""


# ── Création de modèle depuis URL ────────────────────────────────────────

class CreateTemplateFromURLRequest(BaseModel):
    """Corps de la requête pour créer un modèle depuis une URL ou un chemin local."""
    url_fichier_modele: str
    id_type_document:   int


# ── Vérification QR (héritage génération précédente) ─────────────────────

class ResultatVerification(CamelModel):
    """Résultat de la vérification d'un document via QR code."""
    doc_uuid:          str
    statut:            str
    valide:            bool
    message:           str
    type_document:     str  = ""
    nom_etablissement: str  = ""
    date_generation:   str  = ""
    date_expiration:   str  = ""
    signature_valide:  bool = False
    expire:            bool = False
    fichier_existe:    bool = False
    verifie_le:        str  = ""
