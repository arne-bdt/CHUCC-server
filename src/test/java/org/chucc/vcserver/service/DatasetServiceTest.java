package org.chucc.vcserver.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.DatasetRef;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetServiceTest {
  private DatasetService service;
  private BranchRepository branchRepository;
  private CommitRepository commitRepository;
  private SnapshotService snapshotService;

  private static final String DATASET_NAME = "test-dataset";
  private static final String AUTHOR = "test-author";

  @BeforeEach
  void setUp() {
    branchRepository = new BranchRepository();
    commitRepository = new CommitRepository();

    // Create SnapshotService with mocked dependencies
    EventPublisher eventPublisher = Mockito.mock(EventPublisher.class);
    KafkaProperties kafkaProperties = Mockito.mock(KafkaProperties.class);
    VersionControlProperties vcProperties = new VersionControlProperties();
    vcProperties.setSnapshotsEnabled(false); // Disable snapshots for basic tests

    snapshotService = new SnapshotService(
        null, // DatasetService will be set later if needed
        branchRepository,
        commitRepository,
        eventPublisher,
        vcProperties,
        kafkaProperties
    );

    // Use SimpleMeterRegistry for testing - provides metrics without external dependencies
    service = new DatasetService(branchRepository, commitRepository, snapshotService,
        new SimpleMeterRegistry());
  }

  @Test
  void shouldCreateDatasetWithMainBranch() {
    // When
    Branch mainBranch = service.createDataset(DATASET_NAME, AUTHOR);

    // Then
    assertThat(mainBranch.getName()).isEqualTo("main");
    assertThat(mainBranch.getCommitId()).isNotNull();

    // Verify branch exists in repository
    assertThat(branchRepository.exists(DATASET_NAME, "main")).isTrue();

    // Verify commit exists
    assertThat(commitRepository.exists(DATASET_NAME, mainBranch.getCommitId())).isTrue();
  }

  @Test
  void shouldGetDatasetForBranchRef() {
    // Given
    Branch mainBranch = service.createDataset(DATASET_NAME, AUTHOR);
    DatasetRef ref = DatasetRef.forBranch(DATASET_NAME, "main");

    // When
    Dataset dataset = service.getDataset(ref);

    // Then
    assertThat(dataset).isNotNull();
    assertThat(dataset.asDatasetGraph()).isNotNull();
  }

  @Test
  void shouldGetDatasetForCommitRef() {
    // Given
    Branch mainBranch = service.createDataset(DATASET_NAME, AUTHOR);
    DatasetRef ref = DatasetRef.forCommit(DATASET_NAME, mainBranch.getCommitId());

    // When
    Dataset dataset = service.getDataset(ref);

    // Then
    assertThat(dataset).isNotNull();
    assertThat(dataset.asDatasetGraph()).isNotNull();
  }

  @Test
  void shouldGetMutableDataset() {
    // Given
    Branch mainBranch = service.createDataset(DATASET_NAME, AUTHOR);
    DatasetRef ref = DatasetRef.forBranch(DATASET_NAME, "main");

    // When
    Dataset dataset = service.getMutableDataset(ref);

    // Then
    assertThat(dataset).isNotNull();
    assertThat(dataset.asDatasetGraph()).isNotNull();
  }

  @Test
  void shouldThrowExceptionForNonexistentBranch() {
    // Given
    DatasetRef ref = DatasetRef.forBranch(DATASET_NAME, "nonexistent");

    // When/Then
    assertThatThrownBy(() -> service.getDataset(ref))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot resolve reference");
  }

  @Test
  void shouldThrowExceptionForNonexistentCommit() {
    // Given
    CommitId nonexistentId = CommitId.generate();
    DatasetRef ref = DatasetRef.forCommit(DATASET_NAME, nonexistentId);

    // When/Then
    assertThatThrownBy(() -> service.getDataset(ref))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot resolve reference");
  }

  @Test
  void shouldResolveRefWithMultipleCommits() {
    // Given - Create dataset with initial commit
    Branch mainBranch = service.createDataset(DATASET_NAME, AUTHOR);
    CommitId initialCommit = mainBranch.getCommitId();

    // Create a second commit
    Commit secondCommit = Commit.create(
        java.util.List.of(initialCommit),
        AUTHOR,
        "Second commit"
    );
    RDFPatch patch = RDFPatchOps.emptyPatch();
    commitRepository.save(DATASET_NAME, secondCommit, patch);

    // Update main branch to point to second commit
    branchRepository.updateBranchHead(DATASET_NAME, "main", secondCommit.id());

    // When
    DatasetRef branchRef = DatasetRef.forBranch(DATASET_NAME, "main");
    Dataset datasetFromBranch = service.getDataset(branchRef);

    DatasetRef commitRef = DatasetRef.forCommit(DATASET_NAME, secondCommit.id());
    Dataset datasetFromCommit = service.getDataset(commitRef);

    // Then
    assertThat(datasetFromBranch).isNotNull();
    assertThat(datasetFromCommit).isNotNull();
  }

  @Test
  void shouldClearCache() {
    // Given
    Branch mainBranch = service.createDataset(DATASET_NAME, AUTHOR);
    DatasetRef ref = DatasetRef.forBranch(DATASET_NAME, "main");
    service.getDataset(ref); // Populate cache

    // When
    service.clearCache(DATASET_NAME);

    // Then - Should still be able to get dataset (rebuilt from scratch)
    Dataset dataset = service.getDataset(ref);
    assertThat(dataset).isNotNull();
  }

  @Test
  void shouldClearAllCaches() {
    // Given
    service.createDataset("dataset1", AUTHOR);
    service.createDataset("dataset2", AUTHOR);
    service.getDataset(DatasetRef.forBranch("dataset1", "main"));
    service.getDataset(DatasetRef.forBranch("dataset2", "main"));

    // When
    service.clearAllCaches();

    // Then - Should still be able to get datasets
    assertThat(service.getDataset(DatasetRef.forBranch("dataset1", "main"))).isNotNull();
    assertThat(service.getDataset(DatasetRef.forBranch("dataset2", "main"))).isNotNull();
  }

  @Test
  void shouldHandleMultipleBranches() {
    // Given
    Branch mainBranch = service.createDataset(DATASET_NAME, AUTHOR);

    // Create develop branch pointing to same commit
    Branch developBranch = new Branch("develop", mainBranch.getCommitId());
    branchRepository.save(DATASET_NAME, developBranch);

    // When
    Dataset mainDataset = service.getDataset(DatasetRef.forBranch(DATASET_NAME, "main"));
    Dataset developDataset = service.getDataset(
        DatasetRef.forBranch(DATASET_NAME, "develop"));

    // Then
    assertThat(mainDataset).isNotNull();
    assertThat(developDataset).isNotNull();
  }
}
