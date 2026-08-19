package com.example.travelfootprint.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

@Configuration
public class SecurityConfig {

    private static final String DEFAULT_CSP =
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data: blob:; media-src 'self' blob:; font-src 'self' data:; connect-src 'self'; "
                    + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'self'";
    private static final String AMAP_CSP =
            "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://*.amap.com; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data: blob: https://*.amap.com https://*.autonavi.com; "
                    + "media-src 'self' blob:; font-src 'self' data: https://*.amap.com https://*.autonavi.com; "
                    + "connect-src 'self' https://*.amap.com https://*.autonavi.com; worker-src 'self' blob:; "
                    + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'self'";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokenRepository.setCookiePath("/");

        http
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(tokenRepository)
                        .ignoringRequestMatchers("/api/mini/**", "/h2-console/**"))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new AntPathRequestMatcher("/map"),
                                new ContentSecurityPolicyHeaderWriter(AMAP_CSP)))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new NegatedRequestMatcher(new AntPathRequestMatcher("/map")),
                                new ContentSecurityPolicyHeaderWriter(DEFAULT_CSP)))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .permissionsPolicy(permissions -> permissions.policy(
                                "camera=(), microphone=(), payment=(), usb=(), geolocation=(self)")));
        return http.build();
    }
}
