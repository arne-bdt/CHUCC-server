package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.vocabulary.RDF;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for generating SPARQL 1.1 Service Description.
 * Implements W3C SPARQL 1.1 Service Description specification.
 *
 * @see <a href="https://www.w3.org/TR/sparql11-service-description/">
 *   SPARQL 1.1 Service Description</a>
 */
@Service
public class ServiceDescriptionService {

  private static final String SD_NS = "http://www.w3.org/ns/sparql-service-description#";
  private static final String VC_NS = "http://chucc.org/ns/version-control#";
  private static final String VOID_NS = "http://rdfs.org/ns/void#";

  private final String baseUrl;
  private final BranchRepository branchRepository;
  private final MaterializedBranchRepository materializedBranchRepository;

  /**
   * Constructs service description service.
   *
   * @param baseUrl Base URL of the service (configurable via application.yml)
   * @param branchRepository Repository for branch lookups
   * @param materializedBranchRepository Repository for materialized graphs
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "All dependencies are Spring-managed beans and are intentionally shared")
  public ServiceDescriptionService(
      @Value("${server.base-url:http://localhost:8080}") String baseUrl,
      BranchRepository branchRepository,
      MaterializedBranchRepository materializedBranchRepository) {
    this.baseUrl = baseUrl;
    this.branchRepository = branchRepository;
    this.materializedBranchRepository = materializedBranchRepository;
  }

  /**
   * Generates service description model.
   * Creates RDF model describing SPARQL service capabilities,
   * supported features, and version control extensions.
   *
   * @return RDF model describing the service
   */
  public Model generateServiceDescription() {
    Model model = ModelFactory.createDefaultModel();

    // Set namespace prefixes
    model.setNsPrefix("sd", SD_NS);
    model.setNsPrefix("vc", VC_NS);
    model.setNsPrefix("void", VOID_NS);

    // Create service resource
    Resource service = model.createResource(baseUrl + "/sparql");
    service.addProperty(RDF.type, model.createResource(SD_NS + "Service"));

    // Endpoint
    Property endpointProp = model.createProperty(SD_NS + "endpoint");
    service.addProperty(endpointProp, model.createResource(baseUrl + "/sparql"));

    // Supported languages
    addSupportedLanguages(model, service);

    // Features
    addFeatures(model, service);

    // Result formats
    addResultFormats(model, service);

    // Extension features
    addExtensionFeatures(model, service);

    // Datasets
    addDatasets(model, service);

    return model;
  }

  /**
   * Adds supported SPARQL languages to the service description.
   *
   * @param model RDF model
   * @param service Service resource
   */
  private void addSupportedLanguages(Model model, Resource service) {
    Property supportedLanguage = model.createProperty(SD_NS + "supportedLanguage");

    service.addProperty(
        supportedLanguage,
        model.createResource(SD_NS + "SPARQL11Query"));
    service.addProperty(
        supportedLanguage,
        model.createResource(SD_NS + "SPARQL11Update"));
  }

  /**
   * Adds standard SPARQL 1.1 features to the service description.
   *
   * @param model RDF model
   * @param service Service resource
   */
  private void addFeatures(Model model, Resource service) {
    Property feature = model.createProperty(SD_NS + "feature");

    // Standard SPARQL 1.1 features
    service.addProperty(
        feature,
        model.createResource(SD_NS + "UnionDefaultGraph"));
    service.addProperty(
        feature,
        model.createResource(SD_NS + "BasicFederatedQuery"));
  }

  /**
   * Adds supported result formats to the service description.
   *
   * @param model RDF model
   * @param service Service resource
   */
  private void addResultFormats(Model model, Resource service) {
    Property resultFormat = model.createProperty(SD_NS + "resultFormat");

    // SPARQL Results formats
    service.addProperty(
        resultFormat,
        model.createResource("http://www.w3.org/ns/formats/SPARQL_Results_JSON"));
    service.addProperty(
        resultFormat,
        model.createResource("http://www.w3.org/ns/formats/SPARQL_Results_XML"));
    service.addProperty(
        resultFormat,
        model.createResource("http://www.w3.org/ns/formats/SPARQL_Results_CSV"));

    // RDF serialization formats
    service.addProperty(
        resultFormat,
        model.createResource("http://www.w3.org/ns/formats/Turtle"));
    service.addProperty(
        resultFormat,
        model.createResource("http://www.w3.org/ns/formats/RDF_XML"));
    service.addProperty(
        resultFormat,
        model.createResource("http://www.w3.org/ns/formats/JSON-LD"));
  }

  /**
   * Adds CHUCC version control extension features to the service description.
   *
   * @param model RDF model
   * @param service Service resource
   */
  private void addExtensionFeatures(Model model, Resource service) {
    Property feature = model.createProperty(SD_NS + "feature");
    Property versionControlEndpoint = model.createProperty(VC_NS + "versionControlEndpoint");
    Property patchFormat = model.createProperty(VC_NS + "patchFormat");

    // Version control features
    service.addProperty(feature, model.createResource(VC_NS + "Merge"));
    service.addProperty(feature, model.createResource(VC_NS + "Rebase"));
    service.addProperty(feature, model.createResource(VC_NS + "CherryPick"));
    service.addProperty(feature, model.createResource(VC_NS + "TimeTravel"));
    service.addProperty(feature, model.createResource(VC_NS + "Blame"));
    service.addProperty(feature, model.createResource(VC_NS + "Diff"));
    service.addProperty(feature, model.createResource(VC_NS + "Squash"));
    service.addProperty(feature, model.createResource(VC_NS + "Reset"));
    service.addProperty(feature, model.createResource(VC_NS + "Revert"));

    // Version control endpoint
    service.addProperty(
        versionControlEndpoint,
        model.createResource(baseUrl + "/version"));

    // Patch format
    service.addProperty(
        patchFormat,
        model.createResource("http://www.w3.org/ns/formats/RDF_Patch"));
  }

  /**
   * Adds available datasets to the service description.
   * Discovers datasets dynamically from the repository and describes
   * their graphs and endpoints.
   *
   * @param model RDF model
   * @param service Service resource
   */
  private void addDatasets(Model model, Resource service) {
    // Get all datasets
    List<String> datasetNames = branchRepository.findAllDatasetNames();

    for (String datasetName : datasetNames) {
      Resource dataset = addDataset(model, service, datasetName);
      addGraphsForDataset(model, dataset, datasetName);
    }
  }

  /**
   * Adds a single dataset to the service description.
   *
   * @param model RDF model
   * @param service Service resource
   * @param datasetName Dataset name
   * @return Dataset resource
   */
  private Resource addDataset(Model model, Resource service, String datasetName) {
    String datasetUri = baseUrl + "/" + datasetName;
    Resource dataset = model.createResource(datasetUri);

    // Link from service
    Property availableGraphs = model.createProperty(SD_NS + "availableGraphs");
    service.addProperty(availableGraphs, dataset);

    // Dataset metadata
    dataset.addProperty(RDF.type, model.createResource(SD_NS + "Dataset"));

    Property sparqlEndpoint = model.createProperty(VOID_NS + "sparqlEndpoint");
    dataset.addProperty(
        sparqlEndpoint,
        model.createResource(datasetUri + "/sparql"));

    return dataset;
  }

  /**
   * Adds graph descriptions for a dataset.
   * Enumerates named graphs and default graph from the main branch HEAD.
   *
   * @param model RDF model
   * @param dataset Dataset resource
   * @param datasetName Dataset name
   */
  private void addGraphsForDataset(Model model, Resource dataset, String datasetName) {
    // Get main branch
    Optional<Branch> mainBranch = branchRepository.findByDatasetAndName(datasetName, "main");
    if (mainBranch.isEmpty()) {
      return;
    }

    // Get materialized graph at main branch HEAD
    DatasetGraph dsg = materializedBranchRepository.getBranchGraph(datasetName, "main");

    // Add named graphs
    Iterator<Node> graphNames = dsg.listGraphNodes();
    while (graphNames.hasNext()) {
      Node graphNode = graphNames.next();
      if (!graphNode.equals(Quad.defaultGraphIRI)) {
        Graph graph = dsg.getGraph(graphNode);
        long tripleCount = graph.size();

        Resource namedGraphDesc = model.createResource();
        Property sdName = model.createProperty(SD_NS + "name");
        namedGraphDesc.addProperty(
            sdName,
            model.createResource(graphNode.getURI()));

        Resource graphResource = createGraphResourceWithSize(model, tripleCount);

        Property sdGraph = model.createProperty(SD_NS + "graph");
        namedGraphDesc.addProperty(sdGraph, graphResource);

        Property sdNamedGraph = model.createProperty(SD_NS + "namedGraph");
        dataset.addProperty(sdNamedGraph, namedGraphDesc);
      }
    }

    // Add default graph (always present)
    Graph defaultGraph = dsg.getDefaultGraph();
    long defaultTripleCount = defaultGraph.size();

    Resource defaultGraphDesc = model.createResource();
    Resource defaultGraphResource = createGraphResourceWithSize(model, defaultTripleCount);

    Property sdGraph = model.createProperty(SD_NS + "graph");
    defaultGraphDesc.addProperty(sdGraph, defaultGraphResource);

    Property sdDefaultGraph = model.createProperty(SD_NS + "defaultGraph");
    dataset.addProperty(sdDefaultGraph, defaultGraphDesc);
  }

  /**
   * Creates a graph resource with type and triple count.
   * Helper method to avoid code duplication in graph descriptions.
   *
   * @param model RDF model
   * @param tripleCount Number of triples in the graph
   * @return Graph resource with type and void:triples property
   */
  private Resource createGraphResourceWithSize(Model model, long tripleCount) {
    Resource graphResource = model.createResource();
    graphResource.addProperty(RDF.type, model.createResource(SD_NS + "Graph"));

    Property voidTriples = model.createProperty(VOID_NS + "triples");
    graphResource.addProperty(voidTriples, model.createTypedLiteral(tripleCount));

    return graphResource;
  }
}
