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
public final class FurnitureTransportVisualTest {
    boolean requested,done;int age,checks;volatile String failure;final BlockPos pos=new BlockPos(0,65,15);
    record Case(String id,String state){}final java.util.List<Case> cases=new java.util.ArrayList<>();
    public FurnitureTransportVisualTest(){for(String id:FurnitureSystem.MODELS)cases.add(new Case(id,"furniture"));for(String id:AmbientScenery.transports())for(String state:AmbientScenery.states(id))cases.add(new Case(id,state));MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+text);}
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable e){failure=e.toString();e.printStackTrace();}});}
    void shot(String name)throws Exception{var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),name+".png"));}}
    void tick(TickEvent.ClientTickEvent event){if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;age++;
        if(age==25)server(p->{var l=p.serverLevel();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);l.setDayTime(6000);MapAtmosphere.get(l).configure(l,MapAtmosphere.defaults());for(var at:BlockPos.betweenClosed(-8,64,8,8,72,23))l.setBlock(at,at.getY()==64?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);p.teleportTo(l,2.5,66.5,18,140,20);});
        for(int i=0;i<cases.size();i++){int start=50+i*14;var c=cases.get(i);
            if(age==start)server(p->{var l=p.serverLevel();l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                if(c.state.equals("furniture")){
                    p.teleportTo(l,2.5,66.5,18,140,20);var block=(FurnitureSystem.FurnitureBlock)FurnitureSystem.ALL.get(c.id).get();
                    for(var facing:Direction.Plane.HORIZONTAL){var state=block.defaultBlockState().setValue(FurnitureSystem.FurnitureBlock.FACING,facing);check(!state.getCollisionShape(l,pos).isEmpty(),c.id+" collision rotates to "+facing);var mount=block.weaponMount(state,facing);var shape=state.getShape(l,pos);var from=mount.add(facing.getStepX()*.2,0,facing.getStepZ()*.2);var to=mount.add(-facing.getStepX()*.2,0,-facing.getStepZ()*.2);check(shape.clip(from,to,BlockPos.ZERO)!=null,c.id+" weapon mount touches a real face in "+facing);}
                    l.setBlock(pos,BuyWallWeaponSystem.BLOCK.get().defaultBlockState().setValue(BuyWallWeaponSystem.BuyWallWeaponBlock.FACING,Direction.SOUTH),3);var weapon=(BuyWallWeaponSystem.BuyWallWeaponBlockEntity)l.getBlockEntity(pos);weapon.setMimic(block.defaultBlockState().setValue(FurnitureSystem.FurnitureBlock.FACING,Direction.SOUTH));weapon.setPrice(500);weapon.setItemToSell(new net.minecraft.resources.ResourceLocation("zombierool:m1911"));
                    var copy=new BuyWallWeaponSystem.BuyWallWeaponBlockEntity(pos,weapon.getBlockState());copy.load(weapon.saveWithoutMetadata());check(copy.getMimic().equals(weapon.getMimic())&&copy.getPrice()==500&&!copy.getItem(0).isEmpty(),c.id+" wall weapon retains mimic, price and sale item on reload");
                    check(!l.getBlockState(pos).getCollisionShape(l,pos).isEmpty(),c.id+" wall weapon inherits furniture collision");
                }else{
                    p.teleportTo(l,8,70,24,140,18);l.setBlock(pos,MapDeviceSystem.ALL_TYPES.get("decor_"+c.id).get().defaultBlockState(),3);var d=(MapDeviceSystem.Device)l.getBlockEntity(pos);var config=d.config();config.putBoolean("requires_power",false);config.getCompound("scenery").putString("variant",c.state);d.configure(config);d.setActive(true);VehicleDecorPhysics.invalidate(l);
                    check(d.scenery.getString("vehicle").equals(c.id)&&!VehicleDecorPhysics.geometry(l,pos).boxes().isEmpty(),c+" has the correct family and solid collision");var copy=new MapDeviceSystem.Device(pos,d.getBlockState());copy.load(d.saveWithoutMetadata());check(copy.scenery.equals(d.scenery),c+" survives save/reload");
                }
            });
            if(age==start+10){boolean furniture=c.state.equals("furniture");var item=furniture?FurnitureSystem.ALL.get(c.id).get():MapDeviceSystem.ALL_TYPES.get("decor_"+c.id).get();var stack=new net.minecraft.world.item.ItemStack(item);check(mc.getItemRenderer().getModel(stack,mc.level,null,0)!=mc.getModelManager().getMissingModel(),c+" inventory model resolves");check(me.cryo.zombierool.init.CreativeCatalog.category(stack)==(furniture?me.cryo.zombierool.init.CreativeCatalog.Category.DECORATION:me.cryo.zombierool.init.CreativeCatalog.Category.AMBIENCE),c+" has its correct creative category");shot((furniture?"furniture-":"transport-")+c.id+"-"+c.state);}
        }
        if(age==55+cases.size()*14)server(p->{try{
            var l=p.serverLevel();p.getInventory().clearContent();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);l.setBlock(pos,BuyWallWeaponSystem.BLOCK.get().defaultBlockState(),3);var wall=(BuyWallWeaponSystem.BuyWallWeaponBlockEntity)l.getBlockEntity(pos);wall.setMimic(FurnitureSystem.ALL.get("tool_cabinet").get().defaultBlockState());wall.setPrice(500);wall.setItem(0,me.cryo.zombierool.core.system.WeaponFacade.createWeaponStack("m1911",false,p));p.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap->cap.setPoints(1000));
            var purchase=me.cryo.zombierool.handlers.ServerInteractionHandler.class.getDeclaredMethod("handleWallWeapon",net.minecraft.server.level.ServerPlayer.class,net.minecraft.server.level.ServerLevel.class,BlockPos.class);purchase.setAccessible(true);purchase.invoke(null,p,l,pos);
            check(me.cryo.zombierool.gameplay.PointManager.getScore(p)==500&&p.getInventory().items.stream().anyMatch(stack->me.cryo.zombierool.core.system.WeaponFacade.isWeapon(stack)),"Survival purchase from a furniture mimic grants the weapon and charges exactly 500 points");
        }catch(Exception e){throw new RuntimeException(e);}});
        if(age==65+cases.size()*14){check(org.lwjgl.opengl.GL11.glGetError()==0,"Furniture mimics and transport states render without OpenGL errors");done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" furniture and transport checks");mc.stop();}
    }catch(Throwable e){done=true;e.printStackTrace();System.out.println("ATMOSPHERE VISUAL FAIL: "+e);mc.stop();}}
}
