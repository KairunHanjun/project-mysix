package me.simonhamour.mysix.compat;

import java.lang.reflect.*;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.simonhamour.mysix.Mysix;
import me.simonhamour.mysix.music.OnlineJukebox;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** No third-party types in signatures: Backpack remains an optional runtime dependency. */
public final class TravelersBackpackCompat {
    private TravelersBackpackCompat() {}
    public static boolean supported() {
        return FabricLoader.getInstance().getModContainer("travelersbackpack")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("11.4.1")).orElse(false);
    }

    public static void initialize() {
        if (!supported()) {
            if (FabricLoader.getInstance().isModLoaded("travelersbackpack")) {
                Mysix.LOGGER.warn("Backpack music compatibility currently targets Traveler's Backpack 11.4.1 only.");
            }
            return;
        }
        try {
            var api = new Api();
            var controller = new BackpackPlaybackController(api, OnlineJukebox::tryPlayPersonal);
            ServerTickEvents.END_SERVER_TICK.register(server -> controller.tick(server.getPlayerList().getPlayers()));
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> controller.clear());
            Mysix.LOGGER.info("Traveler's Backpack 11.4.1 jukebox integration enabled.");
        } catch (ReflectiveOperationException error) {
            Mysix.LOGGER.error("Backpack API did not match the supported version; integration disabled", error);
        }
    }

    public static final class Api implements BackpackPlaybackController.Access {
        private final Class<?> upgradeClass;
        private final Method wearing, wrapper, manager, getUpgrade, holder, isPlaying, getStack, setStack, update;
        private final Field diskHandler, flags, playing, enabled;
        private boolean failed;

        @SuppressWarnings("unchecked")
        public Api() throws ReflectiveOperationException {
            String root = "com.tiviacz.travelersbackpack.";
            var loader = getClass().getClassLoader();
            // Inspect signatures without initializing Backpack's classes before its own entrypoint runs.
            var attachments = Class.forName(root + "attachment.AttachmentUtils", false, loader);
            upgradeClass = Class.forName(root + "inventory.upgrades.jukebox.JukeboxUpgrade", false, loader);
            var wrapperClass = Class.forName(root + "inventory.BackpackWrapper", false, loader);
            var managerClass = Class.forName(root + "inventory.UpgradeManager", false, loader);
            var handlerClass = Class.forName(root + "inventory.handler.ItemStackHandler", false, loader);
            var components = Class.forName(root + "init.ModDataComponents", false, loader);
            wearing = attachments.getMethod("isWearingBackpack", Player.class);
            wrapper = attachments.getMethod("getBackpackWrapper", Player.class, int[].class);
            flags = attachments.getField("UPGRADES_ONLY");
            manager = wrapperClass.getMethod("getUpgradeManager");
            getUpgrade = managerClass.getMethod("getUpgrade", Class.class);
            holder = upgradeClass.getMethod("getDataHolderStack");
            isPlaying = upgradeClass.getMethod("isPlayingRecord");
            diskHandler = upgradeClass.getField("diskHandler");
            getStack = handlerClass.getMethod("getStackInSlot", int.class);
            setStack = handlerClass.getMethod("setStackInSlot", int.class, ItemStack.class);
            update = upgradeClass.getMethod("updateDataHolderUnchecked", Consumer.class);
            playing = components.getField("IS_PLAYING");
            enabled = components.getField("UPGRADE_ENABLED");
        }

        @SuppressWarnings("unchecked")
        @Override public Optional<BackpackPlaybackController.Slot> find(ServerPlayer player) {
            if (failed || !player.isAlive()) return Optional.empty();
            try {
                if (!(boolean) wearing.invoke(null, player)) return Optional.empty();
                Object pack = wrapper.invoke(null, player, ((Supplier<?>) flags.get(null)).get());
                var upgrade = (Optional<?>) getUpgrade.invoke(manager.invoke(pack), upgradeClass);
                if (upgrade.isEmpty()) return Optional.empty();
                Object instance = upgrade.get(), handler = diskHandler.get(instance);
                var playingType = (DataComponentType<Boolean>) playing.get(null);
                var enabledType = (DataComponentType<Boolean>) enabled.get(null);
                return Optional.of(new BackpackPlaybackController.Slot() {
                    public ItemStack disc() {
                        var value = invoke(getStack, handler, 0);
                        return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
                    }
                    public boolean playing() {
                        return Boolean.TRUE.equals(invoke(isPlaying, instance))
                                && invoke(holder, instance) instanceof ItemStack stack && stack.getOrDefault(enabledType, true);
                    }
                    public void save(ItemStack disc) { invoke(setStack, handler, 0, disc); }
                    public void stop() {
                        invoke(update, instance, (Consumer<ItemStack>) stack -> stack.set(playingType, false));
                    }
                });
            } catch (ReflectiveOperationException error) {
                failed = true;
                Mysix.LOGGER.error("Backpack integration disabled after an API failure", error);
                return Optional.empty();
            }
        }

        private Object invoke(Method method, Object instance, Object... args) {
            if (failed) return null;
            try { return method.invoke(instance, args); }
            catch (ReflectiveOperationException error) {
                failed = true;
                Mysix.LOGGER.error("Backpack integration encountered an API failure", error);
                return null;
            }
        }
    }
}
