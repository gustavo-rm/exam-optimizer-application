package com.ia.project.dynamicstudyplanner.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI/Swagger configuration.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Dynamic Study Planner API",
                version = "v1",
                description = "A stateless REST API that uses Genetic Algorithms to orchestrate an optimized and mathematically superior study plan. Built with Spring Boot.",
                contact = @Contact(
                        name = "Engineering Team",
                        email = "engineering@example.com"
                ),
                license = @License(
                        name = "MIT License",
                        url = "https://opensource.org/licenses/MIT"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local Development Environment")
        }
)
public class OpenApiConfig {
}
