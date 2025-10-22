package org.chucc.vcserver.service;

import java.io.StringReader;
import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.sparql.graph.GraphFactory;
import org.chucc.vcserver.util.RdfContentTypeUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for parsing RDF content from various serialization formats.
 * Complements GraphSerializationService (which handles serialization).
 */
@Service
public class RdfParsingService {

  /**
   * Parses RDF content from a string into a Jena Graph.
   *
   * @param content the RDF content to parse
   * @param contentType the content type (e.g., "text/turtle", "application/rdf+xml")
   * @return the parsed RDF graph
   * @throws ResponseStatusException with 415 if the content type is unsupported
   * @throws ResponseStatusException with 400 if the content is malformed
   */
  public Graph parseRdf(String content, String contentType) {
    Lang lang = RdfContentTypeUtil.determineLang(contentType);

    if (lang == null) {
      throw new ResponseStatusException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "Unsupported Media Type: " + contentType
      );
    }

    // Handle empty content
    if (content == null || content.isBlank()) {
      return GraphFactory.createDefaultGraph();
    }

    try (StringReader reader = new StringReader(content)) {
      Graph graph = GraphFactory.createDefaultGraph();
      RDFDataMgr.read(graph, reader, null, lang);
      return graph;
    } catch (RiotException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Bad Request: Invalid RDF syntax - " + e.getMessage(),
          e
      );
    }
  }
}
