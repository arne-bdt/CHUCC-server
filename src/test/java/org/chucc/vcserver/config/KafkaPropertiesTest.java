package org.chucc.vcserver.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class KafkaPropertiesTest {

  @Test
  void testDefaultValues() {
    KafkaProperties properties = new KafkaProperties();

    assertEquals("localhost:9092", properties.getBootstrapServers());
    assertEquals("vc.{dataset}.events", properties.getTopicTemplate());
    assertEquals(3, properties.getPartitions());
    assertEquals(1, properties.getReplicationFactor());
    assertEquals(-1, properties.getRetentionMs());
    assertFalse(properties.isCompaction());
  }

  @Test
  void testGetTopicName() {
    KafkaProperties properties = new KafkaProperties();

    assertEquals("vc.test-dataset.events", properties.getTopicName("test-dataset"));
    assertEquals("vc.my-data.events", properties.getTopicName("my-data"));
  }

  @Test
  void testSetBootstrapServers() {
    KafkaProperties properties = new KafkaProperties();
    properties.setBootstrapServers("kafka:9092");

    assertEquals("kafka:9092", properties.getBootstrapServers());
  }

  @Test
  void testSetTopicTemplate() {
    KafkaProperties properties = new KafkaProperties();
    properties.setTopicTemplate("events.{dataset}");

    assertEquals("events.test", properties.getTopicName("test"));
  }

  @Test
  void testSetPartitions() {
    KafkaProperties properties = new KafkaProperties();
    properties.setPartitions(10);

    assertEquals(10, properties.getPartitions());
  }

  @Test
  void testSetReplicationFactor() {
    KafkaProperties properties = new KafkaProperties();
    properties.setReplicationFactor((short) 3);

    assertEquals(3, properties.getReplicationFactor());
  }

  @Test
  void testSetRetentionMs() {
    KafkaProperties properties = new KafkaProperties();
    properties.setRetentionMs(604800000); // 7 days

    assertEquals(604800000, properties.getRetentionMs());
  }

  @Test
  void testSetCompaction() {
    KafkaProperties properties = new KafkaProperties();
    properties.setCompaction(true);

    assertEquals(true, properties.isCompaction());
  }
}
