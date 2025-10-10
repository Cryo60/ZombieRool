package net.mcreator.zombierool;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;

import net.mcreator.zombierool.api.IReloadable;
import net.mcreator.zombierool.MysteryBoxManager;

import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.S2CAmmoCratePricePacket;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AmmoCrateManager extends SavedData {

    private static final String DATA_NAME = "zombierool_ammo_crate_manager";
    private static final int BASE_COST = 1000;
    private static final int COST_INCREMENT = 500;
    private static final ResourceLocation BUY_SOUND = new ResourceLocation("zombierool", "buy");
    
    // Stocke le nombre d'utilisations TOTAL par joueur (UUID -> nombre d'utilisations)
    // Ce compteur n'est JAMAIS réinitialisé - il continue d'augmenter entre les manches
    private final Map<UUID, Integer> playerUsageCount = new ConcurrentHashMap<>();
    
    // Stocke les achats PAR MANCHE (clé: "uuid_wave_X" -> true)
    // Permet de limiter à 1 achat par manche
    private final Map<String, Boolean> playerWavePurchases = new ConcurrentHashMap<>();
    
    // Dernière manche pour laquelle on a nettoyé les achats
    private int lastWaveReset = -1;

    public AmmoCrateManager() {
    }

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
        
        // Charge le compteur d'utilisations total (persiste entre les manches)
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
        
        // Charge les achats par manche
        CompoundTag wavePurchasesTag = nbt.getCompound("PlayerWavePurchases");
        for (String key : wavePurchasesTag.getAllKeys()) {
            manager.playerWavePurchases.put(key, wavePurchasesTag.getBoolean(key));
        }
        
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        compound.putInt("LastWaveReset", lastWaveReset);
        
        // Sauvegarde le compteur d'utilisations total
        CompoundTag usageTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : playerUsageCount.entrySet()) {
            usageTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        compound.put("PlayerUsageCount", usageTag);
        
        // Sauvegarde les achats par manche
        CompoundTag wavePurchasesTag = new CompoundTag();
        for (Map.Entry<String, Boolean> entry : playerWavePurchases.entrySet()) {
            wavePurchasesTag.putBoolean(entry.getKey(), entry.getValue());
        }
        compound.put("PlayerWavePurchases", wavePurchasesTag);
        
        return compound;
    }

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient() {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().level == null) {
            return false;
        }
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    // Helper method for dynamic translation
    private static String getTranslatedMessage(String frenchMessage, String englishMessage) {
        return isEnglishClient() ? englishMessage : frenchMessage;
    }

    /**
     * Nettoie les achats de la manche précédente au début d'une nouvelle manche
     * NE touche PAS au compteur d'utilisations total (playerUsageCount)
     */
    public void checkAndResetForNewWave(int currentWave) {
        if (currentWave != lastWaveReset) {
            // On nettoie uniquement les achats de la manche précédente
            // Le compteur d'utilisations total n'est JAMAIS réinitialisé
            playerWavePurchases.entrySet().removeIf(entry -> 
                entry.getKey().endsWith("_wave_" + lastWaveReset)
            );
            lastWaveReset = currentWave;
            setDirty();
        }
    }

    /**
     * Vérifie si le joueur peut acheter des munitions (une fois par manche)
     */
    public boolean canPlayerPurchase(ServerPlayer player, int currentWave) {
        checkAndResetForNewWave(currentWave);
        
        // Utilise la clé combinée UUID + vague
        String key = player.getUUID().toString() + "_wave_" + currentWave;
        return !playerWavePurchases.containsKey(key);
    }

    /**
     * Calcule le prix pour le joueur basé sur le nombre TOTAL d'achats
     * Ce prix augmente continuellement entre les manches
     */
    public int getPriceForPlayer(UUID playerUUID) {
        int usageCount = playerUsageCount.getOrDefault(playerUUID, 0);
        return BASE_COST + (usageCount * COST_INCREMENT);
    }

    /**
     * Vérifie si l'arme en main est une Wonder Weapon
     */
    private boolean isWonderWeapon(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return false;
        return MysteryBoxManager.WONDER_WEAPONS.contains(stack.getItem());
    }

    /**
     * Vérifie si l'arme en main peut être rechargée
     */
    private boolean isReloadableWeapon(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IReloadable;
    }

    /**
     * Tente d'acheter des munitions
     */
    public boolean tryPurchaseAmmo(ServerPlayer player, ServerLevel level, int currentWave) {
        checkAndResetForNewWave(currentWave);
        
        ItemStack heldItem = player.getMainHandItem();
        
        // Vérifications
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

        IReloadable weapon = (IReloadable) heldItem.getItem();
        
        // Vérifie si les munitions sont déjà pleines
        if (weapon.getReserve(heldItem) >= weapon.getMaxReserve() && 
            weapon.getAmmo(heldItem) >= weapon.getMaxAmmo()) {
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

        // Achète les munitions
        PointManager.modifyScore(player, -cost);
        
        weapon.setAmmo(heldItem, weapon.getMaxAmmo());
        weapon.setReserve(heldItem, weapon.getMaxReserve());
        
        // Enregistre l'utilisation GLOBALE (pour le prix croissant entre les manches)
        playerUsageCount.put(player.getUUID(), playerUsageCount.getOrDefault(player.getUUID(), 0) + 1);
        
        // Enregistre l'achat pour CETTE MANCHE (limite 1 fois/manche)
        String key = player.getUUID().toString() + "_wave_" + currentWave;
        playerWavePurchases.put(key, true);
        
        setDirty();
        
        // Joue le son d'achat
        level.playSound(null, player.blockPosition(), 
            BuiltInRegistries.SOUND_EVENT.get(BUY_SOUND), 
            SoundSource.PLAYERS, 1.0F, 1.0F);
        
        player.sendSystemMessage(Component.literal(getTranslatedMessage(
            "§aMunitions rechargées pour " + cost + " points !",
            "§aAmmo refilled for " + cost + " points!"
        )).withStyle(ChatFormatting.GREEN));
        
        return true;
    }

    /**
	 * Envoie les informations de prix au client
	 */
	public void sendPriceInfoToClient(ServerPlayer player, int currentWave) {
	    String hudMessage = getHudMessage(player, currentWave);
	    int price = getPriceForPlayer(player.getUUID());
	    boolean canPurchase = canPlayerPurchase(player, currentWave);
	    
	    NetworkHandler.INSTANCE.send(
	        PacketDistributor.PLAYER.with(() -> player),
	        new S2CAmmoCratePricePacket(price, canPurchase, hudMessage)
	    );
	}

	/**
	 * Réinitialise toutes les données de l'AmmoCrate (appelé à la fin de la partie)
	 */
	public void resetAllData() {
	    playerUsageCount.clear();
	    playerWavePurchases.clear();
	    lastWaveReset = -1;
	    setDirty();
	}

    /**
     * Obtient le message d'affichage pour le HUD
     */
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