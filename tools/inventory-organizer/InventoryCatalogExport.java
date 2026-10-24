package local.inventorycatalog;

import com.google.gson.*;
import me.cryo.zombierool.init.CreativeCatalog;
import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.file.*;
import java.util.*;

/** Read-only registry and real GUI icon export in a disposable client world. */
@Mod("inventorycatalog")
public final class InventoryCatalogExport {
    private static final Path OUT=Path.of("D:/Dev/ZombieRool/build/inventory-catalog");
    private final List<ItemStack> icons=new ArrayList<>();
    private final JsonArray entries=new JsonArray(),weapons=new JsonArray();
    private int ticks,page,frames;private boolean requested,ready,done;
    public InventoryCatalogExport(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void prepare()throws Exception{
        var items=new ArrayList<>(ForgeRegistries.ITEMS.getEntries());items.sort(Comparator.comparing(e->e.getKey().location().toString()));
        ItemStack gun=ItemStack.EMPTY;
        for(var item:items){
            var id=item.getKey().location();String p=id.getPath();
            if(!id.getNamespace().equals("zombierool")||p.equals("creative_icon")||p.startsWith("maptex_")||p.startsWith("anim_")||p.endsWith("_opened"))continue;
            var stack=new ItemStack(item.getValue());
            if(WeaponFacade.isWeapon(stack)){weapons.add(id.toString());if(gun.isEmpty()||p.equals("mp5"))gun=stack;continue;}
            var n=new JsonObject();n.addProperty("id",id.toString());n.addProperty("key",stack.getDescriptionId());n.addProperty("nameEn",stack.getHoverName().getString());n.addProperty("category",CreativeCatalog.category(stack).name());n.addProperty("kind",stack.getItem() instanceof BlockItem?"block":"item");n.addProperty("iconIndex",icons.size());entries.add(n);icons.add(stack);
        }
        if(!gun.isEmpty()){
            var n=new JsonObject();n.addProperty("id","@weapons");n.addProperty("nameEn","All weapons");n.addProperty("category","WEAPONS");n.addProperty("kind","family");n.addProperty("iconIndex",icons.size());n.add("members",weapons);entries.add(n);icons.add(gun);
        }
        var root=new JsonObject();root.addProperty("version",1);root.add("items",entries);root.addProperty("weaponCount",weapons.size());
        Files.createDirectories(OUT);Files.writeString(OUT.resolve("catalog.json"),new GsonBuilder().setPrettyPrinting().create().toJson(root));
        System.out.println("INVENTORY CATALOG: "+entries.size()+" entries, "+weapons.size()+" grouped weapons");
    }
    private void tick(TickEvent.ClientTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        try{
            if(!requested){requested=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"InventoryCatalogFixture");return;}
            if(mc.player==null||mc.level==null||mc.isPaused())return;
            if(!ready){if(mc.screen!=null)return;prepare();ready=true;mc.setScreen(new AtlasScreen());ticks=0;return;}
            if(!(mc.screen instanceof AtlasScreen)){mc.setScreen(new AtlasScreen());ticks=frames=0;return;}
            if(++ticks<10||frames<3)return;ticks=frames=0;
            try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve("atlas-"+page+".png"));}
            page++;
            if(page*400>=icons.size()){System.out.println("INVENTORY EXPORT PASS");done=true;mc.stop();}
        }catch(Throwable error){error.printStackTrace();done=true;mc.stop();}
    }
    private final class AtlasScreen extends Screen {
        AtlasScreen(){super(Component.literal("Catalog icon export"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            frames++;g.fill(0,0,width,height,0xFF191D24);Minecraft.getInstance().getToasts().clear();
            for(int i=0;i<400&&page*400+i<icons.size();i++){
                g.pose().pushPose();g.pose().translate((i%20)*48+8,(i/20)*48+8,0);g.pose().scale(2,2,1);g.renderItem(icons.get(page*400+i),0,0);g.pose().popPose();
            }
        }
    }
}
