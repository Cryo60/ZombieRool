package local.hotfixtest;
import net.minecraft.client.Minecraft;
import me.cryo.zombierool.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import com.mojang.blaze3d.vertex.*;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.api.layered.*;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
public final class KnuckleClientTest {
 private static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);System.out.println("TACZ CLIENT HANDS CHECK: "+s);}
 public static void run()throws Exception{
  var mc=Minecraft.getInstance();var player=mc.player;var original=player.getMainHandItem();
  ThirdPersonAnimHandler.reset();
  check(mc.getSoundManager().getSoundEvent(new net.minecraft.resources.ResourceLocation("zombierool","knuckle_0"))!=null&&mc.getSoundManager().getSoundEvent(new net.minecraft.resources.ResourceLocation("zombierool","knuckle_1"))!=null,"Both original T6 cracking sounds load through the actual Minecraft resource manager");
  var gun=me.cryo.zombierool.core.system.WeaponFacade.createWeaponStack("ak47",false,player);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,gun);
  check(me.cryo.zombierool.core.system.WeaponFacade.isTaczWeapon(gun),"The render fixture holds a real TacZ AK47");
  var field=BrutusUnlockClient.class.getDeclaredField("requested");field.setAccessible(true);var pos=BlockPos.ZERO;field.set(null,pos);me.cryo.zombierool.init.KeyBindings.REPAIR_AND_PURCHASE_KEY.setDown(true);
  var n=new CompoundTag();n.putLong("position",pos.asLong());n.putInt("elapsed",15);n.putInt("duration",60);BrutusUnlockClient.receive(n);
  float a=BrutusUnlockClient.progress(.05f),b=BrutusUnlockClient.progress(.95f);
  check(b>a&&b-a<.02,"Unlock progress advances between rendered frames within one game tick");
  int[] vertices={0};var sink=(VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(KnuckleClientTest.class.getClassLoader(),new Class[]{VertexConsumer.class},(o,m,args)->{if(m.getName().equals("endVertex"))vertices[0]++;return m.getReturnType()==VertexConsumer.class?o:null;});net.minecraft.client.renderer.MultiBufferSource buffer=t->sink;
  var hand=new net.minecraftforge.client.event.RenderHandEvent(net.minecraft.world.InteractionHand.MAIN_HAND,new PoseStack(),buffer,15728880,0,0,0,0,gun);
  com.tacz.guns.client.event.FirstPersonRenderEvent.onRenderHand(hand);
  check(hand.isCanceled()&&vertices[0]==0,"TacZ's own renderer is canceled during unlocking and emits no gun geometry");
  ThirdPersonAnimHandler.startAnim(player.getUUID(),"knuckle_crack",60);
  var layer=(ModifierLayer<?>)PlayerAnimationAccess.getPlayerAssociatedData(player).get(ThirdPersonAnimHandler.LAYER_ID);var playback=(KeyframeAnimationPlayer)layer.getAnimation();System.out.println("HANDS BONES "+dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry.getAnimation(new net.minecraft.resources.ResourceLocation("zombierool","knuckle_crack")).getBodyParts().keySet());
  check(playback!=null&&ThirdPersonAnimHandler.isAnimationPlaying(player.getUUID()),"The original T6 motion loads as a Minecraft player animation");
  for(int tick:new int[]{0,10,17,27,33}){while(playback.getTick()<tick)playback.tick();capture(mc,playback,tick);}
  BrutusUnlockClient.cancel();me.cryo.zombierool.init.KeyBindings.REPAIR_AND_PURCHASE_KEY.setDown(false);
  check(!BrutusUnlockClient.hideHeldItems(),"Canceling unlock immediately restores the held gun");
  ThirdPersonAnimHandler.startAnim(player.getUUID(),"knuckle_crack",60);check(BrutusUnlockClient.hideHeldItems(),"Pack-a-Punch animation also suppresses held weapons without an unlock action");
  ThirdPersonAnimHandler.startAnim(player.getUUID(),"knuckle_cancel",0);check(!BrutusUnlockClient.hideHeldItems(),"Server animation cancellation restores normal rendering");
  player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,original);
 }
 private static boolean connectedHands(com.mojang.blaze3d.platform.NativeImage image){
  int w=image.getWidth(),h=image.getHeight(),background=image.getPixelRGBA(w/2,0);boolean[] visited=new boolean[w*h];var queue=new java.util.ArrayDeque<Integer>();
  if(image.getPixelRGBA(0,h-1)==background||image.getPixelRGBA(w-1,h-1)==background)return false;
  queue.add((h-1)*w);visited[(h-1)*w]=true;
  while(!queue.isEmpty()){int p=queue.removeFirst();if(p==h*w-1)return true;int x=p%w,y=p/w;
   for(int n:new int[]{x>0?p-1:-1,x<w-1?p+1:-1,y>0?p-w:-1,y<h-1?p+w:-1})if(n>=0&&!visited[n]&&image.getPixelRGBA(n%w,n/w)!=background){visited[n]=true;queue.add(n);}
  }return false;
 }
 private static void capture(Minecraft mc,KeyframeAnimationPlayer playback,int tick)throws Exception{
  var target=new com.mojang.blaze3d.pipeline.TextureTarget(640,360,true,Minecraft.ON_OSX);target.bindWrite(true);org.lwjgl.opengl.GL11.glClearColor(.07f,.09f,.08f,1);org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT|org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
  var projection=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());var sorting=com.mojang.blaze3d.systems.RenderSystem.getVertexSorting();
  com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().perspective((float)Math.toRadians(70),640f/360,.05f,10),VertexSorting.DISTANCE_TO_ORIGIN);
  var view=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();view.pushPose();view.setIdentity();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
  var player=mc.player;var renderer=(PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(player);float body=player.yBodyRot,old=player.yBodyRotO;player.yBodyRot=player.yBodyRotO=180;
  var pose=new PoseStack();pose.translate(0,-1.62,-.35);FirstPersonMode.setFirstPersonPass(true);playback.setupAnim(.5f);
  try{renderer.render(player,180,.5f,pose,mc.renderBuffers().bufferSource(),15728880);mc.renderBuffers().bufferSource().endBatch();var l=renderer.getModel().leftArm;var r=renderer.getModel().rightArm;System.out.println("HANDS POSE "+tick+" L="+l.x+","+l.y+","+l.z+":"+l.xRot+","+l.yRot+","+l.zRot+" R="+r.x+","+r.y+","+r.z+":"+r.xRot+","+r.yRot+","+r.zRot);check(renderer.getModel().leftArm.visible&&renderer.getModel().rightArm.visible,"Both skin arms render at animation tick "+tick);
   try(var image=new com.mojang.blaze3d.platform.NativeImage(640,360,true)){target.bindRead();image.downloadTexture(0,false);image.flipY();image.writeToFile(java.nio.file.Path.of("D:/Dev/ZombieRool/build/dev-26.7/knuckle-"+tick+".png"));if(tick==10)check(connectedHands(image),"The two voxel hands meet visibly in the knuckle-cracking pose");}
  }finally{FirstPersonMode.setFirstPersonPass(false);player.yBodyRot=body;player.yBodyRotO=old;view.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(projection,sorting);target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
 }
}
