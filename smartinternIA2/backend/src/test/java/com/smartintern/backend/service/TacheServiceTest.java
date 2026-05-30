package com.smartintern.backend.service;

import com.smartintern.backend.dto.TacheDto;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.SprintRepository;
import com.smartintern.backend.repository.TacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires de la machine à états de TacheService.
 *
 * Couverture : toutes les transitions valides + toutes les transitions invalides.
 */
@ExtendWith(MockitoExtension.class)
class TacheServiceTest {

    @Mock private TacheRepository    tacheRepository;
    @Mock private SprintRepository   sprintRepository;
    @Mock private LogActiviteService logActiviteService;
    @Mock private SprintService      sprintService;

    @InjectMocks
    private TacheService tacheService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private EncadrantEntreprise encadrant;
    private Etudiant            etudiant;
    private Stage               stage;
    private Sprint              sprint;

    @BeforeEach
    void setUp() {
        encadrant = new EncadrantEntreprise();
        encadrant.setId(10L);
        encadrant.setEmail("encadrant@test.com");

        etudiant = new Etudiant();
        etudiant.setId(20L);
        etudiant.setEmail("etudiant@test.com");

        stage = new Stage();
        stage.setId(1L);
        stage.setEncadrantEntreprise(encadrant);
        stage.setEtudiant(etudiant);

        sprint = new Sprint();
        sprint.setId(1L);
        sprint.setStage(stage);
        sprint.setStatut(Sprint.Statut.EN_COURS);
        sprint.setNumero(1);
        sprint.setTitre("Sprint 1");
    }

    private Tache buildTache(Tache.Statut statut) {
        Tache t = new Tache();
        t.setId(100L);
        t.setSprint(sprint);
        t.setTitre("Tâche de test");
        t.setStatut(statut);
        t.setDecisionValidation(Tache.DecisionValidation.NON_TRAITE);
        return t;
    }

    // ════════════════════════════════════════════════════════════
    // Tests de la machine à états
    // ════════════════════════════════════════════════════════════

    // ── demarrer ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("demarrer : A_FAIRE → EN_COURS (transition valide)")
    void demarrer_depuisAFaire_doitPasserEnCours() {
        Tache tache = buildTache(Tache.Statut.A_FAIRE);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));
        when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Tache result = tacheService.demarrer(100L, etudiant, null);

        assertThat(result.getStatut()).isEqualTo(Tache.Statut.EN_COURS);
        verify(sprintService).transitionnerEnCours(sprint);
    }

    @Test
    @DisplayName("demarrer : EN_COURS → EN_COURS interdite")
    void demarrer_depuisEnCours_doitLeverException() {
        Tache tache = buildTache(Tache.Statut.EN_COURS);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));

        assertThatThrownBy(() -> tacheService.demarrer(100L, etudiant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EN_COURS → EN_COURS interdite");
    }

    @Test
    @DisplayName("demarrer : VALIDEE → EN_COURS interdite")
    void demarrer_depuisValidee_doitLeverException() {
        Tache tache = buildTache(Tache.Statut.VALIDEE);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));

        assertThatThrownBy(() -> tacheService.demarrer(100L, etudiant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("interdite");
    }

    // ── marquerTerminee ───────────────────────────────────────────────────────

    @Test
    @DisplayName("marquerTerminee : EN_COURS → TERMINEE_PAR_ETUDIANT (transition valide)")
    void marquerTerminee_depuisEnCours_doitPasserTerminee() {
        Tache tache = buildTache(Tache.Statut.EN_COURS);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));
        when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Tache result = tacheService.marquerTerminee(100L, "Mon travail est fini", etudiant, null);

        assertThat(result.getStatut()).isEqualTo(Tache.Statut.TERMINEE_PAR_ETUDIANT);
        assertThat(result.getNoteEtudiant()).isEqualTo("Mon travail est fini");
        assertThat(result.getDateRealisationEtudiant()).isNotNull();
    }

    @Test
    @DisplayName("marquerTerminee : A_FAIRE → TERMINEE_PAR_ETUDIANT interdite")
    void marquerTerminee_depuisAFaire_doitLeverException() {
        Tache tache = buildTache(Tache.Statut.A_FAIRE);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));

        assertThatThrownBy(() -> tacheService.marquerTerminee(100L, null, etudiant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EN_COURS");
    }

    // ── valider ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("valider : TERMINEE_PAR_ETUDIANT → VALIDEE (transition valide)")
    void valider_depuisTerminee_doitPasserValidee() {
        Tache tache = buildTache(Tache.Statut.TERMINEE_PAR_ETUDIANT);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));
        when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TacheDto.ValidationRequest dto = new TacheDto.ValidationRequest();
        dto.setObservation("Excellent travail");
        dto.setNote(18.5f);

        Tache result = tacheService.valider(100L, dto, encadrant, null);

        assertThat(result.getStatut()).isEqualTo(Tache.Statut.VALIDEE);
        assertThat(result.getDecisionValidation()).isEqualTo(Tache.DecisionValidation.VALIDEE);
        assertThat(result.getObservationEncadrant()).isEqualTo("Excellent travail");
        assertThat(result.getNote()).isEqualTo(18.5f);
        assertThat(result.getDateTraitementEncadrant()).isNotNull();
    }

    @Test
    @DisplayName("valider : EN_COURS → VALIDEE interdite")
    void valider_depuisEnCours_doitLeverException() {
        Tache tache = buildTache(Tache.Statut.EN_COURS);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));

        assertThatThrownBy(() -> tacheService.valider(100L, null, encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TERMINEE_PAR_ETUDIANT");
    }

    @Test
    @DisplayName("valider : A_FAIRE → VALIDEE interdite")
    void valider_depuisAFaire_doitLeverException() {
        Tache tache = buildTache(Tache.Statut.A_FAIRE);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));

        assertThatThrownBy(() -> tacheService.valider(100L, null, encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("interdite");
    }

    // ── refuser ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refuser : TERMINEE_PAR_ETUDIANT → REFUSEE (transition valide)")
    void refuser_depuisTerminee_doitPasserRefusee() {
        Tache tache = buildTache(Tache.Statut.TERMINEE_PAR_ETUDIANT);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));
        when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Tache result = tacheService.refuser(100L, "Travail insuffisant", encadrant, null);

        assertThat(result.getStatut()).isEqualTo(Tache.Statut.REFUSEE);
        assertThat(result.getDecisionValidation()).isEqualTo(Tache.DecisionValidation.REFUSEE);
        assertThat(result.getObservationEncadrant()).isEqualTo("Travail insuffisant");
        assertThat(result.getDateTraitementEncadrant()).isNotNull();
    }

    @Test
    @DisplayName("refuser : observation vide → IllegalStateException")
    void refuser_avecObservationVide_doitLeverException() {
        Tache tache = buildTache(Tache.Statut.TERMINEE_PAR_ETUDIANT);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));

        assertThatThrownBy(() -> tacheService.refuser(100L, "  ", encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("observation est obligatoire");
    }

    @Test
    @DisplayName("refuser : observation null → IllegalStateException")
    void refuser_avecObservationNull_doitLeverException() {
        Tache tache = buildTache(Tache.Statut.TERMINEE_PAR_ETUDIANT);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));

        assertThatThrownBy(() -> tacheService.refuser(100L, null, encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("observation est obligatoire");
    }

    // ── reprendre ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reprendre : REFUSEE → EN_COURS (transition valide)")
    void reprendre_depuisRefusee_doitPasserEnCours() {
        Tache tache = buildTache(Tache.Statut.REFUSEE);
        tache.setDecisionValidation(Tache.DecisionValidation.REFUSEE);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));
        when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Tache result = tacheService.reprendre(100L, etudiant, null);

        assertThat(result.getStatut()).isEqualTo(Tache.Statut.EN_COURS);
        assertThat(result.getDecisionValidation()).isEqualTo(Tache.DecisionValidation.NON_TRAITE);
    }

    @Test
    @DisplayName("reprendre : A_FAIRE → EN_COURS interdite")
    void reprendre_depuisAFaire_doitLeverException() {
        Tache tache = buildTache(Tache.Statut.A_FAIRE);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));

        assertThatThrownBy(() -> tacheService.reprendre(100L, etudiant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REFUSEE");
    }

    @Test
    @DisplayName("reprendre : VALIDEE → EN_COURS interdite")
    void reprendre_depuisValidee_doitLeverException() {
        Tache tache = buildTache(Tache.Statut.VALIDEE);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));

        assertThatThrownBy(() -> tacheService.reprendre(100L, etudiant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("interdite");
    }

    // ── supprimer ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("supprimer : tâche VALIDEE → IllegalStateException")
    void supprimer_tacheValidee_doitLeverException() {
        Tache tache = buildTache(Tache.Statut.VALIDEE);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));

        assertThatThrownBy(() -> tacheService.supprimer(100L, encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VALIDÉE");
    }

    @Test
    @DisplayName("supprimer : tâche TERMINEE_PAR_ETUDIANT → IllegalStateException")
    void supprimer_tacheTerminee_doitLeverException() {
        Tache tache = buildTache(Tache.Statut.TERMINEE_PAR_ETUDIANT);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));

        assertThatThrownBy(() -> tacheService.supprimer(100L, encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TERMINEE_PAR_ETUDIANT");
    }

    @Test
    @DisplayName("supprimer : tâche A_FAIRE → soft-delete réussi")
    void supprimer_tacheAFaire_doitSoftDelete() {
        Tache tache = buildTache(Tache.Statut.A_FAIRE);
        when(tacheRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(tache));
        when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(logActiviteService)
                .loguer(any(), any(), any(), any(), any(), any());

        tacheService.supprimer(100L, encadrant, null);

        assertThat(tache.getDeletedAt()).isNotNull();
        assertThat(tache.getDeletedByUserId()).isEqualTo(encadrant.getId());
    }
}
