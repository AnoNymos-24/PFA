package com.smartintern.backend.notification;

public enum NotificationEvent {

    // ── Authentification ───────────────────────────────────────────────────
    INSCRIPTION_OTP,
    RENVOI_OTP,
    RESET_PASSWORD_OTP,
    COMPTE_ACTIVE,
    CHANGEMENT_EMAIL,
    CHANGEMENT_MOT_DE_PASSE,

    // ── Stages ─────────────────────────────────────────────────────────────
    STAGE_CREE,
    STAGE_VALIDE,
    STAGE_REFUSE,
    ENCADRANT_AFFECTE,
    STATUT_STAGE_CHANGE,

    // ── Tâches ─────────────────────────────────────────────────────────────
    TACHE_CREEE,
    TACHE_MODIFIEE,
    TACHE_VALIDEE,
    TACHE_REJETEE,
    TACHE_EN_RETARD,

    // ── Rapports ───────────────────────────────────────────────────────────
    RAPPORT_DEPOSE,
    RAPPORT_VALIDE,
    RAPPORT_REFUSE,
    CORRECTION_DEMANDEE,

    // ── Administration ─────────────────────────────────────────────────────
    COMPTE_CREE,
    COMPTE_BLOQUE,
    COMPTE_DEBLOQUE,
    ROLE_CHANGE,

    // ── IA ─────────────────────────────────────────────────────────────────
    ALERTE_RISQUE,
    RECOMMANDATIONS_DISPONIBLES
}
