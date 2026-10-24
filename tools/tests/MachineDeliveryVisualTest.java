package local.atmospherevisual;

import me.cryo.zombierool.block.system.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("atmospherevisual")
public final class MachineDeliveryVisualTest {
    boolean requested,done;int age,checks;volatile String failure;final BlockPos site=new BlockPos(100,80,100),source=MachineDeliverySystem.SKY_POS;
    public MachineDeliveryVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+text);}
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable t){failure=t.toString();t.printStackTrace();}});}
    MachineDeliverySystem.Delivery d(net.minecraft.world.level.Level l,BlockPos p){return l instanceof net.minecraft.server.level.ServerLevel server?MachineDeliverySystem.at(server,p):(MachineDeliverySystem.Delivery)l.getBlockEntity(p);}
    void shot(String name)throws Exception {var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output")+"/"+name+".png"));}}
    void click(String key){var mc=Minecraft.getInstance();var wanted=net.minecraft.network.chat.Component.translatable(key).getString();for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(wanted)){b.onPress();return;}throw new AssertionError("Missing button "+wanted);}
    int deliverySounds()throws Exception{var f=me.cryo.zombierool.client.MachineDeliveryEffects.class.getDeclaredField("FLIGHTS");f.setAccessible(true);return ((java.util.Map<?,?>)f.get(null)).size();}
    void tick(TickEvent.ClientTickEvent e){if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null)return;age++;
        if(age==30)server(p->{var l=p.serverLevel();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getInventory().clearContent();for(var q:BlockPos.betweenClosed(94,79,94,108,110,108))l.setBlock(q,q.getY()==79?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);p.teleportTo(l,100.5,80,106.5,180,0);l.setDayTime(6000);l.setBlock(site,MachineDeliverySystem.BLOCK.get().defaultBlockState().setValue(MachineDeliverySystem.DeliveryBlock.FACING,Direction.SOUTH),3);var d=d(l,site);var n=d.config();n.putString("role","controller");n.putString("mode","roll");n.putString("trigger","manual");n.putInt("duration",120);d.configure(n);});
        if(age==55)server(p->d(p.level(),site).trigger("juggernog",null));
        if(age==75){check(d(mc.level,site).phase.equals("animating"),"Client receives floating machine state");check(mc.getBlockRenderer().getBlockModel(PerksSystem.BLOCK.get().defaultBlockState())!=mc.getModelManager().getMissingModel(),"Roulette reuses the real perk machine model");shot("roulette-early");}
        if(age==110)shot("roulette-late");
        if(age==190){check(mc.level.getBlockEntity(site.above()) instanceof PerksSystem.PerksAColaBlockEntity,"Roulette settles into a synchronized usable perk machine");shot("roulette-landed");}
        if(age==195)server(p->{var l=p.serverLevel();var landing=d(l,site);landing.reset();var n=landing.config();n.putString("role","landing");landing.configure(n);var controller=MachineDeliverySystem.sky(l);n=controller.config();n.putBoolean("enabled",true);n.putString("mode","drop");n.putString("trigger","manual");n.putInt("duration",200);n.putDouble("dropHeight",18);var pool=new ListTag();pool.add(StringTag.valueOf("pack_a_punch"));n.put("pool",pool);controller.configure(n);controller.trigger("pack_a_punch",site);p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,new Vec3(100.5,96,100.5));});
        if(age==220){check(d(mc.level,site).falling&&d(mc.level,site).selected.equals("pack_a_punch"),"Sky delivery syncs a fixed Pack-a-Punch selection");check(deliverySounds()==1,"One positional audio set follows the falling machine");shot("pack-falling-high");}
        if(age==345)server(p->p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,new Vec3(100.5,87,100.5)));
        if(age==360)shot("pack-falling-low");
        if(age==405)server(p->p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,new Vec3(100.5,81,100.5)));
        if(age==415){check(mc.level.getBlockEntity(site) instanceof PackAPunchSystem.PackAPunchBlockEntity,"Sky delivery lands as a synchronized Pack-a-Punch");check(deliverySounds()==0,"Delivery sounds stop when the machine lands");shot("pack-landed");}
        if(age==420)server(p->MachineDeliveryPackets.open(p,d(p.level(),source)));
        if(age==435){check(mc.screen instanceof me.cryo.zombierool.client.gui.MachineDeliveryScreen,"Creative configuration opens through server packet");shot("menu-setup");click("gui.zombierool.delivery.machines");}
        if(age==440){shot("menu-machines");click("gui.zombierool.delivery.animation");}
        if(age==445){shot("menu-animation");var sliders=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.AbstractSliderButton).count();check(sliders==2,"Animation values are editable with two dedicated sky sliders");var model=mc.getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(MachineDeliverySystem.ITEM.get()),mc.level,mc.player,0);check(model!=mc.getModelManager().getMissingModel(),"Delivery marker has a visible inventory model");mc.setScreen(null);}
        if(age==450){done=true;System.out.println("ATMOSPHERE VISUAL PASS: machine deliveries "+checks+" checks");mc.stop();}
    }catch(Throwable t){t.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}}
}
