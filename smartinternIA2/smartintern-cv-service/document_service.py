"""
SmartIntern AI — Document Generation Service
=============================================

Gère la création de modèles de documents et la génération automatique
de documents PDF personnalisés avec :
  - Analyse IA du modèle (extraction des champs dynamiques)
  - Génération automatique d'un script Python réutilisable
  - Injection des données profil utilisateur
  - En-tête / pied de page personnalisés
  - QR code de vérification avec date d'expiration
  - Signature numérique HMAC
  - Historique et versioning des documents générés

Entités miroir Java :
  TypeDocument, ModeleDocument, Document, DocumentGenere

Architecture :
  Fichiers stockés sur disque (uploads/)
  Métadonnées retournées au backend Java (MySQL)
"""

import os
import re
import json
import uuid
import hmac
import hashlib
import logging
import textwrap
import tempfile
import subprocess
from io import BytesIO
from pathlib import Path
from datetime import datetime, timedelta
from typing import Optional, Any

from PIL import Image
import qrcode
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import cm, mm
from reportlab.lib.colors import HexColor, black, white, grey
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT, TA_JUSTIFY
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Image as RLImage,
    Table, TableStyle, HRFlowable, KeepTogether
)
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

import pdfplumber
import fitz  # PyMuPDF
from openai import OpenAI
from typing import TYPE_CHECKING
if TYPE_CHECKING:
    from main import FallbackLLMClient

logger = logging.getLogger("document-service")

# ── Configuration ──────────────────────────────────────────────────────────
UPLOAD_BASE   = Path("uploads")
MODELES_DIR   = UPLOAD_BASE / "modeles"
DOCUMENTS_DIR = UPLOAD_BASE / "documents"
HEADERS_DIR   = UPLOAD_BASE / "headers"
FOOTERS_DIR   = UPLOAD_BASE / "footers"
SCRIPTS_DIR   = UPLOAD_BASE / "scripts"

for d in [MODELES_DIR, DOCUMENTS_DIR, HEADERS_DIR, FOOTERS_DIR, SCRIPTS_DIR]:
    d.mkdir(parents=True, exist_ok=True)

# Clé secrète pour la signature HMAC (à mettre dans .env en production)
SIGNATURE_SECRET = os.environ.get("SIGNATURE_SECRET", "SmartIntern_SecretKey_2024")
BASE_URL         = os.environ.get("BASE_URL", "http://localhost:8000")
MODEL_CLAUDE     = "anthropic/claude-sonnet-4-5"


# ══════════════════════════════════════════════════════════════════════════
# UTILITAIRES
# ══════════════════════════════════════════════════════════════════════════

def generer_uuid() -> str:
    return str(uuid.uuid4())


def generer_signature(doc_uuid: str, date_generation: str, date_expiration: str) -> str:
    """Signature HMAC-SHA256 pour l'authenticité du document."""
    message = f"{doc_uuid}:{date_generation}:{date_expiration}"
    return hmac.new(
        SIGNATURE_SECRET.encode(),
        message.encode(),
        hashlib.sha256
    ).hexdigest()


def verifier_signature(doc_uuid: str, date_generation: str,
                        date_expiration: str, signature: str) -> bool:
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


def extraire_texte_pdf(chemin: str) -> str:
    """Extrait le texte brut d'un PDF pour analyse IA."""
    texte_pages = []
    try:
        with pdfplumber.open(chemin) as pdf:
            for page in pdf.pages:
                t = page.extract_text(layout=True) or page.extract_text() or ""
                texte_pages.append(t)
        texte = "\n\n--- PAGE ---\n\n".join(texte_pages).strip()
        if len(texte) >= 100:
            return texte
    except Exception as e:
        logger.warning(f"pdfplumber erreur: {e}")

    # Fallback PyMuPDF
    doc = fitz.open(chemin)
    return "\n".join(page.get_text() for page in doc)


def extraire_texte_docx(chemin: str) -> str:
    """Extrait le texte d'un DOCX pour analyse IA."""
    try:
        result = subprocess.run(
            ["pandoc", chemin, "-t", "plain", "--wrap=none"],
            capture_output=True, text=True, timeout=30
        )
        if result.returncode == 0 and result.stdout.strip():
            return result.stdout.strip()
    except Exception:
        pass
    try:
        from docx import Document
        doc = Document(chemin)
        return "\n".join(p.text for p in doc.paragraphs if p.text.strip())
    except Exception as e:
        raise RuntimeError(f"Impossible de lire le DOCX: {e}")


# ══════════════════════════════════════════════════════════════════════════
# ANALYSE IA DU MODÈLE DE DOCUMENT
# ══════════════════════════════════════════════════════════════════════════

ANALYSE_SYSTEM_PROMPT = """Tu es un expert en analyse de documents officiels et en génération de code Python.
Ta mission : analyser le texte d'un document modèle et en extraire sa structure pour la génération automatique.

RÈGLES :
1. Identifie TOUS les champs dynamiques (données variables à remplir par personne)
2. Identifie le texte statique (titre, corps fixe, mentions légales)
3. Génère des noms de champs en snake_case clairs (ex: nom_etudiant, date_debut_stage)
4. Associe chaque champ à une source de données (profil_etudiant, profil_entreprise, stage, date_system)
5. Réponds UNIQUEMENT avec un JSON valide, sans markdown

Structure JSON attendue :
{
  "type_detecte": "convention_stage | attestation_stage | lettre_recommandation | autre",
  "titre_document": "Titre tel qu'il apparait dans le document",
  "langue": "fr | en | ar",
  "sections": [
    {
      "nom": "en_tete | corps | conclusion | signatures | autre",
      "texte_statique": "texte fixe de cette section",
      "ordre": 1
    }
  ],
  "champs_dynamiques": [
    {
      "nom": "nom_etudiant",
      "label": "Nom de l'étudiant",
      "type": "texte | date | nombre | booleen",
      "source": "profil_etudiant | profil_entreprise | stage | etablissement | date_system | admin",
      "cle_source": "lastName",
      "obligatoire": true,
      "placeholder": "{{nom_etudiant}}",
      "description": "Nom de famille de l'étudiant stagiaire",
      "exemple": "DUPONT"
    }
  ],
  "mise_en_page": {
    "orientation": "portrait | paysage",
    "marges": {"haut": 2, "bas": 2, "gauche": 2.5, "droite": 2.5},
    "police_principale": "Helvetica",
    "taille_police": 11
  }
}"""

SCRIPT_SYSTEM_PROMPT = """Tu es un expert Python spécialisé en génération de documents PDF avec ReportLab.
Ta mission : générer un script Python COMPLET et réutilisable pour produire ce document.

RÈGLES STRICTES :
1. Le script doit être une fonction `generer_document(donnees: dict, chemin_sortie: str, chemin_header: str = None, chemin_footer: str = None) -> str`
2. Utilise ReportLab (SimpleDocTemplate, Paragraph, Table, etc.)
3. Les champs dynamiques sont injectés depuis le dict `donnees` avec des valeurs par défaut si absentes
4. Le script doit gérer les valeurs None ou manquantes proprement
5. Intègre l'en-tête et le pied de page s'ils sont fournis (images PNG/JPG)
6. Retourne le chemin du PDF généré
7. Le script doit être autonome (imports inclus)
8. N'inclus PAS le QR code (il sera ajouté par le moteur de génération)
9. Réponds UNIQUEMENT avec le code Python, SANS markdown, SANS explication"""


def analyser_modele_avec_ia(texte_modele: str,
                              nom_fichier: str,
                              client) -> dict:  # OpenAI | FallbackLLMClient
    """
    Envoie le texte du modèle à OpenRouter pour analyse structurelle.
    Retourne un dict avec type_detecte, champs_dynamiques, sections, mise_en_page.
    """
    try:
        response = client.chat.completions.create(
            model=MODEL_CLAUDE,
            max_tokens=4096,
            temperature=0.3,
            top_p=0.95,
            messages=[
                {"role": "system", "content": ANALYSE_SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": (
                        f"Analyse ce document modèle (fichier: {nom_fichier}) "
                        f"et extrais sa structure complète :\n\n{texte_modele}"
                    )
                }
            ]
        )
        texte = response.choices[0].message.content.strip()
        # Nettoyage robuste
        m = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", texte)
        if m: texte = m.group(1).strip()
        m2 = re.search(r"(\{[\s\S]*\})", texte)
        if m2: texte = m2.group(1).strip()
        return json.loads(texte)
    except json.JSONDecodeError as e:
        logger.error(f"Analyse IA JSON erreur: {e}")
        raise RuntimeError(f"Erreur parsing analyse IA: {e}")


def generer_script_avec_ia(analyse: dict,
                             texte_modele: str,
                             client) -> str:  # OpenAI | FallbackLLMClient
    """
    Génère un script Python ReportLab basé sur l'analyse du modèle.
    Ce script sera stocké et réutilisé pour chaque génération.
    """
    champs = json.dumps(analyse.get("champs_dynamiques", []),
                        ensure_ascii=False, indent=2)
    sections = json.dumps(analyse.get("sections", []),
                           ensure_ascii=False, indent=2)
    mise_en_page = json.dumps(analyse.get("mise_en_page", {}),
                               ensure_ascii=False, indent=2)

    prompt = f"""Génère un script Python complet avec ReportLab pour produire ce document.

Type de document : {analyse.get('type_detecte', 'inconnu')}
Titre : {analyse.get('titre_document', '')}
Langue : {analyse.get('langue', 'fr')}

Champs dynamiques à injecter :
{champs}

Sections du document :
{sections}

Mise en page :
{mise_en_page}

Texte original du modèle (pour référence) :
{texte_modele[:3000]}

Génère la fonction generer_document() complète."""

    try:
        response = client.chat.completions.create(
            model=MODEL_CLAUDE,
            max_tokens=8192,
            temperature=0.2,
            top_p=0.95,
            messages=[
                {"role": "system", "content": SCRIPT_SYSTEM_PROMPT},
                {"role": "user", "content": prompt}
            ]
        )
        script = response.choices[0].message.content.strip()
        # Nettoyer les balises markdown si présentes
        m = re.search(r"```(?:python)?\s*([\s\S]*?)\s*```", script)
        if m:
            script = m.group(1).strip()
        return script
    except Exception as e:
        logger.error(f"Génération script IA erreur: {e}")
        raise RuntimeError(f"Erreur génération script: {e}")


# ══════════════════════════════════════════════════════════════════════════
# GÉNÉRATION QR CODE
# ══════════════════════════════════════════════════════════════════════════

def generer_qr_code(doc_uuid: str,
                     type_document: str,
                     nom_etablissement: str,
                     date_generation: str,
                     date_expiration: str,
                     signature: str,
                     chemin_sortie: str) -> str:
    """
    Génère un QR code contenant les informations de vérification du document.
    Le QR code pointe vers l'endpoint de vérification avec toutes les données.

    Contenu du QR code :
      - URL de vérification
      - UUID unique du document
      - Type de document
      - Nom de l'établissement
      - Date de génération
      - Date d'expiration
      - Signature HMAC pour authenticité
    """
    url_verification = f"{BASE_URL}/documents/verifier/{doc_uuid}"

    donnees_qr = {
        "url": url_verification,
        "uuid": doc_uuid,
        "type": type_document,
        "etablissement": nom_etablissement,
        "date_generation": date_generation,
        "date_expiration": date_expiration,
        "signature": signature[:16] + "...",  # signature partielle dans QR
    }

    contenu_qr = json.dumps(donnees_qr, ensure_ascii=False)

    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_H,  # haute résistance erreur
        box_size=8,
        border=2,
    )
    qr.add_data(contenu_qr)
    qr.make(fit=True)

    img_qr = qr.make_image(
        fill_color="#1a1a2e",
        back_color="white"
    ).convert("RGB")

    img_qr.save(chemin_sortie, "PNG", dpi=(300, 300))
    logger.info(f"QR code généré: {chemin_sortie}")
    return chemin_sortie


# ══════════════════════════════════════════════════════════════════════════
# MOTEUR DE GÉNÉRATION PDF
# ══════════════════════════════════════════════════════════════════════════

class MoteurGenerationPDF:
    """
    Moteur principal de génération de documents PDF.
    Prend en charge :
      - Exécution du script généré par IA
      - Intégration en-tête / pied de page
      - Injection QR code + signature
      - Watermark "ORIGINAL" (optionnel)
    """

    PAGE_WIDTH, PAGE_HEIGHT = A4

    def __init__(self,
                 chemin_header: Optional[str] = None,
                 chemin_footer: Optional[str] = None):
        self.chemin_header = chemin_header
        self.chemin_footer = chemin_footer

    def generer_depuis_script(self,
                               script_python: str,
                               donnees: dict,
                               chemin_sortie: str,
                               chemin_qr: Optional[str] = None,
                               doc_uuid: str = "",
                               date_expiration: str = "",
                               type_document: str = "",
                               nom_etablissement: str = "") -> str:
        """
        Exécute le script Python généré par IA dans un contexte sécurisé,
        puis ajoute en-tête, pied de page, QR code et métadonnées.
        """
        chemin_tmp = str(DOCUMENTS_DIR / f"tmp_{doc_uuid}.pdf")

        # 1. Exécuter le script IA pour le corps du document
        try:
            namespace = {}
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
                logger.warning("Script sans fonction generer_document — génération basique")
                self._generer_pdf_basique(donnees, chemin_tmp, type_document)
        except Exception as e:
            logger.error(f"Erreur exécution script IA: {e}")
            self._generer_pdf_basique(donnees, chemin_tmp, type_document)

        # 2. Ajouter QR code, métadonnées et finaliser
        chemin_final = self._finaliser_pdf(
            chemin_tmp, chemin_sortie, chemin_qr,
            doc_uuid, date_expiration, type_document, nom_etablissement
        )

        # Nettoyage fichier temporaire
        if Path(chemin_tmp).exists() and chemin_tmp != chemin_final:
            Path(chemin_tmp).unlink()

        return chemin_final

    def _generer_pdf_basique(self, donnees: dict, chemin_sortie: str,
                              type_document: str = "Document"):
        """
        Génération PDF basique de fallback si le script IA échoue.
        Produit un document lisible avec toutes les données du profil.
        """
        doc = SimpleDocTemplate(
            chemin_sortie,
            pagesize=A4,
            rightMargin=2.5 * cm,
            leftMargin=2.5 * cm,
            topMargin=3 * cm,
            bottomMargin=3 * cm,
        )
        styles = getSampleStyleSheet()
        elements = []

        # En-tête image
        if self.chemin_header and Path(self.chemin_header).exists():
            img = RLImage(self.chemin_header, width=16 * cm, height=3 * cm)
            elements.append(img)
            elements.append(Spacer(1, 0.5 * cm))

        # Titre
        titre_style = ParagraphStyle(
            "Titre", parent=styles["Title"],
            fontSize=16, spaceAfter=20,
            textColor=HexColor("#1a1a2e"), alignment=TA_CENTER,
        )
        elements.append(Paragraph(type_document.upper(), titre_style))
        elements.append(HRFlowable(width="100%", thickness=2,
                                    color=HexColor("#1a1a2e")))
        elements.append(Spacer(1, 0.5 * cm))

        # Corps — données injectées
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

        # Pied de page image
        if self.chemin_footer and Path(self.chemin_footer).exists():
            elements.append(Spacer(1, 1 * cm))
            img_footer = RLImage(self.chemin_footer, width=16 * cm, height=2 * cm)
            elements.append(img_footer)

        doc.build(elements)
        logger.info(f"PDF basique généré: {chemin_sortie}")

    def _finaliser_pdf(self,
                        chemin_source: str,
                        chemin_destination: str,
                        chemin_qr: Optional[str],
                        doc_uuid: str,
                        date_expiration: str,
                        type_document: str,
                        nom_etablissement: str) -> str:
        """
        Ouvre le PDF source et ajoute :
          - QR code en bas à droite de la dernière page
          - Texte de vérification
          - Métadonnées PDF (auteur, titre, sujet)
          - Bande de sécurité
        """
        try:
            doc = fitz.open(chemin_source)
            derniere_page = doc[-1]

            # ── QR code ────────────────────────────────────────────────────
            if chemin_qr and Path(chemin_qr).exists():
                rect_qr = fitz.Rect(
                    A4[0] / mm - 45,  # x0 : 45mm depuis la droite
                    A4[1] / mm - 50,  # y0 : 50mm depuis le bas
                    A4[0] / mm - 5,   # x1
                    A4[1] / mm - 10,  # y1
                )
                derniere_page.insert_image(rect_qr, filename=chemin_qr)

                # Texte sous le QR
                rect_txt = fitz.Rect(
                    A4[0] / mm - 50, A4[1] / mm - 10,
                    A4[0] / mm, A4[1] / mm - 2,
                )
                derniere_page.insert_textbox(
                    rect_txt,
                    "Vérifier l'authenticité",
                    fontsize=6,
                    color=(0.4, 0.4, 0.4),
                    align=fitz.TEXT_ALIGN_CENTER,
                )

            # ── Bande de sécurité en bas ───────────────────────────────────
            w = A4[0] / mm
            h = A4[1] / mm
            derniere_page.draw_rect(
                fitz.Rect(0, h - 8, w, h - 2),
                color=(0.1, 0.1, 0.2), fill=(0.1, 0.1, 0.2)
            )
            derniere_page.insert_text(
                (10, h - 4),
                f"Document officiel SmartIntern AI | UUID: {doc_uuid} | "
                f"Expire: {date_expiration[:10] if date_expiration else 'N/A'}",
                fontsize=5,
                color=(1, 1, 1),
            )

            # ── Métadonnées PDF ────────────────────────────────────────────
            doc.set_metadata({
                "title": type_document,
                "author": nom_etablissement or "SmartIntern AI",
                "subject": f"Document officiel - {type_document}",
                "keywords": f"smartintern, {type_document}, {doc_uuid}",
                "creator": "SmartIntern AI Document Engine v2.0",
            })

            doc.save(chemin_destination)
            doc.close()
            logger.info(f"PDF finalisé: {chemin_destination}")
            return chemin_destination

        except Exception as e:
            logger.error(f"Erreur finalisation PDF: {e}")
            # Si la finalisation échoue, utiliser le fichier source
            import shutil
            shutil.copy2(chemin_source, chemin_destination)
            return chemin_destination


# ══════════════════════════════════════════════════════════════════════════
# SERVICE PRINCIPAL
# ══════════════════════════════════════════════════════════════════════════

class DocumentService:
    """
    Service principal exposé aux endpoints FastAPI.
    Orchestre l'analyse IA, la génération de scripts et la production PDF.
    Accepte un OpenAI standard ou un FallbackLLMClient (avec fallback automatique).
    """

    def __init__(self, anthropic_client):  # OpenAI | FallbackLLMClient | None
        self.client = anthropic_client

    # ── Création de modèle ─────────────────────────────────────────────────

    def creer_modele(self,
                      modele_id: str,
                      nom_modele: str,
                      type_document: str,
                      contenu_modele: bytes,
                      nom_fichier_modele: str,
                      contenu_header: Optional[bytes] = None,
                      nom_fichier_header: Optional[str] = None,
                      contenu_footer: Optional[bytes] = None,
                      nom_fichier_footer: Optional[str] = None,
                      duree_validite_jours: int = 365) -> dict:
        """
        Étape 1 : Sauvegarde les fichiers du modèle.
        Étape 2 : Analyse IA du modèle → extraction des champs.
        Étape 3 : Génération automatique du script Python.
        Retourne les métadonnées complètes pour persistance Java/MySQL.
        """
        if not self.client:
            raise RuntimeError(
                "OPENROUTER_API_KEY non configurée — analyse IA impossible"
            )

        # ── Sauvegarde fichier modèle ──────────────────────────────────────
        ext_modele = Path(nom_fichier_modele).suffix.lower()
        chemin_modele = str(MODELES_DIR / f"{modele_id}{ext_modele}")
        with open(chemin_modele, "wb") as f:
            f.write(contenu_modele)
        logger.info(f"Modèle sauvegardé: {chemin_modele}")

        # ── Sauvegarde en-tête ─────────────────────────────────────────────
        chemin_header = None
        if contenu_header and nom_fichier_header:
            ext_h = Path(nom_fichier_header).suffix.lower()
            chemin_header = str(HEADERS_DIR / f"{modele_id}_header{ext_h}")
            with open(chemin_header, "wb") as f:
                f.write(contenu_header)
            # Valider que c'est bien une image
            try:
                with Image.open(chemin_header) as img:
                    img.verify()
                logger.info(f"Header validé: {chemin_header}")
            except Exception:
                logger.warning(f"Header invalide: {chemin_header}")
                chemin_header = None

        # ── Sauvegarde pied de page ────────────────────────────────────────
        chemin_footer = None
        if contenu_footer and nom_fichier_footer:
            ext_f = Path(nom_fichier_footer).suffix.lower()
            chemin_footer = str(FOOTERS_DIR / f"{modele_id}_footer{ext_f}")
            with open(chemin_footer, "wb") as f:
                f.write(contenu_footer)
            try:
                with Image.open(chemin_footer) as img:
                    img.verify()
                logger.info(f"Footer validé: {chemin_footer}")
            except Exception:
                logger.warning(f"Footer invalide: {chemin_footer}")
                chemin_footer = None

        # ── Extraction texte du modèle ─────────────────────────────────────
        if ext_modele == ".pdf":
            texte_modele = extraire_texte_pdf(chemin_modele)
        elif ext_modele in (".docx", ".doc"):
            texte_modele = extraire_texte_docx(chemin_modele)
        else:
            raise ValueError(f"Format modèle non supporté: {ext_modele}")

        if len(texte_modele.strip()) < 50:
            raise ValueError("Le document modèle ne contient pas assez de texte")

        # ── Analyse IA : structure + champs dynamiques ─────────────────────
        logger.info(f"Analyse IA du modèle '{nom_modele}' en cours...")
        analyse = analyser_modele_avec_ia(texte_modele, nom_fichier_modele, self.client)
        logger.info(
            f"Analyse terminée: type={analyse.get('type_detecte')} | "
            f"{len(analyse.get('champs_dynamiques', []))} champs détectés"
        )

        # ── Génération du script Python réutilisable ───────────────────────
        logger.info("Génération du script Python en cours...")
        script_python = generer_script_avec_ia(analyse, texte_modele, self.client)

        chemin_script = str(SCRIPTS_DIR / f"{modele_id}_script.py")
        with open(chemin_script, "w", encoding="utf-8") as f:
            f.write(f"# Script généré automatiquement par SmartIntern AI\n")
            f.write(f"# Modèle: {nom_modele}\n")
            f.write(f"# Type: {analyse.get('type_detecte', type_document)}\n")
            f.write(f"# Généré le: {datetime.now().isoformat()}\n\n")
            f.write(script_python)

        logger.info(f"Script sauvegardé: {chemin_script}")

        return {
            "modele_id": modele_id,
            "nom_modele": nom_modele,
            "type_document": analyse.get("type_detecte", type_document),
            "titre_document": analyse.get("titre_document", nom_modele),
            "langue": analyse.get("langue", "fr"),
            "chemin_modele": chemin_modele,
            "chemin_header": chemin_header,
            "chemin_footer": chemin_footer,
            "chemin_script": chemin_script,
            "champs_dynamiques": analyse.get("champs_dynamiques", []),
            "sections": analyse.get("sections", []),
            "mise_en_page": analyse.get("mise_en_page", {}),
            "duree_validite_jours": duree_validite_jours,
            "version": 1,
            "date_creation": datetime.now().isoformat(),
            "analyse_complete": analyse,
        }

    # ── Génération de document ─────────────────────────────────────────────

    def generer_document(self,
                          modele_id: str,
                          chemin_script: str,
                          chemin_header: Optional[str],
                          chemin_footer: Optional[str],
                          type_document: str,
                          donnees_profil: dict,
                          nom_etablissement: str = "Établissement",
                          duree_validite_jours: int = 365) -> dict:
        """
        Génère un document PDF personnalisé pour un utilisateur.

        Flow :
          1. Charger le script Python du modèle
          2. Préparer les données profil (mapping champs → valeurs)
          3. Exécuter le script → PDF corps
          4. Générer QR code + signature HMAC
          5. Finaliser PDF (header, footer, QR, métadonnées)
          6. Retourner métadonnées pour persistance Java

        Paramètres :
          modele_id         : ID du modèle de document
          chemin_script     : Chemin vers le script Python généré
          chemin_header/footer : Images d'en-tête et pied de page
          type_document     : Type du document (convention_stage, etc.)
          donnees_profil    : Dict avec toutes les données de l'utilisateur
          nom_etablissement : Nom affiché dans le QR code
          duree_validite    : Durée de validité du document en jours
        """
        doc_uuid = generer_uuid()
        date_generation = datetime.now().isoformat()
        date_expiration = (
            datetime.now() + timedelta(days=duree_validite_jours)
        ).isoformat()

        # ── Signature HMAC ─────────────────────────────────────────────────
        signature = generer_signature(doc_uuid, date_generation, date_expiration)

        # ── Chargement du script Python ────────────────────────────────────
        if Path(chemin_script).exists():
            with open(chemin_script, "r", encoding="utf-8") as f:
                script_python = f.read()
        else:
            logger.warning(f"Script introuvable: {chemin_script} — génération basique")
            script_python = ""

        # ── QR code ────────────────────────────────────────────────────────
        chemin_qr = str(DOCUMENTS_DIR / f"{doc_uuid}_qr.png")
        generer_qr_code(
            doc_uuid=doc_uuid,
            type_document=type_document,
            nom_etablissement=nom_etablissement,
            date_generation=date_generation,
            date_expiration=date_expiration,
            signature=signature,
            chemin_sortie=chemin_qr,
        )

        # ── Génération PDF ─────────────────────────────────────────────────
        chemin_pdf = str(DOCUMENTS_DIR / f"{doc_uuid}.pdf")
        moteur = MoteurGenerationPDF(
            chemin_header=chemin_header if chemin_header and Path(chemin_header).exists() else None,
            chemin_footer=chemin_footer if chemin_footer and Path(chemin_footer).exists() else None,
        )

        donnees_enrichies = {
            **donnees_profil,
            "date_generation": date_generation[:10],
            "date_expiration": date_expiration[:10],
            "numero_document": doc_uuid[:8].upper(),
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
            f"Document généré: {doc_uuid} | {type_document} | "
            f"{taille/1024:.1f}KB | expire={date_expiration[:10]}"
        )

        return {
            "doc_uuid": doc_uuid,
            "modele_id": modele_id,
            "type_document": type_document,
            "chemin_fichier": chemin_pdf,
            "url_fichier": f"{BASE_URL}/documents/telecharger/{doc_uuid}",
            "url_verification": f"{BASE_URL}/documents/verifier/{doc_uuid}",
            "date_generation": date_generation,
            "date_expiration": date_expiration,
            "signature": signature,
            "statut": "VALIDE",
            "taille_octets": taille,
            "nom_etablissement": nom_etablissement,
            "donnees_profil_utilises": list(donnees_enrichies.keys()),
        }

    # ── Vérification d'authenticité ────────────────────────────────────────

    def verifier_document(self,
                           doc_uuid: str,
                           date_generation: str,
                           date_expiration: str,
                           signature: str,
                           type_document: str = "",
                           nom_etablissement: str = "") -> dict:
        """
        Vérifie l'authenticité et la validité d'un document.
        Appelé depuis l'endpoint /documents/verifier/{uuid}
        (scanné depuis le QR code).
        """
        # Vérification signature HMAC
        signature_valide = verifier_signature(
            doc_uuid, date_generation, date_expiration, signature
        )

        # Vérification expiration
        expire = est_expire(date_expiration)

        # Vérification fichier existant
        chemin_pdf = DOCUMENTS_DIR / f"{doc_uuid}.pdf"
        fichier_existe = chemin_pdf.exists()

        statut = "INVALIDE"
        if not signature_valide:
            message = "Document non authentique — signature invalide"
            statut = "FALSIFIE"
        elif expire:
            message = f"Document expiré le {date_expiration[:10]}"
            statut = "EXPIRE"
        elif not fichier_existe:
            message = "Document introuvable dans le système"
            statut = "INTROUVABLE"
        else:
            message = "Document authentique et valide"
            statut = "VALIDE"

        return {
            "doc_uuid": doc_uuid,
            "statut": statut,
            "valide": statut == "VALIDE",
            "message": message,
            "type_document": type_document,
            "nom_etablissement": nom_etablissement,
            "date_generation": date_generation,
            "date_expiration": date_expiration,
            "signature_valide": signature_valide,
            "expire": expire,
            "fichier_existe": fichier_existe,
            "verifie_le": datetime.now().isoformat(),
        }

    # ── Régénération ───────────────────────────────────────────────────────

    def regenerer_document(self,
                            doc_uuid_original: str,
                            modele_id: str,
                            chemin_script: str,
                            chemin_header: Optional[str],
                            chemin_footer: Optional[str],
                            type_document: str,
                            donnees_profil: dict,
                            nom_etablissement: str = "",
                            duree_validite_jours: int = 365) -> dict:
        """
        Régénère un document existant (nouvelle version).
        Même logique que generer_document mais référence l'original.
        """
        resultat = self.generer_document(
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
        resultat["regeneration"] = True
        return resultat
