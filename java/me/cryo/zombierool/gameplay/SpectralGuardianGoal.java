package me.cryo.zombierool.gameplay;
import me.cryo.zombierool.entity.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
/** One threat ranking across enemy classes prevents class-priority target thrashing. */
public final class SpectralGuardianGoal extends Goal {
    private final WhiteKnightEntity knight;
    private int nextScan,unseen;
    public SpectralGuardianGoal(WhiteKnightEntity knight){this.knight=knight;setFlags(java.util.EnumSet.of(Flag.TARGET));}
    public boolean canUse(){return knight.isTame()&&knight.getOwner()!=null;}
    public boolean canContinueToUse(){return canUse();}
    public void tick(){
        if(knight.tickCount<nextScan)return;nextScan=knight.tickCount+10;
        var owner=knight.getOwner();if(owner==null)return;var current=knight.getTarget();
        if(current!=null&&current.isAlive()&&!knight.hasLineOfSight(current)){unseen+=10;if(unseen<40)return;}else unseen=0;
        Mob best=null;double rank=Double.POSITIVE_INFINITY;
        for(var enemy:knight.level().getEntitiesOfClass(AbstractZombieRoolEntity.class,knight.getBoundingBox().inflate(24),e->e.isAlive()&&knight.hasLineOfSight(e))){
            double score=knight.distanceToSqr(enemy)+owner.distanceToSqr(enemy)*.5;
            if(enemy.getTarget()==owner)score*=.35;if(enemy==current)score*=.65;
            if(score<rank){rank=score;best=enemy;}
        }
        knight.setTarget(best);
    }
    public void stop(){knight.setTarget(null);}
}
