package local.feedbackvisual;

import me.cryo.zombierool.gameplay.PortableEquipment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Hidden disposable client: validates models, crow rendering, spatial sound and pause mixin. */
@Mod("feedbackvisual")
public final class OctoberFeedbackVisualTest {
    private boolean requested,done;
    private int ticks;
    public OctoberFeedbackVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        try{
            if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"FeedbackFixture");return;}
            if(mc.level==null||mc.player==null)return;ticks++;
            if(ticks==30){
                mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();me.cryo.zombierool.config.WorldConfig.get(level).setDayNightMode("day");level.setDayTime(6000);});
                me.cryo.zombierool.client.ClientEnvironmentEffects.setFogPreset("none",0,0,0,1,512);
                mc.player.setXRot(-10);
                for(var item:PortableEquipment.PAP_PARTS){
                    var model=mc.getItemRenderer().getModel(new ItemStack(item.get()),mc.level,mc.player,0);
                    if(model==mc.getModelManager().getMissingModel())throw new AssertionError("Missing part model "+item.getId());
                    if(model.getQuads(null,null,net.minecraft.util.RandomSource.create()).isEmpty())throw new AssertionError("Empty part model "+item.getId());
                }
                var gun=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("zombierool:whisper")));
                mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,gun);
                if(gun.getItem() instanceof me.cryo.zombierool.item.WhisperItem whisper){whisper.setAmmo(gun,1);}
                System.out.println("FEEDBACK VISUAL models loaded");
            }
            if(ticks==50){
                var at=mc.player.getEyePosition().add(mc.player.getLookAngle().scale(4));
                for(int i=0;i<9;i++){double angle=i*Math.PI*2/9;mc.level.addParticle(me.cryo.zombierool.init.ZombieroolModParticleTypes.BLACK_CROW.get(),at.x,at.y-.2,at.z,Math.cos(angle)*.15,.10+(i%3)*.025,Math.sin(angle)*.15);}
            }
            if(ticks==65){
                try(var screenshot=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){screenshot.writeToFile(java.nio.file.Path.of(System.getProperty("feedback.output"),"crows.png"));}
                mc.getSoundManager().pause();mc.getSoundManager().resume();
                mc.setScreen(new PartsScreen());
            }
            if(ticks==80){
                try(var screenshot=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){screenshot.writeToFile(java.nio.file.Path.of(System.getProperty("feedback.output"),"pap-parts.png"));}
                done=true;System.out.println("FEEDBACK VISUAL PASS");mc.stop();
            }
        }catch(Throwable error){done=true;error.printStackTrace();mc.stop();}
    }
    private static final class PartsScreen extends net.minecraft.client.gui.screens.Screen {
        PartsScreen(){super(Component.literal("Pack-a-Punch buildable components"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){
            g.fill(0,0,width,height,0xffd8d9dd);g.drawCenteredString(font,title,width/2,30,0x20242b);
            int index=0;for(var item:PortableEquipment.PAP_PARTS){var stack=new ItemStack(item.get());int center=width*(++index)/4;
                g.pose().pushPose();g.pose().translate(center-40,height/2-40,0);g.pose().scale(5,5,5);g.renderItem(stack,0,0);g.pose().popPose();
                g.drawCenteredString(font,stack.getHoverName(),center,height/2+60,0x20242b);
            }
        }
    }
}
