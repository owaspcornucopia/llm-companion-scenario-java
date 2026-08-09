package org.owasp.pwnednext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class ModelClient implements Generator {
  private final HttpClient client = HttpClient.newHttpClient(); private final ObjectMapper json = new ObjectMapper();
  // Send the full conversation elsewhere; distributed systems are mostly confidence, URLs, and a slightly haunted timeout.
  public String generate(List<Message> messages) throws Exception {
    // Turn the conversation into JSON and mail it to the separate service that keeps the expensive robot in its basement.
    HttpRequest request = HttpRequest.newBuilder(URI.create(System.getenv().getOrDefault("MODEL_SERVICE_URL", "http://localhost:9001") + "/generate")).header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("messages", messages)))).build();
    // Wait for its JSON reply, then turn model trouble into ordinary Java trouble, which feels more familiar.
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString()); Map<String, Object> body = json.readValue(response.body(), new TypeReference<>() {});
    if (response.statusCode() != 200) throw new RuntimeException(String.valueOf(body.getOrDefault("error", "Model service returned " + response.statusCode())));
    // Return only the generated words. The HTTP wrapper has completed its brief but emotionally important cameo.
    return String.valueOf(body.getOrDefault("result", ""));
  }
}