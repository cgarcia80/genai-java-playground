package com.genailab.orchestrator.infrastructure.adapter.routing;

import com.genailab.orchestrator.domain.model.OrchestrationRequest;
import com.genailab.orchestrator.domain.model.RoutingTarget;
import com.genailab.orchestrator.domain.port.RoutingClassifier;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedRoutingClassifier implements RoutingClassifier {

  private static final Pattern STACK_TRACE =
      Pattern.compile("\\bat\\s+[\\w.$]+\\([\\w.$]+\\.java:\\d+\\)");
  private static final List<String> ERROR_TERMS = List.of(
      "exception",
      "error",
      "stacktrace",
      "stack trace",
      "nullpointerexception",
      "illegalargumentexception",
      "caused by",
      "failed",
      "fallo",
      "traceback");
  private static final List<String> DOC_TERMS = List.of(
      "documentacion",
      "documentación",
      "docs",
      "doc",
      "entidad",
      "entidades",
      "arquitectura",
      "proceso",
      "procesos",
      "modelo",
      "dominio",
      "value object",
      "objeto de valor");

  @Override
  public RoutingTarget classify(OrchestrationRequest request) {
    String question = normalized(request.question());
    if (isDiagnosis(question)) {
      return RoutingTarget.DIAGNOSIS;
    }
    if (isDocQuery(question)) {
      return RoutingTarget.DOC_QUERY;
    }
    return RoutingTarget.SMART_SEARCH;
  }

  private boolean isDiagnosis(String question) {
    return STACK_TRACE.matcher(question).find() || containsAny(question, ERROR_TERMS);
  }

  private boolean isDocQuery(String question) {
    return containsAny(question, DOC_TERMS);
  }

  private boolean containsAny(String text, List<String> terms) {
    return terms.stream().anyMatch(text::contains);
  }

  private String normalized(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }
}
