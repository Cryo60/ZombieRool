package local.hotfixtest;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.core.system.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.init.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;

public final class WonderWeaponGameplayTest {
    private static int count;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);count++;System.out.println("WONDER CHECK: "+message);}
    private static ZombieEntity enemy(ServerLevel level,double x,double z){var zombie=ZombieroolModEntities.ZOMBIE.get().create(level);zombie.setPos(x,80,z);zombie.setEmergenceTicks(0);zombie.setNoAi(false);zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);zombie.setHealth(1024);zombie.getPersistentData().putBoolean("zombierool:no_gore",true);level.addFreshEntity(zombie);return zombie;}
    public static void run(ServerLevel level,ServerPlayer a,ServerPlayer b)throws Exception{
        for(int x=520;x<=545;x++)for(int z=0;z<=7;z++){level.setBlock(new BlockPos(x,78,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),3);level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);for(int y=80;y<87;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        a.setGameMode(GameType.ADVENTURE);a.setPos(523.5,80,3.5);a.setYRot(-90);a.setYHeadRot(-90);a.setXRot(0);a.getPersistentData().putBoolean("zr_prefer_zr_weapons",true);
        for(String id:new String[]{"winters_howl","vr11"}){
            var normal=WeaponFacade.createWeaponStack(id,false,a);var upgraded=WeaponFacade.createWeaponStack(id,true,a);
            check(normal.getItem() instanceof me.cryo.zombierool.item.ClassicWonderWeaponItem,"Registered special shooting implementation: "+id);
            check(WeaponFacade.getMaxAmmo(normal)==(id.equals("vr11")?3:6)&&WeaponFacade.getMaxAmmo(upgraded)==(id.equals("vr11")?6:9),"Normal and Pack-a-Punch clips: "+id);
            check(WeaponFacade.getMaxReserve(normal)==(id.equals("vr11")?9:24)&&WeaponFacade.getMaxReserve(upgraded)==(id.equals("vr11")?18:36),"Normal and Pack-a-Punch reserves: "+id);
            check(WeaponFacade.getDefinition(normal).is_wonder_weapon,"Wonder flag supports mystery-box and catalog rules: "+id);
        }
        var cold=enemy(level,528.5,3.5);double speed=cold.getAttributeValue(Attributes.MOVEMENT_SPEED);
        WonderWeaponEffects.frost(cold,a,false,0);check(!WonderWeaponEffects.frozen(cold)&&cold.getAttributeValue(Attributes.MOVEMENT_SPEED)<speed,"First winter hit slows without freezing");
        WonderWeaponEffects.frost(cold,a,false,0);check(WonderWeaponEffects.frozen(cold)&&cold.getAttributeValue(Attributes.MOVEMENT_SPEED)==0&&cold.getTicksFrozen()>=130,"Second winter hit freezes with synchronized visual ice");
        cold.setTarget(a);check(cold.getTarget()==null,"Frozen zombie cannot resume player targeting");
        long now=level.getGameTime();level.getServer().getWorldData().overworldData().setGameTime(now+101);WonderWeaponEffects.tick(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(cold));
        check(!WonderWeaponEffects.frozen(cold)&&Math.abs(cold.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed)<.00001&&cold.getTicksFrozen()==0,"Winter freeze expires and restores movement and appearance");level.getServer().getWorldData().overworldData().setGameTime(now);cold.discard();
        var boss=enemy(level,529.5,3.5);boss.addTag("zr_boss");WonderWeaponEffects.frost(boss,a,true,1);WonderWeaponEffects.frost(boss,a,true,1);
        check(!WonderWeaponEffects.frozen(boss)&&boss.getAttributeValue(Attributes.MOVEMENT_SPEED)>0,"Winter slows a boss but never immobilizes it");WonderWeaponEffects.convert(boss,a,true);check(boss.isAlive(),"V-R11 cannot convert a boss");boss.discard();
        var victim=enemy(level,528.5,3.5);WonderWeaponEffects.convert(victim,a,false);
        var humans=level.getEntitiesOfClass(VrilHumanEntity.class,new AABB(520,79,0,545,86,7));check(victim.isRemoved()&&humans.size()==1,"V-R11 replaces a zombie with one living human");var human=humans.get(0);
        check(human.getMaxHealth()==20&&human.getHealth()==20,"Human decoy has twenty health, independent of the zombie's wave-scaled health");
        int limbs=level.getEntitiesOfClass(GorePieceEntity.class,new AABB(520,79,0,545,86,7)).size();
        me.cryo.zombierool.core.manager.GoreManager.triggerHeadExplosion(human);
        me.cryo.zombierool.core.manager.GoreManager.triggerArmExplosion(human,me.cryo.zombierool.core.manager.GoreManager.Limb.LEFT_ARM);
        me.cryo.zombierool.core.manager.GoreManager.triggerLegsExplosion(human);
        check(java.util.Arrays.stream(me.cryo.zombierool.core.manager.GoreManager.Limb.values()).noneMatch(l->me.cryo.zombierool.core.manager.GoreManager.hasLostLimb(human,l))&&level.getEntitiesOfClass(GorePieceEntity.class,new AABB(520,79,0,545,86,7)).size()==limbs,"Human cannot lose limbs or spawn gore pieces even from explicit weapon dismemberment calls");
        var chaser=enemy(level,529.5,3.5);check(WonderWeaponEffects.decoy(chaser,100)==human&&WonderWeaponEffects.decoy(chaser,.01)==human,"Human attracts nearby zombies and keeps their attention through the initial commitment");
        WonderWeaponEffects.convert(human,a,true);check(human.isAlive()&&human.lazarusHits==2,"Second Lazarus shot charges the converted human");float health=chaser.getHealth();WonderWeaponEffects.convert(human,a,true);check(!human.isAlive()&&!human.isRemoved()&&chaser.getHealth()<health,"Third Lazarus shot explodes the human, leaves an intact corpse and damages nearby enemies");chaser.discard();
        var expiryVictim=enemy(level,527.5,5.5);WonderWeaponEffects.convert(expiryVictim,a,false);
        var expiry=level.getEntitiesOfClass(VrilHumanEntity.class,new AABB(520,79,0,545,86,7),VrilHumanEntity::isAlive).get(0);
        int blood=level.getEntitiesOfClass(BloodDecalEntity.class,new AABB(520,79,0,545,86,7)).size();
        expiry.life=1;expiry.tick();
        check(!expiry.isAlive()&&!expiry.isRemoved()&&level.getEntitiesOfClass(BloodDecalEntity.class,new AABB(520,79,0,545,86,7)).size()>blood,"Natural expiry is a real death with blood and a persistent human corpse");
        expiry.setPos(527.5,83,5.5);for(int i=0;i<35;i++)expiry.tick();
        check(!expiry.isRemoved()&&expiry.getY()<81&&expiry.deathTime==19,"Intact human corpse falls to the floor and lingers after the vanilla twenty-tick death");
        WonderWeaponEffects.convert(b,a,false);check(me.cryo.zombierool.bonuses.BonusManager.isZombieBloodActive(b),"V-R11 camouflage reuses every existing zombie-blood targeting check");
        WonderWeaponEffects.convert(b,a,true);check(b.getPersistentData().getLong("zr_vril_damage_until")==level.getGameTime()+200,"Lazarus grants a ten-second ally damage buff");b.getPersistentData().remove("zr_vril_camouflage_until");b.getPersistentData().remove("zr_vril_damage_until");
        var blocked=enemy(level,532.5,3.5);for(int y=80;y<84;y++)level.setBlock(new BlockPos(530,y,3),Blocks.STONE.defaultBlockState(),3);health=blocked.getHealth();
        WonderWeaponEffects.fire(a,WeaponFacade.createWeaponStack("winters_howl",false,a),true,50,20);check(blocked.getHealth()==health&&!WonderWeaponEffects.frozen(blocked),"Winter shots cannot hit enemies through a solid wall");
        WonderWeaponEffects.fire(a,WeaponFacade.createWeaponStack("vr11",false,a),false,0,40);check(blocked.isAlive(),"V-R11 beam cannot convert through a wall");blocked.discard();
        for(int y=80;y<84;y++)level.setBlock(new BlockPos(530,y,3),Blocks.AIR.defaultBlockState(),3);
        var shot=enemy(level,530.5,3.5);var gun=WeaponFacade.createWeaponStack("vr11",false,a);WonderWeaponEffects.fire(a,gun,false,0,40);check(shot.isRemoved(),"Actual aimed V-R11 shooting traces and converts an enemy");
        DroppedItemCleanup.clear(level);check(level.getEntitiesOfClass(VrilHumanEntity.class,new AABB(520,79,0,545,86,7)).isEmpty(),"Match cleanup removes temporary V-R11 humans");
        a.setGameMode(GameType.CREATIVE);System.out.println("WONDER WEAPON GAMEPLAY PASS: "+count+" assertions");
    }
}
