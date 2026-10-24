package local.actorcombat;

import com.mojang.authlib.GameProfile;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.entity.MapActorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod("actorcombat")
public final class MapActorCombatTest {
    private net.minecraft.server.MinecraftServer server;private ServerLevel level;private FakePlayer player;private net.minecraft.world.entity.Mob actor;private int age,index,checks;private boolean done,webObserved;private double startX;private net.minecraft.world.entity.Mob rival;private float bossHealth,rivalHealth;
    private final String[] kinds={"balnazzar","baron_rivendare","crypt_fiend","scarlet_zombie","builtin_ghoul","test_retaliator","baron_rivendare","balnazzar","boss_factions"};
    public MapActorCombatTest(){MinecraftForge.EVENT_BUS.addListener(this::start);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PORTABLE CHECK: "+text);}
    @SuppressWarnings("unchecked") private void start(ServerStartedEvent event){server=event.getServer();level=server.overworld();try{
        for(int x=-20;x<=20;x++)for(int z=-12;z<=12;z++)for(int y=78;y<=88;y++)level.setBlock(new BlockPos(x,y,z),y==78?me.cryo.zombierool.init.ZombieroolModBlocks.PATH.get().defaultBlockState():y==79?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        MapActors.end(level);
        player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"ActorMeleeTarget")){@Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}};level.addNewPlayer(player);player.setGameMode(GameType.SURVIVAL);var spawnProtection=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");spawnProtection.setAccessible(true);spawnProtection.setInt(player,0);player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);
        var players=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");players.setAccessible(true);((List<ServerPlayer>)players.get(server.getPlayerList())).add(player);
        var ids=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");ids.setAccessible(true);((Map<UUID,ServerPlayer>)ids.get(server.getPlayerList())).put(player.getUUID(),player);
        me.cryo.zombierool.scripting.LuaScriptManager.loadScripts(level);
        var roamer=MapActors.get(level).profile("builtin_ghoul");roamer.putString("behavior","wander");roamer.putDouble("aggroRadius",2);roamer.putDouble("leashRadius",0);roamer.putDouble("wanderRadius",1);MapActors.get(level).put(level,"test_retaliator",roamer);
        next();
    }catch(Throwable t){fail(t);}}
    private void next()throws Exception{
        MapActors.end(level);player.removeAllEffects();webObserved=false;player.setPos(4.5,80,.5);player.setHealth(1000);player.invulnerableTime=0;
        if(index==8){player.setGameMode(GameType.CREATIVE);player.setPos(18.5,80,10.5);var first=MapActors.get(level).spawn(level,"baron_rivendare",new net.minecraft.world.phys.Vec3(-3.5,80,.5),"");var second=MapActors.get(level).spawn(level,"balnazzar",new net.minecraft.world.phys.Vec3(3.5,80,.5),"");check(!first.isEmpty()&&!second.isEmpty(),"Both rival bosses spawn for faction combat");actor=(net.minecraft.world.entity.Mob)level.getEntity(UUID.fromString(first));rival=(net.minecraft.world.entity.Mob)level.getEntity(UUID.fromString(second));bossHealth=actor.getHealth();rivalHealth=rival.getHealth();age=0;return;}
        String id;boolean fromMarker=index<2||index>=6;
        if(fromMarker){
            var pos=new BlockPos(index==1?-1:index>=6?-4:-8,80,0);level.setBlock(pos,me.cryo.zombierool.block.system.UniversalSpawnerSystem.ACTOR_MARKER_BLOCK.get().defaultBlockState(),3);
            var rule=MapActorTemplates.marker();rule.putString("profile",kinds[index]);rule.putString("healthMode","fixed");rule.putString("behavior",index>=6?"wander":"melee");rule.putDouble("aggroRadius",index>=6?2:index==1?6:28);rule.putDouble("leashRadius",index>=6?0:48);rule.putDouble("wanderRadius",1);MapActors.get(level).marker(pos,rule);
            var active=WaveManager.class.getDeclaredField("gameRunning");active.setAccessible(true);active.setBoolean(null,true);server.getWorldData().overworldData().setGameTime((level.getGameTime()/10+1)*10);
            var pulse=new TickEvent.LevelTickEvent(net.minecraftforge.fml.LogicalSide.SERVER,TickEvent.Phase.END,level,()->true);MapActors.tick(pulse);MapActors.tick(pulse);
            var spawned=new ArrayList<MapActorEntity>();for(var entity:level.getAllEntities())if(entity instanceof MapActorEntity custom&&custom.marker().equals(Long.toString(pos.asLong())))spawned.add(custom);
            check(spawned.size()==1,"Physical floor spawner creates exactly one boss: "+kinds[index]+" / "+rule.getString("behavior"));actor=spawned.get(0);id=actor.getUUID().toString();actor.getPersistentData().putBoolean("matchActor",false);active.setBoolean(null,false);var running=MapActors.class.getDeclaredField("running");running.setAccessible(true);running.setBoolean(MapActors.get(level),false);level.removeBlock(pos,false);
            var configured=((MapActorEntity)actor).profile();check(configured.getString("behavior").equals(rule.getString("behavior"))&&configured.getDouble("aggroRadius")==rule.getDouble("aggroRadius")&&configured.getString("callback").equals(kinds[index]),"Spawner applies behavior/radius and preserves original boss Lua callbacks: "+kinds[index]);
            check(actor.getPersistentData().getCompound("script").contains(index==0||index==7?"nextCast":"nextAura"),"Boss spawn script initialized through the physical spawner: "+kinds[index]);
        }else{id=MapActors.get(level).spawn(level,kinds[index],new net.minecraft.world.phys.Vec3(index>=3?-3.5:-7.5,80,.5),"");actor=(net.minecraft.world.entity.Mob)level.getEntity(UUID.fromString(id));}
        check(!id.isEmpty(),"Combat spawn: "+kinds[index]);startX=actor.getX();age=0;if(index>=5){check(actor.getTarget()==null,"Wander enemy starts without aggro outside its detection radius");actor.hurt(player.damageSources().playerAttack(player),1);}
        check(actor.getNavigation() instanceof RouteNavigation,"Uses path-block route navigation: "+kinds[index]);
        if(index<3){var before=actor.getDeltaMovement();actor.knockback(1,1,0);check(actor.getDeltaMovement().distanceToSqr(before)<.00001,"Knockback immunity: "+kinds[index]);}
        // No navigation marker exists beyond the arena: a raw vanilla route must not leak out.
        check(actor.getNavigation().createPath(30,80,0,0)==null||!actor.getNavigation().createPath(30,80,0,0).canReach(),"Refuses an unmarked destination: "+kinds[index]);
    }
    private void tick(TickEvent.ServerTickEvent event){if(done||actor==null||event.phase!=TickEvent.Phase.END)return;try{
        age++;if(index==8){if(age==4)check(actor.getTarget()==rival&&rival.getTarget()==actor,"Rivendare and Balnazzar acquire their opposite faction within four ticks");if(actor.getHealth()<bossHealth&&rival.getHealth()<rivalHealth){check(true,"Rivendare and Balnazzar walk into range and damage each other");done=true;System.out.println("PORTABLE RUNTIME PASS: "+checks+" actor combat/navigation checks");server.halt(false);}else if(age>350)throw new AssertionError("Boss faction combat did not deal reciprocal damage: "+actor.position()+" / "+rival.position());return;}
        if(age==25&&(index==1||index>=5)){player.setPos(17.5,80,.5);if(index==1)for(int y=80;y<=83;y++)for(int z=-1;z<=1;z++)level.setBlock(new BlockPos(8,y,z),Blocks.STONE.defaultBlockState(),3);}
        if(age==65&&(index==1||index>=5))check(actor.getTarget()==player,"Retains pursuit outside detection range after retreat: "+kinds[index]);
        if(index==2&&player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))webObserved=true;player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);player.fallDistance=0;
        if(age==20)check(actor.getTarget()==player,"Acquires a player in its configured radius: "+kinds[index]);
        if(player.getHealth()<1000&&(actor instanceof MapActorEntity custom?custom.inMeleeRange(player):actor.getBoundingBox().inflate(1).intersects(player.getBoundingBox()))&&actor.getX()>startX+4){
            check((actor instanceof MapActorEntity custom?custom.inMeleeRange(player):actor.getBoundingBox().inflate(1).intersects(player.getBoundingBox())),"Actually walks into melee range: "+kinds[index]);
            float before=player.getHealth();player.invulnerableTime=0;actor.tickCount+=31;check(actor.doHurtTarget(player)&&player.getHealth()<before,"Deals real melee damage: "+kinds[index]);
            if(index==2)check(webObserved,"Crypt Fiend Lua web projectile reaches and slows its target outside a match");if(index==1)for(int y=80;y<=83;y++)for(int z=-1;z<=1;z++)level.setBlock(new BlockPos(8,y,z),Blocks.AIR.defaultBlockState(),3);actor.discard();actor=null;if(++index<kinds.length)next();else{done=true;System.out.println("PORTABLE RUNTIME PASS: "+checks+" actor combat/navigation checks");server.halt(false);}
        }else if(age>500)throw new AssertionError(kinds[index]+" failed pursuit/melee: pos="+actor.position()+" target="+actor.getTarget()+" health="+player.getHealth()+" path="+actor.getNavigation().getPath()+" casting="+(actor instanceof MapActorEntity custom&&custom.casting())+" range="+(actor instanceof MapActorEntity custom?custom.inMeleeRange(player):actor.getBoundingBox().inflate(1).intersects(player.getBoundingBox()))+" direct="+actor.doHurtTarget(player));
    }catch(Throwable t){fail(t);}}
    private void fail(Throwable t){done=true;t.printStackTrace();System.out.println("PORTABLE RUNTIME FAIL: "+t);if(server!=null)server.halt(false);}
}
