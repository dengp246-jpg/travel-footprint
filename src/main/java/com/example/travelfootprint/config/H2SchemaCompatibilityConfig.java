package com.example.travelfootprint.config;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class H2SchemaCompatibilityConfig {

    @Bean
    ApplicationRunner widenNotificationTypeForExistingH2Database(DataSource dataSource) {
        return args -> {
            try (var connection = dataSource.getConnection()) {
                String product = connection.getMetaData().getDatabaseProductName();
                if (product != null && product.toLowerCase().contains("h2")) {
                    new JdbcTemplate(dataSource).execute(
                            "ALTER TABLE notification_entry ALTER COLUMN type VARCHAR(20)");
                }
            }
        };
    }
}
