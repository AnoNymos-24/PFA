# SmartIntern AI — Documentation API (Sprints 1 & 2)

**Base URL :** `http://localhost:8081`  
**Auth :** `Authorization: Bearer <token>` sur tous les endpoints sauf ceux marqués 🔓  
**Format :** JSON (`Content-Type: application/json`)

---

## AUTHENTIFICATION

### 🔓 POST `/api/auth/register`
Créer un compte.

**Body :**
```json
{
  "firstName": "string",
  "lastName": "string",
  "email": "string",
  "password": "string",
  "role": "ETUDIANT | ENTREPRISE | ENCADRANT_ACADEMIQUE | ENCADRANT_ENTREPRISE",
  "telephone": "string"
}
```
**Réponse 200 :**
```json
{
  "token": "PENDING_VERIFICATION",
  "type": "OTP_REQUIRED",
  "id": 1,
  "firstName": "string",
  "lastName": "string",
  "email": "string",
  "role": "ETUDIANT",
  "statut": "EN_ATTENTE"
}
```
> Un OTP est envoyé par email. Appeler `/api/auth/verify-otp` pour activer le compte.

---

### 🔓 POST `/api/auth/verify-otp`
Vérifier l'OTP reçu par email (activation du compte).

**Body :**
```json
{ "email": "string", "code": "1234" }
```
**Réponse 200 :** même format que `/register` avec token JWT valide.

---

### 🔓 POST `/api/auth/resend-otp`
Renvoyer le code OTP.

**Body :** `{ "email": "string" }`  
**Réponse 200 :** `{ "message": "Code renvoyé avec succès" }`

---

### 🔓 POST `/api/auth/login`
Connexion.

**Body :**
```json
{ "email": "string", "password": "string" }
```
**Réponse 200 :**
```json
{
  "token": "eyJ...",
  "type": "Bearer",
  "id": 1,
  "firstName": "string",
  "lastName": "string",
  "email": "string",
  "role": "ETUDIANT",
  "statut": "ACTIF"
}
```

---

### POST `/api/auth/logout`
Déconnexion sécurisée (invalide le token côté serveur).

**Headers :** `Authorization: Bearer <token>`  
**Body :** aucun  
**Réponse 200 :** `{ "message": "Déconnexion réussie" }`

---

### 🔓 POST `/api/auth/forgot-password`
Demander un code de réinitialisation.

**Body :** `{ "email": "string" }`  
**Réponse 200 :** `{ "message": "Si cet email existe, un code vous a été envoyé" }`

---

### 🔓 POST `/api/auth/reset-password`
Réinitialiser le mot de passe.

**Body :**
```json
{ "email": "string", "code": "1234", "newPassword": "string" }
```
**Réponse 200 :** `{ "message": "Mot de passe réinitialisé avec succès" }`

---

## NOTIFICATIONS
> Accessible par tous les rôles authentifiés.

### GET `/api/notifications`
Récupérer mes notifications (triées par date décroissante).

**Réponse 200 :**
```json
[
  {
    "id": 1,
    "message": "string",
    "type": "string",
    "lu": false,
    "createdAt": "2025-01-01T10:00:00"
  }
]
```

---

### GET `/api/notifications/non-lues`
Nombre de notifications non lues.

**Réponse 200 :** `{ "nonLues": 3 }`

---

### PATCH `/api/notifications/{id}/lire`
Marquer une notification comme lue.

**Réponse 200 :** objet `NotificationResponse` mis à jour (`lu: true`).

---

### PATCH `/api/notifications/lire-tout`
Marquer toutes les notifications comme lues.

**Réponse 200 :** `{ "message": "Toutes les notifications marquées comme lues", "count": 5 }`

---

## OFFRES DE STAGE

### GET `/api/etudiant/offres` 🎓
Lister les offres actives et validées (paginé).

**Query params :**
- `page` (défaut: 0), `size` (défaut: 20), `sortBy` (défaut: datePublication), `sortDir` (asc/desc)
- `size=0` → retourne toute la liste sans pagination

**Réponse 200 :** Page ou liste d'`OffreStageResponse` (voir structure ci-dessous).

---

### GET `/api/etudiant/offres/{id}` 🎓
Consulter le détail d'une offre.

**Réponse 200 :**
```json
{
  "id": 1,
  "titre": "string",
  "typeStage": "string",
  "domaine": "string",
  "theme": "string",
  "description": "string",
  "localisation": "string",
  "niveauRequis": "string",
  "dureeMois": 6,
  "statut": "ACTIVE",
  "statutValidation": "VALIDEE",
  "datePublication": "2025-01-01",
  "dateExpiration": "2025-06-01",
  "nombrePlaces": 2,
  "remuneration": true,
  "entrepriseId": 1,
  "entrepriseNom": "string",
  "createdAt": "2025-01-01T10:00:00"
}
```

---

### GET `/api/etudiant/offres/search` 🎓
Filtrer les offres.

**Query params (tous optionnels) :**
- `domaine`, `localisation`, `typeStage`, `niveauRequis`

**Réponse 200 :** liste d'`OffreStageResponse`.

---

### POST `/api/entreprise/offres` 🏢
Publier une offre de stage.

**Body :**
```json
{
  "titre": "string",
  "description": "string",
  "typeStage": "string",
  "domaine": "string",
  "theme": "string",
  "localisation": "string",
  "niveauRequis": "string",
  "dureeMois": 6,
  "dateExpiration": "2025-06-01",
  "nombrePlaces": 2,
  "remuneration": false
}
```
**Réponse 200 :** `OffreStageResponse`

---

### PUT `/api/entreprise/offres/{id}` 🏢
Modifier une offre (repart en validation admin).

**Body :** même que POST  
**Réponse 200 :** `OffreStageResponse`

---

### DELETE `/api/entreprise/offres/{id}` 🏢
Supprimer une offre.

**Réponse 200 :** `{ "message": "Offre supprimée" }`

---

### GET `/api/admin/offres/en-attente` 👑
Lister les offres en attente de validation.

**Réponse 200 :** liste d'`OffreStageResponse`.

---

### PATCH `/api/admin/offres/{id}/valider` 👑
Valider ou rejeter une offre.

**Body :** `{ "approuve": true }`  
**Réponse 200 :** `OffreStageResponse` mis à jour.

---

## CANDIDATURES

### POST `/api/etudiant/candidatures` 🎓
Postuler à une offre.

**Body :**
```json
{ "offreId": 1, "lettreMotivation": "string" }
```
**Réponse 200 :**
```json
{
  "id": 1,
  "statut": "EN_ATTENTE",
  "statutLabel": "En attente de réponse",
  "dateCandidature": "2025-01-01",
  "dateReponse": null,
  "lettreMotivation": "string",
  "urlDemandeStage": null,
  "scoreMatching": null,
  "commentaireEntreprise": null,
  "offreId": 1,
  "offreTitre": "string",
  "entrepriseNom": "string",
  "etudiantId": 1,
  "etudiantNom": "string"
}
```

---

### GET `/api/etudiant/candidatures` 🎓
Suivre mes candidatures.

**Réponse 200 :** liste de `CandidatureResponse`.

---

### GET `/api/entreprise/offres/{offreId}/candidatures` 🏢
Voir les candidatures reçues pour une offre.

**Réponse 200 :** liste de `CandidatureResponse`.

---

### PATCH `/api/entreprise/candidatures/{id}/decision` 🏢
Accepter ou refuser une candidature.

**Body :**
```json
{ "statut": "ACCEPTEE | REFUSEE", "commentaire": "string" }
```
**Réponse 200 :** `CandidatureResponse` mis à jour.

---

### GET `/api/entreprise/candidatures/{id}/etudiant` 🏢
Voir le profil complet d'un candidat + données CV.

**Réponse 200 :**
```json
{
  "etudiantId": 1,
  "firstName": "string",
  "lastName": "string",
  "email": "string",
  "telephone": "string",
  "filiere": "string",
  "classe": "string",
  "dateNaissance": "2000-01-01",
  "nationalite": "string",
  "cvStatutExtraction": "EXTRAIT | EN_COURS | ERREUR",
  "cvScoreCompletude": 85.5,
  "cvNiveauQualite": "BON",
  "cvDonneesJson": "{...}",
  "candidatureId": 1,
  "statutCandidature": "EN_ATTENTE",
  "lettreMotivation": "string",
  "dateCandidature": "2025-01-01"
}
```

---

## CV ÉTUDIANT

### POST `/api/etudiant/cv` 🎓
Téléverser un CV (PDF ou image). Extraction asynchrone.

**Body :** `multipart/form-data` — champ `file`  
**Réponse 202 :** `{ "message": "CV reçu, extraction en cours..." }`

---

### GET `/api/etudiant/cv` 🎓
Récupérer les données du CV extrait.

**Réponse 200 :**
```json
{
  "hasCv": true,
  "filename": "cv.pdf",
  "statutExtraction": "EXTRAIT",
  "scoreCompletude": 85.5,
  "cvData": { ... }
}
```

---

### GET `/api/etudiant/cv/statut` 🎓
Polling du statut d'extraction (à appeler toutes les 2-3 secondes).

**Réponse 200 :** `{ "statut": "EN_COURS | EXTRAIT | ERREUR", "pret": false }`

---

## STAGES

### GET `/api/etudiant/stages` 🎓
Mes stages en cours / terminés.

**Réponse 200 :** liste de `StageResponse` (voir structure ci-dessous).

---

### GET `/api/encadrant-academique/stages` 🎓👨‍🏫
Stages de mes étudiants suivis.

**Réponse 200 :** liste de `StageResponse`.

---

### GET `/api/encadrant-entreprise/stages` 🏭
Stages que j'encadre.

**Réponse 200 :** liste de `StageResponse`.

---

### POST `/api/admin/stages/depuis-candidature/{candidatureId}` 👑
Créer un stage à partir d'une candidature acceptée.

**Réponse 200 :** `StageResponse`.

---

### PATCH `/api/admin/stages/{id}/encadrants` 👑
Affecter les encadrants et définir les dates/sujet/mission.

**Body :**
```json
{
  "dateDebut": "2025-02-01",
  "dateFin": "2025-08-01",
  "sujet": "string",
  "mission": "string",
  "encadrantAcademiqueId": 1,
  "encadrantEntrepriseId": 2
}
```
**Réponse 200 :** `StageResponse`.

---

### PATCH `/api/entreprise/stages/{id}/signer-convention` 🏢
Signer la convention de stage (côté entreprise).

**Body :** aucun  
**Réponse 200 :** `StageResponse` avec `conventionSigneeEntreprise: true` et `dateSignatureEntreprise`.

---

### PATCH `/api/encadrant-entreprise/stages/{id}/signer-convention` 🏭
Signer la convention de stage (côté encadrant entreprise).

**Body :** aucun  
**Réponse 200 :** `StageResponse` avec `conventionSigneeEncadrant: true` et `dateSignatureEncadrant`.

---

### PATCH `/api/encadrant-entreprise/stages/{id}/mission` 🏭
Définir ou modifier la mission du stagiaire.

**Body :** `{ "mission": "string" }`  
**Réponse 200 :** `StageResponse`.

---

**Structure `StageResponse` :**
```json
{
  "id": 1,
  "dateDebut": "2025-02-01",
  "dateFin": "2025-08-01",
  "dureeMois": 6,
  "statut": "EN_COURS",
  "sujet": "string",
  "mission": "string",
  "evaluationFinale": null,
  "etudiantId": 1,
  "etudiantNom": "string",
  "entrepriseId": 1,
  "entrepriseNom": "string",
  "encadrantAcademiqueNom": "string",
  "encadrantEntrepriseNom": "string",
  "candidatureId": 1,
  "conventionSigneeEntreprise": false,
  "dateSignatureEntreprise": null,
  "conventionSigneeEncadrant": false,
  "dateSignatureEncadrant": null
}
```

---

## RAPPORTS DE STAGE

### POST `/api/etudiant/stages/{stageId}/rapports` 🎓
Créer un rapport (hebdomadaire ou final). Statut initial : `BROUILLON`.

**Body :**
```json
{
  "titre": "string",
  "contenu": "string",
  "type": "HEBDOMADAIRE | FINAL",
  "semaineDebut": "2025-02-03"
}
```
> `semaineDebut` uniquement pour `HEBDOMADAIRE`. Un seul `FINAL` par stage autorisé.

**Réponse 200 :** `RapportResponse`.

---

### PATCH `/api/etudiant/rapports/{id}/soumettre` 🎓
Soumettre un rapport (`BROUILLON` → `SOUMIS`).

**Réponse 200 :** `RapportResponse` avec `statut: "SOUMIS"` et `dateSoumission`.

---

### GET `/api/etudiant/stages/{stageId}/rapports` 🎓
Mes rapports pour un stage.

**Réponse 200 :** liste de `RapportResponse`.

---

### GET `/api/encadrant-academique/stages/{stageId}/rapports` 👨‍🏫
Voir les rapports d'un stagiaire.

**Réponse 200 :** liste de `RapportResponse`.

---

### PATCH `/api/encadrant-academique/rapports/{id}/valider` 👨‍🏫
Valider un rapport soumis (`SOUMIS` → `VALIDE`).

**Réponse 200 :** `RapportResponse`.

---

### PATCH `/api/encadrant-academique/rapports/{id}/commenter` 👨‍🏫
Rejeter un rapport avec commentaire (`SOUMIS` → `REJETE`).

**Body :** `{ "commentaire": "string" }`  
**Réponse 200 :** `RapportResponse`.

---

**Structure `RapportResponse` :**
```json
{
  "id": 1,
  "stageId": 1,
  "etudiantId": 1,
  "etudiantNom": "string",
  "type": "HEBDOMADAIRE | FINAL",
  "titre": "string",
  "contenu": "string",
  "semaineDebut": "2025-02-03",
  "statut": "BROUILLON | SOUMIS | VALIDE | REJETE",
  "commentaireEncadrant": null,
  "dateCreation": "2025-02-03T10:00:00",
  "dateSoumission": null
}
```

---

## GESTION ADMIN — UTILISATEURS

### GET `/api/admin/users` 👑
Lister tous les utilisateurs.

**Réponse 200 :** liste de `UserResponse`.

---

### GET `/api/admin/users/{id}` 👑
Détail d'un utilisateur.

**Réponse 200 :** `UserResponse`.

---

### GET `/api/admin/users/role/{role}` 👑
Filtrer par rôle.

**Path variable :** `ETUDIANT | ENTREPRISE | ADMIN | ENCADRANT_ACADEMIQUE | ENCADRANT_ENTREPRISE`  
**Réponse 200 :** liste de `UserResponse`.

---

### PATCH `/api/admin/users/{id}/role` 👑
Attribuer / changer le rôle d'un utilisateur.

**Body :** `{ "role": "ETUDIANT | ENTREPRISE | ADMIN | ENCADRANT_ACADEMIQUE | ENCADRANT_ENTREPRISE" }`  
**Réponse 200 :** `UserResponse` mis à jour.

---

### PATCH `/api/admin/users/{id}/statut` 👑
Activer, suspendre ou désactiver un compte.

**Body :** `{ "statut": "ACTIF | INACTIF | EN_ATTENTE | SUSPENDU" }`  
**Réponse 200 :** `UserResponse` mis à jour.

---

**Structure `UserResponse` :**
```json
{
  "id": 1,
  "firstName": "string",
  "lastName": "string",
  "email": "string",
  "telephone": "string",
  "role": "ETUDIANT",
  "statut": "ACTIF",
  "createdAt": "2025-01-01T10:00:00"
}
```

---

## DEMANDES DE STAGE (Admin)

### POST `/api/admin/demandes-stage` 👑
Générer une demande de stage pour un étudiant.

**Body :**
```json
{
  "etudiantId": 1,
  "entrepriseId": 1,
  "lettreMotivation": "string",
  "typeDemande": "spontanée | sur offre"
}
```
**Réponse 200 :** `DemandeStageResponse`.

---

### GET `/api/admin/demandes-stage` 👑
Lister toutes les demandes de stage.

**Réponse 200 :** liste de `DemandeStageResponse`.

---

**Structure `DemandeStageResponse` :**
```json
{
  "id": 1,
  "statut": "EN_ATTENTE | ACCEPTEE | REFUSEE",
  "dateDemande": "2025-01-01",
  "lettreMotivation": "string",
  "typeDemande": "string",
  "etudiantId": 1,
  "etudiantNom": "string",
  "entrepriseId": 1,
  "entrepriseNom": "string"
}
```

---

## DOCUMENTS

### POST `/api/documents/generer` 🔐
Générer un document PDF à partir d'un modèle.

**Body :**
```json
{
  "modeleId": 1,
  "donneesProfil": { "nom": "...", "prenom": "..." },
  "nomEtablissement": "string"
}
```
**Réponse 200 :** `{ "uuid": "string", "titre": "string", "url": "string" }`

---

### GET `/api/documents/{uuid}/telecharger` 🔐
Télécharger un document PDF généré.

**Réponse 200 :** fichier PDF (`application/pdf`).

---

### 🔓 GET `/api/documents/{uuid}/verifier`
Vérifier l'authenticité d'un document (QR code).

**Réponse 200 :** `{ "valide": true, "titre": "string", ... }`

---

### GET `/api/etudiant/documents` 🎓
Historique de mes documents générés.

**Réponse 200 :** liste de `DocumentGenere`.

---

## LÉGENDE DES RÔLES
| Icône | Rôle requis |
|-------|-------------|
| 🎓 | `ETUDIANT` |
| 🏢 | `ENTREPRISE` |
| 👑 | `ADMIN` |
| 👨‍🏫 | `ENCADRANT_ACADEMIQUE` |
| 🏭 | `ENCADRANT_ENTREPRISE` |
| 🔐 | Tout utilisateur authentifié |
| 🔓 | Public (pas de token requis) |

---

## CODES D'ERREUR COURANTS

| Statut | Signification |
|--------|--------------|
| 400 | Données invalides / règle métier violée |
| 401 | Token manquant ou expiré (logout effectué) |
| 403 | Rôle insuffisant ou ressource non autorisée |
| 404 | Ressource non trouvée |
| 500 | Erreur serveur interne |

> Les erreurs retournent `{ "message": "description de l'erreur" }`.
