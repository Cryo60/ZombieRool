package me.cryo.zombierool.core.system;

import me.cryo.zombierool.api.IPackAPunchable;
import me.cryo.zombierool.api.IReloadable;
import me.cryo.zombierool.api.ICustomWeapon;
import me.cryo.zombierool.integration.TacZIntegration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class WeaponFacade {

    private static List<ResourceLocation> unmappedTaczGunsCache = null;

    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof ICustomWeapon || isTaczWeapon(stack);
    }

    public static boolean isTaczWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.hasTag() && stack.getTag().getBoolean("zombierool:is_tacz")) return true;
        ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return registryName != null
            && registryName.getNamespace().equals("tacz")
            && registryName.getPath().equals("modern_kinetic_gun");
    }

    public static boolean isHandgun(ItemStack stack) {
        if (stack.getItem() instanceof me.cryo.zombierool.item.IHandgunWeapon) return true;
        if (isTaczWeapon(stack)) {
            WeaponSystem.Definition def = getDefinition(stack);
            return def != null && "PISTOL".equalsIgnoreCase(def.type);
        }
        return false;
    }

    public static Item getAmmoItemForGun(ItemStack stack) {
        WeaponSystem.Definition def = getDefinition(stack);
        if (def != null && def.tacz != null && def.tacz.ammo_id != null) {
            String[] parts = def.tacz.ammo_id.split(":");
            if (parts.length == 2) {
                return ForgeRegistries.ITEMS.getValue(new ResourceLocation(parts[0], parts[1]));
            }
        }
        if (isTaczWeapon(stack)) {
            ResourceLocation ammoId = TacZIntegration.getAmmoIdForGun(stack);
            if (ammoId != null) {
                return ForgeRegistries.ITEMS.getValue(ammoId);
            }
        }
        return null;
    }

    public static WeaponSystem.Definition getDefinition(ItemStack stack) {
        if (stack.getItem() instanceof WeaponSystem.BaseGunItem gun) {
            return gun.getDefinition();
        }
        if (isTaczWeapon(stack)) {
            String wId = stack.hasTag() ? stack.getTag().getString("zombierool:weapon_id") : "";
            if (!wId.isEmpty()) {
                return WeaponSystem.Loader.LOADED_DEFINITIONS.get(wId.replace("zombierool:", ""));
            }

            String gunId = stack.getOrCreateTag().getString("GunId");
            if (!gunId.isEmpty()) {
                for (WeaponSystem.Definition def : WeaponSystem.Loader.LOADED_DEFINITIONS.values()) {
                    if (def.tacz != null && gunId.equals(def.tacz.gun_id)) {
                        stack.getOrCreateTag().putString("zombierool:weapon_id", def.id.replace("zombierool:", ""));
                        stack.getOrCreateTag().putBoolean("zombierool:is_tacz", true);
                        return def;
                    }
                }
            }
        }
        return null;
    }

    public static List<ResourceLocation> getUnmappedTaczGuns() {
        if (unmappedTaczGunsCache != null) return unmappedTaczGunsCache;
        unmappedTaczGunsCache = new ArrayList<>();
        if (!ModList.get().isLoaded("tacz")) return unmappedTaczGunsCache;

        List<ResourceLocation> allTaczGuns = TacZIntegration.getAllTacZGunIds();
        Set<String> mappedIds = new HashSet<>();

        for (WeaponSystem.Definition def : WeaponSystem.Loader.LOADED_DEFINITIONS.values()) {
            if (def.tacz != null && def.tacz.gun_id != null) mappedIds.add(def.tacz.gun_id);
        }

        for (ResourceLocation rl : allTaczGuns) {
            if (!mappedIds.contains(rl.toString())) unmappedTaczGunsCache.add(rl);
        }
        return unmappedTaczGunsCache;
    }

    public static ItemStack createUnmappedTaczWeaponStack(ResourceLocation gunId, boolean pap) {
        Item taczItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:modern_kinetic_gun"));
        if (taczItem == null || taczItem == Items.AIR) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(taczItem);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("GunId", gunId.toString());
        tag.putBoolean("zombierool:is_tacz", true);
        tag.putBoolean("zombierool:unmapped", true);
        tag.putBoolean("HasBulletInBarrel", true);
        tag.putString("GunFireMode", "AUTO");

        tag.putInt("GunCurrentAmmoCount", pap ? 60 : 30);
        setReserve(stack, pap ? 240 : 120);

        if (pap) tag.putBoolean("zombierool:pap", true);

        return stack;
    }

    private static String formatAttachmentType(String type) {
        if (type == null || type.isEmpty()) return "";
        if (type.equalsIgnoreCase("extendedmag") || type.equalsIgnoreCase("extended_mag"))
            return "EXTENDED_MAG";
        return type.toUpperCase(java.util.Locale.US);
    }

    private static void applyTaczAttachments(CompoundTag tag, Map<String, String> attachmentsMap) {
        if (attachmentsMap == null || attachmentsMap.isEmpty()) return;

        for (Map.Entry<String, String> entry : attachmentsMap.entrySet()) {
            String type = formatAttachmentType(entry.getKey());
            String idStr = entry.getValue();

            if (idStr.equalsIgnoreCase("none") || idStr.isEmpty()) {
                CompoundTag emptyNbt = new CompoundTag();
                ItemStack.EMPTY.save(emptyNbt);
                tag.put("Attachment" + type, emptyNbt);
                continue;
            }

            ResourceLocation attId = new ResourceLocation(idStr.contains(":") ? idStr : "tacz:" + idStr);
            boolean exists = true;

            if (ModList.get().isLoaded("tacz")) {
                try {
                    Class<?> apiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
                    Optional<?> opt = (Optional<?>) apiClass.getMethod("getCommonAttachmentIndex", ResourceLocation.class).invoke(null, attId);
                    if (opt.isEmpty()) {
                        exists = false;
                        me.cryo.zombierool.ZombieroolMod.LOGGER.warn("[ZR] L'accessoire {} n'existe pas dans TacZ, remplacé par vide pour éviter une texture manquante.", attId);
                    }
                } catch (Exception e) {}
            }

            if (exists) {
                CompoundTag attNbt = new CompoundTag();
                attNbt.putString("id", "tacz:attachment");
                attNbt.putByte("Count", (byte) 1);
                CompoundTag attTag = new CompoundTag();
                attTag.putString("AttachmentId", attId.toString());
                attNbt.put("tag", attTag);

                tag.put("Attachment" + type, attNbt);
            } else {
                CompoundTag emptyNbt = new CompoundTag();
                ItemStack.EMPTY.save(emptyNbt);
                tag.put("Attachment" + type, emptyNbt);
            }
        }
    }

    public static ItemStack createWeaponStack(String zrId, boolean pap) {
        String cleanId = zrId.replace("zombierool:", "");
        WeaponSystem.Definition def = WeaponSystem.Loader.LOADED_DEFINITIONS.get(cleanId);
        if (def == null) return ItemStack.EMPTY;

        if (def.tacz != null && def.tacz.gun_id != null && ModList.get().isLoaded("tacz")) {
            Item taczItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:modern_kinetic_gun"));
            if (taczItem != null && taczItem != Items.AIR) {
                ItemStack taczStack = new ItemStack(taczItem);
                CompoundTag tag = taczStack.getOrCreateTag();
                tag.putString("GunId", def.tacz.gun_id);
                tag.putBoolean("zombierool:is_tacz", true);
                tag.putString("zombierool:weapon_id", cleanId);
                tag.putBoolean("HasBulletInBarrel", true);

                if (def.tags != null && def.tags.contains("automatic"))
                    tag.putString("GunFireMode", "AUTO");
                else if (def.burst != null && def.burst.count > 1)
                    tag.putString("GunFireMode", "BURST");
                else
                    tag.putString("GunFireMode", "SEMI");

                int maxAmmo = pap ? def.ammo.clip_size + def.pap.clip_bonus : def.ammo.clip_size;
                tag.putInt("GunCurrentAmmoCount", maxAmmo);

                if (pap) {
                    applyTaczPap(taczStack, def);
                } else {
                    setReserve(taczStack, def.ammo.max_reserve);
                    if (def.tacz.attachments != null && !def.tacz.attachments.isEmpty()) {
                        applyTaczAttachments(tag, def.tacz.attachments);
                    }
                }
                return taczStack;
            }
        }

        ResourceLocation zrLoc = new ResourceLocation(
            def.id != null && def.id.contains(":") ? def.id : "zombierool:" + cleanId);
        Item zrItem = ForgeRegistries.ITEMS.getValue(zrLoc);
        if (zrItem != null && zrItem != Items.AIR) {
            ItemStack stack = new ItemStack(zrItem);
            if (zrItem instanceof IReloadable reloadable) reloadable.initializeIfNeeded(stack);
            if (pap && zrItem instanceof IPackAPunchable papable) papable.applyPackAPunch(stack);
            return stack;
        }

        return ItemStack.EMPTY;
    }

    public static void applyTaczPap(ItemStack stack, WeaponSystem.Definition def) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("zombierool:pap", true);

        if (def != null) {
            tag.putInt("GunCurrentAmmoCount", def.ammo.clip_size + def.pap.clip_bonus);
            setReserve(stack, def.ammo.max_reserve + def.pap.reserve_bonus);

            if (def.tacz != null && def.tacz.pap_attachments != null && !def.tacz.pap_attachments.isEmpty()) {
                applyTaczAttachments(tag, def.tacz.pap_attachments);
            }
        } else {
            tag.putInt("GunCurrentAmmoCount", 60);
            setReserve(stack, 240);
        }
    }

    public static boolean isPackAPunched(ItemStack stack) {
        if (stack.getItem() instanceof IPackAPunchable pap) return pap.isPackAPunched(stack);
        if (isTaczWeapon(stack)) return stack.getOrCreateTag().getBoolean("zombierool:pap");
        return false;
    }

    public static void applyPackAPunch(ItemStack stack) {
        if (stack.getItem() instanceof IPackAPunchable pap) pap.applyPackAPunch(stack);
        else if (isTaczWeapon(stack)) applyTaczPap(stack, getDefinition(stack));
    }

    public static boolean canBePackAPunched(ItemStack stack) {
        if (stack.getItem() instanceof IPackAPunchable pap) return pap.canBePackAPunched(stack);
        if (isTaczWeapon(stack)) return !isPackAPunched(stack);
        return false;
    }

    public static int getAmmo(ItemStack stack) {
        if (stack.getItem() instanceof IReloadable r) return r.getAmmo(stack);
        if (isTaczWeapon(stack)) return stack.getOrCreateTag().getInt("GunCurrentAmmoCount");
        return 0;
    }

    public static void setAmmo(ItemStack stack, int ammo) {
        if (stack.getItem() instanceof IReloadable r) r.setAmmo(stack, ammo);
        else if (isTaczWeapon(stack)) stack.getOrCreateTag().putInt("GunCurrentAmmoCount", Math.max(0, ammo));
    }

    public static int getReserve(ItemStack stack) {
        if (stack.getItem() instanceof IReloadable r) return r.getReserve(stack);
        if (isTaczWeapon(stack)) return stack.getOrCreateTag().getInt("DummyAmmo");
        return 0;
    }

    public static void setReserve(ItemStack stack, int reserve) {
        if (stack.getItem() instanceof IReloadable r) {
            r.setReserve(stack, reserve);
        } else if (isTaczWeapon(stack)) {
            stack.getOrCreateTag().putInt("DummyAmmo", Math.max(0, reserve));
            WeaponSystem.Definition def = getDefinition(stack);
            int max = def != null ? (isPackAPunched(stack) ? def.ammo.max_reserve + def.pap.reserve_bonus : def.ammo.max_reserve) : 9999;
            stack.getOrCreateTag().putInt("MaxDummyAmmo", max);
        }
    }

    public static int getMaxAmmo(ItemStack stack) {
        if (stack.getItem() instanceof IReloadable r) return r.getMaxAmmo(stack);
        WeaponSystem.Definition def = getDefinition(stack);
        if (def != null) return isPackAPunched(stack) ? def.ammo.clip_size + def.pap.clip_bonus : def.ammo.clip_size;
        if (isTaczWeapon(stack) && stack.getOrCreateTag().getBoolean("zombierool:unmapped"))
            return isPackAPunched(stack) ? 60 : 30;
        return 0;
    }

    public static int getMaxReserve(ItemStack stack) {
        if (stack.getItem() instanceof IReloadable r) return r.getMaxReserve(stack);
        WeaponSystem.Definition def = getDefinition(stack);
        if (def != null) return isPackAPunched(stack) ? def.ammo.max_reserve + def.pap.reserve_bonus : def.ammo.max_reserve;
        if (isTaczWeapon(stack) && stack.getOrCreateTag().getBoolean("zombierool:unmapped"))
            return isPackAPunched(stack) ? 240 : 120;
        return 0;
    }

    public static void refillAllTaczAmmo(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isTaczWeapon(stack)) {
                setAmmo(stack, getMaxAmmo(stack));
                setReserve(stack, getMaxReserve(stack));
            }
        }
    }

    public static void refillHeldTaczAmmo(Player player, ItemStack stack) {
        if (!isTaczWeapon(stack)) return;
        setAmmo(stack, getMaxAmmo(stack));
        setReserve(stack, getMaxReserve(stack));
    }

    public static void shootCustomTaczProjectile(ServerPlayer player, ItemStack stack, WeaponSystem.Definition def) {
        boolean isPap = isPackAPunched(stack);
        float damage = def.stats.damage + (isPap ? def.pap.damage_bonus : 0);
        float spread = isPap ? def.ballistics.spread * def.pap.spread_mult : def.ballistics.spread;
        float velocity = isPap ? def.ballistics.velocity * 1.25f : def.ballistics.velocity;
        int count = (isPap && def.pap.pellet_count_override > 0) ? def.pap.pellet_count_override : def.ballistics.count;
        int penetration = def.stats.penetration + (isPap ? def.pap.penetration_bonus : 0);

        for (int i = 0; i < count; i++) {
            Arrow projectile = new Arrow(player.level(), player);
            projectile.setBaseDamage(0); 

            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 look = player.getViewVector(1.0F);
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 up = right.cross(look).normalize();

            Vec3 startPos = eyePos.add(right.scale(0.45f)).add(up.scale(-0.35)).add(look.scale(0.6));
            projectile.setPos(startPos.x, startPos.y, startPos.z);

            float yaw = player.getYRot();
            if (count == 3 && isPap) {
                if (i == 0) yaw -= 10.0f;
                else if (i == 2) yaw += 10.0f;
            }

            projectile.shootFromRotation(player, player.getXRot(), yaw, 0.0F, velocity, spread);
            projectile.setSilent(true);
            projectile.pickup = AbstractArrow.Pickup.DISALLOWED;
            if (penetration > 0) projectile.setPierceLevel((byte) Math.min(127, penetration));

            CompoundTag nbt = projectile.getPersistentData();
            nbt.putBoolean("zombierool:custom_projectile", true);
            nbt.putFloat("zombierool:damage", damage);
            nbt.putBoolean("zombierool:invisible", true);
            nbt.putBoolean("zombierool:pap", isPap);
            nbt.putString("zombierool:trail_vfx", def.ballistics.trail_vfx);

            if (def.explosion != null && (!def.explosion.pap_only || isPap)) {
                nbt.putBoolean("zombierool:explosive", true);
                nbt.putFloat("zr_exp_radius", def.explosion.radius + (isPap ? def.pap.explosion_radius_bonus : 0));
                nbt.putFloat("zr_exp_dmg_mult", def.explosion.damage_multiplier);
                nbt.putFloat("zr_exp_self_mult", def.explosion.self_damage_multiplier);
                nbt.putFloat("zr_exp_self_cap", def.explosion.self_damage_cap);
                nbt.putFloat("zr_exp_kb", def.explosion.knockback);
                nbt.putString("zr_exp_vfx", def.explosion.vfx_type);
                nbt.putString("zr_exp_sound", def.explosion.sound);
            }

            if (!def.ballistics.gravity) projectile.setNoGravity(true);
            player.level().addFreshEntity(projectile);
        }

        String soundId = isPap ? def.sounds.fire_pap : def.sounds.fire;
        if (soundId != null && !soundId.isEmpty()) {
            net.minecraft.sounds.SoundEvent evt = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId));
            if (evt != null) {
                player.level().playSound(null, player.blockPosition(), evt, SoundSource.PLAYERS, 1f, 1f);
            }
        }
    }

    public static void clearCache() {
        unmappedTaczGunsCache = null;
    }
}