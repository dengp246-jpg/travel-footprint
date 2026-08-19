package com.example.travelfootprint.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/_AMapService")
public class AmapProxyController {

    private static final String PREFIX = "/_AMapService";
    private static final String WEB_API_ORIGIN = "https://webapi.amap.com";
    private static final String REST_API_ORIGIN = "https://restapi.amap.com";

    private final RestClient restClient;
    private final String securityJsCode;
    private final boolean proxyEnabled;

    public AmapProxyController(
            RestClient.Builder restClientBuilder,
            @Value("${app.map.amap-security-js-code:}") String securityJsCode,
            @Value("${app.map.amap-proxy-enabled:true}") boolean proxyEnabled) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(8));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.securityJsCode = securityJsCode.trim();
        this.proxyEnabled = proxyEnabled;
    }

    @GetMapping("/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        if (!proxyEnabled || securityJsCode.isBlank()) {
            return ResponseEntity.status(503)
                    .cacheControl(CacheControl.noStore())
                    .body(new byte[0]);
        }

        String requestPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String upstreamPath = requestPath == null || !requestPath.startsWith(PREFIX)
                ? ""
                : requestPath.substring(PREFIX.length());
        if (upstreamPath.isBlank() || upstreamPath.contains("..") || upstreamPath.contains("\\")) {
            return ResponseEntity.badRequest().body(new byte[0]);
        }

        String origin = upstreamPath.startsWith("/v4/map/styles") ? WEB_API_ORIGIN : REST_API_ORIGIN;
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(origin).path(upstreamPath);
        request.getParameterMap().forEach((name, values) -> {
            if (!"jscode".equalsIgnoreCase(name)) {
                for (String value : values) {
                    uriBuilder.queryParam(name, value);
                }
            }
        });
        URI upstreamUri = uriBuilder.queryParam("jscode", securityJsCode).build().encode().toUri();

        try {
            return restClient.get().uri(upstreamUri).exchange((upstreamRequest, upstreamResponse) -> {
                HttpHeaders responseHeaders = new HttpHeaders();
                if (request.getParameter("callback") != null) {
                    responseHeaders.setContentType(MediaType.valueOf("application/javascript;charset=UTF-8"));
                } else if (upstreamResponse.getHeaders().getContentType() != null) {
                    responseHeaders.setContentType(upstreamResponse.getHeaders().getContentType());
                } else {
                    responseHeaders.setContentType(MediaType.APPLICATION_JSON);
                }
                String cacheControl = upstreamResponse.getHeaders().getCacheControl();
                responseHeaders.setCacheControl(cacheControl == null || cacheControl.isBlank()
                        ? "public, max-age=300"
                        : cacheControl);
                return ResponseEntity.status(upstreamResponse.getStatusCode())
                        .headers(responseHeaders)
                        .body(upstreamResponse.getBody().readAllBytes());
            });
        } catch (Exception exception) {
            return ResponseEntity.status(502)
                    .cacheControl(CacheControl.noStore())
                    .body(new byte[0]);
        }
    }
}
