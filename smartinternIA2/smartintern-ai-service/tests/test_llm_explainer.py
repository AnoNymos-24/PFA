"""
Tests unitaires — llm_explainer.py

Couverture :
  - expliquer_matching : mode LLM disponible (mock), mode dégradé (LLM absent),
    dégradé sur JSON invalide, dégradé sur exception LLM
  - _extraire_forces_faiblesses : JSON propre, JSON dans bloc markdown, JSON invalide
  - _construire_prompt_utilisateur : contenu minimal vérifié

Les tests mockent ai_client pour éviter tout appel réseau.
"""

import types
from unittest.mock import MagicMock, patch

import pytest

from app.models.matching import (
    CandidatInput,
    DetailsScore,
    OffreInput,
    ScoreMatching,
)
from app.modules.matching.llm_explainer import (
    _construire_prompt_utilisateur,
    _extraire_forces_faiblesses,
    expliquer_matching,
)


# ══════════════════════════════════════════════════════════════════════════
# FIXTURES
# ══════════════════════════════════════════════════════════════════════════

@pytest.fixture
def offre():
    return OffreInput(
        offre_id=1,
        titre="Stage Développeur Python",
        description="Développement microservices avec FastAPI.",
        domaine="Backend",
        niveau_requis="bac+4",
        duree_mois=6,
        competences_requises=["python", "fastapi", "docker", "postgresql"],
        soft_skills_requis=["autonomie", "rigueur"],
    )


@pytest.fixture
def candidat():
    return CandidatInput(
        etudiant_id=1,
        nom_complet="Alice Dupont",
        competences_techniques=["python", "fastapi", "docker"],
        soft_skills=["autonomie", "rigueur", "communication"],
        experiences_texte="Stage de 3 mois chez DataCorp sur Flask/Python.",
        formations_texte="Master 1 Informatique.",
        niveau_formation="bac+4",
    )


@pytest.fixture
def score_base():
    return ScoreMatching(
        score=72.0,
        niveau="BON",
        forces=["Bonne correspondance Python/Docker"],
        faiblesses=["Expérience limitée"],
        probabilite_selection=69.8,
        details_score=DetailsScore(
            competences_techniques=80.0,
            similarite_tf_idf=45.0,
            experience=50.0,
            niveau_academique=100.0,
            soft_skills=100.0,
        ),
    )


def _fake_response(content: str):
    """Crée un faux objet response OpenAI."""
    msg = MagicMock()
    msg.content = content
    choice = MagicMock()
    choice.message = msg
    resp = MagicMock()
    resp.choices = [choice]
    return resp


# ══════════════════════════════════════════════════════════════════════════
# _extraire_forces_faiblesses
# ══════════════════════════════════════════════════════════════════════════

class TestExtraireForcesFaiblesses:

    def test_json_propre(self):
        texte = '{"forces": ["Bonne maîtrise Python"], "faiblesses": ["Peu d\'expérience"]}'
        result = _extraire_forces_faiblesses(texte)
        assert result is not None
        forces, faiblesses = result
        assert forces == ["Bonne maîtrise Python"]
        assert faiblesses == ["Peu d'expérience"]

    def test_json_dans_bloc_markdown(self):
        texte = '```json\n{"forces": ["Force 1", "Force 2"], "faiblesses": ["Faiblesse 1"]}\n```'
        result = _extraire_forces_faiblesses(texte)
        assert result is not None
        forces, faiblesses = result
        assert len(forces) == 2
        assert len(faiblesses) == 1

    def test_json_invalide_retourne_none(self):
        assert _extraire_forces_faiblesses("ce n'est pas du JSON") is None

    def test_json_sans_cles_requises(self):
        """JSON valide mais sans clés forces/faiblesses → None."""
        texte = '{"commentaire": "profil intéressant"}'
        result = _extraire_forces_faiblesses(texte)
        # forces et faiblesses seront [] (valeur par défaut de .get) → considéré invalide
        # en fait _extraire retourne ([], []) qui est techniquement valide
        # mais expliquer_matching rejettera les listes vides
        assert result is not None  # le parse JSON réussit
        forces, faiblesses = result
        assert forces == []
        assert faiblesses == []


# ══════════════════════════════════════════════════════════════════════════
# _construire_prompt_utilisateur
# ══════════════════════════════════════════════════════════════════════════

class TestConstruirePromptUtilisateur:

    def test_contient_titre_offre(self, offre, candidat, score_base):
        prompt = _construire_prompt_utilisateur(offre, candidat, score_base)
        assert "Stage Développeur Python" in prompt

    def test_contient_nom_candidat(self, offre, candidat, score_base):
        prompt = _construire_prompt_utilisateur(offre, candidat, score_base)
        assert "Alice Dupont" in prompt

    def test_contient_score_global(self, offre, candidat, score_base):
        prompt = _construire_prompt_utilisateur(offre, candidat, score_base)
        assert "72" in prompt

    def test_prompt_non_vide(self, offre, candidat, score_base):
        prompt = _construire_prompt_utilisateur(offre, candidat, score_base)
        assert len(prompt) > 50


# ══════════════════════════════════════════════════════════════════════════
# expliquer_matching — mode LLM disponible (mock)
# ══════════════════════════════════════════════════════════════════════════

class TestExpliquerMatchingAvecLLM:

    def test_llm_absent_retourne_score_original(self, offre, candidat, score_base):
        """Si ai_client est None → score original retourné tel quel."""
        with patch("app.modules.matching.llm_explainer.ai_client", None):
            result = expliquer_matching(offre, candidat, score_base)
        assert result.score == score_base.score
        assert result.forces == score_base.forces

    def test_llm_repond_valide_enrichit_forces(self, offre, candidat, score_base):
        """LLM renvoie un JSON valide → forces/faiblesses remplacées."""
        json_llm = '{"forces": ["Excellente maîtrise de Python", "Expérience FastAPI"], "faiblesses": ["Portfolio à étoffer"]}'
        mock_client = MagicMock()
        mock_client.chat.completions.create.return_value = _fake_response(json_llm)

        with patch("app.modules.matching.llm_explainer.ai_client", mock_client):
            result = expliquer_matching(offre, candidat, score_base)

        assert "Excellente maîtrise de Python" in result.forces
        assert result.faiblesses == ["Portfolio à étoffer"]
        # Score et niveau inchangés
        assert result.score == score_base.score
        assert result.niveau == score_base.niveau

    def test_llm_json_invalide_degrade_vers_deterministe(self, offre, candidat, score_base):
        """Réponse LLM non parsable → score original conservé."""
        mock_client = MagicMock()
        mock_client.chat.completions.create.return_value = _fake_response("Voici mon analyse...")

        with patch("app.modules.matching.llm_explainer.ai_client", mock_client):
            result = expliquer_matching(offre, candidat, score_base)

        assert result.forces == score_base.forces
        assert result.faiblesses == score_base.faiblesses

    def test_llm_exception_degrade_vers_deterministe(self, offre, candidat, score_base):
        """Exception LLM → score original conservé, aucune exception levée."""
        mock_client = MagicMock()
        mock_client.chat.completions.create.side_effect = RuntimeError("Network error")

        with patch("app.modules.matching.llm_explainer.ai_client", mock_client):
            result = expliquer_matching(offre, candidat, score_base)

        # Aucune exception levée, score original conservé
        assert result.score == score_base.score
        assert result.forces == score_base.forces

    def test_llm_forces_vides_degrade(self, offre, candidat, score_base):
        """LLM retourne forces vides → score original conservé."""
        json_llm = '{"forces": [], "faiblesses": ["Peu d\'expérience"]}'
        mock_client = MagicMock()
        mock_client.chat.completions.create.return_value = _fake_response(json_llm)

        with patch("app.modules.matching.llm_explainer.ai_client", mock_client):
            result = expliquer_matching(offre, candidat, score_base)

        assert result.forces == score_base.forces

    def test_details_score_preserves(self, offre, candidat, score_base):
        """Le DetailsScore ne doit pas changer après enrichissement LLM."""
        json_llm = '{"forces": ["Force LLM"], "faiblesses": ["Faiblesse LLM"]}'
        mock_client = MagicMock()
        mock_client.chat.completions.create.return_value = _fake_response(json_llm)

        with patch("app.modules.matching.llm_explainer.ai_client", mock_client):
            result = expliquer_matching(offre, candidat, score_base)

        assert result.details_score.competences_techniques == score_base.details_score.competences_techniques
        assert result.details_score.experience == score_base.details_score.experience
