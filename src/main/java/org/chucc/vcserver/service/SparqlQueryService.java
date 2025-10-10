package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import java.io.ByteArrayOutputStream;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriter;
import org.chucc.vcserver.domain.ResultFormat;
import org.springframework.stereotype.Service;

/**
 * Service for executing SPARQL queries using Apache Jena ARQ engine.
 * Supports SELECT, ASK, CONSTRUCT, and DESCRIBE queries with multiple output formats.
 */
@Service
public class SparqlQueryService {

  /**
   * Executes a SPARQL query against a dataset and returns formatted results.
   *
   * @param dataset the dataset to query
   * @param queryString the SPARQL query string
   * @param resultFormat the desired result format
   * @return formatted query results as a string
   * @throws QueryParseException if the query is malformed
   * @throws IllegalArgumentException if the query type is unsupported
   */
  @Timed(
      value = "sparql.query.execution",
      description = "SPARQL query execution time"
  )
  @Counted(
      value = "sparql.query.total",
      description = "Total SPARQL queries executed"
  )
  public String executeQuery(Dataset dataset, String queryString, ResultFormat resultFormat) {
    // Parse query
    Query query = QueryFactory.create(queryString);

    // Execute query based on type
    try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
      if (query.isSelectType()) {
        return executeSelect(qexec, resultFormat);
      } else if (query.isAskType()) {
        return executeAsk(qexec, resultFormat);
      } else if (query.isConstructType()) {
        return executeConstruct(qexec, resultFormat);
      } else if (query.isDescribeType()) {
        return executeDescribe(qexec, resultFormat);
      } else {
        throw new IllegalArgumentException("Unsupported query type");
      }
    }
  }

  /**
   * Executes a SELECT query and formats the results.
   *
   * @param qexec the query execution context
   * @param format the result format
   * @return formatted results
   */
  private String executeSelect(QueryExecution qexec, ResultFormat format) {
    ResultSet results = qexec.execSelect();
    return formatSelectResults(results, format);
  }

  /**
   * Executes an ASK query and formats the result.
   *
   * @param qexec the query execution context
   * @param format the result format
   * @return formatted result
   */
  private String executeAsk(QueryExecution qexec, ResultFormat format) {
    boolean result = qexec.execAsk();
    return formatAskResult(result, format);
  }

  /**
   * Executes a CONSTRUCT query and formats the results.
   *
   * @param qexec the query execution context
   * @param format the result format
   * @return formatted results
   */
  private String executeConstruct(QueryExecution qexec, ResultFormat format) {
    Model model = qexec.execConstruct();
    return formatGraphResults(model, format);
  }

  /**
   * Executes a DESCRIBE query and formats the results.
   *
   * @param qexec the query execution context
   * @param format the result format
   * @return formatted results
   */
  private String executeDescribe(QueryExecution qexec, ResultFormat format) {
    Model model = qexec.execDescribe();
    return formatGraphResults(model, format);
  }

  /**
   * Formats SELECT query results according to the specified format.
   *
   * @param results the result set
   * @param format the desired format
   * @return formatted results as string
   */
  private String formatSelectResults(ResultSet results, ResultFormat format) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    switch (format) {
      case JSON:
        ResultSetFormatter.outputAsJSON(output, results);
        break;
      case XML:
        ResultSetFormatter.outputAsXML(output, results);
        break;
      case CSV:
        ResultSetFormatter.outputAsCSV(output, results);
        break;
      case TSV:
        ResultSetFormatter.outputAsTSV(output, results);
        break;
      default:
        // Default to JSON for SELECT queries
        ResultSetFormatter.outputAsJSON(output, results);
        break;
    }

    return output.toString(java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Formats ASK query result according to the specified format.
   *
   * @param result the boolean result
   * @param format the desired format
   * @return formatted result as string
   */
  @SuppressFBWarnings(
      value = "DB_DUPLICATE_SWITCH_CLAUSES",
      justification = "Duplicate cases are intentional for clarity and maintainability"
  )
  private String formatAskResult(boolean result, ResultFormat format) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    switch (format) {
      case JSON:
        output.writeBytes(String.format(
            "{\"head\":{},\"boolean\":%s}",
            result
        ).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        break;
      case XML:
        output.writeBytes(String.format(
            "<?xml version=\"1.0\"?>%n"
                + "<sparql xmlns=\"http://www.w3.org/2005/sparql-results#\">%n"
                + "  <head></head>%n"
                + "  <boolean>%s</boolean>%n"
                + "</sparql>",
            result
        ).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        break;
      case CSV:
        output.writeBytes(String.valueOf(result)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        break;
      case TSV:
        output.writeBytes(String.valueOf(result)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        break;
      default:
        // Default to JSON
        output.writeBytes(String.format(
            "{\"head\":{},\"boolean\":%s}",
            result
        ).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        break;
    }

    return output.toString(java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Formats CONSTRUCT/DESCRIBE query results according to the specified format.
   *
   * @param model the RDF model result
   * @param format the desired format
   * @return formatted results as string
   */
  @SuppressFBWarnings(
      value = "DB_DUPLICATE_SWITCH_CLAUSES",
      justification = "Fallthrough cases are intentional for default handling"
  )
  private String formatGraphResults(Model model, ResultFormat format) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    switch (format) {
      case TURTLE:
        RDFWriter.source(model).format(RDFFormat.TURTLE).output(output);
        break;
      case RDF_XML:
        RDFWriter.source(model).format(RDFFormat.RDFXML).output(output);
        break;
      case JSON:
        // Use JSON-LD for graph results
        RDFWriter.source(model).format(RDFFormat.JSONLD).output(output);
        break;
      case XML:
      case CSV:
      case TSV:
      default:
        // Default to Turtle for graph results
        RDFWriter.source(model).format(RDFFormat.TURTLE).output(output);
        break;
    }

    return output.toString(java.nio.charset.StandardCharsets.UTF_8);
  }
}
