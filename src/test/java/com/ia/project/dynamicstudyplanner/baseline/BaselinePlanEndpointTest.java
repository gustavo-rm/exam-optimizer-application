package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.plan.PlanInvariantAssertions;
import com.ia.project.dynamicstudyplanner.plan.PlanRequests;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.coreapi.contract.SessionKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * {@code POST /plans} end to end, with the profile on.
 *
 * <h2>The exchange is the reference exchange</h2>
 *
 * The request posted is {@code src/test/resources/contract/plan-request-v1.0.json} — the document
 * {@code sinapse-platform} keeps byte for byte identical — and the answer's shape is compared against
 * {@code plan-response-v1.0.json}. A fixture written here could describe a payload the platform never
 * sends; these two cannot drift without {@code coreapi/CoreContractGoldenTest} failing as well.
 *
 * <h2>Why the shape is compared and the values are not</h2>
 *
 * The reference response is a <b>genetic</b> run's answer: five sessions, one of them a revision, and a
 * {@code fitness} map of evolutionary terms. This scheduler answers the same request differently, and
 * should. What has to match is the document's <b>shape</b> — which keys exist, at which paths, with
 * nulls omitted rather than written — because that is what the platform deserialises. The values are
 * pinned by {@code GreedyBaselineSchedulerTest} instead.
 *
 * <p>{@code fitness} is compared as a key and not descended into: the contract declares it opaque and
 * never interprets it ({@code docs/CORE_CONTRACT_SURVEY.md} §2.2), and this scheduler reports the terms
 * it actually computes rather than a genetic run's.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(PlanProtocol.PROFILE)
@DisplayName("POST /plans under the baseline-core profile")
class BaselinePlanEndpointTest {

    private static final String REQUEST_GOLDEN = "contract/plan-request-v1.0.json";
    private static final String RESPONSE_GOLDEN = "contract/plan-response-v1.0.json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Every key path of a document, with array indices collapsed and {@code fitness} left opaque. */
    private static Set<String> shapeOf(JsonNode node) {
        Set<String> paths = new LinkedHashSet<>();
        collect(node, "", paths);
        return paths;
    }

    private static void collect(JsonNode node, String path, Set<String> paths) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(name -> {
                String child = path.isEmpty() ? name : path + "." + name;
                paths.add(child);
                if (!"fitness".equals(name)) {
                    collect(node.get(name), child, paths);
                }
            });
        } else if (node.isArray()) {
            node.forEach(element -> collect(element, path + "[]", paths));
        }
    }

    private String golden(String resource) throws IOException {
        try (InputStream stream = new ClassPathResource(resource).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private MvcResult postGoldenRequest() throws Exception {
        return mockMvc.perform(post(PlanProtocol.PLANS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(golden(REQUEST_GOLDEN)))
                .andReturn();
    }

    @Test
    @DisplayName("the reference request is answered with a valid plan, and no credentials are needed")
    void answersTheReferenceRequest() throws Exception {
        MvcResult result = postGoldenRequest();

        assertThat(result.getResponse().getStatus())
                .as("/plans is permitAll, exactly as /api/v1/** is — no 401 and no 403")
                .isEqualTo(200);

        PlanRequest request = objectMapper.readValue(golden(REQUEST_GOLDEN), PlanRequest.class);
        PlanResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), PlanResponse.class);

        PlanInvariantAssertions.assertEveryInvariant(request, response);
        assertThat(response.fitness())
                .as("the answer says which condition of the experiment produced it")
                .containsEntry("strategy", "greedy-baseline");
        assertThat(response.metadata().coreVersion())
                .as("the build version: Maven's resource filtering has to have replaced the "
                        + "placeholder in application-baseline-core.properties")
                .isNotBlank()
                .doesNotContain("@")
                .matches("\\d+\\.\\d+.*");
    }

    @Test
    @DisplayName("the answer has the same shape as the reference response document")
    void theAnswerHasTheReferenceShape() throws Exception {
        JsonNode answer = objectMapper.readTree(
                postGoldenRequest().getResponse().getContentAsString());

        assertThat(shapeOf(answer))
                .as("serialised by the application's own ObjectMapper, so this is the wire shape")
                .isEqualTo(shapeOf(objectMapper.readTree(golden(RESPONSE_GOLDEN))));
    }

    @Test
    @DisplayName("a null field is omitted, never written as null")
    void omitsANullFieldRatherThanWritingIt() throws Exception {
        assertThat(postGoldenRequest().getResponse().getContentAsString())
                .as("nothing in a produced plan is null, so nothing should be written as one")
                .doesNotContain("null");

        // The executable proof that @JsonInclude(NON_NULL) is what does it, and that the application
        // mapper honours it. The contract package guarantees this; the test is here to catch anyone
        // who routes around the package or turns the inclusion into a global setting.
        String withoutMetadata = objectMapper.writeValueAsString(new PlanResponse(
                PlanRequest.VERSION,
                List.of(new PlanResponse.ScheduledSession(PlanRequests.TOPIC_1,
                        SessionKind.STUDY, Instant.parse("2026-09-01T19:00:00Z"), 30, 0)),
                Map.of(), null));

        assertThat(withoutMetadata)
                .as("the key disappears rather than appearing with a null value")
                .doesNotContain("metadata")
                .doesNotContain("null");
    }

    @Test
    @DisplayName("a cycle in the HARD edges is refused with 422, naming the topics of the cycle")
    void refusesACycleWith422() throws Exception {
        PlanRequest cyclic = PlanRequests.builder()
                .withPrerequisites(List.of(
                        PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_2),
                        PlanRequests.hard(PlanRequests.TOPIC_2, PlanRequests.TOPIC_1)))
                .build();

        MvcResult result = mockMvc.perform(post(PlanProtocol.PLANS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cyclic)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(422);

        JsonNode problem = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(problem.path("type").asText())
                .as("the RFC 7807 type names the category, the same way every other error here does")
                .isEqualTo("https://api.dynamicstudyplanner.com/errors/hard-prerequisite-cycle");
        assertThat(problem.path("status").asInt()).isEqualTo(422);
        assertThat(problem.path("offending")).isNotEmpty();
        assertThat(problem.path("offending").toString())
                .as("the body names the topics of the cycle, so the curator knows where to look")
                .contains(PlanRequests.TOPIC_1.toString(), PlanRequests.TOPIC_2.toString());
        assertThat(problem.path("detail").asText())
                .contains("does not choose an edge to ignore");
    }

    @Test
    @DisplayName("an unplannable request is refused with 422 and the service's own error shape")
    void refusesAnUnplannableRequestWith422() throws Exception {
        PlanRequest noWindows = PlanRequests.builder().withAvailability(List.of()).build();

        MvcResult result = mockMvc.perform(post(PlanProtocol.PLANS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noWindows)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(422);

        JsonNode problem = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(problem.path("type").asText()).endsWith("/no-availability");
        assertThat(problem.path("instance").asText()).isEqualTo(PlanProtocol.PLANS_PATH);
        assertThat(problem.has("timestamp"))
                .as("built by the application's ProblemDetails, so the error contract is one contract")
                .isTrue();
    }

    @Test
    @DisplayName("a malformed body is still a 400, handled by the application's own advice")
    void aMalformedBodyIsStillA400() throws Exception {
        assertThat(mockMvc.perform(post(PlanProtocol.PLANS_PATH)
                                .contentType(MediaType.APPLICATION_JSON).content("{"))
                        .andReturn().getResponse().getStatus())
                .as("this module adds no advice, so unreadable input keeps the existing behaviour")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("/plans stays out of the published OpenAPI document even with the profile on")
    void staysOutOfThePublishedOpenApiDocument() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(document).path("paths").has(PlanProtocol.PLANS_PATH))
                .as("the Core protocol is versioned by PlanRequest.VERSION and pinned by the "
                        + "reference documents, not by the OpenAPI snapshot")
                .isFalse();
    }
}
