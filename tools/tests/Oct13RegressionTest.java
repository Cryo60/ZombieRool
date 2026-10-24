package local.hotfixtest;

import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.init.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class Oct13RegressionTest {
    private static int count;
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);count++;System.out.println("OCT13 CHECK: "+text);}
    private static java.lang.reflect.Field field(Class<?> c,String name)throws Exception{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    public static void run(ServerLevel level,ServerPlayer player)throws Exception{
        var running=field(WaveManager.class,"gameRunning");var oldRunning=running.get(null);var oldMode=player.gameMode.getGameModeForPlayer();
        var originals=(java.util.Map)field(MatchWorldJournal.class,"ORIGINAL").get(null);var saved=new java.util.HashMap(originals);originals.clear();
        var pos=new BlockPos(1350,80,0);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        var door=(DefenseDoorSystem.DefenseDoorBlock)me.cryo.zombierool.core.registry.ZRBlocks.IRON_DEFENSE_DOOR.get();
        var lower=door.defaultBlockState();var upper=lower.setValue(DefenseDoorSystem.DefenseDoorBlock.HALF,DoubleBlockHalf.UPPER);
        level.setBlock(pos,lower,2);level.setBlock(pos.above(),upper,2);
        try{
            running.set(null,true);door.updateStage(level,pos,0);
            check(level.getBlockState(pos).getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE)==0,"Iron defense door fixture is fully damaged");
            MatchWorldJournal.note(level,new BlockPos(1350,level.getMinBuildHeight()-1,0));
            MatchWorldJournal.note(level,new BlockPos(1350,level.getMaxBuildHeight(),0));
            check(originals.keySet().stream().noneMatch(p->level.isOutsideBuildHeight((BlockPos)p)),"Journal rejects edits outside both world height boundaries");
            Object snapshot=originals.get(pos);check(snapshot!=null,"Damage records the original iron door");
            originals.put(new BlockPos(1350,level.getMinBuildHeight()-1,0),snapshot);
            originals.put(new BlockPos(1350,level.getMaxBuildHeight(),0),snapshot);
            MatchWorldJournal.restore(level);
            check(level.getBlockState(pos).getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE)==5&&level.getBlockState(pos.above()).getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE)==5,"Restoration skips old invalid records and repairs both iron door halves");
            player.setGameMode(GameType.CREATIVE);
            check(door.getCollisionShape(lower,level,pos,CollisionContext.of(player)).isEmpty()&&door.getCollisionShape(upper,level,pos.above(),CollisionContext.of(player)).isEmpty(),"Creative players cross both halves of iron defense doors");
            player.setGameMode(GameType.ADVENTURE);check(!door.getCollisionShape(lower,level,pos,CollisionContext.of(player)).isEmpty(),"Defense door still blocks survival/adventure players");
            check(SpecialEnemySpawns.bossCount(0,1)==1&&SpecialEnemySpawns.bossCount(8,1)==1,"Solo boss quota stays one at high rounds");
            check(SpecialEnemySpawns.bossCount(0,4)==1&&SpecialEnemySpawns.bossCount(2,4)==2&&SpecialEnemySpawns.bossCount(5,4)==3,"Cooperative boss quota follows T6 encounter progression");
            for(var type:java.util.List.of(ZombieroolModEntities.PANZER.get(),ZombieroolModEntities.BRUTUS.get())){
                var boss=type.create(level);boss.moveTo(1354,80,0,0,0);boss.getPersistentData().putBoolean("zr_special_spawner",true);level.addFreshEntity(boss);boss.setNoAi(true);
                if(boss.isPanzer())check(boss.capture(player),"Panzer captures a player before the teleporter shock fixture");float hp=boss.getHealth();MapDeviceSystem.teleporterShock(boss,player);
                check(boss.isAlive()&&Math.abs(boss.getHealth()-hp*.9f)<.1,"Teleporter removes ten percent of "+boss.kind()+" HP instead of killing it");
                check(!boss.doHurtTarget(player)&&!boss.hasCapturedPlayer(),"Teleporter stun prevents boss melee and releases grabs");boss.discard();
            }
            player.getPersistentData().putLong("zr_teleporter_lock",level.getGameTime()+40);
            check(TeleporterTravel.bossProtected(player),"Arrival grants temporary boss protection");
            var boss=ZombieroolModEntities.BRUTUS.get().create(level);
            var attack=new net.minecraftforge.event.entity.living.LivingAttackEvent(player,level.damageSources().mobAttack(boss),100);
            TeleporterTravel.onBossAttack(attack);check(attack.isCanceled(),"Boss damage is canceled during teleporter arrival protection");
            player.getPersistentData().remove("zr_teleporter_lock");check(!TeleporterTravel.bossProtected(player),"Teleporter protection ends and does not remain globally active");
            System.out.println("OCT13 REGRESSION PASS: "+count+" assertions");
        }finally{originals.clear();originals.putAll(saved);running.set(null,oldRunning);player.setGameMode(oldMode);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);}
    }
}
