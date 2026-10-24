package me.cryo.zombierool.gameplay;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Shared spatial gravity, including a dimension-local snapshot for client prediction. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class LunarPhysics {
    private record Snapshot(LunarEnvironment zones,double gravity){}
    private static final Map<Level,Snapshot> CLIENT=new WeakHashMap<>();
    private static final Map<Player,CompoundTag> SENT=new WeakHashMap<>();
    public static double gravityAt(Entity entity){
        if(entity instanceof Player p&&(p.isCreative()||p.isSpectator()))return 1;
        if(entity.level() instanceof ServerLevel level){
            var settings=me.cryo.zombierool.config.WorldConfig.get(level).getAddonConfig().getCompound("zombierool:lunar");
            return LunarEnvironment.get(level).at(entity.getX(),entity.getEyeY(),entity.getZ(),new LunarEnvironment.Conditions(LunarEnvironment.gravity(settings.getDouble("gravity")),false)).gravity();
        }
        var snapshot=CLIENT.get(entity.level());
        return snapshot==null?1:snapshot.zones.at(entity.getX(),entity.getEyeY(),entity.getZ(),new LunarEnvironment.Conditions(snapshot.gravity,false)).gravity();
    }
    public static void receive(Level level,CompoundTag n){if(level!=null)CLIENT.put(level,new Snapshot(LunarEnvironment.load(n),n.getDouble("defaultGravity")));}
    public static void sync(net.minecraft.server.level.ServerPlayer p){
        var n=LunarEnvironment.get(p.serverLevel()).describe();
        n.putDouble("defaultGravity",LunarEnvironment.gravity(me.cryo.zombierool.config.WorldConfig.get(p.serverLevel()).getAddonConfig().getCompound("zombierool:lunar").getDouble("gravity")));
        n.putString("dimension",p.level().dimension().location().toString());
        if(!n.equals(SENT.get(p))){SENT.put(p,n.copy());LunarEquipment.sendEnvironment(p,n);}
    }
    public static void forget(Player p){SENT.remove(p);}
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload e){if(e.getLevel().isClientSide())CLIENT.remove(e.getLevel());else SENT.keySet().removeIf(p->p.level()==e.getLevel());}
    @SubscribeEvent public static void living(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e){
        LivingEntity entity=e.getEntity();LunarEquipment.applyGravity(entity,gravityAt(entity));
    }
    private LunarPhysics(){}
}
