package net.mcreator.zombierool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class InstantTreeGrowthHandler {

    @SubscribeEvent
    public static void onBonemealUsed(BonemealEvent event) {
        if (event.getBlock().getBlock() instanceof SaplingBlock sapling) {
            event.setResult(BonemealEvent.Result.ALLOW);

            if (!event.getLevel().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) event.getLevel();
                BlockPos pos = event.getPos();
                BlockState state = event.getBlock();

                // Forcer la croissance immédiate de l'arbre
                sapling.performBonemeal(
                    serverLevel,
                    serverLevel.getRandom(),
                    pos,
                    state
                );

                // Mise à jour du bloc pour s'assurer que l'arbre pousse correctement
                serverLevel.levelEvent(2005, pos, 0); // Effet visuel de la poudre d'os
            }
        }
    }
}