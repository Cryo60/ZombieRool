package local.atmospherevisual;
import me.cryo.zombierool.client.ThirdPersonAnimHandler;
import me.cryo.zombierool.core.registry.ZRRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
@Mod("atmospherevisual")
public final class HandActionRevisionTest {
 boolean requested,done;int age,checks;
 public HandActionRevisionTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
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
  if(age==30){mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);p.getInventory().clearContent();});mc.options.hideGui=false;mc.player.setXRot(0);for(var item:new net.minecraft.world.item.Item[]{ZRRegistry.ANIM_KNIFE,ZRRegistry.BOWIE_KNIFE}){var model=mc.getItemRenderer().getModel(new ItemStack(item),mc.level,mc.player,0);check(model!=mc.getModelManager().getMissingModel(),"Knife mesh bakes successfully: "+item);var faces=model.getQuads(null,null,net.minecraft.util.RandomSource.create());check(!faces.isEmpty(),"Knife has visible geometry: "+item);check(!model.getParticleIcon().contents().name().getPath().contains("missing"),"Knife diffuse texture resolves: "+item);}mc.setScreen(new Props());}
  if(age==40){shot("knife-models");mc.setScreen(null);var state=me.cryo.zombierool.block.system.TeleporterSystem.PLATFORM.get().defaultBlockState().setValue(me.cryo.zombierool.block.system.TeleporterSystem.Platform.PART,4);var model=mc.getBlockRenderer().getBlockModel(state);var quads=model.getQuads(state,null,net.minecraft.util.RandomSource.create());check(quads.stream().anyMatch(q->q.getDirection()!=net.minecraft.core.Direction.UP),"Platform has baked side faces");check(quads.stream().filter(q->q.getDirection()!=net.minecraft.core.Direction.UP).allMatch(q->q.getSprite().contents().name().getPath().equals("block/gray_concrete")),"Platform sides and underside never repeat the animated radar texture");}
  if(age==50)shot("hands-baseline-front");
  String[] names={"grenade_cook","grenade_throw","stielhandgranate_cook","stielhandgranate_throw","melee","melee"};
  for(int i=0;i<names.length;i++){int start=55+i*35;String name=names[i];
   if(age==start){mc.player.getPersistentData().putBoolean("zr_has_bowie_knife",i==5);ThirdPersonAnimHandler.startAnim(mc.player.getUUID(),name,100);check(ThirdPersonAnimHandler.isAnimationPlaying(mc.player.getUUID()),"Original clip starts: "+name);if(i==5)check("bowie_sweep".equals(ThirdPersonAnimHandler.currentAnim(mc.player.getUUID())),"Bowie selects its own slash animation");}
   if(age==start+2||age==start+5||age==start+10)shot(i+"-"+name+"-"+(age-start));
   if(age==start+26)ThirdPersonAnimHandler.reset();
  }
  if(age==275){mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);ThirdPersonAnimHandler.startAnim(mc.player.getUUID(),"melee",100);}
  if(age==280)shot("third-person-shoulders");
  if(age==295){ThirdPersonAnimHandler.reset();mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.player.setYRot(90);mc.player.setYHeadRot(90);}
  if(age==299)shot("hands-baseline-side");
  if(age==300){ThirdPersonAnimHandler.reset();mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.player.setYRot(90);mc.player.setYHeadRot(90);ThirdPersonAnimHandler.startAnim(mc.player.getUUID(),"grenade_cook",100);}
  if(age==310)shot("grenade-looking-sideways");
  if(age==330){ThirdPersonAnimHandler.reset();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var l=p.serverLevel();p.teleportTo(l,.5,65,28.5,0,0);for(var at:net.minecraft.core.BlockPos.betweenClosed(-1,65,31,1,68,31))l.setBlock(at,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);var pos=new net.minecraft.core.BlockPos(0,66,31);var sys=me.cryo.zombierool.block.system.BuyWallWeaponSystem.BLOCK.get();l.setBlock(pos,sys.defaultBlockState(),3);var wall=(me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponBlockEntity)l.getBlockEntity(pos);wall.setMimic(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());wall.setItem(0,new ItemStack(ZRRegistry.BOWIE_KNIFE));wall.setPrice(3000);});}
  if(age==365)shot("bowie-wall-silhouette");
  if(age==380){done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" hand action checks");mc.stop();}
 }catch(Throwable t){t.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}}
}
