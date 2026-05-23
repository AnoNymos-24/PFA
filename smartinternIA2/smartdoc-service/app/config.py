"""
Configuration centralisée — chargée depuis les variables d'environnement (.env).
"""

import os
from pathlib import Path
from dotenv import load_dotenv

load_dotenv()

BASE_DIR = Path(__file__).resolve().parent.parent


class Settings:
    # ── Service ────────────────────────────────────────────────────────────
    SERVICE_NAME: str = "SmartIntern AI — Document Service"
    SERVICE_VERSION: str = "2.0.0"
    HOST: str = os.getenv("SERVICE_HOST", "0.0.0.0")
    PORT: int = int(os.getenv("SERVICE_PORT", "8000"))

    # ── Anthropic ──────────────────────────────────────────────────────────
    ANTHROPIC_API_KEY: str = os.getenv("ANTHROPIC_API_KEY", "")
    ANTHROPIC_MODEL: str = os.getenv("ANTHROPIC_MODEL", "claude-sonnet-4-20250514")

    # ── Fichiers ───────────────────────────────────────────────────────────
    MAX_FILE_SIZE_MB: int = int(os.getenv("MAX_FILE_SIZE_MB", "10"))
    OUTPUT_DIR: Path = BASE_DIR / os.getenv("OUTPUT_DIR", "outputs")

    # Dossier de stockage des fichiers .docx uploadés (templates)
    TEMPLATES_STORAGE_DIR: Path = BASE_DIR / "templates_storage"

    # Fichier JSON de persistance du registre des modèles
    TEMPLATES_REGISTRY_FILE: Path = BASE_DIR / "templates_storage" / "registry.json"

    # ── CORS ───────────────────────────────────────────────────────────────
    CORS_ORIGINS: list[str] = ["*"]


settings = Settings()
