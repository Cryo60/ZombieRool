package local.aggrotests;

import me.cryo.zombierool.entity.SpecialEnemyEntity;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.init.ZombieroolModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("aggrotests")
public final class MapBossAggroTest {
    boolean requested, queued, done;
    public MapBossAggroTest(){MinecraftForge.EVENT_BUS.addListener(this::client);}
    void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    void run(ServerPlayer player)throws Exception{
        ServerLevel level=player.serverLevel();
        for(var e:com.google.common.collect.Lists.newArrayList(level.getAllEntities()))if(!(e instanceof net.minecraft.world.entity.player.Player))e.discard();
        for(int x=128;x<160;x++)for(int z=128;z<144;z++){
            level.setBlock(new BlockPos(x,63,z),net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("zombierool:path")).defaultBlockState(),2);
            level.setBlock(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=65;y<72;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        player.setGameMode(GameType.ADVENTURE);player.teleportTo(level,146.5,65,136.5,0,0);
        var mob=ZombieroolModEntities.CUSTOM_MINI_BOSS.get().create(level);
        mob.setBossProfile(me.cryo.zombierool.config.MiniBossDefinitions.get("zombierool:abomination"));
        CompoundTag profile=new CompoundTag();profile.putDouble("aggroRadius",4);profile.putDouble("leashRadius",32);profile.putDouble("wanderRadius",0);profile.putString("behavior","guard");profile.putString("faction","scourge");profile.putString("baseEntity","abomination");profile.putDouble("meleeReach",1);profile.putInt("meleeCooldown",20);
        mob.getPersistentData().put("MapNativeProfile",profile);mob.getPersistentData().putDouble("MapHomeX",134.5);mob.getPersistentData().putDouble("MapHomeY",65);mob.getPersistentData().putDouble("MapHomeZ",136.5);
        mob.setPos(134.5,65,136.5);mob.setEmergenceTicks(0);mob.setOnGround(true);level.addFreshEntity(mob);
        mob.setNoAi(false);mob.setEmergenceTicks(0);mob.playBossAnimation("idle",0);
        check(mob.canPerformBossAction(),"Fixture boss must be able to act: boss="+mob.isBoss()+" noAi="+mob.isNoAi()+" anim="+mob.bossAnimation());
        var goal=new BossJumpLinkGoal(mob);
        for(int i=0;i<80;i++){
            mob.tickCount+=2;level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+1);
            mob.setPos(134.5,65,136.5);mob.setOnGround(true);
            NativeMapActors.tick(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(mob));
            goal.canUse();
            check(mob.getTarget()==null,"Traversal acquired a player 12 blocks away despite aggro radius 4");
        }
        player.teleportTo(level,140.5,65,136.5,0,0);mob.tickCount+=20;goal.canUse();
        check(mob.getTarget()==null,"Traversal acquired a player 6 blocks away despite aggro radius 4");
        player.teleportTo(level,137.5,65,136.5,0,0);
        mob.setTarget(MapActorBehavior.target(mob,profile,mob.position()));
        check(mob.getTarget()==player,"Failed to acquire player inside aggro radius");
        player.teleportTo(level,146.5,65,136.5,0,0);
        check(MapActorBehavior.target(mob,profile,mob.position())==player,"Legitimate pursuit lost outside initial detection radius");
        mob.setTarget(null);profile.putString("behavior","wander");mob.tickCount+=20;goal.canUse();
        check(mob.getTarget()==null,"Wander-only boss acquired player through traversal");
        mob.getPersistentData().putUUID("MapRetaliation",player.getUUID());
        check(MapActorBehavior.target(mob,profile,mob.position())==player,"Retaliation stopped working");
        mob.discard();
        System.out.println("AGGRO PASS: radius 4, outside at 6/12, inside acquisition, pursuit, wander and retaliation");
    }
    void client(TickEvent.ClientTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"FeedbackFixture");return;}
        if(!queued&&mc.level!=null&&mc.getSingleplayerServer()!=null){queued=true;mc.getSingleplayerServer().execute(()->{try{run(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable error){System.out.println("AGGRO FAIL "+error);error.printStackTrace();}finally{done=true;mc.execute(mc::stop);}});}
    }
}
