package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Optional;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.dto.CommitMetadataDto;
import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.stereotype.Service;

/**
 * Service for commit-related operations.
 * This is a read-only service that queries the CommitRepository directly
 * (no CQRS commands needed for queries).
 */
@Service
public class CommitService {

  private final CommitRepository commitRepository;

  /**
   * Constructs a CommitService.
   *
   * @param commitRepository the commit repository
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "Repository is a Spring-managed bean and is intentionally shared")
  public CommitService(CommitRepository commitRepository) {
    this.commitRepository = commitRepository;
  }

  /**
   * Retrieves commit metadata by dataset and commit ID.
   *
   * @param dataset the dataset name
   * @param commitId the commit ID string
   * @return an Optional containing the commit metadata if found
   */
  public Optional<CommitMetadataDto> getCommitMetadata(String dataset, String commitId) {
    return commitRepository.findByDatasetAndId(dataset, CommitId.of(commitId))
        .map(commit -> new CommitMetadataDto(
            commit.id().toString(),
            commit.message(),
            commit.author(),
            commit.timestamp(),
            commit.parents().stream()
                .map(CommitId::toString)
                .toList(),
            commit.patchSize()
        ));
  }
}
