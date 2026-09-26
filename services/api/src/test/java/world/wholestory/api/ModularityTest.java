package world.wholestory.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void boundedContextsRespectModuleBoundaries() {
        ApplicationModules.of(ApiApplication.class).verify();
    }
}
