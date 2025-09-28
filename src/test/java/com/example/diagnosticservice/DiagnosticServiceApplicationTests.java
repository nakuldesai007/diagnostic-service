package com.example.diagnosticservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DiagnosticServiceApplicationTests {

    @Test
    void contextLoads() {
        // This test ensures that the Spring context loads successfully
        // with all the required beans and configurations
    }

}
