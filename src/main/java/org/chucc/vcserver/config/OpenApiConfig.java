package org.chucc.vcserver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for the SPARQL 1.2 Protocol
 * Version Control Extension.
 */
@Configuration
public class OpenApiConfig {

  /**
   * Configures the OpenAPI specification.
   *
   * @return the configured OpenAPI instance
   */
  @Bean
  public OpenAPI customOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("SPARQL 1.2 Protocol Version Control Extension")
            .description("""
                **SPARQL 1.2 Graph Store Protocol with Version Control**

                This API implements the SPARQL 1.2 Graph Store Protocol with version control \
                extensions, providing Git-like version control for RDF datasets.

                ## Key Features

                - **Graph Store Protocol (GSP)**: CRUD operations on RDF graphs \
                (GET, PUT, POST, DELETE, HEAD, PATCH)
                - **Version Control**: Branch-based commits with full history tracking
                - **Time-Travel Queries**: Query dataset state at any point in time \
                using `asOf` parameter
                - **Optimistic Locking**: ETag and If-Match headers for conflict detection
                - **RDF Patch**: Incremental graph updates with PATCH method
                - **Batch Operations**: Execute multiple graph operations atomically

                ## Version Control Selectors

                All read and write operations support version control selectors:

                - `branch`: Target a specific branch (default: "main")
                - `commit`: Read from a specific commit (read-only)
                - `asOf`: Time-travel queries with RFC 3339 timestamps

                ## Error Handling

                All errors follow RFC 7807 Problem Details format with:

                - Structured error responses (`application/problem+json`)
                - Machine-readable error codes for programmatic handling
                - Human-readable messages and hints for resolution

                ## Getting Started

                1. **Read a graph**: `GET /data?graph=http://example.org/g`
                2. **Create/update a graph**: `PUT /data?graph=http://example.org/g` \
                with Turtle content
                3. **Query version history**: `GET /version/history?branch=main`
                4. **Time-travel query**:
                `GET /data?graph=http://example.org/g&asOf=2025-10-01T12:00:00Z`

                For more details, see the
                [protocol specification](https://www.w3.org/TR/sparql12-protocol/).
                """)
            .version("1.0.0")
            .contact(new Contact()
                .name("CHUCC Project")
                .url("https://github.com/chucc/vc-server"))
            .license(new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
        .addServersItem(new Server()
            .url("http://localhost:8080")
            .description("Development server"));
  }
}
