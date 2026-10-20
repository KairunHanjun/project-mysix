package me.simonhamour.mysix;

import me.simonhamour.mysix.item.ModItems;
import me.simonhamour.mysix.music.CdJukeboxInteraction;
import me.simonhamour.mysix.music.OnlineJukebox;
import me.simonhamour.mysix.download.DownloadCommands;
import me.simonhamour.mysix.network.JukeboxAudioPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Mysix.MOD_ID)
public final class Mysix {
    public static final String MOD_ID = "mysix";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
    public Mysix(FMLJavaModLoadingContext context) {
        ModItems.initialize(context);
        if (!net.minecraftforge.fml.loading.FMLLoader.isProduction()) ForgeChecks.register(context);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(event -> {
            var result = CdJukeboxInteraction.use(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
            }
            return result != InteractionResult.PASS;
        });
        DownloadCommands.initialize();
        JukeboxAudioPayload.register();
        OnlineJukebox.initialize();
        LOGGER.info("Project Mysix Forge loaded: 16 craftable CDs.");
    }
    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(me.simonhamour.mysix.client.MysixClient::initialize);
        }
    }
}
