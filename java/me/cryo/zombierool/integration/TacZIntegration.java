package me.cryo.zombierool.integration;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.handlers.LethalWeaponManager;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.init.ZombieroolModSounds;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TacZIntegration {

    private static boolean initialized = false;
    private static boolean hasSyncedServer = false;
    private static boolean hasSyncedClient = false;

    private static final Map<UUID, Map<Item, Integer>> lastTickPhysicalAmmo = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> syncLockTicks = new ConcurrentHashMap<>();

    @Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onCommonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
            event.enqueueWork(TacZIntegration::init);
        }
    }

    public static boolean equipWallAttachment(Player player, ItemStack attachment) {
        return ModList.get().isLoaded("tacz") && TacZImpl.equipWallAttachment(player, attachment);
    }
    public static void init() {
        if (ModList.get().isLoaded("tacz")) {
            TacZImpl.init();
            initialized = true;
            ZombieroolMod.LOGGER.info("[ZR] TacZ hooks initialized successfully (Mixin Mode).");
        }
    }

    public static boolean isTaczGunAvailable(String gunIdStr) {
        if (!ModList.get().isLoaded("tacz")) return false;
        try {
            ResourceLocation gunId = new ResourceLocation(gunIdStr);
            return com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    public static void applyTaczPap(ItemStack stack, WeaponSystem.Definition def) {
        if (ModList.get().isLoaded("tacz")) {
            TacZImpl.applyTaczPap(stack, def);
        }
    }

    public static void applyDefaultAttachments(ItemStack stack, WeaponSystem.Definition def) {
        if (ModList.get().isLoaded("tacz")) {
            TacZImpl.applyDefaultAttachments(stack, def);
        }
    }

    public static void generateMappingTemplate(ResourceLocation taczId, WeaponSystem.Definition def) {
        if (ModList.get().isLoaded("tacz")) {
            TacZImpl.generateMappingTemplate(taczId, def);
        }
    }

    public static void syncTaczGunData() {
        if (ModList.get().isLoaded("tacz")) {
            TacZImpl.syncTaczGunData();
        }
    }

    public static List<ResourceLocation> getAllTacZGunIds() {
        if (ModList.get().isLoaded("tacz")) {
            return TacZImpl.getAllTacZGunIds();
        }
        return new ArrayList<>();
    }

    public static int getTacZWeaponBaseAmmo(ItemStack stack) {
        if (ModList.get().isLoaded("tacz")) {
            return TacZImpl.getTacZWeaponBaseAmmo(stack);
        }
        return 30;
    }

    public static int getTacZWeaponMaxAmmo(ItemStack stack, WeaponSystem.Definition def) {
        if (ModList.get().isLoaded("tacz")) {
            return TacZImpl.getTacZWeaponMaxAmmo(stack, def);
        }
        return 30;
    }

    public static int getTacZWeaponMaxReserve(ItemStack stack, WeaponSystem.Definition def) {
        if (ModList.get().isLoaded("tacz")) {
            return TacZImpl.getTacZWeaponMaxReserve(stack, def);
        }
        return 120;
    }

    public static Item getAmmoItemForGun(ItemStack stack) {
        if (ModList.get().isLoaded("tacz")) {
            return TacZImpl.getAmmoItemForGun(stack);
        }
        return null;
    }

    public static void applyUnmappedTaczProperties(ItemStack stack, ResourceLocation gunId) {
        if (ModList.get().isLoaded("tacz")) {
            TacZImpl.applyUnmappedTaczProperties(stack, gunId);
        }
    }

    public static ResourceLocation getGunIcon(ResourceLocation gunId) {
        if (ModList.get().isLoaded("tacz")) {
            return TacZImpl.getGunIcon(gunId);
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!initialized) return;
        if (!hasSyncedServer && ModList.get().isLoaded("tacz")) {
            syncTaczGunData();
            hasSyncedServer = true;
        }
    }

    @SubscribeEvent
    public static void onTagsUpdated(net.minecraftforge.event.TagsUpdatedEvent event) {
        hasSyncedServer = false;
        hasSyncedClient = false;
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (isDummyAmmo(event.getEntity().getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        ItemStack s = event.getItem().getItem();
        if (isDummyAmmo(s)) {
            event.setCanceled(true);
            event.getItem().discard();
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!initialized || event.phase != TickEvent.Phase.END) return;
        if (!event.player.level().isClientSide) {
            syncDummyAmmo(event.player);
        }
    }

    public static void markSyncLock(Player player, int ticks) {
        syncLockTicks.put(player.getUUID(), ticks);
    }

    public static void syncDummyAmmo(Player player) {
        if (!initialized || player.level().isClientSide) return;
        UUID pid = player.getUUID();
        Map<Item, Integer> currentPhysical = buildPhysicalSnapshot(player);

        int lock = syncLockTicks.getOrDefault(pid, 0);
        if (lock > 0) {
            syncLockTicks.put(pid, lock - 1);
            if (lock == 1) {
                syncLockTicks.remove(pid);
            }
        } else {
            Map<Item, Integer> lastPhysical = lastTickPhysicalAmmo.getOrDefault(pid, new HashMap<>());
            for (Map.Entry<Item, Integer> entry : lastPhysical.entrySet()) {
                Item ammoItem = entry.getKey();
                int lastAmount = entry.getValue();
                int nowAmount = currentPhysical.getOrDefault(ammoItem, 0);
                if (nowAmount < lastAmount) {
                    deductReserveFromGuns(player, ammoItem, lastAmount - nowAmount);
                }
            }
        }

        Map<Item, Integer> targetReserves = buildTargetReserves(player);
        for (Map.Entry<Item, Integer> entry : targetReserves.entrySet()) {
            Item ammoItem = entry.getKey();
            int target = entry.getValue();
            int current = currentPhysical.getOrDefault(ammoItem, 0);
            if (current != target) {
                setDummyAmmo(player, ammoItem, target);
            }
        }

        for (Item ammoItem : currentPhysical.keySet()) {
            if (!targetReserves.containsKey(ammoItem)) {
                setDummyAmmo(player, ammoItem, 0);
            }
        }

        lastTickPhysicalAmmo.put(pid, buildPhysicalSnapshot(player));
    }

    private static Map<Item, Integer> buildPhysicalSnapshot(Player player) {
        Map<Item, Integer> map = new HashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (isDummyAmmo(s)) {
                map.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }
        return map;
    }

    private static Map<Item, Integer> buildTargetReserves(Player player) {
        Map<Item, Integer> map = new HashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!me.cryo.zombierool.core.system.WeaponFacade.isTaczWeapon(s)) continue;
            Item ammoItem = getAmmoItemForGun(s);
            if (ammoItem == null || ammoItem == Items.AIR) continue;
            int reserve = me.cryo.zombierool.core.system.WeaponFacade.getReserve(s);
            map.merge(ammoItem, reserve, Integer::sum);
        }
        return map;
    }

    private static boolean isDummyAmmo(ItemStack s) {
        return !s.isEmpty() && s.hasTag() && s.getTag().getBoolean("zombierool:dummy_ammo");
    }

    private static void deductReserveFromGuns(Player player, Item ammoItem, int amountToDeduct) {
        if (amountToDeduct <= 0) return;
        ItemStack held = player.getMainHandItem();
        if (me.cryo.zombierool.core.system.WeaponFacade.isTaczWeapon(held) && getAmmoItemForGun(held) == ammoItem) {
            int reserve = me.cryo.zombierool.core.system.WeaponFacade.getReserve(held);
            int deduct = Math.min(reserve, amountToDeduct);
            me.cryo.zombierool.core.system.WeaponFacade.setReserve(held, Math.max(0, reserve - deduct));
            amountToDeduct -= deduct;
        }
        if (amountToDeduct > 0) {
            for (int i = 0; i < player.getInventory().getContainerSize() && amountToDeduct > 0; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s == held || !me.cryo.zombierool.core.system.WeaponFacade.isTaczWeapon(s)) continue;
                if (getAmmoItemForGun(s) != ammoItem) continue;
                int reserve = me.cryo.zombierool.core.system.WeaponFacade.getReserve(s);
                int deduct = Math.min(reserve, amountToDeduct);
                me.cryo.zombierool.core.system.WeaponFacade.setReserve(s, Math.max(0, reserve - deduct));
                amountToDeduct -= deduct;
            }
        }
    }

    private static int setDummyAmmo(Player player, Item ammoItem, int amount) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == ammoItem && isDummyAmmo(s)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }

        if (amount <= 0) return 0;

        int remaining = amount;
        int maxStack = Math.max(1, ammoItem.getMaxStackSize(new ItemStack(ammoItem)));

        for (int i = 9; i < player.getInventory().items.size() && remaining > 0; i++) {
            if (player.getInventory().items.get(i).isEmpty()) {
                int toAdd = Math.min(remaining, maxStack);
                player.getInventory().setItem(i, makeDummy(ammoItem, toAdd));
                remaining -= toAdd;
            }
        }
        for (int i = 0; i < 9 && remaining > 0; i++) {
            if (player.getInventory().items.get(i).isEmpty()) {
                int toAdd = Math.min(remaining, maxStack);
                player.getInventory().setItem(i, makeDummy(ammoItem, toAdd));
                remaining -= toAdd;
            }
        }
        if (remaining > 0) {
            for (int slot = 35; slot >= 9 && remaining > 0; slot--) {
                ItemStack old = player.getInventory().getItem(slot);
                if (!old.isEmpty() && !isDummyAmmo(old)) {
                    player.drop(old.copy(), false);
                }
                int toAdd = Math.min(remaining, maxStack);
                player.getInventory().setItem(slot, makeDummy(ammoItem, toAdd));
                remaining -= toAdd;
            }
        }
        return amount - remaining;
    }

    private static ItemStack makeDummy(Item item, int count) {
        ItemStack s = new ItemStack(item, count);
        s.getOrCreateTag().putBoolean("zombierool:dummy_ammo", true);
        return s;
    }

    public static void onPlayerLeave(UUID playerId) {
        lastTickPhysicalAmmo.remove(playerId);
        syncLockTicks.remove(playerId);
    }

    @Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientHooks {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            net.minecraft.world.entity.player.Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                if (!hasSyncedClient && ModList.get().isLoaded("tacz")) {
                    syncTaczGunData();
                    hasSyncedClient = true;
                }
            }
        }

        @SubscribeEvent
        public static void onClientLogOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            hasSyncedClient = false;
        }

        @SubscribeEvent
        public static void onScreenOpen(net.minecraftforge.client.event.ScreenEvent.Opening event) {
            if (event.getScreen() != null) {
                String screenName = event.getScreen().getClass().getSimpleName();
                if (screenName.contains("GunRefitScreen") || screenName.contains("AttachmentScreen")) {
                    net.minecraft.world.entity.player.Player player = net.minecraft.client.Minecraft.getInstance().player;
                    if (player != null) {
                        net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
                        if (me.cryo.zombierool.core.system.WeaponFacade.isTaczWeapon(stack)) {
                            me.cryo.zombierool.core.system.WeaponSystem.Definition def = me.cryo.zombierool.core.system.WeaponFacade.getDefinition(stack);
                            if (def != null) {
                                event.setCanceled(true);
                                player.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable("message.zombierool.refit_disabled")
                                        .withStyle(net.minecraft.ChatFormatting.RED), 
                                    true
                                );
                            }
                        }
                    }
                }
            }
        }

        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
            net.minecraft.world.item.ItemStack stack = event.getItemStack();
            if (me.cryo.zombierool.core.system.WeaponFacade.isTaczWeapon(stack)) {
                me.cryo.zombierool.core.system.WeaponSystem.Definition def = me.cryo.zombierool.core.system.WeaponFacade.getDefinition(stack);
                if (def != null) {
                    boolean isPap = me.cryo.zombierool.core.system.WeaponFacade.isPackAPunched(stack);
                    String nameKey = isPap && def.pap != null && def.pap.name != null && !def.pap.name.isEmpty() ? def.pap.name : def.name;
                    
                    net.minecraft.network.chat.Component nameComp = net.minecraft.network.chat.Component.translatable(nameKey)
                            .withStyle(isPap ? net.minecraft.ChatFormatting.LIGHT_PURPLE : net.minecraft.ChatFormatting.GREEN);
                    event.getToolTip().clear();
                    event.getToolTip().add(nameComp);

                    if (def.lore != null) {
                        for (String l : def.lore) {
                            event.getToolTip().add(net.minecraft.network.chat.Component.translatable(l).withStyle(net.minecraft.ChatFormatting.GRAY));
                        }
                    }
                    if (def.tags != null && !def.tags.isEmpty()) {
                        event.getToolTip().add(net.minecraft.network.chat.Component.translatable("weapon.zombierool.tags").append(": " + String.join(", ", def.tags)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                    }

                    String originalId = stack.getOrCreateTag().getString("GunId");
                    if (!originalId.isEmpty()) {
                        net.minecraft.resources.ResourceLocation loc = new net.minecraft.resources.ResourceLocation(originalId);
                        event.getToolTip().add(net.minecraft.network.chat.Component.literal(""));
                        event.getToolTip().add(net.minecraft.network.chat.Component.literal("§8[Arme originale : " + loc.getPath().replace("_", " ") + "]"));
                        event.getToolTip().add(net.minecraft.network.chat.Component.literal("§8[Gunpack : " + loc.getNamespace() + "]"));
                    }
                }
            }
        }

        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void onGatherTooltipComponents(net.minecraftforge.client.event.RenderTooltipEvent.GatherComponents event) {
            net.minecraft.world.item.ItemStack stack = event.getItemStack();
            if (me.cryo.zombierool.core.system.WeaponFacade.isTaczWeapon(stack)) {
                event.getTooltipElements().removeIf(element -> element.right().isPresent());
            }
        }
    }
}

