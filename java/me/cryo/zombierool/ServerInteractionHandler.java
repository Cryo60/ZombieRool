package me.cryo.zombierool.handlers;

import me.cryo.zombierool.block.system.BlindBuySystem.BlindBuyCabinetBlock;
import me.cryo.zombierool.block.system.BlindBuySystem.BlindBuyCabinetBlockEntity;
import me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponBlockEntity;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.system.MeteoriteEasterEgg;
import me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock;
import me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlockEntity;
import me.cryo.zombierool.block.system.PerksSystem.PerksAColaBlockEntity;
import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;
import me.cryo.zombierool.network.InteractionType;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.S2CNotifyPurchasePacket;
import me.cryo.zombierool.network.S2CStartWunderfizzDrinkAnimationPacket;
import me.cryo.zombierool.network.packet.S2CSyncThirdPersonAnimPacket;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.api.IReloadable;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.PerksManager;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.MysteryBoxManager;
import me.cryo.zombierool.block.system.PackAPunchSystem;
import me.cryo.zombierool.AmmoCrateManager;
import me.cryo.zombierool.item.IngotSaleItem;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.scripting.LuaScriptManager;
import me.cryo.zombierool.util.PlayerVoiceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class ServerInteractionHandler {

    private static final long REPAIR_COOLDOWN = 1250;
    private static final double SPEED_COLA_REPAIR_MULTIPLIER = 0.5; 

    private static final Map<UUID, Long> lastRepairTimes = new HashMap<>();
    private static final Map<UUID, Long> activeDrinkAnimations = new HashMap<>();

    public static void resetAll() {
        lastRepairTimes.clear();
        activeDrinkAnimations.clear();
    }

    public static void handleInteraction(ServerPlayer player, BlockPos pos, InteractionType type) {
        if (player == null || player.level().isClientSide) return;
        ServerLevel level = (ServerLevel) player.level();

        if (type != InteractionType.ACTION_KEY && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 25.0) {
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
            case BLIND_BUY_CABINET -> handleBlindBuy(player, level, pos);
            case ACTION_KEY -> {
                LuaScriptManager.callEvent("OnActionKeyPressed", player.getUUID().toString());
                for (me.cryo.zombierool.core.manager.InteractableManager.Interactable inter : me.cryo.zombierool.core.manager.InteractableManager.getInteractables().values()) {
                    if (player.distanceToSqr(inter.pos) <= inter.radius * inter.radius) {
                        LuaScriptManager.callEvent("OnCustomInteract", player.getUUID().toString(), inter.id);
                    }
                }
            }
        }
    }

    private static void handleBlindBuy(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlindBuyCabinetBlock) || !(level.getBlockEntity(pos) instanceof BlindBuyCabinetBlockEntity be)) return;

        boolean isOpen = state.getValue(BlindBuyCabinetBlock.OPEN);
        int price = be.getPrice();
        ItemStack weaponToSell = be.getWeapon();

        if (weaponToSell.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.zombierool.blind_buy.empty").withStyle(ChatFormatting.RED));
            return;
        }

        int balance = PointManager.getScore(player);
        boolean hasIngot = hasIngot(player);

        if (!isOpen) {
            if (!hasIngot && balance < price) {
                player.sendSystemMessage(Component.translatable("message.zombierool.ammo_crate.not_enough_points", price).withStyle(ChatFormatting.RED));
                PlayerVoiceManager.playNoMoneySound(player, level);
                return;
            }

            if (hasIngot) consumeIngot(player);
            else PointManager.modifyScore(player, -price);

            be.triggerPurchase();
            level.setBlock(pos, state.setValue(BlindBuyCabinetBlock.OPEN, true), 3);
            level.playSound(null, pos, ZombieroolModSounds.CABINET_OPEN.get(), SoundSource.BLOCKS, 1f, 1f);
            level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")), SoundSource.PLAYERS, 1f, 1f);
            WeaponFacade.grantWeaponToPlayer(player, weaponToSell);

        } else {
            boolean isTacz = WeaponFacade.isTaczWeapon(weaponToSell);
            WeaponSystem.Definition def = WeaponFacade.getDefinition(weaponToSell);
            ItemStack existing = ItemStack.EMPTY;

            String wId = WeaponFacade.getWeaponId(weaponToSell);

            for (ItemStack s : player.getInventory().items) {
                if (isTacz && WeaponFacade.isTaczWeapon(s)) {
                    if (wId.equals(s.getOrCreateTag().getString("GunId"))) { existing = s; break; }
                } else if (!isTacz && def != null && WeaponFacade.isWeapon(s)) {
                    WeaponSystem.Definition d = WeaponFacade.getDefinition(s);
                    if (d != null && d.id.replace("zombierool:", "").equals(def.id.replace("zombierool:", ""))) { existing = s; break; }
                } else if (!isTacz && def == null && s.getItem() == weaponToSell.getItem()) {
                    existing = s; break;
                }
            }

            if (!existing.isEmpty() && (def != null || isTacz || weaponToSell.getItem() instanceof IReloadable)) {
                int halfPrice = Math.max(1, price / 2);
                if (WeaponFacade.isPackAPunched(existing)) halfPrice += 5000;

                if (!hasIngot && balance < halfPrice) {
                    player.sendSystemMessage(Component.translatable("message.zombierool.wall_weapon.cannot_afford_reload", halfPrice).withStyle(ChatFormatting.RED));
                    PlayerVoiceManager.playNoMoneySound(player, level);
                    return;
                }

                if (hasIngot) consumeIngot(player);
                else PointManager.modifyScore(player, -halfPrice);

                be.triggerPurchase();
                WeaponFacade.setAmmo(existing, WeaponFacade.getMaxAmmo(existing));
                WeaponFacade.setReserve(existing, WeaponFacade.getMaxReserve(existing));
                if (isTacz) WeaponFacade.refillHeldTaczAmmo(player, existing);

                level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")), SoundSource.PLAYERS, 1f, 1f);
                player.inventoryMenu.broadcastChanges();
            } else {
                if (!hasIngot && balance < price) {
                    player.sendSystemMessage(Component.translatable("message.zombierool.ammo_crate.not_enough_points", price).withStyle(ChatFormatting.RED));
                    PlayerVoiceManager.playNoMoneySound(player, level);
                    return;
                }

                if (hasIngot) consumeIngot(player);
                else PointManager.modifyScore(player, -price);

                be.triggerPurchase();
                level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")), SoundSource.PLAYERS, 1f, 1f);
                WeaponFacade.grantWeaponToPlayer(player, weaponToSell);
            }
        }
    }

    private static void handleMeteorite(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof MeteoriteEasterEgg.MeteoriteBlock && state.getValue(MeteoriteEasterEgg.MeteoriteBlock.ACTIVE)) {
            level.setBlock(pos, state.setValue(MeteoriteEasterEgg.MeteoriteBlock.ACTIVE, false), 3);
            MeteoriteEasterEgg.onMeteoriteFound(level, player, pos);
        }
    }

    private static void handleWallWeapon(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockEntity te = level.getBlockEntity(pos);
        if (!(te instanceof BuyWallWeaponBlockEntity be)) return;

        int basePrice = be.getPrice();
        if (basePrice <= 0) return;

        ItemStack weaponToSell = ItemStack.EMPTY;
        var optional = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null).resolve();
        if (optional.isPresent()) {
            weaponToSell = optional.get().getStackInSlot(0);
        }

        if (weaponToSell.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.zombierool.wall_weapon.empty").withStyle(ChatFormatting.RED));
            return;
        }

        boolean isTacz = WeaponFacade.isTaczWeapon(weaponToSell);
        WeaponSystem.Definition def = WeaponFacade.getDefinition(weaponToSell);
        int balance = PointManager.getScore(player);
        boolean hasIngot = hasIngot(player);

        boolean isReloadable = def != null || isTacz || weaponToSell.getItem() instanceof IReloadable;
        boolean isThrowable = weaponToSell.getItem() instanceof me.cryo.zombierool.item.throwable.ThrowableCore.BaseThrowableItem;
        boolean isBowie = weaponToSell.getItem() == me.cryo.zombierool.core.registry.ZRRegistry.BOWIE_KNIFE;

        if (weaponToSell.getItem() instanceof me.cryo.zombierool.item.BulletVestTier1Item.Chestplate) {
            ItemStack equipped = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            if (equipped.getItem() == weaponToSell.getItem() && equipped.getOrCreateTag().getInt("BulletVestArmorPoints") == 4) {
                player.sendSystemMessage(Component.translatable("message.zombierool.wall_weapon.already_have_vest").withStyle(ChatFormatting.RED));
                return;
            }

            if (hasIngot) consumeIngot(player);
            else if (balance < basePrice) {
                player.sendSystemMessage(Component.translatable("message.zombierool.wall_weapon.cannot_afford", balance, basePrice).withStyle(ChatFormatting.RED));
                PlayerVoiceManager.playNoMoneySound(player, level);
                return;
            } else PointManager.modifyScore(player, -basePrice);

            be.triggerPurchase();

            ItemStack newVest = weaponToSell.copy();
            newVest.getOrCreateTag().putInt("BulletVestArmorPoints", 4);
            newVest.getOrCreateTag().putBoolean("BulletVestInitialized", true);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, newVest);

            level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")), SoundSource.PLAYERS, 1f, 1f);
            player.sendSystemMessage(Component.translatable("message.zombierool.wall_weapon.vest_bought").withStyle(ChatFormatting.GREEN));
            return;
        }

        ItemStack existing = ItemStack.EMPTY;
        String wId = WeaponFacade.getWeaponId(weaponToSell);

        if (isReloadable) {
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
        }

        if (!existing.isEmpty() && isReloadable) {
            int halfPrice = Math.max(1, basePrice / 2);
            if (WeaponFacade.isPackAPunched(existing)) {
                halfPrice += 5000; 
            }

            if (hasIngot) {
                consumeIngot(player);
            } else if (balance < halfPrice) {
                player.sendSystemMessage(Component.translatable("message.zombierool.wall_weapon.cannot_afford_reload", balance, halfPrice).withStyle(ChatFormatting.RED));
                PlayerVoiceManager.playNoMoneySound(player, level);
                return;
            } else {
                PointManager.modifyScore(player, -halfPrice);
            }

            be.triggerPurchase();
            WeaponFacade.setAmmo(existing, WeaponFacade.getMaxAmmo(existing));
            WeaponFacade.setReserve(existing, WeaponFacade.getMaxReserve(existing));
            
            if (existing.getItem() instanceof WeaponSystem.BaseGunItem gun && gun.hasDurability()) {
                gun.setDurability(existing, gun.getMaxDurability(existing));
            }

            if (isTacz) WeaponFacade.refillHeldTaczAmmo(player, existing);

            level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")), SoundSource.PLAYERS, 1f, 1f);
            
            if (hasIngot) {
                player.sendSystemMessage(Component.translatable("message.zombierool.wall_weapon.reloaded_ingot", existing.getHoverName().getString()).withStyle(ChatFormatting.GREEN));
            } else {
                player.sendSystemMessage(Component.translatable("message.zombierool.wall_weapon.reloaded", existing.getHoverName().getString(), halfPrice).withStyle(ChatFormatting.GREEN));
            }
            player.inventoryMenu.broadcastChanges();
        } else {
            if (hasIngot) {
                consumeIngot(player);
            } else if (balance < basePrice) {
                player.sendSystemMessage(Component.translatable("message.zombierool.wall_weapon.cannot_afford", balance, basePrice).withStyle(ChatFormatting.RED));
                PlayerVoiceManager.playNoMoneySound(player, level);
                return;
            } else {
                PointManager.modifyScore(player, -basePrice);
            }

            be.triggerPurchase();
            
            if (isReloadable || isThrowable || isBowie) {
                WeaponFacade.grantWeaponToPlayer(player, weaponToSell);
                if (isReloadable) {
                    S2CNotifyPurchasePacket notifyPacket = new S2CNotifyPurchasePacket(wId);
                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), notifyPacket);
                }
            } else {
                ItemStack copy = weaponToSell.copy();
                if (!player.getInventory().add(copy)) {
                    player.drop(copy, false);
                }
            }

            level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")), SoundSource.PLAYERS, 1f, 1f);
            level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "weapon")), SoundSource.PLAYERS, 1f, 1f);
            
            if (hasIngot) {
                player.sendSystemMessage(Component.translatable("message.zombierool.wall_weapon.purchased_ingot", weaponToSell.getHoverName().getString()).withStyle(ChatFormatting.GREEN));
            } else {
                player.sendSystemMessage(Component.translatable("message.zombierool.wall_weapon.purchased", weaponToSell.getHoverName().getString(), basePrice).withStyle(ChatFormatting.GREEN));
            }
        }
    }

    private static void handleObstacle(ServerPlayer player, ServerLevel level, BlockPos pos) {
        me.cryo.zombierool.handlers.ObstaclePurchaseHandler.tryPurchase(player, pos);
    }

    private static void handleMysteryBox(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        MysteryBoxManager manager = MysteryBoxManager.get(level);
        
        if (!(state.getBlock() instanceof MysteryBoxBlock)) {
            return;
        }
        
        if (state.getValue(MysteryBoxBlock.PART)) {
            pos = MysteryBoxManager.getOppositeOtherPartPos(pos, state.getValue(MysteryBoxBlock.FACING));
            state = level.getBlockState(pos);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MysteryBoxBlockEntity mysteryBox)) return;

        if (mysteryBox.getBoxState() == 1) {
            player.sendSystemMessage(Component.translatable("message.zombierool.mystery_box.already_preparing").withStyle(ChatFormatting.YELLOW));
            return;
        }

        if (mysteryBox.getBoxState() == 2 && mysteryBox.isTeddy()) {
            player.sendSystemMessage(Component.translatable("message.zombierool.mystery_box.moving").withStyle(ChatFormatting.RED));
            return;
        }

        if (mysteryBox.getBoxState() == 0) { 
            if (!manager.hasAvailableWeapons(player, level)) {
                player.sendSystemMessage(Component.translatable("message.zombierool.mystery_box.all_weapons_owned").withStyle(ChatFormatting.RED));
                return;
            }

            int cost = 950;
            boolean hasIngot = hasIngot(player);

            if (!hasIngot && PointManager.getScore(player) < cost) {
                player.sendSystemMessage(Component.translatable("message.zombierool.ammo_crate.not_enough_points", cost).withStyle(ChatFormatting.RED));
                PlayerVoiceManager.playNoMoneySound(player, level);
                return;
            }

            boolean moveBox = false;
            if (!manager.isLocked && manager.getRegisteredLocationsCount() > 1 && manager.currentActiveMysteryBoxPair != null && manager.currentActiveMysteryBoxPair.usesSinceLastMove >= manager.currentActiveMysteryBoxPair.moveThreshold) {
                moveBox = true;
            }

            ItemStack weaponToGive = ItemStack.EMPTY;
            if (!moveBox) {
                weaponToGive = manager.getRandomWeapon(player, level);
                if (weaponToGive.isEmpty()) {
                    weaponToGive = WeaponFacade.createWeaponStack("m1911", false, player);
                    if (weaponToGive.isEmpty()) weaponToGive = new ItemStack(net.minecraft.world.item.Items.WOODEN_SWORD);
                }
            }

            if (hasIngot) consumeIngot(player);
            else PointManager.modifyScore(player, -cost);

            mysteryBox.startCycling(player, weaponToGive, moveBox, hasIngot, cost);

            if (manager.currentActiveMysteryBoxPair != null) {
                manager.currentActiveMysteryBoxPair.usesSinceLastMove++;
                manager.setDirty();
            }

        } else if (mysteryBox.getBoxState() == 2 && !mysteryBox.isTeddy()) { 
            mysteryBox.collectWeapon(player);
        }
    }

    private static void handlePerk(ServerPlayer player, ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        if (now < activeDrinkAnimations.getOrDefault(player.getUUID(), 0L)) {
            player.displayClientMessage(Component.translatable("message.zombierool.already_drinking").withStyle(ChatFormatting.RED), true);
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PerksAColaBlockEntity perksBE)) return;
        BlockState state = level.getBlockState(pos);

        if (!perksBE.isPowered()) {
            player.displayClientMessage(Component.translatable("message.zombierool.power_required").withStyle(ChatFormatting.RED), true);
            return;
        } 

        String perkId = perksBE.getSavedPerkId();
        PerksManager.Perk perkToPurchase = PerksManager.ALL_PERKS.get(perkId);

        if (perkToPurchase == null) {
            player.displayClientMessage(Component.translatable("message.zombierool.invalid_perk").withStyle(ChatFormatting.RED), true);
            return;
        }

        if (perkToPurchase.getAssociatedEffect() != null && player.hasEffect(perkToPurchase.getAssociatedEffect())) {
            player.displayClientMessage(Component.translatable("message.zombierool.already_have_perk", perkToPurchase.getNameComponent()).withStyle(ChatFormatting.RED), true);
            return;
        }

        int currentPerkCount = PerksManager.getPerkCount(player);
        if (currentPerkCount >= PerksManager.MAX_PERKS_LIMIT) {
            player.displayClientMessage(Component.translatable("message.zombierool.max_perks", PerksManager.MAX_PERKS_LIMIT).withStyle(ChatFormatting.RED), true);
            return;
        }

        if (PerksManager.isPerkLimited(perkId, player)) {
            int currentPerkPurchases = PerksManager.getCurrentPerkPurchases(perkId, player);
            int perkLimit = PerksManager.getPerkLimit(perkId, player);
            if (currentPerkPurchases >= perkLimit) {
                Component perkName = perkToPurchase.getNameComponent();
                player.displayClientMessage(Component.translatable("message.zombierool.perk_limit_reached", perkLimit, perkName).withStyle(ChatFormatting.RED), true);
                return;
            }
        }

        int price = perksBE.getSavedPrice();
        int balance = PointManager.getScore(player);
        boolean hasIngot = hasIngot(player);

        if (!hasIngot && balance < price) {
            player.displayClientMessage(Component.translatable("message.zombierool.not_enough_points_perk").withStyle(ChatFormatting.RED), true);
            PlayerVoiceManager.playNoMoneySound(player, level);
            return;
        }

        Component paymentMessage;
        if (hasIngot) {
            consumeIngot(player);
            paymentMessage = Component.translatable("message.zombierool.payment_ingot");
        } else {
            PointManager.modifyScore(player, -price);
            paymentMessage = Component.translatable("message.zombierool.payment_points", price);
        }

        activeDrinkAnimations.put(player.getUUID(), now + 70);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new S2CSyncThirdPersonAnimPacket(player.getUUID(), "drink_perk", 70));
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2CStartWunderfizzDrinkAnimationPacket(perkId));
        
        level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:buy")), SoundSource.BLOCKS, 1f, 1f);

        level.getServer().execute(() -> {
            level.getServer().tell(new net.minecraft.server.TickTask(
                level.getServer().getTickCount() + 70, 
                () -> {
                    if (player.isAlive()) {
                        perkToPurchase.applyEffect(player);
                        PerksManager.incrementPerkPurchases(perkId, player);
                        
                        MutableComponent finalMessage = Component.translatable("message.zombierool.perk_activated", perkToPurchase.getNameComponent()).withStyle(ChatFormatting.GREEN).append(paymentMessage);
                        player.displayClientMessage(finalMessage, true);
                        
                        PlayerVoiceManager.playTookPerk(player, level);
                        LuaScriptManager.callEvent("OnPerkBought", player.getUUID().toString(), perkToPurchase.getId());
                    }
                }
            ));
        });
    }

    private static void handleWunderfizzBuy(ServerPlayer player, ServerLevel level, BlockPos pos) {
        WorldConfig config = WorldConfig.get(level);
        BlockPos activePos = config.getActiveWunderfizzPosition();

        if (activePos == null || !activePos.equals(pos)) {
            player.displayClientMessage(Component.translatable("message.zombierool.wunderfizz.not_here").withStyle(ChatFormatting.RED), true);
            return;
        }

        if (!me.cryo.zombierool.block.DerWunderfizzBlock.isPowered(level, pos)) {
            player.displayClientMessage(Component.translatable("message.zombierool.power_required").withStyle(ChatFormatting.RED), true);
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DerWunderfizzBlockEntity wunderfizz)) return;

        if (wunderfizz.getState() != DerWunderfizzBlockEntity.WunderfizzState.IDLE) return;

        int currentPerkCount = PerksManager.getPerkCount(player);
        if (currentPerkCount >= PerksManager.MAX_PERKS_LIMIT) {
            player.displayClientMessage(Component.translatable("message.zombierool.max_perks", PerksManager.MAX_PERKS_LIMIT).withStyle(ChatFormatting.RED), true);
            return;
        }

        long availablePerksCount = PerksManager.ALL_PERKS.keySet().stream()
            .filter(perkId -> {
                var effect = PerksManager.getEffectInstance(perkId);
                return effect != null && !player.hasEffect(effect) && !config.isRandomPerkDisabled(perkId);
            })
            .count();

        if (availablePerksCount == 0) {
            player.displayClientMessage(Component.translatable("message.zombierool.wunderfizz.all_perks_owned").withStyle(ChatFormatting.RED), true);
            return;
        }

        int cost = 1500;
        boolean hasIngot = hasIngot(player);

        if (hasIngot) {
            consumeIngot(player);
        } else {
            if (PointManager.getScore(player) < cost) {
                player.displayClientMessage(Component.translatable("message.zombierool.ammo_crate.not_enough_points", cost).withStyle(ChatFormatting.RED), true);
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
            player.displayClientMessage(Component.translatable("message.zombierool.already_drinking").withStyle(ChatFormatting.RED), true);
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
            player.displayClientMessage(Component.translatable("message.zombierool.max_perks", PerksManager.MAX_PERKS_LIMIT).withStyle(ChatFormatting.RED), true);
            return;
        }

        var effect = PerksManager.getEffectInstance(perkId);
        if (effect != null && player.hasEffect(effect)) {
            player.displayClientMessage(Component.translatable("message.zombierool.already_have_perk", perk.getNameComponent()).withStyle(ChatFormatting.RED), true);
            wunderfizz.resetAfterCollect();
            return;
        }

        if (PerksManager.isPerkLimited(perkId, player)) {
            int limit = PerksManager.getPerkLimit(perkId, player);
            int purchases = PerksManager.getCurrentPerkPurchases(perkId, player);
            if (purchases >= limit) {
                player.displayClientMessage(Component.translatable("message.zombierool.perk_limit_reached", limit, perk.getNameComponent()).withStyle(ChatFormatting.RED), true);
                return;
            }
        }

        activeDrinkAnimations.put(player.getUUID(), now + 50);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new S2CSyncThirdPersonAnimPacket(player.getUUID(), "drink_perk", 50));
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2CStartWunderfizzDrinkAnimationPacket(perkId));

        final String finalPerkId = perkId;
        level.getServer().execute(() -> {
            level.getServer().tell(new net.minecraft.server.TickTask(
                level.getServer().getTickCount() + 50, 
                () -> {
                    if (player.isAlive()) {
                        perk.applyEffect(player);
                        PerksManager.incrementPerkPurchases(finalPerkId, player);
                        player.displayClientMessage(Component.translatable("message.zombierool.wunderfizz.obtained", perk.getNameComponent()).withStyle(ChatFormatting.GREEN), true);
                        PlayerVoiceManager.playTookPerk(player, level);
                        LuaScriptManager.callEvent("OnPerkBought", player.getUUID().toString(), perkId);
                    }
                }
            ));
        });

        wunderfizz.resetAfterCollect();
    }

    private static void handlePackAPunch(ServerPlayer player, ServerLevel level, BlockPos pos) {
        PackAPunchSystem.Manager.tryUsePack(player, level, pos);
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
}