package local.feedbacktests;

import me.cryo.zombierool.api.BuildablePresets;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.init.*;
import me.cryo.zombierool.scripting.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("hotfixtests")
public final class OctoberFeedbackGameplayTest {
    private static final class DamageProbe extends net.minecraftforge.common.util.FakePlayer {
        DamageProbe(net.minecraft.server.level.ServerLevel level){super(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"FeedbackProbe"));}
        @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}
    }
    public OctoberFeedbackGameplayTest(){MinecraftForge.EVENT_BUS.addListener(this::run);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("FEEDBACK CHECK: "+message);}
    private void run(ServerStartedEvent event){var server=event.getServer();var l=server.overworld();try{
        long time=l.getGameTime();l.setDayTime(18000);
        for(int x=0;x<30;x++)for(int z=0;z<8;z++){
            l.setBlock(new BlockPos(x,63,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),2);
            l.setBlock(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=65;y<79;y++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        var zombie=ZombieroolModEntities.ZOMBIE.get().create(l);zombie.setPos(4.5,65,3.5);l.addFreshEntity(zombie);zombie.setEmergenceTicks(0);zombie.setNoAi(false);zombie.makeCrawler();
        check(zombie.isCrawler() && zombie.getBbWidth()<1,"Legless zombie retains a navigable single-cell body");
        var wall=new BlockPos(5,66,3);var wallState=DefenseWallSystem.MAIN_BLOCK.get().defaultBlockState();l.setBlock(wall,wallState,3);
        var wallEntity=(DefenseWallSystem.DefenseWallBlockEntity)l.getBlockEntity(wall);
        DefenseWallSystem.DefenseWallBlockEntity.tick(l,wall,wallState,wallEntity);
        l.getServer().getWorldData().overworldData().setGameTime(time+61);
        DefenseWallSystem.DefenseWallBlockEntity.tick(l,wall,wallState,wallEntity);
        check(l.getBlockState(wall).getValue(DefenseWallSystem.DefenseWallBlock.STAGE)<wallState.getValue(DefenseWallSystem.DefenseWallBlock.STAGE),"Legless zombie dismantles defense wall");
        l.removeBlock(wall,false);
        for(int x=10;x<18;x++)for(int z=2;z<=4;z++){
            for(int y=65;y<65+x-10;y++)l.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),2);
            l.setBlock(new BlockPos(x,65+x-10,z),Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST),2);
        }
        for(int x=18;x<=22;x++)for(int z=2;z<=4;z++)l.setBlock(new BlockPos(x,72,z),Blocks.STONE.defaultBlockState(),2);
        zombie.setPos(8.5,65,3.5);zombie.setOnGround(true);
        var target=new BlockPos(20,73,3);var path=zombie.getNavigation().createPath(target,0);
        check(path!=null&&path.canReach(),"Crawler plans a route up eight consecutive stairs");
        zombie.getNavigation().moveTo(path,1);
        for(int i=0;i<650;i++){
            l.getServer().getWorldData().overworldData().setGameTime(time+62+i);
            if(i%40==0)zombie.getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,1);
            zombie.tick();
            if(zombie.getY()>72.8)break;
        }
        System.out.println("FEEDBACK crawler final position="+zombie.position());
        check(zombie.getY()>72.8,"Crawler physically climbs to the upper floor");zombie.discard();
        var player=new DamageProbe(l);l.addNewPlayer(player);
        java.lang.reflect.Field spawnShield;
        try{spawnShield=net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");}
        catch(NoSuchFieldException production){spawnShield=net.minecraft.server.level.ServerPlayer.class.getDeclaredField("f_8921_");}
        spawnShield.setAccessible(true);spawnShield.setInt(player,0);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(24,65,3);player.setHealth(player.getMaxHealth());player.invulnerableTime=0;
        float health=player.getHealth();
        ExplosionControl.doCustomExplosion(l,player,player.getBoundingBox().getCenter(),24,2,.6f,.2f,6,0,"RAYGUN_IMPACT","",false);
        check(Math.abs(player.getHealth()-(health-4.8f))<.01,"Ray Gun point-blank splash uses configured damage");
        float afterSplash=player.getHealth();FixedPlayerDamage.raygunSplash(player,4.8f);
        check(Math.abs(player.getHealth()-afterSplash)<.01,"Simultaneous projectiles respect self-damage immunity frames");
        player.setHealth(health);player.invulnerableTime=0;
        ExplosionControl.doCustomExplosion(l,player,player.getBoundingBox().getCenter().add(1,0,0),24,2,.6f,.2f,6,0,"RAYGUN_IMPACT","",false);
        check(Math.abs(player.getHealth()-(health-2.4f))<.01,"Ray Gun self damage falls off with distance");
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get(),100,0));player.invulnerableTime=0;float protectedHealth=player.getHealth();FixedPlayerDamage.raygunSplash(player,6);
        check(player.getHealth()==protectedHealth,"PHD Flopper blocks Ray Gun self damage");
        player.discard();
        var preset=BuildablePresets.get("pack_a_punch");
        check(preset.getString("mode").equals("block")&&preset.getList("presetPieces",10).size()==3&&preset.getInt("limit")==1,"Pack-a-Punch preset assembles three registered parts into one machine");
        LuaScriptManager.loadScripts(l);
        check(LuaScriptManager.executeString("return type(ZombieroolAPI)=='table' and ZombieroolAPI.getClass==nil and ZombieroolAPI.wait==nil and os==nil and io==nil and luajava==nil").endsWith("true"),"Actual map API has no Java object escape");
        check(LuaScriptManager.executeString("ZombieroolAPI:setData('safe','works'); return ZombieroolAPI:getData('safe')").endsWith("works"),"Existing colon gameplay API and data store work");
        check(LuaScriptManager.executeString("ZombieroolAPI.setData('dot','works'); return ZombieroolAPI.getData('dot')").endsWith("works"),"Dot gameplay API calls work");
        check(LuaScriptManager.executeString("while true do pcall(function() while true do end end) end").contains("budget exceeded"),"Runtime host recovers from protected infinite loops");
        check(LuaScriptManager.executeString("return #ZombieroolAPI:getMapAtmospherePresets()>0").endsWith("true"),"Map atmosphere API remains usable after budget abort");
        System.out.println("FEEDBACK RUNTIME PASS");
    }catch(Throwable error){error.printStackTrace();}finally{server.halt(false);}}
}
