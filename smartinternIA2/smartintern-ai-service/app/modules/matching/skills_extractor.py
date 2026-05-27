"""
Extraction et normalisation des compétences.

Fonctions publiques :
  normaliser_competence(s)          → forme canonique via skills_synonyms.json
  extraire_competences_depuis_texte(texte) → set[str] via tokenisation + fuzzy match
  intersection_competences(requises, possedees) → (match, manquantes, bonus)

Dépendances :
  rapidfuzz — fuzzy matching léger, sans modèle ML
"""

import json
import logging
import re
from functools import lru_cache
from pathlib import Path
from typing import Optional

from rapidfuzz import fuzz, process

logger = logging.getLogger("smartintern.matching.skills_extractor")

# ── Chemins ────────────────────────────────────────────────────────────────
_DATA_DIR   = Path(__file__).parent / "data"
_SYNONYMS_FILE = _DATA_DIR / "skills_synonyms.json"

# ── Seuil fuzzy match (0–100) ─────────────────────────────────────────────
FUZZY_THRESHOLD = 85

# ── Longueur minimale d'un token pour le fuzzy match ──────────────────────
MIN_TOKEN_LEN = 3


# ══════════════════════════════════════════════════════════════════════════
# CHARGEMENT DU DICTIONNAIRE (singleton mis en cache)
# ══════════════════════════════════════════════════════════════════════════

@lru_cache(maxsize=1)
def _charger_synonymes() -> dict[str, str]:
    """
    Charge le dictionnaire de synonymes depuis skills_synonyms.json.
    Les entrées dont la clé commence par '#' ou '__' sont ignorées (commentaires).
    Résultat mis en cache — chargement unique au démarrage.
    """
    if not _SYNONYMS_FILE.exists():
        logger.error(f"Fichier synonymes introuvable : {_SYNONYMS_FILE}")
        return {}

    with _SYNONYMS_FILE.open(encoding="utf-8") as fp:
        raw: dict = json.load(fp)

    # Filtrer les commentaires et entrées vides
    synonymes = {
        k.lower().strip(): v.lower().strip()
        for k, v in raw.items()
        if v and not k.startswith("#") and not k.startswith("_")
    }
    logger.info(f"Dictionnaire synonymes chargé : {len(synonymes)} entrées")
    return synonymes


def _competences_connues() -> list[str]:
    """Retourne l'union clés + valeurs du dictionnaire (pour le fuzzy match)."""
    synonymes = _charger_synonymes()
    return list(set(list(synonymes.keys()) + list(synonymes.values())))


# ══════════════════════════════════════════════════════════════════════════
# NORMALISATION
# ══════════════════════════════════════════════════════════════════════════

def normaliser_competence(s: str) -> str:
    """
    Normalise une compétence : lowercase, strip, puis mapping via synonymes.

    Exemples :
      "JS"           → "javascript"
      "Spring Boot"  → "spring boot"
      "k8s"          → "kubernetes"
      "inconnue"     → "inconnue"   (retourne l'entrée si absente du dico)
    """
    if not s:
        return ""
    cle = s.lower().strip()
    synonymes = _charger_synonymes()
    return synonymes.get(cle, cle)


# ══════════════════════════════════════════════════════════════════════════
# EXTRACTION DEPUIS TEXTE LIBRE
# ══════════════════════════════════════════════════════════════════════════

def _tokeniser(texte: str) -> list[str]:
    """
    Tokenise un texte en mots et expressions courantes.
    Gère les tokens composés (ex: 'spring boot', 'react native').
    """
    texte_norm = texte.lower()

    # Unigrams : mots avec caractères spéciaux courants (#, +, .)
    unigrams = re.findall(r'\b[\w\+\#\./-]+\b', texte_norm)

    # Bigrams : paires consécutives (pour 'react native', 'spring boot', etc.)
    mots = re.findall(r'\b\w+\b', texte_norm)
    bigrams = [f"{mots[i]} {mots[i+1]}" for i in range(len(mots) - 1)]

    return unigrams + bigrams


def extraire_competences_depuis_texte(texte: str) -> set[str]:
    """
    Extrait les compétences reconnues depuis un texte libre.

    Pipeline :
      1. Tokenisation (unigrams + bigrams)
      2. Match exact dans le dictionnaire synonymes
      3. Fuzzy match (rapidfuzz.fuzz.ratio ≥ FUZZY_THRESHOLD) sur les tokens
         de longueur ≥ MIN_TOKEN_LEN non encore matchés

    Retourne un set[str] de compétences normalisées (formes canoniques).
    """
    if not texte or not texte.strip():
        return set()

    synonymes          = _charger_synonymes()
    competences_connues = _competences_connues()
    resultats: set[str] = set()
    tokens_non_matchs: list[str] = []

    for token in _tokeniser(texte):
        # ── Match exact ──────────────────────────────────────────────────
        if token in synonymes:
            resultats.add(synonymes[token])
            continue
        if token in set(synonymes.values()):
            resultats.add(token)
            continue
        # Stocker pour le fuzzy round suivant
        if len(token) >= MIN_TOKEN_LEN:
            tokens_non_matchs.append(token)

    # ── Fuzzy match sur les tokens non encore matchés ──────────────────────
    for token in set(tokens_non_matchs):  # déduplique les tokens
        resultat = process.extractOne(
            token,
            competences_connues,
            scorer=fuzz.ratio,
            score_cutoff=FUZZY_THRESHOLD,
        )
        if resultat:
            match_str, _score, _idx = resultat
            # Normaliser le match trouvé
            normalise = synonymes.get(match_str, match_str)
            resultats.add(normalise)

    return resultats


# ══════════════════════════════════════════════════════════════════════════
# INTERSECTION / DIFFÉRENCE
# ══════════════════════════════════════════════════════════════════════════

def intersection_competences(
    requises:  set[str],
    possedees: set[str],
) -> tuple[set[str], set[str], set[str]]:
    """
    Calcule la correspondance entre compétences requises et possédées.

    Retourne :
      (match, manquantes, bonus)
      - match      : requises ∩ possedees
      - manquantes : requises - possedees
      - bonus      : possedees - requises  (compétences en plus)

    Les deux sets sont normalisés avant comparaison.

    Exemple :
      requises  = {"java", "spring boot", "react.js"}
      possedees = {"java", "spring boot", "angular", "docker"}
      → match      = {"java", "spring boot"}
      → manquantes = {"react.js"}
      → bonus      = {"angular", "docker"}
    """
    req_norm = {normaliser_competence(c) for c in requises if c}
    pos_norm = {normaliser_competence(c) for c in possedees if c}

    match      = req_norm & pos_norm
    manquantes = req_norm - pos_norm
    bonus      = pos_norm - req_norm

    return match, manquantes, bonus
