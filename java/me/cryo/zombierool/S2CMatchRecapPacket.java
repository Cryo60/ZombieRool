package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import me.cryo.zombierool.client.gui.MatchRecapScreen;
import net.minecraft.client.Minecraft;

public class S2CMatchRecapPacket {
    private final int waves, kills, headshots, assists, downs, score;

    public S2CMatchRecapPacket(int waves, int kills, int headshots, int assists, int downs, int score) {
        this.waves = waves;
        this.kills = kills;
        this.headshots = headshots;
        this.assists = assists;
        this.downs = downs;
        this.score = score;
    }

    public S2CMatchRecapPacket(FriendlyByteBuf buf) {
        this.waves = buf.readInt();
        this.kills = buf.readInt();
        this.headshots = buf.readInt();
        this.assists = buf.readInt();
        this.downs = buf.readInt();
        this.score = buf.readInt();
    }

    public static void encode(S2CMatchRecapPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.waves);
        buf.writeInt(msg.kills);
        buf.writeInt(msg.headshots);
        buf.writeInt(msg.assists);
        buf.writeInt(msg.downs);
        buf.writeInt(msg.score);
    }

    public static S2CMatchRecapPacket decode(FriendlyByteBuf buf) {
        return new S2CMatchRecapPacket(buf);
    }

    public static void handle(S2CMatchRecapPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
                if (player != null && player.isDeadOrDying()) {
                    player.connection.send(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                }
                me.cryo.zombierool.event.ClientEventHandler.pendingRecapScreen = new MatchRecapScreen(
                    msg.waves, msg.kills, msg.headshots, msg.assists, msg.downs, msg.score
                );
                me.cryo.zombierool.event.ClientEventHandler.pendingRecapTimer = 40; 
            });
        });
        ctx.get().setPacketHandled(true);
    }
}