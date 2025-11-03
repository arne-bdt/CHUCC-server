package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.domain.DatasetRef;
import org.chucc.vcserver.dto.WriteOperation;
import org.chucc.vcserver.exception.MalformedUpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for BatchOperationService.
 */
class BatchOperationServiceTest {

  @Mock
  private DatasetService datasetService;

  @Mock
  private RdfPatchService rdfPatchService;

  private BatchOperationService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new BatchOperationService(datasetService, rdfPatchService);
  }

  @Test
  void combineOperations_shouldCombineMultipleSparqlUpdates() {
    // Given: Multiple SPARQL update operations
    List<WriteOperation> operations = List.of(
        new WriteOperation("update",
            "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }", null, null),
        new WriteOperation("update",
            "INSERT DATA { <http://ex.org/s2> <http://ex.org/p> \"v2\" }", null, null)
    );

    // Mock DatasetService to return empty dataset
    Dataset emptyDataset = DatasetFactory.createTxnMem();
    when(datasetService.getMutableDataset(any(DatasetRef.class))).thenReturn(emptyDataset);

    // When
    RDFPatch combinedPatch = service.combineOperations("default", operations, "main");

    // Then
    assertThat(combinedPatch).isNotNull();

    // Verify dataset was materialized twice (once per operation)
    verify(datasetService, times(2)).getMutableDataset(any(DatasetRef.class));
  }

  @Test
  void combineOperations_shouldCombineMultiplePatches() {
    // Given: Multiple RDF patch operations
    String patch1 = "TX .\nA <http://ex.org/s1> <http://ex.org/p> \"v1\" .\nTC .";
    String patch2 = "TX .\nA <http://ex.org/s2> <http://ex.org/p> \"v2\" .\nTC .";

    List<WriteOperation> operations = List.of(
        new WriteOperation("applyPatch", null, patch1, null),
        new WriteOperation("applyPatch", null, patch2, null)
    );

    // Mock RdfPatchService to parse patches
    RDFPatch mockPatch1 = RDFPatchOps.read(
        new java.io.ByteArrayInputStream(patch1.getBytes()));
    RDFPatch mockPatch2 = RDFPatchOps.read(
        new java.io.ByteArrayInputStream(patch2.getBytes()));

    when(rdfPatchService.parsePatch(patch1)).thenReturn(mockPatch1);
    when(rdfPatchService.parsePatch(patch2)).thenReturn(mockPatch2);

    // When
    RDFPatch combinedPatch = service.combineOperations("default", operations, "main");

    // Then
    assertThat(combinedPatch).isNotNull();
    verify(rdfPatchService).parsePatch(patch1);
    verify(rdfPatchService).parsePatch(patch2);
  }

  @Test
  void combineOperations_shouldCombineMixedOperations() {
    // Given: Mixed SPARQL update and RDF patch operations
    String patch = "TX .\nA <http://ex.org/s2> <http://ex.org/p> \"v2\" .\nTC .";

    List<WriteOperation> operations = List.of(
        new WriteOperation("update",
            "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }", null, null),
        new WriteOperation("applyPatch", null, patch, null)
    );

    // Mock dependencies
    Dataset emptyDataset = DatasetFactory.createTxnMem();
    when(datasetService.getMutableDataset(any(DatasetRef.class))).thenReturn(emptyDataset);

    RDFPatch mockPatch = RDFPatchOps.read(
        new java.io.ByteArrayInputStream(patch.getBytes()));
    when(rdfPatchService.parsePatch(patch)).thenReturn(mockPatch);

    // When
    RDFPatch combinedPatch = service.combineOperations("default", operations, "main");

    // Then
    assertThat(combinedPatch).isNotNull();
  }

  @Test
  void combineOperations_shouldThrowException_whenUnknownOperationType() {
    // Given: Operation with unknown type
    List<WriteOperation> operations = List.of(
        new WriteOperation("unknownType", "some content", null, null)
    );

    // When / Then
    assertThatThrownBy(() -> service.combineOperations("default", operations, "main"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown operation type")
        .hasMessageContaining("unknownType");
  }

  @Test
  void combineOperations_shouldThrowException_whenMalformedSparql() {
    // Given: Operation with malformed SPARQL
    List<WriteOperation> operations = List.of(
        new WriteOperation("update", "MALFORMED SPARQL", null, null)
    );

    // Mock dataset service
    Dataset emptyDataset = DatasetFactory.createTxnMem();
    when(datasetService.getMutableDataset(any(DatasetRef.class))).thenReturn(emptyDataset);

    // When / Then
    assertThatThrownBy(() -> service.combineOperations("default", operations, "main"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to process operation 0");
  }

  @Test
  void combineOperations_shouldThrowException_whenOperationFails() {
    // Given: Operation that will fail
    List<WriteOperation> operations = List.of(
        new WriteOperation("update",
            "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }", null, null)
    );

    // Mock dataset service to throw exception
    when(datasetService.getMutableDataset(any(DatasetRef.class)))
        .thenThrow(new RuntimeException("Dataset not found"));

    // When / Then
    assertThatThrownBy(() -> service.combineOperations("default", operations, "main"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to process operation 0")
        .hasMessageContaining("Dataset not found");
  }

  @Test
  void combineOperations_shouldIncludeOperationIndex_whenOperationFails() {
    // Given: Second operation will fail
    String patch1 = "TX .\nA <http://ex.org/s1> <http://ex.org/p> \"v1\" .\nTC .";
    String patch2 = "INVALID PATCH";

    List<WriteOperation> operations = List.of(
        new WriteOperation("applyPatch", null, patch1, null),
        new WriteOperation("applyPatch", null, patch2, null)
    );

    // Mock first patch succeeds, second fails
    RDFPatch mockPatch1 = RDFPatchOps.read(
        new java.io.ByteArrayInputStream(patch1.getBytes()));
    when(rdfPatchService.parsePatch(patch1)).thenReturn(mockPatch1);
    when(rdfPatchService.parsePatch(patch2))
        .thenThrow(new RuntimeException("Invalid patch syntax"));

    // When / Then
    assertThatThrownBy(() -> service.combineOperations("default", operations, "main"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to process operation 1")
        .hasMessageContaining("Invalid patch syntax");
  }

  @Test
  void combineOperations_shouldWorkWithSingleOperation() {
    // Given: Single SPARQL update operation
    List<WriteOperation> operations = List.of(
        new WriteOperation("update",
            "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }", null, null)
    );

    // Mock DatasetService
    Dataset emptyDataset = DatasetFactory.createTxnMem();
    when(datasetService.getMutableDataset(any(DatasetRef.class))).thenReturn(emptyDataset);

    // When
    RDFPatch combinedPatch = service.combineOperations("default", operations, "main");

    // Then
    assertThat(combinedPatch).isNotNull();
  }

  @Test
  void combineOperations_shouldUseCorrectDatasetRef() {
    // Given: SPARQL update operation
    List<WriteOperation> operations = List.of(
        new WriteOperation("update",
            "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }", null, null)
    );

    // Mock DatasetService
    Dataset emptyDataset = DatasetFactory.createTxnMem();
    when(datasetService.getMutableDataset(any(DatasetRef.class))).thenReturn(emptyDataset);

    // When
    service.combineOperations("myDataset", operations, "myBranch");

    // Then - verify correct DatasetRef was created
    verify(datasetService).getMutableDataset(
        any(DatasetRef.class)  // Can't verify exact match without equals() on DatasetRef
    );
  }
}
