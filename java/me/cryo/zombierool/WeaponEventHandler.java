package me.cryo.zombierool.core.system;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.network.PacketShoot;
import me.cryo.zombierool.item.PlasmaPistolItem;
import me.cryo.zombierool.item.FlamethrowerItem;
import me.cryo.zombierool.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WeaponEventHandler {

    @Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientHandlers {

        private static boolean canClientShoot(WeaponSystem.BaseGunItem gun, ItemStack stack, Player player, boolean isLeft) {
            if (gun.hasOverheat()) {
                return !stack.getOrCreateTag().getBoolean(WeaponSystem.BaseGunItem.TAG_IS_OVERHEATED) && gun.getOverheat(stack) < gun.getMaxOverheat();
            } else if (gun.hasDurability()) {
                return gun.getDurability(stack) > 0;
            } else {
                int ammo = isLeft ? gun.getAmmoLeft(stack) : gun.getAmmo(stack);
                return ammo > 0;
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            ItemStack stack = mc.player.getMainHandItem();
            boolean attackDown = mc.options.keyAttack.isDown(); 
            boolean useDown = mc.options.keyUse.isDown();       

            if (stack.getItem() instanceof WeaponSystem.BaseGunItem gun) {
                if (gun instanceof FlamethrowerItem flamethrower) {
                    if (attackDown && !flamethrower.isOverheatLocked(stack) && flamethrower.getOverheat(stack) < 990) {
                        FlamethrowerItem.FlamethrowerClient.keepAlive(mc.player);
                    }
                } else if (gun instanceof PlasmaPistolItem) {
                    PlasmaPistolItem.PlasmaPistolClient.handleTick(mc, mc.player, stack, attackDown);
                }

                if (!(gun instanceof WeaponImplementations.MeleeWeaponItem) && !(gun instanceof PlasmaPistolItem)) {
                    long now = mc.level.getGameTime();

                    if (gun.isAkimbo(stack)) {
                        if (attackDown) {
                            long lastFireLeft = stack.getOrCreateTag().getLong("LastFireLeftClient");
                            if (now - lastFireLeft >= gun.getFireRate(stack, mc.player)) {
                                NetworkHandler.INSTANCE.sendToServer(new PacketShoot(0f, true));
                                stack.getOrCreateTag().putLong("LastFireLeftClient", now);
                                if (canClientShoot(gun, stack, mc.player, true)) applyRecoil(mc.player, gun, stack);
                            }
                        }
                        if (useDown) {
                            long lastFireRight = stack.getOrCreateTag().getLong("LastFireRightClient");
                            if (now - lastFireRight >= gun.getFireRate(stack, mc.player)) {
                                NetworkHandler.INSTANCE.sendToServer(new PacketShoot(0f, false));
                                stack.getOrCreateTag().putLong("LastFireRightClient", now);
                                if (canClientShoot(gun, stack, mc.player, false)) applyRecoil(mc.player, gun, stack);
                            }
                        }
                    } else {
                        if (attackDown) {
                            long lastFire = stack.getOrCreateTag().getLong("LastFireClient");
                            if (now - lastFire >= gun.getFireRate(stack, mc.player)) {
                                NetworkHandler.INSTANCE.sendToServer(new PacketShoot(0f, false));
                                stack.getOrCreateTag().putLong("LastFireClient", now);
                                if (canClientShoot(gun, stack, mc.player, false)) applyRecoil(mc.player, gun, stack);
                            }
                        }
                    }
                }
            } else {
                PlasmaPistolItem.PlasmaPistolClient.stopSound();
            }
        }

        private static void applyRecoil(Player player, WeaponSystem.BaseGunItem gun, ItemStack stack) {
            float pitchRecoil = gun.getDefinition().recoil.pitch;
            float yawRecoil = gun.getDefinition().recoil.yaw;
            if (gun.isPackAPunched(stack)) {
                pitchRecoil *= gun.getDefinition().pap.recoil_mult;
                yawRecoil *= gun.getDefinition().pap.recoil_mult;
            }
            player.turn((player.getRandom().nextBoolean() ? 1 : -1) * yawRecoil, -pitchRecoil);
        }

        @SubscribeEvent
        public static void onInputInteraction(InputEvent.InteractionKeyMappingTriggered event) {
            if (Minecraft.getInstance().player != null) {
                ItemStack stack = Minecraft.getInstance().player.getMainHandItem();
                if (stack.getItem() instanceof WeaponSystem.BaseGunItem gun) {
                    if (gun instanceof WeaponImplementations.MeleeWeaponItem) {
                        return;
                    }
                    if (event.isAttack()) {
                        event.setCanceled(true);
                        event.setSwingHand(false);
                    }
                    if (event.isUseItem() && gun.isAkimbo(stack)) {
                        event.setCanceled(true);
                        event.setSwingHand(false);
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onRenderHand(RenderHandEvent event) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                ItemStack mainStack = player.getMainHandItem();
                if (mainStack.getItem() instanceof WeaponSystem.BaseGunItem gun) {
                    if (event.getHand() == InteractionHand.MAIN_HAND) {
                        boolean isReloading = mainStack.getOrCreateTag().getBoolean(WeaponSystem.BaseGunItem.TAG_IS_RELOADING);
                        if (isReloading) {
                            event.getPoseStack().translate(0.0, -0.5, 0.0);
                        }
                    }

                    if (event.getHand() == InteractionHand.OFF_HAND && gun.isAkimbo(mainStack)) {
                        event.setCanceled(true);
                        PoseStack poseStack = event.getPoseStack();
                        poseStack.pushPose();

                        float equipProgress = event.getEquipProgress();
                        float swingProgress = event.getSwingProgress();

                        float f = -0.4F * Mth.sin(Mth.sqrt(swingProgress) * (float)Math.PI);
                        float f1 = 0.2F * Mth.sin(Mth.sqrt(swingProgress) * ((float)Math.PI * 2F));
                        float f2 = -0.2F * Mth.sin(swingProgress * (float)Math.PI);

                        poseStack.translate(f, f1, f2);
                        poseStack.translate(-0.56F, -0.52F + equipProgress * -0.6F, -0.72F);

                        boolean isReloading = mainStack.getOrCreateTag().getBoolean(WeaponSystem.BaseGunItem.TAG_IS_RELOADING);
                        if (isReloading) {
                            poseStack.translate(0.0, -0.5, 0.0);
                        }

                        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
                            player,
                            mainStack,
                            net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                            false, 
                            poseStack,
                            event.getMultiBufferSource(),
                            event.getPackedLight()
                        );
                        poseStack.popPose();
                    }
                }
            }
        }
    }
}