package com.refinements.visualizer.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** springdoc metadata matching contracts/openapi.yaml (T012). Swagger UI: /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI visualizerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Architecture Visualizer API")
                        .version("1.0.0")
                        .description("Backend API for the Microservice Architecture & Scenario Visualizer."));
    }

    @Bean
    GroupedOpenApi apiFlow() {
        return GroupedOpenApi.builder().group("api").pathsToMatch("/api/**").build();
    }
}
