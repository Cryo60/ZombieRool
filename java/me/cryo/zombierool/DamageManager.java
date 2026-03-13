package me.cryo.zombierool.core.manager;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.api.IHeadshotWeapon;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.entity.CrawlerEntity;
import me.cryo.zombierool.entity.DummyEntity;
import me.cryo.zombierool.entity.HellhoundEntity;
import me.cryo.zombierool.entity.ZombieEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID)
public class DamageManager {
    public static final String HEADSHOT_TAG = "zombierool:is_headshot_damage";
    public static final String GUN_DAMAGE_TAG = "zombierool:damage_by_gun";

    public static boolean applyDamage(LivingEntity target, DamageSource source, float damage) {
        target.invulnerableTime = 0; 
        return target.hurt(source, damage);
    }

    public static float calculateDamage(ServerPlayer attacker, LivingEntity target, float baseGunDamage, boolean isHeadshot, ItemStack weapon) {
        float damage = baseGunDamage;

        if (isHeadshot) {
            float globalMultiplier = 2.0f; 
            float flatBonus = 0.0f; 

            if (weapon != null) {
                WeaponSystem.Definition def = WeaponFacade.getDefinition(weapon);
                if (def != null && "SNIPER".equalsIgnoreCase(def.type)) {
                    globalMultiplier = 3.0f;
                }

                if (weapon.getItem() instanceof IHeadshotWeapon headshotWeapon) {
                    flatBonus += headshotWeapon.getHeadshotBaseDamage(weapon); 
                    if (WeaponFacade.isPackAPunched(weapon)) {
                        flatBonus += headshotWeapon.getHeadshotPapBonusDamage(weapon); 
                    }
                } else if (WeaponFacade.isTaczWeapon(weapon) && def != null) {
                    flatBonus += def.headshot.base_bonus_damage;
                    if (WeaponFacade.isPackAPunched(weapon)) {
                        flatBonus += def.headshot.pap_bonus_damage;
                    }
                }
            }

            damage = (baseGunDamage + flatBonus) * globalMultiplier;
        }

        if (BonusManager.isInstaKillActive(attacker)) {
            damage = 100000f;
        }

        return damage;
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;

        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        if (target instanceof Player && source.getEntity() instanceof Player && target != source.getEntity()) {
            event.setCanceled(true);
            return;
        }

        if (!isValidTarget(target)) return;

        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof ServerPlayer player)) return;

        if (target.getPersistentData().getBoolean("SkipFlamePoints")) return;

        if (target.getHealth() - event.getAmount() <= 0) {
            return;
        }

        PointManager.modifyScore(player, 10);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;

        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        if (!isValidTarget(target)) return;

        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof ServerPlayer player)) return;

        int points = 50; 

        boolean isGunDamage = target.getPersistentData().getBoolean(GUN_DAMAGE_TAG);
        boolean isHeadshot = target.getPersistentData().getBoolean(HEADSHOT_TAG);
        boolean isExplosive = target.getPersistentData().getBoolean("zombierool:explosive_damage");

        if (isGunDamage) {
            points = isHeadshot ? 100 : 50;
            
            if (!target.getPersistentData().getBoolean("zombierool:no_gore")) {
                if (isHeadshot) {
                    if (target.getRandom().nextFloat() <= 0.3f) {
                        GoreManager.triggerHeadExplosion(target);
                    }
                } else {
                    GoreManager.tryDismemberLimb(target, 100f );
                }
            }
        } else if (isExplosive) {
            points = 50;
        } else if (source.getDirectEntity() instanceof Player) {
            points = 130;
        }

        PointManager.modifyScore(player, points);

        target.getPersistentData().remove(HEADSHOT_TAG);
        target.getPersistentData().remove(GUN_DAMAGE_TAG);
        target.getPersistentData().remove("zombierool:explosive_damage");
        target.getPersistentData().remove("zombierool:no_gore");
    }

    private static boolean isValidTarget(LivingEntity entity) {
        return entity instanceof ZombieEntity || 
               entity instanceof CrawlerEntity || 
               entity instanceof HellhoundEntity || 
               entity instanceof DummyEntity;
    }
}