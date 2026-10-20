package me.simonhamour.mysix.platform;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import me.simonhamour.mysix.network.JukeboxAudioPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class MysixClientPlatform {
    private MysixClientPlatform() {}
    public static void initializeAudio(BiConsumer<Minecraft, JukeboxAudioPayload> receiver,
            Consumer<Minecraft> disconnect, Consumer<Minecraft> tick) {
        ClientPlayNetworking.registerGlobalReceiver(JukeboxAudioPayload.TYPE, (packet, context) -> receiver.accept(context.client(), packet));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disconnect.accept(client));
        ClientTickEvents.END_CLIENT_TICK.register(tick::accept);
    }
}
