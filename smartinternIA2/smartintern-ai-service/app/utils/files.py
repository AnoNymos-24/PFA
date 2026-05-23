"""
Utilitaires partagés — gestion des fichiers, types MIME, formats.
"""

from pathlib import Path

# ── Types MIME acceptés ────────────────────────────────────────────────────

ACCEPTED_TYPES: dict[str, str] = {
    "application/pdf":   "pdf",
    "image/jpeg":        "image",
    "image/jpg":         "image",
    "image/png":         "image",
    "image/webp":        "image",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "docx",
    "application/msword":      "docx",
    "application/octet-stream": "auto",
}

ACCEPTED_EXTENSIONS: set[str] = {
    ".pdf", ".jpg", ".jpeg", ".png", ".webp", ".docx", ".doc"
}


def detecter_format(filename: str, content_type: str) -> str:
    """
    Détermine le format du fichier — extension en priorité, content-type en fallback.
    Retourne : 'pdf' | 'image' | 'docx' | ''
    """
    ext = Path(filename or "").suffix.lower()
    if ext in (".jpg", ".jpeg", ".png", ".webp"):
        return "image"
    if ext == ".pdf":
        return "pdf"
    if ext in (".docx", ".doc"):
        return "docx"
    return ACCEPTED_TYPES.get(content_type, "")


def cleanup_temp_file(chemin: str) -> None:
    """Supprime le fichier temporaire silencieusement."""
    import os
    try:
        if chemin and os.path.exists(chemin):
            os.unlink(chemin)
    except OSError:
        pass
