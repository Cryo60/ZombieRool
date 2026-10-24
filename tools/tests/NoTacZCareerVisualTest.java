package local.atmospherevisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import me.cryo.zombierool.client.gui.CareerScreen;
import me.cryo.zombierool.client.render.SpriteCamoTextures;
@Mod("atmospherevisual")
public final class NoTacZCareerVisualTest {
 int age; boolean done;
 public NoTacZCareerVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
 void tick(TickEvent.ClientTickEvent e){
  if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;if(mc.screen==null)return;
  try{
   age++;
   if(age==1){
    if(net.minecraftforge.fml.ModList.get().isLoaded("tacz"))throw new AssertionError("TaCZ must be absent");
    try{Class.forName("com.tacz.guns.client.model.bedrock.BedrockModel");throw new AssertionError("BedrockModel must be absent");}catch(ClassNotFoundException expected){}
    for(String camo:new String[]{"woodland","halloween_animated","_pap"})if(SpriteCamoTextures.texture(new ResourceLocation("zombierool","item/weapons/m1911"),camo)==null)throw new AssertionError(camo);
    var screen=new CareerScreen(mc.screen);mc.setScreen(screen);var f=CareerScreen.class.getDeclaredField("selectedCamo");f.setAccessible(true);f.set(screen,"camo_woodland");
   }
   if(age==30){
    try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),"career-no-tacz.png"));}
    System.out.println("ATMOSPHERE VISUAL PASS: career camo preview rendered 30 frames without TaCZ; static, animated and Pack-a-Punch textures available");done=true;mc.stop();
   }
  }catch(Throwable t){t.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}
 }
}
