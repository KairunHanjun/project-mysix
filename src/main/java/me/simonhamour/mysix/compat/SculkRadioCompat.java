package me.simonhamour.mysix.compat;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import me.simonhamour.mysix.music.OnlineJukebox;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Uses Sculk Radio's own stream traversal, including its relays and antennas. */
public final class SculkRadioCompat {
    private static Api api;
    private SculkRadioCompat() {}

    public static boolean supported() {
        return FabricLoader.getInstance().getModContainer("sculk-radio")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("1.0.0+26.3")).orElse(false);
    }

    public static void initialize() {
        if (!supported()) return;
        try { api = new Api(); }
        catch (ReflectiveOperationException error) {
            me.simonhamour.mysix.Mysix.LOGGER.warn("Sculk Radio adapter disabled: API or Kotlin runtime unavailable", error);
        }
    }

    public static OnlineJukebox.Route open(ServerLevel level, BlockPos jukebox) {
        if (api == null) return null;
        Object radio = api.node(level.getBlockEntity(jukebox.above()));
        return api.radio.isInstance(radio) ? new RadioRoute(level, jukebox, radio) : null;
    }

    private static final class RadioRoute implements OnlineJukebox.Route {
        final ServerLevel level;
        final BlockPos jukebox;
        final Object radio;
        final Set<Object> speakers = new HashSet<>();
        Object stream;
        boolean closed;
        RadioRoute(ServerLevel level, BlockPos jukebox, Object radio) {
            this.level = level; this.jukebox = jukebox; this.radio = radio;
            Function<Object, Object> factory = source -> {
                try {
                    Consumer<Object> connect = speakers::add, disconnect = speakers::remove;
                    stream = api.audio.newInstance(connect, disconnect, source, true);
                    return stream;
                } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            };
            api.call(api.start, radio, factory);
        }
        public boolean active() {
            return !closed && api.node(level.getBlockEntity(jukebox.above())) == radio
                    && !(boolean) api.call(api.removed, radio)
                    && !api.call(api.state, stream).toString().equals("STOPPED");
        }
        public List<OnlineJukebox.Output> outputs() {
            if (closed) return List.of();
            double range;
            try { range = Math.clamp(api.range.getFloat(null), 0, 64); }
            catch (IllegalAccessException error) { throw new IllegalStateException(error); }
            final double radius = range;
            return speakers.stream().filter(node -> (boolean) api.call(api.loaded, node)
                    && !(boolean) api.call(api.removed, node))
                    .map(node -> new OnlineJukebox.Output((ServerLevel) api.call(api.level, node),
                            (BlockPos) api.call(api.pos, node), radius)).toList();
        }
        public void close() {
            if (closed) return;
            closed = true;
            api.call(api.stop, stream);
            speakers.clear();
        }
    }

    private static final class Api {
        final Class<?> radio;
        final Constructor<?> audio;
        final Method fromEntity, start, stop, state, loaded, removed, level, pos;
        final Field range;
        Api() throws ReflectiveOperationException {
            var loader = getClass().getClassLoader();
            Class.forName("kotlin.Pair", false, loader);
            String root = "nl.gjorgdy.sculk_radio.";
            radio = Class.forName(root + "objects.nodes.audio.RadioNode", false, loader);
            var source = Class.forName(root + "objects.nodes.abstracts.SourceNode", false, loader);
            var node = Class.forName(root + "objects.nodes.abstracts.Node", false, loader);
            var stream = Class.forName(root + "objects.streams.Stream", false, loader);
            var audioType = Class.forName(root + "objects.streams.AudioStream", false, loader);
            audio = audioType.getConstructor(Consumer.class, Consumer.class, source, boolean.class);
            fromEntity = Class.forName(root + "utils.NodeUtils", false, loader).getMethod("getFromBlockEntity", BlockEntity.class);
            range = Class.forName(root + "SculkRadio", false, loader).getField("speakerRange");
            start = source.getMethod("start", Function.class);
            stop = stream.getMethod("stop"); state = stream.getMethod("getState");
            loaded = node.getMethod("isLoaded"); removed = node.getMethod("wasRemoved");
            level = node.getMethod("getLevel"); pos = node.getMethod("getPos");
        }
        Object node(BlockEntity entity) { return call(fromEntity, null, entity); }
        Object call(Method method, Object target, Object... args) {
            try { return method.invoke(target, args); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
    }
}
