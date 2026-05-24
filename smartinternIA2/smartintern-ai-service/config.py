"""
Configuration centralisée — chargée depuis les variables d'environnement (.env).
"""

import os
from pathlib import Path
from dotenv import load_dotenv

load_dotenv()

BASE_DIR = Path(__file__).resolve().parent


class Settings:
    # ── Service ────────────────────────────────────────────────────────────
    SERVICE_NAME: str = "SmartIntern AI — Core Service"
    SERVICE_VERSION: str = "2.1.0"
    HOST: str = os.getenv("SERVICE_HOST", "0.0.0.0")
    PORT: int = int(os.getenv("SERVICE_PORT", "8000"))

    # ── Provider IA primaire : NVIDIA NIM ──────────────────────────────────
    # API key : NVIDIA_API_KEY (préfixe nvapi-)
    # Base URL : https://integrate.api.nvidia.com/v1
    NVIDIA_API_KEY: str    = os.getenv("NVIDIA_API_KEY", "")
    NVIDIA_MODEL: str      = os.getenv("NVIDIA_MODEL", "google/gemma-4-31b-it")
    NVIDIA_MAX_TOKENS: int = int(os.getenv("NVIDIA_MAX_TOKENS", "4096"))

    # ── Provider IA fallback : OpenRouter ──────────────────────────────────
    # API key : OPENROUTER_API_KEY (préfixe sk-or-v1-)
    # Base URL : https://openrouter.ai/api/v1
    OPENROUTER_API_KEY: str    = os.getenv("OPENROUTER_API_KEY", "")
    OPENROUTER_MODEL: str      = os.getenv("OPENROUTER_MODEL", "anthropic/claude-sonnet-4-5")
    OPENROUTER_MAX_TOKENS: int = int(os.getenv("OPENROUTER_MAX_TOKENS", "1500"))

    # ── Fichiers & stockage — documents générés ───────────────────────────
    MAX_FILE_SIZE_MB: int = int(os.getenv("MAX_FILE_SIZE_MB", "10"))
    DOCUMENTS_DIR: Path   = BASE_DIR / "uploads" / "documents"

    # ── Stockage des modèles Word (.docx) ─────────────────────────────────
    # Chaque modèle uploadé est stocké dans templates_storage/template_{id}.docx
    # Les métadonnées (champs, zone QR, id_type_document…) sont persistées
    # dans templates_storage/registry.json (auto-incrémenté).
    TEMPLATES_STORAGE_DIR:  Path = BASE_DIR / "templates_storage"
    TEMPLATES_REGISTRY_FILE: Path = BASE_DIR / "templates_storage" / "registry.json"

    # ── Sécurité ───────────────────────────────────────────────────────────
    SIGNATURE_SECRET: str = os.getenv("SIGNATURE_SECRET", "SmartIntern_SecretKey_2024")
    BASE_URL: str         = os.getenv("BASE_URL", "http://localhost:8000")

    # ── CORS ───────────────────────────────────────────────────────────────
    CORS_ORIGINS: list[str] = ["*"]


settings = Settings()

# Création des répertoires au démarrage
for _d in [settings.DOCUMENTS_DIR, settings.TEMPLATES_STORAGE_DIR]:
    _d.mkdir(parents=True, exist_ok=True)
