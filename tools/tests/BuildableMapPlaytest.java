package local.buildablemaptest;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod("buildablemaptest")
public final class BuildableMapPlaytest {
    private static int checks;
    public BuildableMapPlaytest(){MinecraftForge.EVENT_BUS.addListener(this::started);}
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("MAP PLAYTEST CHECK: "+text);}
    private static BlockPos part(BuildableSystem data,ServerPlayer player,String id){
        for(var tag:data.state(player).getList("targets",Tag.TAG_COMPOUND)){var n=(CompoundTag)tag;if(n.getString("part").equals(id))return BlockPos.of(n.getLong("pos"));}
        throw new AssertionError("Missing live part "+id);
    }
    private static void craft(ServerLevel level,ServerPlayer player,BlockPos bench,String... ids)throws Exception{
        var data=BuildableSystem.get(level);var tick=BuildableSystem.class.getDeclaredMethod("tick",ServerLevel.class);tick.setAccessible(true);
        for(String id:ids){
            var pos=part(data,player,id);player.setPos(pos.getX()+.5,65,pos.getZ()+1.5);BuildableSystem.interact(player,pos);
            check(!data.carried(player.getUUID()).isEmpty(),"Physical component can be collected: "+id);
            player.setPos(bench.getX()+.5,65,bench.getZ()+2);BuildableSystem.interact(player,bench);
            check(BuildableSystem.busy(player),"Component starts assembly at its actual map workbench: "+id);
            level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+45);tick.invoke(data,level);
        }
        check(data.completed(bench),"Workbench completed: "+bench.toShortString());BuildableSystem.interact(player,bench);
    }
    @SuppressWarnings("unchecked") private void started(ServerStartedEvent event){
        var server=event.getServer();try{
            var level=server.overworld();var player=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AteliersPlaytest"));
            level.addNewPlayer(player);var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");field.setAccessible(true);var online=(List<ServerPlayer>)field.get(server.getPlayerList());online.add(player);
            var mapField=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");mapField.setAccessible(true);var players=(Map<UUID,ServerPlayer>)mapField.get(server.getPlayerList());players.put(player.getUUID(),player);
            player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);player.setPos(.5,65,19.5);WaveManager.startGame(level);
            check(WaveManager.isGameRunning(),"Actual Zombies match starts on the saved map");
            check(me.cryo.zombierool.scripting.LuaScriptManager.ready(level),"Map objective Lua loads at match start");
            check(level.getBlockState(new BlockPos(17,66,-9)).is(Blocks.WAXED_CUT_COPPER),"Service-room gate is initially closed");
            craft(level,player,new BlockPos(0,65,-18),"canon","culasse","crosse");
            check(player.getInventory().items.stream().anyMatch(s->WeaponFacade.getWeaponId(s).toLowerCase(java.util.Locale.ROOT).contains("mp5")),"Workshop produces an equipped MP5 through native weapon attribution");
            craft(level,player,new BlockPos(-19,65,0),"porte","cadre","poignee");
            check(player.getInventory().countItem(Items.SHIELD)==1,"Free workbench produces the configured shield");
            craft(level,player,new BlockPos(19,65,0),"batterie","bobine","fusible");
            check(level.getBlockState(new BlockPos(19,66,0)).is(Blocks.REDSTONE_BLOCK),"Fixed relay result is placed above the copper workbench");
            check(level.getBlockState(new BlockPos(18,66,0)).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),"Crafted relay powers its adjacent lamp");
            check(level.getBlockState(new BlockPos(17,66,-9)).isAir(),"Live Lua reward opens the service room");
            check(level.getBlockEntity(new BlockPos(19,65,-18)) instanceof me.cryo.zombierool.block.system.PackAPunchSystem.PackAPunchBlockEntity,"Pack-a-Punch exists in the unlocked room");
            WaveManager.endGame(level,null);
            check(!WaveManager.isGameRunning()&&level.getBlockState(new BlockPos(17,66,-9)).is(Blocks.WAXED_CUT_COPPER),"Ending the match closes the objective gate again");
            check(level.getBlockState(new BlockPos(19,66,0)).isAir(),"Ending the match removes the constructed relay");
            check(level.getBlockState(new BlockPos(23,66,3)).is(me.cryo.zombierool.block.system.BuildableBlocks.PART.get()),"Physical piece markers return for the next match");
            online.remove(player);players.remove(player.getUUID());player.discard();
            System.out.println("ATELIERS PLAYTEST PASS: "+checks+" checks");
        }catch(Throwable t){t.printStackTrace();}finally{server.halt(false);}
    }
}
