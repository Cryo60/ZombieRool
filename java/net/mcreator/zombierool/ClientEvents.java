package net.mcreator.zombierool.events;

import net.mcreator.zombierool.init.KeyBindings;
import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.mcreator.zombierool.init.ZombieroolModBlockEntities;
import net.mcreator.zombierool.init.ZombieroolModMobEffects;
import net.mcreator.zombierool.network.RepairBarricadeMessage;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.block.DefenseDoorBlock;
import net.mcreator.zombierool.client.renderer.TraitorBlockRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.effect.MobEffectInstance;
import net.mcreator.zombierool.block.entity.TraitorBlockEntity;
import net.mcreator.zombierool.block.entity.PerksLowerBlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.lwjgl.glfw.GLFW;

public class ClientEvents {
    
    // Classe pour les événements FORGE bus
    @Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeEvents {
        private static final int BASE_COOLDOWN = 1250;
        private static final double SPEED_COLA_MULTIPLIER = 0.5;
        private static long lastRepairTime = 0;
        private static final int SOUND_INTERVAL = 500;
        private static long lastSoundTime = 0;
        private static final Minecraft mc = Minecraft.getInstance();

        @SubscribeEvent
        public static void onKeyRelease(InputEvent.Key event) {
            if (event.getAction() == GLFW.GLFW_RELEASE &&
                    event.getKey() == KeyBindings.REPAIR_AND_PURCHASE_KEY.getKey().getValue()) {
                lastRepairTime = 0;
                lastSoundTime = 0;
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (mc.player == null) return;

            if (KeyBindings.REPAIR_AND_PURCHASE_KEY.isDown()) {
                BlockPos doorPos = DefenseDoorBlock.getDoorInRepairZone(mc.player.level(), mc.player.blockPosition());
                if (doorPos != null) {
                    BlockState state = mc.player.level().getBlockState(doorPos);

                    if (state.getBlock() instanceof DefenseDoorBlock) {
                        int currentStage = state.getValue(DefenseDoorBlock.STAGE);

                        long now = System.currentTimeMillis();

                        boolean hasSpeedCola = mc.player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get());
                        long effectiveCooldown = (long) (BASE_COOLDOWN * (hasSpeedCola ? SPEED_COLA_MULTIPLIER : 1.0));

                        if (currentStage < DefenseDoorBlock.MAX_STAGE) {
                            if (lastRepairTime == 0 || now - lastRepairTime >= effectiveCooldown) {
                                lastRepairTime = now;
                                NetworkHandler.INSTANCE.sendToServer(new RepairBarricadeMessage(doorPos, 0));
                            }

                            if (now - lastSoundTime >= SOUND_INTERVAL) {
                                mc.player.level().playSound(mc.player, doorPos,
                                        ZombieroolModSounds.BOARDS_FLOAT.get(),
                                        SoundSource.BLOCKS, 0.3f, 1.0f);
                                lastSoundTime = now;
                            }
                        }
                    }
                }
            } else {
                lastRepairTime = 0;
                lastSoundTime = 0;
            }
        }
    }
    
    // Classe pour les événements MOD bus
    @Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {

        @SuppressWarnings("unchecked")
        @SubscribeEvent
        public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(
                    (BlockEntityType<TraitorBlockEntity>) (Object) ZombieroolModBlockEntities.TRAITOR.get(),
                    TraitorBlockRenderer::new
            );
        }
    }
}