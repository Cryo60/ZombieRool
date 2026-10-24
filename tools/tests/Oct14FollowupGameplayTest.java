package local.hotfixtest;
import java.util.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.init.*;
import me.cryo.zombierool.bonuses.BonusManager;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.*;

public final class Oct14FollowupGameplayTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;System.out.println("OCT14 FOLLOWUP CHECK: "+message);}
    private static ZombieEntity zombie(ServerLevel level,Vec3 pos){var mob=ZombieroolModEntities.ZOMBIE.get().create(level);mob.getPersistentData().putBoolean("zr_debug_spawn",true);mob.getPersistentData().putBoolean("zombierool:no_gore",true);mob.setPos(pos);level.addFreshEntity(mob);for(int i=0;i<SpawnEmergence.DURATION;i++)SpawnEmergence.tick(new LivingEvent.LivingTickEvent(mob));mob.setOnGround(true);return mob;}
    private static Vec3 bonusPosition(Object value)throws Exception{var field=value.getClass().getField("position");field.setAccessible(true);return (Vec3)field.get(value);}
    @SuppressWarnings("unchecked") public static void run(ServerLevel level,ServerPlayer original)throws Exception {
        level.getServer().getWorldData().setDifficulty(net.minecraft.world.Difficulty.NORMAL);var base=new BlockPos(1488,80,0);var config=me.cryo.zombierool.config.WorldConfig.get(level);boolean enabled=config.isBonusDropsEnabled(),cheats=WaveManager.areCheatsUsed();
        var field=BonusManager.class.getDeclaredField("ACTIVE_BONUSES_IN_WORLD");field.setAccessible(true);var bonuses=(Map<UUID,Object>)field.get(null);var saved=new HashMap<>(bonuses);bonuses.clear();
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"FollowupProbe"));level.addNewPlayer(player);var protection=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");protection.setAccessible(true);protection.setInt(player,0);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.absMoveTo(base.getX()+.5,base.getY(),.5,0,0);
        for(int x=-2;x<=18;x++)for(int z=-3;z<=3;z++){level.setBlock(base.offset(x,-2,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),3);level.setBlock(base.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<=4;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        try {
            var source=player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            check(level.getServer().getCommands().getDispatcher().execute("zombierool bonus max_ammo ~2 ~3 ~4",source)==1,"Brigadier accepts relative bonus coordinates");
            check(bonuses.size()==1&&bonusPosition(bonuses.values().iterator().next()).distanceToSqr(player.position().add(2,3,4))<.001,"The bonus is placed at the exact requested coordinates");
            DebugConsole.execute(player,"bonus max_ammo ~5 ~1 ~2");check(bonuses.size()==2,"Explicit debug bonuses bypass the ordinary one-per-type cap");
            DebugConsole.execute(player,"bonus random");check(bonuses.size()==3,"Secret console supports immediate random bonuses");
            DebugConsole.execute(player,"bonus_next nuke");check(QueuedBonusDrops.pending(player).equals("nuke"),"A selected bonus is queued for the next kill");
            var pig=net.minecraft.world.entity.EntityType.PIG.create(level);pig.getPersistentData().putBoolean("zr_debug_spawn",true);pig.setPos(player.position());level.addFreshEntity(pig);pig.hurt(level.damageSources().playerAttack(player),10000);pig.discard();
            check(QueuedBonusDrops.pending(player).equals("nuke"),"A passive kill does not consume a queued enemy reward");
            config.setBonusDropsEnabled(false);int before=bonuses.size();var victim=zombie(level,Vec3.atBottomCenterOf(base.offset(4,0,0)));victim.hurt(level.damageSources().playerAttack(player),10000);
            check(!victim.isAlive()&&bonuses.size()==before+1&&QueuedBonusDrops.pending(player).isEmpty(),"One real enemy kill guarantees the chosen bonus even when ordinary drops are disabled");
            victim.die(level.damageSources().playerAttack(player));check(bonuses.size()==before+1,"A corpse cannot consume or duplicate the forced reward");
            DebugConsole.execute(player,"bonus_next");check(QueuedBonusDrops.pending(player).equals("random"),"Next-kill bonus defaults to random");DebugConsole.execute(player,"bonus_next clear");check(QueuedBonusDrops.pending(player).isEmpty(),"A queued bonus can be canceled");
            level.getServer().getCommands().getDispatcher().execute("zombierool bonus next max_ammo",source);check(QueuedBonusDrops.pending(player).equals("max_ammo"),"Brigadier also queues a next-kill reward");
            level.getServer().getCommands().getDispatcher().execute("zombierool bonus clear_next",source);check(QueuedBonusDrops.pending(player).isEmpty(),"Brigadier can cancel a next-kill reward");
            var burning=zombie(level,Vec3.atBottomCenterOf(base.offset(2,0,2)));double speed=burning.getAttributeValue(Attributes.MOVEMENT_SPEED);boolean sprint=burning.isSuperSprinter();
            InfernoZombie.ordinaryBurn(burning,4);
            for(int i=0;i<8;i++){var event=new LivingAttackEvent(burning,level.damageSources().onFire(),1);InfernoZombie.attack(event);check(!event.isCanceled(),"Ordinary flamethrower fire remains damaging");InfernoZombie.tick(new LivingEvent.LivingTickEvent(burning));}
            check(!burning.isInferno()&&burning.isSuperSprinter()==sprint&&burning.getAttributeValue(Attributes.MOVEMENT_SPEED)==speed,"Flamethrower burning preserves the zombie's original speed and sprint state");
            InfernoZombie.ignite(burning);check(burning.isInferno(),"Explicit napalm/inferno encounters still work");burning.discard();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setHealth(player.getMaxHealth());var pursuit=zombie(level,Vec3.atBottomCenterOf(base.offset(1,0,0)));var origin=pursuit.position();for(int y=0;y<3;y++)level.setBlock(base.offset(10,y,0),Blocks.STONE.defaultBlockState(),3);
            player.absMoveTo(base.getX()+10.5,83,.5,0,0);me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.invalidate(pursuit);me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.chooseAndSet(pursuit,player);
            check(pursuit.getTarget()==null,"An inaccessible player makes the zombie wait without a pursuit target");
            for(int i=0;i<80;i++){pursuit.tickCount++;me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.chooseAndSet(pursuit,player);}
            check(pursuit.position().distanceToSqr(origin)<.001,"Waiting for an inaccessible player never relocates the zombie");
            player.absMoveTo(base.getX()+8.5,80,.5,0,0);me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.chooseAndSet(pursuit,player);
            check(pursuit.getTarget()==player&&pursuit.position().distanceToSqr(origin)<.001,"Returning to accessible ground resumes the route from the zombie's original position");
            for(int i=0;i<40;i++){Vec3 previous=pursuit.position();pursuit.tick();check(pursuit.position().distanceToSqr(previous)<1,"Resumed pursuit moves continuously without a teleport");}
            player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player){@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}};DebugPlayerControls.setNoclip(player,true);player.getAbilities().flying=true;player.absMoveTo(base.getX()+.5,80,2.5,0,0);level.setBlock(base.offset(1,0,2),Blocks.STONE.defaultBlockState(),3);level.setBlock(base.offset(1,1,2),Blocks.STONE.defaultBlockState(),3);player.connection.resetPosition();
            player.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(base.getX()+1.5,80,2.5,false));
            check(Math.abs(player.getX()-base.getX()-1.5)<.001,"The actual movement-packet handler accepts authorized noclip inside a wall");
        }finally{
            DebugPlayerControls.reset(player);QueuedBonusDrops.clear(player);level.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);bonuses.clear();bonuses.putAll(saved);config.setBonusDropsEnabled(enabled);WaveManager.setCheatsUsed(cheats);
            for(var entity:level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(base).inflate(25)))entity.discard();
            for(int x=-2;x<=18;x++)for(int z=-3;z<=3;z++)for(int y=-2;y<=4;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        System.out.println("OCT14 FOLLOWUP PASS: "+checks+" checks");
    }
}
