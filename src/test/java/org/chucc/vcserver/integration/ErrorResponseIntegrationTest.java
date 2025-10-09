package org.chucc.vcserver.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chucc.vcserver.testutil.KafkaTestContainers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for error response format.
 * Verifies RFC 7807 problem+json compliance for all VcException types.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class ErrorResponseIntegrationTest {

    private static KafkaContainer kafkaContainer;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void startKafka() {
        kafkaContainer = KafkaTestContainers.createKafkaContainer();
        // Container is started by KafkaTestContainers - shared across all tests
    }

    @DynamicPropertySource
    static void configureKafka(DynamicPropertyRegistry registry) {
        registry.add("kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    // Unique consumer group per test class to prevent cross-test event consumption
    registry.add("spring.kafka.consumer.group-id",
        () -> "test-" + System.currentTimeMillis() + "-" + Math.random());
    }

    @Test
    void selectorConflictError_returnsProblemJson() throws Exception {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/test/errors/selector-conflict",
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/problem+json"));

        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.has("type")).isTrue();
        assertThat(json.has("title")).isTrue();
        assertThat(json.has("status")).isTrue();
        assertThat(json.has("code")).isTrue();

        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("code").asText()).isEqualTo("selector_conflict");
        assertThat(json.get("title").asText()).isEqualTo("Bad Request");
    }

    @Test
    void mergeConflictError_returnsProblemJsonWithConflicts() throws Exception {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/test/errors/merge-conflict",
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/problem+json"));

        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.has("type")).isTrue();
        assertThat(json.has("title")).isTrue();
        assertThat(json.has("status")).isTrue();
        assertThat(json.has("code")).isTrue();
        assertThat(json.has("conflicts")).isTrue();

        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("code").asText()).isEqualTo("merge_conflict");
        assertThat(json.get("title").asText()).isEqualTo("Merge cannot proceed due to conflicts");

        // Verify conflicts array
        JsonNode conflicts = json.get("conflicts");
        assertThat(conflicts.isArray()).isTrue();
        assertThat(conflicts.size()).isEqualTo(1);

        JsonNode conflict = conflicts.get(0);
        assertThat(conflict.has("graph")).isTrue();
        assertThat(conflict.has("subject")).isTrue();
        assertThat(conflict.has("predicate")).isTrue();
        assertThat(conflict.has("object")).isTrue();
        assertThat(conflict.get("graph").asText()).isEqualTo("http://example.org/graph1");
        assertThat(conflict.get("subject").asText()).isEqualTo("http://example.org/subject1");
        assertThat(conflict.get("predicate").asText()).isEqualTo("http://example.org/predicate1");
        assertThat(conflict.get("object").asText()).isEqualTo("http://example.org/object1");
    }

    @Test
    void concurrentWriteConflictError_returnsProblemJson() throws Exception {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/test/errors/concurrent-write",
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/problem+json"));

        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("code").asText()).isEqualTo("concurrent_write_conflict");
    }

    @Test
    void graphNotFoundError_returnsProblemJson() throws Exception {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/test/errors/graph-not-found",
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/problem+json"));

        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asInt()).isEqualTo(404);
        assertThat(json.get("code").asText()).isEqualTo("graph_not_found");
    }

    @Test
    void tagRetargetForbiddenError_returnsProblemJson() throws Exception {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/test/errors/tag-retarget-forbidden",
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/problem+json"));

        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("code").asText()).isEqualTo("tag_retarget_forbidden");
    }

    @Test
    void rebaseConflictError_returnsProblemJsonWithConflicts() throws Exception {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/test/errors/rebase-conflict",
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/problem+json"));

        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("code").asText()).isEqualTo("rebase_conflict");
        assertThat(json.has("conflicts")).isTrue();

        // Verify conflicts array structure (per §11)
        JsonNode conflict = json.get("conflicts").get(0);
        assertThat(conflict.has("graph")).isTrue();
        assertThat(conflict.has("subject")).isTrue();
        assertThat(conflict.has("predicate")).isTrue();
        assertThat(conflict.has("object")).isTrue();
    }

    @Test
    void cherryPickConflictError_returnsProblemJsonWithConflicts() throws Exception {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/test/errors/cherry-pick-conflict",
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/problem+json"));

        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("code").asText()).isEqualTo("cherry_pick_conflict");
        assertThat(json.has("conflicts")).isTrue();

        // Verify conflicts array structure (per §11)
        JsonNode conflict = json.get("conflicts").get(0);
        assertThat(conflict.has("graph")).isTrue();
        assertThat(conflict.has("subject")).isTrue();
        assertThat(conflict.has("predicate")).isTrue();
        assertThat(conflict.has("object")).isTrue();
    }

    @Test
    void allErrorResponses_includeRequiredRfc7807Fields() throws Exception {
        // Test all error endpoints to ensure they have required RFC 7807 fields
        String[] endpoints = {
                "/test/errors/selector-conflict",
                "/test/errors/merge-conflict",
                "/test/errors/concurrent-write",
                "/test/errors/graph-not-found",
                "/test/errors/tag-retarget-forbidden",
                "/test/errors/rebase-conflict",
                "/test/errors/cherry-pick-conflict"
        };

        for (String endpoint : endpoints) {
            ResponseEntity<String> response = restTemplate.getForEntity(endpoint, String.class);

            assertThat(response.getHeaders().getContentType())
                    .as("Content-Type for %s", endpoint)
                    .isEqualTo(MediaType.parseMediaType("application/problem+json"));

            JsonNode json = objectMapper.readTree(response.getBody());

            assertThat(json.has("type")).as("type field for %s", endpoint).isTrue();
            assertThat(json.has("title")).as("title field for %s", endpoint).isTrue();
            assertThat(json.has("status")).as("status field for %s", endpoint).isTrue();
            assertThat(json.has("code")).as("code field for %s", endpoint).isTrue();
        }
    }
}
