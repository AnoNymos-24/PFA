"""
Générateur PDF — conversion DOCX → PDF via LibreOffice CLI.
Si LibreOffice n'est pas disponible, retourne le DOCX et lève une erreur explicite.
"""

import logging
import subprocess
import shutil
from pathlib import Path

logger = logging.getLogger("smartdoc.docgen.pdf")

LIBREOFFICE_CANDIDATES = [
    "soffice",                                        # Linux / PATH
    r"C:\Program Files\LibreOffice\program\soffice.exe",   # Windows standard
    r"C:\Program Files (x86)\LibreOffice\program\soffice.exe",
    "/usr/bin/libreoffice",                           # Linux
    "/Applications/LibreOffice.app/Contents/MacOS/soffice",  # macOS
]


def _trouver_libreoffice() -> str | None:
    """Retourne le chemin de l'exécutable LibreOffice, ou None s'il est absent."""
    for candidat in LIBREOFFICE_CANDIDATES:
        if shutil.which(candidat) or Path(candidat).exists():
            return candidat
    return None


def convertir_docx_en_pdf(chemin_docx: Path, dossier_sortie: Path) -> Path:
    """
    Convertit un fichier DOCX en PDF via LibreOffice headless.

    Args:
        chemin_docx:    Chemin du fichier DOCX source.
        dossier_sortie: Dossier de destination du PDF généré.

    Returns:
        Chemin du fichier PDF créé.

    Raises:
        RuntimeError: Si LibreOffice n'est pas installé ou si la conversion échoue.
    """
    soffice = _trouver_libreoffice()
    if not soffice:
        raise RuntimeError(
            "LibreOffice n'est pas installé sur ce serveur. "
            "Installez-le (https://www.libreoffice.org) pour activer la génération PDF, "
            "ou téléchargez le document au format DOCX."
        )

    dossier_sortie.mkdir(parents=True, exist_ok=True)

    cmd = [
        soffice,
        "--headless",
        "--convert-to", "pdf",
        "--outdir", str(dossier_sortie),
        str(chemin_docx),
    ]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if result.returncode != 0:
            logger.error(f"LibreOffice stderr: {result.stderr}")
            raise RuntimeError(f"Échec de la conversion PDF : {result.stderr}")
    except subprocess.TimeoutExpired:
        raise RuntimeError("Délai dépassé lors de la conversion PDF (>60s).")

    chemin_pdf = dossier_sortie / (chemin_docx.stem + ".pdf")
    if not chemin_pdf.exists():
        raise RuntimeError(f"PDF non trouvé après conversion : {chemin_pdf}")

    logger.info(f"PDF généré : {chemin_pdf}")
    return chemin_pdf


def pdf_disponible() -> bool:
    """Retourne True si LibreOffice est disponible (génération PDF possible)."""
    return _trouver_libreoffice() is not None
