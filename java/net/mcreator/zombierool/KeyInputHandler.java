package net.mcreator.zombierool.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.mcreator.zombierool.client.DrinkPerkAnimationHandler;
import net.mcreator.zombierool.block.DefenseDoorBlock;
import net.mcreator.zombierool.block.PunchPackBlock;
import net.mcreator.zombierool.block.PerksLowerBlock;
import net.mcreator.zombierool.block.MysteryBoxBlock;
import net.mcreator.zombierool.block.EmptymysteryboxBlock;
import net.mcreator.zombierool.block.DerWunderfizzBlock;
import net.mcreator.zombierool.block.entity.PerksLowerBlockEntity;
import net.mcreator.zombierool.client.gui.GuiOverlay;
import net.mcreator.zombierool.init.KeyBindings;
import net.mcreator.zombierool.item.IngotSaleItem;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.C2SReviveAttemptPacket;
import net.mcreator.zombierool.network.RepairBarricadeMessage;
import net.mcreator.zombierool.network.PurchaseObstacleMessage;
import net.mcreator.zombierool.network.StartUpgradeMessage;
import net.mcreator.zombierool.network.PurchasePerkMessage;
import net.mcreator.zombierool.network.PurchaseMysteryBoxMessage;
import net.mcreator.zombierool.network.PurchaseWunderfizzDrinkMessage;

import net.mcreator.zombierool.block.AmmoCrateBlock;
import net.mcreator.zombierool.network.PurchaseAmmoCrateMessage;
import net.mcreator.zombierool.network.C2SRequestAmmoCrateInfoPacket;

import net.mcreator.zombierool.PointManager;
import net.mcreator.zombierool.PerksManager;
import net.mcreator.zombierool.MysteryBoxManager;
import net.mcreator.zombierool.WunderfizzManager;
import net.mcreator.zombierool.WorldConfig;
import net.mcreator.zombierool.player.PlayerDownManager;

import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class KeyInputHandler {

	private static int clientAmmoCratePrice = 2500; // Prix par défaut
	private static boolean clientCanPurchaseAmmo = true;
	private static int ammoCrateInfoRequestCooldown = 0;
	private static String clientAmmoCrateHudMessage = null;

    private static boolean keyWasDown = false;
    public static boolean isLocalPlayerDown = false;

    public static final Set<UUID> downPlayers = new HashSet<>();

    private static int perkHintUpdateTick = 0;
    private static final int PERK_HINT_UPDATE_INTERVAL = 10;
    private static String cachedPerkMessage = null;
    private static BlockPos cachedPerkBlockPos = null;

    private static int mysteryBoxHintUpdateTick = 0;
    private static final int MYSTERY_BOX_HINT_UPDATE_INTERVAL = 10;
    private static String cachedMysteryBoxMessage = null;

    private static int wunderfizzHintUpdateTick = 0;
    private static final int WUNDERFIZZ_HINT_UPDATE_INTERVAL = 10;
    private static String cachedWunderfizzMessage = null;
    private static BlockPos cachedWunderfizzBlockPos = null; 
    private static BlockPos clientActiveWunderfizzLocation = null; // Position active de la Wunderfizz reçue du serveur

    private static int ammoCrateHintUpdateTick = 0;
	private static final int AMMO_CRATE_HINT_UPDATE_INTERVAL = 10;
	private static String cachedAmmoCrateMessage = null;
	private static BlockPos cachedAmmoCrateBlockPos = null;

    public static long reviveBarStartTime = 0;
    public static UUID revivingTargetUUID = null;
    private static long clientReviveDuration = PlayerDownManager.BASE_REVIVE_DURATION_TICKS;

    public static void updateAmmoCrateInfo(int price, boolean canPurchase, String hudMessage) {
	    clientAmmoCratePrice = price;
	    clientCanPurchaseAmmo = canPurchase;
	    clientAmmoCrateHudMessage = hudMessage;
	}

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient() {
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    // Helper method for dynamic translation
    private static String getTranslatedMessage(String frenchMessage, String englishMessage) {
        return isEnglishClient() ? englishMessage : frenchMessage;
    }

    public static void setClientReviveDuration(long duration) {
        clientReviveDuration = duration;
    }

    public static void setClientActiveWunderfizzLocation(BlockPos pos) {
        clientActiveWunderfizzLocation = pos;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        LocalPlayer player = mc.player;
        BlockPos playerPos = player.blockPosition();

        if (isLocalPlayerDown) {
            player.displayClientMessage(Component.literal(getTranslatedMessage(
                "§cVous êtes down et ne pouvez rien faire !",
                "§cYou are down and cannot do anything!"
            )), true);
            keyWasDown = false;
            reviveBarStartTime = 0;
            revivingTargetUUID = null;
            return;
        }

        boolean isFKeyDown = KeyBindings.REPAIR_AND_PURCHASE_KEY.isDown();
        Player detectedReviveTarget = null;

        if (!isLocalPlayerDown) {
            double minDistSq = 2.25;
            for (Player p : mc.level.players()) {
                if (p.getUUID().equals(player.getUUID())) continue;
                if (downPlayers.contains(p.getUUID()) && p.distanceToSqr(player) < minDistSq) {
                    detectedReviveTarget = p;
                    break;
                }
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

        perkHintUpdateTick++;
        if (perkHintUpdateTick >= PERK_HINT_UPDATE_INTERVAL) {
            perkHintUpdateTick = 0;
            cachedPerkMessage = null;
            cachedPerkBlockPos = null;

            if (!player.isCreative()) {
                for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-2, -1, -2), playerPos.offset(2, 2, 2))) {
                    BlockState state = mc.level.getBlockState(pos);
                    if (!(state.getBlock() instanceof PerksLowerBlock)) continue;

                    double distanceSq = player.position().distanceToSqr(pos.getCenter());
                    if (distanceSq > 2.25) continue;

                    BlockEntity be = mc.level.getBlockEntity(pos);
                    if (!(be instanceof PerksLowerBlockEntity perksBE)) continue;

                    boolean powered = state.getValue(PerksLowerBlock.POWERED);
                    String perkId = perksBE.getSavedPerkId();
                    PerksManager.Perk perk = PerksManager.ALL_PERKS.get(perkId);

                    if (perk == null) {
                        continue;
                    }

                    if (!powered) {
                        cachedPerkMessage = getTranslatedMessage(
                            "§cVous devez d'abord activer le courant !",
                            "§cYou must activate the power first!"
                        );
                        cachedPerkBlockPos = pos;
                        break;
                    }

                    int currentPerkCount = PerksManager.getPerkCount(player);
                    if (currentPerkCount >= PerksManager.MAX_PERKS_LIMIT) {
                         cachedPerkMessage = getTranslatedMessage(
                             "§cVous possédez déjà " + PerksManager.MAX_PERKS_LIMIT + " atouts.",
                             "§cYou already have " + PerksManager.MAX_PERKS_LIMIT + " perks."
                         );
                         cachedPerkBlockPos = pos;
                         break;
                    }

                    if (perk.getAssociatedEffect() != null && player.hasEffect(perk.getAssociatedEffect())) {
                        cachedPerkMessage = getTranslatedMessage(
                            "§aVous avez déjà " + perk.getName() + " !",
                            "§aYou already have " + perk.getName() + "!"
                        );
                        cachedPerkBlockPos = pos;
                        continue;
                    }

                    int price = perksBE.getSavedPrice();
                    boolean hasIngot = player.getInventory().items.stream()
                        .anyMatch(s -> s.getItem() instanceof IngotSaleItem);

                    if (hasIngot) {
                        cachedPerkMessage = getTranslatedMessage(
                            "Appuyer sur F pour acheter " + perk.getName() + " (1 lingot)",
                            "Press F to purchase " + perk.getName() + " (1 ingot)"
                        );
                    } else {
                        cachedPerkMessage = getTranslatedMessage(
                            "Appuyer sur F pour acheter " + perk.getName() + " pour " + price + " points",
                            "Press F to purchase " + perk.getName() + " for " + price + " points"
                        );
                    }
                    cachedPerkBlockPos = pos;
                    break;
                }
            }
        }

        mysteryBoxHintUpdateTick++;
        if (mysteryBoxHintUpdateTick >= MYSTERY_BOX_HINT_UPDATE_INTERVAL) {
            mysteryBoxHintUpdateTick = 0;
            cachedMysteryBoxMessage = null;

            if (!player.isCreative()) {
                BlockPos playerFeetPos = player.blockPosition();

                for (BlockPos posIter : BlockPos.betweenClosed(playerFeetPos.offset(-2, -1, -2), playerFeetPos.offset(2, 2, 2))) {
                    BlockState state = mc.level.getBlockState(posIter);
                    Block block = state.getBlock();
                    
                    if (block instanceof MysteryBoxBlock) {
                        if (!state.getValue(MysteryBoxBlock.PART)) {
                            double distanceSq = player.position().distanceToSqr(posIter.getCenter());
                            if (distanceSq <= 2.25) {
                                cachedMysteryBoxMessage = getTranslatedMessage(
                                    "Appuyer sur F pour acheter une arme aléatoire (950 points)",
                                    "Press F to purchase a random weapon (950 points)"
                                );
                                break;
                            }
                        }
                    } else if (block instanceof EmptymysteryboxBlock) {
                        if (!state.getValue(EmptymysteryboxBlock.PART)) {
                            double distanceSq = player.position().distanceToSqr(posIter.getCenter());
                            if (distanceSq <= 2.25) {
                                cachedMysteryBoxMessage = getTranslatedMessage(
                                    "La Mystery Box n'est pas ici !",
                                    "The Mystery Box is not here!"
                                );
                                break;
                            }
                        }
                    }
                }
            }
        }

        wunderfizzHintUpdateTick++;
        if (wunderfizzHintUpdateTick >= WUNDERFIZZ_HINT_UPDATE_INTERVAL) {
            wunderfizzHintUpdateTick = 0;
            cachedWunderfizzMessage = null;
            cachedWunderfizzBlockPos = null; 

            if (!player.isCreative()) {
                BlockPos currentActiveWunderfizzLocation = KeyInputHandler.clientActiveWunderfizzLocation;

                for (BlockPos posIter : BlockPos.betweenClosed(playerPos.offset(-3, -2, -3), playerPos.offset(3, 3, 3))) {
                    BlockState state = mc.level.getBlockState(posIter);
                    if (!(state.getBlock() instanceof DerWunderfizzBlock)) continue;

                    BlockPos actualBasePos = posIter;
                    // Détecte si 'posIter' est le bloc supérieur d'une Wunderfizz de 2 blocs de haut
                    if (mc.level.getBlockState(posIter.below()).getBlock() instanceof DerWunderfizzBlock) {
                        actualBasePos = posIter.below(); // Si oui, utilise la position du bloc inférieur
                    }
                    
                    double distanceSq = player.position().distanceToSqr(actualBasePos.getCenter());
                    if (distanceSq > 2.25) continue;

                    boolean powered = state.getValue(DerWunderfizzBlock.POWERED);
                    
                    boolean isThisTheGloballyActiveWunderfizz = (currentActiveWunderfizzLocation != null &&
                                                                currentActiveWunderfizzLocation.getX() == actualBasePos.getX() &&
                                                                currentActiveWunderfizzLocation.getZ() == actualBasePos.getZ());
                    
                    if (!powered) {
                        cachedWunderfizzMessage = getTranslatedMessage(
                            "§cVous devez d'abord activer le courant !",
                            "§cYou must activate the power first!"
                        );
                    } else if (currentActiveWunderfizzLocation != null && !isThisTheGloballyActiveWunderfizz) {
                        cachedWunderfizzMessage = getTranslatedMessage(
                            "La machine Wunderfizz est ailleurs",
                            "The Wunderfizz machine is elsewhere"
                        );
                    } else {
                        int currentPerkCount = PerksManager.getPerkCount(player);
                        if (currentPerkCount >= PerksManager.MAX_PERKS_LIMIT) {
                            cachedWunderfizzMessage = getTranslatedMessage(
                                "§cVous possédez déjà " + PerksManager.MAX_PERKS_LIMIT + " atouts.",
                                "§cYou already have " + PerksManager.MAX_PERKS_LIMIT + " perks."
                            );
                        } else {
                            cachedWunderfizzMessage = getTranslatedMessage(
                                "Appuyer sur F pour acheter une boisson aléatoire pour 1500 points",
                                "Press F to purchase a random drink for 1500 points"
                            );
                        }
                    }
                    cachedWunderfizzBlockPos = actualBasePos; 
                    System.out.println("Client HUD: Processing Wunderfizz at " + actualBasePos + ". Client active location: " + clientActiveWunderfizzLocation + ". Message: " + cachedWunderfizzMessage); // DEBUG PRINT
                    break;
                }
            }
        }

        ammoCrateHintUpdateTick++;
		if (ammoCrateHintUpdateTick >= AMMO_CRATE_HINT_UPDATE_INTERVAL) {
		    ammoCrateHintUpdateTick = 0;
		    cachedAmmoCrateMessage = null;
		    cachedAmmoCrateBlockPos = null;
		
		    if (!player.isCreative()) {
		        for (BlockPos posIter : BlockPos.betweenClosed(playerPos.offset(-2, -2, -2), playerPos.offset(2, 2, 2))) {
		            BlockState state = mc.level.getBlockState(posIter);
		            if (!(state.getBlock() instanceof AmmoCrateBlock)) continue;
		
		            double distanceSq = player.position().distanceToSqr(posIter.getCenter());
		            if (distanceSq > 4.0) continue;
		
		            // Vérifie si le joueur regarde le bloc
		            HitResult lookResult = mc.hitResult;
		            if (lookResult instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(posIter)) {
		                // Demande les infos au serveur toutes les 20 ticks (1 seconde)
		                if (ammoCrateInfoRequestCooldown <= 0) {
		                    NetworkHandler.INSTANCE.sendToServer(new C2SRequestAmmoCrateInfoPacket());
		                    ammoCrateInfoRequestCooldown = 20;
		                }
		                
		                // Utilise le message reçu du serveur si disponible
		                if (clientAmmoCrateHudMessage != null) {
		                    cachedAmmoCrateMessage = clientAmmoCrateHudMessage;
		                } else {
		                    // Fallback au cas où le serveur n'a pas encore répondu
		                    cachedAmmoCrateMessage = getTranslatedMessage(
		                        "Appuyer sur F pour faire le plein de munitions pour " + clientAmmoCratePrice + " points",
		                        "Press F to refill ammo for " + clientAmmoCratePrice + " points"
		                    );
		                }
		                cachedAmmoCrateBlockPos = posIter;
		                break;
		            }
		        }
		    }
		}
		
		// Décremente le cooldown
		if (ammoCrateInfoRequestCooldown > 0) {
		    ammoCrateInfoRequestCooldown--;
		}
    }

    private static void handleSinglePressActions(LocalPlayer player, BlockPos playerPos, Minecraft mc, boolean isJustPressed) {
        if (isLocalPlayerDown) return;
        if (!isJustPressed) return;

        BlockPos repairPos = DefenseDoorBlock.getDoorInRepairZone(mc.level, playerPos);
        if (repairPos != null) {
        	NetworkHandler.INSTANCE.sendToServer(new RepairBarricadeMessage(repairPos, 0));
            return;
        }

        BlockPos purchasePos = GuiOverlay.findNearbyObstacleDoor(mc.level, playerPos);
        if (purchasePos != null) {
            NetworkHandler.INSTANCE.sendToServer(new PurchaseObstacleMessage(purchasePos));
            return;
        }

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-2, -1, -2), playerPos.offset(2, 2, 2))) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.getBlock() instanceof MysteryBoxBlock) {
                if (!state.getValue(MysteryBoxBlock.PART)) {
                    double distanceSq = player.position().distanceToSqr(pos.getCenter());
                    if (distanceSq <= 2.25) {
                        int cost = 950;
                        if (PointManager.getScore(player) < cost) {
                            player.displayClientMessage(Component.literal(getTranslatedMessage(
                                "§cPas assez de points ! (950 points requis)",
                                "§cNot enough points! (950 points required)"
                            )).withStyle(ChatFormatting.RED), true);
                            return;
                        }
                        NetworkHandler.INSTANCE.sendToServer(new PurchaseMysteryBoxMessage(pos));
                        return;
                    }
                }
            }
        }

        HitResult hitResult = mc.hitResult;
        BlockHitResult bhr = (hitResult instanceof BlockHitResult b) ? b : null;

        if (bhr != null && mc.level.getBlockState(bhr.getBlockPos()).getBlock() instanceof PunchPackBlock) {
            BlockPos pos = bhr.getBlockPos();
            boolean powered = mc.level.getBlockState(pos).getValue(PunchPackBlock.POWERED);

            if (!powered) {
                player.displayClientMessage(Component.literal(getTranslatedMessage(
                    "§cVous devez activer le courant pour améliorer votre arme !",
                    "§cYou must activate the power to upgrade your weapon!"
                )).withStyle(ChatFormatting.RED), true);
                return;
            }

            boolean hasIngot = player.getInventory().items.stream()
                .anyMatch(s -> s.getItem() instanceof IngotSaleItem);

            if (!hasIngot && PointManager.getScore(player) < 5000) {
                player.displayClientMessage(Component.literal(getTranslatedMessage(
                    "§cPas assez de points (5000 requis) ou de lingots !",
                    "§cNot enough points (5000 required) or ingots!"
                )).withStyle(ChatFormatting.RED), true);
                return;
            }

            NetworkHandler.INSTANCE.sendToServer(new StartUpgradeMessage(pos));
            return;
        }

        // --- Début de la logique de la Wunderfizz ---
        BlockPos foundWunderfizzBasePos = null; // Renommée pour plus de clarté
        for (BlockPos posIter : BlockPos.betweenClosed(playerPos.offset(-2, -1, -2), playerPos.offset(2, 2, 2))) {
            BlockState state = mc.level.getBlockState(posIter);
            if (!(state.getBlock() instanceof DerWunderfizzBlock)) continue;

            BlockPos actualBasePos = posIter;
            // Assurez-vous que actualBasePos est toujours le bloc inférieur de la Wunderfizz
            if (mc.level.getBlockState(posIter.below()).getBlock() instanceof DerWunderfizzBlock) {
                actualBasePos = posIter.below();
            }
            // Si le bloc au-dessus de posIter est aussi une Wunderfizz, cela signifie que posIter est le bloc supérieur,
            // et actualBasePos est déjà le bloc inférieur.
            // Si posIter est le bloc inférieur, alors le bloc en dessous n'est pas une Wunderfizz, et actualBasePos reste posIter.

            double distanceSq = player.position().distanceToSqr(actualBasePos.getCenter());
            if (distanceSq > 2.25) continue;

            // Une fois que nous trouvons une Wunderfizz à portée, définissez-la comme la base
            // et sortez de la boucle.
            foundWunderfizzBasePos = actualBasePos; // Utilise la position de base pour l'envoi du paquet
            break; 
        }

        if (foundWunderfizzBasePos != null) { // Vérifie que nous avons bien trouvé une Wunderfizz
            BlockState baseState = mc.level.getBlockState(foundWunderfizzBasePos);
            boolean powered = baseState.getValue(DerWunderfizzBlock.POWERED);
            
            BlockPos currentActiveWunderfizzLocation = KeyInputHandler.clientActiveWunderfizzLocation;
            boolean isThisTheGloballyActiveWunderfizz = (currentActiveWunderfizzLocation != null &&
                                                        currentActiveWunderfizzLocation.getX() == foundWunderfizzBasePos.getX() &&
                                                        currentActiveWunderfizzLocation.getZ() == foundWunderfizzBasePos.getZ());


            if (!powered) {
                player.displayClientMessage(Component.literal(getTranslatedMessage(
                    "§cVous devez d'abord activer le courant !",
                    "§cYou must activate the power first!"
                )).withStyle(ChatFormatting.RED), true);
                return;
            }
            if (currentActiveWunderfizzLocation != null && !isThisTheGloballyActiveWunderfizz) {
                 player.displayClientMessage(Component.literal(getTranslatedMessage(
                     "§cLa machine Wunderfizz est ailleurs !",
                     "§cThe Wunderfizz machine is elsewhere!"
                 )).withStyle(ChatFormatting.RED), true);
                 return;
            }


            int currentPerkCount = PerksManager.getPerkCount(player);
            if (currentPerkCount >= PerksManager.MAX_PERKS_LIMIT) {
                player.displayClientMessage(Component.literal(getTranslatedMessage(
                    "§cVous possédez déjà " + PerksManager.MAX_PERKS_LIMIT + " atouts.",
                    "§cYou already have " + PerksManager.MAX_PERKS_LIMIT + " perks."
                )).withStyle(ChatFormatting.RED), true);
                return;
            }

            int cost = 1500;
            if (PointManager.getScore(player) < cost) {
                player.displayClientMessage(Component.literal(getTranslatedMessage(
                    "§cPas assez de points ! (1500 points requis)",
                    "§cNot enough points! (1500 points required)"
                )).withStyle(ChatFormatting.RED), true);
                return;
            }
            
            // Correction pour la lambda: Assurez-vous que la variable passée est effectivement finale.
            final BlockPos finalPosForPacket = foundWunderfizzBasePos; 
            System.out.println("Client Interaction: Interacted with Wunderfizz at " + finalPosForPacket + ". Client active location: " + KeyInputHandler.clientActiveWunderfizzLocation + ". Cached message: " + cachedWunderfizzMessage); // DEBUG PRINT
            
            DrinkPerkAnimationHandler.startAnimation(() -> {
                NetworkHandler.INSTANCE.sendToServer(new PurchaseWunderfizzDrinkMessage(finalPosForPacket));
            });
            return;
        }
        // --- Fin de la logique de la Wunderfizz ---

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-2, -2, -2), playerPos.offset(2, 2, 2))) {
            BlockState state = mc.level.getBlockState(pos);
            if (!(state.getBlock() instanceof AmmoCrateBlock)) continue;

            double distanceSq = player.position().distanceToSqr(pos.getCenter());
            if (distanceSq > 4.0) continue;

            // Vérifie si le joueur regarde le bloc
            HitResult lookResult = mc.hitResult;
            if (lookResult instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)) {
                // Envoie le packet au serveur
                NetworkHandler.INSTANCE.sendToServer(new PurchaseAmmoCrateMessage());
                return;
            }
        }


        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-2, -1, -2), playerPos.offset(2, 2, 2))) {
            BlockState state = mc.level.getBlockState(pos);
            if (!(state.getBlock() instanceof PerksLowerBlock)) continue;

            double distanceSq = player.position().distanceToSqr(pos.getCenter());
            if (distanceSq > 2.25) continue;

            BlockEntity be = mc.level.getBlockEntity(pos);
            if (!(be instanceof PerksLowerBlockEntity perksBE)) continue;

            boolean powered = state.getValue(PerksLowerBlock.POWERED);
            String perkId = perksBE.getSavedPerkId();
            PerksManager.Perk perk = PerksManager.ALL_PERKS.get(perkId);

            if (perk == null) {
                continue;
            }
            if (perk.getAssociatedEffect() != null && player.hasEffect(perk.getAssociatedEffect())) {
                continue;
            }

            if (!powered) {
                player.displayClientMessage(Component.literal(getTranslatedMessage(
                    "§cVous devez d'abord activer le courant !",
                    "§cYou must activate the power first!"
                )).withStyle(ChatFormatting.RED), true);
                return;
            }

            int currentPerkCount = PerksManager.getPerkCount(player);
            if (currentPerkCount >= PerksManager.MAX_PERKS_LIMIT) {
                 player.displayClientMessage(Component.literal(getTranslatedMessage(
                     "§cVous possédez déjà " + PerksManager.MAX_PERKS_LIMIT + " atouts.",
                     "§cYou already have " + PerksManager.MAX_PERKS_LIMIT + " perks."
                 )).withStyle(ChatFormatting.RED), true);
                 return;
            }

            int price = perksBE.getSavedPrice();
            boolean hasIngot = player.getInventory().items.stream()
                .anyMatch(s -> s.getItem() instanceof IngotSaleItem);

            if (!hasIngot && PointManager.getScore(player) < price) {
                player.displayClientMessage(Component.literal(getTranslatedMessage(
                    "§cPas assez de points ou de lingots !",
                    "§cNot enough points or ingots!"
                )).withStyle(ChatFormatting.RED), true);
                return;
            }

            final BlockPos finalPos = pos;
            DrinkPerkAnimationHandler.startAnimation(() -> {
                NetworkHandler.INSTANCE.sendToServer(new PurchasePerkMessage(finalPos));
            });
            return;
        }
    }


    @SubscribeEvent
    public static void onRenderPackAPunchHint(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null || player.isCreative()) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr)) return;

        BlockPos pos = bhr.getBlockPos();
        Block block = mc.level.getBlockState(pos).getBlock();
        if (!(block instanceof PunchPackBlock)) return;

        double dx = Math.abs(player.getX() - (pos.getX() + 0.5));
        double dz = Math.abs(player.getZ() - (pos.getZ() + 0.5));
        if (dx > 1.5 || dz > 1.5) return;

        boolean powered = mc.level.getBlockState(pos).getValue(PunchPackBlock.POWERED);

        boolean hasIngot = player.getInventory().items.stream()
            .anyMatch(s -> s.getItem() instanceof IngotSaleItem);

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

        int yOffsetAboveHotbar = 20;
        int y = height - mc.font.lineHeight - yOffsetAboveHotbar;

        int x = (width - font.width(text)) / 2;

        event.getGuiGraphics().drawString(font, text, x, y, 0xFFFFFF);
    }


    @SubscribeEvent
    public static void onRenderPerksHint(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null || player.isCreative()) return;

        if (cachedPerkMessage != null) {
            Font font = mc.font;
            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();

            int yOffsetAboveHotbar = 35;
            int y = height - mc.font.lineHeight - yOffsetAboveHotbar;

            int x = (width - font.width(cachedPerkMessage)) / 2;

            event.getGuiGraphics().drawString(font, cachedPerkMessage, x, y, 0xFFFFFF);
        }
    }

    @SubscribeEvent
    public static void onRenderMysteryBoxHint(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null || player.isCreative()) return;

        if (cachedMysteryBoxMessage != null) {
            Font font = mc.font;
            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();

            int yOffsetAboveHotbar = 50; 
            int y = height - mc.font.lineHeight - yOffsetAboveHotbar;

            int x = (width - font.width(cachedMysteryBoxMessage)) / 2;

            event.getGuiGraphics().drawString(font, cachedMysteryBoxMessage, x, y, 0xFFFFFF);
        }
    }

    @SubscribeEvent
    public static void onRenderWunderfizzHint(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null || player.isCreative()) return;

        if (cachedWunderfizzMessage != null) {
            Font font = mc.font;
            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();

            int yOffsetAboveHotbar = 65;
            int y = height - mc.font.lineHeight - yOffsetAboveHotbar;

            int x = (width - font.width(cachedWunderfizzMessage)) / 2;

            event.getGuiGraphics().drawString(font, cachedWunderfizzMessage, x, y, 0xFFFFFF);
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

        if (revivingTargetUUID == null && nearbyDownPlayer != null) {
            String text = getTranslatedMessage(
                "§eMaintenez F pour réanimer " + nearbyDownPlayer.getName().getString(),
                "§eHold F to revive " + nearbyDownPlayer.getName().getString()
            );
            Font font = mc.font;
            int width = event.getWindow().getGuiScaledWidth();
            int height = event.getWindow().getGuiScaledHeight();
            int x = (width - font.width(text)) / 2;
            int y = height / 2 + 50;
            event.getGuiGraphics().drawString(font, text, x, y, 0xFFFFFF);
        }

        if (revivingTargetUUID != null && reviveBarStartTime > 0 && clientReviveDuration > 0) {
            long currentTime = mc.level.getGameTime();
            float progress = (float) (currentTime - reviveBarStartTime) / clientReviveDuration;

            // Réinitialise la barre si la réanimation est terminée
            if (progress >= 1.0f) {
                revivingTargetUUID = null;
                reviveBarStartTime = 0;
                return; // Ne pas dessiner la barre si elle est terminée
            }
            
            if (progress < 0) progress = 0;
            if (progress > 1.0f) progress = 1.0f;

            String targetName = getTranslatedMessage("Joueur", "Player"); // Default translation
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
            int barY = height / 2 + 50;

            event.getGuiGraphics().fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000);
            int filledWidth = (int) (barWidth * progress);
            event.getGuiGraphics().fill(barX + 1, barY + 1, barX + filledWidth - 1, barY + barHeight - 1, 0xFF00FF00);

            int textX = (width - font.width(text)) / 2;
            int textY = barY - font.lineHeight - 2;
            event.getGuiGraphics().drawString(font, text, textX, textY, 0xFFFFFF);
        }
    }

    @SubscribeEvent
    public static void onRenderAmmoCrateHint(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null || player.isCreative()) return;

        if (cachedAmmoCrateMessage != null) {
            Font font = mc.font;
            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();

            int yOffsetAboveHotbar = 80; // Position encore plus haute pour éviter les chevauchements
            int y = height - mc.font.lineHeight - yOffsetAboveHotbar;

            int x = (width - font.width(cachedAmmoCrateMessage)) / 2;

            event.getGuiGraphics().drawString(font, cachedAmmoCrateMessage, x, y, 0xFFFFFF);
        }
    }
}