package me.cryo.zombierool.event;
import me.cryo.zombierool.util.PlayerVoiceManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.ZombieroolMod;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.Optional;
@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonWeaponEventHandler {
    private static final TagKey<EntityType<?>> ZOMBIE_LIKE = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("zombierool", "allowed_mobs"));
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity killedEntity = event.getEntity();
        Entity eventKiller = event.getSource().getEntity(); 
        if (eventKiller instanceof Player player) {
            if (killedEntity.getType().is(ZOMBIE_LIKE)) {
                PlayerVoiceManager.playKillConfirmedSound(player, player.level());
            }
        }
    }
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer() && event.player != null) {
            PlayerVoiceManager.checkAndPlayReloadingSoundOnTick(event.player);
        }
    }
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof SwordItem ||
            stack.getItem() instanceof AxeItem ||
            stack.getItem() instanceof me.cryo.zombierool.core.system.WeaponImplementations.MeleeWeaponItem) {
            BlockState state = event.getLevel().getBlockState(event.getPos());
            if (state.getBlock() instanceof me.cryo.zombierool.block.system.DefenseDoorSystem.BaseDefenseDoor ||
                state.getBlock() instanceof me.cryo.zombierool.block.ObstacleDoorBlock) {
                event.setCanceled(true); 
                if (!event.getLevel().isClientSide()) {
                    double reach = player.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
                    Vec3 start = player.getEyePosition(1.0F);
                    Vec3 look = player.getViewVector(1.0F);
                    Vec3 end = start.add(look.scale(reach));
                    AABB searchBox = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
                    List<LivingEntity> entities = player.level().getEntitiesOfClass(
                        LivingEntity.class, searchBox,
                        e -> e != player && !e.isSpectator() && e.isAlive() && e.isPickable()
                    );
                    LivingEntity target = null;
                    double minDistance = Double.MAX_VALUE;
                    for (LivingEntity e : entities) {
                        AABB bbox = e.getBoundingBox().inflate(e.getPickRadius());
                        Optional<Vec3> clip = bbox.clip(start, end);
                        if (clip.isPresent() || bbox.contains(start)) {
                            double dist = start.distanceTo(clip.orElse(start));
                            if (dist < minDistance) {
                                target = e;
                                minDistance = dist;
                            }
                        }
                    }
                    if (target != null) {
                        player.attack(target);
                        player.resetAttackStrengthTicker();
                    }
                }
            }
        }
    }
}
