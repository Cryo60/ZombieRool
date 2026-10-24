package local.skingalleryclient;
import java.nio.file.*;
import com.mojang.blaze3d.vertex.*;
import me.cryo.zombierool.client.*;
public final class CreatorCamoExport {
 public static void export()throws Exception{
  TacZVisuals.careerWeapons();Path root=Path.of("D:/Dev/ZombieRool/tools/camo-studio/models");Files.createDirectories(root);var catalog=new com.google.gson.JsonArray();var gson=new com.google.gson.Gson();
  for(var entry:com.tacz.guns.api.TimelessAPI.getAllClientGunIndex()){
   String id=entry.getKey().toString();if(!java.util.Set.of("tacz:ak47","tacz:scar_h","tacz:m4a1","tacz:glock_17","tacz:m1911","tacz:awm","tacz:ump45").contains(id))continue;
   var display=entry.getValue().getDefaultDisplay();var model=display.getGunModel();if(model==null)continue;var mc=net.minecraft.client.Minecraft.getInstance();var stack=TacZVisuals.careerPreview(id,mc.player);mc.getItemRenderer().renderStatic(stack,net.minecraft.world.item.ItemDisplayContext.FIXED,15728880,0,new PoseStack(),mc.renderBuffers().bufferSource(),mc.level,0);mc.renderBuffers().bufferSource().endBatch();for(var type:com.tacz.guns.api.item.attachment.AttachmentType.values())model.getCurrentAttachmentItem().putIfAbsent(type,net.minecraft.world.item.ItemStack.EMPTY);var vertices=new java.util.ArrayList<float[]>();for(var part:model.getShouldRender())part(part,new PoseStack(),TacZCamoParts.Finish.PAINT,vertices);if(vertices.isEmpty())continue;
   String name=id.replace(':','_');var data=new com.google.gson.JsonObject();data.addProperty("id",id);data.add("vertices",gson.toJsonTree(vertices));Files.writeString(root.resolve(name+".json"),gson.toJson(data));try(var in=net.minecraft.client.Minecraft.getInstance().getResourceManager().open(display.getModelTexture())){Files.copy(in,root.resolve(name+".png"),StandardCopyOption.REPLACE_EXISTING);}var item=new com.google.gson.JsonObject();item.addProperty("id",id);item.addProperty("file",name);catalog.add(item);
  }Files.writeString(root.resolve("index.json"),gson.toJson(catalog));System.out.println("CREATOR MODELS EXPORTED: "+catalog.size());
 }
 static void part(com.tacz.guns.client.model.bedrock.BedrockPart part,PoseStack pose,TacZCamoParts.Finish inherited,java.util.List<float[]> out){
  if(!part.visible||part.xScale==0||part.yScale==0||part.zScale==0)return;if(part instanceof com.tacz.guns.client.model.FunctionalBedrockPart functional&&functional.functionalRenderer!=null&&functional.functionalRenderer.apply(part)!=null)return;
  var finish=TacZCamoParts.resolve(part.name,part.illuminated,inherited);pose.pushPose();part.translateAndRotateAndScale(pose);float[] v=new float[9];v[8]=finish==TacZCamoParts.Finish.PAINT?1:0;
  var sink=(VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(CreatorCamoExport.class.getClassLoader(),new Class[]{VertexConsumer.class},(p,m,a)->{switch(m.getName()){case "vertex":if(a.length==14){out.add(new float[]{((Number)a[0]).floatValue(),((Number)a[1]).floatValue(),((Number)a[2]).floatValue(),((Number)a[11]).floatValue(),((Number)a[12]).floatValue(),((Number)a[13]).floatValue(),((Number)a[7]).floatValue(),((Number)a[8]).floatValue(),v[8]});}if(a.length==3){v[0]=((Number)a[0]).floatValue();v[1]=((Number)a[1]).floatValue();v[2]=((Number)a[2]).floatValue();}break;case "normal":v[3]=(float)a[0];v[4]=(float)a[1];v[5]=(float)a[2];break;case "uv":v[6]=(float)a[0];v[7]=(float)a[1];break;case "endVertex":out.add(v.clone());break;}return m.getReturnType()==VertexConsumer.class?p:null;});
  part.compile(pose.last(),sink,15728880,0,1,1,1,1);for(var child:part.children)part(child,pose,finish,out);pose.popPose();
 }
}
