package com.smartintern.backend.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class EmailTemplateEngine {

    public String render(String templateName, Map<String, Object> variables) {
        String path = "templates/email/" + templateName + ".html";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                template = template.replace("{{" + entry.getKey() + "}}", value);
            }
            return template;
        } catch (IOException e) {
            log.error("Template email introuvable : {}", path);
            throw new RuntimeException("Template email introuvable : " + templateName);
        }
    }
}
