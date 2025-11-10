package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.vocabulary.RDF;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Tag;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.chucc.vcserver.repository.TagRepository;
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
  private final TagRepository tagRepository;
  private final MaterializedBranchRepository materializedBranchRepository;

  /**
   * Constructs service description service.
   *
   * @param baseUrl Base URL of the service (configurable via application.yml)
   * @param branchRepository Repository for branch lookups
   * @param tagRepository Repository for tag lookups
   * @param materializedBranchRepository Repository for materialized graphs
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "All dependencies are Spring-managed beans and are intentionally shared")
  public ServiceDescriptionService(
      @Value("${server.base-url:http://localhost:8080}") String baseUrl,
      BranchRepository branchRepository,
      TagRepository tagRepository,
      MaterializedBranchRepository materializedBranchRepository) {
    this.baseUrl = baseUrl;
    this.branchRepository = branchRepository;
    this.tagRepository = tagRepository;
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

    // Input formats
    addInputFormats(model, service);

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
   * Detects features supported by Apache Jena ARQ.
   *
   * @param model RDF model
   * @param service Service resource
   */
  private void addFeatures(Model model, Resource service) {
    Property feature = model.createProperty(SD_NS + "feature");

    // Standard SPARQL 1.1 features (always supported by Jena)
    service.addProperty(
        feature,
        model.createResource(SD_NS + "UnionDefaultGraph"));
    service.addProperty(
        feature,
        model.createResource(SD_NS + "BasicFederatedQuery"));

    // Optional features supported by Jena ARQ
    // Property Paths - supported by Jena ARQ
    service.addProperty(
        feature,
        model.createResource(SD_NS + "PropertyPaths"));

    // Aggregates - supported by Jena ARQ
    service.addProperty(
        feature,
        model.createResource(SD_NS + "Aggregates"));

    // Subqueries - supported by Jena ARQ
    service.addProperty(
        feature,
        model.createResource(SD_NS + "SubQueries"));
  }

  /**
   * Adds supported result formats to the service description.
   * Dynamically detects RDF formats supported by Jena.
   *
   * @param model RDF model
   * @param service Service resource
   */
  private void addResultFormats(Model model, Resource service) {
    // SPARQL Results formats (from RDFLanguages)
    addResultFormat(service, model, "http://www.w3.org/ns/formats/SPARQL_Results_JSON");
    addResultFormat(service, model, "http://www.w3.org/ns/formats/SPARQL_Results_XML");
    addResultFormat(service, model, "http://www.w3.org/ns/formats/SPARQL_Results_CSV");
    addResultFormat(service, model, "http://www.w3.org/ns/formats/SPARQL_Results_TSV");

    // RDF serialization formats (supported by Jena)
    for (Lang lang : RDFLanguages.getRegisteredLanguages()) {
      if (lang.equals(Lang.TURTLE)
          || lang.equals(Lang.RDFXML)
          || lang.equals(Lang.JSONLD)
          || lang.equals(Lang.NTRIPLES)
          || lang.equals(Lang.NQUADS)
          || lang.equals(Lang.TRIG)) {
        String formatUri = "http://www.w3.org/ns/formats/" + lang.getName();
        addResultFormat(service, model, formatUri);
      }
    }
  }

  /**
   * Adds a single result format to the service description.
   * Helper method to avoid code duplication.
   *
   * @param service Service resource
   * @param model RDF model
   * @param formatUri Format URI
   */
  private void addResultFormat(Resource service, Model model, String formatUri) {
    service.addProperty(
        model.createProperty(SD_NS + "resultFormat"),
        model.createResource(formatUri));
  }

  /**
   * Adds supported input formats to the service description.
   * These formats are accepted for SPARQL UPDATE and GSP operations.
   *
   * @param model RDF model
   * @param service Service resource
   */
  private void addInputFormats(Model model, Resource service) {
    Property inputFormat = model.createProperty(SD_NS + "inputFormat");

    // Input formats for SPARQL UPDATE and GSP
    service.addProperty(
        inputFormat,
        model.createResource("http://www.w3.org/ns/formats/Turtle"));
    service.addProperty(
        inputFormat,
        model.createResource("http://www.w3.org/ns/formats/RDF_XML"));
    service.addProperty(
        inputFormat,
        model.createResource("http://www.w3.org/ns/formats/JSON-LD"));
    service.addProperty(
        inputFormat,
        model.createResource("http://www.w3.org/ns/formats/N-Triples"));
    service.addProperty(
        inputFormat,
        model.createResource("http://www.w3.org/ns/formats/N-Quads"));
    service.addProperty(
        inputFormat,
        model.createResource("http://www.w3.org/ns/formats/TriG"));
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
    dataset.addProperty(RDF.type, model.createResource(VC_NS + "VersionedDataset"));

    Property sparqlEndpoint = model.createProperty(VOID_NS + "sparqlEndpoint");
    dataset.addProperty(
        sparqlEndpoint,
        model.createResource(datasetUri + "/sparql"));

    // Add version control metadata
    addVersionControlMetadata(model, dataset, datasetName);

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

  /**
   * Adds version control metadata to a dataset resource.
   * Includes default branch, all branches, and all tags.
   *
   * @param model RDF model
   * @param dataset Dataset resource
   * @param datasetName Dataset name
   */
  private void addVersionControlMetadata(Model model, Resource dataset, String datasetName) {
    // Default branch
    Optional<Branch> mainBranch = branchRepository.findByDatasetAndName(datasetName, "main");
    if (mainBranch.isPresent()) {
      Property defaultBranch = model.createProperty(VC_NS + "defaultBranch");
      dataset.addProperty(defaultBranch, model.createLiteral("main"));
    }

    // Add all branches
    List<Branch> branches = branchRepository.findAllByDataset(datasetName);
    Property branchProp = model.createProperty(VC_NS + "branch");
    for (Branch branch : branches) {
      Resource branchResource = createBranchResource(model, branch);
      dataset.addProperty(branchProp, branchResource);
    }

    // Add all tags
    List<Tag> tags = tagRepository.findAllByDataset(datasetName);
    Property tagProp = model.createProperty(VC_NS + "tag");
    for (Tag tag : tags) {
      Resource tagResource = createTagResource(model, tag);
      dataset.addProperty(tagProp, tagResource);
    }
  }

  /**
   * Creates a branch resource with metadata.
   *
   * @param model RDF model
   * @param branch Branch entity
   * @return Branch resource with type and properties
   */
  private Resource createBranchResource(Model model, Branch branch) {
    Resource branchRes = model.createResource();
    branchRes.addProperty(RDF.type, model.createResource(VC_NS + "Branch"));

    Property nameProp = model.createProperty(VC_NS + "name");
    branchRes.addProperty(nameProp, model.createLiteral(branch.getName()));

    Property headProp = model.createProperty(VC_NS + "head");
    branchRes.addProperty(headProp, model.createLiteral(branch.getCommitId().value()));

    Property protectedProp = model.createProperty(VC_NS + "protected");
    branchRes.addProperty(protectedProp, model.createTypedLiteral(branch.isProtected()));

    if (branch.getCreatedAt() != null) {
      Property createdAtProp = model.createProperty(VC_NS + "createdAt");
      branchRes.addProperty(
          createdAtProp,
          model.createTypedLiteral(branch.getCreatedAt().toString(), XSDDatatype.XSDdateTime));
    }

    if (branch.getLastUpdated() != null) {
      Property updatedAtProp = model.createProperty(VC_NS + "updatedAt");
      branchRes.addProperty(
          updatedAtProp,
          model.createTypedLiteral(branch.getLastUpdated().toString(), XSDDatatype.XSDdateTime));
    }

    return branchRes;
  }

  /**
   * Creates a tag resource with metadata.
   *
   * @param model RDF model
   * @param tag Tag entity
   * @return Tag resource with type and properties
   */
  private Resource createTagResource(Model model, Tag tag) {
    Resource tagRes = model.createResource();
    tagRes.addProperty(RDF.type, model.createResource(VC_NS + "Tag"));

    Property nameProp = model.createProperty(VC_NS + "name");
    tagRes.addProperty(nameProp, model.createLiteral(tag.name()));

    Property commitIdProp = model.createProperty(VC_NS + "commitId");
    tagRes.addProperty(commitIdProp, model.createLiteral(tag.commitId().value()));

    if (tag.message() != null) {
      Property messageProp = model.createProperty(VC_NS + "message");
      tagRes.addProperty(messageProp, model.createLiteral(tag.message()));
    }

    if (tag.author() != null) {
      Property authorProp = model.createProperty(VC_NS + "author");
      tagRes.addProperty(authorProp, model.createLiteral(tag.author()));
    }

    if (tag.createdAt() != null) {
      Property createdAtProp = model.createProperty(VC_NS + "createdAt");
      tagRes.addProperty(
          createdAtProp,
          model.createTypedLiteral(tag.createdAt().toString(), XSDDatatype.XSDdateTime));
    }

    return tagRes;
  }
}
