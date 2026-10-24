package local.atmospherevisual;
import me.cryo.zombierool.client.ThirdPersonAnimHandler;
import me.cryo.zombierool.core.registry.ZRRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
@Mod("atmospherevisual")
public final class HandPlacementGrid {
 boolean requested,done;int age,checks;
 public HandPlacementGrid(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
 void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+text);}
 void shot(String name)throws Exception{try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output")+"/"+name+".png"));}}
 static class Props extends net.minecraft.client.gui.screens.Screen {
  Props(){super(net.minecraft.network.chat.Component.literal("Knives"));}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xff333e4a);for(int i=0;i<2;i++){g.pose().pushPose();g.pose().translate(width/2-130+i*150,50,0);g.pose().scale(7,7,7);g.renderItem(new ItemStack(i==0?ZRRegistry.ANIM_KNIFE:ZRRegistry.BOWIE_KNIFE),0,0);g.pose().popPose();}}
 }
 void tick(TickEvent.ClientTickEvent e){if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
  if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
  if(mc.player==null||mc.level==null)return;age++;
  if(age==30){mc.options.hideGui=true;mc.player.setXRot(0);for(var item:new net.minecraft.world.item.Item[]{ZRRegistry.ANIM_KNIFE,ZRRegistry.BOWIE_KNIFE}){var model=mc.getItemRenderer().getModel(new ItemStack(item),mc.level,mc.player,0);check(model!=mc.getModelManager().getMissingModel(),"Knife mesh bakes successfully: "+item);var faces=model.getQuads(null,null,net.minecraft.util.RandomSource.create());check(!faces.isEmpty(),"Knife has visible geometry: "+item);check(!model.getParticleIcon().contents().name().getPath().contains("missing"),"Knife diffuse texture resolves: "+item);}mc.setScreen(new Props());}
  if(age==40){shot("knife-models");mc.setScreen(null);var state=me.cryo.zombierool.block.system.TeleporterSystem.PLATFORM.get().defaultBlockState().setValue(me.cryo.zombierool.block.system.TeleporterSystem.Platform.PART,4);var model=mc.getBlockRenderer().getBlockModel(state);var quads=model.getQuads(state,null,net.minecraft.util.RandomSource.create());check(quads.stream().anyMatch(q->q.getDirection()!=net.minecraft.core.Direction.UP),"Platform has baked side faces");check(quads.stream().filter(q->q.getDirection()!=net.minecraft.core.Direction.UP).allMatch(q->q.getSprite().contents().name().getPath().equals("block/gray_concrete")),"Platform sides and underside never repeat the animated radar texture");}
  String[] names=new String[18];for(int i=0;i<18;i++)names[i]=i<6?"grenade_cook":i<12?"stielhandgranate_cook":"melee";int[][] offsets={{0,-6},{0,-12},{0,-18},{0,0},{-3,-12},{3,-12}};
  for(int i=0;i<names.length;i++){int start=55+i*15;String name=names[i];
   if(age==start){var f=ThirdPersonAnimHandler.class.getDeclaredField("handViewOffset");f.setAccessible(true);f.set(null,new dev.kosmx.playerAnim.core.util.Vec3f(0,offsets[i%6][0],offsets[i%6][1]));mc.player.getPersistentData().putBoolean("zr_has_bowie_knife",false);ThirdPersonAnimHandler.startAnim(mc.player.getUUID(),name,100);check(ThirdPersonAnimHandler.isAnimationPlaying(mc.player.getUUID()),"Original clip starts: "+name);}
   if(age==start+2||age==start+5||age==start+10)shot(i+"-"+name+"-"+(age-start));
   if(age==start+14)ThirdPersonAnimHandler.reset();
  }
  if(age==335){done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" hand action checks");mc.stop();}
 }catch(Throwable t){t.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}}
}
