package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TravelPost;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class LocationNormalizationService {

    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[,，、/\\\\|;；·]+");
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");

    private final ProvinceCatalogService provinceCatalogService;

    public LocationNormalizationService(ProvinceCatalogService provinceCatalogService) {
        this.provinceCatalogService = provinceCatalogService;
    }

    public String normalizeDisplayLocation(String province, String location) {
        if (location == null || location.isBlank()) {
            return "";
        }

        String normalizedProvince = provinceCatalogService.normalizeProvince(province).orElse("");
        String working = location.trim()
                .replace('（', '(')
                .replace('）', ')')
                .replace('【', '[')
                .replace('】', ']')
                .replace('—', ' ')
                .replace('-', ' ');
        working = SEPARATOR_PATTERN.matcher(working).replaceAll(" ");
        working = SPACE_PATTERN.matcher(working).replaceAll(" ").trim();

        for (String alias : provinceCatalogService.provinceAliases(normalizedProvince)) {
            if (!alias.isBlank() && working.startsWith(alias)) {
                working = working.substring(alias.length()).trim();
                break;
            }
        }

        if (working.isBlank()) {
            return normalizedProvince;
        }

        List<String> rawTokens = List.of(working.split(" "));
        Set<String> seenKeys = new LinkedHashSet<>();
        List<String> normalizedTokens = new ArrayList<>();
        for (String rawToken : rawTokens) {
            String token = rawToken.trim();
            if (token.isBlank()) {
                continue;
            }
            if (!normalizedProvince.isBlank() && provinceCatalogService.matchesProvinceAlias(normalizedProvince, token)) {
                continue;
            }
            String lookupKey = toLookupKey(token);
            if (lookupKey.isBlank() || !seenKeys.add(lookupKey)) {
                continue;
            }
            normalizedTokens.add(token);
        }

        if (normalizedTokens.isEmpty()) {
            return working;
        }
        if (normalizedTokens.size() == 1) {
            return normalizedTokens.get(0);
        }
        return String.join(" · ", normalizedTokens);
    }

    public String normalizeLookupKey(String province, String location) {
        return toLookupKey(normalizeDisplayLocation(province, location));
    }

    public String normalizeLookupKey(TravelPost post) {
        return normalizeLookupKey(post.getProvince(), post.getLocation());
    }

    public String normalizeDisplayLocation(TravelPost post) {
        return normalizeDisplayLocation(post.getProvince(), post.getLocation());
    }

    public List<String> locationSegments(String province, String location) {
        String normalizedLocation = normalizeDisplayLocation(province, location);
        if (normalizedLocation.isBlank()) {
            return List.of();
        }
        return List.of(normalizedLocation.split("·")).stream()
                .map(String::trim)
                .filter(segment -> !segment.isBlank())
                .toList();
    }

    public List<String> locationSegments(TravelPost post) {
        return locationSegments(post.getProvince(), post.getLocation());
    }

    public String primaryLocationSegment(String province, String location) {
        return locationSegments(province, location).stream()
                .findFirst()
                .orElse("");
    }

    public String primaryLocationSegment(TravelPost post) {
        return primaryLocationSegment(post.getProvince(), post.getLocation());
    }

    private String toLookupKey(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                        .replace(" ", "")
                        .replace("·", "")
                        .replace(".", "")
                        .replace("，", "")
                        .replace(",", "");
    }
}
