"""
Service d'extraction CV — analyse IA + scoring de complétude.

Pipeline complet :
  1. Détection du format (PDF / image / DOCX)
  2. Extraction du texte brut (extracteurs spécialisés)
  3. Analyse IA → CvStandardise structuré
  4. Calcul du score de complétude (100 pts / 6 sections)
"""

import json
import logging
import re
import tempfile
import os
from pathlib import Path

from fastapi import HTTPException

from app.core.ai_client import ai_client
from app.models.cv import (
    CvStandardise, CvResponse, ScoreResultat, ScoreSection, _dict_vers_str,
)
from app.utils.files import detecter_format, ACCEPTED_EXTENSIONS
from app.modules.cv_extraction.extractors.pdf   import extraire_depuis_pdf
from app.modules.cv_extraction.extractors.image import extraire_depuis_image
from app.modules.cv_extraction.extractors.docx  import extraire_depuis_docx

logger = logging.getLogger("smartintern.cv.service")


# ══════════════════════════════════════════════════════════════════════════
# PROMPT SYSTÈME — ANALYSE CV
# ══════════════════════════════════════════════════════════════════════════

SYSTEM_PROMPT = """Tu es un expert en analyse de CVs multilingues (français, anglais, arabe).
Extrait et structure les informations en JSON standardisé.

RÈGLES :
1. Texte peut être désordonné (OCR) — raisonne sur le sens
2. Distingue formations académiques VS certifications courtes
3. Compétences TECHNIQUES != SOFT SKILLS != OUTILS
4. Ne JAMAIS inventer une info absente → null
5. Dates : format lisible ("Janvier 2020 - Mars 2023")
6. Réponds UNIQUEMENT avec un JSON valide, SANS markdown, SANS texte avant ou après
7. CRITIQUE — certifications, projets, intérêts : UNIQUEMENT des tableaux de CHAÎNES DE TEXTE simples.
   Format attendu  : ["Titre (Organisme, Année)", "Autre titre"]
   FORMAT INTERDIT : [{"nom": "...", "date": "..."}, ...]  ← NE JAMAIS faire ça

Structure JSON exacte (respecter ces noms de clés) :
{
  "profil": {"nom": null, "prenom": null, "email": null, "telephone": null,
             "adresse": null, "nationalite": null, "date_naissance": null,
             "titre_professionnel": null, "resume": null},
  "experiences": [{"poste": "", "entreprise": "", "periode": null,
                   "date_debut": null, "date_fin": null, "description": null, "lieu": null}],
  "formations": [{"diplome": "", "etablissement": "", "periode": null,
                  "date_debut": null, "date_fin": null, "specialite": null, "lieu": null}],
  "competences": {"techniques": [], "soft_skills": [], "outils": [], "autres": []},
  "langues": [{"langue": "", "niveau": null}],
  "certifications": ["Exemple : Formation Python (Coursera, 2024)", "TOEIC 850 (2023)"],
  "projets": ["Exemple : Application mobile de gestion RH (React Native, 2024)"],
  "interets": ["Exemple : Photographie", "Musique"]
}"""


# ══════════════════════════════════════════════════════════════════════════
# ANALYSE IA
# ══════════════════════════════════════════════════════════════════════════

def nettoyer_json(texte: str) -> str:
    """Parsing JSON robuste par regex."""
    texte = texte.strip()
    m = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", texte)
    if m:
        return m.group(1).strip()
    m = re.search(r"(\{[\s\S]*\})", texte)
    if m:
        return m.group(1).strip()
    return texte


def analyser_cv_avec_ia(texte_brut: str) -> CvStandardise:
    """
    Envoie le texte brut du CV au LLM et retourne un CvStandardise structuré.

    Raises:
        HTTPException 503 si aucun provider IA configuré.
        HTTPException 500 si JSON invalide retourné.
        HTTPException 502 si le provider échoue.
    """
    if not ai_client:
        raise HTTPException(
            status_code=503,
            detail="Aucune clé API IA configurée. "
                   "Renseignez NVIDIA_API_KEY et/ou OPENROUTER_API_KEY dans .env",
        )
    try:
        response = ai_client.chat.completions.create(
            model="",               # ignoré — FallbackLLMClient utilise son propre modèle
            max_tokens=16384,
            temperature=0.10,
            top_p=1.00,
            extra_body={"reasoning_effort": "high"},
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user",
                 "content": f"CV à analyser :\n\n{texte_brut}\n\nRetourne le JSON."},
            ],
        )
        json_propre = nettoyer_json(response.choices[0].message.content)
        data = json.loads(json_propre)

        # Normalisation préventive : certains LLMs retournent des dicts au lieu de strings
        for _champ in ("certifications", "projets", "interets"):
            brut = data.get(_champ)
            if isinstance(brut, list):
                data[_champ] = [
                    _dict_vers_str(item) if not isinstance(item, str) else item
                    for item in brut if item is not None
                ]

        return CvStandardise(**data)

    except json.JSONDecodeError as e:
        logger.error(f"JSON parse erreur : {e}")
        raise HTTPException(status_code=500, detail=f"Erreur structuration CV : {e}")
    except Exception as e:
        logger.error(f"Erreur API IA (tous providers épuisés) : {e}")
        raise HTTPException(status_code=502, detail=f"Erreur API IA : {e}")


# ══════════════════════════════════════════════════════════════════════════
# SCORING DE COMPLÉTUDE — 100 points / 6 sections
#
# Barème :
#   Profil de base : 25 pts   Formations  : 20 pts
#   Expériences    : 20 pts   Compétences : 20 pts
#   Langues        :  5 pts   Bonus       : 10 pts
# ══════════════════════════════════════════════════════════════════════════

def calculer_score(cv: CvStandardise) -> ScoreResultat:
    """Calcule le score de complétude d'un CV structuré."""
    details: dict = {}
    recommandations: list[str] = []
    sections_completes = 0
    TOTAL = 6

    # ── 1. Profil (25 pts) ─────────────────────────────────────────────────
    p, s, mx, miss = cv.profil, 0.0, 25.0, []
    if p.nom and p.prenom:
        s += 5
    else:
        miss.append("Nom et prénom")
        recommandations.append("Indiquez votre nom complet en haut du CV")
    if p.email:
        s += 4
    else:
        miss.append("Email")
        recommandations.append("Ajoutez votre adresse email professionnelle")
    if p.telephone:
        s += 3
    else:
        miss.append("Téléphone")
        recommandations.append("Ajoutez votre numéro de téléphone")
    if p.titre_professionnel:
        s += 5
    else:
        miss.append("Titre professionnel")
        recommandations.append("Ajoutez un titre professionnel (ex : Étudiant en Génie Logiciel)")
    if p.resume and len(p.resume) >= 50:
        s += 5
    elif p.resume:
        s += 2; miss.append("Résumé trop court")
        recommandations.append("Développez votre résumé (minimum 50 caractères)")
    else:
        miss.append("Résumé / objectif professionnel")
        recommandations.append("Ajoutez un résumé décrivant votre profil et objectifs")
    if p.adresse:
        s += 3
    else:
        miss.append("Localisation")
        recommandations.append("Indiquez votre ville ou région")

    ok = len(miss) == 0
    if ok: sections_completes += 1
    details["profil"] = ScoreSection(
        score=s, max=mx, pourcentage=round(s/mx*100, 1),
        manquant=miss, complet=ok,
    ).model_dump(by_alias=True)

    # ── 2. Formations (20 pts) ─────────────────────────────────────────────
    s, mx, miss = 0.0, 20.0, []
    if cv.formations:
        s += 10
        f = cv.formations[0]
        if f.specialite:
            s += 3
        else:
            miss.append("Spécialité")
            recommandations.append("Précisez votre spécialité pour chaque formation")
        if f.date_debut or f.periode:
            s += 3
        else:
            miss.append("Dates de formation")
            recommandations.append("Ajoutez les dates de vos formations")
        if f.lieu: s += 2
        else:      miss.append("Lieu")
        if len(cv.formations) >= 2: s += 2
    else:
        miss.append("Aucune formation")
        recommandations.append("Ajoutez vos formations (diplôme, établissement, dates, spécialité)")

    ok = s >= mx * 0.8
    if ok: sections_completes += 1
    details["formations"] = ScoreSection(
        score=s, max=mx, pourcentage=round(s/mx*100, 1),
        manquant=miss, complet=ok,
    ).model_dump(by_alias=True)

    # ── 3. Expériences (20 pts) ────────────────────────────────────────────
    s, mx, miss = 0.0, 20.0, []
    if cv.experiences:
        s += 8
        exp = cv.experiences[0]
        desc_ok = [e for e in cv.experiences if e.description and len(e.description) >= 30]
        if desc_ok:
            s += 6
        else:
            miss.append("Descriptions insuffisantes")
            recommandations.append("Décrivez vos missions et réalisations (minimum 30 caractères)")
        if exp.date_debut or exp.periode:
            s += 3
        else:
            miss.append("Dates des expériences")
            recommandations.append("Ajoutez les dates de chaque expérience")
        if exp.lieu: s += 1
        if len(cv.experiences) >= 2: s += 2
    else:
        miss.append("Aucune expérience")
        recommandations.append(
            "Ajoutez vos stages, projets académiques ou expériences professionnelles"
        )

    ok = s >= mx * 0.7
    if ok: sections_completes += 1
    details["experiences"] = ScoreSection(
        score=s, max=mx, pourcentage=round(s/mx*100, 1),
        manquant=miss, complet=ok,
    ).model_dump(by_alias=True)

    # ── 4. Compétences (20 pts) ────────────────────────────────────────────
    s, mx, miss = 0.0, 20.0, []
    c = cv.competences
    nb_t = len(c.techniques)
    if nb_t >= 5:   s += 8
    elif nb_t >= 3: s += 5
    elif nb_t >= 1:
        s += 2; miss.append(f"Peu de compétences techniques ({nb_t})")
        recommandations.append("Listez au moins 5 compétences techniques (langages, frameworks, BDD)")
    else:
        miss.append("Aucune compétence technique")
        recommandations.append("Ajoutez vos compétences techniques : langages, frameworks, technologies")

    nb_o = len(c.outils)
    if nb_o >= 3:   s += 5
    elif nb_o >= 1:
        s += 3; miss.append("Peu d'outils renseignés")
        recommandations.append("Ajoutez les outils que vous maîtrisez (IDE, Git, Docker, etc.)")
    else:
        miss.append("Aucun outil renseigné")
        recommandations.append("Listez vos outils et logiciels")

    nb_s = len(c.soft_skills)
    if nb_s >= 3:   s += 5
    elif nb_s >= 1:
        s += 3; miss.append("Peu de soft skills")
        recommandations.append("Ajoutez vos compétences comportementales (équipe, communication)")
    else:
        miss.append("Aucun soft skill")
        recommandations.append("Ajoutez des compétences transversales : autonomie, rigueur, esprit d'équipe")

    if nb_t + nb_o + nb_s >= 10: s += 2

    ok = s >= mx * 0.7
    if ok: sections_completes += 1
    details["competences"] = ScoreSection(
        score=s, max=mx, pourcentage=round(s/mx*100, 1),
        manquant=miss, complet=ok,
    ).model_dump(by_alias=True)

    # ── 5. Langues (5 pts) ────────────────────────────────────────────────
    s, mx, miss = 0.0, 5.0, []
    if cv.langues:
        s += 3
        if any(l.niveau for l in cv.langues):
            s += 2
        else:
            miss.append("Niveau de langue non précisé")
            recommandations.append(
                "Précisez votre niveau pour chaque langue "
                "(Débutant / Intermédiaire / Avancé / Natif)"
            )
    else:
        miss.append("Aucune langue")
        recommandations.append("Ajoutez les langues que vous parlez avec votre niveau")

    ok = s >= mx * 0.8
    if ok: sections_completes += 1
    details["langues"] = ScoreSection(
        score=s, max=mx, pourcentage=round(s/mx*100, 1),
        manquant=miss, complet=ok,
    ).model_dump(by_alias=True)

    # ── 6. Bonus (10 pts) ─────────────────────────────────────────────────
    s, mx, miss = 0.0, 10.0, []
    if cv.certifications:
        s += 3
    else:
        miss.append("Aucune certification")
        recommandations.append("Ajoutez vos certifications (MOOC, diplômes complémentaires)")
    if cv.projets:
        s += 4
    else:
        miss.append("Aucun projet")
        recommandations.append("Décrivez vos projets personnels ou académiques (GitHub, hackathons)")
    if cv.interets:
        s += 3
    else:
        miss.append("Aucun intérêt")
        recommandations.append("Ajoutez quelques centres d'intérêt pour humaniser votre candidature")

    ok = s >= mx * 0.7
    if ok: sections_completes += 1
    details["bonus"] = ScoreSection(
        score=s, max=mx, pourcentage=round(s/mx*100, 1),
        manquant=miss, complet=ok,
    ).model_dump(by_alias=True)

    # ── Score global ───────────────────────────────────────────────────────
    score_total = sum(details[k]["score"] for k in details)
    max_total   = sum(details[k]["max"]   for k in details)
    sur_100 = round(score_total / max_total * 100, 1)
    niveau  = (
        "EXCELLENT"    if sur_100 >= 85 else
        "BON"          if sur_100 >= 65 else
        "MOYEN"        if sur_100 >= 40 else
        "INSUFFISANT"
    )

    return ScoreResultat(
        score_global=round(score_total, 1),
        score_sur_100=sur_100,
        niveau=niveau,
        details=details,
        recommandations=recommandations,
        sections_completes=sections_completes,
        sections_totales=TOTAL,
    )


# ══════════════════════════════════════════════════════════════════════════
# ORCHESTRATEUR PRINCIPAL
# ══════════════════════════════════════════════════════════════════════════

async def traiter_cv(file, include_ai: bool = True) -> dict:
    """
    Pipeline complet : upload → extraction → IA → score → réponse.

    Args:
        file       : UploadFile FastAPI
        include_ai : False = texte brut uniquement (pas d'analyse IA)

    Returns:
        dict sérialisé (camelCase) compatible Java/Jackson.
    """
    filename     = file.filename or ""
    content_type = file.content_type or ""

    format_fichier = detecter_format(filename, content_type)
    if not format_fichier or format_fichier == "auto":
        ext = Path(filename).suffix.lower()
        if ext not in ACCEPTED_EXTENSIONS:
            raise HTTPException(
                status_code=400,
                detail="Format non supporté. Acceptés : PDF, JPG, PNG, WEBP, DOCX, DOC",
            )
        format_fichier = detecter_format(filename, "")

    contenu = await file.read()
    if len(contenu) > 10 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="Fichier trop volumineux (max 10 MB)")

    logger.info(f"Traitement : {filename} | format={format_fichier} | {len(contenu)} bytes")

    ext = Path(filename).suffix or (".pdf" if format_fichier == "pdf" else ".tmp")
    with tempfile.NamedTemporaryFile(delete=False, suffix=ext) as tmp:
        tmp.write(contenu)
        chemin_tmp = tmp.name

    try:
        # ── Extraction texte ───────────────────────────────────────────────
        if format_fichier == "pdf":
            texte_brut, nb_pages, methode = extraire_depuis_pdf(chemin_tmp)
        elif format_fichier == "image":
            texte_brut, nb_pages, methode = extraire_depuis_image(chemin_tmp)
        elif format_fichier == "docx":
            texte_brut, nb_pages, methode = extraire_depuis_docx(chemin_tmp)
        else:
            raise HTTPException(status_code=400, detail=f"Format non supporté : {format_fichier}")

        if len(texte_brut.strip()) < 50:
            return CvResponse(
                success=False, nb_pages=nb_pages,
                format_detecte=format_fichier, methode_extraction=methode,
                message="Impossible d'extraire le texte (fichier corrompu ou protégé)",
            ).model_dump(by_alias=True)

        if not include_ai:
            return CvResponse(
                success=True,
                texte_brut=texte_brut, nb_pages=nb_pages,
                format_detecte=format_fichier, methode_extraction=methode,
                message="Texte extrait avec succès",
            ).model_dump(by_alias=True)

        # ── Analyse IA + scoring ───────────────────────────────────────────
        cv    = analyser_cv_avec_ia(texte_brut)
        score = calculer_score(cv)
        cv.score_completude = score.score_sur_100

        logger.info(
            f"CV traité : {nb_pages}p | {methode} | "
            f"score={score.score_sur_100}% ({score.niveau})"
        )

        return CvResponse(
            success=True,
            cv_standardise=cv,
            texte_brut=texte_brut,
            nb_pages=nb_pages,
            format_detecte=format_fichier,
            methode_extraction=methode,
            message="CV extrait, structuré et scoré avec succès",
            score=score,
        ).model_dump(by_alias=True)

    finally:
        if os.path.exists(chemin_tmp):
            os.unlink(chemin_tmp)
