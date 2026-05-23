"""
Utilitaires partagés pour la gestion des fichiers (upload, temp, nettoyage).
"""

import os
import tempfile
from pathlib import Path

# ── Types MIME acceptés ────────────────────────────────────────────────────
ACCEPTED_TYPES: dict[str, str] = {
    "application/pdf":   "pdf",
    "image/jpeg":        "image",
    "image/jpg":         "image",
    "image/png":         "image",
    "image/webp":        "image",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "docx",
    "application/msword": "docx",
    "application/octet-stream": "auto",
}

ACCEPTED_EXTENSIONS: set[str] = {
    ".pdf", ".jpg", ".jpeg", ".png", ".webp", ".docx", ".doc"
}


def detecter_format(filename: str, content_type: str) -> str:
    """
    Détermine le format du fichier depuis l'extension en priorité,
    puis depuis le content-type en fallback.
    """
    ext = Path(filename or "").suffix.lower()
    if ext in {".jpg", ".jpeg", ".png", ".webp"}:
        return "image"
    if ext == ".pdf":
        return "pdf"
    if ext in {".docx", ".doc"}:
        return "docx"
    return ACCEPTED_TYPES.get(content_type, "")


def save_temp_file(content: bytes, suffix: str) -> str:
    """Écrit `content` dans un fichier temporaire et retourne son chemin."""
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        tmp.write(content)
        return tmp.name


def cleanup_temp_file(chemin: str) -> None:
    """Supprime le fichier temporaire s'il existe, silencieusement."""
    try:
        if chemin and os.path.exists(chemin):
            os.unlink(chemin)
    except OSError:
        pass


def ensure_dir(path: Path) -> Path:
    """Crée le dossier s'il n'existe pas et le retourne."""
    path.mkdir(parents=True, exist_ok=True)
    return path
