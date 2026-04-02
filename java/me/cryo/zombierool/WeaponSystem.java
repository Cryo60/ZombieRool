package me.cryo.zombierool.core.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import me.cryo.zombierool.api.ICustomWeapon;
import me.cryo.zombierool.api.IHeadshotWeapon;
import me.cryo.zombierool.api.IOverheatable;
import me.cryo.zombierool.api.IPackAPunchable;
import me.cryo.zombierool.api.IReloadable;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.integration.TacZIntegration;
import me.cryo.zombierool.util.PlayerVoiceManager;

import net.minecraft.ChatFormatting;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeaponSystem {

    public static class Definition {
        public String id;
        public String name;
        public String type;
        public boolean is_wonder_weapon = false;
        public List<String> lore = new ArrayList<>();
        public List<String> tags = new ArrayList<>();
        
        public Stats stats = new Stats();
        public Ammo ammo = new Ammo();
        public Ballistics ballistics = new Ballistics();
        public Burst burst = new Burst();
        public Recoil recoil = new Recoil();
        
        @SerializedName("pack_a_punch")
        public PackAPunch pap = new PackAPunch();
        public Headshot headshot = new Headshot();
        public Sounds sounds = new Sounds();
        public Scoped scoped = new Scoped();
        public Explosion explosion = null;
        public Tacz tacz = null;

        public static class Stats {
            public float damage = 1f;
            public int fire_rate = 10;
            public double range = 100.0;
            public float mobility = 1.0f;
            public int penetration = 0;
            public int durability = 0;
            public boolean damage_reduction_on_pierce = true;
        }

        public static class Ammo {
            public int clip_size = 1;
            public int max_reserve = 10;
            public int reload_time = 40;
            public int ammo_per_shot = 1;
            public String reload_type = "MAGAZINE";
        }

        public static class Ballistics {
            public int count = 1;
            public float spread = 0.0f;
            public float velocity = 3.0f;
            public String type = "BULLET";
            public float explosion_radius = 0.0f;
            public boolean gravity = false;
            public String trail_vfx = "NONE";
            public String hitscan_vfx = "NONE";
        }

        public static class Burst {
            public int count = 1;
            public int delay = 0;
        }

        public static class Recoil {
            public float pitch = 0.0f;
            public float yaw = 0.0f;
        }

        public static class PackAPunch {
            public String name;
            public float damage_bonus = 0.0f;
            public int clip_bonus = 0;
            public int reserve_bonus = 0;
            public float reload_speed_mult = 0.8f;
            public float spread_mult = 0.5f;
            public float recoil_mult = 0.5f;
            public int durability_bonus = 0;
            public float explosion_radius_bonus = 0.0f;
            public int penetration_bonus = 0;
            public boolean incendiary = false;
            public int pellet_count_override = 0;
            public int burst_count_override = 0;
            public int burst_delay_override = 0;
            public float knockback_bonus = 0.0f;
            public float headshot_threshold = 0.85f;
            public int ricochet_count = 0;
        }

        public static class Headshot {
            public float base_bonus_damage = 0.0f;
            public float pap_bonus_damage = 0.0f;
            public boolean can_explode_head = true;
            public float head_explosion_chance = 1.0f; 
        }

        public static class Sounds {
            public String fire;
            public String fire_pap;
            public String reload_start;
            public String reload;
            public String reload_end;
            public String dry;
            public String equip;
            public String pump;
        }

        public static class Scoped {
            @SerializedName("boolean")
            public boolean isScoped = false;
            public String scope = "none";
            public String zoom = "1x";
        }

        public static class Explosion {
            public float radius = 2.0f;
            public float damage_multiplier = 1.0f;
            public float self_damage_multiplier = 0.15f;
            public float self_damage_cap = 3.0f;
            public float knockback = 0.4f;
            public String vfx_type = "EXPLOSION";
            public String sound = "";
            public boolean pap_only = false;
        }

        public static class Tacz {
            public String gun_id;
            public String pap_gun_id;
            public String ammo_id;
            public Map<String, String> attachments = new HashMap<>();
            public Map<String, String> pap_attachments = new HashMap<>();
        }

        public void resolveBackwardCompatibility() {
            if (this.explosion == null && this.ballistics.explosion_radius > 0) {
                this.explosion = new Explosion();
                this.explosion.radius = this.ballistics.explosion_radius;
                this.explosion.self_damage_cap = 5.0f;
                this.explosion.sound = "zombierool:explosion_old";
            }
            if ("NONE".equals(this.ballistics.trail_vfx)) {
                if (id != null && id.contains("raygun") && !id.contains("markii")) this.ballistics.trail_vfx = "RAYGUN";
                else if (id != null && (id.contains("rpg") || id.contains("chinalake"))) this.ballistics.trail_vfx = "RPG";
            }
            if ("NONE".equals(this.ballistics.hitscan_vfx)) {
                if ("SNIPER".equals(this.type)) this.ballistics.hitscan_vfx = "SNIPER";
                else if (id != null && id.contains("barret")) this.ballistics.hitscan_vfx = "SNIPER";
                else if (id != null && id.contains("raygunmarkii")) this.ballistics.hitscan_vfx = "RAYGUN_MK2";
            }
            if (this.id != null && this.id.contains("saw")) {
                this.ballistics.hitscan_vfx = "SAW";
            }
            if (id != null && id.contains("barret")) this.stats.damage_reduction_on_pierce = false;
            if (id != null && id.contains("raygunmarkii")) this.stats.damage_reduction_on_pierce = false;
        }
    }

    public static class Loader {
        private static final Gson GSON = new GsonBuilder().setLenient().create();
        public static final Map<String, Definition> LOADED_DEFINITIONS = new HashMap<>();

        private static final String[] BUILTIN_WEAPONS = {
                "357magnum", "acr", "ak47", "ak74u", "arc12", "arisaka", "aug", "bar",
                "barret", "battlerifle", "beretta93r", "bizon", "browningm1911", "chinalake",
                "covenantcarbine", "czscorpionevo3", "deagle", "dmr", "doublebarrel", "dragunov",
                "energysword", "famas", "fg42", "fiveseven", "flamethrower", "fnfal",
                "g36c", "galil", "gewehr43", "glock", "haymaker", "hk21", "hkg3", "hydra",
                "intervention", "kar98k", "l85a2", "m14", "m16a4", "m1911",
                "m1carbine", "m1garand", "m40a3", "m7smg", "ma5d", "magnum", "maschinenpistole28",
                "mauserc96", "mg42", "mosinnagant", "mp40", "mp5", "mp7", "mpx",
                "needler", "oldcrossbow", "oldsword", "olympia", "p90", "peacekeeper", "percepteur",
                "plasmapistol", "ppsh41", "r4c", "raygun", "raygunmarkii", "rpd", "rpg", "rpk",
                "saw", "scarh", "shotgun", "sniper", "spas12", "springfield", "starr1858",
                "stg44", "storm", "superbow", "svt40", "tar21", "thompson", "thundergun", "trenchgun",
                "type100", "ump45", "usp45", "uzi", "vandal", "vector", "whisper", "wunderwaffedg2"
        };

        public static BaseGunItem createWeapon(Definition def) {
            String type = def.type != null ? def.type.toUpperCase() : "RIFLE";
            String id = def.id != null ? def.id.replace("zombierool:", "").trim().toLowerCase() : "";

            if ("energysword".equals(id)) {
                return new me.cryo.zombierool.item.EnergySwordItem(def);
            } else if ("oldsword".equals(id)) {
                return new me.cryo.zombierool.item.OldSwordWeaponItem(def);
            } else if ("needler".equals(id)) {
                return new me.cryo.zombierool.item.NeedlerItem(def);
            } else if ("thundergun".equals(id)) {
                return new me.cryo.zombierool.item.ThundergunItem(def);
            } else if ("wunderwaffedg2".equals(id)) {
                return new me.cryo.zombierool.item.WunderwaffeItem(def);
            } else if ("m16a4".equals(id)) {
                return new me.cryo.zombierool.item.M16A4Item(def);
            } else if ("galil".equals(id)) {
                return new me.cryo.zombierool.item.GalilItem(def);
            } else if ("scarh".equals(id)) {
                return new me.cryo.zombierool.item.ScarHItem(def);
            } else if ("m1911".equals(id)) {
                return new me.cryo.zombierool.item.M1911Item(def);
            } else if ("m1garand".equals(id)) {
                return new me.cryo.zombierool.item.M1GarandItem(def);
            } else if ("plasmapistol".equals(id) || "PLASMAPISTOL".equals(type)) {
                return new me.cryo.zombierool.item.PlasmaPistolItem(def);
            } else if ("flamethrower".equals(id) || "FLAMETHROWER".equals(type)) {
                return new me.cryo.zombierool.item.FlamethrowerItem(def);
            }

            if ("WHISPER".equals(type) || "whisper".equals(id)) {
                return new me.cryo.zombierool.item.WhisperItem(def);
            } else if ("STORM".equals(type) || "storm".equals(id)) {
                return new me.cryo.zombierool.item.StormItem(def);
            } else if ("BOLT".equals(type)) {
                def.stats.fire_rate = 30;
                return new WeaponImplementations.BoltActionRifleItem(def);
            } else if ("SHOTGUN".equals(type)) {
                if ("SHELL".equalsIgnoreCase(def.ammo.reload_type)) def.stats.fire_rate = 25;
                return new WeaponImplementations.ShotgunItem(def);
            } else if ("SNIPER".equals(type)) {
                return new WeaponImplementations.SniperGunItem(def);
            } else if ("MELEE".equals(type)) {
                return new WeaponImplementations.MeleeWeaponItem(def);
            } else if ("LAUNCHER".equals(type) || "PROJECTILE".equals(type) || "RAYGUN".equals(type)) {
                return new WeaponImplementations.ProjectileGunItem(def);
            } else if ("PISTOL".equals(type)) {
                return new WeaponImplementations.PistolGunItem(def);
            }

            return new WeaponImplementations.HitscanGunItem(def);
        }

        public static void loadWeapons() {
            LOADED_DEFINITIONS.clear();

            for (String weaponId : BUILTIN_WEAPONS) {
                String path = "data/zombierool/gameplay/weapons/" + weaponId + ".json";
                try (InputStream stream = Loader.class.getClassLoader().getResourceAsStream(path)) {
                    if (stream != null) {
                        try (InputStreamReader reader = new InputStreamReader(stream)) {
                            Definition def = GSON.fromJson(reader, Definition.class);
                            if (def != null) {
                                if (def.id == null || def.id.trim().isEmpty()) {
                                    def.id = "zombierool:" + weaponId;
                                }
                                def.resolveBackwardCompatibility();
                                registerDefinition(def, weaponId);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            File externalFolder = FMLPaths.GAMEDIR.get().resolve("data/zombierool/gameplay/weapons/").toFile();
            if (externalFolder.exists() && externalFolder.isDirectory()) {
                File[] files = externalFolder.listFiles((dir, name) -> name.endsWith(".json"));
                if (files != null) {
                    for (File file : files) {
                        try (FileReader reader = new FileReader(file)) {
                            Definition def = GSON.fromJson(reader, Definition.class);
                            if (def != null) {
                                String filenameId = file.getName().replace(".json", "");
                                if (def.id == null || def.id.trim().isEmpty()) {
                                    def.id = "zombierool:" + filenameId;
                                }
                                def.resolveBackwardCompatibility();
                                registerDefinition(def, filenameId);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        private static void registerDefinition(Definition def, String filenameId) {
            if (def != null) {
                if (def.id == null || def.id.trim().isEmpty()) {
                    def.id = "zombierool:" + filenameId;
                }
                String id = def.id.replace("zombierool:", "").trim();
                if (!id.isEmpty()) LOADED_DEFINITIONS.put(id, def);
            }
        }
    }

    public static abstract class BaseGunItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon, IOverheatable {
        protected final Definition def;

        public static final String TAG_AMMO = "Ammo";
        public static final String TAG_AMMO_LEFT = "AmmoLeft";
        public static final String TAG_RESERVE = "Reserve";
        public static final String TAG_RELOAD_TIMER = "ReloadTimer";
        public static final String TAG_IS_RELOADING = "IsReloading";
        public static final String TAG_PAP = "PackAPunch";
        public static final String TAG_LAST_FIRE = "LastFire";
        public static final String TAG_LAST_FIRE_LEFT = "LastFireLeft";
        public static final String TAG_OVERHEAT = "Overheat";
        public static final String TAG_IS_OVERHEATED = "IsOverheated";
        public static final String TAG_DURABILITY = "Durability";
        public static final String TAG_EQUIPPED_PREV = "EquippedPrev";
        public static final String TAG_PUMP_PLAYED = "PumpSoundPlayed";
        public static final String TAG_BURST_SHOTS_LEFT = "BurstShotsLeft";
        public static final String TAG_BURST_DELAY = "BurstDelayTimer";

        public static final java.util.UUID MOBILITY_MODIFIER_UUID = java.util.UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CE");

        public BaseGunItem(Definition def) {
            super(new Item.Properties().stacksTo(1).rarity((def.is_wonder_weapon || "WONDER".equalsIgnoreCase(def.type)) ? Rarity.EPIC : Rarity.COMMON));
            this.def = def;
        }

        public Definition getDefinition() {
            if (def == null || def.id == null) return def;
            String cleanId = def.id.replace("zombierool:", "");
            Definition loaded = Loader.LOADED_DEFINITIONS.get(cleanId);
            return loaded != null ? loaded : def;
        }

        protected CompoundTag getOrCreateTag(ItemStack s) {
            if (!s.hasTag()) s.setTag(new CompoundTag());
            return s.getTag();
        }

        @Override
        public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
            Multimap<Attribute, AttributeModifier> map = super.getDefaultAttributeModifiers(slot);
            if (slot == EquipmentSlot.MAINHAND && getDefinition().stats.mobility != 1.0f) {
                ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                builder.putAll(map);
                builder.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(
                        MOBILITY_MODIFIER_UUID,
                        "Weapon mobility",
                        getDefinition().stats.mobility - 1.0f,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
                return builder.build();
            }
            return map;
        }

        public float getDynamicSpread(ItemStack stack, Player player) {
            Definition currentDef = getDefinition();
            float baseSpread = isPackAPunched(stack) ? currentDef.ballistics.spread * currentDef.pap.spread_mult : currentDef.ballistics.spread;
            if (player == null) return baseSpread;

            float mult = 1.0f;
            if (!player.onGround()) {
                mult = 2.5f;
            } else if (player.isSprinting()) {
                mult = 2.0f;
            } else if (player.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
                mult = 1.5f;
            } else if (player.isCrouching()) {
                mult = 0.5f;
            }
            return baseSpread * mult;
        }

        public boolean isAkimbo(ItemStack stack) {
            return false;
        }

        public int getFireRate(ItemStack stack, @Nullable Player player) {
            Definition currentDef = getDefinition();
            int rate = currentDef.stats.fire_rate;
            if (player != null && player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAP.get())) {
                rate = Math.max(1, (int)(rate * 0.75f));
            }
            return rate;
        }

        public Vec3 getVisualMuzzlePos(Player player, boolean isLeft) {
            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 lookVec = player.getViewVector(1.0F);
            Vec3 rightVec = lookVec.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 upVec = rightVec.cross(lookVec).normalize();
            
            float sideOffset = isLeft ? -0.45f : 0.45f;
            
            return eyePos.add(rightVec.scale(sideOffset)).add(upVec.scale(-0.35)).add(lookVec.scale(0.6));
        }

        public Vec3 getVisualMuzzlePos(Player player) {
            return getVisualMuzzlePos(player, false);
        }

        @Override
        public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
            return slotChanged || oldStack.getItem() != newStack.getItem();
        }

        @Override public int getAmmo(ItemStack s) { return getOrCreateTag(s).getInt(TAG_AMMO); }
        @Override public void setAmmo(ItemStack s, int a) { getOrCreateTag(s).putInt(TAG_AMMO, a); }

        public int getAmmoLeft(ItemStack s) { return getOrCreateTag(s).getInt(TAG_AMMO_LEFT); }
        public void setAmmoLeft(ItemStack s, int a) { getOrCreateTag(s).putInt(TAG_AMMO_LEFT, a); }

        @Override public int getReserve(ItemStack s) { return getOrCreateTag(s).getInt(TAG_RESERVE); }
        @Override public void setReserve(ItemStack s, int r) { getOrCreateTag(s).putInt(TAG_RESERVE, r); }

        @Override public int getReloadTimer(ItemStack s) { return getOrCreateTag(s).getInt(TAG_RELOAD_TIMER); }
        @Override public void setReloadTimer(ItemStack s, int t) { getOrCreateTag(s).putInt(TAG_RELOAD_TIMER, t); }

        @Override public int getMaxAmmo(ItemStack s) { Definition currentDef = getDefinition(); return isPackAPunched(s) ? currentDef.ammo.clip_size + currentDef.pap.clip_bonus : currentDef.ammo.clip_size; }
        @Override public int getMaxReserve(ItemStack s) { Definition currentDef = getDefinition(); return isPackAPunched(s) ? currentDef.ammo.max_reserve + currentDef.pap.reserve_bonus : currentDef.ammo.max_reserve; }
        @Override public boolean isInfinite(ItemStack s) { return getDefinition().ammo.max_reserve < 0; }

        public boolean hasDurability() { return getDefinition().stats.durability > 0; }
        public int getMaxDurability(ItemStack stack) { Definition currentDef = getDefinition(); return isPackAPunched(stack) ? currentDef.stats.durability + currentDef.pap.durability_bonus : currentDef.stats.durability; }
        public int getDurability(ItemStack stack) { return getOrCreateTag(stack).getInt(TAG_DURABILITY); }
        public void setDurability(ItemStack stack, int durability) { getOrCreateTag(stack).putInt(TAG_DURABILITY, Mth.clamp(durability, 0, getMaxDurability(stack))); }

        @Override
        public void initializeIfNeeded(ItemStack stack) {
            CompoundTag tag = getOrCreateTag(stack);
            Definition currentDef = getDefinition();
            if (!tag.contains(TAG_AMMO)) {
                setAmmo(stack, currentDef.ammo.clip_size);
                setAmmoLeft(stack, currentDef.ammo.clip_size);
                setReserve(stack, currentDef.ammo.max_reserve);
                if (hasOverheat()) setOverheat(stack, 0);
                if (hasDurability()) setDurability(stack, getMaxDurability(stack));
            }
        }

        @Override
        public void applyPackAPunch(ItemStack stack) {
            getOrCreateTag(stack).putBoolean(TAG_PAP, true);
            setAmmo(stack, getMaxAmmo(stack));
            setAmmoLeft(stack, getMaxAmmo(stack));
            setReserve(stack, getMaxReserve(stack));
            if (hasDurability()) setDurability(stack, getMaxDurability(stack));
        }
        @Override public boolean isPackAPunched(ItemStack stack) { return getOrCreateTag(stack).getBoolean(TAG_PAP); }
        @Override public boolean isFoil(ItemStack stack) { return isPackAPunched(stack); }

        @Override public float getHeadshotBaseDamage(ItemStack stack) { return getDefinition().headshot.base_bonus_damage; }
        @Override public float getHeadshotPapBonusDamage(ItemStack stack) { return getDefinition().headshot.pap_bonus_damage; }
        @Override public float getWeaponDamage(ItemStack stack) { Definition currentDef = getDefinition(); return isPackAPunched(stack) ? currentDef.stats.damage + currentDef.pap.damage_bonus : currentDef.stats.damage; }

        @Override public int getOverheat(ItemStack stack) { return getOrCreateTag(stack).getInt(TAG_OVERHEAT); }
        @Override
        public void setOverheat(ItemStack stack, int overheat) {
            getOrCreateTag(stack).putInt(TAG_OVERHEAT, Mth.clamp(overheat, 0, getMaxOverheat()));
        }
        @Override public int getMaxOverheat() { return 100; }
        public boolean hasOverheat() { return false; }
        protected int getOverheatPerShot(ItemStack stack) { return 10; }
        protected int getDurabilityDrainPerShot(ItemStack stack) { return 1; }
        protected int getCooldownPerTick(ItemStack stack) { return 2; }
        public void onOverheat(ItemStack stack, Player player) {}
        public void onOverheatCooled(ItemStack stack, Player player) {}

        @Override
        public boolean isBarVisible(ItemStack stack) {
            return false;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
            if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
                if (isAkimbo(player.getItemInHand(hand))) {
                    return InteractionResultHolder.consume(player.getItemInHand(hand));
                }
                
                if (this.getDefinition().scoped != null && this.getDefinition().scoped.isScoped) {
                    return InteractionResultHolder.consume(player.getItemInHand(hand));
                } else {
                    return InteractionResultHolder.pass(player.getItemInHand(hand));
                }
            }
            return super.use(level, player, hand);
        }

        public void tryShoot(ItemStack stack, Player player, float charge, boolean isLeft) {
            Definition currentDef = getDefinition();
            long now = player.level().getGameTime();
            String timeTag = isLeft ? TAG_LAST_FIRE_LEFT : TAG_LAST_FIRE;
            long lastFire = getOrCreateTag(stack).getLong(timeTag);

            if (getOrCreateTag(stack).getBoolean(TAG_IS_RELOADING)) {
                if ("SHELL".equalsIgnoreCase(currentDef.ammo.reload_type)) {
                    getOrCreateTag(stack).putBoolean(TAG_IS_RELOADING, false);
                    setReloadTimer(stack, 0);
                    if (!isAkimbo(stack)) player.getCooldowns().removeCooldown(this);
                    if (player.level().isClientSide) {
                        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> ClientSoundStopper.stopReloadSounds(currentDef));
                    }
                } else {
                    return;
                }
            }

            if (!isAkimbo(stack) && player.getCooldowns().isOnCooldown(this)) return;
            if (getOrCreateTag(stack).getInt(TAG_BURST_SHOTS_LEFT) > 0) return;
            if (now - lastFire < getFireRate(stack, player) - 1) return;

            int burstCount = currentDef.burst.count;
            if (isPackAPunched(stack) && currentDef.pap.burst_count_override > 0) {
                burstCount = currentDef.pap.burst_count_override;
            }

            if (burstCount > 1) {
                getOrCreateTag(stack).putInt(TAG_BURST_SHOTS_LEFT, burstCount);
                getOrCreateTag(stack).putInt(TAG_BURST_DELAY, 0);
                getOrCreateTag(stack).putFloat("LastCharge", charge);
            } else {
                if (executeShot(stack, player, charge, isLeft)) {
                    getOrCreateTag(stack).putLong(timeTag, now);
                    if (!isAkimbo(stack) && !getOrCreateTag(stack).getBoolean(TAG_IS_RELOADING)) {
                        player.getCooldowns().addCooldown(this, getFireRate(stack, player));
                    }
                }
            }
        }

        public void tryShoot(ItemStack stack, Player player, float charge) {
            tryShoot(stack, player, charge, false);
        }

        protected boolean executeShot(ItemStack stack, Player player, float charge, boolean isLeft) {
            Definition currentDef = getDefinition();
            if (hasOverheat()) {
                if (getOrCreateTag(stack).getBoolean(TAG_IS_OVERHEATED) || getOverheat(stack) >= getMaxOverheat()) {
                    playSound(player.level(), player, currentDef.sounds.dry);
                    return false;
                }
            }
            if (hasDurability() && getDurability(stack) <= 0) {
                playSound(player.level(), player, currentDef.sounds.dry);
                return false;
            }

            if (!hasDurability() && !hasOverheat()) {
                int currentAmmo = isLeft ? getAmmoLeft(stack) : getAmmo(stack);
                if (currentAmmo <= 0) {
                    if (isInfinite(stack) || getReserve(stack) > 0) {
                        startReload(stack, player);
                    } else {
                        playSound(player.level(), player, currentDef.sounds.dry);
                        PlayerVoiceManager.playEmptyClipSound(player, player.level());
                    }
                    return false;
                }
            }

            int multiplier = player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAP.get()) ? 2 : 1;
            for (int m = 0; m < multiplier; m++) {
                performShooting(stack, player, charge, isLeft);
            }

            PlayerVoiceManager.onPlayerShoot(player);

            if (!player.isCreative()) {
                if (hasOverheat()) {
                    long now = player.level().getGameTime();
                    String timeTag = isLeft ? TAG_LAST_FIRE_LEFT : TAG_LAST_FIRE;
                    long lastFire = getOrCreateTag(stack).getLong(timeTag);
                    int heatAdd = getOverheatPerShot(stack);
                    if (now - lastFire > getFireRate(stack, player) * 2) {
                        heatAdd *= 2;
                    }
                    setOverheat(stack, Math.min(getMaxOverheat(), getOverheat(stack) + heatAdd));
                }
                if (hasDurability()) {
                    setDurability(stack, getDurability(stack) - getDurabilityDrainPerShot(stack));
                }

                if (!hasDurability() && !hasOverheat()) {
                    if (isLeft) {
                        setAmmoLeft(stack, getAmmoLeft(stack) - currentDef.ammo.ammo_per_shot);
                    } else {
                        setAmmo(stack, getAmmo(stack) - currentDef.ammo.ammo_per_shot);
                    }
                    int currentRight = getAmmo(stack);
                    int currentLeft = isAkimbo(stack) ? getAmmoLeft(stack) : 0;

                    if (currentRight <= 0 && (!isAkimbo(stack) || currentLeft <= 0)) {
                        if (isInfinite(stack) || getReserve(stack) > 0) {
                            startReload(stack, player);
                            getOrCreateTag(stack).putInt(TAG_BURST_SHOTS_LEFT, 0);
                        }
                    }
                }
            }

            String soundId = isPackAPunched(stack) ? currentDef.sounds.fire_pap : currentDef.sounds.fire;
            playSound(player.level(), player, soundId);

            if (currentDef.id != null && currentDef.id.contains("raygunmarkii")) {
                Vec3 look = player.getViewVector(1.0f);
                double propStrength = isPackAPunched(stack) ? 0.3 : 0.15;
                player.setDeltaMovement(player.getDeltaMovement().subtract(look.x * propStrength, look.y * propStrength, look.z * propStrength));
                player.hurtMarked = true;
            }

            getOrCreateTag(stack).putBoolean(TAG_PUMP_PLAYED, false);
            return true;
        }

        protected void performShooting(ItemStack stack, Player player, float charge, boolean isLeft) {
            performShooting(stack, player, charge);
        }

        protected abstract void performShooting(ItemStack stack, Player player, float charge);

        protected void playSound(Level level, Player player, String soundId) {
            if (soundId == null || soundId.isEmpty()) return;
            SoundEvent evt = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId));
            if (evt != null) level.playSound(null, player.blockPosition(), evt, SoundSource.PLAYERS, 1f, 1f);
        }

        @Override
        public void startReload(ItemStack stack, Player player) {
            if (!isAkimbo(stack) && player.getCooldowns().isOnCooldown(this)) return;
            if (hasOverheat() || hasDurability()) return;

            int maxClip = getMaxAmmo(stack);
            boolean rightNeedsReload = getAmmo(stack) < maxClip;
            boolean leftNeedsReload = isAkimbo(stack) && getAmmoLeft(stack) < maxClip;

            if (!rightNeedsReload && !leftNeedsReload) return;
            if (!isInfinite(stack) && getReserve(stack) <= 0 && !player.isCreative()) return;

            getOrCreateTag(stack).putInt(TAG_BURST_SHOTS_LEFT, 0);
            
            Definition currentDef = getDefinition();
            float baseTime = currentDef.ammo.reload_time;
            if (isPackAPunched(stack)) baseTime *= currentDef.pap.reload_speed_mult;
            if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) baseTime *= 0.5f;

            int ticks = Math.max(1, (int) baseTime);
            setReloadTimer(stack, ticks);
            getOrCreateTag(stack).putBoolean(TAG_IS_RELOADING, true);
            if (!isAkimbo(stack)) {
                player.getCooldowns().addCooldown(this, ticks);
            }

            if (!player.level().isClientSide) {
                if (!"SHELL".equalsIgnoreCase(currentDef.ammo.reload_type)) {
                    playSound(player.level(), player, currentDef.sounds.reload);
                }
                triggerCherryEffect(player);
            }
        }

        protected void triggerCherryEffect(Player player) {
            if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_CHERRY.get())) {
                AABB box = player.getBoundingBox().inflate(3.0);
                player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Monster && e != player)
                        .forEach(e -> {
                            me.cryo.zombierool.core.manager.DamageManager.applyDamage(e, player.level().damageSources().playerAttack(player), 5.0f);
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 4));
                        });

                if (!player.level().isClientSide() && player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        player.getX(), player.getY() + 1, player.getZ(),
                        30, 1.5, 1.0, 1.5, 0.1
                    );
                    player.level().playSound(
                        null,
                        player.blockPosition(),
                        me.cryo.zombierool.init.ZombieroolModSounds.RELOADING_WITH_CHERRY.get(),
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        1.0f, 1.0f
                    );
                    player.level().playSound(
                        null,
                        player.blockPosition(),
                        me.cryo.zombierool.init.ZombieroolModSounds.ZOMBIE_ELEC.get(),
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        1.0f, 1.0f
                    );
                }
            }
        }

        @Override
        public void tickReload(ItemStack stack, Player player, Level level) {}

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            if (!(entity instanceof Player player)) return;

            initializeIfNeeded(stack);
            CompoundTag tag = getOrCreateTag(stack);
            Definition currentDef = getDefinition();

            if (hasOverheat()) {
                int heat = getOverheat(stack);
                boolean isOverheated = tag.getBoolean(TAG_IS_OVERHEATED);
                if (heat >= getMaxOverheat() && !isOverheated) {
                    tag.putBoolean(TAG_IS_OVERHEATED, true);
                    if (!level.isClientSide) onOverheat(stack, player);
                } else if (isOverheated && heat <= getMaxOverheat() * 0.15f) {
                    tag.putBoolean(TAG_IS_OVERHEATED, false);
                    if (!level.isClientSide) onOverheatCooled(stack, player);
                }

                long lastFire = tag.getLong(TAG_LAST_FIRE);
                long now = level.getGameTime();
                if (now - lastFire > 10 && heat > 0) {
                    setOverheat(stack, Math.max(0, heat - getCooldownPerTick(stack)));
                }
            }

            boolean isReloading = tag.getBoolean(TAG_IS_RELOADING);

            if (!selected && isReloading) {
                tag.putBoolean(TAG_IS_RELOADING, false);
                setReloadTimer(stack, 0);
                isReloading = false;
                if (player.getCooldowns().isOnCooldown(this)) {
                    player.getCooldowns().removeCooldown(this);
                }
                if (level.isClientSide) {
                    net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> ClientSoundStopper.stopReloadSounds(currentDef));
                }
            }

            if (!level.isClientSide) {
                int burstShots = tag.getInt(TAG_BURST_SHOTS_LEFT);
                if (burstShots > 0 && selected && !isReloading) {
                    int burstDelay = tag.getInt(TAG_BURST_DELAY);
                    if (burstDelay <= 0) {
                        float charge = tag.getFloat("LastCharge");
                        boolean success = executeShot(stack, player, charge, false);
                        if (success) {
                            burstShots--;
                            tag.putInt(TAG_BURST_SHOTS_LEFT, burstShots);

                            if (tag.getBoolean(TAG_IS_RELOADING)) {
                                tag.putInt(TAG_BURST_SHOTS_LEFT, 0);
                            } else if (burstShots > 0) {
                                int delay = currentDef.burst.delay;
                                if (isPackAPunched(stack) && currentDef.pap.burst_count_override > 0) {
                                    delay = currentDef.pap.burst_delay_override;
                                }
                                tag.putInt(TAG_BURST_DELAY, Math.max(1, delay));
                            } else {
                                tag.putLong(TAG_LAST_FIRE, level.getGameTime());
                                if (!isAkimbo(stack)) {
                                    player.getCooldowns().addCooldown(this, getFireRate(stack, player));
                                }
                            }
                        } else {
                            tag.putInt(TAG_BURST_SHOTS_LEFT, 0);
                        }
                    } else {
                        tag.putInt(TAG_BURST_DELAY, burstDelay - 1);
                    }
                } else if (!selected && burstShots > 0) {
                    tag.putInt(TAG_BURST_SHOTS_LEFT, 0);
                }
            }

            String pumpSound = currentDef.sounds.pump;
            if (pumpSound != null && !pumpSound.isEmpty()) {
                long lastFire = tag.getLong(TAG_LAST_FIRE);
                long now = level.getGameTime();
                boolean pumpPlayed = tag.getBoolean(TAG_PUMP_PLAYED);
                if (!isReloading && !pumpPlayed && now >= lastFire + (getFireRate(stack, player) / 2) && now < lastFire + getFireRate(stack, player)) {
                    if (!level.isClientSide) playSound(level, player, pumpSound);
                    tag.putBoolean(TAG_PUMP_PLAYED, true);
                }
            }

            if ("MAGAZINE".equalsIgnoreCase(currentDef.ammo.reload_type)) {
                if (isReloading) {
                    int timer = getReloadTimer(stack);
                    if (timer > 0) {
                        setReloadTimer(stack, timer - 1);
                    } else {
                        finishReload(stack, player);
                        tag.putBoolean(TAG_IS_RELOADING, false);
                    }
                }
            }

            boolean equippedPrev = tag.getBoolean(TAG_EQUIPPED_PREV);
            if (selected && !equippedPrev) {
                if (currentDef.sounds.equip != null) playSound(level, player, currentDef.sounds.equip);
                tag.putBoolean(TAG_EQUIPPED_PREV, true);
            } else if (!selected) {
                tag.putBoolean(TAG_EQUIPPED_PREV, false);
            }
        }

        protected void finishReload(ItemStack stack, Player player) {
            if (player.level().isClientSide) return;
            if (hasDurability()) {
                setDurability(stack, getMaxDurability(stack));
                return;
            }

            int max = getMaxAmmo(stack);
            int currentRight = getAmmo(stack);
            int currentLeft = isAkimbo(stack) ? getAmmoLeft(stack) : max;

            int neededRight = max - currentRight;
            int neededLeft = max - currentLeft;
            int totalNeeded = neededRight + neededLeft;

            if (isInfinite(stack)) {
                setAmmo(stack, max);
                if (isAkimbo(stack)) setAmmoLeft(stack, max);
            } else {
                int reserve = getReserve(stack);
                int toLoad = player.isCreative() ? totalNeeded : Math.min(totalNeeded, reserve);

                int loadRight = Math.min(toLoad, neededRight);
                int loadLeft = toLoad - loadRight;

                setAmmo(stack, currentRight + loadRight);
                if (isAkimbo(stack)) setAmmoLeft(stack, currentLeft + loadLeft);

                if (!player.isCreative()) setReserve(stack, reserve - toLoad);
            }
        }

        @Override
        public Component getName(ItemStack stack) {
            Definition currentDef = getDefinition();
            boolean upgraded = isPackAPunched(stack);

            String nameKey = upgraded && currentDef.pap != null && currentDef.pap.name != null && !currentDef.pap.name.isEmpty() ? currentDef.pap.name : currentDef.name;
            if (nameKey == null) nameKey = "Unknown Weapon";
            
            MutableComponent baseComp = (nameKey.startsWith("weapon.") || nameKey.startsWith("item.") || nameKey.contains("zombierool.")) ? 
                    Component.translatable(nameKey) : Component.literal(nameKey);

            MutableComponent nameComponent = Component.empty().append(baseComp).withStyle(upgraded ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE);

            if (hasOverheat()) {
                int currentOverheat = getOverheat(stack);
                if (getOrCreateTag(stack).getBoolean(TAG_IS_OVERHEATED)) {
                    nameComponent.append(Component.literal(" ").append(Component.translatable("message.zombierool.weapon.overheated").withStyle(ChatFormatting.DARK_RED)));
                } else if (currentOverheat > getMaxOverheat() * 0.75) {
                    nameComponent.append(Component.literal(" ").append(Component.translatable("message.zombierool.weapon.heating").withStyle(ChatFormatting.YELLOW)));
                }
            }

            return nameComponent;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            Definition currentDef = getDefinition();
            if (currentDef.lore != null) {
                for (String l : currentDef.lore) {
                    if (l.startsWith("weapon.") || l.startsWith("item.") || l.contains("zombierool.")) {
                        tooltip.add(Component.translatable(l).withStyle(ChatFormatting.GRAY));
                    } else {
                        tooltip.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            if (currentDef.tags != null && !currentDef.tags.isEmpty()) {
                tooltip.add(Component.translatable("weapon.zombierool.tags")
                        .append(": " + String.join(", ", currentDef.tags))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static class ClientSoundStopper {
        public static void stopReloadSounds(Definition def) {
            net.minecraft.client.sounds.SoundManager soundManager = net.minecraft.client.Minecraft.getInstance().getSoundManager();
            if (def.sounds.reload != null && !def.sounds.reload.isEmpty()) soundManager.stop(new net.minecraft.resources.ResourceLocation(def.sounds.reload), net.minecraft.sounds.SoundSource.PLAYERS);
            if (def.sounds.reload_start != null && !def.sounds.reload_start.isEmpty()) soundManager.stop(new net.minecraft.resources.ResourceLocation(def.sounds.reload_start), net.minecraft.sounds.SoundSource.PLAYERS);
            if (def.sounds.pump != null && !def.sounds.pump.isEmpty()) soundManager.stop(new net.minecraft.resources.ResourceLocation(def.sounds.pump), net.minecraft.sounds.SoundSource.PLAYERS);
        }
    }
}