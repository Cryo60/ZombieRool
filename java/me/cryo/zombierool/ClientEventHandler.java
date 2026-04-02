package me.cryo.zombierool.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.glfw.GLFW;

import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.system.DefenseWallSystem;
import me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponBlock;
import me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponBlockEntity;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.api.IReloadable;
import me.cryo.zombierool.client.DrinkPerkAnimationHandler;
import me.cryo.zombierool.client.CutsweepAnimationHandler;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.init.KeyBindings;
import me.cryo.zombierool.handlers.KeyInputHandler;
import me.cryo.zombierool.network.C2SUnifiedInteractPacket;
import me.cryo.zombierool.network.C2SReloadWeaponPacket;
import me.cryo.zombierool.network.C2SMeleeAttackPacket;
import me.cryo.zombierool.network.InteractionType;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.C2SToggleCrawlPacket;
import me.cryo.zombierool.network.packet.C2SRequestConfigMenuPacket;
import me.cryo.zombierool.network.packet.C2SSyncClientPrefsPacket;
import me.cryo.zombierool.network.packet.C2SSyncEquippedCamosPacket;
import me.cryo.zombierool.network.packet.C2SSyncEquippedSkinsPacket;
import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.client.career.LocalCareerManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandler {
    private static boolean lastIsCreative = false;
    private static boolean wallPurchaseKeyWasDown = false;
    private static final double WALL_PURCHASE_MAX_DIST = 2.5;

    private static final int BASE_REPAIR_COOLDOWN = 1250;
    private static final double SPEED_COLA_REPAIR_MULTIPLIER = 0.5;
    private static long lastRepairTime = 0;
    private static final int REPAIR_SOUND_INTERVAL = 500;
    private static long lastRepairSoundTime = 0;

    private static long lastMeleeTime = 0;

    public static me.cryo.zombierool.client.gui.MatchRecapScreen pendingRecapScreen = null;
    public static int pendingRecapTimer = 0;

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        NetworkHandler.INSTANCE.sendToServer(
            new C2SSyncClientPrefsPacket(
                ZRClientConfig.getHalloweenMode().name(),
                ZRClientConfig.prefersZrWeapons()
            )
        );
        LocalCareerManager.load();
        NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedCamosPacket(LocalCareerManager.getData().equippedCamos));
        NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedSkinsPacket(LocalCareerManager.getData().equippedSkins));
    }

    @SubscribeEvent
    public static void onMouseScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player != null && me.cryo.zombierool.WaveManager.isGameRunning() && !player.isCreative() && !player.isSpectator()) {
            double delta = event.getScrollDelta();
            if (delta != 0) {
                event.setCanceled(true); 
                
                int dir = delta > 0 ? -1 : 1;
                int sel = player.getInventory().selected;
                int limit = WeaponFacade.getWeaponLimit(player);
                
                List<Integer> validSlots = new ArrayList<>();
                validSlots.add(0); 
                for (int j = 1; j <= limit; j++) validSlots.add(j); 
                for (int j = limit + 1; j < 9; j++) {
                    if (!player.getInventory().getItem(j).isEmpty()) {
                        validSlots.add(j);
                    }
                }
                
                int currentIndex = validSlots.indexOf(sel);
                if (currentIndex == -1) currentIndex = 0;
                
                int nextIndex = (currentIndex + dir) % validSlots.size();
                if (nextIndex < 0) nextIndex += validSlots.size();
                
                int nextSlot = validSlots.get(nextIndex);
                player.getInventory().selected = nextSlot;
                
                if (mc.getConnection() != null) {
                    mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(nextSlot));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (pendingRecapTimer > 0) {
            pendingRecapTimer--;
            if (pendingRecapTimer <= 0 && pendingRecapScreen != null) {
                mc.setScreen(pendingRecapScreen);
                pendingRecapScreen = null;
            }
        }

        if (player == null || mc.level == null) return;

        if (me.cryo.zombierool.WaveManager.isGameRunning() && !player.isCreative() && !player.isSpectator()) {
            int limit = WeaponFacade.getWeaponLimit(player);
            int sel = player.getInventory().selected;
            if (sel > limit && player.getInventory().getItem(sel).isEmpty()) {
                player.getInventory().selected = 0;
                if (mc.getConnection() != null) {
                    mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
                }
            }
        }

        boolean currentIsCreative = player.isCreative();
        if (currentIsCreative != lastIsCreative) {
            lastIsCreative = currentIsCreative;
            if (mc.levelRenderer != null) {
                mc.levelRenderer.allChanged();
            }
        }

        if (DrinkPerkAnimationHandler.isRunning()) {
            if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)) {
                mc.setScreen(null);
            }
        }

        boolean isFDown = KeyBindings.REPAIR_AND_PURCHASE_KEY.isDown();
        if (isFDown && !wallPurchaseKeyWasDown && !KeyInputHandler.isLocalPlayerDown) { 
            wallPurchaseKeyWasDown = true;
            if (!player.isCreative()) { 
                HitResult ray = mc.hitResult;
                if (ray instanceof BlockHitResult bhr) {
                    BlockPos pos = bhr.getBlockPos();
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.getBlock() instanceof BuyWallWeaponBlock && mc.level.getBlockEntity(pos) instanceof BuyWallWeaponBlockEntity) {
                        if (bhr.getDirection() == state.getValue(BuyWallWeaponBlock.FACING)) {
                            double dx = Math.abs(player.getX() - (pos.getX() + .5));
                            double dy = Math.abs(player.getY() - (pos.getY() + .5));
                            double dz = Math.abs(player.getZ() - (pos.getZ() + .5));
                            if (dx <= WALL_PURCHASE_MAX_DIST && dy <= WALL_PURCHASE_MAX_DIST && dz <= WALL_PURCHASE_MAX_DIST) {
                                NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(pos, InteractionType.WALL_WEAPON));
                            }
                        }
                    }
                }
            }
        }
        if (!isFDown) {
            wallPurchaseKeyWasDown = false;
        }

        if (isFDown && !KeyInputHandler.isLocalPlayerDown) {
            BlockPos targetPos = DefenseDoorSystem.DefenseDoorBlock.getDoorInRepairZone(mc.level, player.blockPosition());
            boolean isWall = false;

            if (targetPos == null) {
                targetPos = DefenseWallSystem.getWallInRepairZone(mc.level, player.blockPosition());
                isWall = true;
            }

            if (targetPos != null) {
                BlockState state = mc.level.getBlockState(targetPos);
                int currentStage = isWall ? state.getValue(DefenseWallSystem.DefenseWallBlock.STAGE) : state.getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE);
                int maxStage = isWall ? 7 : DefenseDoorSystem.DefenseDoorBlock.MAX_STAGE;
                boolean isPermOpen = isWall ? state.getValue(DefenseWallSystem.DefenseWallBlock.PERMANENTLY_OPEN) : state.getValue(DefenseDoorSystem.DefenseDoorBlock.PERMANENTLY_OPEN);

                if (!isPermOpen && currentStage < maxStage) {
                    long now = System.currentTimeMillis();
                    boolean hasSpeedCola = player.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get());
                    long effectiveCooldown = (long) (BASE_REPAIR_COOLDOWN * (hasSpeedCola ? SPEED_COLA_REPAIR_MULTIPLIER : 1.0));

                    if (lastRepairTime == 0 || now - lastRepairTime >= effectiveCooldown) {
                        lastRepairTime = now;
                        NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(targetPos, InteractionType.REPAIR_BARRICADE));
                    }

                    if (now - lastRepairSoundTime >= REPAIR_SOUND_INTERVAL) {
                        if (isWall) {
                            player.level().playSound(player, targetPos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "rock_slam")), net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 1.0f);
                            player.level().playSound(player, targetPos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "repairing_rock")), net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 1.0f);
                        } else {
                            player.level().playSound(player, targetPos, ZombieroolModSounds.BOARDS_FLOAT.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 1.0f);
                        }
                        lastRepairSoundTime = now;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (KeyInputHandler.isLocalPlayerDown) {
            mc.options.keyJump.setDown(false);
        }

        if (KeyBindings.RELOAD_KEY.consumeClick()) {
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof IReloadable reloadable) {
                reloadable.startReload(stack, mc.player);
                NetworkHandler.INSTANCE.sendToServer(new C2SReloadWeaponPacket());
            }
        }

        while (KeyBindings.MELEE_ATTACK_KEY.consumeClick()) {
            long now = System.currentTimeMillis();
            if (!CutsweepAnimationHandler.isRunning() && (now - lastMeleeTime > 800)) {
                lastMeleeTime = now;
                CutsweepAnimationHandler.startCutsweepAnimation(() -> {});
                NetworkHandler.INSTANCE.sendToServer(new C2SMeleeAttackPacket());
            }
        }

        if (KeyBindings.CRAWL_KEY.consumeClick() && !KeyInputHandler.isLocalPlayerDown) {
            NetworkHandler.INSTANCE.sendToServer(new C2SToggleCrawlPacket());
        }

        if (KeyBindings.CONFIG_MENU_KEY.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new C2SRequestConfigMenuPacket());
        }

        if (event.getAction() == GLFW.GLFW_RELEASE && event.getKey() == KeyBindings.REPAIR_AND_PURCHASE_KEY.getKey().getValue()) {
            lastRepairSoundTime = 0;
        }
    }
}