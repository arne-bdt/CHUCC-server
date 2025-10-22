# Task: Integrate Schema Registry (Avro/Protobuf)

**Status:** Not Started
**Priority:** 🔴 **HIGH**
**Estimated Time:** 4-5 hours
**Dependencies:** 03-add-event-metadata-headers.md (for schemaVersion)

---

## Context

**CRITICAL GAP:** CHUCC Server uses JSON serialization without schema management, violating Kafka best practices.

### Current Implementation

[KafkaConfig.java:78-79](src/main/java/org/chucc/vcserver/config/KafkaConfig.java#L78-L79):
```java
configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
    JsonSerializer.class);  // ❌ No schema validation
```

[KafkaConfig.java:166-178](src/main/java/org/chucc/vcserver/config/KafkaConfig.java#L166-L178):
```java
// Manual type mappings (fragile!)
configProps.put(JsonDeserializer.TYPE_MAPPINGS,
    "BranchCreated:org.chucc.vcserver.event.BranchCreatedEvent,"
    + "CommitCreated:org.chucc.vcserver.event.CommitCreatedEvent,"
    // ... 12 manual mappings
);
```

### What's Missing

From German checklist:

> **Schema-Management:** Avro/Protobuf + Schema-Registry, Kompatibilitätsmodus z. B. *BACKWARD* (oder *FULL*).

**Problems:**
- ❌ No schema registry (Confluent/Apicurio)
- ❌ No Avro/Protobuf schemas
- ❌ No compatibility checking (backward/forward)
- ❌ No schema versioning enforcement
- ❌ Fragile type mappings (breaks if class renamed)

**Impact:**
- Events may deserialize incorrectly if schema changes
- No protection against breaking changes
- Cannot evolve event schemas safely
- Consumer/producer incompatibility undetected

---

## Goal

Integrate Schema Registry with Avro serialization to enable:
1. ✅ Schema versioning and evolution
2. ✅ Automatic compatibility checking
3. ✅ Type-safe event serialization
4. ✅ Backward/forward compatibility guarantees

---

## Design Decisions

### 1. Schema Format: Avro vs Protobuf

**Chosen: Apache Avro**

**Why Avro:**
- ✅ Native Kafka ecosystem support
- ✅ Schema evolution built-in (add/remove fields)
- ✅ Compact binary format
- ✅ Better tooling (Maven plugin for code generation)

**Protobuf:**
- ⚠️ Good alternative, but less Kafka-native
- ⚠️ More complex setup

### 2. Schema Registry: Confluent vs Apicurio

**Chosen: Confluent Schema Registry**

**Why:**
- ✅ De-facto standard for Kafka
- ✅ Better documentation
- ✅ Apache 2.0 license (free for commercial use)
- ✅ Easy Docker setup

**Apicurio:**
- ⚠️ Good alternative (Red Hat backed)
- ⚠️ More features but more complex

### 3. Compatibility Mode

**Chosen: BACKWARD (default)**

**Compatibility Modes:**
| Mode | Producer Change | Consumer Change | Use Case |
|------|-----------------|-----------------|----------|
| BACKWARD | Old consumers can read new data | ✅ Can add optional fields | ✅ Recommended |
| FORWARD | New consumers can read old data | Can remove fields | Less common |
| FULL | Both backward and forward | Very restrictive | Strict schemas |
| NONE | No checks | ❌ Dangerous | Not recommended |

**Backward Compatibility Rules:**
- ✅ Can add optional fields (with defaults)
- ✅ Can remove optional fields
- ❌ Cannot remove required fields
- ❌ Cannot change field types

---

## Implementation Plan

### Step 1: Add Dependencies (10 min)

**pom.xml:**
```xml
<properties>
  <confluent.version>7.6.0</confluent.version>
</properties>

<repositories>
  <!-- Confluent Maven repository -->
  <repository>
    <id>confluent</id>
    <url>https://packages.confluent.io/maven/</url>
  </repository>
</repositories>

<dependencies>
  <!-- Kafka Avro Serializer -->
  <dependency>
    <groupId>io.confluent</groupId>
    <artifactId>kafka-avro-serializer</artifactId>
    <version>${confluent.version}</version>
  </dependency>

  <!-- Avro -->
  <dependency>
    <groupId>org.apache.avro</groupId>
    <artifactId>avro</artifactId>
    <version>1.11.3</version>
  </dependency>
</dependencies>

<build>
  <plugins>
    <!-- Avro Maven Plugin -->
    <plugin>
      <groupId>org.apache.avro</groupId>
      <artifactId>avro-maven-plugin</artifactId>
      <version>1.11.3</version>
      <executions>
        <execution>
          <phase>generate-sources</phase>
          <goals>
            <goal>schema</goal>
          </goals>
          <configuration>
            <sourceDirectory>${project.basedir}/src/main/avro</sourceDirectory>
            <outputDirectory>${project.basedir}/target/generated-sources/avro</outputDirectory>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

---

### Step 2: Define Avro Schemas (60 min)

**Create:** `src/main/avro/CommitCreatedEvent.avsc`

```json
{
  "type": "record",
  "name": "CommitCreatedEvent",
  "namespace": "org.chucc.vcserver.event.avro",
  "doc": "Event published when a commit is created",
  "fields": [
    {
      "name": "eventId",
      "type": "string",
      "doc": "Unique event identifier (UUIDv7)"
    },
    {
      "name": "dataset",
      "type": "string",
      "doc": "Target dataset name"
    },
    {
      "name": "commitId",
      "type": "string",
      "doc": "Commit identifier (UUIDv7)"
    },
    {
      "name": "parents",
      "type": {
        "type": "array",
        "items": "string"
      },
      "doc": "Parent commit IDs"
    },
    {
      "name": "branch",
      "type": ["null", "string"],
      "default": null,
      "doc": "Branch name (nullable for detached commits)"
    },
    {
      "name": "author",
      "type": "string",
      "doc": "Commit author"
    },
    {
      "name": "message",
      "type": "string",
      "doc": "Commit message"
    },
    {
      "name": "timestamp",
      "type": "long",
      "logicalType": "timestamp-millis",
      "doc": "Event creation time (epoch millis)"
    },
    {
      "name": "rdfPatch",
      "type": "string",
      "doc": "RDF Patch (text format)"
    },
    {
      "name": "causationId",
      "type": ["null", "string"],
      "default": null,
      "doc": "Event ID that caused this event (nullable)"
    }
  ]
}
```

**Create schemas for all 12 event types:**
1. BranchCreatedEvent.avsc
2. BranchResetEvent.avsc
3. BranchRebasedEvent.avsc
4. BranchDeletedEvent.avsc
5. CommitCreatedEvent.avsc
6. TagCreatedEvent.avsc
7. RevertCreatedEvent.avsc
8. SnapshotCreatedEvent.avsc
9. CherryPickedEvent.avsc
10. CommitsSquashedEvent.avsc
11. BatchGraphsCompletedEvent.avsc
12. DatasetDeletedEvent.avsc

---

### Step 3: Update Kafka Configuration (30 min)

**application.yml:**
```yaml
spring:
  kafka:
    properties:
      # Schema Registry URL
      schema.registry.url: ${SCHEMA_REGISTRY_URL:http://localhost:8081}

    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      properties:
        # Auto-register schemas
        auto.register.schemas: true
        # Use specific Avro classes (not GenericRecord)
        use.latest.version: true

    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      properties:
        # Use specific Avro classes
        specific.avro.reader: true
```

**KafkaConfig.java:**
```java
@Bean
public ProducerFactory<String, SpecificRecordBase> avroProducerFactory() {
  Map<String, Object> configProps = new HashMap<>();
  configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
      kafkaProperties.getBootstrapServers());
  configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
      StringSerializer.class);
  configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
      KafkaAvroSerializer.class);  // ✅ Avro serializer
  configProps.put("schema.registry.url",
      schemaRegistryUrl);
  configProps.put(ProducerConfig.ACKS_CONFIG, "all");
  configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
  configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

  return new DefaultKafkaProducerFactory<>(configProps);
}

@Bean
public ConsumerFactory<String, SpecificRecordBase> avroConsumerFactory() {
  Map<String, Object> configProps = new HashMap<>();
  configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
      kafkaProperties.getBootstrapServers());
  configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "read-model-projector");
  configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
      StringDeserializer.class);
  configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
      KafkaAvroDeserializer.class);  // ✅ Avro deserializer
  configProps.put("schema.registry.url",
      schemaRegistryUrl);
  configProps.put("specific.avro.reader", true);

  return new DefaultKafkaConsumerFactory<>(configProps);
}
```

---

### Step 4: Create Adapter Layer (45 min)

**Problem:** Avro-generated classes are different from existing POJOs.

**Solution:** Adapter pattern to convert between Avro and domain models.

**File:** `src/main/java/org/chucc/vcserver/event/AvroEventAdapter.java`

```java
package org.chucc.vcserver.event;

import org.chucc.vcserver.event.avro.*;

/**
 * Adapter to convert between domain events and Avro events.
 */
public class AvroEventAdapter {

  /**
   * Convert domain event to Avro event.
   */
  public static org.apache.avro.specific.SpecificRecordBase toAvro(
      VersionControlEvent domainEvent) {

    return switch (domainEvent) {
      case org.chucc.vcserver.event.CommitCreatedEvent e -> toAvro(e);
      case org.chucc.vcserver.event.BranchCreatedEvent e -> toAvro(e);
      // ... other event types
      default -> throw new IllegalArgumentException(
          "Unknown event type: " + domainEvent.getClass());
    };
  }

  /**
   * Convert Avro event to domain event.
   */
  public static VersionControlEvent fromAvro(
      org.apache.avro.specific.SpecificRecordBase avroEvent) {

    return switch (avroEvent) {
      case CommitCreatedEvent e -> fromAvro(e);
      case BranchCreatedEvent e -> fromAvro(e);
      // ... other event types
      default -> throw new IllegalArgumentException(
          "Unknown Avro event type: " + avroEvent.getClass());
    };
  }

  // Convert CommitCreatedEvent domain → Avro
  private static CommitCreatedEvent toAvro(
      org.chucc.vcserver.event.CommitCreatedEvent domain) {
    return CommitCreatedEvent.newBuilder()
        .setEventId(domain.getEventId())
        .setDataset(domain.dataset())
        .setCommitId(domain.commitId())
        .setParents(domain.parents())
        .setBranch(domain.branch())
        .setAuthor(domain.author())
        .setMessage(domain.message())
        .setTimestamp(domain.timestamp().toEpochMilli())
        .setRdfPatch(domain.rdfPatch())
        .setCausationId(domain.getCausationId())
        .build();
  }

  // Convert CommitCreatedEvent Avro → domain
  private static org.chucc.vcserver.event.CommitCreatedEvent fromAvro(
      CommitCreatedEvent avro) {
    return new org.chucc.vcserver.event.CommitCreatedEvent(
        avro.getEventId().toString(),
        avro.getDataset().toString(),
        avro.getCommitId().toString(),
        avro.getParents().stream().map(CharSequence::toString).toList(),
        avro.getBranch() != null ? avro.getBranch().toString() : null,
        avro.getAuthor().toString(),
        avro.getMessage().toString(),
        Instant.ofEpochMilli(avro.getTimestamp()),
        avro.getRdfPatch().toString(),
        avro.getCausationId() != null ? avro.getCausationId().toString() : null
    );
  }

  // ... similar methods for other 11 event types
}
```

---

### Step 5: Update EventPublisher (20 min)

**Use adapter to convert before publishing:**

```java
public CompletableFuture<SendResult<String, SpecificRecordBase>> publish(
    VersionControlEvent event) {

  // Convert domain event to Avro
  SpecificRecordBase avroEvent = AvroEventAdapter.toAvro(event);

  String topic = kafkaProperties.getTopicName(event.dataset());
  String key = event.getAggregateIdentity().getPartitionKey();

  ProducerRecord<String, SpecificRecordBase> record =
      new ProducerRecord<>(topic, null, key, avroEvent);

  // Schema Registry automatically validates and registers schema
  return kafkaTemplate.send(record);
}
```

---

### Step 6: Update ReadModelProjector (20 min)

**Use adapter to convert after consuming:**

```java
@KafkaListener(
    topicPattern = "vc\\..*\\.events",
    groupId = "read-model-projector"
)
public void handleAvroEvent(SpecificRecordBase avroEvent) {
  // Convert Avro event to domain event
  VersionControlEvent domainEvent = AvroEventAdapter.fromAvro(avroEvent);

  // Process domain event (existing logic)
  handleEvent(domainEvent);
}
```

---

### Step 7: Docker Compose for Schema Registry (15 min)

**docker-compose.yml:**
```yaml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092

  schema-registry:
    image: confluentinc/cp-schema-registry:7.6.0
    depends_on:
      - kafka
    ports:
      - "8081:8081"
    environment:
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: kafka:9092
      SCHEMA_REGISTRY_HOST_NAME: schema-registry
      SCHEMA_REGISTRY_LISTENERS: http://0.0.0.0:8081
```

**Start:**
```bash
docker-compose up -d
```

---

### Step 8: Schema Evolution Example (20 min)

**Scenario:** Add new field to CommitCreatedEvent

**Version 1.0 (current):**
```json
{
  "fields": [
    {"name": "commitId", "type": "string"},
    {"name": "author", "type": "string"}
  ]
}
```

**Version 2.0 (new - add optional field):**
```json
{
  "fields": [
    {"name": "commitId", "type": "string"},
    {"name": "author", "type": "string"},
    {"name": "tags", "type": {"type": "array", "items": "string"}, "default": []}
  ]
}
```

**Schema Registry Validation:**
```bash
# Check compatibility
curl -X POST http://localhost:8081/compatibility/subjects/CommitCreatedEvent-value/versions/latest \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{"schema": "{...new schema...}"}'

# Response:
{"is_compatible": true}  # ✅ Backward compatible
```

---

### Step 9: Testing (30 min)

**Integration Test:**
```java
@SpringBootTest
@Testcontainers
class SchemaRegistryIT {

  @Container
  static SchemaRegistryContainer schemaRegistry =
      new SchemaRegistryContainer("7.6.0");

  @Test
  void publish_shouldRegisterSchemaAutomatically() {
    // Arrange
    CommitCreatedEvent event = CommitCreatedEvent.create(...);

    // Act: Publish event
    eventPublisher.publish(event).get();

    // Assert: Check schema registered
    RestTemplate restTemplate = new RestTemplate();
    String url = schemaRegistry.getSchemaRegistryUrl() +
        "/subjects/CommitCreatedEvent-value/versions/latest";

    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"version\":1");
  }

  @Test
  void consumer_shouldDeserializeAvroEvent() {
    // Arrange & Act: Publish event
    CommitCreatedEvent event = CommitCreatedEvent.create(...);
    eventPublisher.publish(event).get();

    // Assert: Wait for projector to consume
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // Verify event was processed (projector used Avro deserialization)
          assertThat(commitRepository.findById(...)).isPresent();
        });
  }
}
```

---

## Success Criteria

- [ ] Confluent Schema Registry dependency added
- [ ] Avro schemas defined for all 12 event types
- [ ] Maven Avro plugin configured
- [ ] Kafka configured with Avro serializers
- [ ] AvroEventAdapter created
- [ ] EventPublisher uses Avro serialization
- [ ] ReadModelProjector uses Avro deserialization
- [ ] Docker Compose includes Schema Registry
- [ ] Integration tests verify schema registration
- [ ] Backward compatibility mode configured
- [ ] Documentation updated
- [ ] All tests pass

---

## Migration Strategy

**Phase 1: Dual Serialization (Transition Period)**
- Support both JSON and Avro
- Publish events in both formats temporarily
- Allow gradual consumer migration

**Phase 2: Avro Only**
- Remove JSON serialization
- All consumers on Avro

---

## References

- [Confluent Schema Registry Docs](https://docs.confluent.io/platform/current/schema-registry/index.html)
- [Apache Avro Documentation](https://avro.apache.org/docs/current/)
- German Kafka CQRS/ES Checklist

---

## Notes

**Complexity:** High
**Time:** 4-5 hours
**Risk:** Medium (breaking change for serialization)

This enables safe schema evolution - critical for long-term system stability.
