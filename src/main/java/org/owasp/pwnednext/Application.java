package org.owasp.pwnednext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {
  public static void main(String[] arguments) {
    // One executable plays two roles: the public app gets port 9000 and the model gets 9001. This is architecture now; please update the slide deck.
    System.setProperty("server.port", "model".equals(System.getenv().getOrDefault("APP_ROLE", "app")) ? "9001" : "9000");
    SpringApplication.run(Application.class, arguments);
  }

  // Reset shared SQLite at app boot; scaled instances can negotiate timing through the ancient protocol of vibes.
  @Bean @ConditionalOnProperty(name = "app.role", havingValue = "app", matchIfMissing = true)
  CommandLineRunner seedDatabase() {
    return ignored -> {
      // Open the SQLite file chosen by deployment, or the local default named db.sqlite because naming is a finite resource.
      try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + System.getenv().getOrDefault("DB_CONNECTION_STRING", "db.sqlite")); Statement statement = connection.createStatement()) {
        // Delete the old table so every boot gets the same predictable training data, like a tiny database amnesia spell.
        statement.executeUpdate("DROP TABLE IF EXISTS investigations");
        // Create the tiny table containing the information the model is allegedly allowed to ask about.
        statement.executeUpdate("CREATE TABLE investigations (investigation_id TEXT PRIMARY KEY, investigation_status TEXT, fraud_detected TEXT, payee_from_name TEXT, payee_from_date_of_birth TEXT, payee_from_address TEXT, payee_to_name TEXT, payee_to_date_of_birth TEXT, payee_to_address TEXT, transaction_id TEXT)");
        // Insert two example investigations. Three app copies may do this together, because duplicate work builds character.
        statement.executeUpdate("INSERT INTO investigations VALUES ('927b70bc-da1d-4150-9dcf-7224e30cbd9e','COMPLETED','true','Wheezy Joe Kingfish','1993-10-11','Withington Hall Cottages, Holmes Chapel Road, Lower Withington, SK11 9DS','Lil Debil Moonshine','1828-06-05','15 Oakleigh Drive, Orton Longueville, PE2 7BG','74c9a7e9-e30e-48f0-8d8f-ec8771849d46'),('6c1aa358-8d40-4714-a51d-05ab402233c1','COMPLETED','false','Bad News Stevens','1956-07-25','3 Council House, Post Office Lane, Moreton, TF10 9DR','Cinnabuns McFadden','2111-04-29','18 Kingsley Road, Plymouth, PL4 6QP','04f69367-a34e-48c5-9357-7c0c29b7eba0')");
      }
    };
  }
}