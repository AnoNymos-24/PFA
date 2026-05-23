"""
Client IA multi-provider avec basculement automatique (fallback).

Provider primaire  : NVIDIA NIM    (NVIDIA_API_KEY)
Provider de secours: OpenRouter    (OPENROUTER_API_KEY)

Basculement automatique si le primaire retourne :
  429 rate-limit · 402 crédits · 503/529 surcharge · context_length_exceeded
"""

import logging
from typing import Any, Optional

import openai
from openai import OpenAI

from config import settings

logger = logging.getLogger("smartintern.ai_client")

# ── Codes / mots-clés déclenchant le basculement ──────────────────────────

_FALLBACK_STATUS_CODES = {402, 429, 529}

_FALLBACK_KEYWORDS = {
    "rate_limit", "rate limit",
    "token", "context_length", "context length", "context window",
    "quota", "credit", "insufficient", "overloaded", "capacity",
    "exceeded", "too many", "too large",
}


def _doit_basculer(exc: Exception) -> bool:
    """Retourne True si cette erreur doit déclencher le basculement vers le fallback."""
    if isinstance(exc, openai.RateLimitError):
        return True
    if isinstance(exc, openai.APIStatusError):
        if exc.status_code in _FALLBACK_STATUS_CODES:
            return True
    msg = str(exc).lower()
    return any(kw in msg for kw in _FALLBACK_KEYWORDS)


# ══════════════════════════════════════════════════════════════════════════
# WRAPPER FALLBACK
# ══════════════════════════════════════════════════════════════════════════

class _FallbackCompletions:
    """
    Wrapper autour de deux clients OpenAI-compatible.
    Essaie le provider primaire ; bascule vers le fallback sur erreur de quota/token.
    """

    def __init__(
        self,
        primary_client: OpenAI,          primary_model: str,
        fallback_client: Optional[OpenAI], fallback_model: str,
        primary_max_tokens: int = 4096,
        fallback_max_tokens: int = 1500,
    ):
        self._primary             = primary_client
        self._primary_model       = primary_model
        self._primary_max_tokens  = primary_max_tokens
        self._fallback            = fallback_client
        self._fallback_model      = fallback_model
        self._fallback_max_tokens = fallback_max_tokens

    def _cap_tokens(self, kwargs: dict, max_tokens: int) -> dict:
        current = kwargs.get("max_tokens", max_tokens)
        if current > max_tokens:
            logger.info(f"max_tokens plafonné : {current} → {max_tokens}")
            return {**kwargs, "max_tokens": max_tokens}
        return kwargs

    def create(self, model: str = "", **kwargs) -> Any:
        kwargs.pop("model", None)
        primary_kwargs = self._cap_tokens(kwargs, self._primary_max_tokens)
        try:
            logger.info(
                f"IA → primaire ({self._primary_model}, "
                f"max_tokens={primary_kwargs.get('max_tokens')})"
            )
            return self._primary.chat.completions.create(
                model=self._primary_model, **primary_kwargs
            )
        except Exception as exc:
            if self._fallback and _doit_basculer(exc):
                fallback_kwargs = self._cap_tokens(kwargs, self._fallback_max_tokens)
                logger.warning(
                    f"Provider primaire échoué [{type(exc).__name__}] : {exc} "
                    f"→ fallback ({self._fallback_model}, "
                    f"max_tokens={fallback_kwargs.get('max_tokens')})"
                )
                return self._fallback.chat.completions.create(
                    model=self._fallback_model, **fallback_kwargs
                )
            raise


class _ChatNamespace:
    def __init__(self, completions: _FallbackCompletions):
        self.completions = completions


class FallbackLLMClient:
    """
    Drop-in replacement pour openai.OpenAI avec logique de fallback intégrée.
    Expose client.chat.completions.create(...) — même interface que OpenAI SDK.
    """

    def __init__(
        self,
        primary_client: OpenAI,              primary_model: str,
        fallback_client: Optional[OpenAI] = None, fallback_model: str = "",
        primary_max_tokens: int  = 4096,
        fallback_max_tokens: int = 1500,
    ):
        completions = _FallbackCompletions(
            primary_client,  primary_model,
            fallback_client, fallback_model,
            primary_max_tokens,
            fallback_max_tokens,
        )
        self.chat            = _ChatNamespace(completions)
        self._primary_model  = primary_model
        self._fallback_model = fallback_model
        self._has_fallback   = fallback_client is not None

    def __bool__(self) -> bool:
        return True


# ══════════════════════════════════════════════════════════════════════════
# CONSTRUCTION DU CLIENT GLOBAL
# ══════════════════════════════════════════════════════════════════════════

def construire_client() -> Optional[FallbackLLMClient]:
    """Construit le client IA avec fallback selon les clés disponibles."""
    primary_client  = None
    fallback_client = None

    if settings.NVIDIA_API_KEY:
        primary_client = OpenAI(
            api_key=settings.NVIDIA_API_KEY,
            base_url="https://integrate.api.nvidia.com/v1",
        )

    if settings.OPENROUTER_API_KEY:
        fallback_client = OpenAI(
            api_key=settings.OPENROUTER_API_KEY,
            base_url="https://openrouter.ai/api/v1",
        )

    if not settings.NVIDIA_API_KEY and not settings.OPENROUTER_API_KEY:
        logger.error(
            "Aucune clé API IA configurée — "
            "renseignez NVIDIA_API_KEY et/ou OPENROUTER_API_KEY dans .env"
        )
    elif not settings.NVIDIA_API_KEY:
        logger.warning("NVIDIA_API_KEY absente — OpenRouter utilisé seul (pas de fallback)")
    elif not settings.OPENROUTER_API_KEY:
        logger.warning("OPENROUTER_API_KEY absente — NVIDIA NIM utilisé seul (pas de fallback)")
    else:
        logger.info("Deux providers IA : NVIDIA NIM/Gemma (primaire) + OpenRouter (fallback)")

    if primary_client:
        return FallbackLLMClient(
            primary_client,  settings.NVIDIA_MODEL,
            fallback_client, settings.OPENROUTER_MODEL,
            primary_max_tokens=settings.NVIDIA_MAX_TOKENS,
            fallback_max_tokens=settings.OPENROUTER_MAX_TOKENS,
        )
    if fallback_client:
        return FallbackLLMClient(
            fallback_client, settings.OPENROUTER_MODEL,
            primary_max_tokens=settings.OPENROUTER_MAX_TOKENS,
        )
    return None


# Client global (singleton) utilisé par tous les modules
ai_client = construire_client()
