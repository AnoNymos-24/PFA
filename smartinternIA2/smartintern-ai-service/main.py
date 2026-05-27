"""
SmartIntern AI — Core Service  v2.1
=====================================
Microservice Python central : extraction CV, modèles et génération de documents.

Modules :
  • /cv          — Extraction et structuration de CVs (PDF/image/DOCX) via IA
  • /modeles     — Création et gestion des modèles de documents
  • /documents   — Génération, téléchargement et vérification de documents PDF

Providers IA (avec fallback automatique) :
  • NVIDIA NIM   — Provider primaire (NVIDIA_API_KEY)
  • OpenRouter   — Provider de secours (OPENROUTER_API_KEY)
"""

import logging
from datetime import datetime

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from config import settings
from app.core.ai_client import ai_client
from app.modules.cv_extraction.router      import router as cv_router
from app.modules.document_templates.router import router as templates_router
from app.modules.document_generation.router import router as generation_router
from app.modules.matching.router           import router as matching_router

# ── Logging ────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(name)s | %(message)s",
)
logger = logging.getLogger("smartintern")

# ── Application FastAPI ────────────────────────────────────────────────────
app = FastAPI(
    title=settings.SERVICE_NAME,
    description=(
        "Microservice Python central de SmartIntern AI.\n\n"
        "**Modules :**\n"
        "- 📄 **CV Extraction** — Extraction et structuration des CVs (PDF, image, DOCX) "
        "avec analyse IA multi-provider\n"
        "- 📋 **Modèles de documents** — Création de modèles avec analyse IA "
        "et génération de scripts Python\n"
        "- 📝 **Génération de documents** — Production PDF personnalisée, "
        "QR code de vérification, signature HMAC\n\n"
        "**Providers IA :** NVIDIA NIM (primaire) + OpenRouter (fallback automatique)"
    ),
    version=settings.SERVICE_VERSION,
    docs_url="/docs",
    redoc_url="/redoc",
)

# ── CORS ───────────────────────────────────────────────────────────────────
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Routers ────────────────────────────────────────────────────────────────
app.include_router(cv_router)
app.include_router(templates_router)
app.include_router(generation_router)
app.include_router(matching_router)


# ── Health check global ────────────────────────────────────────────────────
@app.get("/health", tags=["🔧 Système"], summary="État du service")
async def health_check():
    """Vérifie que le service est opérationnel et indique les providers IA actifs."""
    providers = []
    if settings.NVIDIA_API_KEY:
        providers.append({
            "nom":       "nvidia_nim",
            "role":      "primaire",
            "modele":    settings.NVIDIA_MODEL,
            "configure": True,
        })
    if settings.OPENROUTER_API_KEY:
        providers.append({
            "nom":       "openrouter",
            "role":      "fallback" if settings.NVIDIA_API_KEY else "primaire",
            "modele":    settings.OPENROUTER_MODEL,
            "configure": True,
        })

    return {
        "status":        "ok",
        "service":       settings.SERVICE_NAME,
        "version":       settings.SERVICE_VERSION,
        "ia_disponible": ai_client is not None,
        "fallback_actif": bool(ai_client and getattr(ai_client, "_has_fallback", False)),
        "providers":     providers,
        "modules": {
            "cv_extraction":        "actif",
            "document_templates":   "actif",
            "document_generation":  "actif",
            "matching_ia":          "actif",
        },
        "formats_cv_supportes": ["pdf", "pdf_ocr", "jpg", "png", "webp", "docx"],
        "timestamp": datetime.now().isoformat(),
    }


@app.get("/", tags=["🔧 Système"], include_in_schema=False)
async def root():
    return {
        "service": settings.SERVICE_NAME,
        "version": settings.SERVICE_VERSION,
        "docs":    "/docs",
    }


# ── Démarrage ──────────────────────────────────────────────────────────────
if __name__ == "__main__":
    logger.info(f"Démarrage de {settings.SERVICE_NAME} v{settings.SERVICE_VERSION}")
    uvicorn.run(
        "main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=True,
        log_level="info",
    )
