package me.cryo.zombierool.integration;

import me.cryo.zombierool.ExplosionControl;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.manager.DamageManager;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TacZIntegration {

    private static boolean initialized = false;

    // FIX N°2 — NOUVELLE APPROCHE : on ne stocke que l'état physique du TICK PRÉCÉDENT.
    // Aucun "last_reserve_cache" sur l'ItemStack. La source de vérité est uniquement
    // zombierool:Reserve sur chaque arme TacZ.
    // Si les items physiques ont DIMINUÉ entre deux ticks, c'est que TacZ a rechargé
    // (consommé) des munitions → on déduit de la réserve.
    // Ensuite on FORCE les items physiques à correspondre exactement à la somme des
    // réserves. C'est un recalcul absolu, immunisé aux Max Ammo / Ammo Box.
    private static final Map<UUID, Map<Item, Integer>> lastTickPhysicalAmmo = new ConcurrentHashMap<>();

    // Verrou de synchronisation : quand le code externe (Max Ammo, Ammo Box) vient de
    // modifier Reserve, on saute UNE itération de la déduction pour éviter la race
    // condition. La clé est l'UUID du joueur, la valeur est le nombre de ticks à sauter.
    private static final Map<UUID, Integer> syncLockTicks = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────────
    //  INIT — Réflexion Java pour hooker les events TacZ
    // ─────────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public static void init() {
        if (!ModList.get().isLoaded("tacz")) return;

        try {
            // ── FIX N°3 : Annulation du tir natif TacZ pour les armes explosives ──────
            //
            // IMPORTANT : TacZ gère son animation/son de tir côté CLIENT de façon
            // indépendante via un tick d'input. On ne peut pas annuler l'animation
            // cliente par cet event. Ce qu'on peut faire ici :
            //   1. Annuler le PROJECTILE serveur de TacZ (setCanceled → true).
            //   2. Annuler la consommation de munitions par TacZ (on gère nous-mêmes).
            //   3. Spawner notre propre projectile custom.
            //
            // Si TacZ supporte un event "GunFireClientEvent" ou équivalent, il faudrait
            // aussi le hooker côté client pour masquer le flash de bouche natif.
            // En l'absence de cet event public, l'animation TacZ peut jouer (muzzle
            // flash), mais le PROJECTILE destructeur de blocs n'est jamais créé.

            Class<net.minecraftforge.eventbus.api.Event> shootEventClass =
                (Class<net.minecraftforge.eventbus.api.Event>)
                    Class.forName("com.tacz.guns.api.event.entity.EntityShootGunEvent");

            Method getShootEntity   = shootEventClass.getMethod("getEntity");
            Method getShootGunStack = shootEventClass.getMethod("getGunItemStack");
            Method setShootCanceled = net.minecraftforge.eventbus.api.Event.class.getMethod("setCanceled", boolean.class);

            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, shootEventClass, event -> {
                try {
                    Entity entity   = (Entity)    getShootEntity.invoke(event);
                    ItemStack stack = (ItemStack) getShootGunStack.invoke(event);

                    if (!(entity instanceof Player player)) return;
                    if (!WeaponFacade.isTaczWeapon(stack)) return;

                    WeaponSystem.Definition def = WeaponFacade.getDefinition(stack);

                    // Armes explosives gérées par ZombieRool : on tue le tir TacZ natif.
                    if (def != null && isExplosiveType(def.ballistics.type)) {
                        setShootCanceled.invoke(event, true);

                        if (player instanceof ServerPlayer sp) {
                            // Calcul du fire rate (avec Deadshot/Double Tape)
                            long now       = sp.level().getGameTime();
                            long lastFire  = stack.getOrCreateTag().getLong("zombierool:LastFire");
                            int  fireRate  = def.stats.fire_rate;
                            if (sp.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAPE.get())) {
                                fireRate = Math.max(1, (int)(fireRate * 0.75f));
                            }

                            if (now - lastFire < fireRate) return; // cadence non atteinte

                            int currentAmmo = WeaponFacade.getAmmo(stack);
                            if (currentAmmo <= 0 && !sp.isCreative()) return; // plus de balles

                            // Écriture du timestamp + déduction ammo AVANT spawn pour
                            // éviter un double-tir si l'event est appelé deux fois.
                            stack.getOrCreateTag().putLong("zombierool:LastFire", now);
                            if (!sp.isCreative()) {
                                WeaponFacade.setAmmo(stack, currentAmmo - 1);
                            }

                            WeaponFacade.shootCustomTaczProjectile(sp, stack, def);

                            // Recul
                            float pitch = def.recoil.pitch;
                            float yaw   = def.recoil.yaw;
                            if (WeaponFacade.isPackAPunched(stack)) {
                                pitch *= def.pap.recoil_mult;
                                yaw   *= def.pap.recoil_mult;
                            }
                            me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(
                                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                                new me.cryo.zombierool.network.RecoilPacket(
                                    pitch, (sp.getRandom().nextBoolean() ? 1 : -1) * yaw)
                            );
                        }
                    }
                    // Les armes non-explosives (BULLET, HITSCAN…) : on laisse TacZ tirer
                    // normalement. Les dégâts seront interceptés dans EntityHurtByGunEvent.

                } catch (Exception ex) {
                    ZombieroolMod.LOGGER.error("[ZR] Erreur EntityShootGunEvent", ex);
                }
            });

            // ── FIX N°1 : Interception des dégâts TacZ → Application des règles ZR ──

            Class<net.minecraftforge.eventbus.api.Event> hurtEventClass =
                (Class<net.minecraftforge.eventbus.api.Event>)
                    Class.forName("com.tacz.guns.api.event.entity.EntityHurtByGunEvent");

            Method getHurtTarget   = hurtEventClass.getMethod("getHurtEntity");
            Method getHurtAttacker = hurtEventClass.getMethod("getAttacker");
            Method getHurtStack    = hurtEventClass.getMethod("getGunItemStack");
            Method setHurtCanceled = net.minecraftforge.eventbus.api.Event.class.getMethod("setCanceled", boolean.class);

            // TacZ peut nommer la méthode "isHeadShot" ou "isHeadshot" selon la version.
            Method isHeadshotMethod = resolveHeadshotMethod(hurtEventClass);

            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, hurtEventClass, event -> {
                try {
                    Entity    targetEntity  = (Entity)    getHurtTarget.invoke(event);
                    Entity    attackerEntity= (Entity)    getHurtAttacker.invoke(event);
                    ItemStack gunStack      = (ItemStack) getHurtStack.invoke(event);

                    // On n'intercepte que les attaques de ServerPlayer avec une arme TacZ.
                    if (!(attackerEntity instanceof ServerPlayer sp)) return;
                    if (!(targetEntity instanceof LivingEntity target)) return;
                    if (!WeaponFacade.isTaczWeapon(gunStack)) return;

                    // Annulation du dégât natif de TacZ DANS TOUS LES CAS pour les
                    // armes enregistrées dans ZombieRool.
                    WeaponSystem.Definition def = WeaponFacade.getDefinition(gunStack);
                    if (def == null) return; // Arme TacZ non mappée → on laisse TacZ gérer

                    setHurtCanceled.invoke(event, true);

                    boolean isHeadshot = isHeadshotMethod != null
                        && (boolean) isHeadshotMethod.invoke(event);

                    // Calcul du dégât ZombieRool
                    float baseDamage = def.stats.damage;
                    if (WeaponFacade.isPackAPunched(gunStack)) {
                        baseDamage += def.pap.damage_bonus;
                    }

                    float finalDamage = DamageManager.calculateDamage(sp, target, baseDamage, isHeadshot, gunStack);

                    // Marquage pour le système de points / gore
                    target.getPersistentData().putBoolean(DamageManager.GUN_DAMAGE_TAG, true);
                    if (isHeadshot) {
                        target.getPersistentData().putBoolean(DamageManager.HEADSHOT_TAG, true);
                        if (def.headshot.can_explode_head
                                && target.getRandom().nextFloat() <= def.headshot.head_explosion_chance
                                && target.getHealth() - finalDamage <= 0) {
                            me.cryo.zombierool.core.manager.GoreManager.triggerHeadExplosion(target);
                        }
                    } else {
                        target.getPersistentData().remove(DamageManager.HEADSHOT_TAG);
                    }

                    // Application du dégât
                    if (DamageManager.applyDamage(target, sp.damageSources().playerAttack(sp), finalDamage)) {
                        me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                            new me.cryo.zombierool.network.DisplayHitmarkerPacket()
                        );
                    }

                    // Explosion sur impact (pour les armes qui ont une zone d'effet)
                    if (def.explosion != null && (!def.explosion.pap_only || WeaponFacade.isPackAPunched(gunStack))) {
                        float radius = def.explosion.radius
                            + (WeaponFacade.isPackAPunched(gunStack) ? def.pap.explosion_radius_bonus : 0);
                        ExplosionControl.doCustomExplosion(
                            sp.level(), sp, target.position(),
                            finalDamage, radius,
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
                    ZombieroolMod.LOGGER.error("[ZR] Erreur EntityHurtByGunEvent", ex);
                }
            });

            // ── SÉCURITÉ ABSOLUE : aucun bloc ne peut être cassé par les projectiles TacZ ──
            //
            // EntityKineticBulletHitBlockEvent est fired quand le projectile TacZ frappe
            // un bloc. On annule TOUJOURS cet event pour empêcher toute destruction de
            // blocs, quelle que soit l'arme.

            Class<net.minecraftforge.eventbus.api.Event> blockHitClass =
                (Class<net.minecraftforge.eventbus.api.Event>)
                    Class.forName("com.tacz.guns.api.event.entity.EntityKineticBulletHitBlockEvent");
            Method cancelBlockHit = net.minecraftforge.eventbus.api.Event.class.getMethod("setCanceled", boolean.class);

            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, blockHitClass, event -> {
                try {
                    cancelBlockHit.invoke(event, true);
                } catch (Exception ignored) {}
            });

            initialized = true;
            ZombieroolMod.LOGGER.info("[ZR] TacZ hooks initialized successfully.");

        } catch (Exception e) {
            ZombieroolMod.LOGGER.error("[ZR] Failed to hook into TacZ events", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Utilitaires d'initialisation
    // ─────────────────────────────────────────────────────────────────────────────

    private static boolean isExplosiveType(String type) {
        return "ROCKET".equalsIgnoreCase(type)
            || "PROJECTILE".equalsIgnoreCase(type)
            || "RAYGUN".equalsIgnoreCase(type);
    }

    /** Résout la méthode headshot peu importe la casse utilisée par la version TacZ. */
    private static Method resolveHeadshotMethod(Class<?> eventClass) {
        String[] candidates = {"isHeadShot", "isHeadshot", "isHeadshot"};
        for (String name : candidates) {
            try {
                return eventClass.getMethod(name);
            } catch (NoSuchMethodException ignored) {}
        }
        ZombieroolMod.LOGGER.warn("[ZR] Could not find headshot method on EntityHurtByGunEvent. Headshots disabled.");
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  FIX N°2 — DUMMY AMMO : logique de synchronisation sans race condition
    // ─────────────────────────────────────────────────────────────────────────────
    //
    //  ARCHITECTURE SIMPLIFIÉE :
    //
    //  Source de vérité = tag "zombierool:Reserve" sur chaque ItemStack arme TacZ.
    //  Les items physiques (dummy) sont UNIQUEMENT un miroir de cette valeur.
    //
    //  Chaque tick serveur :
    //    1. Si un verrou de sync est actif pour ce joueur → passer en mode "recalcul pur"
    //       (on remet les items physiques = Reserve, sans déduire).
    //    2. Sinon, comparer les items physiques actuels avec le snapshot du tick précédent.
    //       Si TacZ en a consommé → déduire de la réserve de l'arme tenue.
    //    3. Recalcul absolu : forcer items physiques = somme des réserves.
    //    4. Sauvegarder le snapshot pour le prochain tick.

    /**
     * Appelé par WeaponFacade.refillAllTaczAmmo / refillHeldTaczAmmo APRÈS avoir
     * modifié Reserve, pour signaler à syncDummyAmmo de sauter la déduction pendant
     * 2 ticks (latence de l'inventaire + marge de sécurité).
     */
    public static void markSyncLock(Player player, int ticks) {
        syncLockTicks.put(player.getUUID(), ticks);
    }

    public static void syncDummyAmmo(Player player) {
        if (!initialized || player.level().isClientSide) return;

        UUID pid = player.getUUID();

        // ── Étape 1 : Snapshot physique ACTUEL ─────────────────────────────────
        Map<Item, Integer> currentPhysical = buildPhysicalSnapshot(player);

        // ── Étape 2 : Déduction de réserve si TacZ a consommé des items ────────
        //    Ignoré si un verrou de sync est actif (Max Ammo vient d'être appliqué).
        int lock = syncLockTicks.getOrDefault(pid, 0);
        if (lock > 0) {
            syncLockTicks.put(pid, lock - 1);
            if (lock == 1) syncLockTicks.remove(pid);
            // Mode "recalcul pur" : on ne déduit rien, on recalcule depuis Reserve.
        } else {
            Map<Item, Integer> lastPhysical = lastTickPhysicalAmmo.getOrDefault(pid, new HashMap<>());
            for (Map.Entry<Item, Integer> entry : lastPhysical.entrySet()) {
                Item ammoItem   = entry.getKey();
                int  lastAmount = entry.getValue();
                int  nowAmount  = currentPhysical.getOrDefault(ammoItem, 0);

                if (nowAmount < lastAmount) {
                    // TacZ a consommé (lastAmount - nowAmount) munitions → déduire de la réserve
                    deductReserveFromGuns(player, ammoItem, lastAmount - nowAmount);
                }
            }
        }

        // ── Étape 3 : Recalcul absolu — items physiques = somme des réserves ───
        Map<Item, Integer> targetReserves = buildTargetReserves(player);

        for (Map.Entry<Item, Integer> entry : targetReserves.entrySet()) {
            Item ammoItem = entry.getKey();
            int  target   = entry.getValue();
            int  current  = currentPhysical.getOrDefault(ammoItem, 0);
            if (current != target) {
                setDummyAmmo(player, ammoItem, target);
            }
        }

        // Nettoyage des items dummy d'armes qui ne sont plus dans l'inventaire
        for (Item ammoItem : currentPhysical.keySet()) {
            if (!targetReserves.containsKey(ammoItem)) {
                setDummyAmmo(player, ammoItem, 0);
            }
        }

        // ── Étape 4 : Sauvegarde du snapshot POST-recalcul ─────────────────────
        //    Important : on snapshote APRÈS avoir normalisé les items physiques,
        //    pas avant, sinon le delta du tick suivant serait faussé.
        lastTickPhysicalAmmo.put(pid, buildPhysicalSnapshot(player));
    }

    /** Construit un snapshot {Item → count total} des items dummy dans l'inventaire. */
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

    /** Calcule la somme des réserves pour chaque type de munition depuis les armes TacZ. */
    private static Map<Item, Integer> buildTargetReserves(Player player) {
        Map<Item, Integer> map = new HashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!WeaponFacade.isTaczWeapon(s)) continue;
            Item ammoItem = WeaponFacade.getAmmoItemForGun(s);
            if (ammoItem == null || ammoItem == Items.AIR) continue;
            int reserve = s.getOrCreateTag().getInt("zombierool:Reserve");
            map.merge(ammoItem, reserve, Integer::sum);
        }
        return map;
    }

    private static boolean isDummyAmmo(ItemStack s) {
        return !s.isEmpty() && s.hasTag() && s.getTag().getBoolean("zombierool:dummy_ammo");
    }

    /**
     * Déduit {@code amountToDeduct} de la réserve des armes TacZ du joueur qui
     * utilisent {@code ammoItem}. Priorité à l'arme tenue en main.
     */
    private static void deductReserveFromGuns(Player player, Item ammoItem, int amountToDeduct) {
        if (amountToDeduct <= 0) return;

        // Priorité : arme en main
        ItemStack held = player.getMainHandItem();
        if (WeaponFacade.isTaczWeapon(held) && WeaponFacade.getAmmoItemForGun(held) == ammoItem) {
            int reserve = WeaponFacade.getReserve(held);
            int deduct  = Math.min(reserve, amountToDeduct);
            WeaponFacade.setReserve(held, reserve - deduct);
            amountToDeduct -= deduct;
        }

        // Puis les autres armes
        if (amountToDeduct > 0) {
            for (int i = 0; i < player.getInventory().getContainerSize() && amountToDeduct > 0; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s == held) continue;
                if (!WeaponFacade.isTaczWeapon(s)) continue;
                if (WeaponFacade.getAmmoItemForGun(s) != ammoItem) continue;

                int reserve = WeaponFacade.getReserve(s);
                int deduct  = Math.min(reserve, amountToDeduct);
                WeaponFacade.setReserve(s, reserve - deduct);
                amountToDeduct -= deduct;
            }
        }
    }

    /**
     * Force le contenu en items dummy pour {@code ammoItem} à exactement {@code amount}.
     * Supprime d'abord toutes les anciennes instances, puis redistribue dans les slots vides.
     * Si l'inventaire est plein, utilise les slots d'armure inversés (35→9) sans toucher la hotbar.
     *
     * @return le nombre effectivement placé (peut être < amount si l'inventaire est saturé)
     */
    private static int setDummyAmmo(Player player, Item ammoItem, int amount) {
        // Purge des anciens dummy pour cet item
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == ammoItem && isDummyAmmo(s)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }

        if (amount <= 0) return 0;

        int remaining = amount;
        int maxStack  = Math.max(1, ammoItem.getMaxStackSize(new ItemStack(ammoItem)));

        // Passe 1 : slots vides dans l'inventaire principal (indices 0–35, hors hotbar 0–8)
        for (int i = 9; i < player.getInventory().items.size() && remaining > 0; i++) {
            if (player.getInventory().items.get(i).isEmpty()) {
                int     toAdd = Math.min(remaining, maxStack);
                player.getInventory().setItem(i, makeDummy(ammoItem, toAdd));
                remaining -= toAdd;
            }
        }

        // Passe 2 : slots vides dans la hotbar (0–8)
        for (int i = 0; i < 9 && remaining > 0; i++) {
            if (player.getInventory().items.get(i).isEmpty()) {
                int     toAdd = Math.min(remaining, maxStack);
                player.getInventory().setItem(i, makeDummy(ammoItem, toAdd));
                remaining -= toAdd;
            }
        }

        // Passe 3 : inventaire plein — on force dans les slots 35→9 (jamais la hotbar)
        //           en éjectant l'item précédent si ce n'est pas un dummy ZombieRool.
        if (remaining > 0) {
            for (int slot = 35; slot >= 9 && remaining > 0; slot--) {
                ItemStack old = player.getInventory().getItem(slot);
                if (!old.isEmpty() && !isDummyAmmo(old)) {
                    // On jette l'item original (le joueur récupère quand même ses affaires)
                    player.drop(old.copy(), false);
                }
                int     toAdd = Math.min(remaining, maxStack);
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

    // ─────────────────────────────────────────────────────────────────────────────
    //  Events Forge standards
    // ─────────────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!initialized) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        syncDummyAmmo(event.player);
    }

    /** Empêche le joueur de jeter les munitions factices. */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (isDummyAmmo(event.getEntity().getItem())) {
            event.setCanceled(true);
        }
    }

    /** Empêche le ramassage de munitions factices qui auraient été droppées par bug. */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        ItemStack s = event.getItem().getItem();
        if (isDummyAmmo(s)) {
            event.setCanceled(true);
            event.getItem().discard();
        }
    }

    /**
     * Sécurité supplémentaire : si une explosion Minecraft standard se produit
     * pendant que la partie est active, elle ne casse aucun bloc.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (me.cryo.zombierool.WaveManager.isGameRunning()) {
            event.getAffectedBlocks().clear();
        }
    }

    /** Nettoie les données de sync quand un joueur quitte. */
    public static void onPlayerLeave(UUID playerId) {
        lastTickPhysicalAmmo.remove(playerId);
        syncLockTicks.remove(playerId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  API TacZ — Utilitaires de réflexion
    // ─────────────────────────────────────────────────────────────────────────────

    /** Retourne l'ID de munition TacZ associé à une arme (via l'API TacZ). */
    public static ResourceLocation getAmmoIdForGun(ItemStack gunStack) {
        if (!ModList.get().isLoaded("tacz")) return null;
        try {
            Class<?> iGun = Class.forName("com.tacz.guns.api.item.IGunItem");
            if (!iGun.isInstance(gunStack.getItem())) return null;

            ResourceLocation gunId = (ResourceLocation)
                iGun.getMethod("getGunId", ItemStack.class).invoke(gunStack.getItem(), gunStack);

            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            @SuppressWarnings("unchecked")
            Optional<?> opt = (Optional<?>)
                api.getMethod("getCommonGunIndex", ResourceLocation.class).invoke(null, gunId);

            if (opt.isPresent()) {
                Object index   = opt.get();
                Object gunData = index.getClass().getMethod("getGunData").invoke(index);
                return (ResourceLocation) gunData.getClass().getMethod("getAmmoId").invoke(gunData);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Retourne tous les IDs d'armes enregistrées dans TacZ. */
    public static List<ResourceLocation> getAllTacZGunIds() {
        List<ResourceLocation> list = new ArrayList<>();
        if (!ModList.get().isLoaded("tacz")) return list;
        try {
            Class<?> apiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object mapObj = apiClass.getMethod("getAllCommonGunIndex").invoke(null);
            if (mapObj instanceof Map<?, ?> map) {
                for (Object key : map.keySet()) {
                    if (key instanceof ResourceLocation rl) list.add(rl);
                }
            }
        } catch (Exception e) {
            ZombieroolMod.LOGGER.error("[ZR] Failed to retrieve TacZ gun indices", e);
        }
        return list;
    }
}