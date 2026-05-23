"""
SmartIntern AI — SmartDoc Service
Point d'entrée FastAPI du microservice documentaire.

Modules :
  • /api/cv          — Extraction et structuration de CVs (IA)
  • /api/templates   — Gestion des modèles de documents
  • /api/documents   — Génération de documents (DOCX / PDF)
"""

import logging
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.modules.cv_extraction.router      import router as cv_router
from app.modules.document_templates.router import router as templates_router
from app.modules.document_generation.router import router as documents_router

# ── Logging ────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(levelname)-8s │ %(name)s │ %(message)s",
)
logger = logging.getLogger("smartdoc")

# ── Application FastAPI ────────────────────────────────────────────────────
app = FastAPI(
    title=settings.SERVICE_NAME,
    description=(
        "Microservice documentaire de SmartIntern AI.\n\n"
        "**Fonctionnalités :**\n"
        "- 📄 Extraction et structuration de CVs (PDF, image, DOCX) via Claude AI\n"
        "- 📋 Gestion de modèles de documents d'entreprise (convention, rapport, évaluation…)\n"
        "- 📝 Génération automatique de documents Word / PDF à la demande"
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
app.include_router(cv_router,        prefix="/api")
app.include_router(templates_router, prefix="/api")
app.include_router(documents_router, prefix="/api")


# ── Health check global ────────────────────────────────────────────────────
@app.get("/health", tags=["🔧 Système"], summary="État du service")
async def health_check():
    """Vérifie que le service est opérationnel."""
    from app.modules.document_generation.generators import pdf_disponible
    return {
        "status":  "ok",
        "service": "smartdoc-service",
        "version": settings.SERVICE_VERSION,
        "modules": {
            "cv_extraction":       "actif",
            "document_templates":  "actif",
            "document_generation": "actif",
        },
        "capacites": {
            "ocr_pdf":  True,
            "ocr_image": True,
            "extraction_ia": bool(settings.ANTHROPIC_API_KEY),
            "generation_docx": True,
            "generation_pdf":  pdf_disponible(),
        },
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
