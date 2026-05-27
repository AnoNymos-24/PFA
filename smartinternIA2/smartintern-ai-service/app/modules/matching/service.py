"""
Service d'orchestration — Matching & Recommandation IA.

Orchestre les Phases 2 (extraction compétences), 3 (scoring déterministe)
et 4 (enrichissement LLM) pour chaque endpoint.

Pipeline commun :
  extraire_competences → calculer_score_matching → expliquer_matching → trier
"""

import logging

from app.models.matching import (
    MatchingRequest,       MatchingData,       CandidatScore,
    RecommandationRequest, RecommandationData, OffreRecommandee,
    AnalyseProfilRequest,  AnalyseProfilData,
    CandidatInput,         OffreInput,
)

logger = logging.getLogger("smartintern.matching.service")

# ── Import des sous-modules (disponibles après Phase 2-4) ─────────────────
# Ces imports sont différés pour éviter les erreurs circulaires et permettre
# de tester chaque phase indépendamment.
try:
    from app.modules.matching.scorer       import calculer_score_matching, calculer_scores_batch
    from app.modules.matching.llm_explainer import expliquer_matching
    _MODULES_DISPONIBLES = True
except ImportError:
    _MODULES_DISPONIBLES = False
    logger.warning("Modules scorer/llm_explainer non disponibles — mode stub actif")


# ══════════════════════════════════════════════════════════════════════════
# MATCHING CANDIDATS (côté entreprise)
# ══════════════════════════════════════════════════════════════════════════

async def service_matcher_candidats(request: MatchingRequest) -> MatchingData:
    """
    Orchestre le matching candidats pour une offre.

    Pipeline :
      1. Pour chaque candidat : calculer_score_matching (déterministe)
      2. Tri décroissant intermédiaire
      3. Enrichissement LLM pour les top-20 (mode dégradé si LLM indisponible)
      4. Retour de la liste classée complète
    """
    offre    = request.offre
    resultats: list[CandidatScore] = []

    if not _MODULES_DISPONIBLES:
        # Mode stub — retourne des scores fictifs pour la Phase 1
        for candidat in request.candidats:
            from app.models.matching import ScoreMatching, DetailsScore
            score_stub = ScoreMatching(
                score=50.0, niveau="MOYEN",
                forces=["[stub] Module en cours d'installation"],
                faiblesses=["[stub] Scoring non encore disponible"],
                probabilite_selection=47.5,
                details_score=DetailsScore(),
            )
            resultats.append(CandidatScore(
                candidature_id=candidat.candidature_id,
                etudiant_id=candidat.etudiant_id,
                nom_complet=candidat.nom_complet,
                score_matching=score_stub,
            ))
        return MatchingData(
            offre_id=offre.offre_id,
            titre_offre=offre.titre,
            candidats_classes=resultats,
        )

    # ── Scoring déterministe ──────────────────────────────────────────────
    scores_bruts: list[tuple[CandidatInput, object]] = []
    for candidat in request.candidats:
        try:
            score = calculer_score_matching(offre, candidat)
            scores_bruts.append((candidat, score))
        except Exception as exc:
            logger.error(f"Erreur scoring candidat {candidat.etudiant_id}: {exc}")

    # Tri intermédiaire pour prioriser l'enrichissement LLM
    scores_bruts.sort(key=lambda t: t[1].score, reverse=True)

    # ── Enrichissement LLM pour les top-20 ───────────────────────────────
    LLM_LIMIT = 20
    for i, (candidat, score) in enumerate(scores_bruts):
        try:
            if i < LLM_LIMIT:
                score = expliquer_matching(offre, candidat, score)
            resultats.append(CandidatScore(
                candidature_id=candidat.candidature_id,
                etudiant_id=candidat.etudiant_id,
                nom_complet=candidat.nom_complet,
                score_matching=score,
            ))
        except Exception as exc:
            logger.error(f"Erreur enrichissement candidat {candidat.etudiant_id}: {exc}")

    # Tri final (déjà trié, mais on ré-applique pour inclure les éventuelles anomalies)
    resultats.sort(key=lambda r: r.score_matching.score, reverse=True)

    return MatchingData(
        offre_id=offre.offre_id,
        titre_offre=offre.titre,
        candidats_classes=resultats,
    )


# ══════════════════════════════════════════════════════════════════════════
# RECOMMANDATION OFFRES (côté étudiant)
# ══════════════════════════════════════════════════════════════════════════

async def service_recommander_offres(request: RecommandationRequest) -> RecommandationData:
    """
    Orchestre la recommandation d'offres pour un étudiant.

    Pipeline :
      1. Convertir EtudiantInput → CandidatInput (réutilise le même scorer)
      2. Scoring batch (un TF-IDF unique pour toutes les offres — optimisé)
      3. Tri décroissant, top-N
      4. Enrichissement LLM pour les top-5 seulement
    """
    etudiant = request.etudiant

    # Conversion EtudiantInput → CandidatInput pour réutiliser le scorer
    candidat_input = CandidatInput(
        etudiant_id=etudiant.etudiant_id,
        nom_complet=etudiant.nom_complet,
        competences_techniques=etudiant.competences_techniques,
        soft_skills=etudiant.soft_skills,
        experiences_texte=etudiant.experiences_texte,
        formations_texte=etudiant.formations_texte,
        projets=etudiant.projets,
        score_completude_cv=etudiant.score_completude_cv,
        niveau_formation=etudiant.niveau_formation,
    )

    resultats: list[OffreRecommandee] = []

    if not _MODULES_DISPONIBLES:
        # Mode stub
        for offre in request.offres[:request.limit]:
            from app.models.matching import ScoreMatching
            score_stub = ScoreMatching(score=50.0, niveau="MOYEN",
                forces=["[stub]"], faiblesses=["[stub]"], probabilite_selection=47.5)
            resultats.append(OffreRecommandee(
                offre_id=offre.offre_id, titre=offre.titre, domaine=offre.domaine,
                score_matching=score_stub,
            ))
        return RecommandationData(
            etudiant_id=etudiant.etudiant_id, offres_recommandees=resultats
        )

    # ── Scoring batch (TF-IDF calculé une seule fois pour toutes les offres) ──
    try:
        scores_paires = calculer_scores_batch(request.offres, candidat_input)
    except Exception as exc:
        logger.error(f"Erreur scoring batch : {exc}")
        scores_paires = []
        for offre in request.offres:
            try:
                score = calculer_score_matching(offre, candidat_input)
                scores_paires.append((offre, score))
            except Exception as e2:
                logger.error(f"Erreur scoring offre {offre.offre_id}: {e2}")

    # Tri décroissant, top-N
    scores_paires.sort(key=lambda t: t[1].score, reverse=True)
    top_offres = scores_paires[:request.limit]

    # ── Enrichissement LLM pour les top-5 seulement ───────────────────────
    LLM_LIMIT_RECO = 5
    for i, (offre, score) in enumerate(top_offres):
        try:
            if i < LLM_LIMIT_RECO:
                score = expliquer_matching(offre, candidat_input, score)
            resultats.append(OffreRecommandee(
                offre_id=offre.offre_id,
                titre=offre.titre,
                domaine=offre.domaine,
                score_matching=score,
            ))
        except Exception as exc:
            logger.error(f"Erreur enrichissement offre {offre.offre_id}: {exc}")

    return RecommandationData(
        etudiant_id=etudiant.etudiant_id,
        offres_recommandees=resultats,
    )


# ══════════════════════════════════════════════════════════════════════════
# ANALYSE DE PROFIL (transversale)
# ══════════════════════════════════════════════════════════════════════════

async def service_analyser_profil(request: AnalyseProfilRequest) -> AnalyseProfilData:
    """
    Analyse transversale du profil de l'étudiant.

    Implémenté :
      - Liste des compétences présentes (techniques + soft skills)
      - Suggestions génériques

    TODO (Phase 5+) :
      - Appel LLM pour identifier les compétences manquantes par rapport au marché
      - Enrichissement par domaine cible (request.domaine_cible)
    """
    etudiant = request.etudiant

    # Compétences présentes : union sans doublon
    competences_presentes = sorted(set(
        etudiant.competences_techniques + etudiant.soft_skills
    ))

    suggestions = [
        "Enrichissez votre profil avec des certifications reconnues dans votre domaine.",
        "Valorisez vos projets personnels avec des liens GitHub ou des descriptions détaillées.",
        "Ajoutez des expériences concrètes (stages, projets associatifs, freelance).",
    ]
    if request.domaine_cible:
        suggestions.insert(0,
            f"Pour le domaine « {request.domaine_cible} », "
            "ciblez des projets ou certifications spécialisées."
        )

    # TODO : appel LLM pour compétences_manquantes par rapport au marché
    competences_manquantes: list[str] = []

    return AnalyseProfilData(
        competences_presentes=competences_presentes,
        competences_manquantes=competences_manquantes,
        domaines_forts=[request.domaine_cible] if request.domaine_cible else [],
        suggestions=suggestions,
    )
