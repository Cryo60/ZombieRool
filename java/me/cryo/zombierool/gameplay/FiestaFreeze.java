package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.MrChiefEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.Map;
import java.util.WeakHashMap;

/** Freeze mobs during the party, preserving each mob's previous AI setting. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class FiestaFreeze {
    private record Hold(Vec3 position,boolean noAi) {}
    private static final Map<Mob,Hold> SERVER_HELD=new WeakHashMap<>(),CLIENT_HELD=new WeakHashMap<>();
    private static final String AI_BEFORE="zr_fiesta_ai_before";
    private static Map<Mob,Hold> held(Mob mob){return mob.level().isClientSide?CLIENT_HELD:SERVER_HELD;}
    public static boolean previousNoAi(CompoundTag data,boolean current){
        return data.contains(AI_BEFORE,net.minecraft.nbt.Tag.TAG_BYTE)?data.getBoolean(AI_BEFORE):current;
    }
    public static boolean frozen(Mob mob){
        if(mob instanceof MrChiefEntity||!mob.isAlive())return false;
        return mob.level() instanceof ServerLevel level?MatchAtmosphereSync.active(level)
                :mob.level().isClientSide&&me.cryo.zombierool.client.MatchAtmosphere.active();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void tick(LivingEvent.LivingTickEvent event){
        if(!(event.getEntity() instanceof Mob mob))return;
        if(!frozen(mob)){release(mob);return;}
        Hold hold=held(mob).computeIfAbsent(mob,m->{
            boolean original=previousNoAi(m.getPersistentData(),m.isNoAi());
            if(!m.level().isClientSide)m.getPersistentData().putBoolean(AI_BEFORE,original);
            return new Hold(m.position(),original);
        });
        if(!mob.level().isClientSide){mob.setNoAi(true);mob.setTarget(null);mob.getNavigation().stop();}
        mob.xxa=mob.yya=mob.zza=0;mob.setDeltaMovement(Vec3.ZERO);
        mob.setPos(hold.position.x,hold.position.y,hold.position.z);
        event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent event){
        if(event.getSource().getEntity() instanceof Mob attacker&&frozen(attacker))event.setCanceled(true);
    }
    private static void release(Mob mob){
        Hold hold=held(mob).remove(mob);
        if(!mob.level().isClientSide&&(hold!=null||mob.getPersistentData().contains(AI_BEFORE))){
            mob.setNoAi(hold!=null?hold.noAi:previousNoAi(mob.getPersistentData(),mob.isNoAi()));
            mob.getPersistentData().remove(AI_BEFORE);mob.setDeltaMovement(Vec3.ZERO);
        }
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){
        if(event.getEntity() instanceof Mob mob&&!event.getLevel().isClientSide&&!frozen(mob))release(mob);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){
        for(Mob mob:java.util.List.copyOf(SERVER_HELD.keySet()))release(mob);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){
        for(Mob mob:java.util.List.copyOf(event.getLevel().isClientSide()?CLIENT_HELD.keySet():SERVER_HELD.keySet()))if(mob.level()==event.getLevel())release(mob);
    }
}
