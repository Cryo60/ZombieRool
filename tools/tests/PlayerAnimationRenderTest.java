package local.hotfixtest;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import me.cryo.zombierool.client.ThirdPersonAnimHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;

public final class PlayerAnimationRenderTest {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("ANIMATION CHECK: "+message);}
 public static void run() throws Exception {
  var mc=Minecraft.getInstance();var player=mc.player;
  check(player!=null,"Disposable client world provides a real local player");
  var bowie=new net.minecraft.world.item.ItemStack(me.cryo.zombierool.core.registry.ZRRegistry.BOWIE_KNIFE);
  var bowieModel=mc.getItemRenderer().getModel(bowie,mc.level,player,0);
  check(bowieModel.isGui3d()&&Math.abs(bowieModel.getTransforms().thirdPersonRightHand.rotation.x)<.001,"Bowie model presents the blade in the animated hand instead of rotating it edge-on");
  int keys=0;
  for(String file:new String[]{"drink_perk","grenade","knife","molotov","monkey_bomb","stielhandgranate"}){
   try(var source=mc.getResourceManager().open(new ResourceLocation("zombierool","animations/"+file+".json"));var runtime=mc.getResourceManager().open(new ResourceLocation("zombierool","player_animation/"+file+".json"))){
    var authored=AnimationSerializing.deserializeAnimation(source);var smoothed=AnimationSerializing.deserializeAnimation(runtime);
    check(authored.size()==smoothed.size(),file+" keeps its animations");
    for(int i=0;i<authored.size();i++){
     var old=authored.get(i);var updated=smoothed.get(i);
     check(old.beginTick==updated.beginTick&&old.endTick==updated.endTick&&old.stopTick==updated.stopTick,file+" keeps its timing");
     for(var entry:old.getBodyParts().entrySet()){
      var a=entry.getValue();var b=updated.getBodyParts().get(entry.getKey());
      for(var pair:new dev.kosmx.playerAnim.core.data.KeyframeAnimation.StateCollection.State[][]{{a.pitch,b.pitch},{a.yaw,b.yaw},{a.roll,b.roll}}){
       var before=pair[0].getKeyFrames();var after=pair[1].getKeyFrames();check(before.size()==after.size(),file+" "+entry.getKey()+" retains rotation keys");
       for(int k=0;k<before.size();k++){var original=before.get(k);var next=after.get(k);if(original.tick!=next.tick||Math.abs(original.value-next.value)>.00001)throw new AssertionError("Authored pose changed");if(next.ease==dev.kosmx.playerAnim.core.util.Ease.CONSTANT)throw new AssertionError("Stepped rotation remains");keys++;}
      }
     }
    }
   }
  }
  check(keys>100,"Authored rotations keep their poses and times, with interpolated playback ("+keys+" axis keys)");
  var data=PlayerAnimationRegistry.getAnimation(new ResourceLocation("zombierool","drink_perk"));check(data!=null,"Real resource loader registers the smoothed animation");
  var expected=new KeyframeAnimationPlayer(data);for(int i=0;i<5;i++)expected.tick();
  expected.setupAnim(.1f);float early=expected.get3DTransform("head",TransformType.ROTATION,.1f,Vec3f.ZERO).getX();
  expected.setupAnim(.7f);float late=expected.get3DTransform("head",TransformType.ROTATION,.7f,Vec3f.ZERO).getX();
  check(Math.abs(early-late)>.0001,"The head pose changes within a game tick, rather than only twenty times per second");
  ThirdPersonAnimHandler.startAnim(player.getUUID(),"drink_perk",0);
  var layer=(ModifierLayer<?>)PlayerAnimationAccess.getPlayerAssociatedData(player).get(ThirdPersonAnimHandler.LAYER_ID);
  var playback=(KeyframeAnimationPlayer)layer.getAnimation();for(int i=0;i<5;i++)playback.tick();playback.setupAnim(.9f);
  var camera=new net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles(mc.gameRenderer,mc.gameRenderer.getMainCamera(),.1,0,0,0);
  ThirdPersonAnimHandler.onCameraSetup(camera);
  check(Math.abs(camera.getPitch()-Math.toDegrees(early))<.001,"Camera reads this frame's fractional tick and converts radians to degrees");
  var renderer=(PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(player);
  var consumer=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(PlayerAnimationRenderTest.class.getClassLoader(),new Class[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(p,m,args)->m.getReturnType()==com.mojang.blaze3d.vertex.VertexConsumer.class?p:null);
  net.minecraft.client.renderer.MultiBufferSource buffer=type->consumer;
  mc.setCameraEntity(player);
  mc.gameRenderer.getMainCamera().setup(mc.level,player,false,false,.1f);
  mc.getEntityRenderDispatcher().prepare(mc.level,mc.gameRenderer.getMainCamera(),player);
  FirstPersonMode.setFirstPersonPass(true);
  try{
   renderer.render(player,0,.1f,new com.mojang.blaze3d.vertex.PoseStack(),buffer,15728880);
   var model=renderer.getModel();
   check(!model.body.visible&&!model.leftLeg.visible&&!model.rightLeg.visible,"Real PlayerRenderer hides torso and legs during the first-person animation");
   check(!model.head.visible&&!model.hat.visible,"First-person animated body keeps the head and hat hidden");
   check(model.leftArm.visible&&model.rightArm.visible,"Both animated arms remain visible");
   check(!model.jacket.visible&&!model.leftPants.visible&&!model.rightPants.visible,"Torso and leg outer layers remain hidden");
  }finally{FirstPersonMode.setFirstPersonPass(false);playback.stop();}
  renderer.render(player,0,.1f,new com.mojang.blaze3d.vertex.PoseStack(),buffer,15728880);
  check(renderer.getModel().head.visible&&renderer.getModel().body.visible,"Ordinary rendering restores the complete model after the animation");
  boolean oldBowie=player.getPersistentData().getBoolean("zr_has_bowie_knife");
  player.getPersistentData().putBoolean("zr_has_bowie_knife",true);ThirdPersonAnimHandler.startAnim(player.getUUID(),"melee",0);
  var slash=(KeyframeAnimationPlayer)layer.getAnimation();for(int i=0;i<5;i++)slash.tick();
  int[] bladeVertices={0};
  var bladeConsumer=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(PlayerAnimationRenderTest.class.getClassLoader(),new Class[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(p,m,args)->{if(m.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(p,m,args);if(m.getName().equals("endVertex")||m.getName().equals("vertex")&&m.getReturnType()==void.class)bladeVertices[0]++;return m.getReturnType()==com.mojang.blaze3d.vertex.VertexConsumer.class?p:null;});
  net.minecraft.client.renderer.MultiBufferSource knifeBuffer=type->type.toString().contains("textures/atlas/blocks.png")?bladeConsumer:consumer;
  FirstPersonMode.setFirstPersonPass(true);
  try{renderer.render(player,0,.5f,new com.mojang.blaze3d.vertex.PoseStack(),knifeBuffer,15728880);check(bladeVertices[0]>40,"Actual first-person slash submits the purchased Bowie blade geometry through the animated hand layer");}
  finally{FirstPersonMode.setFirstPersonPass(false);slash.stop();player.getPersistentData().putBoolean("zr_has_bowie_knife",oldBowie);}
  drinkSounds(mc);
  System.out.println("ANIMATION RENDER PASS");
 }

 private static void drinkSounds(Minecraft mc)throws Exception{
  var player=mc.player;
  ThirdPersonAnimHandler.startAnim(player.getUUID(),"drink_perk",70);
  var layer=(ModifierLayer<?>)PlayerAnimationAccess.getPlayerAssociatedData(player).get(ThirdPersonAnimHandler.LAYER_ID);
  var playback=(KeyframeAnimationPlayer)layer.getAnimation();
  var bottle=me.cryo.zombierool.client.DrinkPerkAnimationHandler.class.getDeclaredField("bottleSound");bottle.setAccessible(true);
  var burp=me.cryo.zombierool.client.DrinkPerkAnimationHandler.class.getDeclaredField("burpSound");burp.setAccessible(true);
  int[] finished={0};me.cryo.zombierool.client.DrinkPerkAnimationHandler.reset();
  me.cryo.zombierool.client.DrinkPerkAnimationHandler.startAnimation(()->finished[0]++);
  var event=new net.minecraftforge.event.TickEvent.ClientTickEvent(net.minecraftforge.event.TickEvent.Phase.END);
  for(int i=0;i<80;i++)me.cryo.zombierool.client.DrinkPerkAnimationHandler.onTick(event);
  check(bottle.get(null)==null&&burp.get(null)==null&&finished[0]==0,"Drink sounds and completion cannot advance ahead of paused or stalled animation playback");
  for(int i=0;i<7;i++)playback.tick();me.cryo.zombierool.client.DrinkPerkAnimationHandler.onTick(event);
  check(bottle.get(null)==null,"Bottle sound waits for the cap-opening gesture");
  playback.tick();
  var pause=Minecraft.class.getDeclaredField("pause");pause.setAccessible(true);boolean previous=mc.isPaused();
  try{pause.setBoolean(mc,true);me.cryo.zombierool.client.DrinkPerkAnimationHandler.onTick(event);check(bottle.get(null)==null,"Paused single-player does not emit the next perk-drink cue");}finally{pause.setBoolean(mc,previous);}
  me.cryo.zombierool.client.DrinkPerkAnimationHandler.onTick(event);
  var sound=(net.minecraft.client.resources.sounds.SoundInstance)bottle.get(null);
  check(sound!=null&&sound.getLocation().equals(new ResourceLocation("zombierool","perks_glook")),"Opening and drinking audio starts at the actual cap-opening animation tick");
  me.cryo.zombierool.client.DrinkPerkAnimationHandler.onTick(event);check(bottle.get(null)==sound,"Repeated client ticks cannot replay the same bottle sound");
  while(playback.getTick()<61)playback.tick();me.cryo.zombierool.client.DrinkPerkAnimationHandler.onTick(event);
  check(burp.get(null)==null,"Burp cannot overlap the opening and drinking audio");
  playback.tick();me.cryo.zombierool.client.DrinkPerkAnimationHandler.onTick(event);
  check(burp.get(null) instanceof net.minecraft.client.resources.sounds.SoundInstance s&&s.getLocation().equals(new ResourceLocation("zombierool","perks_rot")),"Burp starts once the bottle is lowered at the authored cue");
  while(playback.getTick()<70)playback.tick();me.cryo.zombierool.client.DrinkPerkAnimationHandler.onTick(event);
  check(finished[0]==1,"Drink completion follows the same seventy-tick sequence as perk activation");
  me.cryo.zombierool.client.DrinkPerkAnimationHandler.reset();playback.stop();
  check(bottle.get(null)==null&&burp.get(null)==null,"Reset clears drink sounds and pending cues");
 }
}
