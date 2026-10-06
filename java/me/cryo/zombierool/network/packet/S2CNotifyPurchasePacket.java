package me.cryo.zombierool.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponRenderer;
import java.util.function.Supplier;
public class S2CNotifyPurchasePacket {
    private final String weaponId;
    public S2CNotifyPurchasePacket(String weaponId) {
        this.weaponId = weaponId;
    }
    public S2CNotifyPurchasePacket(FriendlyByteBuf buf) {
        this.weaponId = buf.readUtf();
    }
    public static void encode(S2CNotifyPurchasePacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.weaponId);
    }
    public static S2CNotifyPurchasePacket decode(FriendlyByteBuf buf) {
        return new S2CNotifyPurchasePacket(buf);
    }
    public static void handle(S2CNotifyPurchasePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply(pkt)));
        ctx.setPacketHandled(true);
    }

    private static final class ClientHandler {
        private static void apply(S2CNotifyPurchasePacket pkt) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                BuyWallWeaponRenderer.markAsPurchased(player, pkt.weaponId);
            }
        }
    }
}