package me.simonhamour.mysix.testing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.knot.Knot;
import net.fabricmc.loader.impl.util.SystemProperties;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Fabric Loader 0.19.5's JUnit listener creates a Knot per listener instance.
 * Eclipse/JDT-based runners create multiple instances in the same JVM.
 * Keep Fabric's process-wide launcher and restore each session's context loader.
 * Test infrastructure only: this class is not packaged in the mod.
 */
public final class ReusableFabricSessionListener implements LauncherSessionListener {
    private final ClassLoader gameClassLoader;
    private final ThreadLocal<Deque<ClassLoader>> previousLoaders =
            ThreadLocal.withInitial(ArrayDeque::new);

    public ReusableFabricSessionListener() {
        System.setProperty(SystemProperties.DEVELOPMENT, "true");
        System.setProperty(SystemProperties.UNIT_TEST, "true");
        synchronized (FabricLauncherBase.class) {
            var thread = Thread.currentThread();
            var original = thread.getContextClassLoader();
            try {
                var launcher = FabricLauncherBase.getLauncher();
                if (launcher == null) {
                    var side = EnvType.valueOf(System.getProperty(
                            SystemProperties.SIDE, "CLIENT").toUpperCase(Locale.ROOT));
                    var knot = new Knot(side);
                    gameClassLoader = knot.init(new String[0]);
                } else {
                    gameClassLoader = launcher.getTargetClassLoader();
                }
            } finally {
                thread.setContextClassLoader(original);
            }
        }
    }

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        var thread = Thread.currentThread();
        previousLoaders.get().push(thread.getContextClassLoader());
        thread.setContextClassLoader(gameClassLoader);
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        var stack = previousLoaders.get();
        Thread.currentThread().setContextClassLoader(stack.pop());
        if (stack.isEmpty()) previousLoaders.remove();
    }
}
