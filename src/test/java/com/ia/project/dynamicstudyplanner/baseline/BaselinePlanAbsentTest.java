package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.plan.PlanController;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Without the {@code baseline-core} profile, nothing of this module exists and {@code /plans} is a 404.
 *
 * <h2>404, and not 401</h2>
 *
 * The distinction is the whole reason {@link BaselinePlanSecurityConfig} is not gated by the profile.
 * {@code SecurityConfig} ends in {@code anyRequest().authenticated()} with HTTP Basic, so a request to
 * an unmapped path would be answered {@code 401} by the filter chain before the dispatcher could say
 * that nothing handles it — telling the caller their credentials are wrong when the truth is that the
 * endpoint is not there. The unconditional chain makes the answer honest and exposes nothing: a
 * {@code permitAll} path with no handler grants access to a {@code 404}.
 *
 * <p>This test also runs on the application's default context, which is the one every other
 * {@code @SpringBootTest} in this repository uses. If this module changed how the application boots,
 * this is where it would show.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Without the baseline-core profile")
class BaselinePlanAbsentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("POST /plans answers 404, because nothing handles it")
    void postToPlansIs404() throws Exception {
        assertThat(mockMvc.perform(post(PlanProtocol.PLANS_PATH)
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andReturn().getResponse().getStatus())
                .as("not 401: the endpoint is absent, not protected")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("no bean of the scheduler or the controller is created")
    void noBaselineBeansExist() {
        assertThat(context.getBeanNamesForType(GreedyBaselineScheduler.class))
                .as("@Profile(\"baseline-core\") keeps the scheduler out of the default context")
                .isEmpty();
        assertThat(context.getBeanNamesForType(PlanController.class))
                .as("and the endpoint with it")
                .isEmpty();
    }

    @Test
    @DisplayName("the security chain for /plans does exist, and that asymmetry is the point")
    void theSecurityChainIsUnconditional() {
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class))
                .as("two chains: the application's, and the one that keeps /plans out of the "
                        + "authenticated catch-all so its absence reads as 404")
                .hasSize(2)
                .contains("baselinePlanSecurityFilterChain");
    }

}
