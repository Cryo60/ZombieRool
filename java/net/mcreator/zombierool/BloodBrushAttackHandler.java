package net.mcreator.zombierool.event;

import net.mcreator.zombierool.WorldConfig;
import net.mcreator.zombierool.item.BloodBrushItem;
import net.mcreator.zombierool.network.BloodOverlayPacket;
import net.mcreator.zombierool.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BloodBrushAttackHandler {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        
        if (!(stack.getItem() instanceof BloodBrushItem)) {
            return;
        }

        Level level = player.level();
        if (level.isClientSide) {
            return;
        }

        BlockPos pos = event.getPos();
        Direction face = event.getFace();
        
        // Supprimer de WorldConfig
        if (level instanceof ServerLevel serverLevel) {
            WorldConfig config = WorldConfig.get(serverLevel);
            String key = pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + face.getName();
            config.removeBloodOverlay(key);
        }
        
        // Clic gauche : retirer overlay
        BloodOverlayPacket packet = new BloodOverlayPacket(pos, face, 0, 0, false);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)), packet);
        
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        
        if (!(stack.getItem() instanceof BloodBrushItem)) {
            return;
        }

        Level level = player.level();
        if (!level.isClientSide) {
            return;
        }

        // Raycast pour trouver le bloc visé
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getLookAngle();
        Vec3 endVec = eyePos.add(lookVec.scale(5.0));
        
        BlockHitResult result = level.clip(new ClipContext(eyePos, endVec, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        
        if (result.getType() == HitResult.Type.BLOCK) {
            // Le LeftClickBlock gérera l'action
        }
    }
}