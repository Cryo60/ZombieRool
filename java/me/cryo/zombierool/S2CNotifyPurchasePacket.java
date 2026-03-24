package me.cryo.zombierool.network;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
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
        ctx.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                BuyWallWeaponRenderer.markAsPurchased(player, pkt.weaponId);
            }
        });
        ctx.setPacketHandled(true);
    }
}