package me.cryo.zombierool.integration;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public static void init() {
        if (ModList.get().isLoaded("tacz")) {
            TacZImpl.init();
            initialized = true;
            ZombieroolMod.LOGGER.info("[ZR] TacZ hooks initialized successfully (Mixin Mode).");
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
                    String name = isPap && def.pap.name != null && !def.pap.name.isEmpty() ? def.pap.name : def.name;
                    net.minecraft.network.chat.Component nameComp = net.minecraft.network.chat.Component.literal((isPap ? "§d" : "§a") + name);
                    
                    event.getToolTip().clear();
                    event.getToolTip().add(nameComp);

                    if (def.lore != null) {
                        for (String l : def.lore) {
                            if (!l.contains("Statistiques import")) {
                                event.getToolTip().add(net.minecraft.network.chat.Component.literal("§7" + l));
                            }
                        }
                    }
                    if (def.tags != null && !def.tags.isEmpty()) {
                        event.getToolTip().add(net.minecraft.network.chat.Component.literal("§8Tags: " + String.join(", ", def.tags)));
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

class TacZImpl {

    public static void init() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(TacZEventHandlers.class);
    }

    public static class TacZEventHandlers {

        private static final java.util.Set<String> OVERRIDE_SOUND_WEAPONS = java.util.Set.of(
            "m40a3", "deagle", "kar98k", "barret", "fg42", "ppsh41", "intervention", "usp45", "m14", "m1garand", "gewehr43"
        );

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onGunReload(com.tacz.guns.api.event.common.GunReloadEvent event) {
            if (event.getLogicalSide().isServer() && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_CHERRY.get())) {
                    net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(3.0);
                    player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box, 
                        e -> e instanceof net.minecraft.world.entity.monster.Monster && e != player)
                        .forEach(e -> {
                            me.cryo.zombierool.core.manager.DamageManager.applyDamage(
                                e, 
                                player.level().damageSources().playerAttack(player), 
                                5.0f
                            );
                            e.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 80, 4
                            ));
                        });
                    
                    ((net.minecraft.server.level.ServerLevel)player.level()).sendParticles(
                        net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, 
                        player.getX(), player.getY() + 1, player.getZ(), 
                        30, 1.5, 1.0, 1.5, 0.1
                    );
                    player.level().playSound(
                        null, 
                        player.blockPosition(), 
                        net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation("zombierool:reloading_with_cherry")), 
                        net.minecraft.sounds.SoundSource.PLAYERS, 
                        1.0f, 1.0f
                    );
                }
            }
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public static void onEntityHurtByGunPre(com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre event) {
            if (event.getLogicalSide().isClient()) return; 

            net.minecraft.world.entity.Entity target = event.getHurtEntity();
            net.minecraft.world.entity.LivingEntity attacker = event.getAttacker();
            boolean headshot = event.isHeadShot();

            if (!(attacker instanceof net.minecraft.server.level.ServerPlayer sp)) return;
            if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) return;

            net.minecraft.world.item.ItemStack gunStack = sp.getMainHandItem();
            if (!me.cryo.zombierool.core.system.WeaponFacade.isTaczWeapon(gunStack)) return;

            me.cryo.zombierool.core.system.WeaponSystem.Definition def = me.cryo.zombierool.core.system.WeaponFacade.getDefinition(gunStack);
            boolean isPap = me.cryo.zombierool.core.system.WeaponFacade.isPackAPunched(gunStack);

            float baseDamage = event.getBaseAmount(); 
            int pelletCount = 1;
            float headshotFlatBonus = 0f;
            boolean canExplodeHead = true;
            float headshotExplosionChance = 0.3f;

            if (def != null) {
                baseDamage = def.stats.damage;
                if (isPap) baseDamage += def.pap.damage_bonus;

                pelletCount = def.ballistics.count;
                if (isPap && def.pap.pellet_count_override > 0) {
                    pelletCount = def.pap.pellet_count_override;
                }

                headshotFlatBonus = def.headshot.base_bonus_damage;
                if (isPap) {
                    headshotFlatBonus += def.headshot.pap_bonus_damage;
                }
                canExplodeHead = def.headshot.can_explode_head;
                headshotExplosionChance = def.headshot.head_explosion_chance;
            }

            if (me.cryo.zombierool.bonuses.BonusManager.isInstaKillActive(sp)) {
                baseDamage = 100000f;
            }
            if (sp.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAPE.get())) {
                baseDamage *= 2.0f;
            }

            float totalDamage = baseDamage;
            
            if (headshot) {
                float globalMultiplier = (def != null && "SNIPER".equalsIgnoreCase(def.type)) ? 3.0f : 2.0f;
                float adjustedFlatBonus = headshotFlatBonus / Math.max(1, pelletCount);
                totalDamage = (baseDamage + adjustedFlatBonus) * globalMultiplier;
            }

            if (def != null && def.id.contains("percepteur")) {
                if (living.getHealth() / living.getMaxHealth() <= 0.05f) {
                    totalDamage += living.getHealth() + 10;
                    int pts = isPap ? 75 : 50; 
                    me.cryo.zombierool.PointManager.modifyScore(sp, pts); 
                    
                    ((net.minecraft.server.level.ServerLevel)sp.level()).sendParticles(
                        net.minecraft.core.particles.ParticleTypes.SOUL, 
                        living.getX(), living.getY() + 1, living.getZ(), 
                        10, 0.2, 0.5, 0.2, 0.1
                    );
                    sp.level().playSound(
                        null, living.blockPosition(), 
                        net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, 
                        net.minecraft.sounds.SoundSource.PLAYERS, 
                        1f, 1f
                    );
                }
            }

            event.setBaseAmount(totalDamage);
            
            if (headshot) {
                event.setHeadshotMultiplier(1.0f); 
            }

            if (def != null && isPap && (def.id.contains("scarh") || def.id.contains("wunderwaffedg2"))) {
                int maxChains = def.id.contains("wunderwaffedg2") ? 24 : (def.stats.penetration + def.pap.penetration_bonus > 0 ? def.stats.penetration + def.pap.penetration_bonus : 3);
                double chainRange = 8.0;
                
                java.util.Set<net.minecraft.world.entity.LivingEntity> hitTargets = new java.util.HashSet<>();
                net.minecraft.world.entity.LivingEntity currentTarget = living;
                net.minecraft.world.phys.Vec3 lastPos = sp.getEyePosition();

                for (int i = 0; i < maxChains; i++) {
                    if (currentTarget == null || !currentTarget.isAlive()) break;
                    hitTargets.add(currentTarget);

                    net.minecraft.world.phys.Vec3 targetCenter = currentTarget.position().add(0, currentTarget.getBbHeight() / 2.0, 0);
                    
                    me.cryo.zombierool.network.packet.WeaponVfxPacket packet = new me.cryo.zombierool.network.packet.WeaponVfxPacket("WUNDERWAFFE", lastPos, targetCenter, isPap, false);
                    me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(
                        net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(() -> sp.level().getChunkAt(sp.blockPosition())), 
                        packet
                    );
                    me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp), 
                        packet
                    );

                    lastPos = targetCenter;

                    if (i > 0) { 
                        me.cryo.zombierool.core.manager.DamageManager.applyDamage(currentTarget, sp.damageSources().playerAttack(sp), totalDamage);
                    }

                    if (def.id.contains("wunderwaffedg2")) {
                        currentTarget.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 9, false, false
                        ));
                    }

                    net.minecraft.world.phys.AABB searchBox = currentTarget.getBoundingBox().inflate(chainRange);
                    java.util.List<net.minecraft.world.entity.LivingEntity> potentialNext = sp.level().getEntitiesOfClass(
                        net.minecraft.world.entity.LivingEntity.class, searchBox, e ->
                            e != sp && e.isAlive() && !hitTargets.contains(e) &&
                            (e instanceof me.cryo.zombierool.entity.ZombieEntity || e instanceof me.cryo.zombierool.entity.CrawlerEntity || e instanceof me.cryo.zombierool.entity.HellhoundEntity || e instanceof me.cryo.zombierool.entity.DummyEntity)
                    );
                    if (potentialNext.isEmpty()) break;
                    
                    final net.minecraft.world.entity.LivingEntity refTarget = currentTarget;
                    potentialNext.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(refTarget)));
                    currentTarget = potentialNext.get(0);
                }
            }

            living.getPersistentData().putBoolean(me.cryo.zombierool.core.manager.DamageManager.GUN_DAMAGE_TAG, true);
            if (headshot) {
                living.getPersistentData().putBoolean(me.cryo.zombierool.core.manager.DamageManager.HEADSHOT_TAG, true);
            } else {
                living.getPersistentData().remove(me.cryo.zombierool.core.manager.DamageManager.HEADSHOT_TAG);
            }

            me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp), 
                new me.cryo.zombierool.network.DisplayHitmarkerPacket()
            );

            if (def != null && def.pap.incendiary && isPap) {
                living.setSecondsOnFire(5);
            }

            boolean willDie = living.getHealth() - totalDamage <= 0;
            if (willDie) {
                if (headshot && canExplodeHead && living.getRandom().nextFloat() <= headshotExplosionChance) {
                    me.cryo.zombierool.core.manager.GoreManager.triggerHeadExplosion(living);
                } else if (!headshot) {
                    me.cryo.zombierool.core.manager.GoreManager.tryDismemberLimb(living, totalDamage);
                }
            } else if (!headshot) {
                me.cryo.zombierool.core.manager.GoreManager.tryDismemberLimb(living, totalDamage);
            }

            if (def != null && def.explosion != null && def.explosion.radius > 0 && (!def.explosion.pap_only || isPap)) {
                float radius = def.explosion.radius + (isPap ? def.pap.explosion_radius_bonus : 0);
                me.cryo.zombierool.ExplosionControl.doCustomExplosion(
                    sp.level(), sp, living.position(), totalDamage, radius, 
                    def.explosion.damage_multiplier, def.explosion.self_damage_multiplier, 
                    def.explosion.self_damage_cap, def.explosion.knockback, 
                    def.explosion.vfx_type, def.explosion.sound, isPap
                );
            }
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public static void onGunFire(com.tacz.guns.api.event.common.GunFireEvent event) {
            net.minecraft.world.entity.LivingEntity shooter = event.getShooter();
            net.minecraft.world.item.ItemStack stack = event.getGunItemStack();

            if (!(shooter instanceof net.minecraft.server.level.ServerPlayer sp)) return;
            if (!me.cryo.zombierool.core.system.WeaponFacade.isTaczWeapon(stack)) return;
            
            me.cryo.zombierool.core.system.WeaponSystem.Definition def = me.cryo.zombierool.core.system.WeaponFacade.getDefinition(stack);
            if (def == null) return;

            boolean isPap = me.cryo.zombierool.core.system.WeaponFacade.isPackAPunched(stack);

            if (OVERRIDE_SOUND_WEAPONS.contains(def.id.replace("zombierool:", ""))) {
                String soundId = isPap ? def.sounds.fire_pap : def.sounds.fire;
                if (soundId != null && !soundId.isEmpty()) {
                    net.minecraft.sounds.SoundEvent sound = net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation(soundId));
                    if (sound != null) {
                        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), sound, net.minecraft.sounds.SoundSource.PLAYERS, 5.0f, 1.0f);
                    }
                }
            }

            if ("RAYGUN".equalsIgnoreCase(def.ballistics.type) || 
                "ROCKET".equalsIgnoreCase(def.ballistics.type) || 
                "PROJECTILE".equalsIgnoreCase(def.ballistics.type)) {
                
                long now = sp.level().getGameTime();
                long lastFire = stack.getOrCreateTag().getLong("zombierool:LastFire");
                int fireRate = def.stats.fire_rate;
                
                if (sp.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAPE.get())) {
                    fireRate = (int)(fireRate * 0.75f);
                }
                
                if (now - lastFire < Math.max(1, fireRate)) return;
                
                stack.getOrCreateTag().putLong("zombierool:LastFire", now);
                
                me.cryo.zombierool.core.system.WeaponFacade.shootCustomTaczProjectile(sp, stack, def);
            }

            if (def.id.contains("m16a4") && isPap) {
                float damage = def.stats.damage * 5.0f;
                net.minecraft.world.entity.projectile.Arrow projectile = new net.minecraft.world.entity.projectile.Arrow(sp.level(), sp);
                projectile.setBaseDamage(0);
                projectile.setPos(sp.getX(), sp.getEyeY() - 0.1, sp.getZ());
                projectile.shootFromRotation(sp, sp.getXRot(), sp.getYRot(), 0.0F, 2.0f, 1.0f);
                projectile.setSilent(true);
                projectile.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
                
                net.minecraft.nbt.CompoundTag nbt = projectile.getPersistentData();
                nbt.putBoolean("zombierool:custom_projectile", true);
                nbt.putFloat("zombierool:damage", damage);
                nbt.putBoolean("zombierool:invisible", true);
                nbt.putBoolean("zombierool:pap", true);
                nbt.putString("zombierool:trail_vfx", "RPG");
                
                nbt.putBoolean("zombierool:explosive", true);
                nbt.putFloat("zr_exp_radius", 3.5f);
                nbt.putFloat("zr_exp_dmg_mult", 1.0f);
                nbt.putFloat("zr_exp_self_mult", 0.25f);
                nbt.putFloat("zr_exp_self_cap", 4.0f);
                nbt.putFloat("zr_exp_kb", 0.8f);
                nbt.putString("zr_exp_vfx", "EXPLOSION");
                nbt.putString("zr_exp_sound", "zombierool:explosion_old");
                
                sp.level().addFreshEntity(projectile);
            }

            if (def.id.contains("thundergun")) {
                float damage = def.stats.damage;
                if (isPap) damage += def.pap.damage_bonus;
                
                double range = def.stats.range > 0 ? def.stats.range : 15.0;
                
                net.minecraft.world.phys.Vec3 eyePos = sp.getEyePosition(1.0f);
                net.minecraft.world.phys.Vec3 lookVec = sp.getViewVector(1.0f);
                
                me.cryo.zombierool.network.packet.WeaponVfxPacket packet = new me.cryo.zombierool.network.packet.WeaponVfxPacket(
                    "THUNDERGUN", eyePos, eyePos.add(lookVec.scale(range)), isPap, false
                );
                me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(() -> sp.level().getChunkAt(sp.blockPosition())), 
                    packet
                );
                me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp), 
                    packet
                );

                net.minecraft.world.phys.AABB box = sp.getBoundingBox().inflate(range);
                java.util.List<net.minecraft.world.entity.LivingEntity> targets = sp.level().getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class, box, e ->
                        e != sp && e.isAlive() &&
                        (e instanceof me.cryo.zombierool.entity.ZombieEntity || e instanceof me.cryo.zombierool.entity.CrawlerEntity || e instanceof me.cryo.zombierool.entity.HellhoundEntity || e instanceof me.cryo.zombierool.entity.DummyEntity)
                );
                
                for (net.minecraft.world.entity.LivingEntity target : targets) {
                    net.minecraft.world.phys.Vec3 toTarget = target.position().subtract(sp.position());
                    if (toTarget.length() > range) continue;
                    
                    if (lookVec.dot(toTarget.normalize()) > 0.5) {
                        me.cryo.zombierool.core.manager.DamageManager.applyDamage(target, sp.damageSources().playerAttack(sp), damage);
                        double kbStrength = isPap ? 3.5 : 2.5;
                        target.setDeltaMovement(lookVec.x * kbStrength, isPap ? 1.5 : 1.0, lookVec.z * kbStrength);
                        target.hurtMarked = true;
                    }
                }
            }
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public static void onGunShoot(com.tacz.guns.api.event.common.GunShootEvent event) {
            if (event.getLogicalSide().isServer()) {
                net.minecraft.world.item.ItemStack stack = event.getGunItemStack();
                me.cryo.zombierool.core.system.WeaponSystem.Definition def = me.cryo.zombierool.core.system.WeaponFacade.getDefinition(stack);
                if (def != null) {
                    boolean isPap = me.cryo.zombierool.core.system.WeaponFacade.isPackAPunched(stack);
                    if ("RAYGUN".equalsIgnoreCase(def.ballistics.type) || 
                        "ROCKET".equalsIgnoreCase(def.ballistics.type) || 
                        "PROJECTILE".equalsIgnoreCase(def.ballistics.type) ||
                        def.id.contains("thundergun") || 
                        (def.id.contains("m16a4") && isPap)) {
                        if (event.isCancelable()) {
                            event.setCanceled(true); 
                        }
                    }
                }
            }
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onExplosionDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
            net.minecraft.world.entity.Entity exploder = event.getExplosion().getExploder();
            if (exploder != null && exploder.getClass().getName().startsWith("com.tacz.guns.")) {
                event.getAffectedBlocks().clear();
            }
        }
    }

    public static void applyTaczPap(net.minecraft.world.item.ItemStack stack, me.cryo.zombierool.core.system.WeaponSystem.Definition def) {
        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("zombierool:pap", true);

        if (def != null && def.pap.name != null && !def.pap.name.isEmpty()) {
            stack.setHoverName(net.minecraft.network.chat.Component.literal("§d" + def.pap.name));
        } else if (def != null) {
            stack.setHoverName(net.minecraft.network.chat.Component.literal("§d" + def.name + " (PAP)"));
        }

        if (def != null && def.tacz != null && def.tacz.pap_gun_id != null && !def.tacz.pap_gun_id.isEmpty()) {
            tag.putString("GunId", def.tacz.pap_gun_id);
        }

        net.minecraft.resources.ResourceLocation gunId = new net.minecraft.resources.ResourceLocation(tag.getString("GunId"));

        String validMag = findValidExtendedMag(gunId);
        if (validMag != null) {
            net.minecraft.nbt.CompoundTag attTag = new net.minecraft.nbt.CompoundTag();
            attTag.putString("id", "tacz:attachment");
            attTag.putByte("Count", (byte)1);
            net.minecraft.nbt.CompoundTag attData = new net.minecraft.nbt.CompoundTag();
            attData.putString("AttachmentId", validMag);
            attTag.put("tag", attData);
            tag.put("AttachmentEXTENDED_MAG", attTag);
        }

        if (def != null && def.tacz != null && def.tacz.pap_attachments != null) {
            for (java.util.Map.Entry<String, String> entry : def.tacz.pap_attachments.entrySet()) {
                String type = entry.getKey().toUpperCase();
                String attId = entry.getValue();
                
                if (com.tacz.guns.util.AllowAttachmentTagMatcher.match(gunId, new net.minecraft.resources.ResourceLocation(attId))) {
                    net.minecraft.nbt.CompoundTag attTag = new net.minecraft.nbt.CompoundTag();
                    attTag.putString("id", "tacz:attachment");
                    attTag.putByte("Count", (byte)1);
                    net.minecraft.nbt.CompoundTag attData = new net.minecraft.nbt.CompoundTag();
                    attData.putString("AttachmentId", attId);
                    attTag.put("tag", attData);
                    tag.put("Attachment" + type, attTag);
                }
            }
        }

        int nativeMax = getTacZWeaponMaxAmmo(stack, def);
        tag.putInt("GunCurrentAmmoCount", nativeMax);
        if (def != null) {
            me.cryo.zombierool.core.system.WeaponFacade.setReserve(stack, def.ammo.max_reserve + def.pap.reserve_bonus);
        } else {
            me.cryo.zombierool.core.system.WeaponFacade.setReserve(stack, nativeMax * 8);
        }
    }

    public static void applyDefaultAttachments(net.minecraft.world.item.ItemStack stack, me.cryo.zombierool.core.system.WeaponSystem.Definition def) {
        if (def != null && def.tacz != null && def.tacz.attachments != null) {
            net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
            for (Map.Entry<String, String> entry : def.tacz.attachments.entrySet()) {
                String type = entry.getKey().toUpperCase();
                String attId = entry.getValue();
                net.minecraft.nbt.CompoundTag attTag = new net.minecraft.nbt.CompoundTag();
                attTag.putString("id", "tacz:attachment");
                attTag.putByte("Count", (byte)1);
                net.minecraft.nbt.CompoundTag attData = new net.minecraft.nbt.CompoundTag();
                attData.putString("AttachmentId", attId);
                attTag.put("tag", attData);
                tag.put("Attachment" + type, attTag);
            }
        }
    }

    private static String findValidExtendedMag(ResourceLocation gunId) {
        for (java.util.Map.Entry<ResourceLocation, com.tacz.guns.resource.index.CommonAttachmentIndex> entry : com.tacz.guns.api.TimelessAPI.getAllCommonAttachmentIndex()) {
            if (entry.getValue().getType() == com.tacz.guns.api.item.attachment.AttachmentType.EXTENDED_MAG) {
                if (com.tacz.guns.util.AllowAttachmentTagMatcher.match(gunId, entry.getKey())) {
                    return entry.getKey().toString();
                }
            }
        }
        return null;
    }

    public static int getTacZWeaponMaxAmmo(net.minecraft.world.item.ItemStack stack, me.cryo.zombierool.core.system.WeaponSystem.Definition def) {
        int maxAmmo = 30;
        int attachmentBonus = 0;
        
        if (stack.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
            ResourceLocation gunId = iGun.getGunId(stack);
            var indexOpt = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId);
            if (indexOpt.isPresent()) {
                maxAmmo = indexOpt.get().getGunData().getAmmoAmount();
            } else if (def != null) {
                maxAmmo = def.ammo.clip_size;
            }
            
            ResourceLocation magId = iGun.getAttachmentId(stack, com.tacz.guns.api.item.attachment.AttachmentType.EXTENDED_MAG);
            if (!com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(magId)) {
                var magIndex = com.tacz.guns.api.TimelessAPI.getCommonAttachmentIndex(magId);
                if (magIndex.isPresent() && magIndex.get().getData() != null) {
                    attachmentBonus = magIndex.get().getData().getExtendedMagLevel();
                }
            }
        } else if (def != null) {
            maxAmmo = def.ammo.clip_size;
        }
        
        return maxAmmo + attachmentBonus;
    }

    public static int getTacZWeaponMaxReserve(net.minecraft.world.item.ItemStack stack, me.cryo.zombierool.core.system.WeaponSystem.Definition def) {
        int baseReserve = def != null ? def.ammo.max_reserve : 120;
        int attachmentBonus = 0;
        
        if (stack.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
            ResourceLocation magId = iGun.getAttachmentId(stack, com.tacz.guns.api.item.attachment.AttachmentType.EXTENDED_MAG);
            if (!com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(magId)) {
                var magIndex = com.tacz.guns.api.TimelessAPI.getCommonAttachmentIndex(magId);
                if (magIndex.isPresent() && magIndex.get().getData() != null) {
                    attachmentBonus = magIndex.get().getData().getExtendedMagLevel();
                }
            }
        }
        
        int totalReserve = baseReserve + (attachmentBonus * 4);
        if (def != null && me.cryo.zombierool.core.system.WeaponFacade.isPackAPunched(stack)) {
            totalReserve += def.pap.reserve_bonus;
        }
        return totalReserve;
    }

    public static List<ResourceLocation> getAllTacZGunIds() {
        List<ResourceLocation> list = new ArrayList<>();
        java.util.Set<java.util.Map.Entry<ResourceLocation, com.tacz.guns.resource.index.CommonGunIndex>> guns = com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex();
        for (java.util.Map.Entry<ResourceLocation, com.tacz.guns.resource.index.CommonGunIndex> entry : guns) {
            list.add(entry.getKey());
        }
        return list;
    }

    public static int getTacZWeaponBaseAmmo(net.minecraft.world.item.ItemStack stack) {
        int maxAmmo = 30;
        int attachmentBonus = 0;
        if (stack.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
            ResourceLocation gunId = iGun.getGunId(stack);
            var indexOpt = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId);
            if (indexOpt.isPresent()) {
                maxAmmo = indexOpt.get().getGunData().getAmmoAmount();
            }
            ResourceLocation magId = iGun.getAttachmentId(stack, com.tacz.guns.api.item.attachment.AttachmentType.EXTENDED_MAG);
            if (!com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(magId)) {
                var magIndex = com.tacz.guns.api.TimelessAPI.getCommonAttachmentIndex(magId);
                if (magIndex.isPresent() && magIndex.get().getData() != null) {
                    attachmentBonus = magIndex.get().getData().getExtendedMagLevel();
                }
            }
        }
        return maxAmmo + attachmentBonus;
    }

    public static Item getAmmoItemForGun(net.minecraft.world.item.ItemStack stack) {
        if (stack.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
            ResourceLocation gunId = iGun.getGunId(stack);
            return com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId)
                    .map(index -> {
                        ResourceLocation ammoId = index.getGunData().getAmmoId();
                        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ammoId);
                    })
                    .orElse(null);
        }
        return null;
    }

    public static void applyUnmappedTaczProperties(net.minecraft.world.item.ItemStack stack, ResourceLocation gunId) {
        com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
            List<com.tacz.guns.api.item.gun.FireMode> fireModes = index.getGunData().getFireModeSet();
            if (fireModes != null && !fireModes.isEmpty()) {
                stack.getOrCreateTag().putString("GunFireMode", fireModes.get(0).name());
            } else {
                stack.getOrCreateTag().putString("GunFireMode", "SEMI");
            }
        });
    }

    public static ResourceLocation getGunIcon(ResourceLocation gunId) {
        try {
            var indexOpt = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId);
            if (indexOpt.isPresent()) {
                Object index = indexOpt.get();
                java.lang.reflect.Method getGunDisplay = index.getClass().getMethod("getGunDisplay");
                Object display = getGunDisplay.invoke(index);
                if (display != null) {
                    java.lang.reflect.Method method = null;
                    try { method = display.getClass().getMethod("getGunIcon"); } catch (Exception e) {}
                    if (method == null) {
                        try { method = display.getClass().getMethod("getIcon"); } catch (Exception e) {}
                    }
                    if (method != null) {
                        ResourceLocation icon = (ResourceLocation) method.invoke(display);
                        if (icon != null) return icon;
                    }
                }
            }
        } catch (Exception e) {}
        
        return new ResourceLocation(gunId.getNamespace(), "gun/icon/" + gunId.getPath());
    }

    public static void generateMappingTemplate(ResourceLocation taczId, me.cryo.zombierool.core.system.WeaponSystem.Definition def) {
        com.tacz.guns.api.TimelessAPI.getCommonGunIndex(taczId).ifPresent(index -> {
            com.tacz.guns.resource.pojo.data.gun.GunData gunData = index.getGunData();
            if (gunData != null) {
                if (gunData.getBulletData() != null) {
                    def.stats.damage = gunData.getBulletData().getDamageAmount();
                }
                def.stats.fire_rate = Math.max(1, 1200 / Math.max(1, gunData.getRoundsPerMinute()));
                def.ammo.clip_size = gunData.getAmmoAmount();
                def.ammo.max_reserve = gunData.getAmmoAmount() * 4;
                def.ammo.reload_time = 40; 
                
                if (gunData.getBurstData() != null && gunData.getBurstData().getCount() > 1) {
                    def.burst.count = gunData.getBurstData().getCount();
                    def.burst.delay = Math.max(1, 1200 / Math.max(1, gunData.getBurstData().getBpm()));
                    def.type = "RIFLE";
                } else if (gunData.getFireModeSet() != null && gunData.getFireModeSet().contains(com.tacz.guns.api.item.gun.FireMode.AUTO)) {
                    def.type = "RIFLE";
                } else {
                    def.type = "PISTOL";
                }
                
                def.pap.name = def.name + " (PAP)";
                def.pap.damage_bonus = def.stats.damage;
                def.pap.clip_bonus = def.ammo.clip_size;
                def.pap.reserve_bonus = def.ammo.max_reserve;
            }
        });
    }

    public static void syncTaczGunData() {
        try {
            java.util.Set<java.util.Map.Entry<ResourceLocation, com.tacz.guns.resource.index.CommonGunIndex>> guns = com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex();
            for (java.util.Map.Entry<ResourceLocation, com.tacz.guns.resource.index.CommonGunIndex> entry : guns) {
                ResourceLocation taczId = entry.getKey();
                com.tacz.guns.resource.index.CommonGunIndex index = entry.getValue();
                
                me.cryo.zombierool.core.system.WeaponSystem.Definition match = null;
                for (me.cryo.zombierool.core.system.WeaponSystem.Definition def : me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.values()) {
                    if (def.tacz != null && taczId.toString().equals(def.tacz.gun_id)) {
                        match = def;
                        break;
                    }
                }
                
                if (match != null) {
                    com.tacz.guns.resource.pojo.data.gun.GunData gunData = index.getGunData();
                    
                    int targetRpm = 1200 / Math.max(1, match.stats.fire_rate);
                    if (gunData.getBolt() == com.tacz.guns.resource.pojo.data.gun.Bolt.MANUAL_ACTION) {
                        targetRpm = 1200; 
                    }
                    
                    setFieldValue(gunData, "roundsPerMinute", targetRpm);
                    setFieldValue(gunData, "ammoAmount", match.ammo.clip_size);
                    
                    List<com.tacz.guns.api.item.gun.FireMode> newFireModes = new ArrayList<>();
                    if (match.burst != null && match.burst.count > 1) {
                        newFireModes.add(com.tacz.guns.api.item.gun.FireMode.BURST);
                        com.tacz.guns.resource.pojo.data.gun.BurstData burstData = gunData.getBurstData();
                        if (burstData == null) {
                            try {
                                burstData = com.tacz.guns.resource.pojo.data.gun.BurstData.class.getDeclaredConstructor().newInstance();
                                setFieldValue(gunData, "burstData", burstData);
                            } catch (Exception e) {}
                        }
                        if (burstData != null) {
                            setFieldValue(burstData, "count", match.burst.count);
                            int burstRpm = 1200 / Math.max(1, match.burst.delay);
                            setFieldValue(burstData, "bpm", burstRpm);
                        }
                    } else if (match.stats.fire_rate <= 5) {
                        newFireModes.add(com.tacz.guns.api.item.gun.FireMode.AUTO);
                    } else {
                        newFireModes.add(com.tacz.guns.api.item.gun.FireMode.SEMI);
                    }
                    setFieldValue(gunData, "fireModeSet", newFireModes);
                    
                    com.tacz.guns.resource.pojo.data.gun.BulletData bulletData = gunData.getBulletData();
                    if (bulletData != null) {
                        setFieldValue(bulletData, "damageAmount", match.stats.damage);
                        setFieldValue(bulletData, "extraDamage", null); 
                    }
                    
                    setFieldValue(gunData, "weight", 0.0f);
                    
                    com.tacz.guns.resource.pojo.data.gun.MoveSpeed moveSpeed = gunData.getMoveSpeed();
                    if (moveSpeed != null) {
                        float mobilityOffset = match.stats.mobility - 1.0f;
                        setFieldValue(moveSpeed, "baseMultiplier", mobilityOffset);
                        setFieldValue(moveSpeed, "aimMultiplier", mobilityOffset - 0.2f);
                        setFieldValue(moveSpeed, "reloadMultiplier", mobilityOffset - 0.1f);
                    }
                }
            }
        } catch (Exception e) {
            me.cryo.zombierool.ZombieroolMod.LOGGER.error("[ZR] Failed to sync TacZ GunData via Reflection", e);
        }
    }

    private static void setFieldValue(Object obj, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = getFieldInHierarchy(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(obj, value);
            }
        } catch (Exception ignored) {}
    }

    private static java.lang.reflect.Field getFieldInHierarchy(Class<?> clazz, String fieldName) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}