package com.smartintern.backend.security;

import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class JwtBlacklist {

    private final Set<String> tokens = Collections.synchronizedSet(new HashSet<>());

    public void blacklist(String token) {
        tokens.add(token);
    }

    public boolean isBlacklisted(String token) {
        return tokens.contains(token);
    }
}
