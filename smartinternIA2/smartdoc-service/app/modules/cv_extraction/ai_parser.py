"""
Analyse IA du texte extrait d'un CV — structuration JSON via Claude.
"""

import json
import logging
from fastapi import HTTPException

from app.models.cv import CvStandardise
from app.utils.ai_client import get_ai_client
from app.config import settings

logger = logging.getLogger("smartdoc.cv.ai_parser")

# ── Prompt système ─────────────────────────────────────────────────────────
SYSTEM_PROMPT = """Tu es un expert en analyse de CVs multilingues (français, anglais, arabe).
Ton rôle est d'extraire et structurer les informations d'un CV en JSON standardisé.

RÈGLES :
1. Le texte peut être désordonné (OCR, multi-colonnes) — raisonne sur le sens, pas sur la mise en page
2. Distingue bien : formations académiques VS certifications/formations courtes
3. Compétences TECHNIQUES (langages, frameworks, logiciels) ≠ SOFT SKILLS ≠ OUTILS
4. Ne jamais inventer une information absente → mettre null
5. Dates : standardise en format lisible ("Janvier 2020 - Mars 2023", "2019 - 2021", etc.)
6. Si un résumé/objectif est absent mais déductible, synthétise en 2-3 phrases
7. Réponds UNIQUEMENT avec un JSON valide, sans markdown ni explication

Structure attendue :
{
  "profil": {
    "nom": null, "prenom": null, "email": null, "telephone": null,
    "adresse": null, "nationalite": null, "date_naissance": null,
    "titre_professionnel": null, "resume": null
  },
  "experiences": [
    {"poste": "", "entreprise": "", "periode": null,
     "date_debut": null, "date_fin": null, "description": null, "lieu": null}
  ],
  "formations": [
    {"diplome": "", "etablissement": "", "periode": null,
     "date_debut": null, "date_fin": null, "specialite": null, "lieu": null}
  ],
  "competences": {
    "techniques": [], "soft_skills": [], "outils": [], "autres": []
  },
  "langues": [{"langue": "", "niveau": null}],
  "certifications": [],
  "projets": [],
  "interets": []
}"""


def analyser_cv_avec_ia(texte_brut: str) -> CvStandardise:
    """
    Envoie le texte extrait d'un CV à Claude et retourne
    un objet CvStandardise structuré.
    """
    try:
        client = get_ai_client()
        response = client.messages.create(
            model=settings.ANTHROPIC_MODEL,
            max_tokens=4000,
            system=SYSTEM_PROMPT,
            messages=[{
                "role": "user",
                "content": (
                    "Voici le texte extrait d'un CV (peut être désordonné) :\n\n"
                    f"{texte_brut}\n\n"
                    "Structure ce CV en JSON."
                ),
            }],
        )

        texte_reponse = response.content[0].text.strip()

        # Nettoyer les éventuelles balises Markdown ```json ... ```
        if "```" in texte_reponse:
            texte_reponse = texte_reponse.split("```")[1]
            if texte_reponse.startswith("json"):
                texte_reponse = texte_reponse[4:]

        data = json.loads(texte_reponse.strip())
        return CvStandardise(**data)

    except json.JSONDecodeError as exc:
        logger.error(f"Erreur parsing JSON Claude : {exc}")
        raise HTTPException(status_code=500, detail="Erreur de structuration des données CV.")
    except Exception as exc:
        logger.error(f"Claude API erreur : {exc}")
        raise HTTPException(status_code=500, detail=f"Erreur analyse IA : {exc}")
