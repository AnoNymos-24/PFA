"""
AD-04 — Service d'analyse du risque d'engagement étudiant.

Algorithme pondéré déterministe (sans LLM) :
  • Rapports   35 % — retards et rapports non soumis
  • Tâches     30 % — proportion de tâches à jour
  • Activité   20 % — fréquence de connexion hebdomadaire
  • Connexions 15 % — ancienneté de la dernière connexion

Niveaux : EXCELLENT (≥80) · STABLE (60-79) · A_SURVEILLER (40-59) · A_RISQUE (<40)
Alertes  : CRITIQUE · ELEVE · MOYEN
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any

logger = logging.getLogger("smartintern.risque")


# ── Seuils de niveaux ────────────────────────────────────────────────────────

SEUIL_EXCELLENT    = 80
SEUIL_STABLE       = 60
SEUIL_A_SURVEILLER = 40


# ── Point d'entrée ────────────────────────────────────────────────────────────

def analyser_risque(payload: dict[str, Any]) -> dict[str, Any]:
    """
    Point d'entrée du service.

    :param payload: dict conforme à RisqueAnalyseRequest (camelCase)
    :return:        dict conforme à RisqueAnalyseResponse
    """
    try:
        score, detail = _calculer_score(payload)
        niveau        = _determiner_niveau(score)
        alertes       = _generer_alertes(payload, detail, score)
        recommandations = _generer_recommandations(detail, niveau)

        return {
            "success":        True,
            "scoreEngagement": score,
            "niveauRisque":   niveau,
            "analyse": {
                "retardsImportants":       detail["retards_importants"],
                "inactiviteDetectee":      detail["inactivite_detectee"],
                "tachesNonCommencees":     detail["taches_non_commencees"],
                "faibleFrequenceConnexion": detail["faible_frequence"],
            },
            "alertes":         alertes,
            "recommandations": recommandations,
        }
    except Exception as exc:
        logger.exception("Erreur analyse risque : %s", exc)
        return {
            "success":        False,
            "scoreEngagement": 50,
            "niveauRisque":   "STABLE",
            "analyse": {
                "retardsImportants":       False,
                "inactiviteDetectee":      False,
                "tachesNonCommencees":     False,
                "faibleFrequenceConnexion": False,
            },
            "alertes":         [],
            "recommandations": ["Données insuffisantes pour une analyse complète."],
            "erreur":          str(exc),
        }


# ── Calcul du score pondéré ───────────────────────────────────────────────────

def _calculer_score(payload: dict) -> tuple[int, dict]:
    """
    Retourne (score_final: int, detail: dict).

    Formule :
        score_final = 0.35 * rapports + 0.30 * taches + 0.20 * activite + 0.15 * connexions
    """
    detail: dict[str, Any] = {
        "retards_importants":      False,
        "inactivite_detectee":     False,
        "taches_non_commencees":   False,
        "faible_frequence":        False,
        "nb_rapports_retard":      0,
        "nb_rapports_non_soumis":  0,
        "nb_taches_non_commencees": 0,
        "nb_taches_en_retard":     0,
        "jours_inactivite":        0,
        "frequence":               0.0,
        "progression":             0.0,
        "score_rapports":          100.0,
        "score_taches":            100.0,
        "score_activite":          100.0,
        "score_connexions":        100.0,
    }

    # ── 1. Composante Rapports (35 %) ────────────────────────────────────────
    rapports   = payload.get("rapports", {})
    en_retard  = int(rapports.get("enRetard",  0))
    non_soumis = int(rapports.get("nonSoumis", 0))

    score_rapports = max(0.0, 100.0 - en_retard * 20 - non_soumis * 30)
    detail["nb_rapports_retard"]     = en_retard
    detail["nb_rapports_non_soumis"] = non_soumis
    detail["retards_importants"]     = (en_retard + non_soumis) >= 2
    detail["score_rapports"]         = score_rapports

    # ── 2. Composante Tâches (30 %) ──────────────────────────────────────────
    taches       = payload.get("taches", [])
    total_taches = len(taches)

    nb_non_commencees  = 0
    nb_en_retard_cours = 0
    taches_ok          = 0

    for t in taches:
        statut         = t.get("statut", "")
        jours_inactifs = int(t.get("joursInactifs", 0))

        if statut == "A_FAIRE" and jours_inactifs > 5:
            nb_non_commencees += 1
        elif statut == "EN_COURS" and jours_inactifs > 0:
            nb_en_retard_cours += 1
        else:
            taches_ok += 1

    score_taches = (taches_ok / total_taches * 100.0) if total_taches > 0 else 100.0
    detail["nb_taches_non_commencees"] = nb_non_commencees
    detail["nb_taches_en_retard"]      = nb_en_retard_cours
    detail["taches_non_commencees"]    = nb_non_commencees > 0
    detail["score_taches"]             = score_taches

    # ── 3. Composante Activité / fréquence (20 %) ────────────────────────────
    activite  = payload.get("activite", {})
    frequence = float(activite.get("frequenceConnexionSemaine", 0.0))

    if frequence >= 3:
        score_activite = 100.0
    elif frequence >= 1:
        score_activite = 60.0
    else:
        score_activite = 0.0

    detail["frequence"]       = frequence
    detail["faible_frequence"] = frequence < 2.0
    detail["score_activite"]  = score_activite

    # ── 4. Composante Connexion / ancienneté (15 %) ──────────────────────────
    derniere_connexion_str = activite.get("derniereConnexion")
    jours_inactif = 0

    if derniere_connexion_str:
        try:
            dc  = datetime.fromisoformat(derniere_connexion_str.replace("Z", "+00:00"))
            now = datetime.now(tz=dc.tzinfo if dc.tzinfo else timezone.utc)
            jours_inactif = max(0, (now - dc).days)
        except Exception:
            pass

    if jours_inactif < 3:
        score_connexions = 100.0
    elif jours_inactif <= 7:
        score_connexions = 70.0
    elif jours_inactif <= 14:
        score_connexions = 30.0
    else:
        score_connexions = 0.0

    detail["jours_inactivite"]    = jours_inactif
    detail["inactivite_detectee"] = jours_inactif > 7
    detail["score_connexions"]    = score_connexions

    # ── 5. Progression temporelle (contexte alertes uniquement) ──────────────
    stage       = payload.get("stage", {})
    progression = float(stage.get("progressionTemporelle", 0))
    detail["progression"] = progression

    # ── Score final pondéré ──────────────────────────────────────────────────
    score_final = (
        0.35 * score_rapports
        + 0.30 * score_taches
        + 0.20 * score_activite
        + 0.15 * score_connexions
    )
    score_final = max(0, min(100, round(score_final)))
    return int(score_final), detail


# ── Niveau de risque ─────────────────────────────────────────────────────────

def _determiner_niveau(score: int) -> str:
    if score >= SEUIL_EXCELLENT:
        return "EXCELLENT"
    elif score >= SEUIL_STABLE:
        return "STABLE"
    elif score >= SEUIL_A_SURVEILLER:
        return "A_SURVEILLER"
    else:
        return "A_RISQUE"


# ── Alertes (niveaux : CRITIQUE · ELEVE · MOYEN) ─────────────────────────────

def _generer_alertes(payload: dict, detail: dict, score: int) -> list[dict]:
    alertes: list[dict] = []
    etudiant = payload.get("etudiant", {})
    prenom   = etudiant.get("prenom", "L'étudiant")

    # — Inactivité (dernière connexion) —
    jours_inactif = detail["jours_inactivite"]
    if jours_inactif > 14:
        alertes.append({
            "type":    "INACTIVITE",
            "niveau":  "CRITIQUE",
            "message": f"{prenom} ne s'est pas connecté depuis {jours_inactif} jours.",
        })
    elif jours_inactif > 7:
        alertes.append({
            "type":    "INACTIVITE",
            "niveau":  "ELEVE",
            "message": f"{prenom} ne s'est pas connecté depuis {jours_inactif} jours.",
        })

    # — Faible fréquence —
    if detail["faible_frequence"]:
        alertes.append({
            "type":    "FAIBLE_FREQUENCE",
            "niveau":  "MOYEN",
            "message": (
                f"Fréquence de connexion insuffisante : {detail['frequence']:.1f} "
                f"fois/semaine (minimum recommandé : 2)."
            ),
        })

    # — Rapports non soumis —
    if detail["nb_rapports_non_soumis"] > 0:
        alertes.append({
            "type":    "RAPPORT",
            "niveau":  "CRITIQUE" if detail["nb_rapports_non_soumis"] >= 2 else "ELEVE",
            "message": (
                f"{detail['nb_rapports_non_soumis']} rapport(s) hebdomadaire(s) "
                f"attendu(s) mais jamais rédigé(s)."
            ),
        })

    # — Rapports en brouillon retard —
    if detail["nb_rapports_retard"] > 0:
        alertes.append({
            "type":    "RAPPORT",
            "niveau":  "MOYEN",
            "message": (
                f"{detail['nb_rapports_retard']} rapport(s) en brouillon non soumis "
                f"depuis plus de 3 jours."
            ),
        })

    # — Tâches non commencées —
    if detail["nb_taches_non_commencees"] > 0:
        alertes.append({
            "type":    "TACHE",
            "niveau":  "CRITIQUE" if detail["nb_taches_non_commencees"] >= 3 else "ELEVE",
            "message": (
                f"{detail['nb_taches_non_commencees']} tâche(s) non commencée(s) "
                f"alors que leur date de début est dépassée depuis plus de 5 jours."
            ),
        })

    # — Tâches en cours en retard —
    if detail["nb_taches_en_retard"] > 0:
        alertes.append({
            "type":    "TACHE",
            "niveau":  "MOYEN",
            "message": (
                f"{detail['nb_taches_en_retard']} tâche(s) en cours dépassée(s) "
                f"par rapport à la date de fin prévue."
            ),
        })

    # — Score global —
    if score < SEUIL_A_SURVEILLER:
        alertes.append({
            "type":    "RETARD_GLOBAL",
            "niveau":  "CRITIQUE",
            "message": (
                f"Score d'engagement critique ({score}/100). "
                f"Une intervention de l'encadrant est fortement recommandée."
            ),
        })
    elif score < SEUIL_STABLE:
        alertes.append({
            "type":    "RETARD_GLOBAL",
            "niveau":  "ELEVE",
            "message": f"Score d'engagement faible ({score}/100). Surveiller l'évolution.",
        })

    return alertes


# ── Recommandations ──────────────────────────────────────────────────────────

def _generer_recommandations(detail: dict, niveau: str) -> list[str]:
    recs: list[str] = []

    if detail["inactivite_detectee"]:
        recs.append(
            "Contacter l'étudiant par email ou téléphone pour vérifier sa situation."
        )
    if detail["faible_frequence"]:
        recs.append(
            "Rappeler à l'étudiant de se connecter régulièrement à la plateforme "
            "pour enregistrer sa progression."
        )
    if detail["nb_rapports_non_soumis"] > 0:
        recs.append(
            "Relancer l'étudiant pour la rédaction et la soumission des rapports hebdomadaires manquants."
        )
    if detail["nb_rapports_retard"] > 0:
        recs.append(
            "Encourager l'étudiant à soumettre les rapports actuellement en brouillon."
        )
    if detail["taches_non_commencees"]:
        recs.append(
            "Vérifier avec l'encadrant entreprise que les tâches non commencées sont "
            "bien attribuées et que l'étudiant dispose des ressources nécessaires."
        )
    if detail["nb_taches_en_retard"] > 0:
        recs.append(
            "Planifier un point de suivi pour identifier les blocages sur les tâches en retard."
        )

    if niveau == "A_RISQUE":
        recs.append(
            "Organiser une réunion tripartite (étudiant / encadrant académique / "
            "encadrant entreprise) en urgence."
        )
        recs.append(
            "Envisager un plan de rattrapage ou une révision des objectifs du stage."
        )
    elif niveau == "A_SURVEILLER":
        recs.append(
            "Programmer un point de suivi dans les 5 prochains jours ouvrables."
        )

    if not recs:
        recs.append("Continuer le suivi standard — situation satisfaisante.")

    return recs
