package me.cryo.zombierool;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
	private static final int BASE_COST = 1000;
	private static final int COST_INCREMENT = 500;
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
	private static boolean isEnglishClient() {
	    if (Minecraft.getInstance() == null || Minecraft.getInstance().level == null) {
	        return false;
	    }
	    return Minecraft.getInstance().options.languageCode.startsWith("en");
	}
	private static String getTranslatedMessage(String frenchMessage, String englishMessage) {
	    return isEnglishClient() ? englishMessage : frenchMessage;
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
	    return BASE_COST + (usageCount * COST_INCREMENT);
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
	        player.sendSystemMessage(Component.literal(getTranslatedMessage(
	            "§cVous devez tenir une arme rechargeable !",
	            "§cYou must be holding a reloadable weapon!"
	        )).withStyle(ChatFormatting.RED));
	        return false;
	    }
	    if (isWonderWeapon(heldItem, level)) {
	        player.sendSystemMessage(Component.literal(getTranslatedMessage(
	            "§cLes Wonder Weapons ne peuvent pas être rechargées ici !",
	            "§cWonder Weapons cannot be refilled here!"
	        )).withStyle(ChatFormatting.RED));
	        return false;
	    }
	    if (!canPlayerPurchase(player, currentWave)) {
	        player.sendSystemMessage(Component.literal(getTranslatedMessage(
	            "§cVous avez déjà acheté des munitions cette manche !",
	            "§cYou already purchased ammo this wave!"
	        )).withStyle(ChatFormatting.RED));
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
	        player.sendSystemMessage(Component.literal(getTranslatedMessage(
	            "§cVos munitions sont déjà pleines !",
	            "§cYour ammo is already full!"
	        )).withStyle(ChatFormatting.YELLOW));
	        return false;
	    }
	    int cost = getPriceForPlayer(player.getUUID());
	    if (PointManager.getScore(player) < cost) {
	        player.sendSystemMessage(Component.literal(getTranslatedMessage(
	            "§cPas assez de points ! (" + cost + " points requis)",
	            "§cNot enough points! (" + cost + " points required)"
	        )).withStyle(ChatFormatting.RED));
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
	    player.sendSystemMessage(Component.literal(getTranslatedMessage(
	        "§aMunitions rechargées pour " + cost + " points !",
	        "§aAmmo refilled for " + cost + " points!"
	    )).withStyle(ChatFormatting.GREEN));
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
	        return getTranslatedMessage(
	            "§cVous devez tenir une arme rechargeable",
	            "§cYou must hold a reloadable weapon"
	        );
	    }
	    if (isWonderWeapon(heldItem, player.serverLevel())) {
	        return getTranslatedMessage(
	            "§cLes Wonder Weapons ne peuvent pas être rechargées",
	            "§cWonder Weapons cannot be refilled"
	        );
	    }
	    if (!canPlayerPurchase(player, currentWave)) {
	        return getTranslatedMessage(
	            "§cDéjà acheté cette manche",
	            "§cAlready purchased this wave"
	        );
	    }
	    int cost = getPriceForPlayer(player.getUUID());
	    return getTranslatedMessage(
	        "Appuyer sur F pour faire le plein de munitions pour " + cost + " points",
	        "Press F to refill ammo for " + cost + " points"
	    );
	}
}