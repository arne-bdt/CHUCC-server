package org.chucc.vcserver.projection;

import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.DatasetRef;
import org.chucc.vcserver.domain.Snapshot;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.SnapshotCreatedEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.SnapshotService;
import org.chucc.vcserver.testutil.IntegrationTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for snapshot recovery functionality.
 * Tests that snapshots are loaded on startup and used to speed up recovery.
 *
 * <p>This test enables the ReadModelProjector to verify that SnapshotCreatedEvents
 * are properly processed and cached.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class SnapshotRecoveryIT extends IntegrationTestFixture {

  @Autowired
  private EventPublisher eventPublisher;

  @Autowired
  private ReadModelProjector projector;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  @Autowired
  private DatasetService datasetService;

  @Autowired
  private SnapshotService snapshotService;

  @Override
  protected String getDatasetName() {
    return "snapshot-test-dataset";
  }

  @Test
  void snapshotCreatedEvent_shouldBeLoaded() throws Exception {
    String dataset = getDatasetName();
    String branch = "main";

    // Create a commit
    CommitId commit1Id = CommitId.generate();
    String rdfPatch1 = "TX .\n"
        + "A <http://example.org/subject1> <http://example.org/predicate1> \"value1\" .\n"
        + "TC .";

    CommitCreatedEvent event1 = new CommitCreatedEvent(
        dataset,
        commit1Id.value(),
        List.of(),
        branch,
        "First commit",
        "test-author",
        Instant.now(),
        rdfPatch1
    );

    // Publish commit event
    eventPublisher.publish(event1).get();

    // Wait for commit to be processed
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var commit = commitRepository.findByDatasetAndId(dataset, commit1Id);
          assertThat(commit).isPresent();
        });

    // Materialize the dataset at commit1
    Dataset materializedDataset = datasetService.materializeAtCommit(dataset, commit1Id);
    DatasetGraph graph = materializedDataset.asDatasetGraph();

    // Serialize to N-Quads
    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, graph, Lang.NQUADS);
    String nquads = writer.toString();

    // Create and publish snapshot event
    SnapshotCreatedEvent snapshotEvent = new SnapshotCreatedEvent(
        dataset,
        commit1Id.value(),
        branch,
        Instant.now(),
        nquads
    );

    eventPublisher.publish(snapshotEvent).get();

    // Wait for snapshot to be processed
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Optional<Snapshot> snapshot = snapshotService.getLatestSnapshot(dataset, branch);
          assertThat(snapshot).isPresent();
          assertThat(snapshot.get().commitId()).isEqualTo(commit1Id);
          assertThat(snapshot.get().branchName()).isEqualTo(branch);
        });

    // Verify snapshot was loaded
    Optional<Snapshot> loadedSnapshot = snapshotService.getLatestSnapshot(dataset, branch);
    assertThat(loadedSnapshot).isPresent();
    assertThat(loadedSnapshot.get().commitId()).isEqualTo(commit1Id);
    assertThat(loadedSnapshot.get().branchName()).isEqualTo(branch);

    // Verify snapshot graph contains the expected data
    DatasetGraph snapshotGraph = loadedSnapshot.get().graph();
    long quadCount = 0;
    java.util.Iterator<org.apache.jena.sparql.core.Quad> iter = snapshotGraph.find();
    while (iter.hasNext()) {
      iter.next();
      quadCount++;
    }
    assertThat(quadCount).isEqualTo(1);
  }

  @Test
  void snapshotRecovery_withMultipleCommits_shouldCacheSnapshot() throws Exception {
    String dataset = getDatasetName();
    String branch = "main";

    // Create 3 commits
    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();
    CommitId commit3Id = CommitId.generate();

    String rdfPatch1 = "TX .\n"
        + "A <http://example.org/s1> <http://example.org/p1> \"v1\" .\n"
        + "TC .";
    String rdfPatch2 = "TX .\n"
        + "A <http://example.org/s2> <http://example.org/p2> \"v2\" .\n"
        + "TC .";
    String rdfPatch3 = "TX .\n"
        + "A <http://example.org/s3> <http://example.org/p3> \"v3\" .\n"
        + "TC .";

    // Publish commits
    CommitCreatedEvent event1 = new CommitCreatedEvent(
        dataset, commit1Id.value(), List.of(), branch,
        "Commit 1", "test-author", Instant.now(), rdfPatch1);
    eventPublisher.publish(event1).get();

    CommitCreatedEvent event2 = new CommitCreatedEvent(
        dataset, commit2Id.value(), List.of(commit1Id.value()), branch,
        "Commit 2", "test-author", Instant.now(), rdfPatch2);
    eventPublisher.publish(event2).get();

    CommitCreatedEvent event3 = new CommitCreatedEvent(
        dataset, commit3Id.value(), List.of(commit2Id.value()), branch,
        "Commit 3", "test-author", Instant.now(), rdfPatch3);
    eventPublisher.publish(event3).get();

    // Wait for all commits to be processed
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          assertThat(commitRepository.findByDatasetAndId(dataset, commit1Id)).isPresent();
          assertThat(commitRepository.findByDatasetAndId(dataset, commit2Id)).isPresent();
          assertThat(commitRepository.findByDatasetAndId(dataset, commit3Id)).isPresent();
        });

    // Create snapshot at commit2
    Dataset materializedDataset = datasetService.materializeAtCommit(dataset, commit2Id);
    DatasetGraph graph = materializedDataset.asDatasetGraph();

    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, graph, Lang.NQUADS);
    String nquads = writer.toString();

    SnapshotCreatedEvent snapshotEvent = new SnapshotCreatedEvent(
        dataset,
        commit2Id.value(),
        branch,
        Instant.now(),
        nquads
    );

    eventPublisher.publish(snapshotEvent).get();

    // Wait for snapshot to be processed
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Optional<Snapshot> snapshot = snapshotService.getLatestSnapshot(dataset, branch);
          assertThat(snapshot).isPresent();
          assertThat(snapshot.get().commitId()).isEqualTo(commit2Id);
        });

    // Verify snapshot contains data from commit1 and commit2
    Optional<Snapshot> loadedSnapshot = snapshotService.getLatestSnapshot(dataset, branch);
    assertThat(loadedSnapshot).isPresent();
    DatasetGraph snapshotGraph = loadedSnapshot.get().graph();

    // Should have 2 triples (from commit1 and commit2)
    long snapshotQuadCount = 0;
    java.util.Iterator<org.apache.jena.sparql.core.Quad> snapIter = snapshotGraph.find();
    while (snapIter.hasNext()) {
      snapIter.next();
      snapshotQuadCount++;
    }
    assertThat(snapshotQuadCount).isEqualTo(2);

    // Verify that the snapshot graph was cached in DatasetService
    // by checking that we can retrieve the dataset at commit2
    Dataset cachedDataset = datasetService.getDataset(new DatasetRef(dataset, commit2Id.value()));
    assertThat(cachedDataset).isNotNull();
    long cachedQuadCount = 0;
    java.util.Iterator<org.apache.jena.sparql.core.Quad> cachedIter =
        cachedDataset.asDatasetGraph().find();
    while (cachedIter.hasNext()) {
      cachedIter.next();
      cachedQuadCount++;
    }
    assertThat(cachedQuadCount).isEqualTo(2);
  }

  @Test
  void multipleSnapshots_onSameBranch_shouldKeepLatestOnly() throws Exception {
    String dataset = getDatasetName();
    String branch = "main";

    // Create 2 commits
    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();

    String rdfPatch1 = "TX .\nA <http://example.org/s1> <http://example.org/p1> \"v1\" .\nTC .";
    String rdfPatch2 = "TX .\nA <http://example.org/s2> <http://example.org/p2> \"v2\" .\nTC .";

    // Publish commits
    CommitCreatedEvent event1 = new CommitCreatedEvent(
        dataset, commit1Id.value(), List.of(), branch,
        "Commit 1", "test-author", Instant.now(), rdfPatch1);
    eventPublisher.publish(event1).get();

    CommitCreatedEvent event2 = new CommitCreatedEvent(
        dataset, commit2Id.value(), List.of(commit1Id.value()), branch,
        "Commit 2", "test-author", Instant.now(), rdfPatch2);
    eventPublisher.publish(event2).get();

    // Wait for commits
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          assertThat(commitRepository.findByDatasetAndId(dataset, commit2Id)).isPresent();
        });

    // Create first snapshot at commit1
    Dataset ds1 = datasetService.materializeAtCommit(dataset, commit1Id);
    StringWriter w1 = new StringWriter();
    RDFDataMgr.write(w1, ds1.asDatasetGraph(), Lang.NQUADS);

    SnapshotCreatedEvent snapshot1 = new SnapshotCreatedEvent(
        dataset, commit1Id.value(), branch, Instant.now(), w1.toString());
    eventPublisher.publish(snapshot1).get();

    // Wait for first snapshot
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Optional<Snapshot> snap = snapshotService.getLatestSnapshot(dataset, branch);
          assertThat(snap).isPresent();
          assertThat(snap.get().commitId()).isEqualTo(commit1Id);
        });

    // Create second snapshot at commit2
    Dataset ds2 = datasetService.materializeAtCommit(dataset, commit2Id);
    StringWriter w2 = new StringWriter();
    RDFDataMgr.write(w2, ds2.asDatasetGraph(), Lang.NQUADS);

    SnapshotCreatedEvent snapshot2 = new SnapshotCreatedEvent(
        dataset, commit2Id.value(), branch, Instant.now(), w2.toString());
    eventPublisher.publish(snapshot2).get();

    // Wait and verify only the latest snapshot is kept
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Optional<Snapshot> snap = snapshotService.getLatestSnapshot(dataset, branch);
          assertThat(snap).isPresent();
          assertThat(snap.get().commitId()).isEqualTo(commit2Id);

          long finalQuadCount = 0;
          java.util.Iterator<org.apache.jena.sparql.core.Quad> finalIter =
              snap.get().graph().find();
          while (finalIter.hasNext()) {
            finalIter.next();
            finalQuadCount++;
          }
          assertThat(finalQuadCount).isEqualTo(2);
        });
  }
}
