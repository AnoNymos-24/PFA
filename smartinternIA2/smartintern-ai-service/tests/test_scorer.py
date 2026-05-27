"""
Tests unitaires — scorer.py

Couverture :
  - calculer_score_matching : candidat parfait / partiel / hors-sujet
  - calculer_scores_batch   : ordre des résultats, nombre de résultats
  - Composantes individuelles : niveau académique, expérience, soft skills
"""

import pytest
from app.models.matching import CandidatInput, OffreInput
from app.modules.matching.scorer import (
    _niveau_vers_rang,
    _score_experience,
    _score_niveau_academique,
    _score_soft_skills,
    calculer_score_matching,
    calculer_scores_batch,
)


# ══════════════════════════════════════════════════════════════════════════
# FIXTURES — offre de référence
# ══════════════════════════════════════════════════════════════════════════

@pytest.fixture
def offre_java():
    return OffreInput(
        offre_id=1,
        titre="Stage Développeur Java Spring Boot",
        description="Rejoignez notre équipe pour développer des microservices Java.",
        domaine="Développement logiciel",
        type_stage="stage",
        niveau_requis="bac+4",
        duree_mois=6,
        competences_requises=["java", "spring boot", "rest api", "docker", "postgresql"],
        soft_skills_requis=["travail en équipe", "autonomie", "communication"],
    )


@pytest.fixture
def candidat_parfait():
    """Candidat qui possède toutes les compétences et le bon niveau."""
    return CandidatInput(
        etudiant_id=10,
        nom_complet="Alice Martin",
        competences_techniques=["java", "spring boot", "rest api", "docker", "postgresql", "git"],
        soft_skills=["travail en équipe", "autonomie", "communication", "rigueur"],
        experiences_texte="Stage de 6 mois chez TechCorp sur du développement Java Spring Boot.",
        formations_texte="Master 1 Génie Logiciel — Université Paris.",
        projets=["API REST Java Spring Boot avec PostgreSQL et Docker"],
        score_completude_cv=0.9,
        niveau_formation="bac+4",
    )


@pytest.fixture
def candidat_partiel():
    """Candidat avec 3/5 compétences, niveau légèrement inférieur."""
    return CandidatInput(
        etudiant_id=20,
        nom_complet="Bob Dupont",
        competences_techniques=["java", "spring boot", "postgresql"],
        soft_skills=["autonomie", "communication"],
        experiences_texte="Projet académique de 3 mois utilisant Java.",
        formations_texte="Licence Informatique.",
        projets=["Application web Java"],
        score_completude_cv=0.6,
        niveau_formation="bac+3",
    )


@pytest.fixture
def candidat_hors_sujet():
    """Candidat qui ne correspond pas du tout au poste Java."""
    return CandidatInput(
        etudiant_id=30,
        nom_complet="Charlie Rousseau",
        competences_techniques=["php", "laravel", "mysql"],
        soft_skills=["créativité"],
        experiences_texte="Stage webdesign PHP/Laravel.",
        formations_texte="BTS Multimédia.",
        projets=["Site web PHP"],
        score_completude_cv=0.4,
        niveau_formation="bac+2",
    )


# ══════════════════════════════════════════════════════════════════════════
# calculer_score_matching
# ══════════════════════════════════════════════════════════════════════════

class TestCalculerScoreMatching:

    def test_candidat_parfait_score_eleve(self, offre_java, candidat_parfait):
        """Candidat parfait : score ≥ 75."""
        result = calculer_score_matching(offre_java, candidat_parfait)
        assert result.score >= 75, f"Score attendu ≥ 75, obtenu {result.score}"

    def test_candidat_parfait_niveau_bon_ou_excellent(self, offre_java, candidat_parfait):
        """Candidat parfait : niveau BON ou EXCELLENT."""
        result = calculer_score_matching(offre_java, candidat_parfait)
        assert result.niveau in ("BON", "EXCELLENT")

    def test_candidat_hors_sujet_score_faible(self, offre_java, candidat_hors_sujet):
        """Candidat hors-sujet : score < 50."""
        result = calculer_score_matching(offre_java, candidat_hors_sujet)
        assert result.score < 50, f"Score attendu < 50, obtenu {result.score}"

    def test_candidat_hors_sujet_niveau_faible_ou_moyen(self, offre_java, candidat_hors_sujet):
        """Candidat hors-sujet : niveau FAIBLE ou MOYEN."""
        result = calculer_score_matching(offre_java, candidat_hors_sujet)
        assert result.niveau in ("FAIBLE", "MOYEN")

    def test_ordre_parfait_gt_partiel_gt_horsujet(
        self, offre_java, candidat_parfait, candidat_partiel, candidat_hors_sujet
    ):
        """L'ordre de scoring doit être : parfait > partiel > hors-sujet."""
        s_parfait = calculer_score_matching(offre_java, candidat_parfait).score
        s_partiel = calculer_score_matching(offre_java, candidat_partiel).score
        s_horsujet = calculer_score_matching(offre_java, candidat_hors_sujet).score
        assert s_parfait > s_partiel, f"{s_parfait} > {s_partiel} attendu"
        assert s_partiel > s_horsujet, f"{s_partiel} > {s_horsujet} attendu"

    def test_ecart_parfait_horsujet_superieur_20pts(
        self, offre_java, candidat_parfait, candidat_hors_sujet
    ):
        """L'écart entre parfait et hors-sujet doit être > 20 points."""
        s_parfait  = calculer_score_matching(offre_java, candidat_parfait).score
        s_horsujet = calculer_score_matching(offre_java, candidat_hors_sujet).score
        assert s_parfait - s_horsujet > 20, (
            f"Écart {s_parfait - s_horsujet:.1f} trop faible (attendu > 20)"
        )

    def test_forces_et_faiblesses_presentes(self, offre_java, candidat_partiel):
        """Le résultat doit toujours contenir des forces ET des faiblesses."""
        result = calculer_score_matching(offre_java, candidat_partiel)
        assert len(result.forces) >= 1
        assert len(result.faiblesses) >= 1

    def test_probabilite_selection_entre_0_et_95(self, offre_java, candidat_parfait):
        """La probabilité de sélection doit être comprise entre 0 et 95."""
        result = calculer_score_matching(offre_java, candidat_parfait)
        assert 0 <= result.probabilite_selection <= 95

    def test_details_score_renseignes(self, offre_java, candidat_parfait):
        """Toutes les sous-composantes du DetailsScore doivent être renseignées."""
        result = calculer_score_matching(offre_java, candidat_parfait)
        d = result.details_score
        assert d.competences_techniques >= 0
        assert d.similarite_tf_idf >= 0
        assert d.experience >= 0
        assert d.niveau_academique >= 0
        assert d.soft_skills >= 0

    def test_offre_sans_competences_requises(self, candidat_parfait):
        """Offre sans compétences requises → score calculable sans erreur."""
        offre_vide = OffreInput(
            offre_id=99,
            titre="Stage libre",
            description="Offre généraliste",
            domaine="Divers",
        )
        result = calculer_score_matching(offre_vide, candidat_parfait)
        assert 0.0 <= result.score <= 100.0


# ══════════════════════════════════════════════════════════════════════════
# calculer_scores_batch
# ══════════════════════════════════════════════════════════════════════════

class TestCalculerScoresBatch:

    def test_batch_retourne_autant_de_resultats_que_offres(
        self, offre_java, candidat_parfait
    ):
        """Le batch retourne un résultat par offre fournie."""
        offres = [offre_java] * 3
        resultats = calculer_scores_batch(offres, candidat_parfait)
        assert len(resultats) == 3

    def test_batch_vide(self, candidat_parfait):
        """Batch sur liste vide → liste vide."""
        assert calculer_scores_batch([], candidat_parfait) == []

    def test_batch_ordre_decroissant(
        self, offre_java, candidat_parfait, candidat_hors_sujet
    ):
        """Le batch trie par score décroissant."""
        offre_hors_sujet = OffreInput(
            offre_id=2,
            titre="Stage Marketing Digital",
            description="SEO, réseaux sociaux, contenus",
            domaine="Marketing",
            competences_requises=["seo", "google analytics", "réseaux sociaux"],
        )
        resultats = calculer_scores_batch([offre_java, offre_hors_sujet], candidat_parfait)
        scores = [r[1].score for r in resultats]
        assert scores == sorted(scores, reverse=True), f"Scores non triés : {scores}"

    def test_batch_vs_individuel_meme_score_approx(
        self, offre_java, candidat_partiel
    ):
        """
        Le score batch et le score individuel doivent être proches (± 5 pts).
        La différence vient du TF-IDF qui change de corpus.
        """
        score_batch = calculer_scores_batch([offre_java], candidat_partiel)[0][1].score
        score_indiv = calculer_score_matching(offre_java, candidat_partiel).score
        assert abs(score_batch - score_indiv) <= 5, (
            f"Divergence batch/indiv trop grande : {score_batch} vs {score_indiv}"
        )


# ══════════════════════════════════════════════════════════════════════════
# Composantes individuelles
# ══════════════════════════════════════════════════════════════════════════

class TestComposantesIndividuelles:

    def test_niveau_vers_rang_connu(self):
        """Les niveaux connus retournent le bon rang."""
        assert _niveau_vers_rang("bac+3") == 4
        assert _niveau_vers_rang("Master") == 6
        assert _niveau_vers_rang("bts") == 3

    def test_niveau_vers_rang_inconnu(self):
        """Un niveau inconnu retourne 0."""
        assert _niveau_vers_rang("xyz inconnu") == 0

    def test_score_niveau_superieur(self, offre_java):
        """Candidat au-dessus du niveau requis → score 100."""
        candidat_sup = CandidatInput(
            etudiant_id=1, nom_complet="Test",
            niveau_formation="bac+5",
        )
        assert _score_niveau_academique(offre_java, candidat_sup) == 100.0

    def test_score_niveau_inferieur_de_2(self, offre_java):
        """Candidat 2 niveaux en dessous → score 40."""
        candidat_inf = CandidatInput(
            etudiant_id=1, nom_complet="Test",
            niveau_formation="bac+2",
        )
        assert _score_niveau_academique(offre_java, candidat_inf) == 40.0

    def test_score_experience_sans_mention(self, offre_java):
        """Candidat sans expérience mentionnée → score 0 (aucune durée extraite)."""
        candidat_sans_exp = CandidatInput(
            etudiant_id=1, nom_complet="Test",
            experiences_texte="",
        )
        score = _score_experience(offre_java, candidat_sans_exp)
        assert score == 0.0

    def test_score_experience_suffisante(self, offre_java):
        """Candidat avec 6 mois d'expérience pour un stage de 6 mois → score 100."""
        candidat_exp = CandidatInput(
            etudiant_id=1, nom_complet="Test",
            experiences_texte="Stage de 6 mois en développement logiciel.",
        )
        score = _score_experience(offre_java, candidat_exp)
        assert score == 100.0

    def test_score_soft_skills_complet(self, offre_java):
        """Candidat qui possède tous les soft skills requis → score 100."""
        candidat_soft = CandidatInput(
            etudiant_id=1, nom_complet="Test",
            soft_skills=["travail en équipe", "autonomie", "communication"],
        )
        score = _score_soft_skills(offre_java, candidat_soft)
        assert score == 100.0

    def test_score_soft_skills_vide(self, offre_java):
        """Candidat sans soft skills → score 0."""
        candidat_no_soft = CandidatInput(
            etudiant_id=1, nom_complet="Test",
            soft_skills=[],
        )
        score = _score_soft_skills(offre_java, candidat_no_soft)
        assert score == 0.0
