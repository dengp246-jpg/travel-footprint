package com.example.travelfootprint.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class RenderEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "renderDatabaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String configuredUrl = environment.getProperty("spring.datasource.url");
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        if (configuredUrl != null && configuredUrl.startsWith("jdbc:postgresql://")) {
            return;
        }

        URI uri = URI.create(databaseUrl);
        if (uri.getScheme() == null || !uri.getScheme().startsWith("postgres")) {
            return;
        }

        String jdbcUrl = buildJdbcUrl(uri);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.putIfAbsent("spring.datasource.url", jdbcUrl);
        properties.putIfAbsent("spring.datasource.driverClassName", "org.postgresql.Driver");

        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            String[] parts = userInfo.split(":", 2);
            if (parts.length > 0 && !parts[0].isBlank()) {
                properties.putIfAbsent("spring.datasource.username", decode(parts[0]));
            }
            if (parts.length > 1) {
                properties.putIfAbsent("spring.datasource.password", decode(parts[1]));
            }
        }

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    private String buildJdbcUrl(URI uri) {
        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://");
        jdbcUrl.append(uri.getHost());
        if (uri.getPort() > 0) {
            jdbcUrl.append(':').append(uri.getPort());
        }
        jdbcUrl.append(uri.getPath());
        if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
            jdbcUrl.append('?').append(uri.getQuery());
        }
        return jdbcUrl.toString();
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
