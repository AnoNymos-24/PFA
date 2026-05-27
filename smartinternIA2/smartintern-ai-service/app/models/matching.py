"""
Modèles Pydantic — Module Matching & Recommandation IA.

Miroirs des DTO Java (MatchingDto.java).
alias_generator=to_camel : snake_case Python → camelCase JSON pour Jackson.

Endpoints couverts :
  POST /ai/matching/candidats       → classement candidats pour une offre
  POST /ai/recommandation/offres    → recommandation d'offres pour un étudiant
  POST /ai/analyse/profil           → analyse transversale du profil
"""

from datetime import datetime
from typing import Optional
from pydantic import Field

from app.models.cv import CamelModel


# ══════════════════════════════════════════════════════════════════════════
# MODÈLES D'ENTRÉE (reçus depuis Java ou l'interface de test)
# ══════════════════════════════════════════════════════════════════════════

class OffreInput(CamelModel):
    """
    Représentation d'une offre de stage transmise au moteur de matching.
    Miroir partiel de l'entité Java OffreStage.
    """
    offre_id:            Optional[int]  = None       # → offreId
    titre:               str            = ""
    description:         str            = ""
    domaine:             str            = ""
    type_stage:          str            = ""         # → typeStage  (PFE, PFA, Stage d'été…)
    niveau_requis:       str            = ""         # → niveauRequis  (Bac+3, Bac+5…)
    duree_mois:          int            = 0          # → dureeMois
    competences_requises: list[str]     = Field(default_factory=list)   # → competencesRequises
    soft_skills_requis:  list[str]      = Field(default_factory=list)   # → softSkillsRequis


class CandidatInput(CamelModel):
    """
    Profil d'un candidat transmis au moteur de matching.
    Construit côté Java à partir de Candidature + Etudiant + CvStandardise.
    """
    candidature_id:        Optional[int]   = None    # → candidatureId
    etudiant_id:           Optional[int]   = None    # → etudiantId
    nom_complet:           str             = ""       # → nomComplet
    competences_techniques: list[str]      = Field(default_factory=list)   # → competencesTechniques
    soft_skills:           list[str]       = Field(default_factory=list)   # → softSkills
    experiences_texte:     str             = ""       # → experiencesTexte  (texte libre)
    formations_texte:      str             = ""       # → formationsTexte   (texte libre)
    projets:               list[str]       = Field(default_factory=list)
    score_completude_cv:   float           = 0.0      # → scoreCompletudeCv (0–100)
    niveau_formation:      str             = ""       # → niveauFormation   (ex: "Bac+5")


class EtudiantInput(CamelModel):
    """
    Profil d'un étudiant transmis au moteur de recommandation.
    Identique à CandidatInput mais sans candidature_id.
    """
    etudiant_id:           Optional[int]   = None
    nom_complet:           str             = ""
    competences_techniques: list[str]      = Field(default_factory=list)
    soft_skills:           list[str]       = Field(default_factory=list)
    experiences_texte:     str             = ""
    formations_texte:      str             = ""
    projets:               list[str]       = Field(default_factory=list)
    score_completude_cv:   float           = 0.0
    niveau_formation:      str             = ""


# ══════════════════════════════════════════════════════════════════════════
# REQUÊTES
# ══════════════════════════════════════════════════════════════════════════

class MatchingRequest(CamelModel):
    """Corps de la requête POST /ai/matching/candidats."""
    offre:     OffreInput
    candidats: list[CandidatInput] = Field(default_factory=list)


class RecommandationRequest(CamelModel):
    """Corps de la requête POST /ai/recommandation/offres."""
    etudiant: EtudiantInput
    offres:   list[OffreInput] = Field(default_factory=list)
    limit:    int              = 10   # nombre maximal d'offres retournées


class AnalyseProfilRequest(CamelModel):
    """Corps de la requête POST /ai/analyse/profil."""
    etudiant:       EtudiantInput
    domaine_cible:  str = ""   # → domaineCible  (domaine visé pour l'analyse)


# ══════════════════════════════════════════════════════════════════════════
# RÉSULTATS DE MATCHING
# ══════════════════════════════════════════════════════════════════════════

class DetailsScore(CamelModel):
    """Détail pondéré du score de matching (sous-scores par critère, 0–100)."""
    competences_techniques: float = 0.0   # → competencesTechniques  (40 %)
    similarite_tf_idf:      float = 0.0   # → similariteTfIdf        (15 %)
    experience:             float = 0.0   # → experience              (20 %)
    niveau_academique:      float = 0.0   # → niveauAcademique        (15 %)
    soft_skills:            float = 0.0   # → softSkills              (10 %)


class ScoreMatching(CamelModel):
    """
    Score de compatibilité calculé pour un couple (offre, candidat/étudiant).

    Niveaux :
      EXCELLENT ≥ 80 · BON ≥ 60 · MOYEN ≥ 40 · FAIBLE < 40

    probabilite_selection : clamp(score × 0.95, 0, 95) — jamais 100 % pour rester crédible.
    """
    score:                float        = 0.0
    niveau:               str          = "FAIBLE"    # EXCELLENT | BON | MOYEN | FAIBLE
    forces:               list[str]    = Field(default_factory=list)
    faiblesses:           list[str]    = Field(default_factory=list)
    probabilite_selection: float       = 0.0         # → probabiliteSelection
    details_score:        DetailsScore = Field(default_factory=DetailsScore)  # → detailsScore


# ══════════════════════════════════════════════════════════════════════════
# RÉSULTATS TYPÉS PAR ENDPOINT
# ══════════════════════════════════════════════════════════════════════════

class CandidatScore(CamelModel):
    """Un candidat avec son score de matching — élément de la liste classée."""
    candidature_id:  Optional[int] = None
    etudiant_id:     Optional[int] = None
    nom_complet:     str           = ""
    score_matching:  ScoreMatching = Field(default_factory=ScoreMatching)  # → scoreMatching


class OffreRecommandee(CamelModel):
    """Une offre recommandée avec son score — élément du classement étudiant."""
    offre_id:       Optional[int] = None
    titre:          str           = ""
    domaine:        str           = ""
    entreprise_nom: str           = ""    # → entrepriseNom
    score_matching: ScoreMatching = Field(default_factory=ScoreMatching)


# ── Data wrappers (contenu du champ `data` dans la réponse) ───────────────

class MatchingData(CamelModel):
    """Contenu de la réponse matching candidats."""
    offre_id:           Optional[int]      = None
    titre_offre:        str                = ""
    candidats_classes:  list[CandidatScore] = Field(default_factory=list)  # → candidatsClasses


class RecommandationData(CamelModel):
    """Contenu de la réponse recommandation offres."""
    etudiant_id:         Optional[int]        = None
    offres_recommandees: list[OffreRecommandee] = Field(default_factory=list)  # → offresRecommandees


class AnalyseProfilData(CamelModel):
    """Contenu de la réponse analyse de profil."""
    competences_presentes:  list[str] = Field(default_factory=list)   # → competencesPresentes
    competences_manquantes: list[str] = Field(default_factory=list)   # → competencesManquantes
    domaines_forts:         list[str] = Field(default_factory=list)   # → domainesForts
    suggestions:            list[str] = Field(default_factory=list)


# ══════════════════════════════════════════════════════════════════════════
# RÉPONSES API (format standard)
# ══════════════════════════════════════════════════════════════════════════

def _now_iso() -> str:
    return datetime.now().isoformat()


class MatchingResponse(CamelModel):
    """Réponse standard de POST /ai/matching/candidats."""
    success:      bool                   = True
    generated_at: str                    = Field(default_factory=_now_iso)   # → generatedAt
    ai_version:   str                    = "smartintern-ai-v1"               # → aiVersion
    data:         Optional[MatchingData] = None
    message:      str                    = ""


class RecommandationResponse(CamelModel):
    """Réponse standard de POST /ai/recommandation/offres."""
    success:      bool                          = True
    generated_at: str                           = Field(default_factory=_now_iso)
    ai_version:   str                           = "smartintern-ai-v1"
    data:         Optional[RecommandationData]  = None
    message:      str                           = ""


class AnalyseProfilResponse(CamelModel):
    """Réponse standard de POST /ai/analyse/profil."""
    success:      bool                          = True
    generated_at: str                           = Field(default_factory=_now_iso)
    ai_version:   str                           = "smartintern-ai-v1"
    data:         Optional[AnalyseProfilData]   = None
    message:      str                           = ""
