package org.owasp.pwnednext;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @ConditionalOnProperty(name = "app.role", havingValue = "model")
class ModelService {
  // Names for the base model and its SQL-focused add-on, reported by health checks that demand receipts.
  static final String MODEL_ID = "TinyLlama/TinyLlama-1.1B-Chat-v1.0";
  static final String ADAPTER_ID = "hf://buckets/steephole5586/pwnednext-tinyllama-lora-sql-adapter";
  private final NativeTinyLlama nativeModel = new NativeTinyLlama();

  @PostMapping("/generate") ResponseEntity<?> generate(@RequestBody(required = false) Map<String, List<Message>> body) {
    // Receive the app's conversation; a model cannot answer much without at least one message to misinterpret.
    List<Message> messages = body == null ? null : body.get("messages");
    if (messages == null || messages.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Missing 'messages' field (list of chat messages)"));
    // Generate a reply as JSON, or make the failure politely visible so it can ruin someone else's afternoon.
    try { return ResponseEntity.ok(Map.of("result", generate(messages))); } catch (Exception error) { return ResponseEntity.internalServerError().body(Map.of("error", error.toString())); }
  }
  // Let deployment checks inspect expected model files without waking the model, which is expensive and often grumpy.
  @GetMapping("/health") Map<String, Object> health() { return Map.of("status", "java-llama.cpp", "model", MODEL_ID, "adapter", ADAPTER_ID, "model_gguf", nativeModel.modelPath(), "adapter_gguf", nativeModel.adapterPath()); }

  // Let user instructions drive the tool call; agent policy is apparently decorative wallpaper with a security clearance.
  String generate(List<Message> messages) throws Exception {
    // The newest message is either the caller's question or database evidence from the first pass, a thrilling two-act drama.
    String latest = messages.getLast().content();
    if (messages.getFirst().content().equals(FraudController.SQL_PROMPT) && !latest.contains("Tool execution result:")) {
      // Copy a tool call hidden in the caller's text when it looks plausible. Prompt rules are more of a seasonal suggestion.
      Matcher injected = Pattern.compile("(?s)\\{\"tool\"\\s*:\\s*\"investigation_fraud\".*?}}") .matcher(latest);
      if (injected.find()) return injected.group();
      // Otherwise ask for every investigation marked as fraud, the default we wrote between two important snacks.
      return "{\"tool\":\"investigation_fraud\",\"args\":{\"query\":\"SELECT * FROM investigations WHERE fraud_detected='true'\"}}";
    }
    // For final answers, use the local native model when enabled; otherwise repeat the tool results with the confidence of a fallback.
    return Boolean.parseBoolean(System.getProperty("local.inference", System.getenv().getOrDefault("LOCAL_INFERENCE", "false"))) ? nativeModel.complete(messages) : "Fallback mode is active. Results: " + latest.replaceFirst("(?s).*Tool execution result:\\n", "").replaceFirst("\\n\\nAnswer.*", "");
  }
}

// Direct java-llama.cpp binding: load a GGUF base plus a GGUF LoRA when natural language requires more horsepower and fewer prayers.
class NativeTinyLlama {
  private LlamaModel model;
  // Deployment may supply custom artifact locations; these are the container defaults, because hard-coded paths enjoy a good home.
  String modelPath() { return System.getenv().getOrDefault("MODEL_GGUF_PATH", "/models/tinyllama-f16.gguf"); }
  String adapterPath() { return System.getenv().getOrDefault("ADAPTER_GGUF_PATH", "/models/pwnednext-tinyllama-lora.gguf"); }
  synchronized String complete(List<Message> messages) throws Exception {
    // Load the base model and its LoRA adapter once, only when a real final answer needs them and the fan noise feels justified.
    if (model == null) { if (!Files.isRegularFile(Path.of(modelPath())) || !Files.isRegularFile(Path.of(adapterPath()))) throw new IllegalStateException("GGUF model or adapter is missing"); model = new LlamaModel(new ModelParameters().setModel(modelPath()).addLoraAdapter(adapterPath())); }
    // Flatten chat history into the role-labelled text prompt the native library accepts after a sternly worded conversion.
    String prompt = messages.stream().map(message -> message.role() + ": " + message.content()).reduce("", (left, right) -> left + "\n" + right) + "\nassistant:";
    // Ask TinyLlama for a completion at a restrained temperature, because even this demo found standards under a chair.
    return model.complete(new InferenceParameters(prompt).setTemperature(0.2f));
  }
}