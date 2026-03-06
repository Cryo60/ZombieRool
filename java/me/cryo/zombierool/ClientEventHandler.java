package me.cryo.zombierool.event;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.api.IReloadable;
import me.cryo.zombierool.block.BuyWallWeaponBlock;
import me.cryo.zombierool.block.ObstacleDoorBlock;
import me.cryo.zombierool.block.entity.BuyWallWeaponBlockEntity;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.client.CutsweepAnimationHandler;
import me.cryo.zombierool.client.DrinkPerkAnimationHandler;
import me.cryo.zombierool.handlers.KeyInputHandler;
import me.cryo.zombierool.init.KeyBindings;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.network.*;
import me.cryo.zombierool.player.PlayerDownManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandler {

private static boolean lastIsCreative = false;
private static boolean wallPurchaseKeyWasDown = false;
private static final double WALL_PURCHASE_MAX_DIST = 2.5;

private static final int BASE_REPAIR_COOLDOWN = 1250;
private static final double SPEED_COLA_REPAIR_MULTIPLIER = 0.5;
private static long lastRepairTime = 0;
private static final int REPAIR_SOUND_INTERVAL = 500;
private static long lastRepairSoundTime = 0;

@SubscribeEvent
public static void onClientTick(TickEvent.ClientTickEvent event) {
if (event.phase != TickEvent.Phase.END) return;
Minecraft mc = Minecraft.getInstance();
LocalPlayer player = mc.player;
if (player == null || mc.level == null) return;

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
    BlockPos doorPos = DefenseDoorSystem.DefenseDoorBlock.getDoorInRepairZone(mc.level, player.blockPosition());
    if (doorPos != null) {
        BlockState state = mc.level.getBlockState(doorPos);
        if (state.getBlock() instanceof DefenseDoorSystem.DefenseDoorBlock) {
            int currentStage = state.getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE);
            boolean isPermOpen = state.getValue(DefenseDoorSystem.DefenseDoorBlock.PERMANENTLY_OPEN);

            if (!isPermOpen && currentStage < DefenseDoorSystem.DefenseDoorBlock.MAX_STAGE) {
                long now = System.currentTimeMillis();
                boolean hasSpeedCola = player.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get());
                long effectiveCooldown = (long) (BASE_REPAIR_COOLDOWN * (hasSpeedCola ? SPEED_COLA_REPAIR_MULTIPLIER : 1.0));

                if (lastRepairTime == 0 || now - lastRepairTime >= effectiveCooldown) {
                    lastRepairTime = now;
                    NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(doorPos, InteractionType.REPAIR_BARRICADE));
                }
                if (now - lastRepairSoundTime >= REPAIR_SOUND_INTERVAL) {
                    player.level().playSound(player, doorPos, ZombieroolModSounds.BOARDS_FLOAT.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 1.0f);
                    lastRepairSoundTime = now;
                }
            }
        }
    }
}
// Removed logic that reset lastRepairTime on else to prevent exploit
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
            NetworkHandler.INSTANCE.sendToServer(new ReloadWeaponMessage());
        }
    }

    if (KeyBindings.MELEE_ATTACK_KEY.consumeClick() && !CutsweepAnimationHandler.isRunning()) {
        CutsweepAnimationHandler.startCutsweepAnimation(() -> {});
        NetworkHandler.INSTANCE.sendToServer(new MeleeAttackPacket());
    }

    if (event.getAction() == GLFW.GLFW_RELEASE && event.getKey() == KeyBindings.REPAIR_AND_PURCHASE_KEY.getKey().getValue()) {
        // Do NOT reset lastRepairTime here to prevent spam
        lastRepairSoundTime = 0;
    }
}

@SubscribeEvent
public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
if (event.getLevel().isClientSide && event.getHand() == InteractionHand.MAIN_HAND) {
Player player = event.getEntity(); 
if (player.isCreative() && player.isSecondaryUseActive()) {
BlockPos clickedPos = event.getPos();
if (event.getLevel().getBlockState(clickedPos).getBlock() instanceof ObstacleDoorBlock) {
ItemStack heldItem = player.getItemInHand(event.getHand());
if (!heldItem.isEmpty() && heldItem.getItem() instanceof BlockItem blockItem) {
Block blockToCopy = blockItem.getBlock();
if (!(blockToCopy instanceof ObstacleDoorBlock)) {
event.setCanceled(true);
NetworkHandler.INSTANCE.sendToServer(new ObstacleDoorCopyBlockPacket(clickedPos, blockToCopy));
player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aTexture d'obstacle copiée !"), true);
}
}
}
}
}
}
}