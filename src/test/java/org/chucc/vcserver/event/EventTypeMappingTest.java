package org.chucc.vcserver.event;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that KafkaConfig type mappings are correct.
 * Prevents runtime errors from typos in type mapping strings.
 */
@SpringBootTest
class EventTypeMappingTest {

  @Autowired
  private ConsumerFactory<String, VersionControlEvent> consumerFactory;

  @Test
  void kafkaConsumer_shouldHaveCorrectTypeMappings() {
    // Act: Get consumer config
    Map<String, Object> config = consumerFactory.getConfigurationProperties();
    String typeMappings = (String) config.get("spring.json.type.mapping");

    // Assert: All 12 event types mapped
    assertThat(typeMappings).contains("BranchCreated:org.chucc.vcserver.event.BranchCreatedEvent");
    assertThat(typeMappings).contains("CommitCreated:org.chucc.vcserver.event.CommitCreatedEvent");
    assertThat(typeMappings).contains("TagCreated:org.chucc.vcserver.event.TagCreatedEvent");
    assertThat(typeMappings).contains("BranchReset:org.chucc.vcserver.event.BranchResetEvent");
    assertThat(typeMappings).contains("RevertCreated:org.chucc.vcserver.event.RevertCreatedEvent");
    assertThat(typeMappings).contains("SnapshotCreated:org.chucc.vcserver.event.SnapshotCreatedEvent");
    assertThat(typeMappings).contains("CherryPicked:org.chucc.vcserver.event.CherryPickedEvent");
    assertThat(typeMappings).contains("BranchRebased:org.chucc.vcserver.event.BranchRebasedEvent");
    assertThat(typeMappings).contains("CommitsSquashed:org.chucc.vcserver.event.CommitsSquashedEvent");
    assertThat(typeMappings).contains("BatchGraphsCompleted:org.chucc.vcserver.event.BatchGraphsCompletedEvent");
    assertThat(typeMappings).contains("BranchDeleted:org.chucc.vcserver.event.BranchDeletedEvent");
    assertThat(typeMappings).contains("DatasetDeleted:org.chucc.vcserver.event.DatasetDeletedEvent");
  }

  @Test
  void allEventClasses_shouldExist() {
    // Assert: All classes in type mappings can be loaded
    String[] expectedClasses = {
        "org.chucc.vcserver.event.BranchCreatedEvent",
        "org.chucc.vcserver.event.CommitCreatedEvent",
        "org.chucc.vcserver.event.TagCreatedEvent",
        "org.chucc.vcserver.event.BranchResetEvent",
        "org.chucc.vcserver.event.RevertCreatedEvent",
        "org.chucc.vcserver.event.SnapshotCreatedEvent",
        "org.chucc.vcserver.event.CherryPickedEvent",
        "org.chucc.vcserver.event.BranchRebasedEvent",
        "org.chucc.vcserver.event.CommitsSquashedEvent",
        "org.chucc.vcserver.event.BatchGraphsCompletedEvent",
        "org.chucc.vcserver.event.BranchDeletedEvent",
        "org.chucc.vcserver.event.DatasetDeletedEvent"
    };

    for (String className : expectedClasses) {
      assertThat(className)
          .as("Event class %s should exist", className)
          .satisfies(name -> {
            try {
              Class.forName(name);
            } catch (ClassNotFoundException e) {
              throw new AssertionError("Class not found: " + name, e);
            }
          });
    }
  }
}
