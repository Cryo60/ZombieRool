package local.skingalleryclient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import me.cryo.zombierool.client.render.SpriteCamoTextures;
import me.cryo.zombierool.client.CamoAppearance;
public final class SpriteCamoRegressionTest {
 public static int checks;private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;System.out.println("SPRITE CAMO CHECK: "+message);}
 private static NativeImage composed(String sprite,String camo){var loc=SpriteCamoTextures.texture(new ResourceLocation("zombierool",sprite),camo);check(loc!=null,"Coating available: "+sprite+" / "+camo);return ((DynamicTexture)Minecraft.getInstance().getTextureManager().getTexture(loc)).getPixels();}
 public static void run()throws Exception{
  var mc=Minecraft.getInstance();var masks=me.cryo.zombierool.client.render.SpriteCamoMasks.load();
  var profiles=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("D:/Dev/ZombieRool/build/zombie-presets/camo-profile-migration.json"))).getAsJsonObject();
  for(var entry:profiles.entrySet()){var expected=entry.getValue().getAsJsonObject();var actual=CamoAppearance.get(entry.getKey());check(actual.repeats==expected.get("repeats").getAsDouble()&&actual.rotation==0&&actual.offsetU==0&&actual.offsetV==0&&actual.opacity==1&&actual.animationSpeed==1&&actual.artworkScale==1&&!actual.fitted&&actual.backgroundColor==-1,"Built-in appearance matches former profile: "+entry.getKey());}
  for(var entry:masks.entrySet()){
   var mask=entry.getValue();try(var stream=mc.getResourceManager().open(new ResourceLocation("zombierool","textures/"+entry.getKey()+".png"));var original=NativeImage.read(stream)){
    var result=composed(entry.getKey(),"camo_woodland");int changed=0,protectedPixels=0;boolean valid=true;int w=original.getWidth(),h=original.getHeight();check(w<=256&&h<=256,"Weapon uses standard sprite resolution: "+entry.getKey());
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){int a=original.getPixelRGBA(x,y),b=result.getPixelRGBA(x,y);if((a>>>24)!=(b>>>24))valid=false;if(!mask.painted(x,y)){protectedPixels++;if(a!=b)valid=false;}else if(a!=b)changed++;}
    check(valid&&changed>0&&protectedPixels>0,"Silhouette and protected details retained while body is painted: "+entry.getKey());
   }
  }
  var profile=CamoAppearance.get("lordaeron");double opacity=profile.opacity;profile.opacity=0;try(var stream=mc.getResourceManager().open(new ResourceLocation("zombierool","textures/item/weapons/ak47.png"));var original=NativeImage.read(stream)){var result=composed("item/weapons/ak47","camo_lordaeron");boolean identical=true;for(int y=0;y<original.getHeight();y++)for(int x=0;x<original.getWidth();x++)if(original.getPixelRGBA(x,y)!=result.getPixelRGBA(x,y))identical=false;check(identical,"Zero opacity restores the exact original sprite");}finally{profile.opacity=opacity;}
  for(String id:java.util.List.of("ak47","glock","scarh","raygun","rpg","needler")){
   var stack=stack(id,"woodland");var model=mc.getItemRenderer().getModel(stack,null,null,0);var passes=model.getRenderPasses(stack,false);check(passes.size()>=2,"Native sprite uses original edges and composed faces: "+id);int count=0;for(var pass:passes){count+=pass.getQuads(null,null,net.minecraft.util.RandomSource.create(42)).size();for(var face:net.minecraft.core.Direction.values())count+=pass.getQuads(null,face,net.minecraft.util.RandomSource.create(42)).size();}check(count<3000,"Coating does not create per-pixel geometry: "+id);}
  show();
 }
 private static net.minecraft.world.item.ItemStack stack(String weapon,String camo){var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool",weapon));var stack=new net.minecraft.world.item.ItemStack(item);if(!camo.isEmpty())stack.getOrCreateTag().putString("zr_camo","camo_"+camo);return stack;}
 public static void show(){var mc=Minecraft.getInstance();mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("2D camo preview")){
  public void render(net.minecraft.client.gui.GuiGraphics g,int mouseX,int mouseY,float partial){g.fill(0,0,width,height,0xFF18232E);String[] guns={"ak47","glock","scarh","raygun"},camos={"","woodland","lordaeron"};int cw=(width-80)/4,rh=(height-28)/3;for(int row=0;row<3;row++){g.drawString(font,row==0?"Original":camos[row],5,24+row*rh,0xFFFFFF);for(int col=0;col<4;col++){int x=80+col*cw,y=12+row*rh;g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(Math.min(5,(cw-8)/16f),Math.min(5,(rh-24)/16f),1);g.renderItem(stack(guns[col],camos[row]),0,0);g.pose().popPose();g.drawString(font,guns[col],x,y+rh-14,0xCCDDEEFF);}}super.render(g,mouseX,mouseY,partial);}
  public boolean isPauseScreen(){return false;}
 });}
}
