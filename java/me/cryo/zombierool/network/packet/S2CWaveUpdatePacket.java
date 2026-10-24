package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import me.cryo.zombierool.gameplay.WaveManager;

public class S2CWaveUpdatePacket {
    private final int wave;
    private final boolean running;
    private final boolean intro;

    public S2CWaveUpdatePacket(int wave) {
        this(wave, WaveManager.isServerGameRunning(),WaveManager.isIntroPhase());
    }
    public S2CWaveUpdatePacket(int wave, boolean running) {
        this(wave,running,false);
    }
    public S2CWaveUpdatePacket(int wave,boolean running,boolean intro){
        this.intro=intro;
        this.wave = wave;
        this.running = running;
    }

    public static S2CWaveUpdatePacket decode(FriendlyByteBuf buffer) {
        return new S2CWaveUpdatePacket(buffer.readInt(), buffer.readBoolean(),buffer.readBoolean());
    }

    public static void encode(S2CWaveUpdatePacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.wave);
        buffer.writeBoolean(msg.running);
        buffer.writeBoolean(msg.intro);
    }

    public static void handle(S2CWaveUpdatePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply(msg)));
        context.setPacketHandled(true);
    }

    private static final class ClientHandler {
        private static void apply(S2CWaveUpdatePacket msg) {
            if (net.minecraft.client.Minecraft.getInstance().level == null) return;
            WaveManager.setClientWave(msg.wave);
            WaveManager.setClientGameRunning(msg.running);
            WaveManager.setClientIntroPhase(msg.running&&msg.intro);
            if (!msg.running) {
                me.cryo.zombierool.client.ClientHUDHandler.resetRoundAnimations();
                me.cryo.zombierool.client.BloodSplatters.clear();
                me.cryo.zombierool.client.BloodJets.clientTick(null);
                me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponRenderer.clearAllPurchases();
                me.cryo.zombierool.client.DrinkPerkAnimationHandler.reset();
                me.cryo.zombierool.client.ThirdPersonAnimHandler.reset();
                me.cryo.zombierool.client.screens.TeleporterChargeOverlay.reset();
            }
        }
    }
}
