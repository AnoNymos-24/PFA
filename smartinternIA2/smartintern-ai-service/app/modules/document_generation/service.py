"""
Service de génération de documents PDF.

Fonctionnalités :
  - Génération PDF depuis un script Python (produit par l'analyse IA)
  - En-tête / pied de page personnalisés
  - QR code de vérification avec signature HMAC
  - Bande de sécurité + métadonnées PDF embarquées
  - Vérification d'authenticité (depuis scan QR)
  - Régénération (versioning)
"""

import hashlib
import hmac
import logging
import shutil
import uuid
from datetime import datetime, timedelta
from pathlib import Path
from typing import Optional

import fitz
from PIL import Image
from reportlab.lib.colors import HexColor
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm, mm
from reportlab.platypus import (
    HRFlowable, Paragraph, SimpleDocTemplate, Spacer,
    Image as RLImage,
)

from app.utils.qr import generer_qr_code
from config import settings

logger = logging.getLogger("smartintern.document_generation.service")


# ══════════════════════════════════════════════════════════════════════════
# UTILITAIRES SÉCURITÉ
# ══════════════════════════════════════════════════════════════════════════

def generer_uuid() -> str:
    return str(uuid.uuid4())


def generer_signature(doc_uuid: str, date_generation: str, date_expiration: str) -> str:
    """Signature HMAC-SHA256 pour l'authenticité du document."""
    message = f"{doc_uuid}:{date_generation}:{date_expiration}"
    return hmac.new(
        settings.SIGNATURE_SECRET.encode(),
        message.encode(),
        hashlib.sha256,
    ).hexdigest()


def verifier_signature(
    doc_uuid: str, date_generation: str, date_expiration: str, signature: str
) -> bool:
    """Vérifie l'authenticité d'un document par sa signature HMAC."""
    expected = generer_signature(doc_uuid, date_generation, date_expiration)
    return hmac.compare_digest(expected, signature)


def est_expire(date_expiration_str: str) -> bool:
    """Retourne True si le document a dépassé sa date d'expiration."""
    try:
        exp = datetime.fromisoformat(date_expiration_str)
        return datetime.now() > exp
    except Exception:
        return False


# ══════════════════════════════════════════════════════════════════════════
# MOTEUR DE GÉNÉRATION PDF
# ══════════════════════════════════════════════════════════════════════════

class MoteurGenerationPDF:
    """
    Moteur principal de génération PDF.

    Responsabilités :
      1. Exécution du script Python généré par IA (corps du document)
      2. Fallback PDF basique si le script échoue
      3. Ajout du QR code, de la bande de sécurité et des métadonnées PDF
    """

    PAGE_WIDTH, PAGE_HEIGHT = A4

    def __init__(
        self,
        chemin_header: Optional[str] = None,
        chemin_footer: Optional[str] = None,
    ):
        self.chemin_header = chemin_header
        self.chemin_footer = chemin_footer

    def generer_depuis_script(
        self,
        script_python: str,
        donnees: dict,
        chemin_sortie: str,
        chemin_qr: Optional[str] = None,
        doc_uuid: str = "",
        date_expiration: str = "",
        type_document: str = "",
        nom_etablissement: str = "",
    ) -> str:
        """
        Exécute le script IA puis finalise le PDF (QR, métadonnées, sécurité).
        """
        chemin_tmp = str(settings.DOCUMENTS_DIR / f"tmp_{doc_uuid}.pdf")

        # 1. Exécuter le script généré par IA
        try:
            namespace: dict = {}
            exec(compile(script_python, "<generated_script>", "exec"), namespace)
            if "generer_document" in namespace:
                namespace["generer_document"](
                    donnees=donnees,
                    chemin_sortie=chemin_tmp,
                    chemin_header=self.chemin_header,
                    chemin_footer=self.chemin_footer,
                )
                logger.info(f"Script IA exécuté → {chemin_tmp}")
            else:
                logger.warning("Script sans fonction generer_document — fallback basique")
                self._generer_pdf_basique(donnees, chemin_tmp, type_document)
        except Exception as e:
            logger.error(f"Erreur exécution script IA : {e}")
            self._generer_pdf_basique(donnees, chemin_tmp, type_document)

        # 2. Finalisation (QR + métadonnées + sécurité)
        chemin_final = self._finaliser_pdf(
            chemin_tmp, chemin_sortie, chemin_qr,
            doc_uuid, date_expiration, type_document, nom_etablissement,
        )

        if Path(chemin_tmp).exists() and chemin_tmp != chemin_final:
            Path(chemin_tmp).unlink()

        return chemin_final

    def _generer_pdf_basique(
        self, donnees: dict, chemin_sortie: str, type_document: str = "Document"
    ):
        """PDF de fallback lisible si le script IA échoue."""
        doc = SimpleDocTemplate(
            chemin_sortie, pagesize=A4,
            rightMargin=2.5*cm, leftMargin=2.5*cm,
            topMargin=3*cm, bottomMargin=3*cm,
        )
        styles   = getSampleStyleSheet()
        elements = []

        if self.chemin_header and Path(self.chemin_header).exists():
            elements.append(RLImage(self.chemin_header, width=16*cm, height=3*cm))
            elements.append(Spacer(1, 0.5*cm))

        titre_style = ParagraphStyle(
            "Titre", parent=styles["Title"],
            fontSize=16, spaceAfter=20,
            textColor=HexColor("#1a1a2e"), alignment=TA_CENTER,
        )
        elements.append(Paragraph(type_document.upper(), titre_style))
        elements.append(HRFlowable(width="100%", thickness=2, color=HexColor("#1a1a2e")))
        elements.append(Spacer(1, 0.5*cm))

        corps_style = ParagraphStyle(
            "Corps", parent=styles["Normal"],
            fontSize=11, leading=16, spaceAfter=8, alignment=TA_JUSTIFY,
        )
        label_style = ParagraphStyle(
            "Label", parent=styles["Normal"],
            fontSize=10, textColor=HexColor("#666666"), spaceAfter=2,
        )

        for cle, valeur in donnees.items():
            if valeur and str(valeur).strip():
                label = cle.replace("_", " ").title()
                elements.append(Paragraph(f"<b>{label} :</b>", label_style))
                elements.append(Paragraph(str(valeur), corps_style))

        if self.chemin_footer and Path(self.chemin_footer).exists():
            elements.append(Spacer(1, 1*cm))
            elements.append(RLImage(self.chemin_footer, width=16*cm, height=2*cm))

        doc.build(elements)
        logger.info(f"PDF basique généré : {chemin_sortie}")

    def _finaliser_pdf(
        self,
        chemin_source: str,
        chemin_destination: str,
        chemin_qr: Optional[str],
        doc_uuid: str,
        date_expiration: str,
        type_document: str,
        nom_etablissement: str,
    ) -> str:
        """Ajoute QR code, bande de sécurité et métadonnées au PDF."""
        try:
            doc = fitz.open(chemin_source)
            page = doc[-1]
            w = A4[0] / mm
            h = A4[1] / mm

            # ── QR code ────────────────────────────────────────────────────
            if chemin_qr and Path(chemin_qr).exists():
                rect_qr = fitz.Rect(w - 45, h - 50, w - 5, h - 10)
                page.insert_image(rect_qr, filename=chemin_qr)
                rect_txt = fitz.Rect(w - 50, h - 10, w, h - 2)
                page.insert_textbox(
                    rect_txt, "Vérifier l'authenticité",
                    fontsize=6, color=(0.4, 0.4, 0.4), align=fitz.TEXT_ALIGN_CENTER,
                )

            # ── Bande de sécurité ──────────────────────────────────────────
            page.draw_rect(
                fitz.Rect(0, h - 8, w, h - 2),
                color=(0.1, 0.1, 0.2), fill=(0.1, 0.1, 0.2),
            )
            page.insert_text(
                (10, h - 4),
                f"Document officiel SmartIntern AI | UUID: {doc_uuid} | "
                f"Expire: {date_expiration[:10] if date_expiration else 'N/A'}",
                fontsize=5, color=(1, 1, 1),
            )

            # ── Métadonnées PDF ────────────────────────────────────────────
            doc.set_metadata({
                "title":    type_document,
                "author":   nom_etablissement or "SmartIntern AI",
                "subject":  f"Document officiel - {type_document}",
                "keywords": f"smartintern, {type_document}, {doc_uuid}",
                "creator":  "SmartIntern AI Document Engine v2.1",
            })

            doc.save(chemin_destination)
            doc.close()
            logger.info(f"PDF finalisé : {chemin_destination}")
            return chemin_destination

        except Exception as e:
            logger.error(f"Erreur finalisation PDF : {e}")
            shutil.copy2(chemin_source, chemin_destination)
            return chemin_destination


# ══════════════════════════════════════════════════════════════════════════
# SERVICE PRINCIPAL
# ══════════════════════════════════════════════════════════════════════════

def generer_document(
    modele_id: str,
    chemin_script: str,
    chemin_header: Optional[str],
    chemin_footer: Optional[str],
    type_document: str,
    donnees_profil: dict,
    nom_etablissement: str = "Établissement",
    duree_validite_jours: int = 365,
) -> dict:
    """
    Génère un document PDF personnalisé pour un utilisateur.

    Flow :
      1. UUID + dates + signature HMAC
      2. Chargement du script Python du modèle
      3. Génération QR code
      4. Exécution script → PDF corps
      5. Finalisation PDF (QR + sécurité + métadonnées)
      6. Retour des métadonnées pour persistance Java/MySQL
    """
    doc_uuid        = generer_uuid()
    date_generation = datetime.now().isoformat()
    date_expiration = (datetime.now() + timedelta(days=duree_validite_jours)).isoformat()
    signature       = generer_signature(doc_uuid, date_generation, date_expiration)

    # ── Chargement du script ───────────────────────────────────────────────
    if Path(chemin_script).exists():
        with open(chemin_script, "r", encoding="utf-8") as f:
            script_python = f.read()
    else:
        logger.warning(f"Script introuvable : {chemin_script} — génération basique")
        script_python = ""

    # ── QR code ────────────────────────────────────────────────────────────
    chemin_qr = str(settings.DOCUMENTS_DIR / f"{doc_uuid}_qr.png")
    generer_qr_code(
        doc_uuid=doc_uuid,
        type_document=type_document,
        nom_etablissement=nom_etablissement,
        date_generation=date_generation,
        date_expiration=date_expiration,
        signature=signature,
        chemin_sortie=chemin_qr,
    )

    # ── Génération PDF ─────────────────────────────────────────────────────
    chemin_pdf = str(settings.DOCUMENTS_DIR / f"{doc_uuid}.pdf")
    moteur = MoteurGenerationPDF(
        chemin_header=chemin_header if chemin_header and Path(chemin_header).exists() else None,
        chemin_footer=chemin_footer if chemin_footer and Path(chemin_footer).exists() else None,
    )

    donnees_enrichies = {
        **donnees_profil,
        "date_generation":   date_generation[:10],
        "date_expiration":   date_expiration[:10],
        "numero_document":   doc_uuid[:8].upper(),
        "nom_etablissement": nom_etablissement,
    }

    moteur.generer_depuis_script(
        script_python=script_python,
        donnees=donnees_enrichies,
        chemin_sortie=chemin_pdf,
        chemin_qr=chemin_qr,
        doc_uuid=doc_uuid,
        date_expiration=date_expiration,
        type_document=type_document,
        nom_etablissement=nom_etablissement,
    )

    # Nettoyage QR temporaire
    if Path(chemin_qr).exists():
        Path(chemin_qr).unlink()

    taille = Path(chemin_pdf).stat().st_size if Path(chemin_pdf).exists() else 0
    logger.info(
        f"Document généré : {doc_uuid} | {type_document} | "
        f"{taille/1024:.1f} KB | expire={date_expiration[:10]}"
    )

    return {
        "doc_uuid":               doc_uuid,
        "modele_id":              modele_id,
        "type_document":          type_document,
        "chemin_fichier":         chemin_pdf,
        "url_fichier":            f"{settings.BASE_URL}/documents/telecharger/{doc_uuid}",
        "url_verification":       f"{settings.BASE_URL}/documents/verifier/{doc_uuid}",
        "date_generation":        date_generation,
        "date_expiration":        date_expiration,
        "signature":              signature,
        "statut":                 "VALIDE",
        "taille_octets":          taille,
        "nom_etablissement":      nom_etablissement,
        "donnees_profil_utilises": list(donnees_enrichies.keys()),
    }


def regenerer_document(
    doc_uuid_original: str,
    modele_id: str,
    chemin_script: str,
    chemin_header: Optional[str],
    chemin_footer: Optional[str],
    type_document: str,
    donnees_profil: dict,
    nom_etablissement: str = "",
    duree_validite_jours: int = 365,
) -> dict:
    """Régénère un document existant (nouvelle version, référence l'original)."""
    resultat = generer_document(
        modele_id=modele_id,
        chemin_script=chemin_script,
        chemin_header=chemin_header,
        chemin_footer=chemin_footer,
        type_document=type_document,
        donnees_profil=donnees_profil,
        nom_etablissement=nom_etablissement,
        duree_validite_jours=duree_validite_jours,
    )
    resultat["doc_uuid_original"] = doc_uuid_original
    resultat["regeneration"]      = True
    return resultat


def verifier_document(
    doc_uuid: str,
    date_generation: str,
    date_expiration: str,
    signature: str,
    type_document: str = "",
    nom_etablissement: str = "",
) -> dict:
    """
    Vérifie l'authenticité et la validité d'un document.
    Appelé depuis l'endpoint /documents/verifier/{uuid} (scan QR).
    """
    signature_valide = verifier_signature(doc_uuid, date_generation, date_expiration, signature)
    expire           = est_expire(date_expiration)
    fichier_existe   = (settings.DOCUMENTS_DIR / f"{doc_uuid}.pdf").exists()

    if not signature_valide:
        statut  = "FALSIFIE"
        message = "Document non authentique — signature invalide"
    elif expire:
        statut  = "EXPIRE"
        message = f"Document expiré le {date_expiration[:10]}"
    elif not fichier_existe:
        statut  = "INTROUVABLE"
        message = "Document introuvable dans le système"
    else:
        statut  = "VALIDE"
        message = "Document authentique et valide"

    return {
        "doc_uuid":          doc_uuid,
        "statut":            statut,
        "valide":            statut == "VALIDE",
        "message":           message,
        "type_document":     type_document,
        "nom_etablissement": nom_etablissement,
        "date_generation":   date_generation,
        "date_expiration":   date_expiration,
        "signature_valide":  signature_valide,
        "expire":            expire,
        "fichier_existe":    fichier_existe,
        "verifie_le":        datetime.now().isoformat(),
    }
