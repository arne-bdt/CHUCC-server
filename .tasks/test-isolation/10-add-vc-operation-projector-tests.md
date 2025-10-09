# Task 10: Add Projector Tests for Version Control Operation Events

## Objective
Add comprehensive projector tests to GraphEventProjectorIT for version control operation event handlers: RevertCreatedEvent, CherryPickedEvent, and CommitsSquashedEvent.

## Background
These event handlers process version control operations that create new commits and update branches. With projector disabled by default, we need dedicated tests to verify these handlers work correctly.

## Prerequisite
Complete Task 08 to confirm which handlers need tests. This task assumes these handlers lack dedicated projector tests.

## Tasks

### 1. Add Test for RevertCreatedEvent Projection

Add to `src/test/java/org/chucc/vcserver/integration/GraphEventProjectorIT.java`:

```java
/**
 * Test that RevertCreatedEvent is projected correctly.
 */
@Test
void revertCreatedEvent_shouldBeProjected() throws Exception {
  // Given - Create a commit to revert
  CommitId commitToRevertId = CommitId.generate();

  org.chucc.vcserver.event.CommitCreatedEvent originalCommit =
      new org.chucc.vcserver.event.CommitCreatedEvent(
      DEFAULT_DATASET,
      commitToRevertId.value(),
      java.util.List.of(initialCommitId.value()),
      "Original commit to revert",
      "Alice",
      java.time.Instant.now(),
      PATCH_CONTENT
  );
  eventPublisher.publish(originalCommit).get();

  // Wait for commit
  await().atMost(Duration.ofSeconds(5))
      .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, commitToRevertId).isPresent());

  // Update main branch to point to this commit (needed for revert context)
  branchRepository.updateBranchHead(DEFAULT_DATASET, DEFAULT_BRANCH, commitToRevertId);

  // Create revert commit
  CommitId revertCommitId = CommitId.generate();

  // Create RevertCreatedEvent
  org.chucc.vcserver.event.RevertCreatedEvent revertEvent =
      new org.chucc.vcserver.event.RevertCreatedEvent(
      DEFAULT_DATASET,
      revertCommitId.value(),
      commitToRevertId.value(),
      DEFAULT_BRANCH,
      "Revert commit",
      "Bob",
      java.time.Instant.now(),
      "TX .\\nD <http://example.org/s> <http://example.org/p> \\\"value\\\" .\\nTC ." // inverse patch
  );

  // When - Publish revert event
  eventPublisher.publish(revertEvent).get();

  // Then - Wait for async event projection
  await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        // Verify revert commit was saved
        var commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, revertCommitId);
        assertThat(commit).isPresent();
        assertThat(commit.get().message()).isEqualTo("Revert commit");
        assertThat(commit.get().author()).isEqualTo("Bob");

        // Verify main branch was updated to point to revert commit
        var branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, DEFAULT_BRANCH);
        assertThat(branch).isPresent();
        assertThat(branch.get().getCommitId()).isEqualTo(revertCommitId);
      });
}
```

**Key points:**
- Creates original commit, then reverts it
- RevertCreatedEvent includes both commit creation and branch update
- Verifies commit saved and branch head updated

### 2. Add Test for CherryPickedEvent Projection

Add to GraphEventProjectorIT:

```java
/**
 * Test that CherryPickedEvent is projected correctly.
 */
@Test
void cherryPickedEvent_shouldBeProjected() throws Exception {
  // Given - Create source commit to cherry-pick from
  CommitId sourceCommitId = CommitId.generate();

  org.chucc.vcserver.event.CommitCreatedEvent sourceCommit =
      new org.chucc.vcserver.event.CommitCreatedEvent(
      DEFAULT_DATASET,
      sourceCommitId.value(),
      java.util.List.of(initialCommitId.value()),
      "Source commit to cherry-pick",
      "Alice",
      java.time.Instant.now(),
      PATCH_CONTENT
  );
  eventPublisher.publish(sourceCommit).get();

  // Wait for source commit
  await().atMost(Duration.ofSeconds(5))
      .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, sourceCommitId).isPresent());

  // Create target branch
  CommitId targetBranchCommitId = CommitId.generate();

  org.chucc.vcserver.event.CommitCreatedEvent targetBranchCommit =
      new org.chucc.vcserver.event.CommitCreatedEvent(
      DEFAULT_DATASET,
      targetBranchCommitId.value(),
      java.util.List.of(initialCommitId.value()),
      "Target branch commit",
      "Bob",
      java.time.Instant.now(),
      PATCH_CONTENT
  );
  eventPublisher.publish(targetBranchCommit).get();

  org.chucc.vcserver.event.BranchCreatedEvent branchEvent =
      new org.chucc.vcserver.event.BranchCreatedEvent(
      DEFAULT_DATASET,
      "target-branch",
      targetBranchCommitId.value(),
      java.time.Instant.now()
  );
  eventPublisher.publish(branchEvent).get();

  // Wait for target branch
  await().atMost(Duration.ofSeconds(5))
      .until(() -> branchRepository.findByDatasetAndName(DEFAULT_DATASET, "target-branch").isPresent());

  // Create cherry-picked commit
  CommitId cherryPickedCommitId = CommitId.generate();

  // Create CherryPickedEvent
  org.chucc.vcserver.event.CherryPickedEvent cherryPickEvent =
      new org.chucc.vcserver.event.CherryPickedEvent(
      DEFAULT_DATASET,
      cherryPickedCommitId.value(),
      sourceCommitId.value(),
      "target-branch",
      "Cherry-pick: Source commit to cherry-pick",
      "Charlie",
      java.time.Instant.now(),
      PATCH_CONTENT
  );

  // When - Publish cherry-pick event
  eventPublisher.publish(cherryPickEvent).get();

  // Then - Wait for async event projection
  await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        // Verify cherry-picked commit was saved
        var commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, cherryPickedCommitId);
        assertThat(commit).isPresent();
        assertThat(commit.get().message()).contains("Cherry-pick");
        assertThat(commit.get().author()).isEqualTo("Charlie");

        // Verify target branch was updated to point to cherry-picked commit
        var branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "target-branch");
        assertThat(branch).isPresent();
        assertThat(branch.get().getCommitId()).isEqualTo(cherryPickedCommitId);
      });
}
```

**Key points:**
- Creates source commit and target branch
- Cherry-picks source commit to target branch
- Verifies new commit created and target branch updated

### 3. Add Test for CommitsSquashedEvent Projection

Add to GraphEventProjectorIT:

```java
/**
 * Test that CommitsSquashedEvent is projected correctly.
 */
@Test
void commitsSquashedEvent_shouldBeProjected() throws Exception {
  // Given - Create multiple commits to squash
  CommitId commit1Id = CommitId.generate();
  CommitId commit2Id = CommitId.generate();
  CommitId commit3Id = CommitId.generate();

  // Create commit chain
  org.chucc.vcserver.event.CommitCreatedEvent commit1 =
      new org.chucc.vcserver.event.CommitCreatedEvent(
      DEFAULT_DATASET,
      commit1Id.value(),
      java.util.List.of(initialCommitId.value()),
      "Commit 1",
      "Alice",
      java.time.Instant.now(),
      PATCH_CONTENT
  );
  eventPublisher.publish(commit1).get();

  org.chucc.vcserver.event.CommitCreatedEvent commit2 =
      new org.chucc.vcserver.event.CommitCreatedEvent(
      DEFAULT_DATASET,
      commit2Id.value(),
      java.util.List.of(commit1Id.value()),
      "Commit 2",
      "Alice",
      java.time.Instant.now().plusMillis(1),
      PATCH_CONTENT
  );
  eventPublisher.publish(commit2).get();

  org.chucc.vcserver.event.CommitCreatedEvent commit3 =
      new org.chucc.vcserver.event.CommitCreatedEvent(
      DEFAULT_DATASET,
      commit3Id.value(),
      java.util.List.of(commit2Id.value()),
      "Commit 3",
      "Alice",
      java.time.Instant.now().plusMillis(2),
      PATCH_CONTENT
  );
  eventPublisher.publish(commit3).get();

  // Create feature branch pointing to commit3
  org.chucc.vcserver.event.BranchCreatedEvent branchEvent =
      new org.chucc.vcserver.event.BranchCreatedEvent(
      DEFAULT_DATASET,
      "feature",
      commit3Id.value(),
      java.time.Instant.now()
  );
  eventPublisher.publish(branchEvent).get();

  // Wait for setup
  await().atMost(Duration.ofSeconds(5))
      .until(() -> branchRepository.findByDatasetAndName(DEFAULT_DATASET, "feature").isPresent());

  // Create squashed commit
  CommitId squashedCommitId = CommitId.generate();

  org.chucc.vcserver.event.CommitCreatedEvent squashedCommit =
      new org.chucc.vcserver.event.CommitCreatedEvent(
      DEFAULT_DATASET,
      squashedCommitId.value(),
      java.util.List.of(initialCommitId.value()),
      "Squashed commits 1-3",
      "Alice",
      java.time.Instant.now(),
      PATCH_CONTENT
  );
  eventPublisher.publish(squashedCommit).get();

  // Wait for squashed commit
  await().atMost(Duration.ofSeconds(5))
      .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, squashedCommitId).isPresent());

  // Create CommitsSquashedEvent
  org.chucc.vcserver.event.CommitsSquashedEvent squashEvent =
      new org.chucc.vcserver.event.CommitsSquashedEvent(
      DEFAULT_DATASET,
      "feature",
      commit3Id.value(), // previous head
      squashedCommitId.value(), // new head
      java.util.List.of(commit1Id.value(), commit2Id.value(), commit3Id.value()), // squashed commits
      java.time.Instant.now()
  );

  // When - Publish squash event
  eventPublisher.publish(squashEvent).get();

  // Then - Wait for branch update
  await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        // Verify feature branch now points to squashed commit
        var branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "feature");
        assertThat(branch).isPresent();
        assertThat(branch.get().getCommitId()).isEqualTo(squashedCommitId);
      });
}
```

**Key points:**
- Creates commit chain (commit1 → commit2 → commit3)
- Squashes them into single commit
- Verifies branch updated to point to squashed commit

## Verification Steps

1. **Add tests to GraphEventProjectorIT**

2. **Run ONLY GraphEventProjectorIT**:
   ```bash
   mvn -q test -Dtest=GraphEventProjectorIT
   ```

   Expected: All tests pass (11 from Task 09 + 3 new = 14 tests)

3. **Run with coverage**:
   ```bash
   mvn -q test -Dtest=GraphEventProjectorIT jacoco:report
   ```

   Check coverage of:
   - ReadModelProjector.handleRevertCreated - should be covered
   - ReadModelProjector.handleCherryPicked - should be covered
   - ReadModelProjector.handleCommitsSquashed - should be covered

4. **Verify logs**:
   - Should see "Processing RevertCreatedEvent" logs
   - Should see "Processing CherryPickedEvent" logs
   - Should see "Processing CommitsSquashedEvent" logs
   - Should see "Successfully projected event" logs

## Acceptance Criteria

- [ ] revertCreatedEvent_shouldBeProjected test added and passes
- [ ] cherryPickedEvent_shouldBeProjected test added and passes
- [ ] commitsSquashedEvent_shouldBeProjected test added and passes
- [ ] GraphEventProjectorIT passes with 14 tests (11 + 3 new)
- [ ] ReadModelProjector VC operation handlers have test coverage
- [ ] Zero Checkstyle violations
- [ ] Zero SpotBugs warnings

## Dependencies

- Task 09 must be completed (branch lifecycle tests added)
- Task 08 must be completed (coverage analysis)

## Next Task

Task 11: Update CLAUDE.md with Testing Strategy Documentation

## Estimated Complexity

Medium-High (2-2.5 hours)
- Write revertCreatedEvent test: 30 minutes
- Write cherryPickedEvent test: 40 minutes (complex setup)
- Write commitsSquashedEvent test: 40 minutes (complex setup)
- Testing and debugging: 20-30 minutes
- Checkstyle/SpotBugs fixes: 10 minutes

## Notes

**Event Dependencies:**
- RevertCreatedEvent requires original commit and branch to exist
- CherryPickedEvent requires source commit and target branch to exist
- CommitsSquashedEvent requires all original commits, squashed commit, and branch to exist

**Complex Scenarios:**
- Cherry-pick requires setting up two different branches
- Squash requires creating a commit chain
- All operations involve both commit creation and branch updates

**Test Data:**
- Use different authors (Alice, Bob, Charlie) for traceability
- Use descriptive commit messages
- Use realistic patch content (PATCH_CONTENT constant)

**Coverage Goal:**
After this task, GraphEventProjectorIT should cover 9 out of 10 event handlers:
1. ✅ handleCommitCreated (Task 08 existing)
2. ✅ handleBranchCreated (Task 09)
3. ✅ handleBranchReset (Task 09)
4. ✅ handleBranchRebased (Task 09)
5. ⚠️ handleTagCreated (may skip - tags are synchronous operations)
6. ✅ handleRevertCreated (Task 10)
7. ⚠️ handleSnapshotCreated (may skip - snapshot feature status TBD)
8. ✅ handleCherryPicked (Task 10)
9. ✅ handleCommitsSquashed (Task 10)
10. ✅ handleBatchGraphsCompleted (Task 08 existing)
