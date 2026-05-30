"""
AD-04 — Router FastAPI : analyse de risque d'engagement.

Endpoint : POST /ai/stage/analyse-risque
"""

import logging
from fastapi import APIRouter
from pydantic import BaseModel, Field
from typing import Optional

from .service import analyser_risque

logger = logging.getLogger("smartintern.risque.router")

router = APIRouter(prefix="/ai/stage", tags=["🚨 Analyse de risque (AD-04)"])


# ── Modèles Pydantic ──────────────────────────────────────────────────────────

class StagePayload(BaseModel):
    id: int
    dateDebut: Optional[str] = None
    dateFin:   Optional[str] = None
    progressionTemporelle: float = Field(default=0.0, ge=0, le=100)


class EtudiantPayload(BaseModel):
    id: int
    nom:    str = ""
    prenom: str = ""


class RapportsPayload(BaseModel):
    total:     int = 0
    enRetard:  int = 0
    nonSoumis: int = 0


class TachePayload(BaseModel):
    titre:         str = ""
    statut:        str = "A_FAIRE"
    joursInactifs: int = 0


class ActivitePayload(BaseModel):
    derniereConnexion:         Optional[str] = None
    frequenceConnexionSemaine: float = 0.0


class RisqueAnalyseRequest(BaseModel):
    stage:    StagePayload
    etudiant: EtudiantPayload
    rapports: RapportsPayload = RapportsPayload()
    taches:   list[TachePayload] = []
    activite: ActivitePayload = ActivitePayload()


# ── Endpoint ──────────────────────────────────────────────────────────────────

@router.post(
    "/analyse-risque",
    summary="Analyser le risque d'engagement d'un étudiant",
    description=(
        "Reçoit les métriques agrégées par Spring Boot et retourne un score "
        "d'engagement (0-100), un niveau de risque, des alertes et des recommandations.\n\n"
        "**Niveaux de risque :** `EXCELLENT` (80-100) · `STABLE` (60-79) · "
        "`A_SURVEILLER` (40-59) · `A_RISQUE` (0-39)\n\n"
        "**Pondération :** Rapports 35 % · Tâches 30 % · Activité 20 % · Connexions 15 %"
    ),
)
async def analyser(payload: RisqueAnalyseRequest):
    """
    Algorithme déterministe pondéré (sans LLM) :

    - score_rapports   : max(0, 100 − retards×20 − nonSoumis×30)
    - score_taches     : (tâches_ok / total) × 100
    - score_activite   : ≥ 3/sem → 100 · 1-2/sem → 60 · 0/sem → 0
    - score_connexions : < 3 j → 100 · 3-7 j → 70 · 7-14 j → 30 · > 14 j → 0
    - score_final      : 0.35 × rapports + 0.30 × tâches + 0.20 × activité + 0.15 × connexions

    Alertes : CRITIQUE · ELEVE · MOYEN
    """
    logger.info("Analyse risque — stageId=%s etudiant=%s %s",
                payload.stage.id, payload.etudiant.prenom, payload.etudiant.nom)
    return analyser_risque(payload.model_dump())
