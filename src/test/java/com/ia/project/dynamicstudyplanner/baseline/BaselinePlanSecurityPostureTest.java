package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * {@code /plans} is held to the same standard as the rest of the service, not to a different one.
 *
 * <h2>What this pins, and why it is worth pinning</h2>
 *
 * {@code /plans} is served by a filter chain of its own ({@link BaselinePlanSecurityConfig}), and a
 * second chain is exactly where a posture diverges quietly: it could be stricter, it could be looser,
 * and nothing would say so. So the comparison is made against {@code /api/v1/**} directly, request by
 * request:
 *
 * <ul>
 *   <li>neither needs credentials — and that is what obliges this service to live on a private
 *       network, behind the platform, which the README states in both languages;</li>
 *   <li>the new chain widens nothing else: {@code /actuator/prometheus} still demands credentials;</li>
 *   <li>when the operator declares a TLS proxy in front, cleartext is refused on {@code /plans} the
 *       same way, with the same HSTS header.</li>
 * </ul>
 */
@DisplayName("Security posture of /plans")
class BaselinePlanSecurityPostureTest {

    /**
     * A path under the prefix the application chain declares {@code permitAll}.
     *
     * <p>Nothing handles it since EOA-4b — the concurso endpoints left with their path
     * — and that is exactly what makes it usable here: {@code permitAll} on a path with no handler
     * answers {@code 404}, while the chain's {@code anyRequest().authenticated()} would answer
     * {@code 401}. The status tells the two apart, which is the property this file is about.
     */
    private static final String PERMITTED_PREFIX = "/api/v1/nothing-handles-this";

    /**
     * A snapshot that deserialises cleanly and cannot be planned: it carries no topics.
     *
     * <p>It has to deserialise, or the answer would be the {@code 400} of an unreadable body and this
     * test would be pinning Jackson rather than the filter chain.
     */
    private static final String UNPLANNABLE_SNAPSHOT = """
            {"contractVersion":"1.0",
             "horizon":{"start":"2026-09-01","end":"2026-09-28"},
             "availability":[],"goals":[],"topics":[],"prerequisites":[],"history":[],
             "algorithmParams":{},"randomSeed":1}""";

    private static MockHttpServletRequestBuilder plans() {
        return post(PlanProtocol.PLANS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UNPLANNABLE_SNAPSHOT);
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles(PlanProtocol.PROFILE)
    @DisplayName("Default: public, exactly as /api/v1/** is")
    class ByDefault {

        @Autowired
        private MockMvc mockMvc;

        private int statusOf(MockHttpServletRequestBuilder request) throws Exception {
            return mockMvc.perform(request).andReturn().getResponse().getStatus();
        }

        @Test
        @DisplayName("neither /plans nor /api/v1/** asks for credentials")
        void neitherAsksForCredentials() throws Exception {
            assertThat(statusOf(plans()))
                    .as("/plans reaches the handler and is answered on its merits")
                    .isEqualTo(422);
            assertThat(statusOf(post(PERMITTED_PREFIX).contentType(MediaType.APPLICATION_JSON)
                    .content("{}")))
                    .as("and /api/v1/** is answered on its merits too — 404 for "
                            + "\"nothing handles it\", not 401 for \"prove who you are\"")
                    .isEqualTo(404);
        }

        @Test
        @DisplayName("the new chain widens nothing: /actuator/prometheus still demands credentials")
        void theProtectedActuatorEndpointStaysProtected() throws Exception {
            assertThat(statusOf(get("/actuator/prometheus")))
                    .as("the chain matches /plans and nothing else")
                    .isEqualTo(401);
        }

        @Test
        @DisplayName("no HSTS over plain HTTP, which is the default posture")
        void noHstsOverPlainHttp() throws Exception {
            MvcResult result = mockMvc.perform(plans()).andReturn();

            assertThat(result.getResponse().getHeader("Strict-Transport-Security")).isNull();
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles(PlanProtocol.PROFILE)
    @TestPropertySource(properties = {
            "api.security.require-https=true",
            "api.security.hsts-max-age-seconds=31536000"
    })
    @DisplayName("With api.security.require-https: the same TLS posture as the rest")
    class WhenHttpsIsRequired {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("cleartext to /plans is refused and redirected to https")
        void cleartextIsRefused() throws Exception {
            MvcResult result = mockMvc.perform(plans()).andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("the Core protocol is not exempt from the deployment's TLS posture")
                    .isIn(301, 302, 307, 308);
            assertThat(result.getResponse().getRedirectedUrl()).startsWith("https://");
        }

        @Test
        @DisplayName("over TLS the request is served, and carries HSTS")
        void overTlsTheRequestIsServedWithHsts() throws Exception {
            MvcResult result = mockMvc.perform(plans().secure(true)).andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("served on its merits: a snapshot with no topics cannot be planned")
                    .isEqualTo(422);
            assertThat(result.getResponse().getHeader("Strict-Transport-Security"))
                    .as("the same header the rest of the service emits")
                    .isNotNull()
                    .contains("max-age=31536000")
                    .contains("includeSubDomains");
        }
    }
}
