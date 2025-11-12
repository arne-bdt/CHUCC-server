package org.chucc.vcserver.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.chucc.vcserver.util.PrefixValidator;
import org.springframework.stereotype.Component;

/**
 * Handles prefix update commands by generating RDFPatch with PA/PD directives.
 *
 * <p>This handler delegates to CreateCommitCommandHandler - no new events needed.
 * Prefix changes are version-controlled via the existing CQRS infrastructure.
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class UpdatePrefixesCommandHandler {

  private final MaterializedBranchRepository materializedBranchRepository;
  private final BranchRepository branchRepository;
  private final CreateCommitCommandHandler createCommitCommandHandler;

  /**
   * Creates an update prefixes command handler.
   *
   * @param materializedBranchRepository the materialized branch repository
   * @param branchRepository the branch repository
   * @param createCommitCommandHandler the commit creation handler
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories and handlers are Spring-managed beans "
          + "and are intentionally shared")
  public UpdatePrefixesCommandHandler(
      MaterializedBranchRepository materializedBranchRepository,
      BranchRepository branchRepository,
      CreateCommitCommandHandler createCommitCommandHandler) {
    this.materializedBranchRepository = materializedBranchRepository;
    this.branchRepository = branchRepository;
    this.createCommitCommandHandler = createCommitCommandHandler;
  }

  /**
   * Handles prefix update command.
   *
   * @param cmd the update prefixes command
   * @return commit created event
   * @throws BranchNotFoundException if branch doesn't exist
   * @throws IllegalArgumentException if prefix names or IRIs are invalid
   */
  public CommitCreatedEvent handle(UpdatePrefixesCommand cmd) {
    // 1. Validate prefix names and IRIs
    cmd.newPrefixes().forEach((prefixName, iri) -> {
      PrefixValidator.validatePrefixName(prefixName);
      // Only validate IRIs for PUT and PATCH operations (DELETE doesn't use IRI values)
      if (cmd.operation() != UpdatePrefixesCommand.Operation.DELETE) {
        PrefixValidator.validateAbsoluteIri(iri);
      }
    });

    // 2. Validate branch exists
    branchRepository.findByDatasetAndName(cmd.dataset(), cmd.branch())
        .orElseThrow(() -> new BranchNotFoundException(cmd.branch()));

    // 3. Get current prefixes from materialized branch
    DatasetGraph currentDsg = materializedBranchRepository
        .getBranchGraph(cmd.dataset(), cmd.branch());
    Map<String, String> oldPrefixes = currentDsg.getDefaultGraph()
        .getPrefixMapping()
        .getNsPrefixMap();

    // 4. Generate RDFPatch with PA/PD directives
    RDFPatch patch = buildPrefixPatch(oldPrefixes, cmd.newPrefixes(), cmd.operation());

    // 5. Create commit via existing handler
    String message = cmd.message().orElseGet(() -> generateDefaultMessage(cmd));
    CreateCommitCommand commitCmd = new CreateCommitCommand(
        cmd.dataset(),
        cmd.branch(),
        null,  // baseCommitId (use branch HEAD)
        null,  // sparqlUpdate
        RDFPatchOps.str(patch),  // patch
        message,
        cmd.author(),
        Map.of()  // metadata
    );

    // CreateCommitCommandHandler returns VersionControlEvent, which is CommitCreatedEvent
    return (CommitCreatedEvent) createCommitCommandHandler.handle(commitCmd);
  }

  /**
   * Builds RDFPatch with PA/PD directives.
   *
   * <p>RDFPatch format:
   * <ul>
   *   <li>PA prefix: &lt;iri&gt; . - Add prefix</li>
   *   <li>PD prefix: . - Delete prefix</li>
   * </ul>
   *
   * @param oldPrefixes current prefix mappings
   * @param newPrefixes new prefix mappings
   * @param operation the operation type
   * @return RDFPatch with prefix directives
   */
  RDFPatch buildPrefixPatch(
      Map<String, String> oldPrefixes,
      Map<String, String> newPrefixes,
      UpdatePrefixesCommand.Operation operation) {

    StringBuilder patchStr = new StringBuilder("TX .\n");

    switch (operation) {
      case PUT -> {
        // Remove all old prefixes
        oldPrefixes.forEach((prefix, iri) ->
            patchStr.append("PD \"").append(prefix).append("\" .\n"));
        // Add all new prefixes
        newPrefixes.forEach((prefix, iri) ->
            patchStr.append("PA \"").append(prefix).append("\" <").append(iri).append("> .\n"));
      }
      case PATCH -> {
        // Add/update only specified prefixes
        newPrefixes.forEach((prefix, iri) ->
            patchStr.append("PA \"").append(prefix).append("\" <").append(iri).append("> .\n"));
      }
      case DELETE -> {
        // Remove specified prefixes
        newPrefixes.keySet().forEach(prefix ->
            patchStr.append("PD \"").append(prefix).append("\" .\n"));
      }
      default -> throw new IllegalArgumentException("Unknown operation: " + operation);
    }

    patchStr.append("TC .\n");
    return RDFPatchOps.read(
        new ByteArrayInputStream(patchStr.toString().getBytes(StandardCharsets.UTF_8))
    );
  }

  /**
   * Generates default commit message based on operation.
   *
   * @param cmd the command
   * @return default message
   */
  private String generateDefaultMessage(UpdatePrefixesCommand cmd) {
    return switch (cmd.operation()) {
      case PUT -> "Replace prefix map";
      case PATCH -> "Add prefixes: " + String.join(", ", cmd.newPrefixes().keySet());
      case DELETE -> "Remove prefixes: " + String.join(", ", cmd.newPrefixes().keySet());
    };
  }
}
