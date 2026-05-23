"""
Modèles Pydantic — Gestion des modèles et génération de documents.

Miroirs des entités Java : TypeDocument, ModeleDocument, DocumentGenere.
"""

from typing import Optional
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


# ── Modèles de documents ───────────────────────────────────────────────────

class ModeleDocumentInfo(CamelModel):
    """Informations sur un modèle de document créé. Miroir de l'entité Java ModeleDocument."""
    modele_id:            str
    nom_modele:           str
    type_document:        str
    titre_document:       str
    langue:               str
    chemin_modele:        str
    chemin_header:        Optional[str] = None
    chemin_footer:        Optional[str] = None
    chemin_script:        str
    champs_dynamiques:    list          = Field(default_factory=list)
    sections:             list          = Field(default_factory=list)
    mise_en_page:         dict          = Field(default_factory=dict)
    duree_validite_jours: int           = 365
    version:              int           = 1
    date_creation:        str           = ""


# ── Requêtes de génération ─────────────────────────────────────────────────

class DemandeGenerationDocument(CamelModel):
    """
    Requête de génération d'un document.
    Envoyé par le backend Java avec toutes les données profil.
    """
    modele_id:            str
    chemin_script:        str
    chemin_header:        Optional[str] = None
    chemin_footer:        Optional[str] = None
    type_document:        str
    donnees_profil:       dict          = Field(default_factory=dict)
    nom_etablissement:    str           = "SmartIntern"
    duree_validite_jours: int           = 365


class DemandeRegenerationDocument(DemandeGenerationDocument):
    """Requête de régénération — étend DemandeGenerationDocument avec l'UUID original."""
    doc_uuid_original: str


# ── Réponses de génération ─────────────────────────────────────────────────

class DocumentGenereInfo(CamelModel):
    """
    Miroir de l'entité Java DocumentGenere.
    Retourne les métadonnées au backend Java pour persistance MySQL.
    """
    doc_uuid:                 str
    modele_id:                str
    type_document:            str
    chemin_fichier:           str
    url_fichier:              str
    url_verification:         str
    date_generation:          str
    date_expiration:          str
    signature:                str
    statut:                   str           = "VALIDE"
    taille_octets:            int           = 0
    nom_etablissement:        str           = ""
    donnees_profil_utilises:  list          = Field(default_factory=list)
    doc_uuid_original:        Optional[str] = None
    regeneration:             bool          = False


# ── Vérification QR ────────────────────────────────────────────────────────

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
