"""
Singleton du client Anthropic — partagé entre tous les modules.
"""

import os
from functools import lru_cache
import anthropic


@lru_cache(maxsize=1)
def get_ai_client() -> anthropic.Anthropic:
    """Retourne (et met en cache) le client Anthropic."""
    api_key = os.environ.get("ANTHROPIC_API_KEY", "")
    if not api_key:
        raise RuntimeError(
            "ANTHROPIC_API_KEY manquante. "
            "Renseignez-la dans le fichier .env ou dans les variables d'environnement."
        )
    return anthropic.Anthropic(api_key=api_key)
