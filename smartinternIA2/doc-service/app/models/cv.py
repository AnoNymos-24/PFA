"""
Modèles Pydantic — CV et extraction.

Miroirs exacts des entités Java :
  Profil, Experience, Formation, Competences, Langue, CvStandardise, CvResponse.

alias_generator=to_camel : snake_case Python → camelCase JSON
Compatible avec Jackson (Spring Boot) sans configuration supplémentaire.
"""

from typing import Annotated, Any, Optional
from pydantic import BaseModel, ConfigDict, Field, BeforeValidator
from pydantic.alias_generators import to_camel


# ── Base camelCase ─────────────────────────────────────────────────────────

class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


# ── Sections du CV ─────────────────────────────────────────────────────────

class Profil(CamelModel):
    nom: Optional[str]                  = None
    prenom: Optional[str]               = None
    email: Optional[str]                = None
    telephone: Optional[str]            = None
    adresse: Optional[str]              = None
    nationalite: Optional[str]          = None
    date_naissance: Optional[str]       = None
    titre_professionnel: Optional[str]  = None
    resume: Optional[str]               = None


class Experience(CamelModel):
    poste: str
    entreprise: str
    periode: Optional[str]     = None
    date_debut: Optional[str]  = None
    date_fin: Optional[str]    = None
    description: Optional[str] = None
    lieu: Optional[str]        = None


class Formation(CamelModel):
    diplome: str
    etablissement: str
    periode: Optional[str]    = None
    date_debut: Optional[str] = None
    date_fin: Optional[str]   = None
    specialite: Optional[str] = None
    lieu: Optional[str]       = None


class Competences(CamelModel):
    techniques:  list[str] = Field(default_factory=list)
    soft_skills: list[str] = Field(default_factory=list)   # → softSkills en JSON
    outils:      list[str] = Field(default_factory=list)
    autres:      list[str] = Field(default_factory=list)


class Langue(CamelModel):
    langue: str
    niveau: Optional[str] = None


# ── Normalisation des listes mixtes (str | dict) ───────────────────────────

def _dict_vers_str(item: object) -> str:
    """
    Convertit un dict renvoyé par un LLM en chaîne lisible.
    Ex: {"nom": "Photographie", "organisme": "Sophaera", "date": "2014"}
        → "Photographie — Sophaera (2014)"
    """
    if isinstance(item, str):
        return item
    if not isinstance(item, dict):
        return str(item)

    nom       = item.get("nom")       or item.get("name")         or item.get("titre")        or ""
    organisme = (item.get("organisme") or item.get("organization") or item.get("organisation")
                 or item.get("etablissement") or item.get("entreprise") or "")
    date      = item.get("date")      or item.get("annee")         or item.get("year")         or ""
    desc      = item.get("description") or item.get("technologie") or item.get("technologies") or ""

    parts = [str(nom)]
    if organisme:
        parts.append(str(organisme))
    detail = []
    if date:
        detail.append(str(date))
    if desc and isinstance(desc, str) and len(desc) < 60:
        detail.append(desc)
    if detail:
        parts.append(f"({', '.join(detail)})")
    return " — ".join(filter(None, parts)) or str(item)


def _normaliser_liste_str(v: Any) -> list[str]:
    """BeforeValidator : convertit chaque élément dict → str avant validation Pydantic."""
    if not isinstance(v, list):
        return []
    return [_dict_vers_str(item) for item in v if item is not None]


StrList = Annotated[list[str], BeforeValidator(_normaliser_liste_str)]


# ── CV structuré complet ───────────────────────────────────────────────────

class CvStandardise(CamelModel):
    """Miroir exact du DTO Java CvExtractionService.CvStandardise + scoreCompletude."""
    profil:          Profil
    experiences:     list[Experience] = Field(default_factory=list)
    formations:      list[Formation]  = Field(default_factory=list)
    competences:     Competences      = Field(default_factory=Competences)
    langues:         list[Langue]     = Field(default_factory=list)
    certifications:  StrList          = Field(default_factory=list)
    projets:         StrList          = Field(default_factory=list)
    interets:        StrList          = Field(default_factory=list)
    score_completude: float           = Field(default=0.0)   # → scoreCompletude


# ── Scoring ────────────────────────────────────────────────────────────────

class ScoreSection(CamelModel):
    score:       float
    max:         float
    pourcentage: float
    manquant:    list[str] = Field(default_factory=list)
    complet:     bool


class ScoreResultat(CamelModel):
    score_global:       float
    score_sur_100:      float
    niveau:             str
    details:            dict
    recommandations:    list[str]
    sections_completes: int
    sections_totales:   int


# ── Réponse API principale ─────────────────────────────────────────────────

class CvResponse(CamelModel):
    """
    Miroir exact du DTO Java CvExtractionService.CvResponse.
    Sérialisation snake_case → camelCase automatique.
    """
    success:             bool
    cv_standardise:      Optional[CvStandardise] = None
    texte_brut:          Optional[str]            = None
    nb_pages:            int                      = 0
    format_detecte:      str                      = ""
    methode_extraction:  str                      = ""
    message:             str                      = ""
    score:               Optional[ScoreResultat]  = None
