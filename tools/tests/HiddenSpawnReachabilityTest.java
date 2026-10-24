package local.aggrotests;

import me.cryo.zombierool.gameplay.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("aggrotests")
public final class HiddenSpawnReachabilityTest {
    boolean requested,queued,done;
    public HiddenSpawnReachabilityTest(){MinecraftForge.EVENT_BUS.addListener(this::client);}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    void advance(ServerLevel level){level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+1);NavigationArea.invalidate(level);}
    void run(ServerPlayer player)throws Exception{
        var level=player.serverLevel();
        for(var e:com.google.common.collect.Lists.newArrayList(level.getAllEntities()))if(!(e instanceof net.minecraft.world.entity.player.Player))e.discard();
        var path=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("zombierool:path"));
        var config=me.cryo.zombierool.config.WorldConfig.get(level);
        for(int x=128;x<160;x++)for(int z=128;z<152;z++){
            level.setBlock(new BlockPos(x,53,z),path.defaultBlockState(),2);
            for(int y=54;y<74;y++)level.setBlock(new BlockPos(x,y,z),(y==54||y==64?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        }
        // Keep a single spawn column, while the surrounding markers still authorize walking routes.
        for(var pos:new java.util.ArrayList<>(config.getPathPositions()))if(!pos.equals(new BlockPos(146,53,140)))config.removePathPosition(pos,level);
        config.addPathPosition(new BlockPos(146,53,140),level);
        player.setGameMode(GameType.ADVENTURE);player.teleportTo(level,140.5,65,140.5,0,0);
        CompoundTag profile=new CompoundTag();profile.putString("name","Test Risen");profile.putString("behavior","melee");profile.putDouble("width",.6);profile.putDouble("height",1.8);profile.putDouble("scale",1);profile.putDouble("health",40);profile.putString("faction","risen");
        MapActors.get(level).put(level,"test_risen",profile);
        var running=WaveManager.class.getDeclaredField("gameRunning");running.setAccessible(true);running.setBoolean(null,true);
        try{
            advance(level);
            String isolated=HiddenActorSpawn.spawn(player,"test_risen","",4,8);
            check(isolated.isEmpty(),"Spawn accepted on sealed floor beneath the map");
            check(java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).noneMatch(MapActors::owns),"Rejected spawn left a live actor");
            // A hidden point on the player's floor, reachable by walking around the screen.
            for(int z=138;z<=142;z++)for(int y=65;y<=68;y++)level.setBlock(new BlockPos(143,y,z),Blocks.STONE.defaultBlockState(),2);
            advance(level);String upper=HiddenActorSpawn.spawn(player,"test_risen","",4,8);
            check(!upper.isEmpty(),"Reachable hidden surface spawn was rejected");
            var actor=level.getEntity(java.util.UUID.fromString(upper));check(actor.getY()==65,"Spawn chose the disconnected lower floor");actor.discard();
            for(int z=138;z<=142;z++)for(int y=65;y<=68;y++)level.setBlock(new BlockPos(143,y,z),Blocks.AIR.defaultBlockState(),2);
            // Join the lower floor to the upper one with supported one-block steps.
            for(int x=133;x<=135;x++)for(int z=130;z<=139;z++){
                int top=55+z-130;
                for(int y=55;y<74;y++)level.setBlock(new BlockPos(x,y,z),(y<=top?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
            }
            advance(level);String lower=HiddenActorSpawn.spawn(player,"test_risen","",4,8);
            check(!lower.isEmpty(),"Connected lower floor with stairs was rejected");
            var downstairs=level.getEntity(java.util.UUID.fromString(lower));check(downstairs.getY()==55,"Stair test did not use lower floor");downstairs.discard();
            System.out.println("AGGRO PASS: sealed under-map spawn rejected, no leftover actor, hidden surface accepted, connected stairs accepted");
        }finally{running.setBoolean(null,false);}
    }
    void client(TickEvent.ClientTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"FeedbackFixture");return;}
        if(!queued&&mc.level!=null&&mc.getSingleplayerServer()!=null){queued=true;mc.getSingleplayerServer().execute(()->{try{run(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable error){System.out.println("AGGRO FAIL "+error);error.printStackTrace();}finally{done=true;mc.execute(mc::stop);}});}
    }
}
