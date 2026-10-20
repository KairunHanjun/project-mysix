package me.simonhamour.mysix.platform;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import me.simonhamour.mysix.network.JukeboxAudioPayload;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;

public final class MysixClientPlatform {
    private MysixClientPlatform() {}
    public static void initializeAudio(BiConsumer<Minecraft, JukeboxAudioPayload> receiver,
            Consumer<Minecraft> disconnect, Consumer<Minecraft> tick) {
        MysixPlatform.clientReceiver(packet -> receiver.accept(Minecraft.getInstance(), packet));
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> disconnect.accept(Minecraft.getInstance()));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> tick.accept(Minecraft.getInstance()));
    }
}
