package me.simonhamour.mysix.compat;

import java.util.*;
import java.util.function.BiPredicate;
import me.simonhamour.mysix.item.MysixCdItem;
import me.simonhamour.mysix.music.OnlineJukebox;
import me.simonhamour.mysix.music.VanillaSongCatalog;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Owns transitions between Backpack's play flag and Mysix's personal audio session. */
public final class BackpackPlaybackController {
    public interface Slot {
        ItemStack disc();
        boolean playing();
        void save(ItemStack disc);
        void stop();
    }
    @FunctionalInterface public interface Access { Optional<Slot> find(ServerPlayer player); }
    private final Access access;
    private final BiPredicate<ServerPlayer, OnlineJukebox.PersonalSource> start;
    private final Map<UUID, String> playing = new HashMap<>();

    public BackpackPlaybackController(Access access, BiPredicate<ServerPlayer, OnlineJukebox.PersonalSource> start) {
        this.access = access; this.start = start;
    }

    public static boolean isOnlineDisc(ItemStack disc) {
        return disc.getItem() instanceof MysixCdItem && disc.getCustomName() != null
                && VanillaSongCatalog.find(disc.getCustomName().getString()).isEmpty();
    }

    private static String identity(ItemStack disc) {
        return disc.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getStringOr("mysix_disc_id", "");
    }

    public void tick(List<ServerPlayer> players) {
        var present = new HashSet<UUID>();
        for (var player : players) {
            present.add(player.getUUID());
            var found = access.find(player).filter(slot -> slot.playing() && isOnlineDisc(slot.disc()));
            if (found.isEmpty()) { playing.remove(player.getUUID()); continue; }
            var slot = found.get();
            if (identity(slot.disc()).isEmpty()) {
                var disc = slot.disc().copy();
                var data = disc.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                data.putString("mysix_disc_id", UUID.randomUUID().toString());
                disc.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                slot.save(disc);
            }
            var disc = slot.disc().copy();
            String id = identity(disc), query = disc.getCustomName().getString();
            String key = id + "\n" + query;
            if (key.equals(playing.get(player.getUUID()))) continue;
            var source = new OnlineJukebox.PersonalSource() {
                private Optional<Slot> current() {
                    return access.find(player).filter(value -> id.equals(identity(value.disc()))
                            && value.disc().getCustomName() != null
                            && query.equals(value.disc().getCustomName().getString()));
                }
                public ItemStack disc() { return disc; }
                public boolean isActive() { return current().filter(Slot::playing).isPresent(); }
                public void save() { current().ifPresent(value -> value.save(disc.copy())); }
                public void stop() { current().ifPresent(Slot::stop); }
            };
            if (start.test(player, source)) playing.put(player.getUUID(), key);
            else source.stop();
        }
        playing.keySet().retainAll(present);
    }

    public void clear() { playing.clear(); }
}
