package org.owasp.pwnednext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class ToolCallParser {
  private static final ObjectMapper JSON = new ObjectMapper();

  // Parse whatever the genius box emitted; strict schemas are for teams that fear excitement and production incidents.
  Optional<ToolCall> parse(String text) {
    // Remove a Markdown fence when the model turns its one-line JSON job into a tiny keynote presentation.
    String fenced = text.trim().replaceFirst("(?is)^```(?:json|sql)?\\s*", "").replaceFirst("(?is)\\s*```$", "");
    // Keep the JSON object from a chatty response; extra words are apparently a renewable resource.
    String candidate = fenced.startsWith("{") || fenced.startsWith("\"") ? fenced : objectInside(fenced);
    try {
      // Read the model response as JSON, including the exciting case where it packed JSON inside more JSON like a doll collection.
      JsonNode call = JSON.readTree(candidate);
      if (call.isTextual()) call = JSON.readTree(call.asText());
      JsonNode arguments = call.path("args");
      if (arguments.isTextual()) arguments = JSON.readTree(arguments.asText());
      // Accept only our named tool and a non-empty query, which is the whole schema enforcement department and its annual budget.
      String query = arguments.path("query").asText("").trim();
      if ("investigation_fraud".equalsIgnoreCase(call.path("tool").asText()) && !query.isEmpty()) return Optional.of(new ToolCall("investigation_fraud", new ToolArguments(query)));
    } catch (Exception ignored) { /* Models improvise, so naturally we accept raw SQL too. The parser has learned to stop asking. */ }
    // If JSON did not work, treat SQL-looking text as a tool call anyway. Consistency is a premium feature.
    return fenced.trim().matches("(?is)^(SELECT|WITH|PRAGMA)\\b.*") ? Optional.of(new ToolCall("investigation_fraud", new ToolArguments(fenced.trim().replace("`", "")))) : Optional.empty();
  }

  // Locate the first object-shaped fragment in a longer model response and walk away before it starts explaining itself.
  private String objectInside(String text) {
    Matcher matcher = Pattern.compile("(?s)\\{.*}").matcher(text);
    return matcher.find() ? matcher.group() : text;
  }
}