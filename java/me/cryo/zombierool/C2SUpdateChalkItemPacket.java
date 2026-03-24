package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.item.ChalkItem;

import java.util.function.Supplier;

public class C2SUpdateChalkItemPacket {
    private final String texture;
    private final int rotation;
    private final InteractionHand hand;

    public C2SUpdateChalkItemPacket(String texture, int rotation, InteractionHand hand) {
        this.texture = texture;
        this.rotation = rotation;
        this.hand = hand;
    }

    public static void encode(C2SUpdateChalkItemPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.texture);
        buf.writeInt(msg.rotation);
        buf.writeEnum(msg.hand);
    }

    public static C2SUpdateChalkItemPacket decode(FriendlyByteBuf buf) {
        return new C2SUpdateChalkItemPacket(buf.readUtf(), buf.readInt(), buf.readEnum(InteractionHand.class));
    }

    public static void handle(C2SUpdateChalkItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                ItemStack stack = player.getItemInHand(msg.hand);
                if (stack.getItem() instanceof ChalkItem) {
                    stack.getOrCreateTag().putString("chalk_texture", msg.texture);
                    stack.getOrCreateTag().putInt("chalk_rotation", msg.rotation);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}