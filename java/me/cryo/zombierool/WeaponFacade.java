package me.cryo.zombierool.core.system;
import me.cryo.zombierool.api.IPackAPunchable;
import me.cryo.zombierool.api.IReloadable;
import me.cryo.zombierool.api.ICustomWeapon;
import me.cryo.zombierool.integration.TacZIntegration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
public class WeaponFacade {
    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof ICustomWeapon || isTaczWeapon(stack);
    }
    public static boolean isTaczWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.hasTag() && stack.getTag().getBoolean("zombierool:is_tacz")) return true;
        ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (registryName != null && registryName.getNamespace().equals("tacz") && registryName.getPath().equals("modern_kinetic_gun")) {
            stack.getOrCreateTag().putBoolean("zombierool:is_tacz", true);
            return true;
        }
        return false;
    }
    public static boolean isHandgun(ItemStack stack) {
        if (stack.getItem() instanceof me.cryo.zombierool.item.IHandgunWeapon) return true;
        if (isTaczWeapon(stack)) {
            WeaponSystem.Definition def = getDefinition(stack);
            return def != null && "PISTOL".equalsIgnoreCase(def.type);
        }
        return false;
    }
    public static String getWeaponId(ItemStack stack) {
        if (isTaczWeapon(stack)) {
            return stack.getOrCreateTag().getString("GunId");
        }
        WeaponSystem.Definition def = getDefinition(stack);
        if (def != null) return def.id;
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return rl != null ? rl.toString() : "";
    }
    public static WeaponSystem.Definition getDefinition(ItemStack stack) {
        if (stack.getItem() instanceof WeaponSystem.BaseGunItem gun) return gun.getDefinition();
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
    public static ItemStack createUnmappedTaczWeaponStack(ResourceLocation gunId, boolean pap) {
        Item taczItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:modern_kinetic_gun"));
        if (taczItem == null || taczItem == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(taczItem);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("GunId", gunId.toString());
        tag.putBoolean("zombierool:is_tacz", true);
        tag.putBoolean("zombierool:unmapped", true);
        tag.putBoolean("HasBulletInBarrel", false); 
        TacZIntegration.applyUnmappedTaczProperties(stack, gunId);
        int baseAmmo = TacZIntegration.getTacZWeaponBaseAmmo(stack);
        tag.putInt("GunCurrentAmmoCount", pap ? baseAmmo * 2 : baseAmmo);
        setReserve(stack, pap ? baseAmmo * 8 : baseAmmo * 4);
        if (pap) TacZIntegration.applyTaczPap(stack, null);
        return stack;
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
                tag.putBoolean("HasBulletInBarrel", false); 
                String mode = "SEMI";
                if (def.burst != null && def.burst.count > 1) {
                    mode = "BURST";
                } else if (def.stats.fire_rate <= 5) {
                    mode = "AUTO";
                }
                tag.putString("GunFireMode", mode);
                TacZIntegration.applyDefaultAttachments(taczStack, def);
                int maxAmmo = TacZIntegration.getTacZWeaponMaxAmmo(taczStack, def);
                tag.putInt("GunCurrentAmmoCount", maxAmmo);
                if (pap) {
                    TacZIntegration.applyTaczPap(taczStack, def);
                } else {
                    setReserve(taczStack, TacZIntegration.getTacZWeaponMaxReserve(taczStack, def));
                    taczStack.setHoverName(net.minecraft.network.chat.Component.literal("§a" + def.name));
                }
                return taczStack;
            }
        }
        ResourceLocation zrLoc = new ResourceLocation(def.id != null && def.id.contains(":") ? def.id : "zombierool:" + cleanId);
        Item zrItem = ForgeRegistries.ITEMS.getValue(zrLoc);
        if (zrItem != null && zrItem != Items.AIR) {
            ItemStack stack = new ItemStack(zrItem);
            if (zrItem instanceof IReloadable reloadable) reloadable.initializeIfNeeded(stack);
            if (pap && zrItem instanceof IPackAPunchable papable) papable.applyPackAPunch(stack);
            return stack;
        }
        return ItemStack.EMPTY;
    }
    public static boolean isPackAPunched(ItemStack stack) {
        if (stack.getItem() instanceof IPackAPunchable pap) return pap.isPackAPunched(stack);
        if (isTaczWeapon(stack)) return stack.getOrCreateTag().getBoolean("zombierool:pap");
        return false;
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
        if (isTaczWeapon(stack)) {
            CompoundTag tag = stack.getOrCreateTag();
            if (!tag.contains("DummyAmmo")) {
                int max = getMaxReserve(stack);
                tag.putInt("DummyAmmo", max);
            }
            return tag.getInt("DummyAmmo");
        }
        return 0;
    }
    public static void setReserve(ItemStack stack, int reserve) {
        if (stack.getItem() instanceof IReloadable r) {
            r.setReserve(stack, reserve);
        } else if (isTaczWeapon(stack)) {
            stack.getOrCreateTag().putInt("DummyAmmo", Math.max(0, reserve));
        }
    }
    public static int getMaxAmmo(ItemStack stack) {
        if (stack.getItem() instanceof IReloadable r) return r.getMaxAmmo(stack);
        WeaponSystem.Definition def = getDefinition(stack);
        if (isTaczWeapon(stack)) {
            return TacZIntegration.getTacZWeaponMaxAmmo(stack, def);
        }
        if (def != null) return isPackAPunched(stack) ? def.ammo.clip_size + def.pap.clip_bonus : def.ammo.clip_size;
        return 0;
    }
    public static int getMaxReserve(ItemStack stack) {
        if (stack.getItem() instanceof IReloadable r) return r.getMaxReserve(stack);
        WeaponSystem.Definition def = getDefinition(stack);
        if (isTaczWeapon(stack)) {
            return TacZIntegration.getTacZWeaponMaxReserve(stack, def);
        }
        if (def != null) return isPackAPunched(stack) ? def.ammo.max_reserve + def.pap.reserve_bonus : def.ammo.max_reserve;
        return 0;
    }
    public static Item getAmmoItemForGun(ItemStack stack) {
        return TacZIntegration.getAmmoItemForGun(stack);
    }
    public static List<ResourceLocation> getUnmappedTaczGuns() {
        List<ResourceLocation> unmapped = new ArrayList<>();
        if (!ModList.get().isLoaded("tacz")) return unmapped;
        List<ResourceLocation> allTacz = TacZIntegration.getAllTacZGunIds();
        Set<String> mappedTaczIds = new HashSet<>();
        for (WeaponSystem.Definition def : WeaponSystem.Loader.LOADED_DEFINITIONS.values()) {
            if (def.tacz != null && def.tacz.gun_id != null) {
                mappedTaczIds.add(def.tacz.gun_id);
            }
        }
        for (ResourceLocation taczId : allTacz) {
            if (!mappedTaczIds.contains(taczId.toString())) {
                unmapped.add(taczId);
            }
        }
        return unmapped;
    }
    public static void refillHeldTaczAmmo(Player player, ItemStack stack) {
        if (!isTaczWeapon(stack)) return;
        setAmmo(stack, getMaxAmmo(stack));
        setReserve(stack, getMaxReserve(stack));
    }
    public static void refillAllTaczAmmo(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isTaczWeapon(stack)) {
                refillHeldTaczAmmo(player, stack);
            }
        }
    }
    public static void applyPackAPunch(ItemStack stack) {
        if (stack.getItem() instanceof IPackAPunchable pap) pap.applyPackAPunch(stack);
        else if (isTaczWeapon(stack)) TacZIntegration.applyTaczPap(stack, getDefinition(stack));
    }
    public static ResourceLocation getTaczIcon(ResourceLocation gunId) {
        return TacZIntegration.getGunIcon(gunId);
    }
    public static void shootCustomTaczProjectile(ServerPlayer player, ItemStack stack, WeaponSystem.Definition def) {
        boolean isPap = isPackAPunched(stack);
        float damage = def.stats.damage;
        if (isPap) damage += def.pap.damage_bonus;
        float spread = isPap ? def.ballistics.spread * def.pap.spread_mult : def.ballistics.spread;
        float velocity = isPap ? def.ballistics.velocity * 1.25f : def.ballistics.velocity;
        int count = (isPap && def.pap.pellet_count_override > 0) ? def.pap.pellet_count_override : def.ballistics.count;
        int penetration = def.stats.penetration;
        if (isPap) penetration += def.pap.penetration_bonus;
        for (int i = 0; i < count; i++) {
            net.minecraft.world.entity.projectile.Arrow projectile = new net.minecraft.world.entity.projectile.Arrow(player.level(), player);
            projectile.setBaseDamage(0); 
            net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
            projectile.setPos(eyePos.x, eyePos.y - 0.1, eyePos.z);
            float currentYaw = player.getYRot();
            if (count == 3 && isPap) {
                if (i == 0) currentYaw -= 10.0f;
                else if (i == 2) currentYaw += 10.0f;
            }
            projectile.shootFromRotation(player, player.getXRot(), currentYaw, 0.0F, velocity, spread);
            projectile.setSilent(true);
            projectile.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
            if (penetration > 0) {
                projectile.setPierceLevel((byte) Math.min(127, penetration));
            }
            net.minecraft.nbt.CompoundTag nbt = projectile.getPersistentData();
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
    }
}