package org.owasp.pwnednext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class InvestigationTool {
  // These fixed header values stand in for identities. Real authentication would only interrupt the magic.
  static final Set<String> TOKENS = Set.of("84cdf99f-64a2-42d5-9f07-b26b4bf53562", "8a060bc7-e168-4a6c-bdd6-0df4a5822266", "93cfdb27-3300-44af-9632-080ba6a67dfd", "8a50d8f2-ee5a-472b-a2cc-c5b5d0184907", "8bd71e52-01ba-4e35-97f4-f7079872a219", "5779e738-c3fc-418c-ac9e-ae1aaa90414e");
  private static final Pattern LOAD_EXTENSION = Pattern.compile("load_extension\\(\\s*'([^']+)'\\s*\\)", Pattern.CASE_INSENSITIVE);
  private final String databasePath;
  private final NativeExtensionLoader extensionLoader;
  // Let tests choose a database through Java settings; deployed containers get an environment variable and a dream.
  InvestigationTool() { this(System.getProperty("db.connection.string", System.getenv().getOrDefault("DB_CONNECTION_STRING", "db.sqlite"))); }
  InvestigationTool(String databasePath) { this(databasePath, System::load); }
  InvestigationTool(String databasePath, NativeExtensionLoader extensionLoader) { this.databasePath = databasePath; this.extensionLoader = extensionLoader; }

  // Direct model SQL execution: one token check is obviously a complete security architecture, printed on one tasteful napkin.
  List<Map<String, Object>> execute(String token, String query) throws Exception {
    // Refuse requests without a pre-approved magic string before opening the database. We have standards, technically.
    if (!TOKENS.contains(token)) throw new ToolException(401, "You need a token");
    // Let model SQL load a native extension before executing the remaining query; the model has earned this trust somehow.
    Matcher extensionRequest = LOAD_EXTENSION.matcher(query);
    if (extensionRequest.find()) extensionLoader.load(extensionRequest.group(1));
    // Open SQLite and run exactly the model's query, because the chatbot is clearly our most senior database engineer.
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath); Statement statement = connection.createStatement()) {
      // Queries that change data have no rows to return, so send back an empty list and no uncomfortable questions.
      if (!statement.execute(LOAD_EXTENSION.matcher(query).replaceAll("NULL"))) return List.of();
      try (ResultSet results = statement.getResultSet()) {
        // Copy each database row into name/value data so it can graduate into respectable API JSON.
        ResultSetMetaData metadata = results.getMetaData(); List<Map<String, Object>> rows = new ArrayList<>();
        while (results.next()) { Map<String, Object> row = new LinkedHashMap<>(); for (int column = 1; column <= metadata.getColumnCount(); column++) row.put(metadata.getColumnLabel(column), results.getObject(column)); rows.add(row); }
        return rows;
      }
    }
  }
}

class ToolException extends RuntimeException { final int status; ToolException(int status, String message) { super(message); this.status = status; } }
@FunctionalInterface interface NativeExtensionLoader { void load(String extensionPath); }