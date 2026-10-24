package me.cryo.zombierool.gameplay;

import java.util.*;
import me.cryo.zombierool.block.system.TraversalSystem;
import me.cryo.zombierool.entity.SpecialEnemyEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Preserve transport history across combat/machine interruptions; permit returns when the target crosses back. */
public final class BossTraversalHistory {
    private record Visit(BlockPos root, Vec3 departure, Vec3 arrival, Vec3 objective) {}
    private static final class History {
        UUID target; Vec3 objective;
        final List<Visit> visits=new ArrayList<>();
    }
    private static final Map<Mob,History> HISTORY=new WeakHashMap<>();
    private static History history(Mob mob) {
        History h=HISTORY.computeIfAbsent(mob,m->new History());
        var target=mob.getTarget();UUID id=target==null?null:target.getUUID();
        Vec3 objective=target==null?null:target.position();
        if(id!=null&&!Objects.equals(id,h.target)){h.visits.clear();h.target=id;}
        if(objective!=null)h.objective=objective;
        return h;
    }
    public static void objective(Mob mob,net.minecraft.world.entity.LivingEntity target) {
        History h=history(mob);
        if(!Objects.equals(h.target,target.getUUID()))h.visits.clear();
        h.target=target.getUUID();h.objective=target.position();
    }
    private static boolean targetCrossedBack(Visit visit,Vec3 objective) {
        if(objective==null||visit.objective==null)return false;
        Vec3 direction=visit.arrival.subtract(visit.departure);
        double length=direction.length();if(length<1)return false;
        Vec3 middle=visit.departure.add(visit.arrival).scale(.5);
        // Small movements on the same side and switches to machine goals cannot reset a circuit.
        return visit.objective.subtract(middle).dot(direction)>=0
            &&objective.subtract(middle).dot(direction)<-length;
    }
    public static boolean allows(Mob mob,TraversalSystem.Route route) {
        if(!(mob instanceof SpecialEnemyEntity boss)||!boss.isBoss()||CrawlerLeap.isCrawler(mob))return true;
        History h=history(mob);
        for(Visit v:h.visits){
            boolean returns=v.departure.distanceToSqr(route.end())<4;
            if((v.root.equals(route.anchor().getBlockPos())||returns)
                &&!(returns&&targetCrossedBack(v,h.objective)))return false;
        }
        return true;
    }
    public static void started(Mob mob,TraversalSystem.Route route) {
        if(!(mob instanceof SpecialEnemyEntity boss)||!boss.isBoss()||CrawlerLeap.isCrawler(mob))return;
        History h=history(mob);
        h.visits.removeIf(v->v.departure.distanceToSqr(route.end())<4&&targetCrossedBack(v,h.objective));
        h.visits.add(new Visit(route.anchor().getBlockPos().immutable(),route.start(),route.end(),h.objective));
        if(h.visits.size()>32)h.visits.remove(0);
    }
}
