package me.cryo.zombierool.handlers;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.block.DerWunderfizzBlock;
import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;
import me.cryo.zombierool.client.DrinkPerkAnimationHandler;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.PunchPackBlock;
import me.cryo.zombierool.block.PerksLowerBlock;
import me.cryo.zombierool.block.entity.PerksLowerBlockEntity;
import me.cryo.zombierool.block.MysteryBoxBlock;
import me.cryo.zombierool.block.EmptymysteryboxBlock;
import me.cryo.zombierool.client.gui.GuiOverlay;
import me.cryo.zombierool.init.KeyBindings;
import me.cryo.zombierool.item.IngotSaleItem;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.C2SReviveAttemptPacket;
import me.cryo.zombierool.block.AmmoCrateBlock;
import me.cryo.zombierool.network.C2SRequestAmmoCrateInfoPacket;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.PerksManager;
import me.cryo.zombierool.player.PlayerDownManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.world.entity.player.Player;
import me.cryo.zombierool.network.C2SUnifiedInteractPacket;
import me.cryo.zombierool.network.InteractionType;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class KeyInputHandler {

	public static boolean isLocalPlayerDown = false;
	public static final Set<UUID> downPlayers = new HashSet<>();
	public static long reviveBarStartTime = 0;
	public static UUID revivingTargetUUID = null;

	private static int clientAmmoCratePrice = 2500;
	private static boolean clientCanPurchaseAmmo = true;
	private static int ammoCrateInfoRequestCooldown = 0;
	private static String clientAmmoCrateHudMessage = null;

	private static boolean keyWasDown = false;
	private static int updateTick = 0;
	private static final int UPDATE_INTERVAL = 5;
	private static final List<String> activeHUDMessages = new ArrayList<>();
	private static BlockPos clientActiveWunderfizzPosition = null;

	private static long clientReviveDuration = PlayerDownManager.BASE_REVIVE_DURATION_TICKS;

	public static void updateAmmoCrateInfo(int price, boolean canPurchase, String hudMessage) {
	    clientAmmoCratePrice = price;
	    clientCanPurchaseAmmo = canPurchase;
	    clientAmmoCrateHudMessage = hudMessage;
	}

	private static boolean isEnglishClient() {
	    return Minecraft.getInstance().options.languageCode.startsWith("en");
	}
	private static String getTranslatedMessage(String frenchMessage, String englishMessage) {
	    return isEnglishClient() ? englishMessage : frenchMessage;
	}

	public static void setClientReviveDuration(long duration) {
	    clientReviveDuration = duration;
	}

	public static void setActiveWunderfizzPosition(BlockPos pos) {
	    clientActiveWunderfizzPosition = pos;
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
	    if (event.phase != TickEvent.Phase.END) return;
	    Minecraft mc = Minecraft.getInstance();
	    if (mc.player == null || mc.level == null) return;

	    LocalPlayer player = mc.player;
	    BlockPos playerPos = player.blockPosition();

	    if (isLocalPlayerDown) {
	        if (!keyWasDown) {
	            player.displayClientMessage(Component.literal(getTranslatedMessage(
	                "§cVous êtes down et ne pouvez rien faire !",
	                "§cYou are down and cannot do anything!"
	            )), true);
	        }
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
	            player.displayClientMessage(Component.literal(getTranslatedMessage(
	                "§cRéanimation annulée.",
	                "§cRevive cancelled."
	            )).withStyle(ChatFormatting.RED), true);
	            NetworkHandler.INSTANCE.sendToServer(new C2SReviveAttemptPacket(revivingTargetUUID, true));
	        }
	    }

	    if (isFKeyDown && !keyWasDown && revivingTargetUUID == null && detectedReviveTarget == null) {
	        handleSinglePressActions(player, playerPos, mc, true);
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

	    for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-2, -2, -2), playerPos.offset(2, 2, 2))) {
	        double distanceSq = player.position().distanceToSqr(pos.getCenter());
	        if (distanceSq > 4.0) continue;

	        BlockState state = mc.level.getBlockState(pos);
	        Block block = state.getBlock();

	        if (block instanceof PerksLowerBlock && distanceSq <= 2.25) {
	            BlockEntity be = mc.level.getBlockEntity(pos);
	            if (be instanceof PerksLowerBlockEntity perksBE) {
	                boolean powered = state.getValue(PerksLowerBlock.POWERED);
	                String perkId = perksBE.getSavedPerkId();
	                PerksManager.Perk perk = PerksManager.ALL_PERKS.get(perkId);

	                if (perk != null) {
	                    if (!powered) {
	                        activeHUDMessages.add(getTranslatedMessage("§cVous devez d'abord activer le courant !", "§cYou must activate the power first!"));
	                    } else if (PerksManager.getPerkCount(player) >= PerksManager.MAX_PERKS_LIMIT && !player.hasEffect(perk.getAssociatedEffect())) {
	                        activeHUDMessages.add(getTranslatedMessage("§cVous possédez déjà " + PerksManager.MAX_PERKS_LIMIT + " atouts.", "§cYou already have " + PerksManager.MAX_PERKS_LIMIT + " perks."));
	                    } else if (perk.getAssociatedEffect() != null && player.hasEffect(perk.getAssociatedEffect())) {
	                        activeHUDMessages.add(getTranslatedMessage("§aVous avez déjà " + perk.getName() + " !", "§aYou already have " + perk.getName() + "!"));
	                    } else {
	                        int price = perksBE.getSavedPrice();
	                        boolean hasIngot = hasIngot(player);
	                        if (hasIngot) {
	                            activeHUDMessages.add(getTranslatedMessage("Appuyer sur F pour acheter " + perk.getName() + " (1 lingot)", "Press F to purchase " + perk.getName() + " (1 ingot)"));
	                        } else {
	                            activeHUDMessages.add(getTranslatedMessage("Appuyer sur F pour acheter " + perk.getName() + " pour " + price + " points", "Press F to purchase " + perk.getName() + " for " + price + " points"));
	                        }
	                    }
	                    return;
	                }
	            }
	        }

	        if ((block instanceof MysteryBoxBlock && !state.getValue(MysteryBoxBlock.PART)) ||
	            (block instanceof EmptymysteryboxBlock && !state.getValue(EmptymysteryboxBlock.PART))) {
	            if (distanceSq <= 2.25) {
	                if (block instanceof EmptymysteryboxBlock) {
	                    activeHUDMessages.add(getTranslatedMessage("La Mystery Box n'est pas ici !", "The Mystery Box is not here!"));
	                } else {
	                    activeHUDMessages.add(getTranslatedMessage("Appuyer sur F pour acheter une arme aléatoire (950 points)", "Press F to purchase a random weapon (950 points)"));
	                }
	                return;
	            }
	        }

	        if (block instanceof DerWunderfizzBlock && distanceSq <= 2.25) {
	            DerWunderfizzBlock.WunderfizzPerk perkType = state.getValue(DerWunderfizzBlock.PERK_TYPE);
	            boolean isActivePosition = clientActiveWunderfizzPosition != null && clientActiveWunderfizzPosition.equals(pos);

	            if (perkType == DerWunderfizzBlock.WunderfizzPerk.IDLE) {
	                if (!isActivePosition) {
	                    activeHUDMessages.add(getTranslatedMessage("§cLa Wunderfizz est ailleurs !", "§cThe Wunderfizz is elsewhere!"));
	                } else if (!state.getValue(DerWunderfizzBlock.POWERED)) {
	                    activeHUDMessages.add(getTranslatedMessage("§cVous devez d'abord activer le courant !", "§cYou must activate the power first!"));
	                } else {
	                    boolean hasIngot = hasIngot(player);
	                    if (hasIngot) {
	                        activeHUDMessages.add(getTranslatedMessage("Appuyer sur F pour acheter une boisson aléatoire (1 lingot)", "Press F to purchase a random drink (1 ingot)"));
	                    } else {
	                        activeHUDMessages.add(getTranslatedMessage("Appuyer sur F pour acheter une boisson aléatoire pour 1500 points", "Press F to purchase a random drink for 1500 points"));
	                    }
	                }
	            } else {
	                BlockEntity be = mc.level.getBlockEntity(pos);
	                if (be instanceof DerWunderfizzBlockEntity wunderfizz) {
	                    if (wunderfizz.getSelectedPerkId() != null) {
	                        activeHUDMessages.add(getTranslatedMessage("§aAppuyer sur F pour récupérer votre boisson", "§aPress F to collect your drink"));
	                    } else {
	                        activeHUDMessages.add(getTranslatedMessage("§eLa Wunderfizz prépare votre boisson...", "§eThe Wunderfizz is preparing your drink..."));
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
	                if (clientAmmoCrateHudMessage != null) {
	                    activeHUDMessages.add(clientAmmoCrateHudMessage);
	                } else {
	                    activeHUDMessages.add(getTranslatedMessage("Appuyer sur F pour faire le plein de munitions pour " + clientAmmoCratePrice + " points", "Press F to refill ammo for " + clientAmmoCratePrice + " points"));
	                }
	                return;
	            }
	        }
	    }
	}

	private static boolean hasIngot(LocalPlayer player) {
	    return player.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof IngotSaleItem);
	}

	private static void handleSinglePressActions(LocalPlayer player, BlockPos playerPos, Minecraft mc, boolean isJustPressed) {
	    if (isLocalPlayerDown) return;
	    if (!isJustPressed) return;

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
	            if (!state.getValue(MysteryBoxBlock.PART)) {
	                double distanceSq = player.position().distanceToSqr(pos.getCenter());
	                if (distanceSq <= 2.25) {
	                    NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(pos, InteractionType.MYSTERY_BOX));
	                    return;
	                }
	            }
	        }
	    }

	    HitResult hitResult = mc.hitResult;
	    BlockHitResult bhr = (hitResult instanceof BlockHitResult b) ? b : null;
	    if (bhr != null && mc.level.getBlockState(bhr.getBlockPos()).getBlock() instanceof PunchPackBlock) {
	        BlockPos pos = bhr.getBlockPos();
	        NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(pos, InteractionType.PACK_A_PUNCH));
	        return;
	    }

	    for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-2, -2, -2), playerPos.offset(2, 2, 2))) {
	        BlockState state = mc.level.getBlockState(pos);
	        if (!(state.getBlock() instanceof AmmoCrateBlock)) continue;
	        double distanceSq = player.position().distanceToSqr(pos.getCenter());
	        if (distanceSq > 4.0) continue;
	        
	        HitResult lookResult = mc.hitResult;
	        if (lookResult instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)) {
	            NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(pos, InteractionType.AMMO_CRATE));
	            return;
	        }
	    }

	    for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-2, -1, -2), playerPos.offset(2, 2, 2))) {
	        BlockState state = mc.level.getBlockState(pos);
	        if (!(state.getBlock() instanceof PerksLowerBlock)) continue;
	        
	        double distanceSq = player.position().distanceToSqr(pos.getCenter());
	        if (distanceSq > 2.25) continue;

	        NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(pos, InteractionType.PERK));
	        return;
	    }

	    for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-2, -1, -2), playerPos.offset(2, 2, 2))) {
	        BlockState state = mc.level.getBlockState(pos);
	        if (!(state.getBlock() instanceof DerWunderfizzBlock)) continue;
	        
	        double distanceSq = player.position().distanceToSqr(pos.getCenter());
	        if (distanceSq > 2.25) continue;

	        DerWunderfizzBlock.WunderfizzPerk perkType = state.getValue(DerWunderfizzBlock.PERK_TYPE);
	        if (perkType == DerWunderfizzBlock.WunderfizzPerk.IDLE) {
	            NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(pos, InteractionType.WUNDERFIZZ_BUY));
	            return;
	        } else {
	            BlockEntity be = mc.level.getBlockEntity(pos);
	            if (be instanceof DerWunderfizzBlockEntity wunderfizz) {
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

	    renderPackAPunchHint(event, mc, player);

	    if (!activeHUDMessages.isEmpty()) {
	        Font font = mc.font;
	        int width = mc.getWindow().getGuiScaledWidth();
	        int height = mc.getWindow().getGuiScaledHeight();
	        int startY = (height / 2) + 15;

	        for (String msg : activeHUDMessages) {
	            int x = (width - font.width(msg)) / 2;
	            event.getGuiGraphics().drawString(font, msg, x, startY, 0xFFFFFF);
	            startY += font.lineHeight + 2;
	        }
	    }
	}

	private static void renderPackAPunchHint(RenderGuiOverlayEvent.Post event, Minecraft mc, LocalPlayer player) {
	    HitResult hit = mc.hitResult;
	    if (!(hit instanceof BlockHitResult bhr)) return;

	    BlockPos pos = bhr.getBlockPos();
	    Block block = mc.level.getBlockState(pos).getBlock();
	    if (!(block instanceof PunchPackBlock)) return;

	    double dx = Math.abs(player.getX() - (pos.getX() + 0.5));
	    double dz = Math.abs(player.getZ() - (pos.getZ() + 0.5));
	    if (dx > 1.5 || dz > 1.5) return;

	    boolean powered = mc.level.getBlockState(pos).getValue(PunchPackBlock.POWERED);
	    boolean hasIngot = hasIngot(player);

	    String text;
	    if (!powered) {
	        text = getTranslatedMessage(
	            "§cVous devez d'abord activer le courant pour améliorer votre arme !",
	            "§cYou must activate the power first to upgrade your weapon!"
	        );
	    } else {
	        if (hasIngot) {
	            text = getTranslatedMessage(
	                "Appuyer sur F pour améliorer votre arme (1 lingot)",
	                "Press F to upgrade your weapon (1 ingot)"
	            );
	        } else {
	            text = getTranslatedMessage(
	                "Appuyer sur F pour améliorer votre arme pour le prix de 5000 points",
	                "Press F to upgrade your weapon for 5000 points"
	            );
	        }
	    }

	    Font font = mc.font;
	    int width = mc.getWindow().getGuiScaledWidth();
	    int height = mc.getWindow().getGuiScaledHeight();
	    int y = (height / 2) + 30;
	    int x = (width - font.width(text)) / 2;
	    
	    event.getGuiGraphics().drawString(font, text, x, y, 0xFFFFFF);
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

	    if (revivingTargetUUID == null && nearbyDownPlayer != null) {
	        String text = getTranslatedMessage(
	            "§eMaintenez F pour réanimer " + nearbyDownPlayer.getName().getString(),
	            "§eHold F to revive " + nearbyDownPlayer.getName().getString()
	        );
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
	        if (progress > 1.0f) progress = 1.0f;

	        String targetName = getTranslatedMessage("Joueur", "Player");
	        Player targetPlayer = mc.level.getPlayerByUUID(revivingTargetUUID);
	        if (targetPlayer != null) {
	            targetName = targetPlayer.getName().getString();
	        }

	        String text = getTranslatedMessage(
	            "Réanimation de " + targetName + ": " + (int)(progress * 100) + "%",
	            "Reviving " + targetName + ": " + (int)(progress * 100) + "%"
	        );

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