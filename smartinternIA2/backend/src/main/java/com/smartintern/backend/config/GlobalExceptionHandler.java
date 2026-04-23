package com.smartintern.backend.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestion centralisée des erreurs.
 * Retourne un JSON structuré pour toutes les exceptions au lieu d'une stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Erreurs de validation (@Valid) ────────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(buildError(
                "Erreur de validation", errors.toString(), HttpStatus.BAD_REQUEST));
    }

    // ── Mauvais identifiants ──────────────────────────────────────────────────
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildError(ex.getMessage(), null, HttpStatus.UNAUTHORIZED));
    }

    // ── Utilisateur non trouvé ────────────────────────────────────────────────
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UsernameNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(ex.getMessage(), null, HttpStatus.NOT_FOUND));
    }

    // ── Erreurs métier génériques (RuntimeException) ──────────────────────────
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(buildError(ex.getMessage(), null, HttpStatus.BAD_REQUEST));
    }

    // ── Erreurs inattendues ───────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("Erreur interne du serveur", ex.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private Map<String, Object> buildError(String message, String detail, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("message", message);
        if (detail != null) body.put("detail", detail);
        return body;
    }
}
