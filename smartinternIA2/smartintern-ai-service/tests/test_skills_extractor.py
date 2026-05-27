"""
Tests unitaires — skills_extractor.py

Couverture :
  - normaliser_competence : variantes de casse, synonymes, valeur inconnue
  - extraire_competences_depuis_texte : texte libre, bigrams, fuzzy
  - intersection_competences : match, manquantes, bonus
"""

import pytest
from app.modules.matching.skills_extractor import (
    normaliser_competence,
    extraire_competences_depuis_texte,
    intersection_competences,
)


# ══════════════════════════════════════════════════════════════════════════
# normaliser_competence
# ══════════════════════════════════════════════════════════════════════════

class TestNormaliserCompetence:

    def test_synonyme_js_vers_javascript(self):
        """'js' doit être normalisé en 'javascript'."""
        assert normaliser_competence("js") == "javascript"

    def test_majuscules_ignorees(self):
        """La casse ne doit pas impacter la normalisation."""
        assert normaliser_competence("JS")     == "javascript"
        assert normaliser_competence("Js")     == "javascript"
        assert normaliser_competence("jS")     == "javascript"

    def test_spring_vers_spring_boot(self):
        """'spring' seul doit être résolu en 'spring boot'."""
        assert normaliser_competence("spring") == "spring boot"

    def test_k8s_vers_kubernetes(self):
        """Abréviation technique k8s → kubernetes."""
        assert normaliser_competence("k8s") == "kubernetes"

    def test_valeur_inconnue_retournee_telle_quelle(self):
        """Une compétence absente du dico est retournée en lowercase."""
        assert normaliser_competence("QuantumComputing2042") == "quantumcomputing2042"

    def test_chaine_vide(self):
        """Chaîne vide → chaîne vide."""
        assert normaliser_competence("") == ""

    def test_espaces_trimmes(self):
        """Les espaces en début/fin doivent être supprimés."""
        assert normaliser_competence("  js  ") == "javascript"

    def test_synonyme_postgres(self):
        """'postgres' → 'postgresql'."""
        assert normaliser_competence("postgres") == "postgresql"

    def test_synonyme_ml(self):
        """'ml' → 'machine learning'."""
        assert normaliser_competence("ml") == "machine learning"

    def test_synonyme_dotnet(self):
        """'.net' → 'dotnet'."""
        assert normaliser_competence(".net") == "dotnet"


# ══════════════════════════════════════════════════════════════════════════
# extraire_competences_depuis_texte
# ══════════════════════════════════════════════════════════════════════════

class TestExtraireCompetencesDepuisTexte:

    def test_texte_vide(self):
        """Texte vide → set vide."""
        assert extraire_competences_depuis_texte("") == set()

    def test_extraction_simple(self):
        """Compétences explicitement mentionnées doivent être extraites."""
        texte = "Expérience en Java, Spring Boot et MySQL"
        resultats = extraire_competences_depuis_texte(texte)
        assert "java" in resultats
        assert "mysql" in resultats

    def test_synonyme_dans_texte(self):
        """'JS' dans un texte doit être résolu en 'javascript'."""
        resultats = extraire_competences_depuis_texte("Développeur JS confirmé")
        assert "javascript" in resultats

    def test_bigram_spring_boot(self):
        """Le bigram 'spring boot' doit être reconnu comme une seule compétence."""
        resultats = extraire_competences_depuis_texte("Maîtrise de spring boot et docker")
        # 'spring' seul → spring boot, et le bigram 'spring boot' aussi → spring boot
        assert "spring boot" in resultats
        assert "docker" in resultats

    def test_fuzzy_faute_de_frappe_legere(self):
        """Une légère faute de frappe ne doit pas empêcher la reconnaissance."""
        # 'javascrpit' ~ 'javascript' (ratio ≈ 93)
        resultats = extraire_competences_depuis_texte("compétences en javascrpit et pytho")
        # Au moins une des deux doit être trouvée via fuzzy
        assert len(resultats) >= 1

    def test_texte_riche(self):
        """Un texte riche doit produire plusieurs compétences."""
        texte = (
            "Stage en développement fullstack avec React, Node.js, "
            "PostgreSQL et déploiement Docker sur AWS"
        )
        resultats = extraire_competences_depuis_texte(texte)
        assert len(resultats) >= 4


# ══════════════════════════════════════════════════════════════════════════
# intersection_competences
# ══════════════════════════════════════════════════════════════════════════

class TestIntersectionCompetences:

    def test_intersection_simple(self):
        """Cas de base : match partiel."""
        requises  = {"java", "spring boot", "react.js"}
        possedees = {"java", "spring boot", "angular"}
        match, manquantes, bonus = intersection_competences(requises, possedees)
        assert "java" in match
        assert "spring boot" in match
        assert len(manquantes) == 1    # react.js manquant
        assert "angular" in bonus

    def test_couverture_totale(self):
        """Si le candidat a toutes les compétences, manquantes = vide."""
        requises  = {"python", "django"}
        possedees = {"python", "django", "fastapi"}
        match, manquantes, bonus = intersection_competences(requises, possedees)
        assert len(manquantes) == 0
        assert "fastapi" in bonus

    def test_aucune_competence_commune(self):
        """Profil hors-sujet : match = vide."""
        requises  = {"java", "spring boot"}
        possedees = {"php", "laravel"}
        match, manquantes, _ = intersection_competences(requises, possedees)
        assert len(match) == 0
        assert len(manquantes) == 2

    def test_normalisation_automatique(self):
        """Les alias doivent être normalisés avant comparaison."""
        # 'js' → 'javascript', 'react' → 'react.js'
        requises  = {"javascript", "react.js"}
        possedees = {"js", "react"}   # alias
        match, manquantes, _ = intersection_competences(requises, possedees)
        assert len(match) == 2
        assert len(manquantes) == 0

    def test_sets_vides(self):
        """Sets vides → tous vides."""
        match, manquantes, bonus = intersection_competences(set(), set())
        assert match == set()
        assert manquantes == set()
        assert bonus == set()
