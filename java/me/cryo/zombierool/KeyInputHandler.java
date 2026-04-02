package me.cryo.zombierool.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.gui.Font;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import me.cryo.zombierool.network.C2SUnifiedInteractPacket;
import me.cryo.zombierool.network.C2SRequestAmmoCrateInfoPacket;
import me.cryo.zombierool.network.C2SReviveAttemptPacket;
import me.cryo.zombierool.network.InteractionType;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.C2SUpdateLethalStatePacket;
import me.cryo.zombierool.network.packet.C2SThrowBackGrenadePacket;
import me.cryo.zombierool.init.KeyBindings;

import me.cryo.zombierool.block.system.PerksSystem.PerksAColaBlock;
import me.cryo.zombierool.block.system.PerksSystem.PerksAColaBlockEntity;
import me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock;
import me.cryo.zombierool.block.system.PerksSystem.DummyPart;
import me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock;
import me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlockEntity;
import me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock;
import me.cryo.zombierool.block.system.BlindBuySystem.BlindBuyCabinetBlock;
import me.cryo.zombierool.block.system.BlindBuySystem.BlindBuyCabinetBlockEntity;
import me.cryo.zombierool.block.DerWunderfizzBlock;
import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;
import me.cryo.zombierool.block.AmmoCrateBlock;
import me.cryo.zombierool.block.system.PackAPunchSystem.PackAPunchBlock;
import me.cryo.zombierool.block.system.PackAPunchSystem.PackAPunchBlockEntity;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.system.DefenseWallSystem;
import me.cryo.zombierool.block.system.MeteoriteEasterEgg;
import me.cryo.zombierool.PerksManager;
import me.cryo.zombierool.item.IngotSaleItem;

import me.cryo.zombierool.client.ClientPlayerDownSoundManager;
import me.cryo.zombierool.client.ClientInteractableManager;

import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.api.IReloadable;

import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class KeyInputHandler {

    public static boolean isLocalPlayerDown = false;
    public static final Set<UUID> downPlayers = new HashSet<>();
    public static long reviveBarStartTime = 0;
    public static UUID revivingTargetUUID = null;
    
    private static int clientAmmoCratePrice = 2500;
    private static boolean clientCanPurchaseAmmo = true;
    private static int ammoCrateInfoRequestCooldown = 0;

    private static boolean keyWasDown = false;
    private static boolean wasLethalDown = false;
    public static int clientGrenadeCookTimer = 0;

    private static final List<Component> activeHUDMessages = new ArrayList<>();
    public static BlockPos clientActiveWunderfizzPosition = null;

    private static long clientReviveDuration = 120; 

    private static final int BASE_REPAIR_COOLDOWN = 1250;
    private static final double SPEED_COLA_REPAIR_MULTIPLIER = 0.5;
    private static long lastRepairTime = 0;
    private static final int REPAIR_SOUND_INTERVAL = 500;
    private static long lastRepairSoundTime = 0;

    private static BlockPos targetInteractPos = null;
    private static InteractionType targetInteractType = null;
    private static String targetInteractCustomId = null;

    private static class InteractionCandidate {
        BlockPos pos;
        InteractionType type;
        Component message;
        String customId;

        InteractionCandidate(BlockPos pos, InteractionType type, Component message) {
            this.pos = pos;
            this.type = type;
            this.message = message;
        }
    }

    public static void updateAmmoCrateInfo(int price, boolean canPurchase, String hudMessage) {
        clientAmmoCratePrice = price;
        clientCanPurchaseAmmo = canPurchase;
    }

    public static void setClientReviveDuration(long duration) {
        clientReviveDuration = duration;
    }

    public static void setActiveWunderfizzPosition(BlockPos pos) {
        clientActiveWunderfizzPosition = pos;
    }

    public static BlockPos getActiveWunderfizzPosition() {
        return clientActiveWunderfizzPosition;
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        isLocalPlayerDown = false;
        downPlayers.clear();
        reviveBarStartTime = 0;
        revivingTargetUUID = null;
        clientActiveWunderfizzPosition = null;
        activeHUDMessages.clear();
        targetInteractPos = null;
        targetInteractType = null;
        targetInteractCustomId = null;
        ClientPlayerDownSoundManager.stopLastStandSound();
        clientGrenadeCookTimer = 0;
        wasLethalDown = false;
        keyWasDown = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) return;

        if (mc.screen != null) {
            if (wasLethalDown) {
                NetworkHandler.INSTANCE.sendToServer(new C2SUpdateLethalStatePacket(2));
                wasLethalDown = false;
                clientGrenadeCookTimer = 0;
            }
            keyWasDown = false;
            return;
        }

        LocalPlayer player = mc.player;

        boolean isLethalDown = KeyBindings.LETHAL_GRENADE_KEY.isDown();

        if (isLethalDown) {
            if (!wasLethalDown) {
                boolean foundGrenadeToPickup = false;
                AABB searchBox = mc.player.getBoundingBox().inflate(3.0);
                for (Entity e : mc.level.getEntitiesOfClass(Entity.class, searchBox)) {
                    if (e instanceof me.cryo.zombierool.item.throwable.Grenade.GrenadeEntity grenade) {
                        Vec3 toGrenade = grenade.position().subtract(mc.player.getEyePosition()).normalize();
                        if (mc.player.getLookAngle().dot(toGrenade) > 0.8) {
                            NetworkHandler.INSTANCE.sendToServer(new C2SThrowBackGrenadePacket(grenade.getId()));
                            foundGrenadeToPickup = true;
                            break;
                        }
                    }
                }
                
                if (!foundGrenadeToPickup) {
                    NetworkHandler.INSTANCE.sendToServer(new C2SUpdateLethalStatePacket(1));
                    clientGrenadeCookTimer = 0;
                }
            } else {
                if (clientGrenadeCookTimer < 100) {
                    clientGrenadeCookTimer++;
                }
            }
        } else {
            if (wasLethalDown) {
                NetworkHandler.INSTANCE.sendToServer(new C2SUpdateLethalStatePacket(2));
            }
            clientGrenadeCookTimer = 0;
        }

        wasLethalDown = isLethalDown;

        if (isLocalPlayerDown) {
            keyWasDown = KeyBindings.REPAIR_AND_PURCHASE_KEY.isDown();
            reviveBarStartTime = 0;
            revivingTargetUUID = null;
            return;
        }

        boolean isFDown = KeyBindings.REPAIR_AND_PURCHASE_KEY.isDown();

        Player detectedReviveTarget = null;
        double minDistSq = 2.25;
        for (Player p : mc.level.players()) {
            if (p.getUUID().equals(player.getUUID())) continue;
            if (downPlayers.contains(p.getUUID()) && p.distanceToSqr(player) < minDistSq) {
                detectedReviveTarget = p;
                break;
            }
        }

        if (isFDown && detectedReviveTarget != null && !player.isSpectator()) {
            NetworkHandler.INSTANCE.sendToServer(new C2SReviveAttemptPacket(detectedReviveTarget.getUUID(), false));
        } else if (keyWasDown && revivingTargetUUID != null) {
            boolean shouldCancelClientSide = !isFDown || detectedReviveTarget == null || !detectedReviveTarget.getUUID().equals(revivingTargetUUID);
            if (shouldCancelClientSide) {
                player.displayClientMessage(Component.translatable("message.zombierool.down.cancel_invalid").withStyle(ChatFormatting.RED), true);
                NetworkHandler.INSTANCE.sendToServer(new C2SReviveAttemptPacket(revivingTargetUUID, true));
            }
        }

        scanForInteractions(player, mc);

        if (isFDown && !KeyInputHandler.isLocalPlayerDown && !player.isSpectator()) {
            if (targetInteractType == InteractionType.REPAIR_BARRICADE && targetInteractPos != null) {
                long now = System.currentTimeMillis();
                boolean hasSpeedCola = player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get());
                long effectiveCooldown = (long) (BASE_REPAIR_COOLDOWN * (hasSpeedCola ? SPEED_COLA_REPAIR_MULTIPLIER : 1.0));

                if (lastRepairTime == 0 || now - lastRepairTime >= effectiveCooldown) {
                    lastRepairTime = now;
                    NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(targetInteractPos, InteractionType.REPAIR_BARRICADE));
                }

                if (now - lastRepairSoundTime >= REPAIR_SOUND_INTERVAL) {
                    BlockState targetState = mc.level.getBlockState(targetInteractPos);
                    boolean isWall = targetState.getBlock() instanceof me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallBlock || targetState.getBlock() instanceof me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallDummyBlock;
                    
                    if (isWall) {
                        player.level().playSound(player, targetInteractPos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "rock_slam")), net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 1.0f);
                        player.level().playSound(player, targetInteractPos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "repairing_rock")), net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 1.0f);
                    } else {
                        player.level().playSound(player, targetInteractPos, ZombieroolModSounds.BOARDS_FLOAT.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 1.0f);
                        player.level().playSound(player, targetInteractPos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "repairing_plank")), net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 1.0f);
                    }
                    lastRepairSoundTime = now;
                }
            }
        }

        if (isFDown && !keyWasDown && !player.isSpectator()) {
            if (revivingTargetUUID == null && detectedReviveTarget == null) {
                if (targetInteractType != null && targetInteractType != InteractionType.REPAIR_BARRICADE && targetInteractPos != null) {
                    NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(targetInteractPos, targetInteractType));
                } else if (targetInteractCustomId != null) {
                    me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnCustomInteract", player.getUUID().toString(), targetInteractCustomId);
                } else if (targetInteractPos == null && targetInteractCustomId == null) {
                    NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(player.blockPosition(), InteractionType.ACTION_KEY));
                }
            }
        }

        keyWasDown = isFDown;

        if (ammoCrateInfoRequestCooldown > 0) {
            ammoCrateInfoRequestCooldown--;
        }
    }

    private static boolean isBlockedByWall(Level level, Vec3 start, Vec3 end, BlockPos targetPos) {
        net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
                start, end, 
                net.minecraft.world.level.ClipContext.Block.COLLIDER, 
                net.minecraft.world.level.ClipContext.Fluid.NONE, 
                Minecraft.getInstance().player
        );
        net.minecraft.world.phys.BlockHitResult result = level.clip(context);
        if (result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockPos hitPos = result.getBlockPos();
            if (targetPos != null && hitPos.equals(targetPos)) {
                return false; 
            }
            return true; 
        }
        return false;
    }

    private static void scanForInteractions(LocalPlayer player, Minecraft mc) {
        activeHUDMessages.clear();
        targetInteractPos = null;
        targetInteractType = null;
        targetInteractCustomId = null;

        if (player.isCreative() || player.isSpectator()) return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f);
        String actionKey = KeyBindings.REPAIR_AND_PURCHASE_KEY.getTranslatedKeyMessage().getString().toUpperCase();

        double bestScore = Double.MAX_VALUE;
        InteractionCandidate bestCandidate = null;

        double maxDistSq = 12.0; 

        for (me.cryo.zombierool.core.manager.InteractableManager.Interactable inter : ClientInteractableManager.getInteractables().values()) {
            double distSq = eyePos.distanceToSqr(inter.pos);
            if (distSq <= Math.min(inter.radius * inter.radius, maxDistSq)) {
                Vec3 dir = inter.pos.subtract(eyePos).normalize();
                double dot = lookVec.dot(dir);
                if (distSq < 2.0 || dot > 0.4) { 
                    if (isBlockedByWall(mc.level, eyePos, inter.pos, null)) continue;
                    
                    double score = distSq - (dot * 10.0);
                    if (score < bestScore) {
                        bestScore = score;
                        String msg = Component.translatable(inter.langKey).getString().replace("%key%", actionKey);
                        bestCandidate = new InteractionCandidate(null, InteractionType.ACTION_KEY, Component.literal(msg).withStyle(ChatFormatting.YELLOW));
                        bestCandidate.customId = inter.id;
                    }
                }
            }
        }

        BlockPos pPos = player.blockPosition();
        for (BlockPos iterPos : BlockPos.betweenClosed(pPos.offset(-2, -2, -2), pPos.offset(2, 2, 2))) {
            BlockPos pos = iterPos.immutable();
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) continue;

            Vec3 blockCenter = Vec3.atCenterOf(pos);
            double distSq = eyePos.distanceToSqr(blockCenter);
            if (distSq > maxDistSq) continue; 

            Vec3 dirToBlock = blockCenter.subtract(eyePos).normalize();
            double dot = lookVec.dot(dirToBlock);

            if (distSq >= 2.0 && dot < 0.4) continue; 

            if (isBlockedByWall(mc.level, eyePos, blockCenter, pos)) continue;

            double score = distSq - (dot * 10.0);
            if (score < bestScore) {
                InteractionCandidate cand = evaluateBlock(state, pos, player, mc.level, actionKey);
                if (cand != null) {
                    bestScore = score;
                    bestCandidate = cand;
                }
            }
        }

        if (bestCandidate != null) {
            targetInteractPos = bestCandidate.pos;
            targetInteractType = bestCandidate.type;
            targetInteractCustomId = bestCandidate.customId;
            if (bestCandidate.message != null) {
                activeHUDMessages.add(bestCandidate.message);
            }
        }
    }

    private static InteractionCandidate evaluateBlock(BlockState state, BlockPos pos, LocalPlayer player, Level level, String actionKey) {
        Block block = state.getBlock();

        if (block instanceof me.cryo.zombierool.block.PowerSwitchBlock) {
            if (!state.getValue(me.cryo.zombierool.block.PowerSwitchBlock.POWERED)) {
                return new InteractionCandidate(pos, InteractionType.POWER_SWITCH, Component.translatable("message.zombierool.power_switch.turn_on", actionKey).withStyle(ChatFormatting.YELLOW));
            }
            return null;
        }

        if (block instanceof PerksAColaDummyBlock) {
            BlockPos mainPos = state.getValue(PerksAColaDummyBlock.PART) == DummyPart.LOWER ? pos.above() : pos.below();
            BlockState mainState = level.getBlockState(mainPos);
            if (mainState.getBlock() instanceof PerksAColaBlock) {
                return evaluateBlock(mainState, mainPos, player, level, actionKey);
            }
            return null;
        }

        if (block instanceof PerksAColaBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PerksAColaBlockEntity perksBE) {
                boolean powered = perksBE.isPowered();
                String perkId = perksBE.getSavedPerkId();
                PerksManager.Perk perk = PerksManager.ALL_PERKS.get(perkId);

                if (perk != null) {
                    net.minecraft.world.effect.MobEffect effect = perk.getAssociatedEffect();
                    if (!powered) {
                        return new InteractionCandidate(pos, InteractionType.PERK, Component.translatable("message.zombierool.power_required").withStyle(ChatFormatting.RED));
                    } else if (PerksManager.getPerkCount(player) >= PerksManager.MAX_PERKS_LIMIT && (effect == null || !player.hasEffect(effect))) {
                        return new InteractionCandidate(pos, InteractionType.PERK, Component.translatable("message.zombierool.max_perks", PerksManager.MAX_PERKS_LIMIT).withStyle(ChatFormatting.RED));
                    } else if (effect != null && player.hasEffect(effect)) {
                        return new InteractionCandidate(pos, InteractionType.PERK, Component.translatable("message.zombierool.already_have_perk", perk.getName()).withStyle(ChatFormatting.GREEN));
                    } else {
                        int price = perksBE.getSavedPrice();
                        boolean hasIngot = hasIngot(player);
                        if (hasIngot) {
                            return new InteractionCandidate(pos, InteractionType.PERK, Component.translatable("message.zombierool.buy_perk_ingot", actionKey, perk.getName()).withStyle(ChatFormatting.WHITE));
                        } else {
                            return new InteractionCandidate(pos, InteractionType.PERK, Component.translatable("message.zombierool.buy_perk", actionKey, perk.getName(), price).withStyle(ChatFormatting.WHITE));
                        }
                    }
                }
            }
            return null;
        }

        if (block instanceof MysteryBoxBlock && !state.getValue(MysteryBoxBlock.PART)) {
            if (!state.getValue(MysteryBoxBlock.ACTIVE)) {
                return new InteractionCandidate(pos, InteractionType.MYSTERY_BOX, Component.translatable("message.zombierool.mystery_box.not_here").withStyle(ChatFormatting.GRAY));
            } else {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MysteryBoxBlockEntity mysteryBox) {
                    if (mysteryBox.getBoxState() == 0) {
                        return new InteractionCandidate(pos, InteractionType.MYSTERY_BOX, Component.translatable("message.zombierool.mystery_box.buy", actionKey, 950).withStyle(ChatFormatting.WHITE));
                    } else if (mysteryBox.getBoxState() == 1) {
                        return new InteractionCandidate(pos, InteractionType.MYSTERY_BOX, Component.translatable("message.zombierool.mystery_box.searching").withStyle(ChatFormatting.YELLOW));
                    } else if (mysteryBox.getBoxState() == 2 && !mysteryBox.isTeddy()) {
                        return new InteractionCandidate(pos, InteractionType.MYSTERY_BOX, Component.translatable("message.zombierool.mystery_box.take", actionKey).withStyle(ChatFormatting.GREEN));
                    }
                }
            }
            return null;
        }

        if (block instanceof DerWunderfizzBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DerWunderfizzBlockEntity wunderfizz) {
                DerWunderfizzBlockEntity.WunderfizzState stateEnum = wunderfizz.getState();
                boolean isActivePosition = clientActiveWunderfizzPosition != null && clientActiveWunderfizzPosition.equals(pos);

                if (stateEnum == DerWunderfizzBlockEntity.WunderfizzState.IDLE) {
                    if (!isActivePosition) {
                        return new InteractionCandidate(pos, InteractionType.WUNDERFIZZ_BUY, Component.translatable("message.zombierool.wunderfizz.not_here").withStyle(ChatFormatting.RED));
                    } else if (!state.getValue(DerWunderfizzBlock.POWERED)) {
                        return new InteractionCandidate(pos, InteractionType.WUNDERFIZZ_BUY, Component.translatable("message.zombierool.power_required").withStyle(ChatFormatting.RED));
                    } else {
                        boolean hasIngot = hasIngot(player);
                        if (hasIngot) {
                            return new InteractionCandidate(pos, InteractionType.WUNDERFIZZ_BUY, Component.translatable("message.zombierool.wunderfizz.buy_ingot", actionKey).withStyle(ChatFormatting.WHITE));
                        } else {
                            return new InteractionCandidate(pos, InteractionType.WUNDERFIZZ_BUY, Component.translatable("message.zombierool.wunderfizz.buy", actionKey, 1500).withStyle(ChatFormatting.WHITE));
                        }
                    }
                } else {
                    if (wunderfizz.getSelectedPerkId() != null) {
                        return new InteractionCandidate(pos, InteractionType.WUNDERFIZZ_COLLECT, Component.translatable("message.zombierool.wunderfizz.collect", actionKey).withStyle(ChatFormatting.GREEN));
                    } else {
                        return new InteractionCandidate(pos, InteractionType.WUNDERFIZZ_COLLECT, Component.translatable("message.zombierool.wunderfizz.preparing").withStyle(ChatFormatting.YELLOW));
                    }
                }
            }
            return null;
        }

        if (block instanceof BlindBuyCabinetBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BlindBuyCabinetBlockEntity cabinet) {
                boolean isOpen = state.getValue(BlindBuyCabinetBlock.OPEN);
                int price = cabinet.getPrice();
                
                if (!isOpen) {
                    return new InteractionCandidate(pos, InteractionType.BLIND_BUY_CABINET, Component.translatable("message.zombierool.blind_buy.buy", actionKey, price).withStyle(ChatFormatting.WHITE));
                } else {
                    net.minecraft.world.item.ItemStack weapon = cabinet.getWeapon();
                    String wpnName = weapon.getHoverName().getString();

                    boolean isTacz = WeaponFacade.isTaczWeapon(weapon);
                    me.cryo.zombierool.core.system.WeaponSystem.Definition def = WeaponFacade.getDefinition(weapon);
                    net.minecraft.world.item.ItemStack existing = net.minecraft.world.item.ItemStack.EMPTY;
                    String wId = WeaponFacade.getWeaponId(weapon);

                    for (net.minecraft.world.item.ItemStack s : player.getInventory().items) {
                        if (isTacz && WeaponFacade.isTaczWeapon(s)) {
                            if (wId.equals(s.getOrCreateTag().getString("GunId"))) { existing = s; break; }
                        } else if (!isTacz && def != null && WeaponFacade.isWeapon(s)) {
                            me.cryo.zombierool.core.system.WeaponSystem.Definition d = WeaponFacade.getDefinition(s);
                            if (d != null && d.id.replace("zombierool:", "").equals(def.id.replace("zombierool:", ""))) { existing = s; break; }
                        } else if (!isTacz && def == null && s.getItem() == weapon.getItem()) {
                            existing = s; break;
                        }
                    }

                    if (!existing.isEmpty()) {
                        int ammoPrice = Math.max(1, price / 2);
                        if (WeaponFacade.isPackAPunched(existing)) ammoPrice += 5000;
                        return new InteractionCandidate(pos, InteractionType.BLIND_BUY_CABINET, Component.translatable("message.zombierool.blind_buy.refill", actionKey, wpnName, ammoPrice).withStyle(ChatFormatting.WHITE));
                    } else {
                        return new InteractionCandidate(pos, InteractionType.BLIND_BUY_CABINET, Component.translatable("message.zombierool.blind_buy.buy_revealed", actionKey, wpnName, price).withStyle(ChatFormatting.WHITE));
                    }
                }
            }
            return null;
        }

        if (block instanceof AmmoCrateBlock) {
            if (ammoCrateInfoRequestCooldown <= 0) {
                NetworkHandler.INSTANCE.sendToServer(new C2SRequestAmmoCrateInfoPacket());
                ammoCrateInfoRequestCooldown = 20;
            }
            if (clientCanPurchaseAmmo) {
                return new InteractionCandidate(pos, InteractionType.AMMO_CRATE, Component.translatable("message.zombierool.ammo_crate.hud_refill", actionKey, clientAmmoCratePrice).withStyle(ChatFormatting.WHITE));
            } else {
                return new InteractionCandidate(pos, InteractionType.AMMO_CRATE, Component.translatable("message.zombierool.ammo_crate.hud_already_purchased").withStyle(ChatFormatting.RED));
            }
        }

        if (block instanceof PackAPunchBlock) {
            BlockEntity te = level.getBlockEntity(pos);
            if (te instanceof PackAPunchBlockEntity be) {
                boolean powered = state.getValue(PackAPunchBlock.POWERED);
                boolean hasIngot = hasIngot(player);
                int price = be.getPrice();
                int beState = be.getState();
                Component text;

                if (!powered) {
                    text = Component.translatable("message.zombierool.power_required").withStyle(ChatFormatting.RED);
                } else if (beState == 2) {
                    text = Component.translatable("message.zombierool.packapunch.retrieve", actionKey).withStyle(ChatFormatting.GREEN);
                } else if (beState == 1) {
                    text = Component.translatable("message.zombierool.packapunch.upgrading").withStyle(ChatFormatting.YELLOW);
                } else {
                    if (hasIngot) {
                        text = Component.translatable("message.zombierool.packapunch.upgrade_ingot", actionKey).withStyle(ChatFormatting.WHITE);
                    } else {
                        text = Component.translatable("message.zombierool.packapunch.upgrade", actionKey, price).withStyle(ChatFormatting.WHITE);
                    }
                }
                return new InteractionCandidate(pos, InteractionType.PACK_A_PUNCH, text);
            }
            return null;
        }

        if (block instanceof me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponBlock) {
            BlockEntity te = level.getBlockEntity(pos);
            if (te instanceof me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponBlockEntity be) {
                
                Direction facing = state.getValue(me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponBlock.FACING);
                Vec3 blockCenter = Vec3.atCenterOf(pos);
                Vec3 faceNormal = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
                Vec3 dirFromPlayer = blockCenter.subtract(player.getEyePosition()).normalize();
                
                if (dirFromPlayer.dot(faceNormal) > -0.2) return null; 

                int basePrice = be.getPrice();
                if (basePrice <= 0) return null;

                net.minecraft.world.item.ItemStack weaponOnWall = net.minecraft.world.item.ItemStack.EMPTY;
                var opt = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null).resolve();
                if (opt.isPresent()) {
                    weaponOnWall = opt.get().getStackInSlot(0);
                } else if (be.getItemToSell() != null) {
                    net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(be.getItemToSell());
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        weaponOnWall = new net.minecraft.world.item.ItemStack(item);
                    }
                }

                if (weaponOnWall.isEmpty()) return null;

                boolean isTacz = WeaponFacade.isTaczWeapon(weaponOnWall);
                WeaponSystem.Definition def = WeaponFacade.getDefinition(weaponOnWall);

                boolean isReloadable = isTacz || def != null || weaponOnWall.getItem() instanceof IReloadable;

                boolean playerHasBaseItem = false;
                boolean playerHasUpgradedItem = false;

                if (isReloadable) {
                    for (net.minecraft.world.item.ItemStack s : player.getInventory().items) {
                        if (isTacz && WeaponFacade.isTaczWeapon(s)) {
                            String wallId = weaponOnWall.getOrCreateTag().getString("GunId");
                            String invId = s.getOrCreateTag().getString("GunId");
                            if (wallId.equals(invId)) {
                                if (WeaponFacade.isPackAPunched(s)) playerHasUpgradedItem = true;
                                else playerHasBaseItem = true;
                            }
                        } else if (!isTacz && def != null && WeaponFacade.isWeapon(s)) {
                            WeaponSystem.Definition d = WeaponFacade.getDefinition(s);
                            if (d != null && d.id.replace("zombierool:", "").equals(def.id.replace("zombierool:", ""))) {
                                if (WeaponFacade.isPackAPunched(s)) playerHasUpgradedItem = true;
                                else playerHasBaseItem = true;
                            }
                        } else if (!isTacz && def == null && s.getItem() == weaponOnWall.getItem()) {
                            if (WeaponFacade.isPackAPunched(s)) playerHasUpgradedItem = true;
                            else playerHasBaseItem = true;
                        }
                    }
                }

                String wpnName;
                if (def != null) {
                    if (def.name != null && (def.name.startsWith("weapon.") || def.name.startsWith("item.") || def.name.contains("zombierool."))) {
                        wpnName = Component.translatable(def.name).getString();
                    } else {
                        wpnName = def.name != null ? def.name : "Unknown Weapon";
                    }
                } else if (isTacz) {
                    String gunId = weaponOnWall.getOrCreateTag().getString("GunId");
                    if (!gunId.isEmpty()) {
                        ResourceLocation loc = new ResourceLocation(gunId);
                        String translatableKey = String.format("gun.%s.%s.name", loc.getNamespace(), loc.getPath());
                        Component translated = Component.translatable(translatableKey);
                        wpnName = translated.getString();
                        if (wpnName.equals(translatableKey)) {
                            wpnName = loc.getPath().replace("_", " ").toUpperCase();
                        }
                    } else {
                        wpnName = weaponOnWall.getHoverName().getString();
                    }
                } else {
                    wpnName = weaponOnWall.getHoverName().getString();
                }

                Component text;
                if (isReloadable) {
                    if (playerHasUpgradedItem) {
                        int rechargePrice = (basePrice / 2) + 5000;
                        text = Component.translatable("zombierool.hud.buy_wall_weapon.reload_pap", actionKey, wpnName, rechargePrice).withStyle(ChatFormatting.WHITE);
                    } else if (playerHasBaseItem) {
                        int rechargePrice = Math.max(1, basePrice / 2);
                        text = Component.translatable("zombierool.hud.buy_wall_weapon.reload", actionKey, wpnName, rechargePrice).withStyle(ChatFormatting.WHITE);
                    } else {
                        text = Component.translatable("zombierool.hud.buy_wall_weapon.buy", actionKey, wpnName, basePrice).withStyle(ChatFormatting.WHITE);
                    }
                } else {
                    text = Component.translatable("zombierool.hud.buy_wall_weapon.buy", actionKey, wpnName, basePrice).withStyle(ChatFormatting.WHITE);
                }

                return new InteractionCandidate(pos, InteractionType.WALL_WEAPON, text);
            }
            return null;
        }

        if (block instanceof MeteoriteEasterEgg.MeteoriteBlock && state.getValue(MeteoriteEasterEgg.MeteoriteBlock.ACTIVE)) {
            return new InteractionCandidate(pos, InteractionType.METEORITE, null);
        }

        if (block instanceof me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock) {
            BlockEntity te = level.getBlockEntity(pos);
            if (te instanceof me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlockEntity be) {
                int prix = be.getPrix();
                if (prix > 0) { 
                    Component text = Component.translatable("gui.zombierool.overlay.purchase", actionKey, prix).withStyle(ChatFormatting.WHITE);
                    return new InteractionCandidate(pos, InteractionType.OBSTACLE, text);
                }
            }
            return null;
        }

        if (block instanceof DefenseDoorSystem.DefenseDoorBlock) {
            int stage = state.getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE); 
            boolean isPermOpen = state.getValue(DefenseDoorSystem.DefenseDoorBlock.PERMANENTLY_OPEN);
            if (!isPermOpen && stage < 5) {
                Component text = Component.translatable("gui.zombierool.overlay.repair", actionKey, (5 - stage)).withStyle(ChatFormatting.YELLOW);
                return new InteractionCandidate(pos, InteractionType.REPAIR_BARRICADE, text);
            }
            return null;
        }

        if (block instanceof DefenseWallSystem.DefenseWallBlock || block instanceof DefenseWallSystem.DefenseWallDummyBlock) {
            BlockPos mainPos = (block instanceof DefenseWallSystem.DefenseWallDummyBlock dummy) ? dummy.getMainPos(pos, state) : pos;
            BlockState mainState = level.getBlockState(mainPos);
            
            if (mainState.getBlock() instanceof DefenseWallSystem.DefenseWallBlock) {
                int stage = mainState.getValue(DefenseWallSystem.DefenseWallBlock.STAGE); 
                boolean isPermOpen = mainState.getValue(DefenseWallSystem.DefenseWallBlock.PERMANENTLY_OPEN);
                if (!isPermOpen && stage < 7) {
                    Component text = Component.translatable("gui.zombierool.overlay.repair", actionKey, (7 - stage)).withStyle(ChatFormatting.YELLOW);
                    return new InteractionCandidate(pos, InteractionType.REPAIR_BARRICADE, text);
                }
            }
            return null;
        }

        return null;
    }

    private static boolean hasIngot(LocalPlayer player) {
        return player.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof IngotSaleItem);
    }

    @SubscribeEvent
    public static void onRenderUnifiedHints(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isCreative() || player.isSpectator()) return;

        if (!activeHUDMessages.isEmpty()) {
            Font font = mc.font;
            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();
            int startY = (height / 2) + 15;

            for (Component msg : activeHUDMessages) {
                int x = (width - font.width(msg)) / 2;
                event.getGuiGraphics().drawString(font, msg, x, startY, 0xFFFFFF);
                startY += font.lineHeight + 2;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderReviveBar(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isCreative() || player.isSpectator()) return;

        Player nearbyDownPlayer = null;
        double minDistSq = 2.25;
        for (Player p : mc.level.players()) {
            if (p.getUUID().equals(player.getUUID())) continue;
            if (downPlayers.contains(p.getUUID()) && p.distanceToSqr(player) < minDistSq) {
                nearbyDownPlayer = p;
                break;
            }
        }

        String actionKey = KeyBindings.REPAIR_AND_PURCHASE_KEY.getTranslatedKeyMessage().getString().toUpperCase();
        
        if (revivingTargetUUID == null && nearbyDownPlayer != null) {
            Component text = Component.translatable("gui.zombierool.overlay.revive", actionKey, nearbyDownPlayer.getName().getString()).withStyle(ChatFormatting.YELLOW);
            Font font = mc.font;
            int width = event.getWindow().getGuiScaledWidth();
            int height = event.getWindow().getGuiScaledHeight();
            int x = (width - font.width(text)) / 2;
            int y = (height / 2) + 45;
            event.getGuiGraphics().drawString(font, text, x, y, 0xFFFFFF);
        }

        if (revivingTargetUUID != null && reviveBarStartTime > 0 && clientReviveDuration > 0) {
            long currentTime = mc.level.getGameTime();
            float progress = (float) (currentTime - reviveBarStartTime) / clientReviveDuration;
            
            if (progress >= 1.0f) {
                revivingTargetUUID = null;
                reviveBarStartTime = 0;
                return;
            }
            if (progress < 0) progress = 0;

            String targetName = Component.translatable("gui.zombierool.player").getString();
            Player targetPlayer = mc.level.getPlayerByUUID(revivingTargetUUID);
            if (targetPlayer != null) {
                targetName = targetPlayer.getName().getString();
            }
            
            Component text = Component.translatable("gui.zombierool.overlay.reviving_progress", targetName, (int)(progress * 100));

            Font font = mc.font;
            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();

            int barWidth = 150;
            int barHeight = 10;
            int barX = (width - barWidth) / 2;
            int barY = (height / 2) + 55;

            event.getGuiGraphics().fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000);
            int filledWidth = (int) (barWidth * progress);
            event.getGuiGraphics().fill(barX + 1, barY + 1, barX + filledWidth - 1, barY + barHeight - 1, 0xFF00FF00);

            int textX = (width - font.width(text)) / 2;
            int textY = barY - font.lineHeight - 2;
            event.getGuiGraphics().drawString(font, text, textX, textY, 0xFFFFFF);
        }
    }
}