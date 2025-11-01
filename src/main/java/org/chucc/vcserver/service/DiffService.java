package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.exception.CommitNotFoundException;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.util.RdfPatchUtil;
import org.springframework.stereotype.Service;

/**
 * Service for computing diffs between commits.
 * Provides methods to generate RDF Patches representing changes between two commits.
 */
@Service
public class DiffService {

  private final DatasetService datasetService;
  private final CommitRepository commitRepository;

  /**
   * Constructor for DiffService.
   *
   * @param datasetService the dataset service for materializing commits
   * @param commitRepository the commit repository for validating commits
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Services and repositories are Spring-managed beans"
  )
  public DiffService(DatasetService datasetService, CommitRepository commitRepository) {
    this.datasetService = datasetService;
    this.commitRepository = commitRepository;
  }

  /**
   * Computes the RDF Patch representing changes from 'from' commit to 'to' commit.
   * Returns a patch where additions are quads in 'to' but not in 'from',
   * and deletions are quads in 'from' but not in 'to'.
   *
   * @param dataset the dataset name
   * @param fromCommitId the source commit ID
   * @param toCommitId the target commit ID
   * @return RDF Patch as string (text/rdf-patch format)
   * @throws CommitNotFoundException if either commit is not found
   */
  public String diffCommits(String dataset, CommitId fromCommitId, CommitId toCommitId) {
    // Validate commits exist
    if (!commitRepository.exists(dataset, fromCommitId)) {
      throw new CommitNotFoundException(
          "Commit not found: " + fromCommitId.value(), true);
    }
    if (!commitRepository.exists(dataset, toCommitId)) {
      throw new CommitNotFoundException(
          "Commit not found: " + toCommitId.value(), true);
    }

    // Load dataset states at both commits
    DatasetGraph fromDataset = datasetService.materializeCommit(dataset, fromCommitId);
    DatasetGraph toDataset = datasetService.materializeCommit(dataset, toCommitId);

    // Compute diff using existing utility
    RDFPatch patch = RdfPatchUtil.diff(fromDataset, toDataset);

    // Serialize to RDF Patch text format
    return serializePatch(patch);
  }

  /**
   * Serializes an RDF Patch to text format.
   *
   * @param patch the RDF patch to serialize
   * @return the patch as a string in text/rdf-patch format
   */
  private String serializePatch(RDFPatch patch) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    RDFPatchOps.write(out, patch);
    return out.toString(StandardCharsets.UTF_8);
  }
}
