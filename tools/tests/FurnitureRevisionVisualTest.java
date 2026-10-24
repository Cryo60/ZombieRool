package local.atmospherevisual;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.gameplay.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("atmospherevisual")
public final class FurnitureRevisionVisualTest {
    boolean requested,done;int age,checks;volatile String failure;final BlockPos pos=new BlockPos(0,65,15);
    record Case(String id,String state){}final java.util.List<Case> cases=new java.util.ArrayList<>();final boolean filtered=System.getenv("ZR_FURNITURE_FILTER")!=null;final int step=filtered?70:14;
    public FurnitureRevisionVisualTest(){for(String id:FurnitureSystem.MODELS)if(!filtered||java.util.Arrays.stream(System.getenv("ZR_FURNITURE_FILTER").split(",")).anyMatch(id::contains))cases.add(new Case(id,"furniture"));if(!filtered)for(String id:java.util.List.of("pave_low","stryker","btr80","bmp2","humvee","snowmobile","zodiac"))for(String state:java.util.List.of("normal","broken","burnt","fire"))cases.add(new Case(id,state));MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+text);}
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable e){failure=e.toString();e.printStackTrace();}});}
    void shot(String name)throws Exception{var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),name+".png"));}}
    void verifyConnectionsAndStacking(net.minecraft.server.level.ServerPlayer p){try{
        var l=p.serverLevel();var at=pos.west(4);var sofa=FurnitureSystem.ALL.get("sofa").get();var corner=FurnitureSystem.ALL.get("sofa_corner").get();
        l.setBlock(at,sofa.defaultBlockState(),3);l.setBlock(at.east(),corner.defaultBlockState(),3);l.setBlock(at.east().north(),sofa.defaultBlockState().setValue(FurnitureSystem.FurnitureBlock.FACING,Direction.EAST),3);
        check(l.getBlockState(at).getValue(FurnitureSystem.SofaBlock.RIGHT),"Straight sofa connects to its neighbouring corner");
        check(l.getBlockState(at.east()).getValue(FurnitureSystem.SofaBlock.LEFT),"Sofa corner connects to the perpendicular front branch");
        l.setBlock(at.east(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);check(!l.getBlockState(at).getValue(FurnitureSystem.SofaBlock.RIGHT),"Removing a sofa neighbour restores the end armrest");
        l.setBlock(at,ObstacleDoorSystem.BLOCK.get().defaultBlockState(),3);l.setBlock(at.east(),ObstacleDoorSystem.BLOCK.get().defaultBlockState(),3);var first=(ObstacleDoorSystem.ObstacleDoorBlockEntity)l.getBlockEntity(at);var second=(ObstacleDoorSystem.ObstacleDoorBlockEntity)l.getBlockEntity(at.east());first.setMimic(sofa.defaultBlockState());second.setMimic(corner.defaultBlockState());
        check(MimicSystem.getConnectedState(first.getMimic(),l,at).getValue(FurnitureSystem.SofaBlock.RIGHT),"Buyable Door sofa copies connect through mimic neighbours");
        l.setBlock(at,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);l.setBlock(at.east(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);l.setBlock(at.east().north(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        var pallet=FurnitureSystem.ALL.get("pallet").get();l.setBlock(at,pallet.defaultBlockState(),3);var stack=new net.minecraft.world.item.ItemStack(pallet,8);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        for(int layer=2;layer<=4;layer++){var hit=new BlockHitResult(Vec3.atLowerCornerOf(at).add(.5,(layer-1)*.25,.5),Direction.UP,at,false);var context=new net.minecraft.world.item.context.BlockPlaceContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit);((net.minecraft.world.item.BlockItem)stack.getItem()).place(context);var state=l.getBlockState(at);check(state.getValue(FurnitureSystem.PalletBlock.LAYERS)==layer&&Math.abs(state.getShape(l,at).max(Direction.Axis.Y)-layer*.25)<.001,"Actual item placement stacks pallet layer "+layer+" without a gap");}
        l.setBlock(at,ObstacleDoorSystem.BLOCK.get().defaultBlockState(),3);var copied=(ObstacleDoorSystem.ObstacleDoorBlockEntity)l.getBlockEntity(at);copied.setMimic(pallet.defaultBlockState());var hit=new BlockHitResult(Vec3.atCenterOf(at),Direction.UP,at,false);var next=MimicSystem.getStateForMimic(p,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit,pallet);check(next.getValue(FurnitureSystem.PalletBlock.LAYERS)==2,"Pallet copies can be stacked on a Buyable Door as well");l.setBlock(at,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
    }catch(Exception e){throw new RuntimeException(e);}}
    void verifyExtendedAndGroups(net.minecraft.server.level.ServerPlayer p){try{
        var l=p.serverLevel();var at=pos.west(5);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.setYRot(0);
        for(String id:java.util.List.of("bed","hospital_bed","iv_stand")){
            var block=(FurnitureSystem.ExtendedFurnitureBlock)FurnitureSystem.ALL.get(id).get();var stack=new net.minecraft.world.item.ItemStack(block);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
            var hit=new BlockHitResult(Vec3.atLowerCornerOf(at),Direction.UP,at.below(),false);var context=new net.minecraft.world.item.context.BlockPlaceContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit);
            ((net.minecraft.world.item.BlockItem)stack.getItem()).place(context);var state=l.getBlockState(at);check(state.is(block)&&!state.getValue(FurnitureSystem.ExtendedFurnitureBlock.EXTENSION),id+" places as one complete item");var other=at.relative(block.extensionDirection(state));check(l.getBlockState(other).is(block)&&l.getBlockState(other).getValue(FurnitureSystem.ExtendedFurnitureBlock.EXTENSION),id+" automatically reserves its second cell");
            l.removeBlock(other,false);check(l.getBlockState(at).isAir(),id+" removing either part removes the whole object");
            l.setBlock(other,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);check(block.getStateForPlacement(context)==null,id+" refuses placement into an occupied extension cell");l.removeBlock(other,false);
        }
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        var head=me.cryo.zombierool.maptexture.MapTextures.BLOCKS[0].head.get();l.setBlock(at,head.defaultBlockState(),3);var headState=l.getBlockState(at);head.use(headState,l,at,p,net.minecraft.world.InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(at),Direction.UP,at,false));check(l.getBlockState(at).getValue(me.cryo.zombierool.maptexture.MapTextures.TexBlock.FACING)==headState.getValue(me.cryo.zombierool.maptexture.MapTextures.TexBlock.FACING).getClockWise(),"Texture Kit head rotates on empty-hand creative right-click");l.removeBlock(at,false);
        for(String id:java.util.List.of("bed","dumpster","humvee")){
            var max=at.offset(3,3,7);for(var cell:BlockPos.betweenClosed(at,max))l.setBlock(cell,ObstacleDoorSystem.BLOCK.get().defaultBlockState(),3);
            check(l.getBlockState(at).getShape(l,at).bounds().equals(new AABB(0,0,0,1,1,1)),"Untextured obstacle has full selection bounds");
            var source=id.equals("humvee")?MapDeviceSystem.ALL_TYPES.get("decor_"+id).get().defaultBlockState():FurnitureSystem.ALL.get(id).get().defaultBlockState();check(ObstacleDecorGroups.apply(p,at,source),id+" applies to part of an obstacle field");var be=(ObstacleDoorSystem.ObstacleDoorBlockEntity)l.getBlockEntity(at);var members=be.decorGroup.getLongArray("cells");check(members.length>0&&members.length<128,id+" reserves only its natural footprint");check(be.decorGroup.getDouble("scale")==1,id+" keeps natural scale rather than stretching");if(id.equals("dumpster"))check(members.length==18,"3 x 2 x 2.5 dumpster reserves exactly 18 cells");
            var excluded=max.immutable();check(((ObstacleDoorSystem.ObstacleDoorBlockEntity)l.getBlockEntity(excluded)).decorGroup.isEmpty(),id+" leaves adjacent excess obstacles untouched");be.setPrix(500);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.teleportTo(l,at.getX()+.5,at.getY(),at.getZ()+2,180,0);p.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap->cap.setPoints(1000));me.cryo.zombierool.handlers.ObstaclePurchaseHandler.tryPurchase(p,at);
            for(long packed:members)check(l.getBlockState(BlockPos.of(packed)).is(me.cryo.zombierool.init.ZombieroolModBlocks.PATH.get()),id+" purchase removes a reserved cell");check(l.getBlockEntity(excluded) instanceof ObstacleDoorSystem.ObstacleDoorBlockEntity,id+" purchase preserves adjacent unrelated obstacles");check(me.cryo.zombierool.gameplay.PointManager.getScore(p)==500,id+" purchase charges once");for(var cell:BlockPos.betweenClosed(at,max))l.removeBlock(cell,false);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }
        for(String id:LargeFurnitureBlock.SIZES.keySet()){
            var block=(LargeFurnitureBlock)FurnitureSystem.ALL.get(id).get();var stack=new net.minecraft.world.item.ItemStack(block);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);var context=new net.minecraft.world.item.context.BlockPlaceContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atLowerCornerOf(at),Direction.UP,at.below(),false));((net.minecraft.world.item.BlockItem)stack.getItem()).place(context);check(l.getBlockState(at).is(block),id+" places as a complete multi-block item");var state=l.getBlockState(at);for(int i=0;i<block.partCount;i++)check(l.getBlockState(at.offset(block.offset(i,state.getValue(FurnitureSystem.FurnitureBlock.FACING)))).getValue(LargeFurnitureBlock.PART)==i,id+" reserves correct body part "+i);
            if(id.equals("dumpster")){var box=block.wholeShape(state).bounds();check(box.getXsize()==3&&box.getYsize()==2.5&&box.getZsize()==2,"Dumpster geometry measures exactly 3 x 2 x 2.5");}
            l.removeBlock(at,false);for(int i=0;i<block.partCount;i++)check(l.getBlockState(at.offset(block.offset(i,state.getValue(FurnitureSystem.FurnitureBlock.FACING)))).isAir(),id+" removes all parts together");
        }
        var duckboard=FurnitureSystem.ALL.get("duckboard").get();l.setBlock(at,duckboard.defaultBlockState(),3);var duckStack=new net.minecraft.world.item.ItemStack(duckboard,4);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,duckStack);for(int layer=2;layer<=4;layer++){var context=new net.minecraft.world.item.context.BlockPlaceContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,duckStack,new BlockHitResult(Vec3.atLowerCornerOf(at).add(.5,(layer-1)*.25,.5),Direction.UP,at,false));((net.minecraft.world.item.BlockItem)duckStack.getItem()).place(context);check(l.getBlockState(at).getValue(FurnitureSystem.PalletBlock.LAYERS)==layer,"Duckboard stacks through actual item placement: "+layer);}l.removeBlock(at,false);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        var glass=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("zombierool:glass_defense_door"));
        var lower=glass.defaultBlockState();l.setBlock(at,lower,3);l.setBlock(at.above(),lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);
        var zombie=new me.cryo.zombierool.entity.ZombieEntity(me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get(),l);String key="zombie_attack_time_"+DefenseDoorSystem.attackRoot(l,at).asLong();var attack=DefenseDoorSystem.DefenseDoorBlockEntity.class.getDeclaredMethod("handleMobAttack",me.cryo.zombierool.entity.ZombieEntity.class,net.minecraft.world.level.Level.class,BlockPos.class,net.minecraft.world.level.block.state.BlockState.class,int.class);attack.setAccessible(true);
        zombie.getPersistentData().putLong(key,l.getGameTime()-30);check((boolean)attack.invoke(null,zombie,l,at,l.getBlockState(at),7)&&l.getBlockState(at).getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE)==6,"Glass barricade takes zombie damage after 30 ticks");
        lower=l.getBlockState(at).setValue(GlassDefenseDoorBlock.BREACHED,true).setValue(DefenseDoorSystem.DefenseDoorBlock.STAGE,5);l.setBlock(at,lower,3);l.setBlock(at.above(),lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);zombie.getPersistentData().putLong(key,l.getGameTime()-30);check(!(boolean)attack.invoke(null,zombie,l,at,l.getBlockState(at),5),"Wooden barricade stage keeps the original damage rate");zombie.getPersistentData().putLong(key,l.getGameTime()-60);check((boolean)attack.invoke(null,zombie,l,at,l.getBlockState(at),5),"Wooden barricade takes zombie damage after 60 ticks");l.removeBlock(at,false);l.removeBlock(at.above(),false);
    }catch(Exception e){throw new RuntimeException(e);}}
    void verifyFlags(net.minecraft.server.level.ServerPlayer p){
        var level=p.serverLevel();var at=pos.west(5);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        for(String id:FurnitureSystem.MODELS){if(!id.startsWith("flag_")&&!id.startsWith("wall_standard_"))continue;
            var block=(LargeFurnitureBlock)FurnitureSystem.ALL.get(id).get();var stack=new net.minecraft.world.item.ItemStack(block);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
            for(var face:Direction.Plane.HORIZONTAL){p.setYRot(face.getOpposite().toYRot());var context=new net.minecraft.world.item.context.BlockPlaceContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atLowerCornerOf(at),Direction.UP,at.below(),false));
                check(((net.minecraft.world.item.BlockItem)stack.getItem()).place(context).consumesAction(),id+" actual item placement succeeds "+face);
                var root=level.getBlockState(at);check(root.is(block)&&root.getValue(FurnitureSystem.FurnitureBlock.FACING)==face,id+" placement orientation "+face);
                for(int part=0;part<block.partCount;part++)check(level.getBlockState(at.offset(block.offset(part,face))).is(block),id+" reserves part "+part);
                var last=at.offset(block.offset(block.partCount-1,face));level.removeBlock(last,false);
                for(int part=0;part<block.partCount;part++)check(level.getBlockState(at.offset(block.offset(part,face))).isAir(),id+" removes complete prop part "+part);
            }
        }
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
    }
    void verifyFlagUVs(){
        var mc=Minecraft.getInstance();
        for(String id:FurnitureSystem.MODELS){if(!id.startsWith("flag_")&&!id.startsWith("wall_standard_"))continue;
            var block=(LargeFurnitureBlock)FurnitureSystem.ALL.get(id).get();
            for(var face:Direction.Plane.HORIZONTAL)for(int part=0;part<block.partCount;part++){
                var state=block.defaultBlockState().setValue(FurnitureSystem.FurnitureBlock.FACING,face).setValue(LargeFurnitureBlock.PART,part);
                var model=mc.getBlockRenderer().getBlockModel(state);
                for(var quad:model.getQuads(state,null,net.minecraft.util.RandomSource.create(1))){
                    if(!quad.getSprite().contents().name().getPath().startsWith("block/flags/"))continue;
                    var direction=quad.getDirection();if(!direction.getAxis().isHorizontal())continue;
                    var vertices=quad.getVertices();int stride=vertices.length/4;double[] horizontal=new double[4],height=new double[4],u=new double[4],v=new double[4];
                    for(int i=0;i<4;i++){double x=Float.intBitsToFloat(vertices[i*stride]),z=Float.intBitsToFloat(vertices[i*stride+2]);height[i]=Float.intBitsToFloat(vertices[i*stride+1]);horizontal[i]=switch(direction){case NORTH->-x;case SOUTH->x;case EAST->-z;default->z;};u[i]=Float.intBitsToFloat(vertices[i*stride+4]);v[i]=Float.intBitsToFloat(vertices[i*stride+5]);}
                    double width=java.util.Arrays.stream(horizontal).max().orElseThrow()-java.util.Arrays.stream(horizontal).min().orElseThrow();if(width<.08)continue;
                    double hMean=java.util.Arrays.stream(horizontal).average().orElseThrow(),yMean=java.util.Arrays.stream(height).average().orElseThrow(),uvMean=java.util.Arrays.stream(u).average().orElseThrow(),vMean=java.util.Arrays.stream(v).average().orElseThrow(),du=0,dv=0;
                    for(int i=0;i<4;i++){du+=(horizontal[i]-hMean)*(u[i]-uvMean);dv+=(height[i]-yMean)*(v[i]-vMean);}
                    check(du>0,id+" motif reads left to right: "+face+" part "+part+" side "+direction);
                    check(dv<0,id+" motif remains upright: "+face+" part "+part+" side "+direction);
                }
            }
        }
    }
    void tick(TickEvent.ClientTickEvent event){if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;age++;
        if(age==25)server(p->{var l=p.serverLevel();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);l.setDayTime(6000);MapAtmosphere.get(l).configure(l,MapAtmosphere.defaults());for(var at:BlockPos.betweenClosed(-8,64,8,8,72,23))l.setBlock(at,at.getY()==64?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);p.teleportTo(l,2.5,66.5,18,140,20);});
        if(age==40&&System.getenv("ZR_FLAG_UV_CHECKS")!=null)verifyFlagUVs();
        if(age==35&&filtered&&System.getenv("ZR_FLAG_CHECKS")!=null)server(this::verifyFlags);
        if(age==35&&!filtered)server(p->{verifyConnectionsAndStacking(p);verifyExtendedAndGroups(p);});
        for(int i=0;i<cases.size();i++){int start=50+i*step;var c=cases.get(i);
            if(age==start)server(p->{var l=p.serverLevel();l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                if(c.state.equals("furniture")){
                    p.teleportTo(l,2.5,66.5,18,140,20);var block=(FurnitureSystem.FurnitureBlock)FurnitureSystem.ALL.get(c.id).get();
                    for(var facing:Direction.Plane.HORIZONTAL){var state=block.defaultBlockState().setValue(FurnitureSystem.FurnitureBlock.FACING,facing);check(!state.getCollisionShape(l,pos).isEmpty(),c.id+" collision rotates to "+facing);var mount=block.weaponMount(state,facing);var shape=state.getShape(l,pos);var from=mount.add(facing.getStepX()*.2,0,facing.getStepZ()*.2);var to=mount.add(-facing.getStepX()*.2,0,-facing.getStepZ()*.2);check(shape.clip(from,to,BlockPos.ZERO)!=null,c.id+" weapon mount touches a real face in "+facing);}
                    l.setBlock(pos,ObstacleDoorSystem.BLOCK.get().defaultBlockState(),3);var weapon=(ObstacleDoorSystem.ObstacleDoorBlockEntity)l.getBlockEntity(pos);weapon.setMimic(block.defaultBlockState().setValue(FurnitureSystem.FurnitureBlock.FACING,Direction.SOUTH));weapon.setPrix(500);weapon.setCanal("23");
                    var copy=new ObstacleDoorSystem.ObstacleDoorBlockEntity(pos,weapon.getBlockState());copy.load(weapon.saveWithoutMetadata());check(copy.getMimic().equals(weapon.getMimic())&&copy.getPrix()==500&&copy.getCanal().equals("23"),c.id+" buyable door retains furniture, price and channel on reload");
                    check(!l.getBlockState(pos).getCollisionShape(l,pos).isEmpty(),c.id+" buyable door inherits furniture collision");
                    if(c.id.startsWith("flag_")||c.id.startsWith("wall_standard_")){
                        l.removeBlock(pos,false);var actual=block.defaultBlockState().setValue(FurnitureSystem.FurnitureBlock.FACING,Direction.SOUTH);l.setBlock(pos,actual,3);block.setPlacedBy(l,pos,actual,p,new net.minecraft.world.item.ItemStack(block));
                        p.teleportTo(l,2.5,66.8,18,140,0);
                    }
                }else{
                    p.teleportTo(l,8,70,24,140,18);l.setBlock(pos,MapDeviceSystem.ALL_TYPES.get("decor_"+c.id).get().defaultBlockState(),3);var d=(MapDeviceSystem.Device)l.getBlockEntity(pos);var config=d.config();config.putBoolean("requires_power",false);config.getCompound("scenery").putString("variant",c.state);d.configure(config);d.setActive(true);VehicleDecorPhysics.invalidate(l);
                    check(d.scenery.getString("vehicle").equals(c.id)&&!VehicleDecorPhysics.geometry(l,pos).boxes().isEmpty(),c+" has the correct family and solid collision");var copy=new MapDeviceSystem.Device(pos,d.getBlockState());copy.load(d.saveWithoutMetadata());check(copy.scenery.equals(d.scenery),c+" survives save/reload");
                }
            });
            if(age==start+(filtered?60:10)){boolean furniture=c.state.equals("furniture");var item=furniture?FurnitureSystem.ALL.get(c.id).get():MapDeviceSystem.ALL_TYPES.get("decor_"+c.id).get();var stack=new net.minecraft.world.item.ItemStack(item);check(mc.getItemRenderer().getModel(stack,mc.level,null,0)!=mc.getModelManager().getMissingModel(),c+" inventory model resolves");check(me.cryo.zombierool.init.CreativeCatalog.category(stack)==(furniture?me.cryo.zombierool.init.CreativeCatalog.Category.DECORATION:me.cryo.zombierool.init.CreativeCatalog.Category.AMBIENCE),c+" has its correct creative category");shot((furniture?"furniture-":"transport-")+c.id+"-"+c.state);}
        }
        if(age==55+cases.size()*step)server(p->{
            var l=p.serverLevel();p.getInventory().clearContent();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.teleportTo(l,.5,65,17,180,0);l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);l.setBlock(pos,ObstacleDoorSystem.BLOCK.get().defaultBlockState(),3);var door=(ObstacleDoorSystem.ObstacleDoorBlockEntity)l.getBlockEntity(pos);door.setMimic(FurnitureSystem.ALL.get("dumpster").get().defaultBlockState());door.setPrix(500);door.setCanal("23");p.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap->cap.setPoints(1000));
            me.cryo.zombierool.handlers.ObstaclePurchaseHandler.tryPurchase(p,pos);
            check(me.cryo.zombierool.gameplay.PointManager.getScore(p)==500&&l.getBlockState(pos).is(me.cryo.zombierool.init.ZombieroolModBlocks.PATH.get()),"Buying a furniture Buyable Door charges 500 and removes its visual and collision");
        });
        if(age==65+cases.size()*step){check(org.lwjgl.opengl.GL11.glGetError()==0,"Furniture mimics and transport states render without OpenGL errors");done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" furniture and transport checks");mc.stop();}
    }catch(Throwable e){done=true;e.printStackTrace();System.out.println("ATMOSPHERE VISUAL FAIL: "+e);mc.stop();}}
}
