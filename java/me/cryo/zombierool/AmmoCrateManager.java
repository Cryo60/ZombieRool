package me.cryo.zombierool;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.S2CAmmoCratePricePacket;
import net.minecraftforge.network.PacketDistributor;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.api.IReloadable;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AmmoCrateManager extends SavedData {
    private static final String DATA_NAME = "zombierool_ammo_crate_manager";
    private int baseCost = 1000;
    private int costIncrement = 500;
    private static final ResourceLocation BUY_SOUND = new ResourceLocation("zombierool", "buy");
    private final Map<UUID, Integer> playerUsageCount = new ConcurrentHashMap<>();
    private final Map<String, Boolean> playerWavePurchases = new ConcurrentHashMap<>();
    private int lastWaveReset = -1;

    public AmmoCrateManager() {}

    public static AmmoCrateManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                AmmoCrateManager::load,
                AmmoCrateManager::new,
                DATA_NAME
        );
    }

    public static AmmoCrateManager load(CompoundTag nbt) {
        AmmoCrateManager manager = new AmmoCrateManager();
        manager.lastWaveReset = nbt.getInt("LastWaveReset");
        if (nbt.contains("BaseCost")) manager.baseCost = nbt.getInt("BaseCost");
        if (nbt.contains("CostIncrement")) manager.costIncrement = nbt.getInt("CostIncrement");
        CompoundTag usageTag = nbt.getCompound("PlayerUsageCount");
        for (String key : usageTag.getAllKeys()) {
            try {
                UUID playerUUID = UUID.fromString(key);
                int count = usageTag.getInt(key);
                manager.playerUsageCount.put(playerUUID, count);
            } catch (IllegalArgumentException e) {
                System.err.println("Failed to parse UUID for ammo crate usage: " + key);
            }
        }
        CompoundTag wavePurchasesTag = nbt.getCompound("PlayerWavePurchases");
        for (String key : wavePurchasesTag.getAllKeys()) {
            manager.playerWavePurchases.put(key, wavePurchasesTag.getBoolean(key));
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        compound.putInt("LastWaveReset", lastWaveReset);
        compound.putInt("BaseCost", baseCost);
        compound.putInt("CostIncrement", costIncrement);
        CompoundTag usageTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : playerUsageCount.entrySet()) {
            usageTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        compound.put("PlayerUsageCount", usageTag);
        CompoundTag wavePurchasesTag = new CompoundTag();
        for (Map.Entry<String, Boolean> entry : playerWavePurchases.entrySet()) {
            wavePurchasesTag.putBoolean(entry.getKey(), entry.getValue());
        }
        compound.put("PlayerWavePurchases", wavePurchasesTag);
        return compound;
    }

    public void setBaseCost(int cost) {
        this.baseCost = cost;
        setDirty();
    }

    public void setCostIncrement(int inc) {
        this.costIncrement = inc;
        setDirty();
    }

    public void resetPlayerUsage(UUID playerUUID) {
        playerUsageCount.remove(playerUUID);
        playerWavePurchases.entrySet().removeIf(e -> e.getKey().startsWith(playerUUID.toString()));
        setDirty();
    }

    public void checkAndResetForNewWave(int currentWave) {
        if (currentWave != lastWaveReset) {
            playerWavePurchases.entrySet().removeIf(entry ->
                    entry.getKey().endsWith("wave" + lastWaveReset)
            );
            lastWaveReset = currentWave;
            setDirty();
        }
    }

    public boolean canPlayerPurchase(ServerPlayer player, int currentWave) {
        checkAndResetForNewWave(currentWave);
        String key = player.getUUID().toString() + "wave" + currentWave;
        return !playerWavePurchases.containsKey(key);
    }

    public int getPriceForPlayer(UUID playerUUID) {
        int usageCount = playerUsageCount.getOrDefault(playerUUID, 0);
        return baseCost + (usageCount * costIncrement);
    }

    private boolean isWonderWeapon(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return false;
        WeaponSystem.Definition def = WeaponFacade.getDefinition(stack);
        if (def != null && (def.is_wonder_weapon || "WONDER".equalsIgnoreCase(def.type))) return true;
        return MysteryBoxManager.WONDER_WEAPONS.contains(stack.getItem());
    }

    private boolean isReloadableWeapon(ItemStack stack) {
        return WeaponFacade.isWeapon(stack);
    }

    public boolean tryPurchaseAmmo(ServerPlayer player, ServerLevel level, int currentWave) {
        checkAndResetForNewWave(currentWave);
        ItemStack heldItem = player.getMainHandItem();
        if (!isReloadableWeapon(heldItem)) {
            player.sendSystemMessage(Component.translatable("message.zombierool.ammo_crate.hold_weapon").withStyle(ChatFormatting.RED));
            return false;
        }
        if (isWonderWeapon(heldItem, level)) {
            player.sendSystemMessage(Component.translatable("message.zombierool.ammo_crate.no_wonder").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!canPlayerPurchase(player, currentWave)) {
            player.sendSystemMessage(Component.translatable("message.zombierool.ammo_crate.already_purchased").withStyle(ChatFormatting.RED));
            return false;
        }
        boolean isFull = false;
        if (heldItem.getItem() instanceof WeaponSystem.BaseGunItem gun && gun.hasDurability()) {
            isFull = gun.getDurability(heldItem) >= gun.getMaxDurability(heldItem);
        } else {
            int maxReserve = WeaponFacade.getMaxReserve(heldItem);
            if (maxReserve == 0) {
                isFull = WeaponFacade.getAmmo(heldItem) >= WeaponFacade.getMaxAmmo(heldItem);
            } else {
                isFull = WeaponFacade.getReserve(heldItem) >= maxReserve && WeaponFacade.getAmmo(heldItem) >= WeaponFacade.getMaxAmmo(heldItem);
            }
        }
        if (isFull) {
            player.sendSystemMessage(Component.translatable("message.zombierool.ammo_crate.full").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        int cost = getPriceForPlayer(player.getUUID());
        if (PointManager.getScore(player) < cost) {
            player.sendSystemMessage(Component.translatable("message.zombierool.ammo_crate.not_enough_points", cost).withStyle(ChatFormatting.RED));
            return false;
        }
        PointManager.modifyScore(player, -cost);
        if (heldItem.getItem() instanceof IReloadable r) {
            r.setAmmo(heldItem, r.getMaxAmmo(heldItem));
            r.setReserve(heldItem, r.getMaxReserve(heldItem));
            r.setReloadTimer(heldItem, 0);
            heldItem.getOrCreateTag().putBoolean(WeaponSystem.BaseGunItem.TAG_IS_RELOADING, false);
            if (heldItem.getItem() instanceof WeaponSystem.BaseGunItem gun && gun.isAkimbo(heldItem)) {
                gun.setAmmoLeft(heldItem, gun.getMaxAmmo(heldItem));
            }
        } else if (WeaponFacade.isTaczWeapon(heldItem)) {
            WeaponFacade.refillHeldTaczAmmo(player, heldItem);
        }
        if (heldItem.getItem() instanceof WeaponSystem.BaseGunItem gun && gun.hasDurability()) {
            gun.setDurability(heldItem, gun.getMaxDurability(heldItem));
        }
        playerUsageCount.put(player.getUUID(), playerUsageCount.getOrDefault(player.getUUID(), 0) + 1);
        String key = player.getUUID().toString() + "wave" + currentWave;
        playerWavePurchases.put(key, true);
        setDirty();
        level.playSound(null, player.blockPosition(),
                BuiltInRegistries.SOUND_EVENT.get(BUY_SOUND),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendSystemMessage(Component.translatable("message.zombierool.ammo_crate.refilled", cost).withStyle(ChatFormatting.GREEN));
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    public void sendPriceInfoToClient(ServerPlayer player, int currentWave) {
        String hudMessage = getHudMessage(player, currentWave);
        int price = getPriceForPlayer(player.getUUID());
        boolean canPurchase = canPlayerPurchase(player, currentWave);
        NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2CAmmoCratePricePacket(price, canPurchase, hudMessage)
        );
    }

    public void resetAllData() {
        playerUsageCount.clear();
        playerWavePurchases.clear();
        lastWaveReset = -1;
        setDirty();
    }

    public String getHudMessage(ServerPlayer player, int currentWave) {
        checkAndResetForNewWave(currentWave);
        ItemStack heldItem = player.getMainHandItem();
        if (!isReloadableWeapon(heldItem)) {
            return "message.zombierool.ammo_crate.hud_hold_weapon";
        }
        if (isWonderWeapon(heldItem, player.serverLevel())) {
            return "message.zombierool.ammo_crate.hud_no_wonder";
        }
        if (!canPlayerPurchase(player, currentWave)) {
            return "message.zombierool.ammo_crate.hud_already_purchased";
        }
        return "message.zombierool.ammo_crate.hud_refill"; 
    }
}