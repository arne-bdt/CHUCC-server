package org.chucc.vcserver.service;

import java.io.StringWriter;
import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.chucc.vcserver.util.RdfContentTypeUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for serializing RDF graphs to various formats.
 * Handles content negotiation based on Accept headers.
 */
@Service
public class GraphSerializationService {

  /**
   * Serializes an RDF graph to the requested format based on content type.
   *
   * @param graph the RDF graph to serialize
   * @param contentType the requested content type
   * @return the serialized graph as a string
   * @throws ResponseStatusException with 406 if the format is not supported
   */
  public String serializeGraph(Graph graph, String contentType) {
    Lang lang = RdfContentTypeUtil.determineLang(contentType);

    if (lang == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_ACCEPTABLE,
          "Unsupported content type: " + contentType
      );
    }

    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, graph, lang);
    return writer.toString();
  }

  /**
   * Gets the content type string for a given Lang.
   *
   * @param lang the Apache Jena Lang
   * @return the corresponding content type string
   */
  public String getContentType(Lang lang) {
    return RdfContentTypeUtil.getContentType(lang);
  }
}
