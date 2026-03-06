package me.cryo.zombierool.integration;

import me.cryo.zombierool.ExplosionControl;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.core.manager.DamageManager;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TacZIntegration {

	private static boolean initialized = false;
	private static boolean hasSyncedServer = false;
	private static boolean hasSyncedClient = false;
	private static final Map<UUID, Map<Item, Integer>> lastTickPhysicalAmmo = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> syncLockTicks = new ConcurrentHashMap<>();

	@SubscribeEvent
	public static void onCommonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
	    event.enqueueWork(() -> {
	        init();
	    });
	}

	@SuppressWarnings("unchecked")
	public static void init() {
	    if (!ModList.get().isLoaded("tacz")) return;

	    // GunShootEvent hook
	    try {
	        Class<net.minecraftforge.eventbus.api.Event> shootEventClass =
	            (Class<net.minecraftforge.eventbus.api.Event>) Class.forName("com.tacz.guns.api.event.common.GunShootEvent");
	        Method getShootShooter = shootEventClass.getMethod("getShooter");
	        Method getShootGunStack = shootEventClass.getMethod("getGunItemStack");
	        Method getShootSide = shootEventClass.getMethod("getLogicalSide");

	        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, shootEventClass, event -> {
	            try {
	                Object side = getShootSide.invoke(event);
	                LivingEntity shooter = (LivingEntity) getShootShooter.invoke(event);
	                ItemStack stack = (ItemStack) getShootGunStack.invoke(event);
	                if (side.toString().equals("SERVER")) {
	                    handleGunFireTrigger(event, shooter, stack);
	                }
	            } catch (Exception ex) {
	                ZombieroolMod.LOGGER.error("[ZR] Erreur GunShootEvent", ex);
	            }
	        });
	    } catch (Exception e) {
	        ZombieroolMod.LOGGER.error("[ZR] Failed to hook GunShootEvent", e);
	    }

	    // GunFireEvent hook
	    try {
	        Class<net.minecraftforge.eventbus.api.Event> fireEventClass =
	            (Class<net.minecraftforge.eventbus.api.Event>) Class.forName("com.tacz.guns.api.event.common.GunFireEvent");
	        Method getFireShooter = fireEventClass.getMethod("getShooter");
	        Method getFireGunStack = fireEventClass.getMethod("getGunItemStack");
	        Method getFireSide = fireEventClass.getMethod("getLogicalSide");

	        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, fireEventClass, event -> {
	            try {
	                Object side = getFireSide.invoke(event);
	                if (side.toString().equals("CLIENT")) return;
	                LivingEntity shooter = (LivingEntity) getFireShooter.invoke(event);
	                ItemStack stack = (ItemStack) getFireGunStack.invoke(event);
	                handleGunFireTrigger(event, shooter, stack);
	            } catch (Exception ex) {
	                ZombieroolMod.LOGGER.error("[ZR] Erreur GunFireEvent", ex);
	            }
	        });
	    } catch (Exception e) {
	        ZombieroolMod.LOGGER.error("[ZR] Failed to hook GunFireEvent", e);
	    }

	    // EntityHurtByGunEvent.Pre hook
	    try {
	        Class<net.minecraftforge.eventbus.api.Event> hurtPreClass =
	            (Class<net.minecraftforge.eventbus.api.Event>) Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
	        Method getHurtTarget = hurtPreClass.getMethod("getHurtEntity");
	        Method getHurtAttacker = hurtPreClass.getMethod("getAttacker");
	        Method isHeadshot = hurtPreClass.getMethod("isHeadShot");
	        Method setBaseAmount = hurtPreClass.getMethod("setBaseAmount", float.class);
	        Method setHeadshotMultiplier = hurtPreClass.getMethod("setHeadshotMultiplier", float.class);

	        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, hurtPreClass, event -> {
	            try {
	                Entity target = (Entity) getHurtTarget.invoke(event);
	                LivingEntity attacker = (LivingEntity) getHurtAttacker.invoke(event);
	                boolean headshot = (boolean) isHeadshot.invoke(event);
	                
	                if (!(attacker instanceof ServerPlayer sp)) return;
	                if (!(target instanceof LivingEntity living)) return;

	                ItemStack gunStack = sp.getMainHandItem();
	                if (!WeaponFacade.isTaczWeapon(gunStack)) return;

	                WeaponSystem.Definition def = WeaponFacade.getDefinition(gunStack);
	                if (def == null) return;

	                float baseDamage = def.stats.damage;
	                if (WeaponFacade.isPackAPunched(gunStack)) {
						baseDamage += def.pap.damage_bonus;
					}
	                
	                if (BonusManager.isInstaKillActive(sp)) {
	                    baseDamage = 100000f;
	                }

	                setBaseAmount.invoke(event, baseDamage);

	                if (headshot) {
	                    float globalMultiplier = 2.0f;
	                    float flatBonus = 0.0f;
	                    if ("SNIPER".equalsIgnoreCase(def.type)) {
	                        globalMultiplier = 3.0f;
	                    }
	                    flatBonus += def.headshot.base_bonus_damage;
	                    if (WeaponFacade.isPackAPunched(gunStack)) {
	                        flatBonus += def.headshot.pap_bonus_damage;
	                    }
	                    float finalDamage = (baseDamage + flatBonus) * globalMultiplier;
	                    float multiplier = baseDamage > 0 ? finalDamage / baseDamage : 1.0f;
	                    setHeadshotMultiplier.invoke(event, multiplier);
	                } else {
	                    setHeadshotMultiplier.invoke(event, 1.0f);
	                }

	                living.getPersistentData().putBoolean(DamageManager.GUN_DAMAGE_TAG, true);
	                if (headshot) living.getPersistentData().putBoolean(DamageManager.HEADSHOT_TAG, true);
	                else living.getPersistentData().remove(DamageManager.HEADSHOT_TAG);

	                me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(
	                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
	                    new me.cryo.zombierool.network.DisplayHitmarkerPacket()
	                );

	                if (def.pap.incendiary && WeaponFacade.isPackAPunched(gunStack)) {
	                    living.setSecondsOnFire(5);
	                }

	                if (def.explosion != null && def.explosion.radius > 0 && (!def.explosion.pap_only || WeaponFacade.isPackAPunched(gunStack))) {
	                    float radius = def.explosion.radius + (WeaponFacade.isPackAPunched(gunStack) ? def.pap.explosion_radius_bonus : 0);
	                    ExplosionControl.doCustomExplosion(
	                        sp.level(), sp, living.position(),
	                        baseDamage, radius,
	                        def.explosion.damage_multiplier,
	                        def.explosion.self_damage_multiplier,
	                        def.explosion.self_damage_cap,
	                        def.explosion.knockback,
	                        def.explosion.vfx_type,
	                        def.explosion.sound,
	                        WeaponFacade.isPackAPunched(gunStack)
	                    );
	                }

	            } catch (Exception ex) {
	                ZombieroolMod.LOGGER.error("[ZR] Erreur EntityHurtByGunEvent.Pre", ex);
	            }
	        });
	    } catch (Exception e) {
	        ZombieroolMod.LOGGER.error("[ZR] Failed to hook EntityHurtByGunEvent.Pre", e);
	    }
	    
	    // EntityKillByGunEvent hook
	    try {
	        Class<net.minecraftforge.eventbus.api.Event> killEventClass =
	            (Class<net.minecraftforge.eventbus.api.Event>) Class.forName("com.tacz.guns.api.event.common.EntityKillByGunEvent");
	        Method getKillTarget = killEventClass.getMethod("getKilledEntity");
	        Method getKillAttacker = killEventClass.getMethod("getAttacker");
	        Method isKillHeadshot = killEventClass.getMethod("isHeadShot");

	        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, killEventClass, event -> {
	            try {
	                LivingEntity target = (LivingEntity) getKillTarget.invoke(event);
	                LivingEntity attacker = (LivingEntity) getKillAttacker.invoke(event);
	                boolean headshot = (boolean) isKillHeadshot.invoke(event);
	                
	                if (attacker instanceof ServerPlayer sp) {
	                    ItemStack gunStack = sp.getMainHandItem();
	                    WeaponSystem.Definition def = WeaponFacade.getDefinition(gunStack);
	                    if (def != null && target != null) {
	                        if (headshot && def.headshot.can_explode_head && target.getRandom().nextFloat() <= def.headshot.head_explosion_chance) {
	                            me.cryo.zombierool.core.manager.GoreManager.triggerHeadExplosion(target);
	                        } else if (!headshot) {
	                            me.cryo.zombierool.core.manager.GoreManager.tryDismemberLimb(target, def.stats.damage);
	                        }
	                    }
	                }
	            } catch (Exception ex) {
	                ZombieroolMod.LOGGER.error("[ZR] Erreur EntityKillByGunEvent", ex);
	            }
	        });
	    } catch (Exception e) {
	        ZombieroolMod.LOGGER.error("[ZR] Failed to hook EntityKillByGunEvent", e);
	    }

	    initialized = true;
	    ZombieroolMod.LOGGER.info("[ZR] TacZ hooks initialized successfully.");
	}

	private static void handleGunFireTrigger(net.minecraftforge.eventbus.api.Event event, LivingEntity shooter, ItemStack stack) {
	    if (!(shooter instanceof ServerPlayer sp)) return;
	    if (!WeaponFacade.isTaczWeapon(stack)) return;

	    WeaponSystem.Definition def = WeaponFacade.getDefinition(stack);
	    if (def == null) return;

	    if (isExplosiveType(def.ballistics.type)) {
	        if (event.isCancelable()) {
	            event.setCanceled(true);
	        }
	        long now = sp.level().getGameTime();
	        long lastFire = stack.getOrCreateTag().getLong("zombierool:LastFire");

	        int fireRate = def.stats.fire_rate;
	        if (sp.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAPE.get())) {
	            fireRate = Math.max(1, (int)(fireRate * 0.75f));
	        }

	        if (now - lastFire < fireRate) return;

	        int currentAmmo = WeaponFacade.getAmmo(stack);
	        if (currentAmmo <= 0 && !sp.isCreative()) return;

	        stack.getOrCreateTag().putLong("zombierool:LastFire", now);
	        if (!sp.isCreative()) WeaponFacade.setAmmo(stack, currentAmmo - 1);

	        WeaponFacade.shootCustomTaczProjectile(sp, stack, def);
	    }
	}

	private static boolean isExplosiveType(String type) {
	    return "ROCKET".equalsIgnoreCase(type) || "PROJECTILE".equalsIgnoreCase(type) || "RAYGUN".equalsIgnoreCase(type);
	}

	public static void syncTaczGunData() {
	    if (!ModList.get().isLoaded("tacz")) return;

	    try {
	        Class<?> apiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
	        Object setObj = apiClass.getMethod("getAllCommonGunIndex").invoke(null);

	        if (setObj instanceof Set<?> set) {
	            for (Object entry : set) {
	                if (entry instanceof Map.Entry<?, ?> mapEntry) {
	                    ResourceLocation taczId = (ResourceLocation) mapEntry.getKey();
	                    Object index = mapEntry.getValue();

	                    WeaponSystem.Definition match = null;
	                    for (WeaponSystem.Definition def : WeaponSystem.Loader.LOADED_DEFINITIONS.values()) {
	                        if (def.tacz != null && taczId.toString().equals(def.tacz.gun_id)) {
	                            match = def;
	                            break;
	                        }
	                    }

	                    if (match != null) {
	                        Object gunData = index.getClass().getMethod("getGunData").invoke(index);

	                        // Sync Fire Rate (RPM)
	                        int targetRpm = 1200 / Math.max(1, match.stats.fire_rate);
	                        setFieldValue(gunData, "roundsPerMinute", targetRpm);

	                        // Sync Burst Data
	                        if (match.burst != null && match.burst.count > 1) {
	                            Object burstData = getFieldValue(gunData, "burstData");
	                            if (burstData != null) {
	                                setFieldValue(burstData, "count", match.burst.count);
	                                int burstRpm = 1200 / Math.max(1, match.burst.delay);
	                                setFieldValue(burstData, "bpm", burstRpm);
	                            }
	                        }

	                        // Sync Damage
	                        Object bulletData = getFieldValue(gunData, "bulletData");
	                        if (bulletData != null) {
	                            setFieldValue(bulletData, "damageAmount", match.stats.damage);
	                            setFieldValue(bulletData, "extraDamage", null); 
	                        }

	                        // Sync Mobility
	                        setFieldValue(gunData, "weight", 0.0f);
	                        Object moveSpeed = getFieldValue(gunData, "moveSpeed");
	                        if (moveSpeed != null) {
	                            float mobilityOffset = match.stats.mobility - 1.0f;
	                            setFieldValue(moveSpeed, "baseMultiplier", mobilityOffset);
	                            setFieldValue(moveSpeed, "aimMultiplier", mobilityOffset - 0.2f);
	                            setFieldValue(moveSpeed, "reloadMultiplier", mobilityOffset - 0.1f);
	                        }
	                    }
	                }
	            }
	        }
	    } catch (Exception e) {
	        ZombieroolMod.LOGGER.error("[ZR] Failed to sync TacZ GunData", e);
	    }
	}

	private static void setFieldValue(Object obj, String fieldName, Object value) {
	    try {
	        Field field = getFieldInHierarchy(obj.getClass(), fieldName);
	        if (field != null) {
	            field.setAccessible(true);
	            field.set(obj, value);
	        }
	    } catch (Exception e) {
	    }
	}

	private static Object getFieldValue(Object obj, String fieldName) {
	    try {
	        Field field = getFieldInHierarchy(obj.getClass(), fieldName);
	        if (field != null) {
	            field.setAccessible(true);
	            return field.get(obj);
	        }
	    } catch (Exception e) {
	    }
	    return null;
	}

	private static Field getFieldInHierarchy(Class<?> clazz, String fieldName) {
	    while (clazz != null && clazz != Object.class) {
	        try {
	            return clazz.getDeclaredField(fieldName);
	        } catch (NoSuchFieldException e) {
	            clazz = clazz.getSuperclass();
	        }
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
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
	    if (!initialized) return;
	    if (event.phase != TickEvent.Phase.END) return;

	    Player player = event.player;
	    if (!player.level().isClientSide) {
	        syncDummyAmmo(player);
	    }
	}

	@SubscribeEvent
	public static void onTagsUpdated(net.minecraftforge.event.TagsUpdatedEvent event) {
	    hasSyncedServer = false;
	    hasSyncedClient = false;
	}

	@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
	public static class ClientHooks {
	    @SubscribeEvent
	    public static void onClientTick(TickEvent.ClientTickEvent event) {
	        if (!hasSyncedClient && ModList.get().isLoaded("tacz")) {
	            syncTaczGunData();
	            hasSyncedClient = true;
	        }
	    }

	    @SubscribeEvent
	    public static void onClientLogOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
	        hasSyncedClient = false;
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
	        if (lock == 1) syncLockTicks.remove(pid);
	    } else {
	        Map<Item, Integer> lastPhysical = lastTickPhysicalAmmo.getOrDefault(pid, new HashMap<>());
	        for (Map.Entry<Item, Integer> entry : lastPhysical.entrySet()) {
	            Item ammoItem   = entry.getKey();
	            int  lastAmount = entry.getValue();
	            int  nowAmount  = currentPhysical.getOrDefault(ammoItem, 0);
	            if (nowAmount < lastAmount) {
	                deductReserveFromGuns(player, ammoItem, lastAmount - nowAmount);
	            }
	        }
	    }

	    Map<Item, Integer> targetReserves = buildTargetReserves(player);

	    for (Map.Entry<Item, Integer> entry : targetReserves.entrySet()) {
	        Item ammoItem = entry.getKey();
	        int  target   = entry.getValue();
	        int  current  = currentPhysical.getOrDefault(ammoItem, 0);

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
	        if (isDummyAmmo(s)) map.merge(s.getItem(), s.getCount(), Integer::sum);
	    }
	    return map;
	}

	private static Map<Item, Integer> buildTargetReserves(Player player) {
	    Map<Item, Integer> map = new HashMap<>();
	    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
	        ItemStack s = player.getInventory().getItem(i);
	        if (!WeaponFacade.isTaczWeapon(s)) continue;

	        Item ammoItem = WeaponFacade.getAmmoItemForGun(s);
	        if (ammoItem == null || ammoItem == Items.AIR) continue;

	        int reserve = WeaponFacade.getReserve(s);
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
	    if (WeaponFacade.isTaczWeapon(held) && WeaponFacade.getAmmoItemForGun(held) == ammoItem) {
	        int reserve = WeaponFacade.getReserve(held);
	        int deduct  = Math.min(reserve, amountToDeduct);
	        WeaponFacade.setReserve(held, reserve - deduct);
	        amountToDeduct -= deduct;
	    }
	    if (amountToDeduct > 0) {
	        for (int i = 0; i < player.getInventory().getContainerSize() && amountToDeduct > 0; i++) {
	            ItemStack s = player.getInventory().getItem(i);
	            if (s == held || !WeaponFacade.isTaczWeapon(s)) continue;
	            if (WeaponFacade.getAmmoItemForGun(s) != ammoItem) continue;

	            int reserve = WeaponFacade.getReserve(s);
	            int deduct  = Math.min(reserve, amountToDeduct);
	            WeaponFacade.setReserve(s, reserve - deduct);
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
	    int maxStack  = Math.max(1, ammoItem.getMaxStackSize(new ItemStack(ammoItem)));

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
	            if (!old.isEmpty() && !isDummyAmmo(old)) player.drop(old.copy(), false);

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

	@SubscribeEvent
	public static void onItemToss(ItemTossEvent event) {
	    if (isDummyAmmo(event.getEntity().getItem())) event.setCanceled(true);
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
	public static void onExplosionDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
	    event.getAffectedBlocks().clear();
	}

	public static void onPlayerLeave(UUID playerId) {
	    lastTickPhysicalAmmo.remove(playerId);
	    syncLockTicks.remove(playerId);
	}

	public static ResourceLocation getAmmoIdForGun(ItemStack gunStack) {
	    if (!ModList.get().isLoaded("tacz")) return null;
	    try {
	        Class<?> iGun = Class.forName("com.tacz.guns.api.item.IGun");
	        if (!iGun.isInstance(gunStack.getItem())) return null;

	        ResourceLocation gunId = (ResourceLocation) iGun.getMethod("getGunId", ItemStack.class).invoke(gunStack.getItem(), gunStack);

	        Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
	        Optional<?> opt = (Optional<?>) api.getMethod("getCommonGunIndex", ResourceLocation.class).invoke(null, gunId);
	        if (opt.isPresent()) {
	            Object index = opt.get();
	            Object gunData = index.getClass().getMethod("getGunData").invoke(index);
	            return (ResourceLocation) gunData.getClass().getMethod("getAmmoId").invoke(gunData);
	        }
	    } catch (Exception ignored) {}
	    return null;
	}

	public static List<ResourceLocation> getAllTacZGunIds() {
	    List<ResourceLocation> list = new ArrayList<>();
	    if (!ModList.get().isLoaded("tacz")) return list;
	    try {
	        Class<?> apiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
	        Object setObj = apiClass.getMethod("getAllCommonGunIndex").invoke(null);
	        if (setObj instanceof Set<?> set) {
	            for (Object entry : set) {
	                if (entry instanceof Map.Entry<?, ?> mapEntry) {
	                    if (mapEntry.getKey() instanceof ResourceLocation rl) {
	                        list.add(rl);
	                    }
	                }
	            }
	        }
	    } catch (Exception e) {
	        ZombieroolMod.LOGGER.error("[ZR] Failed to retrieve TacZ gun indices", e);
        }
        return list;
	}
}