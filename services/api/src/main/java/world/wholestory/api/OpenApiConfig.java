package world.wholestory.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;

/** The generated document is the contract for the web client's typed API (web/openapi.json). */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Whole Story API")
                .description("Internal API consumed by the web application (BFF). Not exposed to the internet.")
                .version("v1"));
    }

    /**
     * Response fields are non-null by convention, so the generated TypeScript types do not force
     * the client to handle values that can never be missing.
     */
    @Bean
    OpenApiCustomizer fieldsAreRequiredByDefault() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().values().forEach(schema -> {
                if (schema.getProperties() != null) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                }
            });
        };
    }
}
