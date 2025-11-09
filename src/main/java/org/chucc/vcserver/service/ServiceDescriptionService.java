package org.chucc.vcserver.service;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
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

  private final String baseUrl;

  /**
   * Constructs service description service.
   *
   * @param baseUrl Base URL of the service (configurable via application.yml)
   */
  public ServiceDescriptionService(
      @Value("${server.base-url:http://localhost:8080}") String baseUrl) {
    this.baseUrl = baseUrl;
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
}
