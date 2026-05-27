"""
Scoring déterministe — Phase 3.

Calcule un ScoreMatching pour une paire (OffreInput, CandidatInput).

Poids :
  Compétences techniques  40 %
  Similarité TF-IDF       15 %
  Expérience              20 %
  Niveau académique       15 %
  Soft skills             10 %

Fonctions publiques :
  calculer_score_matching(offre, candidat) -> ScoreMatching
  calculer_scores_batch(offres, candidat)  -> list[tuple[OffreInput, ScoreMatching]]

Dépendances :
  scikit-learn (TfidfVectorizer + cosine_similarity)
  rapidfuzz    (via skills_extractor)
"""

import logging
import re
from typing import Optional

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from app.models.matching import (
    CandidatInput,
    DetailsScore,
    OffreInput,
    ScoreMatching,
)
from app.modules.matching.skills_extractor import (
    extraire_competences_depuis_texte,
    intersection_competences,
    normaliser_competence,
)

logger = logging.getLogger("smartintern.matching.scorer")

# ── Poids des composantes ─────────────────────────────────────────────────
POIDS_TECHNIQUES = 0.40
POIDS_TFIDF      = 0.15
POIDS_EXPERIENCE = 0.20
POIDS_NIVEAU     = 0.15
POIDS_SOFT       = 0.10

# ── Seuils niveau de matching ──────────────────────────────────────────────
SEUIL_EXCELLENT = 80
SEUIL_BON       = 60
SEUIL_MOYEN     = 40

# ── Correspondances niveau académique ─────────────────────────────────────
_NIVEAUX_ORDRE = {
    "bac": 1,
    "bac+1": 2, "bac +1": 2,
    "bac+2": 3, "bac +2": 3, "bts": 3, "dut": 3,
    "bac+3": 4, "bac +3": 4, "licence": 4, "bachelor": 4,
    "bac+4": 5, "bac +4": 5, "master 1": 5, "m1": 5,
    "bac+5": 6, "bac +5": 6, "master": 6, "master 2": 6, "m2": 6,
    "ingénieur": 6, "ingenieur": 6, "grande école": 6,
    "doctorat": 7, "phd": 7,
}


def _niveau_vers_rang(niveau: str) -> int:
    """Convertit un libellé de niveau en rang numérique (0 si inconnu)."""
    return _NIVEAUX_ORDRE.get(niveau.lower().strip(), 0)


# ══════════════════════════════════════════════════════════════════════════
# COMPOSANTE 1 — Compétences techniques (40 %)
# ══════════════════════════════════════════════════════════════════════════

def _score_competences_techniques(
    offre: OffreInput,
    candidat: CandidatInput,
) -> tuple[float, set[str], set[str], set[str]]:
    """
    Calcule le score de correspondance des compétences techniques.

    Retourne (score_0_100, match, manquantes, bonus).
    score = len(match) / len(requises) * 100  (0 si requises vide → 50 par défaut)
    """
    requises_raw = set(offre.competences_requises)

    # Extraction depuis le texte libre du candidat si la liste explicite est courte
    competences_candidat: set[str] = {
        normaliser_competence(c)
        for c in candidat.competences_techniques
        if c
    }
    if candidat.experiences_texte:
        competences_candidat |= extraire_competences_depuis_texte(candidat.experiences_texte)
    if candidat.formations_texte:
        competences_candidat |= extraire_competences_depuis_texte(candidat.formations_texte)
    for projet in candidat.projets:
        if projet:
            competences_candidat |= extraire_competences_depuis_texte(projet)

    match, manquantes, bonus = intersection_competences(requises_raw, competences_candidat)

    if not requises_raw:
        # Offre sans liste explicite → score neutre 50
        return 50.0, match, manquantes, bonus

    score = len(match) / len(requises_raw) * 100.0
    return min(score, 100.0), match, manquantes, bonus


# ══════════════════════════════════════════════════════════════════════════
# COMPOSANTE 2 — Similarité TF-IDF (15 %)
# ══════════════════════════════════════════════════════════════════════════

def _construire_texte_candidat(candidat: CandidatInput) -> str:
    """Concatène toutes les surfaces textuelles du candidat."""
    parties = [
        " ".join(candidat.competences_techniques),
        " ".join(candidat.soft_skills),
        candidat.experiences_texte or "",
        candidat.formations_texte or "",
        " ".join(candidat.projets),
    ]
    return " ".join(p for p in parties if p).strip()


def _construire_texte_offre(offre: OffreInput) -> str:
    """Concatène toutes les surfaces textuelles de l'offre."""
    parties = [
        offre.titre or "",
        offre.description or "",
        offre.domaine or "",
        " ".join(offre.competences_requises),
        " ".join(offre.soft_skills_requis),
    ]
    return " ".join(p for p in parties if p).strip()


def _score_tfidf(offre: OffreInput, candidat: CandidatInput) -> float:
    """
    Calcule la similarité cosinus TF-IDF entre le texte de l'offre et du candidat.
    Retourne un score 0–100.
    """
    texte_offre     = _construire_texte_offre(offre)
    texte_candidat  = _construire_texte_candidat(candidat)

    if not texte_offre or not texte_candidat:
        return 0.0

    try:
        vectorizer = TfidfVectorizer(
            analyzer="word",
            ngram_range=(1, 2),
            min_df=1,
            sublinear_tf=True,
        )
        tfidf_matrix = vectorizer.fit_transform([texte_offre, texte_candidat])
        similarity = cosine_similarity(tfidf_matrix[0:1], tfidf_matrix[1:2])[0][0]
        return round(float(similarity) * 100.0, 2)
    except Exception as exc:
        logger.warning(f"TF-IDF score error: {exc}")
        return 0.0


def _score_tfidf_batch(
    offres: list[OffreInput],
    candidat: CandidatInput,
) -> list[float]:
    """
    Version batch du TF-IDF : vectorise toutes les offres + le candidat en une passe.
    Beaucoup plus efficace pour la recommandation (1 candidat vs N offres).
    """
    textes_offres  = [_construire_texte_offre(o) for o in offres]
    texte_candidat = _construire_texte_candidat(candidat)

    if not texte_candidat:
        return [0.0] * len(offres)

    corpus = textes_offres + [texte_candidat]

    try:
        vectorizer = TfidfVectorizer(
            analyzer="word",
            ngram_range=(1, 2),
            min_df=1,
            sublinear_tf=True,
        )
        tfidf_matrix = vectorizer.fit_transform(corpus)
        candidat_vec = tfidf_matrix[-1]
        offres_matrix = tfidf_matrix[:-1]
        similarities = cosine_similarity(offres_matrix, candidat_vec).flatten()
        return [round(float(s) * 100.0, 2) for s in similarities]
    except Exception as exc:
        logger.warning(f"TF-IDF batch error: {exc}")
        return [0.0] * len(offres)


# ══════════════════════════════════════════════════════════════════════════
# COMPOSANTE 3 — Expérience (20 %)
# ══════════════════════════════════════════════════════════════════════════

_RE_MOIS = re.compile(
    r'(\d+)\s*(?:mois|month)',
    re.IGNORECASE,
)
_RE_ANS = re.compile(
    r'(\d+)\s*(?:an(?:s|née)?|year(?:s)?)',
    re.IGNORECASE,
)


def _extraire_duree_mois(texte: str) -> int:
    """Extrait la durée totale en mois depuis un texte libre."""
    total = 0
    for m in _RE_MOIS.finditer(texte):
        total += int(m.group(1))
    for m in _RE_ANS.finditer(texte):
        total += int(m.group(1)) * 12
    return total


def _score_experience(offre: OffreInput, candidat: CandidatInput) -> float:
    """
    Score d'expérience 0–100.
    Durée exigée par l'offre vs durée totale du candidat.
    Score = min(duree_candidat / duree_requise * 100, 100)
    Si l'offre ne précise pas de durée → score neutre 60.
    """
    duree_requise = offre.duree_mois or 0  # durée du stage en mois

    texte_exp = candidat.experiences_texte or ""
    duree_candidat = _extraire_duree_mois(texte_exp)

    if duree_requise <= 0:
        # Offre sans durée précise : donner un score basé sur l'expérience brute
        if duree_candidat >= 6:
            return 80.0
        if duree_candidat >= 3:
            return 60.0
        if duree_candidat >= 1:
            return 40.0
        return 30.0  # aucune expérience mentionnée

    # Score proportionnel à la durée exigée
    score = min(duree_candidat / duree_requise * 100.0, 100.0)
    return round(score, 2)


# ══════════════════════════════════════════════════════════════════════════
# COMPOSANTE 4 — Niveau académique (15 %)
# ══════════════════════════════════════════════════════════════════════════

def _score_niveau_academique(offre: OffreInput, candidat: CandidatInput) -> float:
    """
    Comparaison des niveaux académiques.
    Score :
      candidat >= requis  → 100
      candidat = requis-1 → 70
      candidat = requis-2 → 40
      candidat < requis-2 → 10
      niveau requis inconnu → 60 (neutre)
    """
    rang_requis   = _niveau_vers_rang(offre.niveau_requis or "")
    rang_candidat = _niveau_vers_rang(candidat.niveau_formation or "")

    if rang_requis == 0:
        return 60.0  # pas de prérequis défini → neutre

    if rang_candidat == 0:
        return 50.0  # niveau candidat inconnu → en dessous

    diff = rang_candidat - rang_requis
    if diff >= 0:
        return 100.0
    if diff == -1:
        return 70.0
    if diff == -2:
        return 40.0
    return 10.0


# ══════════════════════════════════════════════════════════════════════════
# COMPOSANTE 5 — Soft skills (10 %)
# ══════════════════════════════════════════════════════════════════════════

def _score_soft_skills(offre: OffreInput, candidat: CandidatInput) -> float:
    """
    Comparaison des soft skills exigés vs possédés.
    Score = len(match) / len(requis) * 100  (50 si aucun requis)
    """
    requis_raw = set(offre.soft_skills_requis)
    possedes_raw = set(candidat.soft_skills)

    if not requis_raw:
        return 50.0

    match, _, _ = intersection_competences(requis_raw, possedes_raw)
    score = len(match) / len(requis_raw) * 100.0
    return min(round(score, 2), 100.0)


# ══════════════════════════════════════════════════════════════════════════
# INTERPRÉTATION
# ══════════════════════════════════════════════════════════════════════════

def _niveau_depuis_score(score: float) -> str:
    if score >= SEUIL_EXCELLENT:
        return "EXCELLENT"
    if score >= SEUIL_BON:
        return "BON"
    if score >= SEUIL_MOYEN:
        return "MOYEN"
    return "FAIBLE"


def _probabilite_selection(score: float) -> float:
    """Heuristique simple : P(selection) ≈ score * 0.9 + 5 (clamped 0–95)."""
    return round(min(max(score * 0.9 + 5, 0.0), 95.0), 1)


def _generer_forces_faiblesses(
    details: DetailsScore,
    match: set[str],
    manquantes: set[str],
    bonus: set[str],
) -> tuple[list[str], list[str]]:
    """
    Génère des forces et faiblesses déterministes à partir des scores de détail.
    Utilisé quand le LLM n'est pas disponible ou échoue.
    """
    forces: list[str] = []
    faiblesses: list[str] = []

    # Compétences (champ : competences_techniques)
    if details.competences_techniques >= 80:
        forces.append(f"Excellente couverture des compétences requises ({len(match)} correspondances)")
    elif details.competences_techniques >= 50:
        forces.append(f"Bonne correspondance des compétences ({len(match)} sur les principales)")
    else:
        faiblesses.append(
            f"Compétences techniques insuffisantes — {len(manquantes)} compétences requises manquantes"
            + (f" : {', '.join(list(manquantes)[:3])}" if manquantes else "")
        )

    # Expérience (champ : experience)
    if details.experience >= 80:
        forces.append("Expérience pratique solide et bien documentée")
    elif details.experience >= 50:
        forces.append("Expérience partielle correspondant au profil")
    else:
        faiblesses.append("Expérience professionnelle limitée ou non renseignée")

    # Niveau académique (champ : niveau_academique)
    if details.niveau_academique >= 100:
        forces.append("Niveau académique égal ou supérieur au prérequis")
    elif details.niveau_academique >= 70:
        forces.append("Niveau académique proche du prérequis")
    else:
        faiblesses.append("Niveau de formation inférieur au requis pour ce poste")

    # Soft skills (champ : soft_skills)
    if details.soft_skills >= 80:
        forces.append("Profil comportemental très aligné avec les attentes")
    elif details.soft_skills < 40:
        faiblesses.append("Soft skills peu détaillées dans le profil")

    # TF-IDF (champ : similarite_tf_idf)
    if details.similarite_tf_idf >= 60:
        forces.append("Vocabulaire et domaine de compétences très proches de l'offre")
    elif details.similarite_tf_idf < 25:
        faiblesses.append("Peu de correspondance sémantique avec la description du poste")

    # Compétences bonus
    if bonus:
        top_bonus = ", ".join(list(bonus)[:3])
        forces.append(f"Compétences complémentaires appréciables : {top_bonus}")

    # Garantir au moins une force/faiblesse
    if not forces:
        forces.append("Profil à évaluer plus en détail lors d'un entretien")
    if not faiblesses:
        faiblesses.append("Continuer à enrichir le CV avec des expériences concrètes")

    return forces[:5], faiblesses[:3]


# ══════════════════════════════════════════════════════════════════════════
# FONCTION PRINCIPALE
# ══════════════════════════════════════════════════════════════════════════

def calculer_score_matching(
    offre: OffreInput,
    candidat: CandidatInput,
    tfidf_override: Optional[float] = None,
) -> ScoreMatching:
    """
    Calcule un ScoreMatching déterministe pour une paire offre/candidat.

    tfidf_override : score TF-IDF pré-calculé (pour le mode batch).
    """
    # Composante 1 : Compétences techniques
    s_tech, match, manquantes, bonus = _score_competences_techniques(offre, candidat)

    # Composante 2 : TF-IDF
    s_tfidf = tfidf_override if tfidf_override is not None else _score_tfidf(offre, candidat)

    # Composante 3 : Expérience
    s_exp = _score_experience(offre, candidat)

    # Composante 4 : Niveau académique
    s_niveau = _score_niveau_academique(offre, candidat)

    # Composante 5 : Soft skills
    s_soft = _score_soft_skills(offre, candidat)

    # ── Score pondéré global ──────────────────────────────────────────────
    score_global = (
        s_tech   * POIDS_TECHNIQUES
        + s_tfidf  * POIDS_TFIDF
        + s_exp    * POIDS_EXPERIENCE
        + s_niveau * POIDS_NIVEAU
        + s_soft   * POIDS_SOFT
    )
    score_global = round(min(max(score_global, 0.0), 100.0), 2)

    details = DetailsScore(
        competences_techniques=round(s_tech, 2),
        similarite_tf_idf=round(s_tfidf, 2),
        experience=round(s_exp, 2),
        niveau_academique=round(s_niveau, 2),
        soft_skills=round(s_soft, 2),
    )

    forces, faiblesses = _generer_forces_faiblesses(details, match, manquantes, bonus)

    return ScoreMatching(
        score=score_global,
        niveau=_niveau_depuis_score(score_global),
        forces=forces,
        faiblesses=faiblesses,
        probabilite_selection=_probabilite_selection(score_global),
        details_score=details,
    )


# ══════════════════════════════════════════════════════════════════════════
# SCORING BATCH (optimisé recommandation)
# ══════════════════════════════════════════════════════════════════════════

def calculer_scores_batch(
    offres: list[OffreInput],
    candidat: CandidatInput,
) -> list[tuple[OffreInput, ScoreMatching]]:
    """
    Calcule les scores pour une liste d'offres face à un candidat.

    Le TF-IDF est calculé une seule fois pour toutes les offres (efficacité O(N)).
    Retourne une liste triée par score décroissant.
    """
    if not offres:
        return []

    # TF-IDF batch
    tfidf_scores = _score_tfidf_batch(offres, candidat)

    resultats: list[tuple[OffreInput, ScoreMatching]] = []
    for offre, tfidf_score in zip(offres, tfidf_scores):
        try:
            score = calculer_score_matching(offre, candidat, tfidf_override=tfidf_score)
            resultats.append((offre, score))
        except Exception as exc:
            logger.error(f"Erreur scoring offre {offre.offre_id}: {exc}")

    resultats.sort(key=lambda t: t[1].score, reverse=True)
    return resultats
