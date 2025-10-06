package net.mcreator.zombierool;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.mcreator.zombierool.block.entity.BuyWallWeaponBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * Le code de cet élément de mod est toujours verrouillé.
 *
 * Vous pouvez également enregistrer de nouveaux événements dans cette classe.
 *
 * Si vous voulez faire une classe indépendante, créez-la en-dehors de
 * net.mcreator.zombierool, car ce package est géré par MCreator.
 *
 * Cette classe sera ajoutée dans le package racine du mod.
 */
public record SetWallWeaponConfigPacket(BlockPos pos, int price, ResourceLocation itemToSell) {

    public static void encode(SetWallWeaponConfigPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos());
        buf.writeInt(pkt.price());
        buf.writeResourceLocation(pkt.itemToSell());
    }

    public static SetWallWeaponConfigPacket decode(FriendlyByteBuf buf) {
        return new SetWallWeaponConfigPacket(
            buf.readBlockPos(),
            buf.readInt(),
            buf.readResourceLocation()
        );
    }

    public static void handle(SetWallWeaponConfigPacket pkt, Supplier<NetworkEvent.Context> ctx) {
	    ctx.get().enqueueWork(() -> {
	        ServerPlayer sender = ctx.get().getSender();
	        if (sender == null) return;
	
	        // Récupérer le ServerLevel
	        Level lvl = sender.getCommandSenderWorld();
	        if (!(lvl instanceof ServerLevel serverLevel)) return;
	
	        BlockEntity be = serverLevel.getBlockEntity(pkt.pos());
	        if (!(be instanceof BuyWallWeaponBlockEntity weaponBe)) return;
	
	        // 1) Mettre à jour le prix et la ressource
	        weaponBe.setPrice(pkt.price());
	        weaponBe.setItemToSell(pkt.itemToSell());
	
	        // 2) Mettre à jour l’inventaire interne (slot 0) via la capability
	        ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(pkt.itemToSell()));
	        weaponBe.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(cap -> {
	            if (cap instanceof ItemStackHandler handler) {
	                handler.setStackInSlot(0, stack);
	            }
	        });
	
	        // 3) Marquer comme dirty et notifier le client
	        weaponBe.setChanged();
	        serverLevel.sendBlockUpdated(
	            pkt.pos(),
	            weaponBe.getBlockState(),
	            weaponBe.getBlockState(),
	            3
	        );
	    });
	    ctx.get().setPacketHandled(true);
	}
}
