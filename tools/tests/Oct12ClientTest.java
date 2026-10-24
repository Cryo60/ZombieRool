package local.hotfixtest;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import me.cryo.zombierool.client.*;
import me.cryo.zombierool.client.screens.TeleporterChargeOverlay;
import org.lwjgl.opengl.GL11;
public final class Oct12ClientTest {
 private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("TACZ CLIENT CHECK: "+text);}
 private static int[] canvas(Minecraft mc,String name,java.util.function.Consumer<GuiGraphics> draw)throws Exception{
  var target=new com.mojang.blaze3d.pipeline.TextureTarget(640,360,true,Minecraft.ON_OSX);target.bindWrite(true);
  GL11.glClearColor(.07f,.09f,.08f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
  var projection=new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
  RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,640,360,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
  var view=RenderSystem.getModelViewStack();view.pushPose();view.setIdentity();view.translate(0,0,-10000);RenderSystem.applyModelViewMatrix();
  var gui=new GuiGraphics(mc,mc.renderBuffers().bufferSource());draw.accept(gui);gui.flush();mc.renderBuffers().bufferSource().endBatch();
  int[] pixels;
  try(var image=new NativeImage(640,360,true)){target.bindRead();image.downloadTexture(0,false);image.flipY();image.writeToFile(java.nio.file.Path.of("D:/Dev/ZombieRool/build/dev-26.7/"+name+".png"));pixels=image.getPixelsRGBA();}
  finally{view.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
  check(GL11.glGetError()==GL11.GL_NO_ERROR,"No GL errors rendering "+name);return pixels;
 }
 public static void run()throws Exception{
  SpecialEnemyClientTest.run();
  var mc=Minecraft.getInstance();
  var coordinates=new java.util.ArrayList<float[]>();
  var recording=(VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(Oct12ClientTest.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,args)->{
   if(method.getName().equals("uv"))coordinates.add(new float[]{(float)args[0],(float)args[1]});
   return method.getReturnType()==VertexConsumer.class?proxy:null;
  });
  var projectedPose=new PoseStack();projectedPose.translate(7,9,-12);projectedPose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(55));
  var surface=new TacZCamoOverlay.SurfaceConsumer(recording,projectedPose.last());
  for(float x:new float[]{.25f,.75f})surface.vertex(projectedPose.last().pose(),x,.5f,0).color(255,255,255,255).uv(.1f,.1f).overlayCoords(0,0).uv2(15,15).normal(projectedPose.last().normal(),0,0,1).endVertex();
  check(Math.abs(coordinates.get(1)[0]-coordinates.get(0)[0])>.25,"Sub-pixel palette UVs produce a complete surface pattern independent of camera rotation");
  var bone=new com.tacz.guns.client.model.bedrock.BedrockPart("moving_magazine");
  bone.cubes.add(new com.tacz.guns.client.model.bedrock.BedrockCubeBox(0,0,1,2,3,3,4,5,0,false,32,32));
  var renderPart=TacZCamoOverlay.class.getDeclaredMethod("renderPart",com.tacz.guns.client.model.bedrock.BedrockPart.class,PoseStack.class,VertexConsumer.class,int.class,int.class);renderPart.setAccessible(true);
  coordinates.clear();renderPart.invoke(null,bone,projectedPose,recording,15728880,0);var rest=new java.util.ArrayList<>(coordinates);
  bone.offsetX=.7f;bone.offsetY=-.4f;bone.xRot=.8f;bone.yRot=1.7f;bone.zRot=.4f;
  coordinates.clear();renderPart.invoke(null,bone,projectedPose,recording,15728880,0);
  check(rest.size()==coordinates.size(),"Animated gun bone retains its full camo mesh");
  for(int i=0;i<rest.size();i++)if(Math.abs(rest.get(i)[0]-coordinates.get(i)[0])>.0001||Math.abs(rest.get(i)[1]-coordinates.get(i)[1])>.0001)throw new AssertionError("Camo slides across an animated bone");
  check(true,"Static camo UVs remain identical while a bone translates and rotates during reload");
  var aPiece=new com.tacz.guns.client.model.bedrock.BedrockPart("left_piece");aPiece.x=2;
  aPiece.cubes.add(new com.tacz.guns.client.model.bedrock.BedrockCubeBox(0,0,0,0,0,3,4,5,0,false,32,32));
  var bPiece=new com.tacz.guns.client.model.bedrock.BedrockPart("right_piece");
  bPiece.cubes.add(new com.tacz.guns.client.model.bedrock.BedrockCubeBox(0,0,2,0,0,3,4,5,0,false,32,32));
  coordinates.clear();renderPart.invoke(null,aPiece,projectedPose,recording,15728880,0);var joined=new java.util.ArrayList<>(coordinates);
  coordinates.clear();renderPart.invoke(null,bPiece,projectedPose,recording,15728880,0);
  for(int i=0;i<joined.size();i++)if(Math.abs(joined.get(i)[0]-coordinates.get(i)[0])>.0001||Math.abs(joined.get(i)[1]-coordinates.get(i)[1])>.0001)throw new AssertionError("Camo resets at a bone boundary");
  check(true,"Adjacent pieces share the same model-space pattern without resetting at their bone pivots");
  var hand=new com.tacz.guns.client.model.FunctionalBedrockPart(part->(pose,consumer,context,light,overlay)->{throw new AssertionError("Camo repeated a hand renderer");},bone);
  coordinates.clear();renderPart.invoke(null,hand,projectedPose,recording,15728880,0);
  check(coordinates.isEmpty(),"Bone-local camo does not recolor or duplicate the player hands or attachment renderers");
  var clientOnly=new ResourceLocation("testpack:client_only_career_gun");
  var index=com.tacz.guns.api.TimelessAPI.getAllClientGunIndex().iterator().next().getValue();
  com.tacz.guns.client.resource.ClientIndexManager.GUN_INDEX.put(clientOnly,index);
  try{check(me.cryo.zombierool.client.gui.CareerScreen.getSupportedWeaponsCache().contains(clientOnly.toString()),"Career lists an unmapped client-pack gun even when absent from the common server index");}
  finally{com.tacz.guns.client.resource.ClientIndexManager.GUN_INDEX.remove(clientOnly);}
  var profile=me.cryo.zombierool.client.career.LocalCareerManager.getData();
  var oldCamos=new java.util.HashMap<>(profile.equippedCamos);var oldSkins=new java.util.HashMap<>(profile.equippedSkins);
  var oldGlobal=new java.util.ArrayList<>(profile.globalUnlockedCamos);var oldUnlocks=new java.util.HashMap<>(profile.weaponUnlockedCamos);
  String single=me.cryo.zombierool.client.career.CareerUnlockables.CAMOS.entrySet().stream().filter(e->e.getValue().exclusiveWeapons.isEmpty()).findFirst().orElseThrow().getKey();
  try{
   profile.globalUnlockedCamos.clear();profile.weaponUnlockedCamos.clear();profile.weaponUnlockedCamos.put("m1911",new java.util.concurrent.CopyOnWriteArrayList<>(java.util.List.of(single)));
   profile.equippedCamos.put("ak47","camo_gold");profile.equippedSkins.put("olympia","keep_existing_skin");
   me.cryo.zombierool.client.career.LocalCareerManager.equipCamoOnAll(single);
   check(single.equals(profile.equippedCamos.get("m1911")),"Equip on all applies a camo to the weapon that owns it");
   check("camo_gold".equals(profile.equippedCamos.get("ak47"))&&"keep_existing_skin".equals(profile.equippedSkins.get("olympia")),"Equip on all preserves other weapons' camos and skins when they lack the selected unlock");
  }finally{profile.equippedCamos.clear();profile.equippedCamos.putAll(oldCamos);profile.equippedSkins.clear();profile.equippedSkins.putAll(oldSkins);profile.globalUnlockedCamos.clear();profile.globalUnlockedCamos.addAll(oldGlobal);profile.weaponUnlockedCamos.clear();profile.weaponUnlockedCamos.putAll(oldUnlocks);me.cryo.zombierool.client.career.LocalCareerManager.forceSave();}
  // Check precisely the gun models reported by the player, through their real registered item renderer.
  var reloadProbe=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:modern_kinetic_gun")));
  reloadProbe.getOrCreateTag().putString("GunId","tacz:m4a1");var reloadGun=com.tacz.guns.api.item.IGun.getIGunOrNull(reloadProbe);
  reloadGun.setCurrentAmmoCount(reloadProbe,0);reloadGun.setBulletInBarrel(reloadProbe,false);me.cryo.zombierool.core.system.WeaponFacade.setReserve(reloadProbe,40);
  check(TacZAutoReload.shouldReload(reloadProbe),"Empty TacZ gun with reserve requests the regular automatic reload");
  reloadGun.setBulletInBarrel(reloadProbe,true);check(!TacZAutoReload.shouldReload(reloadProbe),"Automatic reload waits until the chambered last bullet has been fired");
  reloadGun.setBulletInBarrel(reloadProbe,false);me.cryo.zombierool.core.system.WeaponFacade.setReserve(reloadProbe,0);check(!TacZAutoReload.shouldReload(reloadProbe),"No reserve cannot cause a repeated automatic reload");
  for(String id:new String[]{"m4a1","m1014","hk_mp5a5","g36k"}){
   var stack=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:modern_kinetic_gun")));
   stack.getOrCreateTag().putString("GunId","tacz:"+id);
   var display=com.tacz.guns.api.TimelessAPI.getGunDisplay(stack).orElseThrow();var base=display.getModelTexture();
   var plain=canvas(mc,"tacz-"+id+"-plain",gui->weapon(mc,stack,gui));
   stack.getOrCreateTag().putString("zr_camo","camo_bubbles");
   stack.getOrCreateTag().putBoolean(me.cryo.zombierool.ZombieroolKeys.PAP,true);
   check(!stack.getItem().isFoil(stack),"Pack-a-Punch glint does not hide the selected TacZ camo");
   int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);int unit=GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
   var painted=TacZCamoOverlay.pattern(stack);check(painted!=null,"Bubbles load on reported TacZ gun "+id);
   var texture=(net.minecraft.client.renderer.texture.DynamicTexture)mc.getTextureManager().getTexture(painted);
   texture.getPixels().writeToFile(java.nio.file.Path.of("D:/Dev/ZombieRool/build/dev-26.7/tacz-"+id+"-surface-pattern.png"));
   int transparent=0,soft=0;for(int pixel:texture.getPixels().getPixelsRGBA()){int alpha= pixel>>>24;if(alpha==0)transparent++;else if(alpha<255)soft++;}
   check(transparent>0&&soft>0,"Projected bubbles preserve transparent gaps and soft edges over the original gun texture");
   check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==binding,"Painting does not replace the renderer texture binding");
   check(GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE)==unit,"Painting does not change the active GL texture unit");
   texture.bind();
   check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S)==GL11.GL_REPEAT&&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T)==GL11.GL_REPEAT,"Surface coordinates repeat bubbles instead of stretching the edge into a foil-like patch");
   com.mojang.blaze3d.platform.GlStateManager._bindTexture(binding);
   var camo=canvas(mc,"tacz-"+id+"-bubbles",gui->weapon(mc,stack,gui));
   int afterBinding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);texture.bind();
   check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER)==GL11.GL_LINEAR,"The actual gun render pass cannot reset animated bubbles to square nearest-neighbour pixels");
   com.mojang.blaze3d.platform.GlStateManager._bindTexture(afterBinding);
   int changed=0;for(int i=0;i<plain.length;i++)if(plain[i]!=camo[i])changed++;
   check(changed>150,"Actual TacZ "+id+" model visibly changes with bubbles: "+changed+" pixels");
   var held=mc.player.getMainHandItem();mc.player.getInventory().setItem(mc.player.getInventory().selected,stack);
   try{
    var stale=stack.copy();stale.getOrCreateTag().remove("zr_camo");
    check(TacZVisuals.firstPersonStack(stale,mc.player)==stack,"First-person rendering replaces stale animation NBT with the same held gun's live camo");
    var different=stale.copy();different.getOrCreateTag().putString("GunId","tacz:ai_awp");check(TacZVisuals.firstPersonStack(different,mc.player)==different,"Unequipping a different gun retains that gun's own visual data");
    stack.getOrCreateTag().remove("zr_camo");var fpPlain=canvas(mc,"tacz-"+id+"-first-person-plain",gui->firstPerson(mc,stack,gui));
    stack.getOrCreateTag().putString("zr_camo","camo_bubbles");var fpCamo=canvas(mc,"tacz-"+id+"-first-person-bubbles",gui->firstPerson(mc,stack,gui));
    int fpChanged=0;for(int i=0;i<fpPlain.length;i++)if(fpPlain[i]!=fpCamo[i])fpChanged++;check(fpChanged>150,"First-person TacZ "+id+" visibly renders bubbles: "+fpChanged+" pixels");
    stack.getOrCreateTag().remove("zr_camo");
    canvas(mc,"tacz-"+id+"-first-person-pap",gui->{firstPerson(mc,stack,gui);try{assertGlintFlushed(mc);}catch(Exception e){throw new RuntimeException(e);}});
   }finally{mc.player.getInventory().setItem(mc.player.getInventory().selected,held);}

  }
  var knight=me.cryo.zombierool.init.ZombieroolModEntities.WHITE_KNIGHT.get().create(mc.level);
  check(mc.getEntityRenderDispatcher().getRenderer(knight) instanceof me.cryo.zombierool.client.renderer.WhiteKnightRenderer,"Spectral knight retains its saved entity ID and has its new renderer");
  try(var input=mc.getResourceManager().open(new ResourceLocation("zombierool:textures/entities/spectral_knight.png"));var skin=NativeImage.read(input)){check(skin.getWidth()==128&&skin.getHeight()==128,"Spectral knight loads its original 128-pixel armor atlas");}
  check(me.cryo.zombierool.client.model.SpectralKnightModel.layer().bakeRoot().getAllParts().count()>=19,"Spectral mesh includes plate armor, closed helm and articulated cloak strips");
  canvas(mc,"spectral-knight-preview",gui->net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(gui,320,300,95,0,0,knight));
  var saved=mc.player.getInventory().save(new net.minecraft.nbt.ListTag());int selected=mc.player.getInventory().selected;
  try{
   mc.player.getInventory().clearContent();var weapon=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:modern_kinetic_gun")));weapon.getOrCreateTag().putString("GunId","tacz:m1014");me.cryo.zombierool.core.system.WeaponFacade.setAmmo(weapon,7);me.cryo.zombierool.core.system.WeaponFacade.setReserve(weapon,32);mc.player.getInventory().setItem(1,weapon);mc.player.getInventory().selected=1;
   mc.player.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap->{cap.setLethalType("zombierool:semtex");cap.setLethalCount(4);cap.setEquipmentType("zombierool:claymore");cap.setEquipmentCount(2);cap.setTacticalType("zombierool:flash");cap.setTacticalCount(2);});
   canvas(mc,"classic-hud-preview",gui->ClassicZombieHUD.render(gui,mc,mc.player,640,360));
   mc.player.getInventory().setItem(3,new ItemStack(me.cryo.zombierool.init.ZombieroolModItems.INGOT_SALE.get()));
   canvas(mc,"ingot-sale-preview",gui->ClassicZombieHUD.render(gui,mc,mc.player,640,360));
  }finally{mc.player.getInventory().load(saved);mc.player.getInventory().selected=selected;ClassicZombieHUD.reset();}
  TeleporterChargeOverlay.start(0);check(TeleporterChargeOverlay.active(),"Arrival starts wormhole and daze rather than a six-tick flash");
  var method=TeleporterChargeOverlay.class.getDeclaredMethod("tunnel",GuiGraphics.class,int.class,int.class,double.class);method.setAccessible(true);
  var tunnelPixels=canvas(mc,"teleport-tunnel-preview",gui->{try{method.invoke(null,gui,640,360,24d);}catch(Exception e){throw new RuntimeException(e);}});TeleporterChargeOverlay.reset();
  int colorful=0;for(int pixel:tunnelPixels)if((pixel&255)>40||((pixel>>>16)&255)>50)colorful++;check(colorful>10000,"Wormhole red and blue surfaces are visible, not culled or hidden behind the background");
  // Include the vanilla renderer's final mirror and model-origin translation.
  var crawler=me.cryo.zombierool.init.ZombieroolModEntities.CRAWLER.get().create(mc.level);
  var crawlerRenderer=(me.cryo.zombierool.client.renderer.CrawlerRenderer)mc.getEntityRenderDispatcher().getRenderer(crawler);
  var rotate=crawlerRenderer.getClass().getDeclaredMethod("setupRotations",crawler.getClass(),PoseStack.class,float.class,float.class,float.class);rotate.setAccessible(true);
  for(var face:net.minecraft.core.Direction.Plane.HORIZONTAL){
   var start=new net.minecraft.world.phys.Vec3(.5,8,.5);
   me.cryo.zombierool.gameplay.TraversalMotion.receive(crawler,new me.cryo.zombierool.block.system.TransportPackets.Jump(crawler.getId(),crawler.getUUID(),start,start.add(face.getStepX(),0,face.getStepZ()),start.add(0,-6,0),80,0,2));
   var pose=new PoseStack();rotate.invoke(crawlerRenderer,crawler,pose,0f,face.toYRot(),0f);pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
   var matrix=pose.last().pose();var bellyNormal=matrix.transformDirection(new org.joml.Vector3f(0,1,0));var headDirection=matrix.transformDirection(new org.joml.Vector3f(0,0,-1));
   var bellyPoint=matrix.transformPosition(new org.joml.Vector3f(0,19f/16,0));float toward=bellyPoint.x*face.getStepX()+bellyPoint.z*face.getStepZ();
   check(bellyNormal.x*face.getStepX()+bellyNormal.z*face.getStepZ()>.99&&headDirection.y<-.99,"Full model transform faces the belly into the "+face+" wall and head down");
   check(Math.abs(toward-.465)<.001,"Crawler belly stays 3.5 cm from the "+face+" wall instead of floating away");
   me.cryo.zombierool.gameplay.TraversalMotion.cancel(crawler);
  }
  var swing=knight.getClass().getDeclaredField("SPECTRAL_SWING");swing.setAccessible(true);var accessor=(net.minecraft.network.syncher.EntityDataAccessor<Integer>)swing.get(null);
  knight.getEntityData().set(accessor,18);knight.tickCount+=5;float before=knight.spectralSwing(.4f);knight.getEntityData().set(accessor,12);
  check(Math.abs(before-knight.spectralSwing(.4f))<.001,"Delayed knight animation packets cannot step the client animation backwards or forwards");
  check(Math.abs(knight.spectralSwing(.1f)-knight.spectralSwing(.9f)-.8f)<.001,"Knight animation advances at render-frame speed between ticks");
  System.out.println("OCT12 CLIENT PASS");
 }
 private static void firstPerson(Minecraft mc,ItemStack stack,GuiGraphics gui){
  var pose=gui.pose();pose.pushPose();pose.translate(280,0,150);pose.scale(90,-90,90);
  var renderer=new com.tacz.guns.client.renderer.item.GunItemRendererWrapper();renderer.tryInit(stack,mc.player,0);renderer.renderFirstPerson(mc.player,stack,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,pose,mc.renderBuffers().bufferSource(),15728880,0);pose.popPose();
 }
 private static void assertGlintFlushed(Minecraft mc)throws Exception{
  var field=net.minecraft.client.renderer.MultiBufferSource.BufferSource.class.getDeclaredField("fixedBuffers");field.setAccessible(true);
  var buffers=(java.util.Map<net.minecraft.client.renderer.RenderType,BufferBuilder>)field.get(mc.renderBuffers().bufferSource());
  for(var type:new net.minecraft.client.renderer.RenderType[]{net.minecraft.client.renderer.RenderType.glintDirect(),net.minecraft.client.renderer.RenderType.entityGlintDirect()}){
   var builder=buffers.get(type);if(builder!=null&&builder.building())throw new AssertionError("Pack-a-Punch glint leaked beyond hand rendering");
  }
  check(true,"Pack-a-Punch finishes glint geometry before the hand projection ends");
 }
 private static void weapon(Minecraft mc,ItemStack stack,GuiGraphics gui){
  var pose=gui.pose();pose.pushPose();pose.translate(320,210,150);pose.scale(145,-145,145);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(75));
  mc.getItemRenderer().renderStatic(stack,ItemDisplayContext.GROUND,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,mc.renderBuffers().bufferSource(),mc.level,0);pose.popPose();
 }
}
