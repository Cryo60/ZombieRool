package me.cryo.zombierool.career;

import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerCareerManager {

    private static final Map<UUID, Map<String, String>> playerEquippedCamos = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, String>> playerEquippedSkins = new ConcurrentHashMap<>();

    public static void setEquippedCamos(ServerPlayer player, Map<String, String> camos) {
        playerEquippedCamos.put(player.getUUID(), new ConcurrentHashMap<>(camos));
        updateInventoryVisuals(player);
    }

    public static void setEquippedSkins(ServerPlayer player, Map<String, String> skins) {
        playerEquippedSkins.put(player.getUUID(), new ConcurrentHashMap<>(skins));
        updateInventoryVisuals(player);
    }

    public static String getEquippedCamo(UUID player, String weaponId) {
        Map<String, String> camos = playerEquippedCamos.get(player);
        if (camos != null) {
            return camos.getOrDefault(weaponId, "");
        }
        return "";
    }

    public static String getEquippedSkin(UUID player, String weaponId) {
        Map<String, String> skins = playerEquippedSkins.get(player);
        if (skins != null) {
            return skins.getOrDefault(weaponId, "");
        }
        return "";
    }

    private static void updateInventoryVisuals(ServerPlayer player) {
        boolean inventoryChanged = false;
        Map<String, String> camos = playerEquippedCamos.getOrDefault(player.getUUID(), new ConcurrentHashMap<>());
        Map<String, String> skins = playerEquippedSkins.getOrDefault(player.getUUID(), new ConcurrentHashMap<>());

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (WeaponFacade.isWeapon(stack)) {
                String wId = WeaponFacade.getWeaponId(stack).replace("zombierool:", "");
                String camo = camos.getOrDefault(wId, "");
                String skin = skins.getOrDefault(wId, "");

                if (!skin.isEmpty()) {
                    String currentSkin = stack.hasTag() ? stack.getTag().getString("zr_skin") : "";
                    if (!skin.equals(currentSkin)) {
                        stack.getOrCreateTag().putString("zr_skin", skin);
                        inventoryChanged = true;
                    }
                    if (stack.hasTag() && stack.getTag().contains("zr_camo")) {
                        stack.getTag().remove("zr_camo");
                        inventoryChanged = true;
                    }
                } else if (!camo.isEmpty()) {
                    String currentCamo = stack.hasTag() ? stack.getTag().getString("zr_camo") : "";
                    if (!camo.equals(currentCamo)) {
                        stack.getOrCreateTag().putString("zr_camo", camo);
                        inventoryChanged = true;
                    }
                    if (stack.hasTag() && stack.getTag().contains("zr_skin")) {
                        stack.getTag().remove("zr_skin");
                        inventoryChanged = true;
                    }
                } else {
                    if (stack.hasTag() && stack.getTag().contains("zr_camo")) {
                        stack.getTag().remove("zr_camo");
                        inventoryChanged = true;
                    }
                    if (stack.hasTag() && stack.getTag().contains("zr_skin")) {
                        stack.getTag().remove("zr_skin");
                        inventoryChanged = true;
                    }
                }
            }
        }
        if (inventoryChanged) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    public static void clearPlayer(UUID player) {
        playerEquippedCamos.remove(player);
        playerEquippedSkins.remove(player);
    }
}