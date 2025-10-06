package net.mcreator.zombierool.item;

import net.mcreator.zombierool.WorldConfig;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.BloodOverlayPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

public class BloodBrushItem extends Item {

    public BloodBrushItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.COMMON));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        
        int textureIndex = level.random.nextInt(10) + 1;
		int rotation = level.random.nextInt(4) * 90; // 0, 90, 180, ou 270
        
        
        level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, 
            SoundSource.BLOCKS, 0.8f, 0.8f + level.random.nextFloat() * 0.4f);
        
        String key = pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + face.getName();
        System.out.println("DEBUG BloodBrush: Création de l'overlay avec clé: " + key);
        
        // Sauvegarder dans WorldConfig
        if (level instanceof ServerLevel serverLevel) {
		    WorldConfig config = WorldConfig.get(serverLevel);
		    config.addBloodOverlay(key, textureIndex + ":" + rotation);
		}
        
        BloodOverlayPacket packet = new BloodOverlayPacket(pos, face, textureIndex, rotation, true);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)), packet);
        
        return InteractionResult.SUCCESS;
    }
}