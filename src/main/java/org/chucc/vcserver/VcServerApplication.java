package org.chucc.vcserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the SPARQL 1.2 Protocol with Version Control Extension server.
 */
@SpringBootApplication
@SuppressWarnings("PMD.UseUtilityClass") // Spring Boot requires instantiable main class
public class VcServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(VcServerApplication.class, args);
  }
}
