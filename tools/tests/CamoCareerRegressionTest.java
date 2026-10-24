package local.skingalleryclient;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.shapes.CollisionContext;
public final class CamoCareerRegressionTest {
 private static int checks;
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("CAMO CAREER CHECK: "+label);}
 public static void menu()throws Exception {
  var mc=Minecraft.getInstance();check(mc.level==null&&mc.player==null,"Fresh launch tests career before entering any map");
  var ids=TacZVisuals.careerWeapons();var all=com.tacz.guns.api.TimelessAPI.getAllClientGunIndex();check(!all.isEmpty(),"Installed gunpack indexes load in the main menu");
  var mapped=new java.util.HashSet<String>();for(var def:me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.values())mapped.addAll(me.cryo.zombierool.integration.TacZMappings.mappedIds(def));
  for(var e:all){if(!mapped.contains(e.getKey().toString()))check(ids.contains(e.getKey().toString()),"Menu catalog includes unmapped gun "+e.getKey());}
  var entry=all.iterator().next();var preview=TacZVisuals.careerPreview(entry.getKey().toString(),null);check(!preview.isEmpty()&&preview.getOrCreateTag().getString("GunId").equals(entry.getKey().toString()),"Menu builds a TACZ cosmetic preview without a server");check(entry.getValue().getDefaultDisplay()!=null,"Menu preview resolves its local gun display");
  for(String id:new String[]{"grotog","sponsor_animated","halloween_animated","horror_eye_animated"}){var camo=me.cryo.zombierool.client.career.CareerUnlockables.CAMOS.get("camo_"+id);check(camo.rarity.equals("exclusive")&&camo.price==0&&camo.isGlobalUnlock,"Exclusive camo cannot be bought: "+id);}
  for(String id:new String[]{"cherry_blossom_animated","halloween_animated","horror_eye_animated","sponsor_animated"}){String label=net.minecraft.client.resources.language.I18n.get("camo.zombierool."+id);check(!label.toLowerCase(java.util.Locale.ROOT).contains("anim"),"Display label omits animation suffix: "+label);}
  fitted();
 }
 private static void fitted()throws Exception {
  var bounds=new TacZCamoOverlay.FittedBounds();bounds.vertex(-1,-2,-3);bounds.vertex(3,2,5);
  for(int axis=0;axis<3;axis++)for(float angle:new float[]{0,.4f,1.8f}){
   float[] uv=new float[2];var sink=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(CamoCareerRegressionTest.class.getClassLoader(),new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(p,m,a)->{if(m.getName().equals("uv")){uv[0]=(float)a[0];uv[1]=(float)a[1];}return m.getReturnType()==com.mojang.blaze3d.vertex.VertexConsumer.class?p:null;});
   var bind=new com.mojang.blaze3d.vertex.PoseStack();var animated=new com.mojang.blaze3d.vertex.PoseStack();animated.translate(2,-3,4);animated.mulPose(com.mojang.math.Axis.YP.rotation(angle));
   var local=new org.joml.Vector3f(1,0,1);var position=new org.joml.Vector3f(local).mulPosition(animated.last().pose());var normal=new org.joml.Vector3f(axis==0?1:0,axis==1?1:0,axis==2?1:0).mul(animated.last().normal());
   new TacZCamoOverlay.SurfaceConsumer(sink,animated.last(),bind.last(),1,bounds).vertex(position.x,position.y,position.z).normal(normal.x,normal.y,normal.z).endVertex();check(Math.abs(uv[0]-.5)<.0001&&Math.abs(uv[1]-.5)<.0001,"Fitted artwork remains centered during animation, axis "+axis);
  }
 }
 public static void show(){
  var mc=Minecraft.getInstance();mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("Fitted TACZ artwork")){
   @Override public boolean isPauseScreen(){return false;}
   @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mouseX,int mouseY,float partial){
    g.fill(0,0,width,height,0xFF141C26);String[] ids={"grotog","halloween_animated","horror_eye_animated"};
    for(int i=0;i<3;i++){int w=width/3,cx=w*i+w/2;g.drawCenteredString(font,ids[i].replace("_animated",""),cx,24,0xFFFFFF);
     var stack=me.cryo.zombierool.core.system.WeaponFacade.createWeaponStack("scarh",false,mc.player);stack.getOrCreateTag().putString("zr_camo","camo_"+ids[i]);var pose=g.pose();pose.pushPose();pose.translate(cx,height/2,150);float scale=Math.min(w,height)*.65f;pose.scale(scale,-scale,scale);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(10));pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(0));mc.getItemRenderer().renderStatic(stack,net.minecraft.world.item.ItemDisplayContext.FIXED,0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,g.bufferSource(),mc.level,0);pose.popPose();}
    g.flush();
   }
  });
 }
 public static int server(ServerPlayer p)throws Exception {
  var l=p.serverLevel();var pos=new BlockPos(810,65,0);var old=p.position();var mode=p.gameMode.getGameModeForPlayer();
  try{
   for(var block:java.util.List.of(me.cryo.zombierool.core.registry.ZRBlocks.DEFENSE_DOOR.get(),me.cryo.zombierool.core.registry.ZRBlocks.IRON_DEFENSE_DOOR.get(),me.cryo.zombierool.core.registry.ZRBlocks.GLASS_DEFENSE_DOOR.get(),DefenseWallSystem.MAIN_BLOCK.get(),DefenseWallSystem.DUMMY_BLOCK.get())){
    var stage=(net.minecraft.world.level.block.state.properties.IntegerProperty)block.getStateDefinition().getProperty("stage");
    for(int value:stage.getPossibleValues())for(var facing:net.minecraft.core.Direction.Plane.HORIZONTAL){
     var s=block.defaultBlockState().setValue(stage,value).setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,facing);
     for(var gm:new GameType[]{GameType.CREATIVE,GameType.SPECTATOR}){p.setGameMode(gm);check(s.getCollisionShape(l,pos,CollisionContext.of(p)).isEmpty(),block+" allows "+gm+" at stage "+value+" facing "+facing);}
     if(s.hasProperty(net.minecraft.world.level.block.DoorBlock.HALF)){p.setGameMode(GameType.CREATIVE);check(s.setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER).getCollisionShape(l,pos,CollisionContext.of(p)).isEmpty(),"Upper door half also allows creative at stage "+value);}
    }
    var full=block.defaultBlockState();l.setBlock(pos,full,2);p.setGameMode(GameType.CREATIVE);p.setPos(810.5,65,-.6);p.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(0,0,1.5));check(p.getZ()>.8,"Real creative movement crosses full-stage "+block);
    p.setGameMode(GameType.SURVIVAL);check(!full.getCollisionShape(l,pos,CollisionContext.of(p)).isEmpty(),"Survival collision remains solid for full-stage "+block);l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
   }
  }finally{l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);p.setGameMode(mode);p.teleportTo(l,old.x,old.y,old.z,0,0);}
  System.out.println("CAMO CAREER PASS: "+checks+" checks");return checks;
 }
}
