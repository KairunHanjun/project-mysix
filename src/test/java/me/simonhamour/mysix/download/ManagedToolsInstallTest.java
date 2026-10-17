package me.simonhamour.mysix.download;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** External tool smoke test; intentionally does not depend on YouTube accepting the CI IP. */
@Tag("network")
@EnabledIfSystemProperty(named = "mysix.setupInstallTests", matches = "true")
class ManagedToolsInstallTest {
    @Test void installsAndStartsProviderOnDetectedHostAndReusesInstallation() throws Exception {
        try (var tools = new ManagedTools(Path.of(System.getProperty("mysix.setupTestDirectory")))) {
            System.out.println("MYSIX_INSTALL_PLATFORM=" + tools.platform());
            tools.install(System.out::println); assertTrue(tools.installed());
            tools.ensureProvider(); tools.ensureProvider();
            tools.install(message -> assertFalse(message.startsWith("Installing "), "Completed installation must be reused"));
        }
    }
}
