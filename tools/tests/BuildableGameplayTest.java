package local.buildabletests;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.scripting.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Runs inside an isolated real Forge server, including registered block entities and packets. */
@Mod("buildabletests")
public final class BuildableGameplayTest {
    private static int checks;
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("BUILDABLE CHECK: "+text);}
    public BuildableGameplayTest(){MinecraftForge.EVENT_BUS.addListener(this::started);}
    private static BlockPos pos(int x){return new BlockPos(x,80,0);}
    private static void stand(ServerPlayer p,BlockPos target){p.setPos(target.getX()+.5,80,2);}
    private static void act(ServerPlayer p,BlockPos pos){
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,net.minecraft.world.phys.Vec3.atCenterOf(pos));
        BuildableSystem.holdAction(p,pos,true);
        if(!BuildableSystem.busy(p))BuildableSystem.interact(p,pos);
    }
    private static void rawTick(ServerLevel l,BuildableSystem data)throws Exception{
        var m=BuildableSystem.class.getDeclaredMethod("tick",ServerLevel.class);m.setAccessible(true);m.invoke(data,l);
    }
    private static void tick(ServerLevel l,BuildableSystem data)throws Exception{
        // Simulate the live client's held-key heartbeat independently of the action timer.
        var holds=BuildableSystem.class.getDeclaredField("holds");holds.setAccessible(true);
        for(var entry:new HashMap<>((Map<UUID,?>)holds.get(data)).entrySet()){
            var player=l.getServer().getPlayerList().getPlayer(entry.getKey());if(player==null)continue;
            var pos=entry.getValue().getClass().getDeclaredMethod("pos");pos.setAccessible(true);
            BuildableSystem.holdAction(player,(BlockPos)pos.invoke(entry.getValue()),true);
        }
        rawTick(l,data);
    }
    private static void finish(ServerLevel l,BuildableSystem data)throws Exception{l.getServer().getWorldData().overworldData().setGameTime(l.getGameTime()+45);tick(l,data);}
    private static void rejects(Runnable r,String text){boolean rejected=false;try{r.run();}catch(IllegalArgumentException e){rejected=true;}check(rejected,text);}
    @SuppressWarnings("unchecked") private void started(ServerStartedEvent e){
        var server=e.getServer();try{
            var l=server.overworld();var data=BuildableSystem.get(l);data.reset(l);
            var definitions=BuildableSystem.class.getDeclaredField("definitions");definitions.setAccessible(true);definitions.set(data,new CompoundTag());
            var a=new FakePlayer(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BuilderA"));
            var b=new FakePlayer(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BuilderB"));
            var onlineField=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");onlineField.setAccessible(true);var online=(List<ServerPlayer>)onlineField.get(server.getPlayerList());
            var mapField=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");mapField.setAccessible(true);var players=(Map<UUID,ServerPlayer>)mapField.get(server.getPlayerList());
            for(var p:List.of(a,b)){l.addNewPlayer(p);online.add(p);players.put(p.getUUID(),p);p.setGameMode(GameType.SURVIVAL);}
            for(int x=0;x<24;x++)for(int z=-2;z<5;z++)for(int y=79;y<85;y++)l.setBlock(new BlockPos(x,y,z),y==79?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            var reward=new ItemStack(Items.DIAMOND,3);reward.setHoverName(net.minecraft.network.chat.Component.literal("Buildable reward"));reward.getOrCreateTag().putString("custom","preserved");
            data.define("demo","Demo","body,core",reward,"item",40,2);
            rejects(()->data.define("invalid","Bad","same,same",reward,"item",40,0),"Duplicate component IDs rejected");
            rejects(()->data.define("invalid","Bad","one",ItemStack.EMPTY,"item",40,0),"Empty item reward rejected");
            rejects(()->data.define("invalid","Bad","one",reward,"block",40,0),"Non-block fixed reward rejected");
            l.setBlock(pos(2),Blocks.IRON_BLOCK.defaultBlockState(),3);l.setBlock(pos(4),Blocks.IRON_BLOCK.defaultBlockState(),3);
            l.setBlock(pos(6),Blocks.CHEST.defaultBlockState(),3);((ChestBlockEntity)l.getBlockEntity(pos(6))).setItem(0,new ItemStack(Items.EMERALD,7));
            l.setBlock(pos(10),Blocks.CRAFTING_TABLE.defaultBlockState(),3);l.setBlock(pos(14),Blocks.CRAFTING_TABLE.defaultBlockState(),3);
            data.anchor(l,pos(2),"demo","body");data.anchor(l,pos(4),"demo","body");data.anchor(l,pos(6),"demo","core");
            data.anchor(l,pos(10),"","");data.anchor(l,pos(14),"demo","");
            var running=WaveManager.class.getDeclaredField("gameRunning");running.setAccessible(true);running.setBoolean(null,true);
            data.start(l);
            check(data.state(a).getList("targets",10).size()==4,"One random location per unique part and two workbenches");
            check(l.getBlockState(pos(2)).isAir()!=l.getBlockState(pos(4)).isAir(),"Exactly one alternate physical spawn is hidden");
            rejects(()->data.define("late","Late","p",reward,"item",40,0),"Active recipe editing rejected");
            BlockPos body=l.getBlockState(pos(2)).isAir()?pos(4):pos(2);
            a.setPos(23,80,2);act(a,body);check(data.carried(a.getUUID()).isEmpty(),"Remote pickup packet rejected");
            stand(a,body);var wall=new BlockPos(body.getX(),81,1);l.setBlock(wall,Blocks.STONE.defaultBlockState(),3);act(a,body);
            check(data.carried(a.getUUID()).isEmpty(),"Pickup through a solid wall rejected");l.setBlock(wall,Blocks.AIR.defaultBlockState(),3);
            a.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,net.minecraft.world.phys.Vec3.atCenterOf(body.east()));BuildableSystem.interact(a,body);check(data.carried(a.getUUID()).isEmpty(),"Looking at a nearby decoy cannot collect the real component beside it");
            a.setPos(body.getX()+.5,80,3.5);a.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,net.minecraft.world.phys.Vec3.atCenterOf(body));BuildableSystem.interact(a,body);check(data.carried(a.getUUID()).isEmpty(),"Correct aim outside close pickup range cannot collect a component");stand(a,body);
            act(a,body);check(data.carried(a.getUUID()).equals("demo:body")&&l.getBlockState(body).isAir(),"Pickup hides the physical part and sets server-owned carried state");
            stand(a,pos(6));act(a,pos(6));check(data.carried(a.getUUID()).equals("demo:core")&&l.getBlockState(body).isAir(),"F swaps carried parts while keeping the original support empty");
            stand(a,pos(6));act(a,pos(6));check(data.carried(a.getUUID()).equals("demo:body")&&data.state(a).getList("targets",10).stream().anyMatch(t->((CompoundTag)t).getLong("pos")==pos(6).asLong()&&((CompoundTag)t).getString("part").equals("core")),"Swapping back retains exactly one carried part without losing either component");
            stand(a,pos(10));BuildableSystem.interact(a,pos(10));check(!BuildableSystem.busy(a),"A single interact request cannot start assembly without held-key input");
            act(a,pos(10));BuildableSystem.holdAction(a,pos(10),false);finish(l,data);check(!BuildableSystem.busy(a)&&data.installed(pos(10))==0&&!data.carried(a.getUUID()).isEmpty(),"Releasing F cancels assembly without consuming the component");
            act(a,pos(10));l.getServer().getWorldData().overworldData().setGameTime(l.getGameTime()+11);rawTick(l,data);check(!BuildableSystem.busy(a)&&data.installed(pos(10))==0,"Missing held-key heartbeats cancel assembly before completion");BuildableSystem.holdAction(a,pos(10),false);
            act(a,pos(10));check(BuildableSystem.busy(a),"Assembly starts with the existing Pack-a-Punch animation");
            WaveManager.setGamePaused(true);for(int i=0;i<45;i++){l.getServer().getWorldData().overworldData().setGameTime(l.getGameTime()+1);tick(l,data);}
            check(BuildableSystem.busy(a)&&data.installed(pos(10))==0,"Paused match cannot advance or finish assembly");WaveManager.setGamePaused(false);
            a.setPos(23,80,2);finish(l,data);check(!BuildableSystem.busy(a)&&data.installed(pos(10))==0&&!data.carried(a.getUUID()).isEmpty(),"Leaving workbench cancels without consuming the part");
            stand(b,pos(6));int before=0;for(var entity:l.getAllEntities())if(entity instanceof net.minecraft.world.entity.item.ItemEntity)before++;
            act(b,pos(6));int after=0;for(var entity:l.getAllEntities())if(entity instanceof net.minecraft.world.entity.item.ItemEntity)after++;
            check(after==before&&data.carried(b.getUUID()).equals("demo:core"),"Picking a container component does not spill or duplicate its inventory");
            stand(b,pos(10));act(b,pos(10));stand(a,pos(10));act(a,pos(10));
            check(BuildableSystem.busy(b)&&!BuildableSystem.busy(a),"Workbench serializes concurrent assembly attempts");
            finish(l,data);check(data.installed(pos(10))==1&&data.carried(b.getUUID()).isEmpty(),"Another player installs any component first");
            stand(a,pos(14));act(a,pos(14));check(!BuildableSystem.busy(a),"First installed part locks the recipe to its chosen workbench");
            stand(a,pos(10));act(a,pos(10));finish(l,data);
            check(data.completed(pos(10))&&data.installed(pos(10))==2,"Team completes one shared build without enforcing component order");
            check(l.getAllEntities().spliterator().getExactSizeIfKnown()!=0,"World entities remain accessible after assembly");
            int visuals=0;boolean small=true;for(var entity:l.getAllEntities())if(entity.getTags().contains("zr_buildable_display")){
                visuals++;float scale=entity.saveWithoutId(new CompoundTag()).getCompound("transformation").getList("scale",Tag.TAG_FLOAT).getFloat(0);small &= scale>.64f&&scale<.66f;
            }
            check(visuals==1,"Completion replaces the installed parts with the physical reward on the workbench");
            check(java.util.stream.StreamSupport.stream(l.getAllEntities().spliterator(),false).filter(display->display.getTags().contains("zr_buildable_result")).allMatch(display->display.saveWithoutId(new CompoundTag()).getString("billboard").equals("vertical")),"Workbench rewards rotate towards each viewing player");
            check(small,"Final reward uses a stable ground display at the configured workbench scale");
            check(java.util.stream.StreamSupport.stream(l.getAllEntities().spliterator(),false).filter(display->display.getTags().contains("zr_buildable_result")).allMatch(display->display.getY()>pos(10).getY()+1.3),"Completed models have clearance above the tabletop");
            a.getInventory().clearContent();for(int i=0;i<36;i++)a.getInventory().setItem(i,new ItemStack(Items.APPLE,64));act(a,pos(10));
            check(a.getInventory().countItem(Items.DIAMOND)==0&&a.getInventory().countItem(Items.APPLE)==36*64,"A full inventory neither consumes a copy nor partially inserts a reward");
            a.getInventory().clearContent();act(a,pos(10));
            var granted=a.getInventory().items.stream().filter(s->s.is(Items.DIAMOND)).findFirst().orElseThrow();
            check(granted.getCount()==3&&granted.getTag().getString("custom").equals("preserved"),"Reward retains count, custom name and complete NBT");
            act(a,pos(10));check(granted.getCount()==3,"Repeated take cannot duplicate an owned reward");
            b.getInventory().clearContent();stand(b,pos(10));act(b,pos(10));check(b.getInventory().countItem(Items.DIAMOND)==3,"Second player can claim an available copy");
            a.getInventory().clearContent();act(a,pos(10));check(a.getInventory().countItem(Items.DIAMOND)==0,"Recipe-wide copy limit enforced");
            check(java.util.stream.StreamSupport.stream(l.getAllEntities().spliterator(),false).anyMatch(display->display.getTags().contains("zr_buildable_result")),"Exhausting the final copy leaves the completed model on the table");
            data.reset(l);check(!data.completed(pos(10))&&data.carried(a.getUUID()).isEmpty(),"Reset clears completion, carried parts and free-bench locks");
            check(l.getBlockState(pos(2)).is(Blocks.IRON_BLOCK)&&l.getBlockState(pos(4)).is(Blocks.IRON_BLOCK),"Reset restores every alternate component spawn");
            check(((ChestBlockEntity)l.getBlockEntity(pos(6))).getItem(0).getCount()==7,"Reset restores original block entity inventory");
            check(data.definitions().getCompound("recipes").contains("demo"),"Author definitions survive the reset");
            data.start(l);body=l.getBlockState(pos(2)).isAir()?pos(4):pos(2);stand(a,body);act(a,body);data.drop(a);
            check(data.carried(a.getUUID()).isEmpty()&&!l.getBlockState(body).isAir(),"Drop returns the part to its selected source");
            stand(a,body);act(a,body);stand(a,pos(6));act(a,pos(6));data.drop(a);
            check(data.carried(a.getUUID()).isEmpty()&&!l.getBlockState(body).isAir()&&l.getBlockState(pos(6)).is(Blocks.CHEST),"Dropping after an on-location swap returns both components without overwriting either");
            stand(a,body);act(a,body);a.setGameMode(GameType.SPECTATOR);tick(l,data);
            check(data.carried(a.getUUID()).isEmpty()&&!l.getBlockState(body).isAir(),"Spectating returns a carried part without losing it");a.setGameMode(GameType.SURVIVAL);
            stand(a,body);act(a,body);BuildableSystem.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(a,l.damageSources().generic()));
            check(data.carried(a.getUUID()).isEmpty()&&!l.getBlockState(body).isAir(),"Death lifecycle returns a carried part immediately");
            stand(a,body);act(a,body);var saved=data.save(new CompoundTag());
            var load=BuildableSystem.class.getDeclaredMethod("load",CompoundTag.class);load.setAccessible(true);var recovered=(BuildableSystem)load.invoke(null,saved);
            recovered.reset(l);check(!l.getBlockState(body).isAir(),"Saved recovery state restores a hidden part after interruption");data.reset(l);
            running.setBoolean(null,false);
            var visual=new ItemStack(Items.STICK);visual.getOrCreateTag().putInt("CustomModelData",123);visual.setHoverName(net.minecraft.network.chat.Component.literal("Custom component"));
            data.define("visual","Visual recipe","piece",reward,"item",40,0);data.itemPart(l,pos(2),"visual","piece",visual);data.anchor(l,pos(10),"visual","");
            running.setBoolean(null,true);data.start(l);check(l.getBlockState(pos(2)).isAir(),"Arbitrary item component replaces its placement marker during the match");
            stand(a,pos(2));me.cryo.zombierool.handlers.ServerInteractionHandler.handleInteraction(a,pos(2),me.cryo.zombierool.network.InteractionType.BUILDABLE);
            check(data.carried(a.getUUID()).equals("visual:piece")&&data.state(a).getCompound("icon").getCompound("tag").getInt("CustomModelData")==123,"Interaction packet picks a custom item component and preserves its model NBT");
            data.drop(a);check(data.carried(a.getUUID()).isEmpty()&&l.getBlockState(pos(2)).isAir(),"Dropping a custom item component respawns its model at the marker");
            act(a,pos(2));stand(a,pos(10));act(a,pos(10));finish(l,data);
            boolean rewardModel=false;for(var entity:l.getAllEntities())if(entity.getTags().contains("zr_buildable_result")&&entity.saveWithoutId(new CompoundTag()).getCompound("item").getString("id").equals("minecraft:diamond"))rewardModel=true;
            check(data.completed(pos(10))&&rewardModel,"An arbitrary item recipe displays its actual completed reward");
            a.getInventory().clearContent();act(a,pos(10));a.getInventory().clearContent();act(a,pos(10));
            check(a.getInventory().countItem(Items.DIAMOND)==3,"Reusable results can be reclaimed after loss without rebuilding");
            data.reset(l);check(l.getBlockState(pos(2)).is(Blocks.IRON_BLOCK),"Custom item markers return to their authoring blocks after reset");running.setBoolean(null,false);
            data.define("fixed","Fixed machine","key",new ItemStack(Blocks.REDSTONE_BLOCK),"block",40,0);
            data.define("lua","Quest","trigger",ItemStack.EMPTY,"event",40,1);
            check(data.definitions().getCompound("recipes").getCompound("fixed").getString("mode").equals("block"),"Fixed machine and Lua quest result modes accepted");
            LuaScriptManager.loadScripts(l);
            var api=new ZombieroolAPI(l);api.defineBuildable("lua_api","Lua API","a,b","{id:\"minecraft:apple\",Count:1b}","item",40,0);
            api.bindBuildableBench("lua_api",10,80,0);api.bindBuildablePart("lua_api","a",2,80,0);api.bindBuildablePart("lua_api","b",4,80,0);
            check(data.definitions().getCompound("recipes").contains("lua_api")&&api.getBuildableInstalledCount(10,80,0)==0,"Lua exposes recipe definition, physical bindings and progress queries");
            check(LuaScriptManager.executeString("return ZombieroolAPI:getBuildableInstalledCount(10,80,0)").contains("0"),"Buildable methods are callable through the actual Lua binding");
            LuaScriptManager.executeString("claims=0; function OnBuildableCompleted(uuid,recipe,x,y,z) ZombieroolAPI:setData('completed',recipe) end; function OnBuildableClaimed(uuid,recipe,x,y,z,count) claims=claims+1; ZombieroolAPI:setData('claims',tostring(claims)) end");
            l.setBlock(pos(20),Blocks.LEVER.defaultBlockState(),3);l.setBlock(pos(22),Blocks.LEVER.defaultBlockState(),3);
            data.anchor(l,pos(20),"fixed","key");data.anchor(l,pos(22),"lua","trigger");
            data.anchor(l,pos(14),"fixed","");data.anchor(l,pos(10),"lua","");running.setBoolean(null,true);data.start(l);
            stand(a,pos(20));act(a,pos(20));stand(a,pos(14));act(a,pos(14));finish(l,data);
            check(data.completed(pos(14))&&LuaScriptManager.getData("completed").equals("fixed"),"Completion dispatches a real Lua callback with the recipe ID");
            l.setBlock(pos(14).above(),Blocks.STONE.defaultBlockState(),3);act(a,pos(14));check(l.getBlockState(pos(14).above()).is(Blocks.STONE),"Fixed result never overwrites an obstructing block");
            l.setBlock(pos(14).above(),Blocks.AIR.defaultBlockState(),3);act(a,pos(14));
            check(l.getBlockState(pos(14).above()).is(Blocks.REDSTONE_BLOCK),"Completed fixed buildable creates a working redstone block");
            stand(b,pos(22));act(b,pos(22));stand(b,pos(10));act(b,pos(10));finish(l,data);act(b,pos(10));act(b,pos(10));
            check(data.completed(pos(10))&&LuaScriptManager.getData("claims").equals("2"),"Lua-only quest reward respects one-copy limit and dispatches exactly once");
            data.reset(l);check(l.getBlockState(pos(14).above()).isAir(),"Match reset restores the original space occupied by a fixed result");running.setBoolean(null,false);
            l.setBlock(pos(14).above(),Blocks.GOLD_BLOCK.defaultBlockState(),3);
            data.define("overlap","Overlap","key",new ItemStack(Blocks.REDSTONE_BLOCK),"block",40,0);data.anchor(l,pos(14).above(),"overlap","key");data.anchor(l,pos(14),"overlap","");
            running.setBoolean(null,true);data.start(l);a.setPos(14.5,81,2);act(a,pos(14).above());stand(a,pos(14));act(a,pos(14));finish(l,data);act(a,pos(14));
            check(l.getBlockState(pos(14).above()).is(Blocks.REDSTONE_BLOCK),"A result can occupy the original position of a collected part");
            data.reset(l);check(l.getBlockState(pos(14).above()).is(Blocks.GOLD_BLOCK),"Reset restores a component even when a fixed result reused its position");running.setBoolean(null,false);
            // Real blocks: no dedicated-bench settings, automatic component groups, serialized support state.
            var blocks=me.cryo.zombierool.block.system.BuildableBlocks.BENCH.get();
            var benchPos=new BlockPos(32,80,0);l.getChunkAt(benchPos);l.setBlock(benchPos.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(benchPos.east().below(),Blocks.STONE.defaultBlockState(),2);
            l.setBlock(benchPos,blocks.defaultBlockState(),2);blocks.setPlacedBy(l,benchPos,blocks.defaultBlockState(),null,net.minecraft.world.item.ItemStack.EMPTY);
            check(l.getBlockState(benchPos.east()).getBlock()==blocks&&me.cryo.zombierool.block.system.BuildableBlocks.root(l,benchPos.east()).equals(benchPos),"Physical two-block workbench has one shared interaction root");
            data.editRecipe("block_recipe","Actual block recipe",new ItemStack(Items.APPLE),"item",1);
            var p1=new BlockPos(35,80,0);var p2=new BlockPos(37,80,0);var p3=new BlockPos(39,80,0);
            for(var p:List.of(p1,p2,p3)){
                l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(p,me.cryo.zombierool.block.system.BuildableBlocks.PART.get().defaultBlockState(),2);
                data.configurePart(l,p,"block_recipe","door",new ItemStack(Items.IRON_DOOR));
            }
            String beforeInvalid=data.definitions().toString();
            rejects(()->data.configurePart(l,p1,"block_recipe","bad",ItemStack.EMPTY),"A component support rejects an empty appearance before changing its data");
            check(beforeInvalid.equals(data.definitions().toString()),"Rejected support edits preserve the original recipe and physical bindings");
            check(data.definitions().getCompound("recipes").getCompound("block_recipe").getList("parts",8).size()==1,"Duplicate support blocks create one unique logical component group");
            var support=(me.cryo.zombierool.block.system.BuildableBlocks.PartSupport)l.getBlockEntity(p1);var supportNbt=support.saveWithoutMetadata();
            check(supportNbt.getString("part").equals("door")&&supportNbt.getCompound("visual").getString("id").equals("minecraft:iron_door"),"Support block saves its recipe, group and actual item model");
            data.anchor(l,benchPos,"block_recipe","");
            var anchors=data.definitions().getList("anchors",10);boolean allFree=true;for(var t:anchors){var n=(CompoundTag)t;if(n.getString("part").isEmpty()&&!n.getString("recipe").isEmpty())allFree=false;}
            check(allFree,"Legacy dedicated-workbench configuration is normalized to free benches");
            running.setBoolean(null,true);data.start(l);BlockPos chosen=null;int selectedLocations=0;
            for(var tag:data.state(a).getList("targets",10)){var entry=(CompoundTag)tag;if(entry.getString("part").equals("door")){chosen=BlockPos.of(entry.getLong("pos"));selectedLocations++;}}
            check(selectedLocations==1,"Exactly one of three configured door supports is selected per match");
            var live=data.state(a);check(live.getList("catalog",10).stream().anyMatch(t->((CompoundTag)t).getString("recipe").equals("block_recipe")&&((CompoundTag)t).getString("status").equals("missing")),"Tab inventory lists all map components before pickup");
            a.setPos(chosen.getX()+.5,80,2);act(a,chosen);
            check(data.carried(a.getUUID()).equals("block_recipe:door"),"A randomly selected physical support can be collected");
            check(data.state(a).getList("catalog",10).stream().anyMatch(t->((CompoundTag)t).getString("recipe").equals("block_recipe")&&((CompoundTag)t).getString("status").equals("self")),"Tab inventory distinguishes the local carried piece");
            check(data.state(b).getList("catalog",10).stream().anyMatch(t->((CompoundTag)t).getString("recipe").equals("block_recipe")&&((CompoundTag)t).getString("status").equals("team")),"Tab inventory distinguishes a teammate carrying the same piece");
            a.setPos(32.5,80,2);act(a,benchPos.east());
            check(BuildableSystem.busy(a)&&data.state(a).getInt("duration")==40&&data.state(a).contains("elapsed"),"Either half of an unrestricted bench starts bar-based assembly");
            finish(l,data);check(data.completed(benchPos),"Physical support and workbench complete their shared recipe");
            data.reset(l);check(l.getBlockEntity(p1) instanceof me.cryo.zombierool.block.system.BuildableBlocks.PartSupport&&l.getBlockEntity(p2) instanceof me.cryo.zombierool.block.system.BuildableBlocks.PartSupport&&l.getBlockEntity(p3) instanceof me.cryo.zombierool.block.system.BuildableBlocks.PartSupport,"Reset restores configured component support blocks, including alternatives");running.setBoolean(null,false);
            data.define("swap_cycle","Swap cycle","one,two,three",new ItemStack(Items.APPLE),"item",40,0);
            var c1=new BlockPos(50,80,0);var c2=new BlockPos(52,80,0);var c3=new BlockPos(54,80,0);l.getChunkAt(c1);l.getChunkAt(c3);
            l.setBlock(c1,Blocks.IRON_BLOCK.defaultBlockState(),2);l.setBlock(c2,Blocks.GOLD_BLOCK.defaultBlockState(),2);l.setBlock(c3,Blocks.COPPER_BLOCK.defaultBlockState(),2);
            data.anchor(l,c1,"swap_cycle","one");data.anchor(l,c2,"swap_cycle","two");data.anchor(l,c3,"swap_cycle","three");running.setBoolean(null,true);data.start(l);
            for(var target:List.of(c3,c1,c2,c1,c2)){a.setPos(target.getX()+.5,80,2);act(a,target);}
            boolean otherStillVisible=false;for(var entity:l.getAllEntities())if(entity.getTags().contains("zr_buildable_display")&&Math.abs(entity.getX()-(c1.getX()+.5))<.01&&Math.abs(entity.getZ()-.5)<.01)otherStillVisible=true;
            check(otherStillVisible,"Repeated on-location swaps preserve the other component model when original locations form a cycle");
            data.drop(a);check(l.getBlockState(c1).is(Blocks.IRON_BLOCK)&&l.getBlockState(c2).is(Blocks.GOLD_BLOCK)&&l.getBlockState(c3).is(Blocks.COPPER_BLOCK),"Returning a component unwinds a multi-component swap cycle without losing pieces");
            data.reset(l);running.setBoolean(null,false);
            // The guided editor takes exact stacks from the player's inventory and gives configured supports.
            var handle=BuildablePackets.class.getDeclaredMethod("handle",ServerPlayer.class,BuildablePackets.Request.class);handle.setAccessible(true);
            a.getInventory().clearContent();var custom=new ItemStack(Items.DIAMOND,2);custom.getOrCreateTag().putString("guided","kept");a.getInventory().setItem(0,custom);
            var edit=new CompoundTag();edit.putString("name","Guided result");edit.putString("mode","item");edit.putBoolean("capture",true);edit.putInt("rewardSlot",0);
            handle.invoke(null,a,new BuildablePackets.Request("save",edit));
            check(data.definitions().getCompound("recipes").getCompound("guided_result").getCompound("reward").getCompound("tag").getString("guided").equals("kept"),"Guided inventory picker preserves the finished item's custom NBT");
            handle.invoke(null,a,new BuildablePackets.Request("save",edit));
            check(data.definitions().getCompound("recipes").contains("guided_result_2"),"Creating two objects with the same name never overwrites the existing recipe");
            var component=new CompoundTag();component.putString("id","guided_result");component.putString("visualItem","minecraft:iron_door");handle.invoke(null,a,new BuildablePackets.Request("support",component));
            var ready=a.getInventory().items.stream().filter(s->s.is(me.cryo.zombierool.block.system.BuildableBlocks.PART.get().asItem())).findFirst().orElseThrow();var readyTag=ready.getTag().getCompound("BlockEntityTag");
            check(readyTag.getString("recipe").equals("guided_result")&&readyTag.getString("part").equals("minecraft:iron_door")&&readyTag.getCompound("visual").getString("id").equals("minecraft:iron_door"),"Add component gives a ready-to-place support with an automatic unique component name");
            var placement=new BlockPos(60,80,0);l.getChunkAt(placement);l.setBlock(placement,me.cryo.zombierool.block.system.BuildableBlocks.PART.get().defaultBlockState(),2);
            var placed=(me.cryo.zombierool.block.system.BuildableBlocks.PartSupport)l.getBlockEntity(placement);placed.load(readyTag);placed.attach();
            check(data.definitions().getCompound("recipes").getCompound("guided_result").getList("parts",8).contains(net.minecraft.nbt.StringTag.valueOf("minecraft:iron_door")),"Placing the configured support adds its component to the recipe without another editor");
            data.reset(l);running.setBoolean(null,false);
            // Selection follows the visible panel on every supporting face.
            var switchBlock=me.cryo.zombierool.init.ZombieroolModBlocks.POWER_SWITCH.get();
            for(var face:net.minecraft.world.level.block.state.properties.AttachFace.values())for(var facing:net.minecraft.core.Direction.Plane.HORIZONTAL){
                var shape=switchBlock.defaultBlockState().setValue(me.cryo.zombierool.block.PowerSwitchBlock.FACE,face).setValue(me.cryo.zombierool.block.PowerSwitchBlock.FACING,facing).getShape(l,pos(90)).bounds();
                var axis=face==net.minecraft.world.level.block.state.properties.AttachFace.WALL?facing.getAxis():net.minecraft.core.Direction.Axis.Y;
                check(Math.abs((shape.max(axis)-shape.min(axis))-4.5/16)<.001,"Switch selection is a continuous plate: "+face+" / "+facing);
            }
            var decoyPos=pos(85);l.getChunkAt(decoyPos);var decoy=me.cryo.zombierool.block.system.BuildableBlocks.DECO.get();
            l.setBlock(decoyPos,decoy.defaultBlockState(),3);server.getPlayerList().op(a.getGameProfile());a.setGameMode(GameType.CREATIVE);a.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_DOOR));
            decoy.use(l.getBlockState(decoyPos),l,decoyPos,a,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(decoyPos),net.minecraft.core.Direction.UP,decoyPos,false));
            var decoySupport=(me.cryo.zombierool.block.system.BuildableBlocks.PartSupport)l.getBlockEntity(decoyPos);
            check(decoySupport.visual.is(Items.IRON_DOOR),"Creative right click configures a decorative component from the held item");
            String beforeDecoy=data.definitions().toString();decoySupport.attach();check(beforeDecoy.equals(data.definitions().toString()),"Decorative component never registers a collectible or a recipe");
            a.setGameMode(GameType.SURVIVAL);a.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);running.setBoolean(null,true);data.start(l);stand(a,decoyPos);act(a,decoyPos);
            check(data.carried(a.getUUID()).isEmpty()&&l.getBlockState(decoyPos).is(decoy)&&decoySupport.visual.is(Items.IRON_DOOR),"F cannot collect a decorative bait during a match");data.reset(l);running.setBoolean(null,false);
            for(var registered:new HashSet<>(me.cryo.zombierool.config.WorldConfig.get(l).getPowerSwitchPositions()))me.cryo.zombierool.config.WorldConfig.get(l).removePowerSwitchPosition(registered);
            me.cryo.zombierool.config.GlobalSwitchState.setActivated(l,false);
            var controllerPos=pos(88);l.getChunkAt(controllerPos);l.setBlock(controllerPos,me.cryo.zombierool.block.system.MapDeviceSystem.TYPES.get(me.cryo.zombierool.block.system.MapDeviceSystem.Kind.CONTROL).get().defaultBlockState(),3);
            var powerProbe=(me.cryo.zombierool.block.system.MapDeviceSystem.Device)l.getBlockEntity(controllerPos);
            check(powerProbe.hasPower(),"Default trap works on a map with no power switch");
            for(String deviceId:List.of("power_switch","trap_controller","turret","pack_a_punch","minecraft:redstone_block","minecraft:lever","minecraft:piston")){
                var device=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(deviceId.contains(":")?new net.minecraft.resources.ResourceLocation(deviceId):new net.minecraft.resources.ResourceLocation("zombierool",deviceId));check(device!=null,"Registered deployment target exists: "+deviceId);
                var machine=new ItemStack(device);var machineTag=new CompoundTag();var config=new CompoundTag();config.putString("channel","built_channel");config.putInt("cost",137);machineTag.put("Device",config);machineTag.putInt("Price",1234);machine.getOrCreateTag().put("BlockEntityTag",machineTag);
                data.define("deploy_test","Deployment test","key",machine,"deploy",40,1);
                var source=pos(74);var destination=pos(78);var wrong=pos(76);
                for(int x=73;x<=80;x++){l.getChunkAt(pos(x));l.setBlock(pos(x).below(),Blocks.STONE.defaultBlockState(),2);for(int y=80;y<85;y++)for(int z=-2;z<=3;z++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
                l.setBlock(source,Blocks.IRON_BLOCK.defaultBlockState(),2);data.anchor(l,source,"deploy_test","key");data.anchor(l,benchPos,"","");
                for(var location:List.of(destination,wrong)){l.setBlock(location,me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get().defaultBlockState(),2);var marker=(me.cryo.zombierool.block.system.BuildableBlocks.PartSupport)l.getBlockEntity(location);marker.configure(location.equals(destination)?"deploy_test":"block_recipe","destination",machine);if(location.equals(destination))new ZombieroolAPI(l).bindBuildableDestination("deploy_test",location.getX(),location.getY(),location.getZ());else data.destination(l,location,marker.recipe);}
                if(deviceId.equals("power_switch")){
                    check(MapPower.hasMapPower(l)&&!powerProbe.hasPower(),"A planned buildable power switch blocks default traps before construction");
                    powerProbe.requiresPower=false;check(powerProbe.hasPower(),"A trap configured without power stays independent");powerProbe.requiresPower=true;
                    BlockPowerSettings.setMode(l,controllerPos,BlockPowerSettings.Mode.LOCAL);l.setBlock(controllerPos.east(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);check(powerProbe.hasPower(),"An explicit local trap source works before global power is built");l.removeBlock(controllerPos.east(),false);check(!powerProbe.hasPower(),"A locally powered trap stops when its redstone source is removed");BlockPowerSettings.setMode(l,controllerPos,BlockPowerSettings.Mode.DEFAULT);
                    var floorPreview=BuildableSystem.placementState(l,destination,a,machine);check(floorPreview.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE)==net.minecraft.world.level.block.state.properties.AttachFace.FLOOR,"Ghost preview follows a floor deployment marker instead of the player's viewing angle");
                    l.setBlock(destination,l.getBlockState(destination).setValue(me.cryo.zombierool.block.system.BuildableBlocks.PartBlock.FACE,net.minecraft.core.Direction.SOUTH),2);
                    var wallPreview=BuildableSystem.placementState(l,destination,a,machine);check(wallPreview.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE)==net.minecraft.world.level.block.state.properties.AttachFace.WALL&&wallPreview.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)==net.minecraft.core.Direction.SOUTH,"Wall deployment ghost faces outwards instead of showing the back of the switch");
                    check(l.getBlockState(destination).is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get()),"Rendering or calculating a ghost never places a real machine");
                    l.setBlock(destination,l.getBlockState(destination).setValue(me.cryo.zombierool.block.system.BuildableBlocks.PartBlock.FACE,net.minecraft.core.Direction.UP),2);
                }
                running.setBoolean(null,true);data.start(l);stand(a,source);act(a,source);stand(a,benchPos);act(a,benchPos);finish(l,data);check(data.completed(benchPos),"A physical bench builds the deployable "+deviceId);
                act(a,benchPos);check(new ZombieroolAPI(l).getCarriedBuildableResult(a.getUUID().toString()).equals("deploy_test")&&l.getBlockState(destination).is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get()),"Taking "+deviceId+" carries it without placing it on the bench");
                stand(a,wrong);act(a,wrong);check(data.delivery(a.getUUID()).equals("deploy_test"),"Finished "+deviceId+" cannot be installed at a different recipe's marker");
                stand(b,destination);act(b,destination);check(l.getBlockState(destination).is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get()),"A player without the finished "+deviceId+" cannot install it");b.setPos(60,80,5);
                if(deviceId.equals("power_switch")){data.drop(a);check(data.delivery(a.getUUID()).isEmpty(),"Dropping or losing a deployable releases the carried object");stand(a,benchPos);act(a,benchPos);check(data.delivery(a.getUUID()).equals("deploy_test"),"The released unique deployable is available again without rebuilding");}
                stand(a,destination);BuildableSystem.interact(a,destination);check(!BuildableSystem.busy(a),"A simple press cannot install "+deviceId+" without holding F");act(a,destination);
                BuildableSystem.holdAction(a,destination,false);finish(l,data);check(l.getBlockState(destination).is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get())&&!data.delivery(a.getUUID()).isEmpty(),"Releasing F cancels installation and keeps the transported "+deviceId);act(a,destination);
                check(BuildableSystem.busy(a)&&data.state(a).getBoolean("installing")&&data.state(a).getInt("duration")==40&&l.getBlockState(destination).is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get()),"Installing "+deviceId+" starts an animated progress bar before placing any block");
                if(deviceId.equals("power_switch")){
                    WaveManager.setGamePaused(true);for(int frame=0;frame<45;frame++){l.getServer().getWorldData().overworldData().setGameTime(l.getGameTime()+1);tick(l,data);}check(BuildableSystem.busy(a)&&l.getBlockState(destination).is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get()),"Pausing freezes installation instead of creating the machine early");WaveManager.setGamePaused(false);
                    a.setPos(85,80,5);tick(l,data);check(!BuildableSystem.busy(a)&&data.delivery(a.getUUID()).equals("deploy_test")&&l.getBlockState(destination).is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get()),"Leaving an installation cancels the animation without consuming the transported machine");stand(a,destination);act(a,destination);
                }
                finish(l,data);check(l.getBlockState(destination).is(device)&&data.delivery(a.getUUID()).isEmpty(),"F installs the actual "+deviceId+" and consumes the transported result");
                if(deviceId.equals("power_switch")){
                    check(!powerProbe.hasPower(),"Installed but inactive power switch keeps traps unpowered");
                    me.cryo.zombierool.block.PowerSwitchBlock.togglePower(l,destination,a);check(powerProbe.hasPower(),"Activating the crafted power switch powers default traps");
                }
                if(deviceId.equals("minecraft:redstone_block"))check(l.getBlockState(destination).getSignal(l,destination,net.minecraft.core.Direction.UP)==15,"A deployed vanilla redstone block supplies an actual redstone signal");
                if(deviceId.equals("pack_a_punch"))check(l.getBlockEntity(destination).saveWithoutMetadata().getInt("Price")==1234,"Deployed Pack-a-Punch retains its configured price");
                if(deviceId.equals("turret")||deviceId.equals("trap_controller"))check(l.getBlockEntity(destination).saveWithoutMetadata().getCompound("Device").getString("channel").equals("built_channel"),"Deployed "+deviceId+" retains its configured channel");
                stand(a,benchPos);act(a,benchPos);check(data.delivery(a.getUUID()).isEmpty(),"Installing the unique "+deviceId+" does not allow a second copy");
                data.reset(l);running.setBoolean(null,false);check(l.getBlockState(destination).is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get())&&((me.cryo.zombierool.block.system.BuildableBlocks.PartSupport)l.getBlockEntity(destination)).recipe.equals("deploy_test"),"Reset restores the configured placement marker for "+deviceId);
            }
            check(!me.cryo.zombierool.config.WorldConfig.get(l).getPowerSwitchPositions().contains(pos(78))&&!me.cryo.zombierool.config.GlobalSwitchState.isActivated(l),"Restoring a crafted switch marker removes stale power registration and resets its power");
            for(var block:List.of(Blocks.PISTON,Blocks.LEVER,Blocks.REDSTONE_BLOCK)){
                var pose=me.cryo.zombierool.block.system.BuildableBlocks.componentPose(new ItemStack(block),net.minecraft.core.Direction.UP);
                check(pose.context()==net.minecraft.world.item.ItemDisplayContext.NONE&&pose.size()>=.8f&&pose.y()>.4,"Component "+net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block)+" uses the full block model above the floor instead of the miniature ground-item transform");
            }
            var categoryChecks=Map.ofEntries(
                Map.entry("black_pumpkin","DECORATION"),Map.entry("decorative_component","DECORATION"),Map.entry("teddy_bear","DECORATION"),
                Map.entry("blood_brush","TOOLS"),Map.entry("sound_emitter","TOOLS"),Map.entry("zombie_spawn_egg","SPAWNERS"),Map.entry("universal_spawner","SPAWNERS"),
                Map.entry("buildable_part","CRAFTING"),Map.entry("buildable_workbench","CRAFTING"),Map.entry("fire_pit","MACHINES"),Map.entry("buy_wall_weapon","MACHINES"),
                Map.entry("grenade","WEAPONS"),Map.entry("molotov","WEAPONS"),Map.entry("monkey_bomb","WEAPONS"),Map.entry("cerberus","QUESTS"),Map.entry("soul_chest","QUESTS"),Map.entry("lunar_helmet","SUITS"),Map.entry("stielhandgranate","WEAPONS"),Map.entry("power_switch","SIGNALS"),Map.entry("meteorite","QUESTS"),
                Map.entry("asphalt_stairs","BUILDING"),Map.entry("death_barrier","NAVIGATION"),Map.entry("texture_kit","TEXTURES"));
            for(var entry:categoryChecks.entrySet()){
                var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("zombierool",entry.getKey()));
                check(item!=null&&me.cryo.zombierool.init.CreativeCatalog.category(new ItemStack(item)).name().equals(entry.getValue()),"Creative catalog classifies "+entry.getKey()+" by its actual purpose: "+entry.getValue());
            }
            for(var registered:net.minecraftforge.registries.ForgeRegistries.ITEMS.getEntries())if(registered.getKey().location().getNamespace().equals("zombierool")){
                var stack=new ItemStack(registered.getValue());if(me.cryo.zombierool.core.system.WeaponFacade.isWeapon(stack)||stack.getItem() instanceof me.cryo.zombierool.item.throwable.ThrowableCore.BaseThrowableItem)check(me.cryo.zombierool.init.CreativeCatalog.category(stack)==me.cryo.zombierool.init.CreativeCatalog.Category.WEAPONS,"All registered weapon variants stay in Weapons: "+registered.getKey().location());
            }
            System.out.println("BUILDABLE RUNTIME PASS: "+checks+" checks");
        }catch(Throwable failure){failure.printStackTrace();}finally{server.halt(false);}
    }
}
