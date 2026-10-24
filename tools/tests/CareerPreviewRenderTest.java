package local.skingalleryclient;
public final class CareerPreviewRenderTest {
 static int checks;
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("CAREER RENDER CHECK: "+text);}
 public static void run()throws Exception{
  var mc=net.minecraft.client.Minecraft.getInstance();check(mc.level==null&&mc.player==null,"Preview runs before any world is loaded");me.cryo.zombierool.client.TacZVisuals.careerWeapons();
  for(String id:java.util.List.of("tacz:ak47","tacz:m1911","tacz:scar_h","tacz:ump45","tacz:glock_17")){
   var stack=me.cryo.zombierool.client.TacZVisuals.careerPreview(id,null);check(!stack.isEmpty(),"Client-only preview stack "+id);var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);var display=com.tacz.guns.api.TimelessAPI.getGunDisplay(gun.getGunDisplayId(stack),gun.getGunId(stack)).orElseThrow();var model=display.getGunModel();var baked=mc.getItemRenderer().getModel(stack,null,null,0);System.out.println("CAREER BAKED MODEL: "+id+" custom="+baked.isCustomRenderer()+" type="+baked.getClass().getName());
   me.cryo.zombierool.client.TacZVisuals.renderCareerPreview(stack,new com.mojang.blaze3d.vertex.PoseStack(),mc.renderBuffers().bufferSource(),15728880,0);mc.renderBuffers().bufferSource().endBatch();System.out.println("CAREER RENDERER: "+net.minecraftforge.client.extensions.common.IClientItemExtensions.of(stack).getCustomRenderer().getClass().getName()+" model="+model.getCurrentGunItem()+" lod="+(display.getLodModel()==null?"none":display.getLodModel().getLeft().getCurrentGunItem()));check(model.getCurrentGunItem()==stack||(display.getLodModel()!=null&&display.getLodModel().getLeft().getCurrentGunItem()==stack),"Native TACZ model receives the preview stack "+id);
   stack.getOrCreateTag().putString("zr_camo","camo_lordaeron");me.cryo.zombierool.client.TacZVisuals.renderCareerPreview(stack,new com.mojang.blaze3d.vertex.PoseStack(),mc.renderBuffers().bufferSource(),15728880,0);mc.renderBuffers().bufferSource().endBatch();check((model.getCurrentGunItem()==stack||(display.getLodModel()!=null&&display.getLodModel().getLeft().getCurrentGunItem()==stack)),"Camo reaches native preview "+id);
  }
  check(!me.cryo.zombierool.client.TacZVisuals.isCareerRender(),"Career-only lookup scope is restored");
  show("ak47");
 }
 public static void show(String weapon)throws Exception{var mc=net.minecraft.client.Minecraft.getInstance();var screen=new me.cryo.zombierool.client.gui.CareerScreen(null);var selected=screen.getClass().getDeclaredField("selectedWeapon");selected.setAccessible(true);selected.set(screen,weapon);mc.setScreen(screen);}
}
