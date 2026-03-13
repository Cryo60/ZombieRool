package me.cryo.zombierool.handlers;

import me.cryo.zombierool.AmmoCrateManager;
import me.cryo.zombierool.MysteryBoxManager;
import me.cryo.zombierool.PerksManager;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.api.IPackAPunchable;
import me.cryo.zombierool.api.IReloadable;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.block.*;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.system.MeteoriteEasterEgg;
import me.cryo.zombierool.block.entity.BuyWallWeaponBlockEntity;
import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;
import me.cryo.zombierool.block.entity.ObstacleDoorBlockEntity;
import me.cryo.zombierool.block.entity.PerksLowerBlockEntity;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.item.IngotSaleItem;
import me.cryo.zombierool.logic.PackAPunchManager;
import me.cryo.zombierool.network.*;
import me.cryo.zombierool.util.PlayerVoiceManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class ServerInteractionHandler {

    private static final long REPAIR_COOLDOWN = 1250;
    private static final double SPEED_COLA_REPAIR_MULTIPLIER = 0.5; 
    private static final Map<UUID, Long> lastRepairTimes = new HashMap<>();
    private static final Map<UUID, Long> activeDrinkAnimations = new HashMap<>();

    public static void handleInteraction(ServerPlayer player, BlockPos pos, InteractionType type) {
        if (player == null || player.level().isClientSide) return;
        ServerLevel level = (ServerLevel) player.level();

        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 25.0) {
            return; 
        }

        switch (type) {
            case WALL_WEAPON -> handleWallWeapon(player, level, pos);
            case OBSTACLE -> handleObstacle(player, level, pos);
            case MYSTERY_BOX -> handleMysteryBox(player, level, pos);
            case PERK -> handlePerk(player, level, pos);
            case WUNDERFIZZ_BUY -> handleWunderfizzBuy(player, level, pos);
            case WUNDERFIZZ_COLLECT -> handleWunderfizzCollect(player, level, pos);
            case PACK_A_PUNCH -> handlePackAPunch(player, level, pos);
            case AMMO_CRATE -> handleAmmoCrate(player, level, pos);
            case REPAIR_BARRICADE -> handleRepairBarricade(player, level, pos);
            case METEORITE -> handleMeteorite(player, level, pos);
        }
    }

    private static void handleMeteorite(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof MeteoriteEasterEgg.MeteoriteBlock && state.getValue(MeteoriteEasterEgg.MeteoriteBlock.ACTIVE)) {
            level.setBlock(pos, state.setValue(MeteoriteEasterEgg.MeteoriteBlock.ACTIVE, false), 3);
            MeteoriteEasterEgg.onMeteoriteFound(level, player);
        }
    }

    private static void handleWallWeapon(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockEntity te = level.getBlockEntity(pos);
        if (!(te instanceof BuyWallWeaponBlockEntity be)) return;

        int basePrice = be.getPrice();
        if (basePrice <= 0) return;

        ItemStack weaponToSell = ItemStack.EMPTY;
        var optional = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve();
        if (optional.isPresent()) {
            weaponToSell = optional.get().getStackInSlot(0);
        }

        if (weaponToSell.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cCette arme au mur est vide (Mod manquant ?).").withStyle(ChatFormatting.RED));
            return;
        }

        boolean isTacz = WeaponFacade.isTaczWeapon(weaponToSell);
        WeaponSystem.Definition def = WeaponFacade.getDefinition(weaponToSell);

        int balance = PointManager.getScore(player);
        boolean hasIngot = hasIngot(player);

        ItemStack existing = ItemStack.EMPTY;
        String wId = WeaponFacade.getWeaponId(weaponToSell);

        for (ItemStack s : player.getInventory().items) {
            if (isTacz && WeaponFacade.isTaczWeapon(s)) {
                String sGunId = s.getOrCreateTag().getString("GunId");
                if (wId.equals(sGunId)) {
                    existing = s; break;
                }
            } else if (!isTacz && def != null && WeaponFacade.isWeapon(s)) {
                WeaponSystem.Definition d = WeaponFacade.getDefinition(s);
                if (d != null && d.id.replace("zombierool:", "").equals(def.id.replace("zombierool:", ""))) {
                    existing = s; break;
                }
            } else if (!isTacz && def == null && s.getItem() == weaponToSell.getItem()) {
                existing = s; break;
            }
        }

        boolean isReloadable = def != null || isTacz || weaponToSell.getItem() instanceof IReloadable;

        if (!existing.isEmpty() && isReloadable) {
            int halfPrice = Math.max(1, basePrice / 2);
            if (WeaponFacade.isPackAPunched(existing)) {
                halfPrice += 5000; 
            }

            if (hasIngot) {
                consumeIngot(player);
            } else if (balance < halfPrice) {
                player.sendSystemMessage(getTranslatedComponent(player,
                    "§cPas assez de points pour recharger (" + balance + " / " + halfPrice + ")",
                    "§cNot enough points to reload (" + balance + " / " + halfPrice + ")"
                ));
                PlayerVoiceManager.playNoMoneySound(player, level);
                return;
            } else {
                PointManager.modifyScore(player, -halfPrice);
            }

            WeaponFacade.setAmmo(existing, WeaponFacade.getMaxAmmo(existing));
            WeaponFacade.setReserve(existing, WeaponFacade.getMaxReserve(existing));
            if (existing.getItem() instanceof WeaponSystem.BaseGunItem gun && gun.hasDurability()) {
                gun.setDurability(existing, gun.getMaxDurability(existing));
            }

            if (isTacz) WeaponFacade.refillHeldTaczAmmo(player, existing);

            level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")), SoundSource.PLAYERS, 1f, 1f);
            player.sendSystemMessage(getTranslatedComponent(player,
                "§aRechargé : " + existing.getHoverName().getString() + (hasIngot ? " pour 1 ingot" : " pour " + halfPrice + " points"),
                "§aReloaded: " + existing.getHoverName().getString() + (hasIngot ? " for 1 ingot" : " for " + halfPrice + " points")
            ));
            player.inventoryMenu.broadcastChanges();

        } else {
            if (hasIngot) {
                consumeIngot(player);
            } else if (balance < basePrice) {
                player.sendSystemMessage(getTranslatedComponent(player,
                    "§cPas assez de points (" + balance + " / " + basePrice + ")",
                    "§cNot enough points (" + balance + " / " + basePrice + ")"
                ));
                PlayerVoiceManager.playNoMoneySound(player, level);
                return;
            } else {
                PointManager.modifyScore(player, -basePrice);
            }

            ItemStack stackToGive = weaponToSell.copy();
            stackToGive.setCount(1);
            if (def != null) {
                stackToGive = WeaponFacade.createWeaponStack(def.id, false);
                if (stackToGive.isEmpty()) stackToGive = new ItemStack(weaponToSell.getItem(), 1);
            } else if (isTacz) {
                ResourceLocation gunId = new ResourceLocation(stackToGive.getOrCreateTag().getString("GunId"));
                stackToGive = WeaponFacade.createUnmappedTaczWeaponStack(gunId, false);
            } else if (stackToGive.getItem() instanceof IReloadable r) {
                r.initializeIfNeeded(stackToGive);
            }

            if (!player.getInventory().add(stackToGive)) {
                player.drop(stackToGive, false);
            }

            NotifyPurchasePacket notifyPacket = new NotifyPurchasePacket(wId);
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), notifyPacket);

            level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")), SoundSource.PLAYERS, 1f, 1f);
            level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "weapon")), SoundSource.PLAYERS, 1f, 1f);

            player.sendSystemMessage(getTranslatedComponent(player,
                "§aAcheté : " + stackToGive.getHoverName().getString() + (hasIngot ? " pour 1 ingot" : " pour " + basePrice + " points"),
                "§aPurchased: " + stackToGive.getHoverName().getString() + (hasIngot ? " for 1 ingot" : " for " + basePrice + " points")
            ));
        }
    }

    private static void handleObstacle(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ObstaclePurchaseHandler.tryPurchase(player, pos);
    }

    private static void handleMysteryBox(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        MysteryBoxManager manager = MysteryBoxManager.get(level);

        if (manager.isMysteryBoxMoving) {
            player.sendSystemMessage(getTranslatedComponent(player, "§cLa Mystery Box est en train de se déplacer... attendez !", "§cThe Mystery Box is moving... wait!").withStyle(ChatFormatting.RED));
            return;
        }
        if (manager.isAwaitingWeapon) {
            player.sendSystemMessage(getTranslatedComponent(player, "§cLa Mystery Box prépare déjà votre arme... un instant !", "§cThe Mystery Box is already preparing your weapon... just a moment!").withStyle(ChatFormatting.YELLOW));
            return;
        }

        if (!(state.getBlock() instanceof MysteryBoxBlock) || state.getValue(MysteryBoxBlock.PART)) {
            return;
        }

        if (!manager.hasAvailableWeapons(player, level)) {
            player.sendSystemMessage(getTranslatedComponent(player, "§cVous possédez déjà toutes les armes disponibles !", "§cYou already have all available weapons!").withStyle(ChatFormatting.RED));
            return;
        }

        int cost = 950;
        boolean hasIngot = hasIngot(player);

        if (hasIngot) {
            consumeIngot(player);
            manager.startMysteryBoxInteraction(level, player, true);
        } else {
            if (PointManager.getScore(player) < cost) {
                player.sendSystemMessage(getTranslatedComponent(player, "§cPas assez de points ! (" + cost + " points requis)", "§cNot enough points! (" + cost + " points required)").withStyle(ChatFormatting.RED));
                PlayerVoiceManager.playNoMoneySound(player, level);
                return;
            }
            manager.startMysteryBoxInteraction(level, player, false);
        }
    }

    private static void handlePerk(ServerPlayer player, ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        if (now < activeDrinkAnimations.getOrDefault(player.getUUID(), 0L)) {
            player.displayClientMessage(getTranslatedComponent(player, "§cVous êtes déjà en train de boire !", "§cYou are already drinking!"), true);
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PerksLowerBlockEntity perksBE)) return;

        BlockState state = level.getBlockState(pos);
        if (!state.getValue(PerksLowerBlock.POWERED)) {
            player.displayClientMessage(getTranslatedComponent(player, "§cVous devez d'abord activer le courant !", "§cYou must activate the power first!"), true);
            return;
        } 

        String perkId = perksBE.getSavedPerkId();
        PerksManager.Perk perkToPurchase = PerksManager.ALL_PERKS.get(perkId);

        if (perkToPurchase == null) {
            player.displayClientMessage(getTranslatedComponent(player, "§cErreur : Atout invalide.", "§cError: Invalid perk."), true);
            return;
        }

        if (perkToPurchase.getAssociatedEffect() != null && player.hasEffect(perkToPurchase.getAssociatedEffect())) {
            player.displayClientMessage(getTranslatedComponent(player, "§cVous avez déjà cette perk !", "§cYou already have this perk!"), true);
            return;
        }

        int currentPerkCount = PerksManager.getPerkCount(player);
        if (currentPerkCount >= PerksManager.MAX_PERKS_LIMIT) {
            player.displayClientMessage(getTranslatedComponent(player, "§cVous avez atteint la limite de " + PerksManager.MAX_PERKS_LIMIT + " perks !", "§cYou have reached the limit of " + PerksManager.MAX_PERKS_LIMIT + " perks!"), true);
            return;
        }

        if (PerksManager.isPerkLimited(perkId, player)) {
            int currentPerkPurchases = PerksManager.getCurrentPerkPurchases(perkId, player);
            int perkLimit = PerksManager.getPerkLimit(perkId, player);
            if (currentPerkPurchases >= perkLimit) {
                String perkName = perkToPurchase.getName();
                player.displayClientMessage(getTranslatedComponent(player, "§cVous avez atteint la limite de " + perkLimit + " achats pour " + perkName + " !", "§cYou have reached the limit of " + perkLimit + " purchases for " + perkName + "!"), true);
                return;
            }
        }

        int price = perksBE.getSavedPrice();
        int balance = PointManager.getScore(player);
        boolean hasIngot = hasIngot(player);

        if (!hasIngot && balance < price) {
            player.displayClientMessage(getTranslatedComponent(player, "§cVous n'avez pas assez de points ou de lingots !", "§cYou don't have enough points or ingots!"), true);
            PlayerVoiceManager.playNoMoneySound(player, level);
            return;
        }

        String paymentMessage;
        if (hasIngot) {
            consumeIngot(player);
            paymentMessage = getTranslatedComponent(player, " (1 lingot)", " (1 ingot)").getString();
        } else {
            PointManager.modifyScore(player, -price);
            paymentMessage = getTranslatedComponent(player, " (" + price + " points)", " (" + price + " points)").getString();
        }

        activeDrinkAnimations.put(player.getUUID(), now + 65);
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new StartWunderfizzDrinkAnimationPacket(perkId));
        level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:buy")), SoundSource.BLOCKS, 1f, 1f);

        level.getServer().execute(() -> {
            level.getServer().tell(new net.minecraft.server.TickTask(
                level.getServer().getTickCount() + 60, 
                () -> {
                    if (player.isAlive()) {
                        perkToPurchase.applyEffect(player);
                        PerksManager.incrementPerkPurchases(perkId, player);
                        MutableComponent finalMessage = getTranslatedComponent(player, "§aAtout activé : ", "§aPerk activated: ")
                            .append(perkToPurchase.getName())
                            .append(paymentMessage);
                        player.displayClientMessage(finalMessage, true);
                    }
                }
            ));
        });
    }

    private static void handleWunderfizzBuy(ServerPlayer player, ServerLevel level, BlockPos pos) {
        WorldConfig config = WorldConfig.get(level);
        BlockPos activePos = config.getActiveWunderfizzPosition();

        if (activePos == null || !activePos.equals(pos)) {
            player.displayClientMessage(getTranslatedComponent(player, "§cLa Wunderfizz est ailleurs !", "§cThe Wunderfizz is elsewhere!"), true);
            return;
        }

        if (!DerWunderfizzBlock.isPowered(level, pos)) {
            player.displayClientMessage(getTranslatedComponent(player, "§cVous devez d'abord activer le courant !", "§cYou must activate the power first!"), true);
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DerWunderfizzBlockEntity wunderfizz)) return;

        if (wunderfizz.getState() != DerWunderfizzBlockEntity.WunderfizzState.IDLE) return;

        int currentPerkCount = PerksManager.getPerkCount(player);
        if (currentPerkCount >= PerksManager.MAX_PERKS_LIMIT) {
            player.displayClientMessage(getTranslatedComponent(player, "§cVous possédez déjà " + PerksManager.MAX_PERKS_LIMIT + " atouts !", "§cYou already have " + PerksManager.MAX_PERKS_LIMIT + " perks!"), true);
            return;
        }

        long availablePerksCount = PerksManager.ALL_PERKS.keySet().stream()
            .filter(perkId -> {
                var effect = PerksManager.getEffectInstance(perkId);
                return effect != null && !player.hasEffect(effect) && !config.isRandomPerkDisabled(perkId);
            })
            .count();

        if (availablePerksCount == 0) {
            player.displayClientMessage(getTranslatedComponent(player, "§cVous possédez déjà tous les atouts disponibles !", "§cYou already have all available perks!"), true);
            return;
        }

        int cost = 1500;
        boolean hasIngot = hasIngot(player);

        if (hasIngot) {
            consumeIngot(player);
        } else {
            if (PointManager.getScore(player) < cost) {
                player.displayClientMessage(getTranslatedComponent(player, "§cPas assez de points ! (1500 requis)", "§cNot enough points! (1500 required)"), true);
                PlayerVoiceManager.playNoMoneySound(player, level);
                return;
            }
            PointManager.modifyScore(player, -cost);
        }

        level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:buy")), SoundSource.BLOCKS, 1.0F, 1.0F);
        wunderfizz.startAnimation(player);
        level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:wunderfizz_start")), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static void handleWunderfizzCollect(ServerPlayer player, ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        if (now < activeDrinkAnimations.getOrDefault(player.getUUID(), 0L)) {
            player.displayClientMessage(getTranslatedComponent(player, "§cVous êtes déjà en train de boire !", "§cYou are already drinking!"), true);
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DerWunderfizzBlockEntity wunderfizz)) return;

        if (wunderfizz.getState() != DerWunderfizzBlockEntity.WunderfizzState.READY) return;

        String perkId = wunderfizz.getSelectedPerkId();
        if (perkId == null) return;

        if (!wunderfizz.collectDrink(player)) return; 

        PerksManager.Perk perk = PerksManager.ALL_PERKS.get(perkId);
        if (perk == null) return;

        int currentPerkCount = PerksManager.getPerkCount(player);
        if (currentPerkCount >= PerksManager.MAX_PERKS_LIMIT) {
             player.displayClientMessage(getTranslatedComponent(player, "§cVous possédez déjà " + PerksManager.MAX_PERKS_LIMIT + " atouts.", "§cYou already have " + PerksManager.MAX_PERKS_LIMIT + " perks."), true);
             return;
        }

        var effect = PerksManager.getEffectInstance(perkId);
        if (effect != null && player.hasEffect(effect)) {
            player.displayClientMessage(getTranslatedComponent(player, "§cVous possédez déjà cet atout !", "§cYou already have this perk!"), true);
            DerWunderfizzBlock.updatePerkType(level, pos, "idle");
            wunderfizz.resetAfterCollect();
            return;
        }

        if (PerksManager.isPerkLimited(perkId, player)) {
            int limit = PerksManager.getPerkLimit(perkId, player);
            int purchases = PerksManager.getCurrentPerkPurchases(perkId, player);
            if (purchases >= limit) {
                 player.displayClientMessage(getTranslatedComponent(player, "§cVous avez atteint la limite d'achats pour " + perk.getName() + ".", "§cYou have reached the purchase limit for " + perk.getName() + "."), true);
                 return;
            }
        }

        activeDrinkAnimations.put(player.getUUID(), now + 65);
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new StartWunderfizzDrinkAnimationPacket(perkId));

        final String finalPerkId = perkId;
        level.getServer().execute(() -> {
            level.getServer().tell(new net.minecraft.server.TickTask(
                level.getServer().getTickCount() + 60, 
                () -> {
                    if (player.isAlive()) {
                        perk.applyEffect(player);
                        PerksManager.incrementPerkPurchases(finalPerkId, player);
                        player.displayClientMessage(getTranslatedComponent(player, "§aVous avez obtenu " + perk.getName() + " !", "§aYou obtained " + perk.getName() + "!"), true);
                    }
                }
            ));
        });

        DerWunderfizzBlock.updatePerkType(level, pos, "idle");
        wunderfizz.resetAfterCollect();
    }

    private static void handlePackAPunch(ServerPlayer player, ServerLevel level, BlockPos pos) {
        PackAPunchManager.tryUsePack(player, level, pos);
    }

    private static void handleAmmoCrate(ServerPlayer player, ServerLevel level, BlockPos pos) {
        AmmoCrateManager manager = AmmoCrateManager.get(level);
        int currentWave = WaveManager.getCurrentWave();
        boolean purchaseSuccessful = manager.tryPurchaseAmmo(player, level, currentWave);
        if (purchaseSuccessful) {
            manager.sendPriceInfoToClient(player, currentWave);
        }
    }

    private static void handleRepairBarricade(ServerPlayer player, ServerLevel level, BlockPos pos) {
        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();
        boolean hasSpeedCola = player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get());
        long effectiveCooldown = hasSpeedCola ? (long) (REPAIR_COOLDOWN * SPEED_COLA_REPAIR_MULTIPLIER) : REPAIR_COOLDOWN;

        long lastTime = lastRepairTimes.getOrDefault(playerId, 0L);
        if (now - lastTime < effectiveCooldown) return;
        lastRepairTimes.put(playerId, now);

        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DefenseDoorSystem.DefenseDoorBlock door) { 
            int stage = state.getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE); 
            if (stage < 5) {
                door.updateStage(level, pos, stage + 1);
                Random rand = new Random();
                int soundIndex = rand.nextInt(3); 
                String soundFile = "board_slam_0" + soundIndex;
                level.playSound(null, pos, SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", soundFile)), SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        }
    }

    private static boolean hasIngot(ServerPlayer player) {
        return player.getInventory().items.stream()
            .anyMatch(s -> s.getItem() instanceof IngotSaleItem);
    }

    private static void consumeIngot(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof IngotSaleItem) {
                stack.shrink(1);
                break;
            }
        }
    }

    private static boolean isEnglishClient() {
        return true; 
    }

    private static MutableComponent getTranslatedComponent(ServerPlayer player, String frenchMessage, String englishMessage) {
        if (player != null && isEnglishClient()) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }
}