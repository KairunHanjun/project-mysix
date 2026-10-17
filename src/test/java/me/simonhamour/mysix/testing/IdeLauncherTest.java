package me.simonhamour.mysix.testing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

class IdeLauncherTest {
    @Test
    void separateDiscoverySessionsReuseFabricAndRestoreContextClassLoader() {
        var thread = Thread.currentThread();
        var original = thread.getContextClassLoader();
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(
                        "me.simonhamour.mysix.CdCraftingTest"))
                .build();

        // This is the lifecycle the IDE uses: create/discover, then create again.
        // The upstream listener throws Duplicate setLauncher call on recreation.
        for (int i = 0; i < 2; i++) {
            try (var session = LauncherFactory.openSession()) {
                var plan = session.getLauncher().discover(request);
                assertFalse(plan.getRoots().isEmpty());
            }
            assertSame(original, thread.getContextClassLoader());
        }
    }
}
