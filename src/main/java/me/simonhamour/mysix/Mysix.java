package me.simonhamour.mysix;

import me.simonhamour.mysix.item.ModItems;
import me.simonhamour.mysix.music.CdJukeboxInteraction;
import me.simonhamour.mysix.download.DownloadCommands;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Mysix implements ModInitializer {
    public static final String MOD_ID = "mysix";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModItems.initialize();
        // Recheck at insertion too: handles old stacks and command-given names.
        UseBlockCallback.EVENT.register(CdJukeboxInteraction::use);
        DownloadCommands.initialize();
        me.simonhamour.mysix.network.JukeboxAudioPayload.register();
        me.simonhamour.mysix.music.OnlineJukebox.initialize();
        me.simonhamour.mysix.compat.TravelersBackpackCompat.initialize();
        me.simonhamour.mysix.compat.AdditionalAdditionsCompat.initialize();
        LOGGER.info("Project Mysix loaded: 16 craftable CDs.");
    }
}
