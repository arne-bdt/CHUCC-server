package org.chucc.vcserver.controller;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.chucc.vcserver.service.ServiceDescriptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for SPARQL 1.1 Service Description endpoint.
 * Provides machine-readable metadata about the service's capabilities,
 * datasets, and version control features.
 *
 * @see <a href="https://www.w3.org/TR/sparql11-service-description/">
 *   SPARQL 1.1 Service Description</a>
 */
@Tag(name = "Service Description", description = "SPARQL service metadata and capabilities")
@RestController
public class ServiceDescriptionController {

  private final ServiceDescriptionService serviceDescriptionService;

  /**
   * Constructs service description controller.
   *
   * @param serviceDescriptionService Service description service
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "ServiceDescriptionService is a Spring-managed bean "
          + "and is intentionally shared"
  )
  public ServiceDescriptionController(ServiceDescriptionService serviceDescriptionService) {
    this.serviceDescriptionService = serviceDescriptionService;
  }

  /**
   * Returns service description (well-known URI).
   * Follows RFC 5785 well-known URI convention for VOID dataset metadata.
   *
   * @param request HTTP request
   * @param response HTTP response
   * @throws IOException if writing response fails
   */
  @Operation(
      summary = "Get service description (well-known URI)",
      description = "Returns SPARQL 1.1 Service Description in RDF format with content "
          + "negotiation. Describes service capabilities, supported features, and available "
          + "datasets. Follows RFC 5785 well-known URI convention.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Service description returned"),
      @ApiResponse(responseCode = "406", description = "Unsupported media type requested")
  })
  @GetMapping(
      value = "/.well-known/void",
      produces = {
          "text/turtle",
          "application/ld+json",
          "application/rdf+xml",
          "application/n-triples"
      })
  public void getServiceDescriptionWellKnown(
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    serveServiceDescription(request, response);
  }

  /**
   * Returns service description (explicit endpoint).
   * Alternative to /.well-known/void for easier discovery.
   *
   * @param request HTTP request
   * @param response HTTP response
   * @throws IOException if writing response fails
   */
  @Operation(
      summary = "Get service description",
      description = "Returns SPARQL 1.1 Service Description in RDF format with content "
          + "negotiation. Alternative to /.well-known/void endpoint.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Service description returned"),
      @ApiResponse(responseCode = "406", description = "Unsupported media type requested")
  })
  @GetMapping(
      value = "/service-description",
      produces = {
          "text/turtle",
          "application/ld+json",
          "application/rdf+xml",
          "application/n-triples"
      })
  public void getServiceDescription(
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    serveServiceDescription(request, response);
  }

  /**
   * Serves service description with content negotiation.
   * Generates RDF model and serializes in requested format.
   *
   * @param request HTTP request
   * @param response HTTP response
   * @throws IOException if writing response fails
   */
  private void serveServiceDescription(
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {

    // Generate service description model
    Model model = serviceDescriptionService.generateServiceDescription();

    // Content negotiation
    String acceptHeader = request.getHeader("Accept");
    Lang format = determineFormat(acceptHeader);

    // Set response headers
    response.setContentType(format.getHeaderString());
    response.setCharacterEncoding("UTF-8");

    // Write RDF
    RDFDataMgr.write(response.getOutputStream(), model, format);
  }

  /**
   * Determines RDF serialization format from Accept header.
   * Defaults to Turtle if no Accept header or wildcard.
   *
   * @param acceptHeader HTTP Accept header value
   * @return RDF serialization language
   */
  private Lang determineFormat(String acceptHeader) {
    if (acceptHeader == null || acceptHeader.contains("*/*")) {
      return Lang.TURTLE; // Default
    }

    // Try Jena's built-in content type mapping
    Lang format = RDFLanguages.contentTypeToLang(acceptHeader);
    if (format != null) {
      return format;
    }

    // Fallback: try specific formats
    if (acceptHeader.contains("application/ld+json")) {
      return Lang.JSONLD;
    } else if (acceptHeader.contains("application/rdf+xml")) {
      return Lang.RDFXML;
    } else if (acceptHeader.contains("application/n-triples")) {
      return Lang.NTRIPLES;
    } else if (acceptHeader.contains("text/turtle")) {
      return Lang.TURTLE;
    }

    return Lang.TURTLE; // Fallback
  }
}
