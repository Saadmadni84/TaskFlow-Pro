package com.taskflow.taskflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TaskFlowApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring application context initializes successfully with database and configurations
    }
}
