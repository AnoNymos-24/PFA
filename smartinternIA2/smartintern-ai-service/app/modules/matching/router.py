"""
Routes FastAPI — Module Matching & Recommandation IA.
Préfixe : /ai

Endpoints :
  POST /ai/matching/candidats       — classement des candidats d'une offre (côté entreprise)
  POST /ai/recommandation/offres    — recommandation d'offres pour un étudiant
  POST /ai/analyse/profil           — analyse transversale du profil étudiant

Sécurité :
  Les contrôles d'authentification sont gérés côté Java (Spring Security).
  Ce microservice reçoit des données déjà validées et ne nécessite pas de JWT.
"""

import logging

from fastapi import APIRouter, HTTPException

from app.models.matching import (
    MatchingRequest,       MatchingResponse,
    RecommandationRequest, RecommandationResponse,
    AnalyseProfilRequest,  AnalyseProfilResponse,
)
from app.modules.matching.service import (
    service_matcher_candidats,
    service_recommander_offres,
    service_analyser_profil,
)

logger = logging.getLogger("smartintern.matching.router")

router = APIRouter(prefix="/ai", tags=["🤖 Matching & Recommandation IA"])


# ══════════════════════════════════════════════════════════════════════════
# POST /ai/matching/candidats
# ══════════════════════════════════════════════════════════════════════════

@router.post(
    "/matching/candidats",
    response_model=MatchingResponse,
    summary="Classement des candidats pour une offre (côté entreprise)",
)
async def matcher_candidats(request: MatchingRequest) -> MatchingResponse:
    """
    Classe les candidats d'une offre de stage par score de compatibilité décroissant.

    **Pipeline :**
    1. Normalisation et extraction des compétences (rapidfuzz + dictionnaire synonymes)
    2. Scoring déterministe pondéré (compétences 40 % + TF-IDF 15 % + expérience 20 %
       + niveau académique 15 % + soft skills 10 %)
    3. Enrichissement LLM pour les forces/faiblesses (mode dégradé si LLM indisponible)
    4. Tri décroissant par score

    **Réponse :**
    - `candidatsClasses` : liste triée avec `scoreMatching.score`, `niveau`,
      `forces`, `faiblesses`, `probabiliteSelection`
    """
    try:
        data = await service_matcher_candidats(request)
        return MatchingResponse(
            success=True,
            data=data,
            message=(
                f"{len(data.candidats_classes)} candidat(s) classé(s) "
                f"pour l'offre « {data.titre_offre} »"
            ),
        )
    except HTTPException:
        raise
    except Exception as exc:
        logger.error(f"Erreur matching candidats : {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Erreur interne matching : {exc}")


# ══════════════════════════════════════════════════════════════════════════
# POST /ai/recommandation/offres
# ══════════════════════════════════════════════════════════════════════════

@router.post(
    "/recommandation/offres",
    response_model=RecommandationResponse,
    summary="Recommandation d'offres adaptées au profil d'un étudiant",
)
async def recommander_offres(request: RecommandationRequest) -> RecommandationResponse:
    """
    Recommande les offres de stage les mieux adaptées au profil de l'étudiant.

    **Paramètre** : `limit` (défaut 10) — nombre maximal d'offres retournées.

    **Pipeline :** même scoring que le matching candidats,
    adapté pour comparer un étudiant unique à plusieurs offres.
    Seules les top-5 offres reçoivent l'enrichissement LLM pour optimiser les coûts.
    """
    try:
        data = await service_recommander_offres(request)
        return RecommandationResponse(
            success=True,
            data=data,
            message=(
                f"{len(data.offres_recommandees)} offre(s) recommandée(s) "
                f"sur {len(request.offres)} analysée(s)"
            ),
        )
    except HTTPException:
        raise
    except Exception as exc:
        logger.error(f"Erreur recommandation offres : {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Erreur interne recommandation : {exc}")


# ══════════════════════════════════════════════════════════════════════════
# POST /ai/analyse/profil
# ══════════════════════════════════════════════════════════════════════════

@router.post(
    "/analyse/profil",
    response_model=AnalyseProfilResponse,
    summary="Analyse transversale du profil d'un étudiant",
)
async def analyser_profil(request: AnalyseProfilRequest) -> AnalyseProfilResponse:
    """
    Analyse le profil de l'étudiant et identifie les compétences manquantes
    par rapport aux tendances du marché (domaine cible optionnel).

    > **Note :** L'analyse de marché via LLM est partiellement implémentée.
    > Les compétences présentes et les suggestions de base sont disponibles.
    """
    try:
        data = await service_analyser_profil(request)
        return AnalyseProfilResponse(
            success=True,
            data=data,
            message=f"Analyse profil — {len(data.competences_presentes)} compétence(s) identifiée(s)",
        )
    except HTTPException:
        raise
    except Exception as exc:
        logger.error(f"Erreur analyse profil : {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Erreur interne analyse profil : {exc}")
