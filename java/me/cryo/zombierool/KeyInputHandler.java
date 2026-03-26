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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.system.MeteoriteEasterEgg;
import me.cryo.zombierool.PerksManager;
import me.cryo.zombierool.item.IngotSaleItem;
import me.cryo.zombierool.client.ClientPlayerDownSoundManager;
import me.cryo.zombierool.client.ClientInteractableManager;
import me.cryo.zombierool.client.gui.GuiOverlay;
import me.cryo.zombierool.core.system.WeaponFacade;

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
    
    private static int updateTick = 0;
    private static final int UPDATE_INTERVAL = 5;
    private static final List<Component> activeHUDMessages = new ArrayList<>();
    
    public static BlockPos clientActiveWunderfizzPosition = null;
    private static long clientReviveDuration = 120; 

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
        BlockPos playerPos = player.blockPosition();

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

        boolean isFKeyDown = KeyBindings.REPAIR_AND_PURCHASE_KEY.isDown();
        
        Player detectedReviveTarget = null;
        double minDistSq = 2.25;
        for (Player p : mc.level.players()) {
            if (p.getUUID().equals(player.getUUID())) continue;
            if (downPlayers.contains(p.getUUID()) && p.distanceToSqr(player) < minDistSq) {
                detectedReviveTarget = p;
                break;
            }
        }

        if (isFKeyDown && detectedReviveTarget != null) {
            NetworkHandler.INSTANCE.sendToServer(new C2SReviveAttemptPacket(detectedReviveTarget.getUUID(), false));
        } else if (keyWasDown && revivingTargetUUID != null) {
            boolean shouldCancelClientSide = !isFKeyDown || detectedReviveTarget == null || !detectedReviveTarget.getUUID().equals(revivingTargetUUID);
            if (shouldCancelClientSide) {
                player.displayClientMessage(Component.translatable("message.zombierool.down.cancel_invalid").withStyle(ChatFormatting.RED), true);
                NetworkHandler.INSTANCE.sendToServer(new C2SReviveAttemptPacket(revivingTargetUUID, true));
            }
        }

        if (isFKeyDown && !keyWasDown) {
            NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(playerPos, InteractionType.ACTION_KEY));
            
            if (revivingTargetUUID == null && detectedReviveTarget == null) {
                handleSinglePressActions(player, playerPos, mc, true);
            }
        }

        keyWasDown = isFKeyDown;

        updateTick++;
        if (updateTick >= UPDATE_INTERVAL) {
            updateTick = 0;
            scanForInteractions(player, playerPos, mc);
        }

        if (ammoCrateInfoRequestCooldown > 0) {
            ammoCrateInfoRequestCooldown--;
        }
    }

    private static void scanForInteractions(LocalPlayer player, BlockPos playerPos, Minecraft mc) {
        activeHUDMessages.clear();
        if (player.isCreative()) return;
        String actionKey = KeyBindings.REPAIR_AND_PURCHASE_KEY.getTranslatedKeyMessage().getString().toUpperCase();

        for (me.cryo.zombierool.core.manager.InteractableManager.Interactable inter : ClientInteractableManager.getInteractables().values()) {
            if (player.distanceToSqr(inter.pos) <= inter.radius * inter.radius) {
                String translatedText = Component.translatable(inter.langKey).getString();
                String formattedMessage = translatedText.replace("%key%", actionKey);
                activeHUDMessages.add(Component.literal(formattedMessage).withStyle(ChatFormatting.YELLOW));
            }
        }

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-2, -2, -2), playerPos.offset(2, 2, 2))) {
            double distanceSq = player.position().distanceToSqr(pos.getCenter());
            if (distanceSq > 4.0) continue;

            BlockState state = mc.level.getBlockState(pos);
            Block block = state.getBlock();

            BlockPos mainPerkPos = pos;
            if (block instanceof PerksAColaDummyBlock) {
                mainPerkPos = state.getValue(PerksAColaDummyBlock.PART) == DummyPart.LOWER ? pos.above() : pos.below();
                state = mc.level.getBlockState(mainPerkPos);
                block = state.getBlock();
            }

            if (block instanceof PerksAColaBlock) {
                BlockEntity be = mc.level.getBlockEntity(mainPerkPos);
                if (be instanceof PerksAColaBlockEntity perksBE) {
                    boolean powered = perksBE.isPowered();
                    String perkId = perksBE.getSavedPerkId();
                    PerksManager.Perk perk = PerksManager.ALL_PERKS.get(perkId);
                    
                    if (perk != null) {
                        net.minecraft.world.effect.MobEffect effect = perk.getAssociatedEffect();
                        if (!powered) {
                            activeHUDMessages.add(Component.translatable("message.zombierool.power_required").withStyle(ChatFormatting.RED));
                        } else if (PerksManager.getPerkCount(player) >= PerksManager.MAX_PERKS_LIMIT && (effect == null || !player.hasEffect(effect))) {
                            activeHUDMessages.add(Component.translatable("message.zombierool.max_perks", PerksManager.MAX_PERKS_LIMIT).withStyle(ChatFormatting.RED));
                        } else if (effect != null && player.hasEffect(effect)) {
                            activeHUDMessages.add(Component.translatable("message.zombierool.already_have_perk", perk.getName()).withStyle(ChatFormatting.GREEN));
                        } else {
                            int price = perksBE.getSavedPrice();
                            boolean hasIngot = hasIngot(player);
                            if (hasIngot) {
                                activeHUDMessages.add(Component.translatable("message.zombierool.buy_perk_ingot", actionKey, perk.getName()));
                            } else {
                                activeHUDMessages.add(Component.translatable("message.zombierool.buy_perk", actionKey, perk.getName(), price));
                            }
                        }
                        return;
                    }
                }
            }

            if (block instanceof MysteryBoxBlock && !state.getValue(MysteryBoxBlock.PART) && distanceSq <= 2.25) {
                if (!state.getValue(MysteryBoxBlock.ACTIVE)) {
                    activeHUDMessages.add(Component.translatable("message.zombierool.mystery_box.not_here"));
                } else {
                    BlockEntity be = mc.level.getBlockEntity(pos);
                    if (be instanceof MysteryBoxBlockEntity mysteryBox) {
                        if (mysteryBox.getBoxState() == 0) {
                            activeHUDMessages.add(Component.translatable("message.zombierool.mystery_box.buy", actionKey));
                        } else if (mysteryBox.getBoxState() == 1) {
                            activeHUDMessages.add(Component.translatable("message.zombierool.mystery_box.searching").withStyle(ChatFormatting.YELLOW));
                        } else if (mysteryBox.getBoxState() == 2 && !mysteryBox.isTeddy()) {
                            activeHUDMessages.add(Component.translatable("message.zombierool.mystery_box.take", actionKey).withStyle(ChatFormatting.GREEN));
                        }
                    }
                }
                return;
            }
            
            if (block instanceof DerWunderfizzBlock && distanceSq <= 2.25) {
                BlockEntity be = mc.level.getBlockEntity(pos);
                if (be instanceof DerWunderfizzBlockEntity wunderfizz) {
                    DerWunderfizzBlockEntity.WunderfizzState stateEnum = wunderfizz.getState();
                    boolean isActivePosition = clientActiveWunderfizzPosition != null && clientActiveWunderfizzPosition.equals(pos);
                    
                    if (stateEnum == DerWunderfizzBlockEntity.WunderfizzState.IDLE) {
                        if (!isActivePosition) {
                            activeHUDMessages.add(Component.translatable("message.zombierool.wunderfizz.not_here").withStyle(ChatFormatting.RED));
                        } else if (!state.getValue(DerWunderfizzBlock.POWERED)) {
                            activeHUDMessages.add(Component.translatable("message.zombierool.power_required").withStyle(ChatFormatting.RED));
                        } else {
                            boolean hasIngot = hasIngot(player);
                            if (hasIngot) {
                                activeHUDMessages.add(Component.translatable("message.zombierool.wunderfizz.buy_ingot", actionKey));
                            } else {
                                activeHUDMessages.add(Component.translatable("message.zombierool.wunderfizz.buy", actionKey));
                            }
                        }
                    } else {
                        if (wunderfizz.getSelectedPerkId() != null) {
                            activeHUDMessages.add(Component.translatable("message.zombierool.wunderfizz.collect", actionKey).withStyle(ChatFormatting.GREEN));
                        } else {
                            activeHUDMessages.add(Component.translatable("message.zombierool.wunderfizz.preparing").withStyle(ChatFormatting.YELLOW));
                        }
                    }
                }
                return;
            }

            if (block instanceof AmmoCrateBlock) {
                HitResult lookResult = mc.hitResult;
                if (lookResult instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)) {
                    if (ammoCrateInfoRequestCooldown <= 0) {
                        NetworkHandler.INSTANCE.sendToServer(new C2SRequestAmmoCrateInfoPacket());
                        ammoCrateInfoRequestCooldown = 20;
                    }
                    if (clientCanPurchaseAmmo) {
                        activeHUDMessages.add(Component.translatable("message.zombierool.ammo_crate.hud_refill", actionKey, clientAmmoCratePrice));
                    } else {
                        activeHUDMessages.add(Component.translatable("message.zombierool.ammo_crate.hud_already_purchased").withStyle(ChatFormatting.RED));
                    }
                    return;
                }
            }

            if (block instanceof BlindBuyCabinetBlock && distanceSq <= 4.0) {
                BlockEntity be = mc.level.getBlockEntity(pos);
                if (be instanceof BlindBuyCabinetBlockEntity cabinet) {
                    boolean isOpen = state.getValue(BlindBuyCabinetBlock.OPEN);
                    int price = cabinet.getPrice();
                    if (!isOpen) {
                        activeHUDMessages.add(Component.translatable("message.zombierool.blind_buy.buy", actionKey, price));
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
                            activeHUDMessages.add(Component.translatable("message.zombierool.blind_buy.refill", actionKey, wpnName, ammoPrice));
                        } else {
                            activeHUDMessages.add(Component.translatable("message.zombierool.blind_buy.buy_revealed", actionKey, wpnName, price));
                        }
                    }
                }
                return;
            }
        }
    }

    private static boolean hasIngot(LocalPlayer player) {
        return player.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof IngotSaleItem);
    }

    private static void handleSinglePressActions(LocalPlayer player, BlockPos playerPos, Minecraft mc, boolean isJustPressed) {
        if (isLocalPlayerDown) return;
        if (!isJustPressed) return;

        HitResult hitResult = mc.hitResult;
        if (hitResult instanceof BlockHitResult bhr) {
            BlockPos hitPos = bhr.getBlockPos();
            BlockState hitState = mc.level.getBlockState(hitPos);
            
            if (hitState.getBlock() instanceof MeteoriteEasterEgg.MeteoriteBlock && hitState.getValue(MeteoriteEasterEgg.MeteoriteBlock.ACTIVE)) {
                if (player.getEyePosition().distanceToSqr(hitResult.getLocation()) <= 16.0) { 
                    NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(hitPos, InteractionType.METEORITE));
                    return; 
                }
            }
        }

        BlockPos repairPos = DefenseDoorSystem.DefenseDoorBlock.getDoorInRepairZone(mc.level, playerPos);
        if (repairPos != null) {
            NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(repairPos, InteractionType.REPAIR_BARRICADE));
            return;
        }

        BlockPos purchasePos = GuiOverlay.findNearbyObstacleDoor(mc.level, playerPos);
        if (purchasePos != null) {
            NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(purchasePos, InteractionType.OBSTACLE));
            return;
        }

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-2, -1, -2), playerPos.offset(2, 2, 2))) {
            BlockState state = mc.level.getBlockState(pos);
            
            if (state.getBlock() instanceof MysteryBoxBlock) {
                if (!state.getValue(MysteryBoxBlock.PART) && state.getValue(MysteryBoxBlock.ACTIVE)) {
                    double distanceSq = player.position().distanceToSqr(pos.getCenter());
                    if (distanceSq <= 2.25) {
                        NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(pos, InteractionType.MYSTERY_BOX));
                        return;
                    }
                }
            }
            if (state.getBlock() instanceof BlindBuyCabinetBlock) {
                double distanceSq = player.position().distanceToSqr(pos.getCenter());
                if (distanceSq <= 4.0) {
                    NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(pos, InteractionType.BLIND_BUY_CABINET));
                    return;
                }
            }
        }

        BlockHitResult actionBhr = (hitResult instanceof BlockHitResult b) ? b : null;
        if (actionBhr != null && mc.level.getBlockState(actionBhr.getBlockPos()).getBlock() instanceof PackAPunchBlock) {
            BlockPos pos = actionBhr.getBlockPos();
            NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(pos, InteractionType.PACK_A_PUNCH));
            return;
        }

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-2, -2, -2), playerPos.offset(2, 2, 2))) {
            BlockState state = mc.level.getBlockState(pos);
            if (!(state.getBlock() instanceof AmmoCrateBlock)) continue;

            double distanceSq = player.position().distanceToSqr(pos.getCenter());
            if (distanceSq > 4.0) continue;

            if (actionBhr != null && actionBhr.getBlockPos().equals(pos)) {
                NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(pos, InteractionType.AMMO_CRATE));
                return;
            }
        }

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-2, -1, -2), playerPos.offset(2, 2, 2))) {
            BlockState state = mc.level.getBlockState(pos);
            Block block = state.getBlock();

            BlockPos mainPerkPos = pos;
            if (block instanceof PerksAColaDummyBlock) {
                mainPerkPos = state.getValue(PerksAColaDummyBlock.PART) == DummyPart.LOWER ? pos.above() : pos.below();
                state = mc.level.getBlockState(mainPerkPos);
                block = state.getBlock();
            }

            if (block instanceof PerksAColaBlock) {
                double distanceSq = player.position().distanceToSqr(pos.getCenter());
                if (distanceSq > 4.0) continue;

                NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(mainPerkPos, InteractionType.PERK));
                return;
            }
        }

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-2, -1, -2), playerPos.offset(2, 2, 2))) {
            BlockState state = mc.level.getBlockState(pos);
            if (!(state.getBlock() instanceof DerWunderfizzBlock)) continue;

            double distanceSq = player.position().distanceToSqr(pos.getCenter());
            if (distanceSq > 2.25) continue;

            BlockEntity be = mc.level.getBlockEntity(pos);
            if (be instanceof DerWunderfizzBlockEntity wunderfizz) {
                DerWunderfizzBlockEntity.WunderfizzState stateEnum = wunderfizz.getState();
                if (stateEnum == DerWunderfizzBlockEntity.WunderfizzState.IDLE) {
                    NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(pos, InteractionType.WUNDERFIZZ_BUY));
                    return;
                } else {
                    String perkId = wunderfizz.getSelectedPerkId();
                    if (perkId != null) {
                        NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(pos, InteractionType.WUNDERFIZZ_COLLECT));
                        return;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderUnifiedHints(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isCreative()) return;

        // Note: The redundant call to renderPackAPunchHint was removed because 
        // ClientHUDHandler.renderWallWeaponHint already renders it, 
        // avoiding rendering the text twice on screen.
        
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
        if (player == null || mc.level == null || player.isCreative()) return;

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