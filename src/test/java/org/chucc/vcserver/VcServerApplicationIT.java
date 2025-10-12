package org.chucc.vcserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Basic application context test.
 * Projector is disabled since this test only verifies context loading.
 */
@SpringBootTest
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=false")
class VcServerApplicationIT {

  @Test
  void contextLoads() {
    // This test verifies that the application context loads successfully
  }
}
