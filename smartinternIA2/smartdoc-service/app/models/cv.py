"""
Modèles Pydantic pour la représentation standardisée d'un CV.
"""

from typing import Optional
from pydantic import BaseModel, Field


class Profil(BaseModel):
    nom: Optional[str] = None
    prenom: Optional[str] = None
    email: Optional[str] = None
    telephone: Optional[str] = None
    adresse: Optional[str] = None
    nationalite: Optional[str] = None
    date_naissance: Optional[str] = None
    titre_professionnel: Optional[str] = None
    resume: Optional[str] = None


class Experience(BaseModel):
    poste: str
    entreprise: str
    periode: Optional[str] = None
    date_debut: Optional[str] = None
    date_fin: Optional[str] = None
    description: Optional[str] = None
    lieu: Optional[str] = None


class Formation(BaseModel):
    diplome: str
    etablissement: str
    periode: Optional[str] = None
    date_debut: Optional[str] = None
    date_fin: Optional[str] = None
    specialite: Optional[str] = None
    lieu: Optional[str] = None


class Competences(BaseModel):
    techniques: list[str] = Field(default_factory=list)
    soft_skills: list[str] = Field(default_factory=list)
    outils: list[str] = Field(default_factory=list)
    autres: list[str] = Field(default_factory=list)


class Langue(BaseModel):
    langue: str
    niveau: Optional[str] = None


class CvStandardise(BaseModel):
    profil: Profil
    experiences: list[Experience] = Field(default_factory=list)
    formations: list[Formation] = Field(default_factory=list)
    competences: Competences = Field(default_factory=Competences)
    langues: list[Langue] = Field(default_factory=list)
    certifications: list[str] = Field(default_factory=list)
    projets: list[str] = Field(default_factory=list)
    interets: list[str] = Field(default_factory=list)


class CvResponse(BaseModel):
    success: bool
    cv_standardise: Optional[CvStandardise] = None
    texte_brut: Optional[str] = None
    nb_pages: int = 0
    format_detecte: str = ""
    methode_extraction: str = ""
    message: str = ""
