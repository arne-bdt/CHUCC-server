# Task 09: Add Projector Tests for Branch Lifecycle Events

## Objective
Add comprehensive projector tests to GraphEventProjectorIT for branch lifecycle event handlers: BranchCreatedEvent, BranchResetEvent, and BranchRebasedEvent.

## Background
These event handlers update the BranchRepository to reflect branch operations. With projector disabled by default, we need dedicated tests in GraphEventProjectorIT to verify these handlers work correctly.

## Prerequisite
Complete Task 08 to confirm which handlers actually need tests. This task assumes Task 08 found that these handlers lack dedicated projector tests.

## Tasks

### 1. Add Test for BranchCreatedEvent Projection

Add to `src/test/java/org/chucc/vcserver/integration/GraphEventProjectorIT.java`:

```java
/**
 * Test that BranchCreatedEvent is projected correctly.
 */
@Test
void branchCreatedEvent_shouldBeProjected() throws Exception {
  // Given - Create a BranchCreatedEvent
  CommitId newCommitId = CommitId.generate();

  // First create a commit for the branch to point to
  org.chucc.vcserver.event.CommitCreatedEvent commitEvent =
      new org.chucc.vcserver.event.CommitCreatedEvent(
      DEFAULT_DATASET,
      newCommitId.value(),
      java.util.List.of(initialCommitId.value()),
      "Test commit for branch",
      "Alice",
      java.time.Instant.now(),
      PATCH_CONTENT
  );
  eventPublisher.publish(commitEvent).get();

  // Wait for commit to be projected
  await().atMost(Duration.ofSeconds(5))
      .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, newCommitId).isPresent());

  // Now create branch event
  org.chucc.vcserver.event.BranchCreatedEvent branchEvent =
      new org.chucc.vcserver.event.BranchCreatedEvent(
      DEFAULT_DATASET,
      "feature-branch",
      newCommitId.value(),
      java.time.Instant.now()
  );

  // When - Publish event to Kafka
  eventPublisher.publish(branchEvent).get();

  // Then - Wait for async event projection
  await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        // Verify branch was created in repository
        var branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "feature-branch");
        assertThat(branch).isPresent();
        assertThat(branch.get().getName()).isEqualTo("feature-branch");
        assertThat(branch.get().getCommitId()).isEqualTo(newCommitId);
      });
}
```

**Key points:**
- Must create commit first (branch points to commit)
- Use two-phase await: commit projection, then branch projection
- Verify branch exists and points to correct commit

### 2. Add Test for BranchResetEvent Projection

Add to GraphEventProjectorIT:

```java
/**
 * Test that BranchResetEvent is projected correctly.
 */
@Test
void branchResetEvent_shouldBeProjected() throws Exception {
  // Given - Create a second commit to reset to
  CommitId commit2Id = CommitId.generate();

  org.chucc.vcserver.event.CommitCreatedEvent commitEvent =
      new org.chucc.vcserver.event.CommitCreatedEvent(
      DEFAULT_DATASET,
      commit2Id.value(),
      java.util.List.of(initialCommitId.value()),
      "Second commit",
      "Bob",
      java.time.Instant.now(),
      PATCH_CONTENT
  );
  eventPublisher.publish(commitEvent).get();

  // Wait for commit to be projected
  await().atMost(Duration.ofSeconds(5))
      .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, commit2Id).isPresent());

  // Create BranchResetEvent
  org.chucc.vcserver.event.BranchResetEvent resetEvent =
      new org.chucc.vcserver.event.BranchResetEvent(
      DEFAULT_DATASET,
      DEFAULT_BRANCH, // main branch
      initialCommitId.value(), // from
      commit2Id.value(), // to
      java.time.Instant.now()
  );

  // When - Publish reset event
  eventPublisher.publish(resetEvent).get();

  // Then - Wait for branch update
  await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        // Verify main branch now points to commit2
        var branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, DEFAULT_BRANCH);
        assertThat(branch).isPresent();
        assertThat(branch.get().getCommitId()).isEqualTo(commit2Id);
      });
}
```

**Key points:**
- Resets existing main branch to new commit
- Verifies branch head moved from initialCommitId to commit2Id
- Tests the updateBranchHead operation

### 3. Add Test for BranchRebasedEvent Projection

Add to GraphEventProjectorIT:

```java
/**
 * Test that BranchRebasedEvent is projected correctly.
 */
@Test
void branchRebasedEvent_shouldBeProjected() throws Exception {
  // Given - Create commits for rebase scenario
  CommitId featureBranchId = CommitId.generate();
  CommitId rebasedCommit1Id = CommitId.generate();
  CommitId rebasedCommit2Id = CommitId.generate();

  // Create initial feature branch commit
  org.chucc.vcserver.event.CommitCreatedEvent featureCommit =
      new org.chucc.vcserver.event.CommitCreatedEvent(
      DEFAULT_DATASET,
      featureBranchId.value(),
      java.util.List.of(initialCommitId.value()),
      "Feature commit",
      "Alice",
      java.time.Instant.now(),
      PATCH_CONTENT
  );
  eventPublisher.publish(featureCommit).get();

  // Create feature branch
  org.chucc.vcserver.event.BranchCreatedEvent branchEvent =
      new org.chucc.vcserver.event.BranchCreatedEvent(
      DEFAULT_DATASET,
      "feature",
      featureBranchId.value(),
      java.time.Instant.now()
  );
  eventPublisher.publish(branchEvent).get();

  // Wait for setup
  await().atMost(Duration.ofSeconds(5))
      .until(() -> branchRepository.findByDatasetAndName(DEFAULT_DATASET, "feature").isPresent());

  // Create rebased commits (would normally be created by rebase command handler)
  org.chucc.vcserver.event.CommitCreatedEvent rebasedCommit1 =
      new org.chucc.vcserver.event.CommitCreatedEvent(
      DEFAULT_DATASET,
      rebasedCommit1Id.value(),
      java.util.List.of(initialCommitId.value()),
      "Feature commit (rebased 1)",
      "Alice",
      java.time.Instant.now(),
      PATCH_CONTENT
  );
  eventPublisher.publish(rebasedCommit1).get();

  org.chucc.vcserver.event.CommitCreatedEvent rebasedCommit2 =
      new org.chucc.vcserver.event.CommitCreatedEvent(
      DEFAULT_DATASET,
      rebasedCommit2Id.value(),
      java.util.List.of(rebasedCommit1Id.value()),
      "Feature commit (rebased 2)",
      "Alice",
      java.time.Instant.now(),
      PATCH_CONTENT
  );
  eventPublisher.publish(rebasedCommit2).get();

  // Wait for rebased commits
  await().atMost(Duration.ofSeconds(5))
      .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, rebasedCommit2Id).isPresent());

  // Create BranchRebasedEvent
  org.chucc.vcserver.event.BranchRebasedEvent rebaseEvent =
      new org.chucc.vcserver.event.BranchRebasedEvent(
      DEFAULT_DATASET,
      "feature",
      featureBranchId.value(), // old head
      rebasedCommit2Id.value(), // new head
      java.util.List.of(rebasedCommit1Id.value(), rebasedCommit2Id.value()), // new commits
      java.time.Instant.now()
  );

  // When - Publish rebase event
  eventPublisher.publish(rebaseEvent).get();

  // Then - Wait for branch update
  await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        // Verify feature branch now points to final rebased commit
        var branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "feature");
        assertThat(branch).isPresent();
        assertThat(branch.get().getCommitId()).isEqualTo(rebasedCommit2Id);
      });
}
```

**Key points:**
- Complex scenario: creates feature branch, then rebases it
- Tests the full rebase flow including branch head update
- Verifies branch points to final rebased commit

## Verification Steps

1. **Add tests to GraphEventProjectorIT**

2. **Run ONLY GraphEventProjectorIT**:
   ```bash
   mvn -q test -Dtest=GraphEventProjectorIT
   ```

   Expected: All tests pass (8 existing + 3 new = 11 tests)

3. **Run with coverage**:
   ```bash
   mvn -q test -Dtest=GraphEventProjectorIT jacoco:report
   ```

   Check coverage of:
   - ReadModelProjector.handleBranchCreated - should be covered
   - ReadModelProjector.handleBranchReset - should be covered
   - ReadModelProjector.handleBranchRebased - should be covered

4. **Verify logs**:
   - Should see "Processing BranchCreatedEvent" logs
   - Should see "Processing BranchResetEvent" logs
   - Should see "Processing BranchRebasedEvent" logs
   - Should see "Successfully projected event" logs

## Acceptance Criteria

- [ ] branchCreatedEvent_shouldBeProjected test added and passes
- [ ] branchResetEvent_shouldBeProjected test added and passes
- [ ] branchRebasedEvent_shouldBeProjected test added and passes
- [ ] GraphEventProjectorIT passes with 11 tests (8 + 3 new)
- [ ] ReadModelProjector branch lifecycle handlers have test coverage
- [ ] Zero Checkstyle violations
- [ ] Zero SpotBugs warnings

## Dependencies

- Task 08 must be completed (coverage analysis)
- Task 01-04 must be completed (projector disabled by default, enabled in GraphEventProjectorIT)

## Next Task

Task 10: Add Projector Tests for Version Control Operation Events (revert, cherry-pick, squash)

## Estimated Complexity

Medium (1.5-2 hours)
- Write branchCreatedEvent test: 20 minutes
- Write branchResetEvent test: 20 minutes
- Write branchRebasedEvent test: 40 minutes (more complex)
- Testing and debugging: 20-30 minutes
- Checkstyle/SpotBugs fixes: 10 minutes

## Notes

**Event Dependencies:**
- BranchCreatedEvent requires commit to exist first
- BranchResetEvent requires target commit to exist
- BranchRebasedEvent requires all rebased commits to exist

**await() Usage:**
- Use intermediate await() to ensure setup is complete
- Use final await() to verify projection
- Timeout should be 5-10 seconds (async processing)

**Test Data:**
- Reuse DEFAULT_DATASET, DEFAULT_BRANCH, initialCommitId from IntegrationTestFixture
- Generate new CommitIds for each test
- Use PATCH_CONTENT constant for RDF patches
