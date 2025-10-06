package net.mcreator.zombierool.event;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.mcreator.zombierool.block.PathBlock;
import net.mcreator.zombierool.block.LimitBlock;
import net.mcreator.zombierool.block.RestrictBlock;
import net.mcreator.zombierool.block.ObstacleDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.mcreator.zombierool.block.SpawnerZombieBlock;
import net.mcreator.zombierool.block.SpawnerDogBlock;
import net.mcreator.zombierool.block.SpawnerCrawlerBlock;
import net.mcreator.zombierool.block.PlayerSpawnerBlock;
import net.mcreator.zombierool.block.ZombiePassBlock;
import net.mcreator.zombierool.client.DrinkPerkAnimationHandler;

@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT)
public class ClientTickHandler {
    private static boolean lastIsCreative = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        boolean currentIsCreative = mc.player.isCreative();
        if (currentIsCreative != lastIsCreative) {
            lastIsCreative = currentIsCreative;
            Level level = mc.level;
            BlockPos playerPos = mc.player.blockPosition();
            int radius = 200; 

            // Tag pour les blocs autorisés
            TagKey<Block> allowedBlocks = TagKey.create(Registries.BLOCK, new ResourceLocation("zombierool", "allowed_blocks"));

            for (int x = playerPos.getX() - radius; x <= playerPos.getX() + radius; x++) {
                for (int y = playerPos.getY() - radius; y <= playerPos.getY() + radius; y++) {
                    for (int z = playerPos.getZ() - radius; z <= playerPos.getZ() + radius; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(pos);

                        // Vérifie si le bloc est dans le tag allowed_blocks ou
                        // est une instance de PathBlock, LimitBlock, ou RestrictBlock
                        if (state.is(allowedBlocks) 
                                || state.getBlock() instanceof PathBlock 
                                || state.getBlock() instanceof LimitBlock
                                || state.getBlock() instanceof ObstacleDoorBlock
                                || state.getBlock() instanceof SpawnerZombieBlock
                                || state.getBlock() instanceof SpawnerCrawlerBlock
                                || state.getBlock() instanceof PlayerSpawnerBlock
                                || state.getBlock() instanceof SpawnerDogBlock
                                || state.getBlock() instanceof ZombiePassBlock
                                || state.getBlock() instanceof RestrictBlock) {
                            level.sendBlockUpdated(pos, state, state, 3);
                        }
                    }
                }
            }
        }

        if (DrinkPerkAnimationHandler.isRunning()) {
		    if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)) {
		        mc.setScreen(null);
		    }
		}

    }
}
