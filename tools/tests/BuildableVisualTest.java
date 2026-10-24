package local.buildablevisual;

import me.cryo.zombierool.block.system.BuildableBlocks;
import me.cryo.zombierool.gameplay.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import java.nio.file.Path;

/** In-process client render regression; writes real Minecraft frames without touching a user instance. */
@Mod("buildablevisual")
public final class BuildableVisualTest {
    private boolean requested,done;
    private int ticks,checks;
    private volatile String failure;
    public BuildableVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("BUILDABLE VISUAL CHECK: "+label);}
    private void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable t){t.printStackTrace();failure=t.toString();}});
    }
    private void shot(String name)throws Exception{
        var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(Path.of("D:/Dev/ZombieRool/build/buildable-visuals/"+name+".png"));}System.out.println("BUILDABLE VISUAL FRAME: "+name);
    }
    private void widgets(){
        var mc=Minecraft.getInstance();check(mc.screen instanceof me.cryo.zombierool.client.gui.BuildableScreen,"Right click opens the buildable editor");
        for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget w){
            check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=mc.getWindow().getGuiScaledWidth()&&w.getY()+w.getHeight()<=mc.getWindow().getGuiScaledHeight(),"Editor control stays in the visible window");
            String text=w.getMessage().getString().toLowerCase(java.util.Locale.ROOT);check(!text.contains("dedicated")&&!text.contains("dÃƒÆ’Ã‚Â©diÃƒÆ’Ã‚Â©")&&!text.contains("ticks"),"No dedicated-workbench or timing setting is exposed");
        }
    }
    private void press(String key){
        String label=net.minecraft.network.chat.Component.translatable("gui.zombierool.buildable."+key).getString();
        for(var child:Minecraft.getInstance().screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals(label)){button.onPress();return;}
        throw new AssertionError("Missing guided editor button: "+key);
    }
    private void chooseCell(int index){int i=0;for(var child:Minecraft.getInstance().screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().isEmpty()){if(i++==index){button.onPress();return;}}throw new AssertionError("Missing picker cell "+index);}
    private void tick(TickEvent.ClientTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        try{
            if(failure!=null)throw new AssertionError(failure);
            if(!requested){requested=true;mc.options.pauseOnLostFocus=false;mc.options.fov().set(70);mc.options.hideGui=true;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"BuildableVisualFixture");return;}
            if(mc.level==null||mc.player==null||mc.isPaused())return;ticks++;if(Boolean.getBoolean("zr.test.creativeOnly")&&ticks==1)ticks=490;
            if(ticks==70)server(p->{p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.teleportTo(p.serverLevel(),3.5,65,-14,144.5f,8);p.serverLevel().setDayTime(1000);
                for(int x=40;x<=49;x++)for(int z=-2;z<=9;z++){p.serverLevel().getChunkAt(new BlockPos(x,65,z));p.serverLevel().setBlock(new BlockPos(x,64,z),net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(),3);for(int y=65;y<=70;y++)p.serverLevel().setBlock(new BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);}
                net.minecraft.world.item.ItemStack[] shapes={new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.PISTON),new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.LEVER),new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK)};
                for(int i=0;i<3;i++){var where=new BlockPos(42+i*2,65,2);p.serverLevel().setBlock(where,BuildableBlocks.PART.get().defaultBlockState(),3);((BuildableBlocks.PartSupport)p.serverLevel().getBlockEntity(where)).configure("","",shapes[i]);}
                var wall=new BlockPos(44,66,0);p.serverLevel().setBlock(wall.north(),net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(),3);p.serverLevel().setBlock(wall,BuildableBlocks.DESTINATION.get().defaultBlockState().setValue(BuildableBlocks.PartBlock.FACE,net.minecraft.core.Direction.SOUTH),3);var papGhost=new BlockPos(47,65,0);p.serverLevel().setBlock(papGhost.below(),net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(),3);p.serverLevel().setBlock(papGhost,BuildableBlocks.DESTINATION.get().defaultBlockState(),3);((BuildableBlocks.PartSupport)p.serverLevel().getBlockEntity(papGhost)).configure("","destination",new net.minecraft.world.item.ItemStack(me.cryo.zombierool.block.system.PackAPunchSystem.PACK_A_PUNCH.get()));
                ((BuildableBlocks.PartSupport)p.serverLevel().getBlockEntity(wall)).configure("","destination",new net.minecraft.world.item.ItemStack(me.cryo.zombierool.init.ZombieroolModBlocks.POWER_SWITCH.get()));p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(p.serverLevel(),44.5,66,7,180,18);
            });
            if(ticks==110){
                me.cryo.zombierool.client.BuildableClient.receive(false,new CompoundTag());
                check(me.cryo.zombierool.client.BuildableClient.drawTab(new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource()),640,18,160,java.util.Map.of("meteorite",1),java.util.Map.of("meteorite",3))==0,"Meteorites do not create a strip in the buildable list");
                for(var face:new net.minecraft.world.level.block.state.properties.AttachFace[]{net.minecraft.world.level.block.state.properties.AttachFace.FLOOR,net.minecraft.world.level.block.state.properties.AttachFace.CEILING}){
                    var panelState=me.cryo.zombierool.init.ZombieroolModBlocks.POWER_SWITCH.get().defaultBlockState().setValue(me.cryo.zombierool.block.PowerSwitchBlock.FACE,face);
                    var panelModel=mc.getBlockRenderer().getBlockModel(panelState);float minY=100,maxY=-100;
                    java.util.List<net.minecraft.core.Direction> faces=new java.util.ArrayList<>();faces.add(null);for(var d:net.minecraft.core.Direction.values())faces.add(d);
                    for(var side:faces)for(var quad:panelModel.getQuads(panelState,side,net.minecraft.util.RandomSource.create(0))){var vertices=quad.getVertices();for(int j=1;j<vertices.length;j+=8){float y=Float.intBitsToFloat(vertices[j]);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}}
                    check(face==net.minecraft.world.level.block.state.properties.AttachFace.FLOOR?minY>=-.01f&&maxY<.4f:minY>.6f&&maxY<=1.01f,"Power switch model touches its supporting face: "+face);
                }
                var decoy=BuildableBlocks.DECO.get().defaultBlockState();var modelDecoy=mc.getBlockRenderer().getBlockModel(decoy);
                check(!modelDecoy.getParticleIcon().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"Decorative component uses the real support model and textures");
                check(me.cryo.zombierool.client.renderer.BuildablePartRenderer.ghostVisible(mc.player,false),"Installation ghost is visible to a creative map maker outside a match");
                check(!me.cryo.zombierool.client.renderer.BuildablePartRenderer.ghostVisible(mc.player,true),"Installation ghost is hidden during a match even for a creative player");
                check(mc.getLanguageManager().getSelected().equals("en_us"),"English client language is active");
                check(net.minecraft.network.chat.Component.translatable("gui.zombierool.buildable.place_support").getString().startsWith("Component saved"),"Placement instructions are localized on the client");
                check(java.util.Arrays.stream(com.tacz.guns.client.gui.overlay.InteractKeyTextOverlay.class.getDeclaredMethods()).anyMatch(m->m.getName().contains("hideInteractionText")),"TacZ interaction text overlay suppression is applied");
                var model=mc.getBlockRenderer().getBlockModel(mc.level.getBlockState(new BlockPos(0,65,-18)));
                check(!model.getParticleIcon().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"Native workbench model and pine texture bake correctly");
                var support=mc.level.getBlockEntity(new BlockPos(-23,66,-3));check(support instanceof BuildableBlocks.PartSupport,"Physical support block is present in the world");
                check((Object)mc.getBlockEntityRenderDispatcher().getRenderer(support) instanceof me.cryo.zombierool.client.renderer.BuildablePartRenderer,"Support has a renderer for its actual configured object");shot("component-sizes-and-wall-marker");
            }
            if(ticks==115)server(p->{p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();});
            if(ticks==120){check(!me.cryo.zombierool.client.renderer.BuildablePartRenderer.ghostVisible(mc.player,false),"Installation ghost and marker are hidden in survival outside a match");shot("installation-ghost-hidden-survival");}
            if(ticks==121){
                var state=new CompoundTag();var targets=new ListTag();var target=new CompoundTag();target.putLong("pos",new BlockPos(44,66,0).asLong());target.putBoolean("destination",true);target.putBoolean("ready",true);targets.add(target);state.put("targets",targets);me.cryo.zombierool.client.BuildableClient.receive(false,state);
                check(me.cryo.zombierool.client.BuildableClient.readyToInstall(new BlockPos(44,66,0)),"Only the carried buildable's matching destination enables the survival ghost");
                check(!me.cryo.zombierool.client.BuildableClient.readyToInstall(new BlockPos(47,65,0)),"Other recipe destinations stay hidden");
                check(me.cryo.zombierool.client.renderer.BuildablePartRenderer.ghostAlpha(true,10)>me.cryo.zombierool.client.renderer.BuildablePartRenderer.ghostAlpha(true,30),"Ready-to-install highlight pulses between bright and dim");
            }
            if(ticks==124){shot("installation-ghost-ready-survival");me.cryo.zombierool.client.BuildableClient.receive(false,new CompoundTag());}
            if(ticks==125)server(p->{p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.teleportTo(p.serverLevel(),3.5,65,-14,144.5f,8);});
            if(ticks==130){mc.options.hideGui=false;server(p->{p.getInventory().clearContent();p.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));p.getInventory().setItem(1,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_DOOR));BuildablePackets.openAt(p,new BlockPos(0,65,-18),"");});}
            if(ticks==155){widgets();shot("create-buildable");for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.EditBox field)field.setValue("Essai guide");press("choose_result");}
            if(ticks==165){widgets();shot("item-picker");chooseCell(0);}
            if(ticks==175){shot("result-selected");press("save_step");}
            if(ticks==195){check(mc.screen instanceof me.cryo.zombierool.client.gui.BuildableScreen,"Saving a new buildable keeps the guided editor open");press("add_component");}
            if(ticks==205)chooseCell(1);
            if(ticks==215)check(mc.screen==null,"Choosing a component closes the editor so its support can be placed immediately");
            if(ticks==220)server(p->{
                var stack=p.getInventory().items.stream().filter(i->i.is(BuildableBlocks.PART.get().asItem())).findFirst().orElseThrow();
                check(stack.getHoverName().getString().contains("Iron Door"),"Component support item name follows the English client language");
                check(stack.getTag().getCompound("BlockEntityTag").getString("recipe").equals("essai_guide"),"The guided client workflow gives the correct ready-to-place component");
                var pos=new BlockPos(5,65,-18);p.serverLevel().setBlock(pos,BuildableBlocks.PART.get().defaultBlockState(),3);var support=(BuildableBlocks.PartSupport)p.serverLevel().getBlockEntity(pos);support.load(stack.getTag().getCompound("BlockEntityTag"));support.attach();BuildablePackets.openAt(p,new BlockPos(0,65,-18),"");
            });
            if(ticks==235){for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().contains("MP5")){button.onPress();break;}}
            if(ticks==240){widgets();shot("workbench-editor");mc.setScreen(null);}
            if(ticks==255)server(p->BuildablePackets.openAt(p,new BlockPos(-23,66,-3),""));
            if(ticks==275){widgets();shot("part-editor");mc.setScreen(null);}
            if(ticks==290){
                server(p->{var state=BuildableSystem.get(p.serverLevel()).state(p);var items=state.getList("catalog",10);if(items.size()>2){items.getCompound(0).putString("status","self");items.getCompound(1).putString("status","team");items.getCompound(2).putString("status","installed");}mc.execute(()->{me.cryo.zombierool.client.BuildableClient.receive(false,state);});});
                mc.options.keyPlayerList.setDown(true);WaveManager.setClientGameRunning(true);
            }
            if(ticks==315){check(net.minecraft.network.chat.Component.translatable("gui.zombierool.buildable.tab_recipes").getString().equals("Quest items and recipes"),"Grouped Tab heading follows English client language");shot("tab-components");mc.options.keyPlayerList.setDown(false);}
            if(ticks==325){var state=new CompoundTag();var entries=new ListTag();var target=new CompoundTag();var location=new BlockPos(1,65,1);target.putLong("pos",location.asLong());target.putString("part","minecraft:raw_gold");entries.add(target);state.put("targets",entries);me.cryo.zombierool.client.BuildableClient.receive(false,state);check(me.cryo.zombierool.client.BuildableClient.hasTarget(location)&&me.cryo.zombierool.client.BuildableClient.hint(location,"F").getString().isEmpty(),"A hidden component keeps its F target without showing a pickup hint or raw item identifier");state.putBoolean("busy",true);state.putInt("elapsed",20);state.putInt("duration",120);me.cryo.zombierool.client.BuildableClient.receive(false,state);}
            if(ticks==340){shot("assembly-bar");me.cryo.zombierool.client.BuildableClient.receive(false,new CompoundTag());}
            if(ticks==355)server(p->{
                try{
                    p.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);WaveManager.startGame(p.serverLevel());var data=BuildableSystem.get(p.serverLevel());
                    var tick=BuildableSystem.class.getDeclaredMethod("tick",net.minecraft.server.level.ServerLevel.class);tick.setAccessible(true);var bench=new BlockPos(0,65,-18);
                    for(String id:new String[]{"canon","culasse","crosse"}){
                        BlockPos source=null;net.minecraft.world.phys.Vec3 pickupAim=null;for(var tag:data.state(p).getList("targets",10)){var n=(CompoundTag)tag;if(n.getString("part").equals(id)){source=BlockPos.of(n.getLong("pos"));var box=n.getCompound("bounds");pickupAim=new net.minecraft.world.phys.Vec3((box.getDouble("x0")+box.getDouble("x1"))/2,(box.getDouble("y0")+box.getDouble("y1"))/2,(box.getDouble("z0")+box.getDouble("z1"))/2);}}
                        if(source==null)throw new AssertionError("Missing part "+id);p.teleportTo(source.getX()+.5,65,source.getZ()+1.5);p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,pickupAim);BuildableSystem.interact(p,source);check(!data.carried(p.getUUID()).isEmpty(),"Precisely aiming at the actual component picks up "+id);
                        p.teleportTo(.5,65,-16);BuildableSystem.holdAction(p,bench,false);BuildableSystem.holdAction(p,bench,true);p.serverLevel().getServer().getWorldData().overworldData().setGameTime(p.serverLevel().getGameTime()+45);BuildableSystem.holdAction(p,bench,true);tick.invoke(data,p.serverLevel());
                    }
                    check(data.completed(bench),"Actual MP5 recipe finishes on a physical workbench");p.getAbilities().invulnerable=true;p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();WaveManager.setGamePaused(true);p.teleportTo(p.serverLevel(),3.5,66,-14.5,140,22);p.serverLevel().setDayTime(1000);
                }catch(Exception x){throw new RuntimeException(x);}
            });
            if(ticks==390)mc.options.hideGui=true;
            if(ticks==410){
                int visibleResults=0;for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.Display.ItemDisplay&&entity.distanceToSqr(1,66,-17.5)<4){var tag=entity.saveWithoutId(new CompoundTag());if(!tag.getCompound("item").isEmpty()){visibleResults++;System.out.println("BUILDABLE RESULT CLIENT NBT: "+tag);}}
                check(visibleResults==1,"Completed weapon remains present and tracked by the client above the table");shot("completed-mp5");}
            if(ticks==420)server(p->{WaveManager.setGamePaused(false);p.teleportTo(.5,65,-16);BuildableSystem.interact(p,new BlockPos(0,65,-18));check(p.getInventory().items.stream().anyMatch(i->me.cryo.zombierool.core.system.WeaponFacade.getWeaponId(i).contains("mp5")),"Actual completed MP5 can be taken from the bench");WaveManager.setGamePaused(true);p.teleportTo(p.serverLevel(),3.5,66,-14.5,140,22);});
            if(ticks==445){int results=0;for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.Display.ItemDisplay&&entity.distanceToSqr(1,66,-17.5)<4&&!entity.saveWithoutId(new CompoundTag()).getCompound("item").isEmpty())results++;check(results==1,"Taking the unique MP5 leaves its completed model displayed on the table");shot("completed-after-take");}
            if(ticks==450){mc.options.hideGui=false;var state=new CompoundTag();state.putString("carried","demo:piston");state.put("icon",new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.PISTON).save(new CompoundTag()));me.cryo.zombierool.client.BuildableClient.receive(false,state);}
            if(ticks==465)shot("carried-component-hud");
            if(ticks==470){var state=new CompoundTag();state.putString("delivery","demo_switch");state.put("icon",new net.minecraft.world.item.ItemStack(me.cryo.zombierool.init.ZombieroolModBlocks.POWER_SWITCH.get()).save(new CompoundTag()));state.putBoolean("busy",true);state.putBoolean("installing",true);state.putInt("duration",120);state.putInt("elapsed",20);me.cryo.zombierool.client.BuildableClient.receive(false,state);}
            if(ticks==485){check(me.cryo.zombierool.client.BuildableClient.busy,"Installation reuses the synchronized gameplay action lock");shot("installation-bar-and-carried-machine");}
            if(ticks==490)server(p->{p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);});
            if(ticks==500){
                var screen=new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player,mc.level.enabledFeatures(),true);mc.setScreen(screen);
                var select=net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab",net.minecraft.world.item.CreativeModeTab.class);select.setAccessible(true);select.invoke(screen,me.cryo.zombierool.init.ZombieroolModTabs.ZB_RCT.get());
            }
            if(ticks==505){
                var screen=(net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)mc.screen;int x=(screen.width-195)/2-54,y=Math.max(4,Math.min((screen.height-136)/2+8,screen.height-((me.cryo.zombierool.init.CreativeCatalog.Category.values().length+1)/2)*28-4));
                check(screen.getMenu().items.stream().noneMatch(stack->stack.is(BuildableBlocks.PART.get().asItem())||stack.is(BuildableBlocks.DESTINATION.get().asItem())),"Unconfigured component supports and deployment markers are absent from the creative catalog");
                var decoyIcon=mc.getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(BuildableBlocks.DECO.get()),mc.level,mc.player,0);
                check(decoyIcon.isGui3d()&&decoyIcon.getParticleIcon().contents().name().toString().equals("minecraft:block/gray_concrete"),"Decoy component uses a simple native grey plate icon with an orange X");
                var categories=me.cryo.zombierool.init.CreativeCatalog.Category.values();
                for(int i=0;i<categories.length;i++){
                    check(screen.mouseClicked(x+(i%2)*26+12,y+(i/2)*28+12,0),"Creative category is clickable: "+categories[i]);
                    check(!screen.getMenu().items.isEmpty(),"Creative category has visible contents: "+categories[i]);
                    if(categories[i]==me.cryo.zombierool.init.CreativeCatalog.Category.WEAPONS)for(String id:new String[]{"grenade","molotov","monkey_bomb"})check(screen.getMenu().items.stream().anyMatch(stack->net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals("zombierool:"+id)),"Throwable is visible under Weapons and Equipment: "+id);
                    if(categories[i]==me.cryo.zombierool.init.CreativeCatalog.Category.QUESTS)for(String id:new String[]{"soul_chest","cerberus"})check(screen.getMenu().items.stream().anyMatch(stack->net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals("zombierool:"+id)),"Soul objective is visible under Objectives and Rewards: "+id);
                    for(var stack:screen.getMenu().items)check(categories[i]==me.cryo.zombierool.init.CreativeCatalog.Category.ALL||me.cryo.zombierool.init.CreativeCatalog.category(stack)==categories[i],"Creative category only shows matching items");
                }
                int i=java.util.Arrays.asList(categories).indexOf(me.cryo.zombierool.init.CreativeCatalog.Category.DECORATION);screen.mouseClicked(x+(i%2)*26+12,y+(i/2)*28+12,0);
                check(screen.getMenu().items.stream().anyMatch(stack->stack.is(me.cryo.zombierool.init.ZombieroolModBlocks.BLACK_PUMPKIN.get().asItem())),"Black Pumpkin is visible among decorative props");
            }
            if(ticks==515){shot("creative-decoration-categories");System.out.println("BUILDABLE VISUAL PASS: "+checks+" checks");done=true;mc.stop();}
        }catch(Throwable t){t.printStackTrace();done=true;mc.stop();}
    }
}
