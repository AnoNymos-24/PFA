-- ============================================================
-- SmartIntern AI — Données initiales (data.sql)
-- Exécuté au démarrage APRÈS Hibernate (defer-datasource-initialization=true)
-- Utilise INSERT IGNORE pour être idempotent (re-exécutable sans erreur)
-- ============================================================

-- ── Migration structurelle idempotente ───────────────────────────────────
-- Convertit la colonne logs_activite.action de ENUM → VARCHAR(100)
-- Compatible MySQL et MariaDB (sans IF EXISTS sur ALTER TABLE MODIFY)
ALTER TABLE logs_activite MODIFY COLUMN action VARCHAR(100) NOT NULL;

-- ── Données de test — Mission C : workflow CDC → Sprint → Tâche ──────────
-- Idempotent via INSERT IGNORE + conditions NOT EXISTS.
-- Ces données permettent le test du flux complet sans passer par l'inscription/candidature.

-- 1. Entreprise de test
INSERT IGNORE INTO entreprises (id_entreprise, nom, domaine_activite)
VALUES (1, 'TechTest SARL', 'Informatique');

-- 2. Subclass rows JOINED inheritance : etudiants + encadrants_entreprise
--    (AuthService.register() crée uniquement la ligne users, pas les sous-tables)
INSERT IGNORE INTO etudiants (user_id)
SELECT id FROM users WHERE email = 'etudiant@test.tn' LIMIT 1;

INSERT IGNORE INTO encadrants_entreprise (user_id, entreprise_id)
SELECT u.id, 1 FROM users u
WHERE u.email = 'encadrant@test.tn'
AND NOT EXISTS (SELECT 1 FROM encadrants_entreprise WHERE user_id = u.id);

-- 3. Stage test (id=1) liant etudiant + entreprise + encadrant
INSERT IGNORE INTO stages (id_stage, etudiant_id, entreprise_id, encadrant_entreprise_id,
                            statut, statut_evaluation, duree_mois, date_debut, date_fin)
SELECT 1,
       (SELECT id FROM users WHERE email = 'etudiant@test.tn' LIMIT 1),
       1,
       (SELECT id FROM users WHERE email = 'encadrant@test.tn' LIMIT 1),
       'EN_COURS',
       'NON_DEMARREE',
       6,
       CURDATE(),
       DATE_ADD(CURDATE(), INTERVAL 6 MONTH)
WHERE NOT EXISTS (SELECT 1 FROM stages WHERE id_stage = 1);

-- ── Types de documents standards ──────────────────────────────────────────
INSERT IGNORE INTO types_documents (nom, description, code) VALUES
    ('Convention de stage',
     'Document officiel tripartite (étudiant, établissement, entreprise)',
     'convention_stage'),
    ('Attestation de stage',
     'Confirme qu''un stage a bien eu lieu',
     'attestation_stage'),
    ('Lettre de recommandation',
     'Évaluation du stagiaire par l''encadrant entreprise',
     'lettre_recommandation'),
    ('Rapport de fin de stage',
     'Rapport final rédigé par l''étudiant',
     'rapport_final'),
    ('Fiche d''évaluation',
     'Grille d''évaluation remplie par l''encadrant',
     'fiche_evaluation'),
    ('Cahier des charges',
     'Document décrivant les objectifs, contraintes et livrables attendus du stage',
     'cahier_des_charges');

-- ── AD-04 : Table resultats_risque (fallback si ddl-auto désactivé) ────────
-- Hibernate (ddl-auto=update) crée la table automatiquement via l'entité ResultatRisque.
-- Ce bloc garantit sa présence si le DDL automatique est désactivé en production.
CREATE TABLE IF NOT EXISTS resultats_risque (
    id                    BIGINT        AUTO_INCREMENT PRIMARY KEY,
    stage_id              BIGINT        NOT NULL,
    score_engagement      INT           NOT NULL DEFAULT 0,
    niveau_risque         VARCHAR(20)   NOT NULL,
    alertes_json          TEXT,
    recommandations_json  TEXT,
    analyse_json          TEXT,
    analyse_le            DATETIME,
    INDEX idx_resultat_risque_stage   (stage_id),
    INDEX idx_resultat_risque_niveau  (niveau_risque),
    INDEX idx_resultat_risque_score   (score_engagement),
    CONSTRAINT fk_resultat_risque_stage
        FOREIGN KEY (stage_id) REFERENCES stages (id_stage)
        ON DELETE CASCADE
);

-- ── U-06/U-07/U-08 : Messagerie temps réel ────────────────────────────────
-- Conversations
CREATE TABLE IF NOT EXISTS conversations (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    type                VARCHAR(10)  NOT NULL,
    sujet               VARCHAR(255),
    statut              VARCHAR(15)  NOT NULL DEFAULT 'ACTIVE',
    stage_id            BIGINT,
    entreprise_id       BIGINT,
    cree_par_id         BIGINT NOT NULL,
    cree_le             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dernier_message_le  DATETIME,
    CONSTRAINT fk_conv_stage      FOREIGN KEY (stage_id)      REFERENCES stages(id_stage)          ON DELETE SET NULL,
    CONSTRAINT fk_conv_entreprise FOREIGN KEY (entreprise_id) REFERENCES entreprises(id_entreprise) ON DELETE SET NULL,
    CONSTRAINT fk_conv_cree_par   FOREIGN KEY (cree_par_id)   REFERENCES users(id)
);

-- Participants
CREATE TABLE IF NOT EXISTS participants_conversation (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id  BIGINT NOT NULL,
    utilisateur_id   BIGINT NOT NULL,
    a_rejoint        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    derniere_lecture DATETIME,
    est_actif        BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY uk_conv_user (conversation_id, utilisateur_id),
    CONSTRAINT fk_part_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_part_user FOREIGN KEY (utilisateur_id)  REFERENCES users(id)
);

-- Messages
CREATE TABLE IF NOT EXISTS messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    expediteur_id   BIGINT NOT NULL,
    contenu         TEXT NOT NULL,
    envoie_le       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lu              BOOLEAN NOT NULL DEFAULT FALSE,
    type            VARCHAR(10) NOT NULL DEFAULT 'TEXTE',
    CONSTRAINT fk_msg_conv     FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_expediteur FOREIGN KEY (expediteur_id) REFERENCES users(id)
);

-- Index de performance messagerie
CREATE INDEX IF NOT EXISTS idx_messages_conv_date  ON messages(conversation_id, envoie_le DESC);
CREATE INDEX IF NOT EXISTS idx_messages_nonlus     ON messages(conversation_id, lu, expediteur_id);
CREATE INDEX IF NOT EXISTS idx_participants_user   ON participants_conversation(utilisateur_id, est_actif);
CREATE INDEX IF NOT EXISTS idx_conv_dernier_msg    ON conversations(dernier_message_le DESC);
