-- ============================================================
-- SmartIntern AI — Schéma complet v7 (feature/version7)
-- Migration initiale : DROP + CREATE
-- Généré depuis les entités JPA (22 tables)
-- MariaDB / MySQL — UTF-8 MB4
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ── Nettoyage complet ──────────────────────────────────────
DROP TABLE IF EXISTS rapports_stage;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS documents_generes;
DROP TABLE IF EXISTS documents;
DROP TABLE IF EXISTS modeles_documents;
DROP TABLE IF EXISTS types_documents;
DROP TABLE IF EXISTS competences;
DROP TABLE IF EXISTS langues;
DROP TABLE IF EXISTS formations;
DROP TABLE IF EXISTS experiences;
DROP TABLE IF EXISTS profils;
DROP TABLE IF EXISTS cv_standardises;
DROP TABLE IF EXISTS demandes_stage;
DROP TABLE IF EXISTS stages;
DROP TABLE IF EXISTS candidatures;
DROP TABLE IF EXISTS offres_stage;
DROP TABLE IF EXISTS encadrants_entreprise;
DROP TABLE IF EXISTS encadrants_academiques;
DROP TABLE IF EXISTS etudiants;
DROP TABLE IF EXISTS entreprises;
DROP TABLE IF EXISTS etablissements;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 1. USERS  (table racine — JOINED inheritance)
-- ============================================================
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    nom           VARCHAR(255) NOT NULL,                     -- lastName
    prenom        VARCHAR(255) NOT NULL,                     -- firstName
    email         VARCHAR(255) NOT NULL UNIQUE,
    password      VARCHAR(255) NOT NULL,
    photo_profil  VARCHAR(255),
    telephone     VARCHAR(50),
    role          VARCHAR(50)  NOT NULL DEFAULT 'ETUDIANT',  -- ETUDIANT | ENTREPRISE | ADMIN | ENCADRANT_ACADEMIQUE | ENCADRANT_ENTREPRISE
    statut        VARCHAR(30)  NOT NULL DEFAULT 'EN_ATTENTE',-- ACTIF | INACTIF | EN_ATTENTE | SUSPENDU
    otp_code      VARCHAR(20),
    otp_expiry    DATETIME,
    created_at    DATETIME,
    updated_at    DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 2. ETABLISSEMENTS
-- ============================================================
CREATE TABLE etablissements (
    id_etablissement  BIGINT       NOT NULL AUTO_INCREMENT,
    nom               VARCHAR(255) NOT NULL,
    adresse           VARCHAR(500),
    identifiant       VARCHAR(100) UNIQUE,
    logo_url          VARCHAR(500),
    PRIMARY KEY (id_etablissement)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 3. ENTREPRISES
-- ============================================================
CREATE TABLE entreprises (
    id_entreprise     BIGINT       NOT NULL AUTO_INCREMENT,
    nom               VARCHAR(255) NOT NULL,
    adresse           VARCHAR(500),
    identifiant       VARCHAR(100) UNIQUE,
    domaine_activite  VARCHAR(255),
    description       TEXT,
    logo              VARCHAR(500),
    site_web          VARCHAR(255),
    page_sociale_1    VARCHAR(255),
    page_sociale_2    VARCHAR(255),
    page_sociale_3    VARCHAR(255),
    PRIMARY KEY (id_entreprise)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. ETUDIANTS  (JOINED → users)
-- ============================================================
CREATE TABLE etudiants (
    user_id          BIGINT       NOT NULL,
    code_etudiant    VARCHAR(50)  UNIQUE,
    filiere          VARCHAR(255),
    classe           VARCHAR(100),
    cin              VARCHAR(20)  UNIQUE,
    date_naissance   DATE,
    nationalite      VARCHAR(100),
    cv_path          VARCHAR(500),
    cv_data_json     LONGTEXT,
    etablissement_id BIGINT,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_etudiant_user       FOREIGN KEY (user_id)          REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_etudiant_etablissement FOREIGN KEY (etablissement_id) REFERENCES etablissements(id_etablissement) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 5. ENCADRANTS ACADÉMIQUES  (JOINED → users)
-- ============================================================
CREATE TABLE encadrants_academiques (
    user_id          BIGINT NOT NULL,
    disponibilite    TINYINT(1),
    domaine          VARCHAR(255),
    etablissement_id BIGINT,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_encacad_user         FOREIGN KEY (user_id)          REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_encacad_etablissement FOREIGN KEY (etablissement_id) REFERENCES etablissements(id_etablissement) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 6. ENCADRANTS ENTREPRISE  (JOINED → users)
-- ============================================================
CREATE TABLE encadrants_entreprise (
    user_id       BIGINT NOT NULL,
    matricule     VARCHAR(100),
    disponibilite TINYINT(1),
    domaine       VARCHAR(255),
    departement   VARCHAR(255),
    entreprise_id BIGINT,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_encent_user      FOREIGN KEY (user_id)      REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_encent_entreprise FOREIGN KEY (entreprise_id) REFERENCES entreprises(id_entreprise) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 7. OFFRES DE STAGE
-- ============================================================
CREATE TABLE offres_stage (
    id_offre           BIGINT       NOT NULL AUTO_INCREMENT,
    titre              VARCHAR(255) NOT NULL,
    type_stage         VARCHAR(100),
    domaine            VARCHAR(255),
    duree_mois         INT          NOT NULL DEFAULT 0,
    statut             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',       -- ACTIVE | FERMEE | EXPIREE
    description        TEXT,
    theme              VARCHAR(255),
    localisation       VARCHAR(255),
    niveau_requis      VARCHAR(50),
    date_publication   DATE,
    date_expiration    DATE,
    nombre_places      INT,
    remuneration       TINYINT(1)   NOT NULL DEFAULT 0,
    statut_validation  VARCHAR(20)  NOT NULL DEFAULT 'EN_ATTENTE',   -- EN_ATTENTE | VALIDEE | REJETEE
    entreprise_id      BIGINT       NOT NULL,
    createur_id        BIGINT,
    created_at         DATETIME,
    PRIMARY KEY (id_offre),
    CONSTRAINT fk_offre_entreprise FOREIGN KEY (entreprise_id) REFERENCES entreprises(id_entreprise) ON DELETE CASCADE,
    CONSTRAINT fk_offre_createur   FOREIGN KEY (createur_id)   REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 8. CANDIDATURES
-- ============================================================
CREATE TABLE candidatures (
    id_candidature             BIGINT       NOT NULL AUTO_INCREMENT,
    statut                     VARCHAR(20)  NOT NULL DEFAULT 'EN_ATTENTE', -- EN_ATTENTE | ACCEPTEE | REFUSEE | ANNULEE
    statut_label               VARCHAR(255),
    date_candidature           DATE,
    date_reponse               DATE,
    lettre_motivation          TEXT,
    url_demande_stage          VARCHAR(500),
    score_matching             FLOAT,
    commentaire_entreprise     TEXT,
    commentaire_administration TEXT,
    etudiant_id                BIGINT NOT NULL,
    offre_id                   BIGINT NOT NULL,
    PRIMARY KEY (id_candidature),
    CONSTRAINT fk_cand_etudiant FOREIGN KEY (etudiant_id) REFERENCES etudiants(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_cand_offre    FOREIGN KEY (offre_id)    REFERENCES offres_stage(id_offre) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 9. STAGES
-- ============================================================
CREATE TABLE stages (
    id_stage                      BIGINT       NOT NULL AUTO_INCREMENT,
    date_debut                    DATE,
    date_fin                      DATE,
    duree_mois                    INT          NOT NULL DEFAULT 0,
    statut                        VARCHAR(20)  NOT NULL DEFAULT 'EN_COURS', -- EN_COURS | TERMINE | ABANDONNE | SUSPENDU
    sujet                         VARCHAR(500),
    mission                       TEXT,
    evaluation_finale             VARCHAR(255),
    convention_signee_entreprise  TINYINT(1)   NOT NULL DEFAULT 0,
    date_signature_entreprise     DATE,
    convention_signee_encadrant   TINYINT(1)   NOT NULL DEFAULT 0,
    date_signature_encadrant      DATE,
    candidature_id                BIGINT UNIQUE,
    etudiant_id                   BIGINT NOT NULL,
    entreprise_id                 BIGINT NOT NULL,
    encadrant_academique_id       BIGINT,
    encadrant_entreprise_id       BIGINT,
    PRIMARY KEY (id_stage),
    CONSTRAINT fk_stage_candidature         FOREIGN KEY (candidature_id)         REFERENCES candidatures(id_candidature) ON DELETE SET NULL,
    CONSTRAINT fk_stage_etudiant            FOREIGN KEY (etudiant_id)            REFERENCES etudiants(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_stage_entreprise          FOREIGN KEY (entreprise_id)          REFERENCES entreprises(id_entreprise) ON DELETE CASCADE,
    CONSTRAINT fk_stage_encadrant_acad      FOREIGN KEY (encadrant_academique_id)  REFERENCES encadrants_academiques(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_stage_encadrant_ent       FOREIGN KEY (encadrant_entreprise_id)  REFERENCES encadrants_entreprise(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 10. CV STANDARDISÉS
-- ============================================================
CREATE TABLE cv_standardises (
    id_cv               BIGINT       NOT NULL AUTO_INCREMENT,
    fichier_original    VARCHAR(500),
    fichier_standardise VARCHAR(500),
    score_completude    FLOAT,
    date_extraction     DATETIME,
    statut_extraction   VARCHAR(20)  NOT NULL DEFAULT 'EN_COURS', -- EN_COURS | EXTRAIT | ERREUR
    donnees_json_brutes LONGTEXT,
    niveau_qualite      VARCHAR(50),
    recommandations     TEXT,
    etudiant_id         BIGINT       NOT NULL UNIQUE,
    PRIMARY KEY (id_cv),
    CONSTRAINT fk_cv_etudiant FOREIGN KEY (etudiant_id) REFERENCES etudiants(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 11. PROFILS  (1:1 avec cv_standardises)
-- ============================================================
CREATE TABLE profils (
    id_profil             BIGINT       NOT NULL AUTO_INCREMENT,
    nom                   VARCHAR(255),
    prenom                VARCHAR(255),
    email                 VARCHAR(255),
    telephone             VARCHAR(50),
    adresse               VARCHAR(500),
    nationalite           VARCHAR(100),
    date_naissance        VARCHAR(50),
    titre_professionnel   VARCHAR(255),
    resume                TEXT,
    cv_standardise_id     BIGINT NOT NULL UNIQUE,
    PRIMARY KEY (id_profil),
    CONSTRAINT fk_profil_cv FOREIGN KEY (cv_standardise_id) REFERENCES cv_standardises(id_cv) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 12. EXPÉRIENCES
-- ============================================================
CREATE TABLE experiences (
    id_experience     BIGINT       NOT NULL AUTO_INCREMENT,
    poste             VARCHAR(255) NOT NULL,
    entreprise        VARCHAR(255) NOT NULL,
    periode           VARCHAR(100),
    date_debut        VARCHAR(50),
    date_fin          VARCHAR(50),
    description       TEXT,
    lieu              VARCHAR(255),
    ordre             INT          NOT NULL DEFAULT 0,
    cv_standardise_id BIGINT NOT NULL,
    PRIMARY KEY (id_experience),
    CONSTRAINT fk_exp_cv FOREIGN KEY (cv_standardise_id) REFERENCES cv_standardises(id_cv) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 13. FORMATIONS
-- ============================================================
CREATE TABLE formations (
    id_formation      BIGINT       NOT NULL AUTO_INCREMENT,
    diplome           VARCHAR(255) NOT NULL,
    etablissement     VARCHAR(255) NOT NULL,
    periode           VARCHAR(100),
    date_debut        VARCHAR(50),
    date_fin          VARCHAR(50),
    specialite        VARCHAR(255),
    lieu              VARCHAR(255),
    ordre             INT          NOT NULL DEFAULT 0,
    cv_standardise_id BIGINT NOT NULL,
    PRIMARY KEY (id_formation),
    CONSTRAINT fk_form_cv FOREIGN KEY (cv_standardise_id) REFERENCES cv_standardises(id_cv) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 14. COMPÉTENCES
-- ============================================================
CREATE TABLE competences (
    id_competence     BIGINT       NOT NULL AUTO_INCREMENT,
    nom               VARCHAR(255) NOT NULL,
    type              VARCHAR(30)  NOT NULL,    -- TECHNIQUE | SOFT_SKILL | OUTIL | AUTRE
    niveau            VARCHAR(100),
    annees_experience INT,
    cv_standardise_id BIGINT NOT NULL,
    PRIMARY KEY (id_competence),
    CONSTRAINT fk_comp_cv FOREIGN KEY (cv_standardise_id) REFERENCES cv_standardises(id_cv) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 15. LANGUES
-- ============================================================
CREATE TABLE langues (
    id_langue         BIGINT       NOT NULL AUTO_INCREMENT,
    langue            VARCHAR(100) NOT NULL,
    niveau            VARCHAR(50),
    cv_standardise_id BIGINT NOT NULL,
    PRIMARY KEY (id_langue),
    CONSTRAINT fk_lang_cv FOREIGN KEY (cv_standardise_id) REFERENCES cv_standardises(id_cv) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 16. TYPES DE DOCUMENTS
-- ============================================================
CREATE TABLE types_documents (
    id_type_document BIGINT       NOT NULL AUTO_INCREMENT,
    nom              VARCHAR(255) NOT NULL UNIQUE,
    description      TEXT,
    code             VARCHAR(100) NOT NULL UNIQUE,
    PRIMARY KEY (id_type_document)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 17. MODÈLES DE DOCUMENTS
-- ============================================================
CREATE TABLE modeles_documents (
    id_modele           BIGINT       NOT NULL AUTO_INCREMENT,
    nom                 VARCHAR(255) NOT NULL,
    titre_document      VARCHAR(255),
    chemin_modele       VARCHAR(500),
    chemin_header       VARCHAR(500),
    chemin_footer       VARCHAR(500),
    chemin_script       VARCHAR(500),
    champs_dynamiques   TEXT,
    analyse_ia          TEXT,
    duree_validite_jours INT         NOT NULL DEFAULT 365,
    version             INT          NOT NULL DEFAULT 1,
    statut              VARCHAR(20)  NOT NULL DEFAULT 'ACTIF',  -- ACTIF | ARCHIVE | BROUILLON
    date_creation       DATETIME,
    date_modification   DATETIME,
    date_expiration     DATE,
    signature_numerique TEXT,
    id_microservice     BIGINT,
    type_document_id    BIGINT NOT NULL,
    PRIMARY KEY (id_modele),
    CONSTRAINT fk_modele_type FOREIGN KEY (type_document_id) REFERENCES types_documents(id_type_document) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 18. DOCUMENTS  (instances de modèles, liées à un user)
-- ============================================================
CREATE TABLE documents (
    id_document       BIGINT       NOT NULL AUTO_INCREMENT,
    url_fichier       VARCHAR(500),
    doc_uuid          VARCHAR(100) UNIQUE,
    numero_version    INT          NOT NULL DEFAULT 1,
    type_document     VARCHAR(100),
    statut            VARCHAR(20)  NOT NULL DEFAULT 'VALIDE',  -- VALIDE | EXPIRE | REVOQUE
    created_at        DATETIME,
    updated_at        DATETIME,
    modele_document_id BIGINT NOT NULL,
    user_id           BIGINT NOT NULL,
    PRIMARY KEY (id_document),
    CONSTRAINT fk_doc_modele FOREIGN KEY (modele_document_id) REFERENCES modeles_documents(id_modele) ON DELETE RESTRICT,
    CONSTRAINT fk_doc_user   FOREIGN KEY (user_id)           REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 19. DOCUMENTS GÉNÉRÉS  (versions/historique d'un document)
-- ============================================================
CREATE TABLE documents_generes (
    id_document_genere BIGINT       NOT NULL AUTO_INCREMENT,
    doc_uuid           VARCHAR(100) NOT NULL UNIQUE,
    url_fichier        VARCHAR(500),
    url_verification   VARCHAR(500),
    chemin_fichier     VARCHAR(500),
    qr_code            TEXT,
    signature          VARCHAR(255) NOT NULL,
    created_at         DATETIME,
    expired_at         DATETIME,
    statut             VARCHAR(20)  NOT NULL DEFAULT 'VALIDE',  -- VALIDE | EXPIRE | REVOQUE | FALSIFIE
    numero_version     INT          NOT NULL DEFAULT 1,
    doc_uuid_original  VARCHAR(100),
    taille_octets      BIGINT,
    nom_etablissement  VARCHAR(255),
    document_id        BIGINT NOT NULL,
    PRIMARY KEY (id_document_genere),
    CONSTRAINT fk_docgen_document FOREIGN KEY (document_id) REFERENCES documents(id_document) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 20. DEMANDES DE STAGE
-- ============================================================
CREATE TABLE demandes_stage (
    id_demande        BIGINT       NOT NULL AUTO_INCREMENT,
    statut            VARCHAR(20)  NOT NULL DEFAULT 'EN_ATTENTE', -- EN_ATTENTE | ACCEPTEE | REFUSEE
    date_demande      DATE,
    lettre_motivation TEXT,
    type_demande      VARCHAR(100),
    etudiant_id       BIGINT NOT NULL,
    entreprise_id     BIGINT NOT NULL,
    PRIMARY KEY (id_demande),
    CONSTRAINT fk_demande_etudiant   FOREIGN KEY (etudiant_id)   REFERENCES etudiants(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_demande_entreprise FOREIGN KEY (entreprise_id) REFERENCES entreprises(id_entreprise) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 21. NOTIFICATIONS
-- ============================================================
CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    type       VARCHAR(100),
    lu         TINYINT(1)   NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 22. RAPPORTS DE STAGE
-- ============================================================
CREATE TABLE rapports_stage (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    stage_id             BIGINT       NOT NULL,
    etudiant_id          BIGINT       NOT NULL,
    type                 VARCHAR(20)  NOT NULL,  -- HEBDOMADAIRE | FINAL
    titre                VARCHAR(255),
    contenu              TEXT,
    semaine_debut        DATE,
    statut               VARCHAR(20)  NOT NULL DEFAULT 'BROUILLON', -- BROUILLON | SOUMIS | VALIDE | REJETE
    commentaire_encadrant TEXT,
    date_creation        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_soumission      DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT fk_rapport_stage    FOREIGN KEY (stage_id)    REFERENCES stages(id_stage)      ON DELETE CASCADE,
    CONSTRAINT fk_rapport_etudiant FOREIGN KEY (etudiant_id) REFERENCES etudiants(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- INDEX  (performances — colonnes fréquemment requêtées)
-- ============================================================
CREATE INDEX idx_offres_statut           ON offres_stage(statut);
CREATE INDEX idx_offres_statut_validation ON offres_stage(statut_validation);
CREATE INDEX idx_offres_domaine          ON offres_stage(domaine);
CREATE INDEX idx_cand_etudiant           ON candidatures(etudiant_id);
CREATE INDEX idx_cand_offre              ON candidatures(offre_id);
CREATE INDEX idx_cand_statut             ON candidatures(statut);
CREATE INDEX idx_stage_etudiant          ON stages(etudiant_id);
CREATE INDEX idx_stage_statut            ON stages(statut);
CREATE INDEX idx_notif_user              ON notifications(user_id);
CREATE INDEX idx_notif_lu                ON notifications(lu);
CREATE INDEX idx_rapport_stage           ON rapports_stage(stage_id);
CREATE INDEX idx_rapport_statut          ON rapports_stage(statut);
CREATE INDEX idx_docgen_uuid             ON documents_generes(doc_uuid);
CREATE INDEX idx_docgen_statut           ON documents_generes(statut);

-- ============================================================
-- DONNÉES INITIALES  (types de documents standards)
-- ============================================================
INSERT INTO types_documents (nom, description, code) VALUES
('Convention de stage',        'Document officiel tripartite (étudiant, établissement, entreprise)', 'convention_stage'),
('Attestation de stage',       'Confirme qu''un stage a bien eu lieu',                               'attestation_stage'),
('Lettre de recommandation',   'Évaluation du stagiaire par l''encadrant entreprise',                'lettre_recommandation'),
('Rapport de fin de stage',    'Rapport final rédigé par l''étudiant',                              'rapport_final'),
('Fiche d''évaluation',        'Grille d''évaluation remplie par l''encadrant',                     'fiche_evaluation'),
('Cahier des charges',         'Document décrivant les objectifs, contraintes et livrables du stage', 'cahier_des_charges');
