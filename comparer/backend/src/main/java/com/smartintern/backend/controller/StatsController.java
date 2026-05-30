package com.smartintern.backend.controller;

import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.repository.OffreRepository;
import com.smartintern.backend.repository.CandidatureRepository;
import com.smartintern.backend.repository.StageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class StatsController {

    private final UserRepository userRepository;
    private final OffreRepository offreRepository;
    private final CandidatureRepository candidatureRepository;
    private final StageRepository stageRepository;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Statistiques utilisateurs
        stats.put("totalUsers", userRepository.count());
        
        // Statistiques offres
        stats.put("totalOffres", offreRepository.count());
        
        // Statistiques candidatures
        stats.put("totalCandidatures", candidatureRepository.count());
        
        // Statistiques stages
        stats.put("totalStages", stageRepository.count());
        
        return ResponseEntity.ok(stats);
    }
}