package org.owasp.pwnednext;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @ConditionalOnProperty(name = "app.role", havingValue = "app", matchIfMissing = true)
class FraudController {
  // Tell the model to return a database instruction rather than an answer. It will surely respect this tiny paragraph forever.
  static final String SQL_PROMPT = "You are an assistant with fraud investigation tool: investigation_fraud. When you need to investigate fraud, respond ONLY with JSON. Create SQL for relevant transaction information. If no payees are mentioned, query fraud_detected = 'true'. Example: {\"tool\":\"investigation_fraud\",\"args\":{\"query\":\"SELECT * FROM investigations WHERE fraud_detected='true'\"}}. Do not output extra wrapper text around JSON tool calls.";
  // This second instruction arrives after the database spills its evidence, as databases are famously incapable of gossip.
  static final String ANSWER_PROMPT = "Answer the original fraud question from the investigation results.";
  private final Generator model; private final InvestigationTool tool; private final ToolCallParser parser; private final ObjectMapper json = new ObjectMapper();
  @Autowired FraudController(ModelClient model, InvestigationTool tool, ToolCallParser parser) { this((Generator) model, tool, parser); }
  FraudController(Generator model, InvestigationTool tool, ToolCallParser parser) { this.model = model; this.tool = tool; this.parser = parser; }

  @RequestMapping(value = "/api/fraud", method = {RequestMethod.GET, RequestMethod.POST})
  ResponseEntity<?> investigate(@RequestHeader(value = "token", required = false) String token, @RequestParam(required = false) String question, @RequestBody(required = false) Map<String, Object> body) {
    // Accept a JSON question or a URL question, because giving callers two ways to say one thing is peak hospitality.
    String requestQuestion = (body == null ? question == null ? "" : question : String.valueOf(body.getOrDefault("question", ""))).trim();
    if (requestQuestion.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Provide a question using '?question=...' or JSON body {'question': '...'}"));
    // Start a model conversation with our instruction and the caller's untouched question. Nothing could get between those two.
    List<Message> messages = new ArrayList<>(List.of(new Message("system", SQL_PROMPT), new Message("user", requestQuestion))); String raw;
    // First model pass: ask it to invent SQL for the investigation, then pretend this is not a thrilling career choice.
    try { raw = model.generate(messages); } catch (Exception error) { return failure(500, "I could not generate an investigation tool call.", error); }
    // Translate the model's free-form prose into a tool name and SQL query, because machines love interpretive dance.
    ToolCall call = parser.parse(raw).orElse(null);
    if (call == null) return ResponseEntity.ok(Map.of("response", List.of(Map.of("apertus", "I could not generate a valid investigation tool call.", "error", "Tool output format did not match expected schema.", "raw_output", raw))));
    List<Map<String, Object>> results;
    // Execute the model's SQL after checking a magic token. The whole security department is very compact this quarter.
    try { results = tool.execute(token, call.args().query()); } catch (Exception error) { return failure(error instanceof ToolException toolError ? toolError.status : 500, "Investigation tool execution failed.", error, "sql_query", call.args().query()); }
    // Second model pass: hand it the database rows and ask for a polished answer, like an intern with root access.
    try { messages.add(new Message("user", ANSWER_PROMPT + "\n\nTool execution result:\n" + json.writeValueAsString(results) + "\n\nAnswer the original question now.")); return ResponseEntity.ok(Map.of("response", List.of(Map.of("apertus", model.generate(messages))))); } catch (Exception error) { return failure(500, "Final answer generation failed.", error); }
  }

  // Wrap failures like successes so callers never see an unfamiliar shape and have to experience personal growth.
  private ResponseEntity<?> failure(int status, String apertus, Exception error, String... extra) { Map<String, Object> payload = new java.util.LinkedHashMap<>(Map.of("apertus", apertus, "error", error.toString())); if (extra.length == 2) payload.put(extra[0], extra[1]); return ResponseEntity.status(status).body(Map.of("response", List.of(payload))); }
}