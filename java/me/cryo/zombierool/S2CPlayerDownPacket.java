package me.cryo.zombierool.network;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.handlers.KeyInputHandler;
import me.cryo.zombierool.client.ClientPlayerDownSoundManager;
import me.cryo.zombierool.player.PlayerDownManager;
import java.util.UUID;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class S2CPlayerDownPacket {
    private final boolean isDown;
    private final UUID playerUUID; 

    public S2CPlayerDownPacket(boolean isDown, UUID playerUUID) {
        this.isDown = isDown;
        this.playerUUID = playerUUID;
    }

    public static void encode(S2CPlayerDownPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isDown);
        buf.writeUUID(msg.playerUUID); 
    }

    public static S2CPlayerDownPacket decode(FriendlyByteBuf buf) {
        boolean isDown = buf.readBoolean();
        UUID playerUUID = buf.readUUID();
        return new S2CPlayerDownPacket(isDown, playerUUID);
    }

    public static void handle(S2CPlayerDownPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                Minecraft mc = Minecraft.getInstance();
                if (msg.isDown) {
                    KeyInputHandler.downPlayers.add(msg.playerUUID);
                } else {
                    KeyInputHandler.downPlayers.remove(msg.playerUUID);
                }

                if (mc.player != null) {
                    if (mc.player.getUUID().equals(msg.playerUUID)) {
                        KeyInputHandler.isLocalPlayerDown = msg.isDown; 
                        if (msg.isDown) {
                            mc.player.sendSystemMessage(Component.translatable("message.zombierool.down.downed_self"));
                            ClientPlayerDownSoundManager.startLastStandSound();
                        } else {
                            mc.player.sendSystemMessage(Component.translatable("message.zombierool.down.revived_self"));
                            ClientPlayerDownSoundManager.stopLastStandSound();
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
    }
}