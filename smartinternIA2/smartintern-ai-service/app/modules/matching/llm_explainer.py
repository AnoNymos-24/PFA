"""
Enrichissement LLM — Phase 4.

Prend un ScoreMatching déterministe (Phase 3) et lui ajoute des forces/faiblesses
générées par le LLM (texte plus naturel et contextualisé).

Règles absolues :
  - Réutilise ai_client (singleton FallbackLLMClient) — jamais de nouveau OpenAI()
  - Si le LLM échoue → dégradation gracieuse (on garde les forces/faiblesses déterministes)
  - Jamais d'exception levée vers l'appelant
  - System prompt < 600 tokens
  - Sortie JSON stricte { "forces": [...], "faiblesses": [...] }
  - eval() / exec() sont interdits — on utilise json.loads()
"""

import json
import logging
from typing import Optional

from app.core.ai_client import ai_client
from app.models.matching import CandidatInput, DetailsScore, OffreInput, ScoreMatching
from app.modules.cv_extraction.service import nettoyer_json

logger = logging.getLogger("smartintern.matching.llm_explainer")

# ── Prompt système (< 600 tokens) ─────────────────────────────────────────
_SYSTEM_PROMPT = """Tu es un assistant RH expert en recrutement de stagiaires.
Ton rôle : analyser la correspondance entre un profil étudiant et une offre de stage,
puis fournir 2 à 4 forces et 1 à 3 faiblesses, en français, concises et actionnables.

IMPORTANT — Réponds UNIQUEMENT avec du JSON valide, sans balise markdown :
{
  "forces":    ["...", "..."],
  "faiblesses": ["...", "..."]
}

Règles :
- Forces : points positifs authentiques, valorisants, spécifiques au profil et au poste.
- Faiblesses : lacunes constructives, jamais insultantes, formulées comme des pistes de progrès.
- Maximum 80 mots par élément.
- Si le profil est très fort : 4 forces, 1 faiblesse.
- Si le profil est faible  : 2 forces, 3 faiblesses.
- Pas de MarkDown, pas de texte hors du JSON."""


def _construire_prompt_utilisateur(
    offre: OffreInput,
    candidat: CandidatInput,
    score: ScoreMatching,
) -> str:
    """
    Construit le message utilisateur envoyé au LLM.
    Format compact pour ne pas dépasser le contexte.
    """
    details: DetailsScore = score.details_score

    lignes = [
        f"OFFRE : {offre.titre} | Domaine : {offre.domaine}",
        f"Compétences requises : {', '.join(offre.competences_requises[:8]) or 'non précisées'}",
        f"Niveau requis : {offre.niveau_requis or 'non précisé'}",
        f"Durée : {offre.duree_mois} mois",
        "",
        f"CANDIDAT : {candidat.nom_complet}",
        f"Niveau formation : {candidat.niveau_formation or 'non précisé'}",
        f"Compétences : {', '.join(candidat.competences_techniques[:10]) or 'non précisées'}",
        f"Soft skills : {', '.join(candidat.soft_skills[:5]) or 'non précisés'}",
        f"Expériences : {(candidat.experiences_texte or 'aucune')[:200]}",
        "",
        f"SCORE GLOBAL : {score.score:.1f}/100 ({score.niveau})",
        f"  Compétences : {details.competences_techniques:.0f}/100",
        f"  Expérience  : {details.experience:.0f}/100",
        f"  Niveau acad.: {details.niveau_academique:.0f}/100",
        f"  Soft skills : {details.soft_skills:.0f}/100",
        f"  Sémantique  : {details.similarite_tf_idf:.0f}/100",
    ]
    return "\n".join(lignes)


def _extraire_forces_faiblesses(
    texte_llm: str,
) -> Optional[tuple[list[str], list[str]]]:
    """
    Parse la réponse JSON du LLM.
    Retourne (forces, faiblesses) ou None si le parsing échoue.
    """
    try:
        nettoye = nettoyer_json(texte_llm)
        data = json.loads(nettoye)
        forces    = data.get("forces", [])
        faiblesses = data.get("faiblesses", [])
        if isinstance(forces, list) and isinstance(faiblesses, list):
            return forces, faiblesses
    except (json.JSONDecodeError, ValueError, KeyError) as exc:
        logger.warning(f"Parse LLM échoué : {exc} | Texte: {texte_llm[:200]}")
    return None


def expliquer_matching(
    offre: OffreInput,
    candidat: CandidatInput,
    score: ScoreMatching,
) -> ScoreMatching:
    """
    Enrichit un ScoreMatching avec des forces/faiblesses générées par le LLM.

    En cas d'indisponibilité du LLM ou d'erreur de parsing :
      → retourne le score d'origine (forces/faiblesses déterministes conservées)

    Ne lève jamais d'exception vers l'appelant.

    Paramètres
    ----------
    offre     : OffreInput
    candidat  : CandidatInput
    score     : ScoreMatching produit par calculer_score_matching() (Phase 3)

    Retour
    ------
    ScoreMatching identique avec forces/faiblesses LLM si disponibles.
    """
    if ai_client is None:
        logger.debug("ai_client non disponible — forces/faiblesses déterministes conservées")
        return score

    try:
        prompt_user = _construire_prompt_utilisateur(offre, candidat, score)

        response = ai_client.chat.completions.create(
            model="",            # ignoré par FallbackLLMClient
            messages=[
                {"role": "system",  "content": _SYSTEM_PROMPT},
                {"role": "user",    "content": prompt_user},
            ],
            max_tokens=400,
            temperature=0.4,
        )

        texte_llm = response.choices[0].message.content or ""
        parsed = _extraire_forces_faiblesses(texte_llm)

        if parsed is None:
            logger.warning("Réponse LLM non parsable — dégradation vers déterministe")
            return score

        forces_llm, faiblesses_llm = parsed

        # Sanity checks : listes non vides, éléments strings
        if not forces_llm or not all(isinstance(f, str) for f in forces_llm):
            logger.warning("Forces LLM invalides — dégradation vers déterministe")
            return score
        if not faiblesses_llm or not all(isinstance(f, str) for f in faiblesses_llm):
            logger.warning("Faiblesses LLM invalides — dégradation vers déterministe")
            return score

        # Retourner un nouveau ScoreMatching enrichi (on remplace forces/faiblesses seulement)
        return ScoreMatching(
            score=score.score,
            niveau=score.niveau,
            forces=forces_llm[:5],
            faiblesses=faiblesses_llm[:3],
            probabilite_selection=score.probabilite_selection,
            details_score=score.details_score,
        )

    except Exception as exc:
        # Toujours en mode dégradé : on ne laisse jamais une exception remonter
        logger.error(f"Erreur LLM explainer [{type(exc).__name__}]: {exc}")
        return score
