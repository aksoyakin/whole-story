package world.wholestory.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The web client's types are generated from the committed {@code web/openapi.json}.
 * This test fails when the API changes without that contract being updated.
 * Update it with: {@code mvn -pl services/api -am verify -Dopenapi.update=true}
 * <p>
 * The endpoint it reads is switched off everywhere else, so this turns it on for itself. That is the point
 * rather than a workaround: the contract is a build artefact, this is the only thing that asks the running
 * application for it, and saying so here means the property in {@code application.yml} needs no exception.
 */
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractTest {

    private static final Path CONTRACT = Path.of("../../web/openapi.json");

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper jsonMapper;

    @Test
    void committedContractMatchesTheApi() throws Exception {
        byte[] body = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsByteArray();
        String actual = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonMapper.readTree(body)) + "\n";

        if (Boolean.getBoolean("openapi.update") || !Files.exists(CONTRACT)) {
            Files.writeString(CONTRACT, actual, StandardCharsets.UTF_8);
        }

        assertThat(Files.readString(CONTRACT, StandardCharsets.UTF_8))
                .as("web/openapi.json is out of date; run with -Dopenapi.update=true and regenerate the web client")
                .isEqualTo(actual);
    }
}
