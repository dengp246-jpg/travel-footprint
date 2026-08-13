package com.example.travelfootprint.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.OnCommittedResponseWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class WebObservabilityFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebObservabilityFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        String requestId = safeRequestId(request.getHeader("X-Request-Id"));
        response.setHeader("X-Request-Id", requestId);
        HttpServletResponse timedResponse = new OnCommittedResponseWrapper(response) {
            @Override
            protected void onResponseCommitted() {
                setTimingHeader(this, started);
            }
        };
        try {
            filterChain.doFilter(request, timedResponse);
        } finally {
            double durationMs = (System.nanoTime() - started) / 1_000_000.0;
            if (!timedResponse.isCommitted()) setTimingHeader(timedResponse, started);
            if (durationMs >= 1000 && !request.getRequestURI().startsWith("/uploads/")) {
                LOGGER.warn("Slow request id={} method={} path={} status={} durationMs={}",
                        requestId, request.getMethod(), request.getRequestURI(), timedResponse.getStatus(), Math.round(durationMs));
            }
        }
    }

    private void setTimingHeader(HttpServletResponse response, long started) {
        double durationMs = (System.nanoTime() - started) / 1_000_000.0;
        response.setHeader("Server-Timing", "app;dur=" + String.format(java.util.Locale.ROOT, "%.1f", durationMs));
    }

    private String safeRequestId(String candidate) {
        if (candidate != null && candidate.matches("[A-Za-z0-9_-]{8,64}")) return candidate;
        return UUID.randomUUID().toString();
    }
}
