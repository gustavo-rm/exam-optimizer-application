package com.ia.project.dynamicstudyplanner.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OptimizerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturn400WhenPopulationSizeExceedsMaximum() throws Exception {
        // Arrange
        String requestJson = """
                {
                  "exam": {
                    "name": "Test Exam",
                    "examDate": "2030-12-01",
                    "generalKnowledgeTotalScore": 50,
                    "generalKnowledgeSubjects": [],
                    "specificKnowledgeAxes": []
                  },
                  "studentProfile": {
                    "name": "Test User",
                    "knowledgeGaps": {"Math": 2.0},
                    "weeklyAvailability": {"MONDAY": 5}
                  },
                  "gaConfig": {
                    "totalStudyDays": 100,
                    "numGenerations": 100,
                    "populationSize": 1000000
                  }
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.invalid_params[0].name").value("gaConfig.populationSize"));
    }

    @Test
    void shouldReturn400WhenExamNameIsBlank() throws Exception {
        // Arrange
        String requestJson = """
                {
                  "exam": {
                    "name": "",
                    "examDate": "2030-12-01",
                    "generalKnowledgeTotalScore": 50,
                    "generalKnowledgeSubjects": [],
                    "specificKnowledgeAxes": []
                  },
                  "studentProfile": {
                    "name": "Test User",
                    "knowledgeGaps": {"Math": 2.0},
                    "weeklyAvailability": {"MONDAY": 5}
                  },
                  "gaConfig": {
                    "totalStudyDays": 100,
                    "numGenerations": 100,
                    "populationSize": 100
                  }
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params[0].name").value("exam.name"));
    }
}
