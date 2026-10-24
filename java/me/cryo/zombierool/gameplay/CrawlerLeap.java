package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.config.SpecialEnemyConfig;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Shared, dodgeable physical leap for either crawler appearance. */
public final class CrawlerLeap {
    private final AbstractZombieRoolEntity mob;
    private int cooldown=60,windup,flight;
    private boolean hit;
    private Vec3 direction=Vec3.ZERO;
    private static final UUID GROUND_SPEED=UUID.fromString("80268e99-64dc-4b54-8a23-b07fd93caea2");
    private static final AttributeModifier GROUND_MODIFIER=new AttributeModifier(GROUND_SPEED,"Ground crawler pace",-.3,AttributeModifier.Operation.MULTIPLY_TOTAL);
    public CrawlerLeap(AbstractZombieRoolEntity mob){this.mob=mob;}
    public static boolean isCrawler(net.minecraft.world.entity.Entity entity){return entity instanceof CrawlerEntity||entity instanceof SpecialEnemyEntity s&&EncounterAbilities.kind(s).equals("geist");}
    public static boolean enabled(ServerLevel l){return SpecialEnemyConfig.get(l).getString("crawlerBehavior").equals("leaper");}
    public boolean busy(){return windup>0||flight>0;}
    public int phase(){return windup>0?1:flight>0?2:0;}
    private boolean valid(ServerPlayer p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID())&&!me.cryo.zombierool.block.system.TeleporterTravel.bossProtected(p);}
    public void reset(){windup=flight=0;sync();}
    private void sync(){if(mob instanceof CrawlerEntity c)c.setLeapPhase(phase());}
    private void animation(String name,int ticks){if(mob instanceof SpecialEnemyEntity s)s.playBossAnimation(name,ticks);}
    public void tick(ServerLevel level){
        if(!WaveManager.isGameRunning()||WaveManager.isPausedByPlayer()||!mob.isAlive()||mob.isNoAi()||mob.getEmergenceTicks()>0)return;
        var speed=mob.getAttribute(Attributes.MOVEMENT_SPEED);boolean leaper=enabled(level);
        if(speed!=null){if(leaper)speed.removeModifier(GROUND_SPEED);else if(!speed.hasModifier(GROUND_MODIFIER))speed.addTransientModifier(GROUND_MODIFIER);}
        if(!leaper||EquipmentEffects.active(mob)||WonderWeaponEffects.frozen(mob)||FiestaFreeze.frozen(mob)||TraversalMotion.active(mob)){reset();return;}
        if(cooldown>0)cooldown--;
        if(windup>0){mob.getNavigation().stop();mob.setDeltaMovement(0,mob.getDeltaMovement().y,0);if(--windup==0){mob.setDeltaMovement(direction.scale(.8).add(0,.45,0));mob.hasImpulse=true;hit=false;flight=24;animation("leap",24);}sync();return;}
        if(flight>0){
            mob.getNavigation().stop();if(!mob.onGround())mob.setDeltaMovement(direction.x*.8,mob.getDeltaMovement().y,direction.z*.8);flight--;
            if(!hit)for(var p:level.players())if(valid(p)&&mob.getBoundingBox().inflate(.1).intersects(p.getBoundingBox())){FixedPlayerDamage.mobHit(p,mob,(float)SpecialEnemyConfig.get(level).getDouble("geistLeapDamage"));hit=true;me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnCrawlerLeapHit",mob.getUUID().toString(),p.getUUID().toString());if(mob instanceof SpecialEnemyEntity)me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnGeistLeapHit",mob.getUUID().toString(),p.getUUID().toString());break;}
            if(mob.onGround()&&flight<20)flight=0;sync();return;
        }
        var target=mob.getTarget() instanceof ServerPlayer p&&valid(p)?p:null;
        if(target!=null&&cooldown==0&&mob.onGround()&&mob.distanceToSqr(target)>9&&mob.distanceToSqr(target)<81&&mob.hasLineOfSight(target)){
            direction=target.position().subtract(mob.position()).multiply(1,0,1).normalize();windup=14;cooldown=100;animation("crouch",14);sync();
            if(mob instanceof SpecialEnemyEntity){EncounterAudio.play(mob,"geist_attack",.8f);me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnGeistLeap",mob.getUUID().toString(),target.getUUID().toString());}
            me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnCrawlerLeap",mob.getUUID().toString(),target.getUUID().toString());
        }
    }
}
