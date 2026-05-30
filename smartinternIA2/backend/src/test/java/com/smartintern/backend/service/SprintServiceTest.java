package com.smartintern.backend.service;

import com.smartintern.backend.dto.SprintDto;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires SprintService.
 * Couverture : règles de clôture + règles de suppression + création.
 */
@ExtendWith(MockitoExtension.class)
class SprintServiceTest {

    @Mock private SprintRepository           sprintRepository;
    @Mock private TacheRepository            tacheRepository;
    @Mock private StageRepository            stageRepository;
    @Mock private CahierDesChargesRepository cdcRepository;
    @Mock private LogActiviteService         logActiviteService;

    @InjectMocks
    private SprintService sprintService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private EncadrantEntreprise encadrant;
    private Stage               stage;
    private Sprint              sprint;

    @BeforeEach
    void setUp() {
        encadrant = new EncadrantEntreprise();
        encadrant.setId(10L);
        encadrant.setEmail("encadrant@test.com");

        stage = new Stage();
        stage.setId(1L);
        stage.setEncadrantEntreprise(encadrant);

        sprint = new Sprint();
        sprint.setId(50L);
        sprint.setStage(stage);
        sprint.setStatut(Sprint.Statut.EN_COURS);
        sprint.setNumero(1);
        sprint.setTitre("Sprint 1");
    }

    // ════════════════════════════════════════════════════════════
    // Tests clôture
    // ════════════════════════════════════════════════════════════

    @Test
    @DisplayName("cloturer : toutes tâches VALIDEE → clôture réussie")
    void cloturer_toutesValidees_doitReussir() {
        when(sprintRepository.findByIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(sprint));
        when(tacheRepository.countBySprintIdAndDeletedAtIsNull(50L)).thenReturn(3L);
        when(tacheRepository.countBySprintIdAndStatutInAndDeletedAtIsNull(
                eq(50L), anyCollection())).thenReturn(3L);
        when(sprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Sprint result = sprintService.cloturer(50L, "Bonne itération", encadrant, null);

        assertThat(result.getStatut()).isEqualTo(Sprint.Statut.CLOTURE);
        assertThat(result.getObservationEncadrant()).isEqualTo("Bonne itération");
        assertThat(result.getDateClotureReelle()).isNotNull();
    }

    @Test
    @DisplayName("cloturer : toutes tâches REFUSEE → clôture réussie")
    void cloturer_toutesRefusees_doitReussir() {
        when(sprintRepository.findByIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(sprint));
        when(tacheRepository.countBySprintIdAndDeletedAtIsNull(50L)).thenReturn(2L);
        when(tacheRepository.countBySprintIdAndStatutInAndDeletedAtIsNull(
                eq(50L), anyCollection())).thenReturn(2L);
        when(sprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Sprint result = sprintService.cloturer(50L, "Sprint terminé", encadrant, null);

        assertThat(result.getStatut()).isEqualTo(Sprint.Statut.CLOTURE);
    }

    @Test
    @DisplayName("cloturer : tâche EN_COURS existante → IllegalStateException")
    void cloturer_avecTacheEnCours_doitLeverException() {
        when(sprintRepository.findByIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(sprint));
        when(tacheRepository.countBySprintIdAndDeletedAtIsNull(50L)).thenReturn(3L);
        // 2 terminées sur 3 → 1 non terminée
        when(tacheRepository.countBySprintIdAndStatutInAndDeletedAtIsNull(
                eq(50L), anyCollection())).thenReturn(2L);

        assertThatThrownBy(() -> sprintService.cloturer(50L, "Observation", encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 tâche(s)");
    }

    @Test
    @DisplayName("cloturer : observation vide → IllegalStateException")
    void cloturer_avecObservationVide_doitLeverException() {
        when(sprintRepository.findByIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(sprint));

        assertThatThrownBy(() -> sprintService.cloturer(50L, "  ", encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("observation est obligatoire");
    }

    @Test
    @DisplayName("cloturer : observation null → IllegalStateException")
    void cloturer_avecObservationNull_doitLeverException() {
        when(sprintRepository.findByIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(sprint));

        assertThatThrownBy(() -> sprintService.cloturer(50L, null, encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("observation est obligatoire");
    }

    @Test
    @DisplayName("cloturer : sprint déjà CLOTURE → IllegalStateException")
    void cloturer_sprintDejaClosture_doitLeverException() {
        sprint.setStatut(Sprint.Statut.CLOTURE);
        when(sprintRepository.findByIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(sprint));

        assertThatThrownBy(() -> sprintService.cloturer(50L, "Observation", encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà clôturé");
    }

    // ════════════════════════════════════════════════════════════
    // Tests suppression
    // ════════════════════════════════════════════════════════════

    @Test
    @DisplayName("supprimer : tâche VALIDEE présente → IllegalStateException")
    void supprimer_avecTacheValidee_doitLeverException() {
        when(sprintRepository.findByIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(sprint));
        when(tacheRepository.countBySprintIdAndStatutAndDeletedAtIsNull(50L, Tache.Statut.VALIDEE))
                .thenReturn(1L);

        assertThatThrownBy(() -> sprintService.supprimer(50L, encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VALIDÉE");
    }

    @Test
    @DisplayName("supprimer : sans tâche VALIDEE → soft-delete sprint + cascade tâches")
    void supprimer_sansTacheValidee_doitSoftDelete() {
        when(sprintRepository.findByIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(sprint));
        when(tacheRepository.countBySprintIdAndStatutAndDeletedAtIsNull(50L, Tache.Statut.VALIDEE))
                .thenReturn(0L);

        Tache t1 = new Tache(); t1.setId(1L); t1.setStatut(Tache.Statut.A_FAIRE);
        Tache t2 = new Tache(); t2.setId(2L); t2.setStatut(Tache.Statut.EN_COURS);
        when(tacheRepository.findBySprintIdAndDeletedAtIsNullOrderByDateDebutPrevueAsc(50L))
                .thenReturn(List.of(t1, t2));
        when(tacheRepository.saveAll(anyList())).thenReturn(List.of(t1, t2));
        when(sprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(logActiviteService)
                .loguer(any(), any(), any(), any(), any(), any());

        sprintService.supprimer(50L, encadrant, null);

        assertThat(sprint.getDeletedAt()).isNotNull();
        assertThat(sprint.getDeletedByUserId()).isEqualTo(encadrant.getId());
        assertThat(t1.getDeletedAt()).isNotNull();
        assertThat(t2.getDeletedAt()).isNotNull();
    }

    // ════════════════════════════════════════════════════════════
    // Tests création
    // ════════════════════════════════════════════════════════════

    @Test
    @DisplayName("creer : sans CDC VALIDE → IllegalStateException")
    void creer_sansCdcValide_doitLeverException() {
        when(stageRepository.findById(1L)).thenReturn(Optional.of(stage));
        CahierDesCharges cdc = new CahierDesCharges();
        cdc.setStatut(CahierDesCharges.Statut.BROUILLON);
        when(cdcRepository.findByStageId(1L)).thenReturn(Optional.of(cdc));

        SprintDto.CreationRequest dto = new SprintDto.CreationRequest();
        dto.setTitre("Sprint 1");
        dto.setNumero(1);

        assertThatThrownBy(() -> sprintService.creer(1L, dto, encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VALIDE");
    }

    @Test
    @DisplayName("creer : sans aucun CDC → IllegalStateException")
    void creer_sansAucunCdc_doitLeverException() {
        when(stageRepository.findById(1L)).thenReturn(Optional.of(stage));
        when(cdcRepository.findByStageId(1L)).thenReturn(Optional.empty());

        SprintDto.CreationRequest dto = new SprintDto.CreationRequest();
        dto.setTitre("Sprint 1");
        dto.setNumero(1);

        assertThatThrownBy(() -> sprintService.creer(1L, dto, encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cahier des charges");
    }

    @Test
    @DisplayName("creer : avec CDC VALIDE → sprint créé en PLANIFIE")
    void creer_avecCdcValide_doitCreerSprint() {
        when(stageRepository.findById(1L)).thenReturn(Optional.of(stage));
        CahierDesCharges cdc = new CahierDesCharges();
        cdc.setStatut(CahierDesCharges.Statut.VALIDE);
        when(cdcRepository.findByStageId(1L)).thenReturn(Optional.of(cdc));
        when(sprintRepository.existsByStageIdAndNumeroAndDeletedAtIsNull(1L, 1)).thenReturn(false);
        when(sprintRepository.save(any())).thenAnswer(inv -> {
            Sprint s = inv.getArgument(0);
            s.setId(99L);
            return s;
        });
        doNothing().when(logActiviteService)
                .loguer(any(), any(), any(), any(), any(), any());

        SprintDto.CreationRequest dto = new SprintDto.CreationRequest();
        dto.setTitre("Sprint 1");
        dto.setNumero(1);

        Sprint result = sprintService.creer(1L, dto, encadrant, null);

        assertThat(result.getStatut()).isEqualTo(Sprint.Statut.PLANIFIE);
        assertThat(result.getTitre()).isEqualTo("Sprint 1");
        assertThat(result.getNumero()).isEqualTo(1);
    }

    @Test
    @DisplayName("creer : numéro de sprint déjà existant → IllegalStateException")
    void creer_numeroDuplique_doitLeverException() {
        when(stageRepository.findById(1L)).thenReturn(Optional.of(stage));
        CahierDesCharges cdc = new CahierDesCharges();
        cdc.setStatut(CahierDesCharges.Statut.VALIDE);
        when(cdcRepository.findByStageId(1L)).thenReturn(Optional.of(cdc));
        when(sprintRepository.existsByStageIdAndNumeroAndDeletedAtIsNull(1L, 1)).thenReturn(true);

        SprintDto.CreationRequest dto = new SprintDto.CreationRequest();
        dto.setTitre("Sprint 1 doublon");
        dto.setNumero(1);

        assertThatThrownBy(() -> sprintService.creer(1L, dto, encadrant, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("numéro 1 existe déjà");
    }

    // ════════════════════════════════════════════════════════════
    // Test transition interne
    // ════════════════════════════════════════════════════════════

    @Test
    @DisplayName("transitionnerEnCours : sprint PLANIFIE → EN_COURS automatique")
    void transitionnerEnCours_sprintPlanifie_doitPasserEnCours() {
        sprint.setStatut(Sprint.Statut.PLANIFIE);
        when(sprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sprintService.transitionnerEnCours(sprint);

        assertThat(sprint.getStatut()).isEqualTo(Sprint.Statut.EN_COURS);
    }

    @Test
    @DisplayName("transitionnerEnCours : sprint déjà EN_COURS → pas de changement")
    void transitionnerEnCours_sprintDejaEnCours_pasDeChangement() {
        sprint.setStatut(Sprint.Statut.EN_COURS);

        sprintService.transitionnerEnCours(sprint);

        verify(sprintRepository, never()).save(any());
        assertThat(sprint.getStatut()).isEqualTo(Sprint.Statut.EN_COURS);
    }
}
