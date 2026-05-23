"""
Service de gestion des modèles de documents.

Pipeline de création d'un modèle :
  1. Sauvegarde du fichier modèle (PDF/DOCX) + header/footer optionnels
  2. Extraction du texte du modèle
  3. Analyse IA → structure JSON (type, champs dynamiques, sections, mise en page)
  4. Génération automatique d'un script Python ReportLab réutilisable
  5. Retour des métadonnées complètes pour persistance Java/MySQL
"""

import json
import logging
import re
import subprocess
import zipfile
from datetime import datetime
from pathlib import Path
from typing import Optional
from xml.etree import ElementTree as ET

import fitz
import pdfplumber
from PIL import Image

from app.core.ai_client import ai_client
from config import settings

logger = logging.getLogger("smartintern.document_templates.service")

# ══════════════════════════════════════════════════════════════════════════
# PROMPTS SYSTÈME
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
  "titre_document": "Titre tel qu'il apparaît dans le document",
  "langue": "fr | en | ar",
  "sections": [
    {"nom": "en_tete | corps | conclusion | signatures | autre",
     "texte_statique": "texte fixe de cette section", "ordre": 1}
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


# ══════════════════════════════════════════════════════════════════════════
# EXTRACTION TEXTE DU MODÈLE
# ══════════════════════════════════════════════════════════════════════════

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
        logger.warning(f"pdfplumber erreur : {e}")

    # Fallback PyMuPDF
    doc = fitz.open(chemin)
    return "\n".join(page.get_text() for page in doc)


def extraire_texte_docx(chemin: str) -> str:
    """
    Extrait le texte d'un DOCX/DOC pour analyse IA.
    3 tentatives : pandoc → python-docx → ZIP XML direct.
    """
    # 1. pandoc
    try:
        result = subprocess.run(
            ["pandoc", chemin, "-t", "plain", "--wrap=none"],
            capture_output=True, text=True, timeout=30,
        )
        if result.returncode == 0 and result.stdout.strip():
            return result.stdout.strip()
    except Exception:
        pass

    # 2. python-docx
    try:
        from docx import Document
        doc = Document(chemin)
        texte = "\n".join(p.text for p in doc.paragraphs if p.text.strip())
        if texte.strip():
            return texte
    except Exception:
        pass

    # 3. ZIP XML interne
    try:
        W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        with zipfile.ZipFile(chemin, "r") as z:
            if "word/document.xml" in z.namelist():
                with z.open("word/document.xml") as f:
                    root = ET.parse(f).getroot()
                texts = [t.text for t in root.iter(f"{{{W}}}t") if t.text]
                texte = " ".join(texts)
                if texte.strip():
                    return texte
    except Exception:
        pass

    raise RuntimeError(
        "Impossible de lire le fichier Word. "
        "Vérifiez que le fichier est un DOCX valide (pas un .doc ancien format)."
    )


# ══════════════════════════════════════════════════════════════════════════
# ANALYSE IA
# ══════════════════════════════════════════════════════════════════════════

def _nettoyer_json(texte: str) -> str:
    m = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", texte)
    if m: return m.group(1).strip()
    m2 = re.search(r"(\{[\s\S]*\})", texte)
    if m2: return m2.group(1).strip()
    return texte.strip()


def analyser_modele_avec_ia(texte_modele: str, nom_fichier: str) -> dict:
    """
    Envoie le texte du modèle au LLM pour analyse structurelle.

    Returns:
        dict avec type_detecte, champs_dynamiques, sections, mise_en_page.
    """
    if not ai_client:
        raise RuntimeError(
            "Aucune clé API IA configurée — "
            "renseignez NVIDIA_API_KEY et/ou OPENROUTER_API_KEY dans .env"
        )
    try:
        response = ai_client.chat.completions.create(
            model="",
            max_tokens=4096,
            temperature=0.3,
            top_p=0.95,
            messages=[
                {"role": "system", "content": ANALYSE_SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": (
                        f"Analyse ce document modèle (fichier : {nom_fichier}) "
                        f"et extrais sa structure complète :\n\n{texte_modele}"
                    ),
                },
            ],
        )
        texte = response.choices[0].message.content.strip()
        return json.loads(_nettoyer_json(texte))
    except json.JSONDecodeError as e:
        logger.error(f"Analyse IA JSON erreur : {e}")
        raise RuntimeError(f"Erreur parsing analyse IA : {e}")


def generer_script_avec_ia(analyse: dict, texte_modele: str) -> str:
    """
    Génère un script Python ReportLab basé sur l'analyse du modèle.
    Ce script est stocké et réutilisé à chaque génération de document.
    """
    if not ai_client:
        raise RuntimeError("Client IA non disponible")

    champs      = json.dumps(analyse.get("champs_dynamiques", []), ensure_ascii=False, indent=2)
    sections    = json.dumps(analyse.get("sections", []),           ensure_ascii=False, indent=2)
    mise_en_page = json.dumps(analyse.get("mise_en_page", {}),      ensure_ascii=False, indent=2)

    prompt = (
        f"Génère un script Python complet avec ReportLab pour produire ce document.\n\n"
        f"Type de document : {analyse.get('type_detecte', 'inconnu')}\n"
        f"Titre : {analyse.get('titre_document', '')}\n"
        f"Langue : {analyse.get('langue', 'fr')}\n\n"
        f"Champs dynamiques :\n{champs}\n\n"
        f"Sections :\n{sections}\n\n"
        f"Mise en page :\n{mise_en_page}\n\n"
        f"Texte original (référence) :\n{texte_modele[:3000]}\n\n"
        f"Génère la fonction generer_document() complète."
    )

    try:
        response = ai_client.chat.completions.create(
            model="",
            max_tokens=8192,
            temperature=0.2,
            top_p=0.95,
            messages=[
                {"role": "system", "content": SCRIPT_SYSTEM_PROMPT},
                {"role": "user",   "content": prompt},
            ],
        )
        script = response.choices[0].message.content.strip()
        m = re.search(r"```(?:python)?\s*([\s\S]*?)\s*```", script)
        if m:
            script = m.group(1).strip()
        return script
    except Exception as e:
        logger.error(f"Génération script IA erreur : {e}")
        raise RuntimeError(f"Erreur génération script : {e}")


# ══════════════════════════════════════════════════════════════════════════
# SERVICE PRINCIPAL
# ══════════════════════════════════════════════════════════════════════════

def creer_modele(
    modele_id: str,
    nom_modele: str,
    type_document: str,
    contenu_modele: bytes,
    nom_fichier_modele: str,
    contenu_header: Optional[bytes] = None,
    nom_fichier_header: Optional[str] = None,
    contenu_footer: Optional[bytes] = None,
    nom_fichier_footer: Optional[str] = None,
    duree_validite_jours: int = 365,
) -> dict:
    """
    Pipeline complet de création d'un modèle de document :
      1. Sauvegarde fichier modèle + header/footer
      2. Extraction du texte
      3. Analyse IA → structure + champs dynamiques
      4. Génération du script Python réutilisable
      5. Retour des métadonnées (pour persistance Java/MySQL)
    """
    if not ai_client:
        raise RuntimeError(
            "Aucune clé API IA configurée — analyse IA impossible"
        )

    # ── Sauvegarde fichier modèle ──────────────────────────────────────────
    ext_modele = Path(nom_fichier_modele).suffix.lower()
    chemin_modele = str(settings.MODELES_DIR / f"{modele_id}{ext_modele}")
    with open(chemin_modele, "wb") as f:
        f.write(contenu_modele)
    logger.info(f"Modèle sauvegardé : {chemin_modele}")

    # ── Sauvegarde en-tête ─────────────────────────────────────────────────
    chemin_header = None
    if contenu_header and nom_fichier_header:
        ext_h = Path(nom_fichier_header).suffix.lower()
        chemin_header = str(settings.HEADERS_DIR / f"{modele_id}_header{ext_h}")
        with open(chemin_header, "wb") as f:
            f.write(contenu_header)
        try:
            with Image.open(chemin_header) as img:
                img.verify()
            logger.info(f"Header validé : {chemin_header}")
        except Exception:
            logger.warning(f"Header invalide : {chemin_header}")
            chemin_header = None

    # ── Sauvegarde pied de page ────────────────────────────────────────────
    chemin_footer = None
    if contenu_footer and nom_fichier_footer:
        ext_f = Path(nom_fichier_footer).suffix.lower()
        chemin_footer = str(settings.FOOTERS_DIR / f"{modele_id}_footer{ext_f}")
        with open(chemin_footer, "wb") as f:
            f.write(contenu_footer)
        try:
            with Image.open(chemin_footer) as img:
                img.verify()
            logger.info(f"Footer validé : {chemin_footer}")
        except Exception:
            logger.warning(f"Footer invalide : {chemin_footer}")
            chemin_footer = None

    # ── Extraction texte ───────────────────────────────────────────────────
    if ext_modele == ".pdf":
        texte_modele = extraire_texte_pdf(chemin_modele)
    elif ext_modele in (".docx", ".doc"):
        texte_modele = extraire_texte_docx(chemin_modele)
    else:
        raise ValueError(f"Format modèle non supporté : {ext_modele}")

    if len(texte_modele.strip()) < 50:
        raise ValueError("Le document modèle ne contient pas assez de texte")

    # ── Analyse IA ─────────────────────────────────────────────────────────
    logger.info(f"Analyse IA du modèle '{nom_modele}' en cours...")
    analyse = analyser_modele_avec_ia(texte_modele, nom_fichier_modele)
    logger.info(
        f"Analyse terminée : type={analyse.get('type_detecte')} | "
        f"{len(analyse.get('champs_dynamiques', []))} champs détectés"
    )

    # ── Génération du script Python réutilisable ───────────────────────────
    logger.info("Génération du script Python en cours...")
    script_python = generer_script_avec_ia(analyse, texte_modele)

    chemin_script = str(settings.SCRIPTS_DIR / f"{modele_id}_script.py")
    with open(chemin_script, "w", encoding="utf-8") as f:
        f.write(f"# Script généré automatiquement par SmartIntern AI\n")
        f.write(f"# Modèle : {nom_modele}\n")
        f.write(f"# Type   : {analyse.get('type_detecte', type_document)}\n")
        f.write(f"# Généré le : {datetime.now().isoformat()}\n\n")
        f.write(script_python)
    logger.info(f"Script sauvegardé : {chemin_script}")

    return {
        "modele_id":           modele_id,
        "nom_modele":          nom_modele,
        "type_document":       analyse.get("type_detecte", type_document),
        "titre_document":      analyse.get("titre_document", nom_modele),
        "langue":              analyse.get("langue", "fr"),
        "chemin_modele":       chemin_modele,
        "chemin_header":       chemin_header,
        "chemin_footer":       chemin_footer,
        "chemin_script":       chemin_script,
        "champs_dynamiques":   analyse.get("champs_dynamiques", []),
        "sections":            analyse.get("sections", []),
        "mise_en_page":        analyse.get("mise_en_page", {}),
        "duree_validite_jours": duree_validite_jours,
        "version":             1,
        "date_creation":       datetime.now().isoformat(),
        "analyse_complete":    analyse,
    }


def obtenir_champs_modele(modele_id: str) -> dict:
    """Retourne les informations d'un modèle (existence du script)."""
    chemin_script = settings.SCRIPTS_DIR / f"{modele_id}_script.py"
    if not chemin_script.exists():
        return None
    return {
        "modele_id":    modele_id,
        "script_existe": True,
        "chemin_script": str(chemin_script),
    }
