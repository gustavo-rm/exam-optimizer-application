package com.ia.project.dynamicstudyplanner.baseline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The security posture of {@code /plans}: public, exactly as public as {@code /api/v1/**}.
 *
 * <h2>This service has to live on a private network</h2>
 *
 * {@code /api/v1/**} is {@code permitAll} and this chain makes {@code /plans} {@code permitAll} too.
 * <b>There is no authentication of any kind in front of either</b> — none here, and none delegated to
 * an external component. Anything that can reach this service can post a study snapshot to it and read
 * a plan back. <b>The service therefore must not be reachable from the internet: it belongs on a
 * private network, behind the platform, which is the only party meant to call it.</b> That requirement
 * is written in the README, in both languages, rather than only in a comment here, because it is a
 * deployment constraint and whoever deploys this reads the README.
 *
 * <h2>Why a separate chain instead of one more rule in {@code SecurityConfig}</h2>
 *
 * Two reasons, and the first is the stronger one.
 *
 * <p><b>It touches nothing.</b> {@code SecurityConfig} belongs to the existing exam-optimiser path and
 * stays exactly as it is; this module adds a chain of its own and the two compose. A chain with a
 * {@code securityMatcher} is ordered before the application's chain, which matches any request and must
 * therefore stay last.
 *
 * <p><b>It makes the missing endpoint answer {@code 404}.</b> This chain is deliberately <b>not</b>
 * gated by {@code baseline-core}, although the controller is. {@code SecurityConfig} ends in
 * {@code anyRequest().authenticated()} with HTTP Basic, so without this chain a request to
 * {@code /plans} with the profile off would be answered {@code 401} by the filter chain before the
 * dispatcher could report that nothing handles the path — the caller would read "wrong credentials"
 * where the truth is "no such endpoint". Making the chain unconditional keeps the answer honest, and it
 * exposes nothing when the profile is off: {@code permitAll} on a path with no handler grants access to
 * a {@code 404}.
 *
 * <h2>{@code /plans} is not held to a different standard than the rest</h2>
 *
 * Authentication: none, the same as {@code /api/v1/**}. CSRF: disabled, the same as the rest of this
 * stateless API. TLS: the {@code api.security.require-https} posture is mirrored here deliberately, so
 * that an operator who declares a TLS proxy in front gets the same refusal of cleartext and the same
 * HSTS header on {@code /plans} as on every other path. Duplicating those six lines is the price of not
 * editing {@code SecurityConfig}; {@code BaselinePlanSecurityPostureTest} pins the equivalence, so the
 * two cannot drift apart unnoticed.
 */
@Configuration
public class BaselinePlanSecurityConfig {

    /**
     * Ordered before the application's chain, which declares no matcher.
     *
     * <p>Spring Security requires the chain that matches any request to be last, so this one has to be
     * first. The application's chain carries no {@code @Order} and therefore sorts at lowest
     * precedence, which leaves any positive value here correct; {@code 1} says "before the catch-all"
     * without claiming a position among chains that do not exist.
     */
    private static final int BEFORE_APPLICATION_CHAIN = 1;

    /** Mirrors {@code SecurityConfig}: only switch it on together with {@code api.trusted-proxies}. */
    @Value("${api.security.require-https:false}")
    private boolean requireHttps;

    /** Mirrors {@code SecurityConfig}: one year, and only in effect when HTTPS is required. */
    @Value("${api.security.hsts-max-age-seconds:31536000}")
    private long hstsMaxAgeSeconds;

    /**
     * The chain that serves {@code /plans}.
     *
     * @param http the builder Spring Boot hands to each chain
     * @return the configured chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @Order(BEFORE_APPLICATION_CHAIN)
    public SecurityFilterChain baselinePlanSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(BaselineCore.PLANS_PATH)
                // Stateless protocol endpoint called by a server, never by a browser form — the same
                // reasoning SecurityConfig applies to /api/v1/**.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

        if (requireHttps) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
            http.headers(headers -> headers
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(hstsMaxAgeSeconds)));
        }

        return http.build();
    }
}
