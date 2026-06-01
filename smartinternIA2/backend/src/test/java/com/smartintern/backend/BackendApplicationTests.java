package com.smartintern.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=false",
    "spring.sql.init.mode=never",
    "jwt.secret=SmartInternTest_SecretKey_512bits_ForTestsOnly_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
    "jwt.expiration=86400000",
    "spring.mail.host=localhost",
    "spring.mail.port=1025",
    "spring.mail.username=test@test.com",
    "spring.mail.password=",
    "spring.mail.properties.mail.smtp.auth=false",
    "spring.mail.properties.mail.smtp.starttls.enable=false",
    "cv.service.url=http://localhost:8000",
    "ai.matching.url=http://localhost:8000"
})
class BackendApplicationTests {

    @Test
    void contextLoads() {
        // Vérifie que le contexte Spring démarre sans erreur
    }

}
