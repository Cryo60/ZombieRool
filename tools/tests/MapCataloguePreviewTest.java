package local.cataloguetests;
import me.cryo.zombierool.client.*;
import me.cryo.zombierool.client.gui.*;
import me.cryo.zombierool.configuration.ZRClientConfig;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod("cataloguetests")
public final class MapCataloguePreviewTest {
    int stage,age;boolean done;MapDownloaderScreen catalogue;MapDetailsScreen details;
    public MapCataloguePreviewTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    static Object get(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("CATALOGUE CHECK: "+name);}
    static void click(Screen s,String translation){String label=net.minecraft.network.chat.Component.translatable(translation).getString();s.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(label)).map(c->(Button)c).findFirst().orElseThrow().onPress();}
    static void shot(String name)throws Exception{var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("catalogue.output"),name+".png"));}}
    @SuppressWarnings("unchecked")
    void tick(TickEvent.ClientTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        try{
            if(stage==0){mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());ZRClientConfig.ALLOW_NETWORK_REQUESTS.set(true);catalogue=new MapDownloaderScreen(null);mc.setScreen(catalogue);stage=1;return;}
            age++;
            if(stage==1){
                if((int)get(catalogue,"pending")>0){if(age>1200)throw new AssertionError("Catalogue load timeout");return;}
                if(age<200)return;
                var maps=(List<MapCatalogue.Entry>)get(catalogue,"maps");check(maps.size()>=4,"Real official/community catalogue metadata loaded");
                check(((Map<?,?>)get(catalogue,"textures")).size()>0,"Remote map covers registered as native textures");
                check(MapDownloaderScreen.validMapLink("https://example.com/map.zip")&&!MapDownloaderScreen.validMapLink("file:///test")&&!MapDownloaderScreen.validMapLink("javascript:alert(1)"),"Only web map links are accepted");
                check((int)get(catalogue,"tab")==1,"Catalogue opens directly on Official");
                check(((List<?>)get(catalogue,"cards")).stream().allMatch(c->{try{return ((MapCatalogue.Entry)get(c,"map")).source.equals("official");}catch(Exception e){throw new RuntimeException(e);}}),"Initial cards show official maps only");
                shot("catalogue");
                var search=(EditBox)get(catalogue,"search");search.setValue("__no_results__");check(((List<?>)get(catalogue,"cards")).isEmpty(),"Search filters every discovery shelf");search.setValue("");
                click(catalogue,"gui.zombierool.catalog.community");check(((List<?>)get(catalogue,"cards")).stream().allMatch(c->{try{return ((MapCatalogue.Entry)get(c,"map")).source.equals("community");}catch(Exception e){throw new RuntimeException(e);}}),"Community filter excludes official maps");
                click(catalogue,"gui.zombierool.catalog.discover");
                var card=((List<?>)get(catalogue,"cards")).get(0);int y=((Button)card).getY();
                check(!((Button)card).mouseClicked(((Button)card).getX()+4,30,0),"Cards cannot intercept clicks outside the scrolling viewport");
                ((Button)card).onPress();check(mc.screen instanceof MapDetailsScreen,"Catalogue card opens the details screen");mc.screen.onClose();check(mc.screen==catalogue,"Back restores the existing catalogue");
                var chosen=maps.stream().filter(m->m.reviews.stream().anyMatch(r->!r.comment().isBlank())).findFirst().orElse(maps.get(0));
                set(catalogue,"openingChild",true);details=new MapDetailsScreen(catalogue,chosen);set(details,"loaded",true);mc.setScreen(details);
                for(var child:details.children())if(child instanceof MultiLineEditBox box)box.setValue("Très bonne ambiance ! Une map à essayer entre amis.");
                var stars=(List<Button>)get(details,"starButtons");stars.get(3).onPress();check((int)get(details,"stars")==4,"Native star selector updates the draft rating");
                stage=2;age=0;
            }else if(stage==2&&age==8){shot("map-details");click(details,"gui.zombierool.downloader.get_link");check(mc.screen.children().stream().anyMatch(c->c instanceof EditBox box&&box.getValue().startsWith("https://")),"Get a link keeps the selectable URL flow");mc.screen.onClose();check(((MultiLineEditBox)get(details,"comment")).getValue().contains("ambiance"),"Draft survives link dialog and widget reconstruction");mc.options.guiScale().set(4);mc.resizeDisplay();stage=3;age=0;}
            else if(stage==3&&age==5){check(mc.screen.width<520,"Compact layout exercised");click(details,"gui.zombierool.catalog.reviews");stage=4;age=0;}
            else if(stage==4&&age==5){shot("map-details-compact");check(((MultiLineEditBox)get(details,"comment")).getValue().contains("ambiance"),"Draft survives resize and compact-page switch");details.onClose();ZRClientConfig.ALLOW_NETWORK_REQUESTS.set(false);catalogue.onClose();done=true;System.out.println("CATALOGUE CLIENT PASS");mc.stop();}
        }catch(Throwable e){done=true;e.printStackTrace();mc.stop();}
    }
}
