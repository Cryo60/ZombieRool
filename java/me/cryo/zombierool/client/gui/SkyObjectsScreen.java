package me.cryo.zombierool.client.gui;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.client.MapAtmosphereClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Model imports are embedded in the preset, including on dedicated servers. */
public final class SkyObjectsScreen extends Screen {
    private final Screen parent;
    private final CompoundTag original,draft;
    private final Consumer<ListTag> accept;
    private int selected,page,dirty;
    private String error="";
    private final List<String> labels=new ArrayList<>();
    private final List<Consumer<Integer>> rows=new ArrayList<>();
    public SkyObjectsScreen(Screen parent,CompoundTag settings,Consumer<ListTag> accept){super(tr("title"));this.parent=parent;original=settings.copy();draft=settings.copy();this.accept=accept;}
    private static Component tr(String key){return Component.translatable("gui.zombierool.skyObjects."+key);}
    private ListTag objects(){return draft.getList("skyObjects",10);}
    private CompoundTag object(){selected=Math.max(0,Math.min(selected,objects().size()-1));return objects().isEmpty()?null:objects().getCompound(selected);}
    private int left(){return (width-Math.min(width-16,550))/2;}
    private int formX(){return left()+140;}
    private int formWidth(){return Math.min(width-16,550)-140;}
    private int capacity(){return Math.max(3,(height-111)/23);}
    private void changed(){draft.putBoolean("enabled",true);draft.putBoolean("skyEnabled",true);dirty=5;}
    private void preview(CompoundTag n){MapAtmosphereClient.receive("preview",n);MapAtmospherePackets.request("preview",n);}
    private void row(String name,Consumer<Integer> widget){labels.add(name);rows.add(widget);}
    private void slider(CompoundTag n,String key,double min,double max){row(key,y->addRenderableWidget(new AbstractSliderButton(formX()+Math.min(115,formWidth()/2),y,Math.max(65,formWidth()-Math.min(115,formWidth()/2)),18,Component.empty(),Math.max(0,Math.min(1,(n.getDouble(key)-min)/(max-min)))){
        {updateMessage();}protected void updateMessage(){setMessage(Component.literal(String.format(Locale.ROOT,"%.2f",min+value*(max-min))));}protected void applyValue(){n.putDouble(key,min+value*(max-min));changed();}
    }));}
    @Override protected void init(){
        clearWidgets();rows.clear();labels.clear();int x=left();
        addRenderableWidget(Button.builder(tr("add"),b->minecraft.setScreen(new Types())).bounds(x,35,130,18).build()).active=objects().size()<SkyObjects.MAX_OBJECTS;
        addRenderableWidget(Button.builder(tr("import"),b->importModel()).bounds(x,57,130,18).build()).active=objects().size()<SkyObjects.MAX_OBJECTS;
        int visible=Math.max(1,(height-164)/21),start=Math.max(0,selected-visible+1);for(int i=start;i<Math.min(objects().size(),start+visible);i++){final int index=i;var n=objects().getCompound(i);String name=n.getString("name");if(name.isBlank())name=tr(n.getString("type")).getString();String label=font.plainSubstrByWidth(name,108);addRenderableWidget(Button.builder(Component.literal((i==selected?"> ":"")+label),b->{selected=index;page=0;init();}).bounds(x,85+(i-start)*21,130,18).build());}
        if(objects().size()>1){addRenderableWidget(Button.builder(Component.literal("<"),b->{selected=Math.floorMod(selected-1,objects().size());page=0;init();}).bounds(x,height-78,35,18).build());addRenderableWidget(Button.builder(Component.literal((selected+1)+" / "+objects().size()+" >"),b->{selected=(selected+1)%objects().size();page=0;init();}).bounds(x+40,height-78,90,18).build());}
        var n=object();if(n!=null){
            row("name",y->{var input=new EditBox(font,formX()+Math.min(115,formWidth()/2),y,Math.max(65,formWidth()-Math.min(115,formWidth()/2)),18,tr("name"));input.setMaxLength(64);input.setValue(n.getString("name"));input.setResponder(v->{n.putString("name",v);changed();});addRenderableWidget(input);});
            row("enabled",y->addRenderableWidget(CycleButton.onOffBuilder(n.getBoolean("enabled")).displayOnlyValue().create(formX()+Math.min(115,formWidth()/2),y,Math.max(65,formWidth()-Math.min(115,formWidth()/2)),18,tr("enabled"),(b,v)->{n.putBoolean("enabled",v);changed();})));
            row("look",y->addRenderableWidget(Button.builder(tr("look"),b->{if(minecraft.player==null)return;var look=minecraft.player.getLookAngle();n.putDouble("yaw",Math.toDegrees(Math.atan2(look.x,look.z)));n.putDouble("elevation",Math.max(-85,Math.min(85,Math.toDegrees(Math.asin(look.y)))));changed();init();}).bounds(formX(),y,formWidth(),18).build()));
            if(n.getString("type").equals("moon")||n.getString("type").equals("sun")){slider(n,"brightness",0,5);slider(n,"lunarPhase",0,7);slider(n,"tintR",0,1);slider(n,"tintG",0,1);slider(n,"tintB",0,1);}
            slider(n,"yaw",-180,180);slider(n,"elevation",-85,85);slider(n,"size",.1,200);slider(n,"distance",10,400);slider(n,"rotation",-180,180);slider(n,"tilt",-180,180);slider(n,"roll",-180,180);slider(n,"spin",-10,10);slider(n,"orbit",-6,6);slider(n,"bob",0,10);slider(n,"speed",0,4);slider(n,"phase",0,360);slider(n,"opacity",0,1);
            addRenderableWidget(Button.builder(tr("remove"),b->{objects().remove(selected);selected=Math.max(0,selected-1);page=0;changed();init();}).bounds(x,height-55,130,18).build());
            int pages=(rows.size()+capacity()-1)/capacity();page=Math.min(page,pages-1);for(int i=page*capacity();i<Math.min(rows.size(),(page+1)*capacity());i++)rows.get(i).accept(35+(i%capacity())*23);
            addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);init();}).bounds(formX(),height-55,30,18).build());addRenderableWidget(Button.builder(Component.literal((page+1)+" / "+pages+"  >"),b->{page=(page+1)%pages;init();}).bounds(formX()+35,height-55,90,18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->{var clean=SkyObjects.sanitize(objects());draft.put("skyObjects",clean);accept.accept(clean.copy());preview(draft);minecraft.setScreen(parent);}).bounds(x,height-28,130,20).build());addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(formX(),height-28,Math.min(150,formWidth()),20).build());
    }
    private void importModel(){
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
            var patterns=stack.mallocPointer(1);patterns.put(stack.UTF8("*.json")).flip();String file=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(tr("import").getString(),"",patterns,"Sky model JSON (*.json)",false);if(file==null)return;
            var path=java.nio.file.Path.of(file);if(java.nio.file.Files.size(path)>128*1024)throw new IllegalArgumentException();var n=SkyObjects.importModel(java.nio.file.Files.readString(path),"object_"+UUID.randomUUID().toString().substring(0,8));objects().add(n);selected=objects().size()-1;page=0;error="";changed();init();
        }catch(Exception e){error=tr("importError").getString();}
    }
    @Override public void tick(){if(dirty>0&&--dirty==0)preview(MapAtmosphere.sanitize(draft));}
    @Override public void onClose(){preview(original);minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){g.fill(0,0,width,height,0xB00D141E);g.drawCenteredString(font,title,width/2,13,0xFFFFFF);super.render(g,mx,my,pt);for(int i=page*capacity();i<Math.min(labels.size(),(page+1)*capacity());i++){String key=labels.get(i);if(key.equals("look"))continue;int y=40+(i%capacity())*23;g.drawString(font,font.plainSubstrByWidth(tr(key).getString(),Math.min(110,formWidth()/2-3)),formX(),y,0xFFFFFF);if(mx>=formX()&&mx<formX()+Math.min(110,formWidth()/2)&&my>=y-5&&my<y+15)g.renderTooltip(font,tr(key+".help"),mx,my);}if(objects().isEmpty())g.drawCenteredString(font,tr("empty"),formX()+formWidth()/2,100,0xAFC8DB);if(!error.isBlank())g.drawCenteredString(font,error,width/2,height-70,0xFF8888);}
    private final class Types extends Screen {
        Types(){super(tr("add"));}
        @Override protected void init(){int typeIndex=0;for(String type:SkyObjects.TYPES.stream().filter(type->!type.equals("necropolis")).toList()){if(type.equals("custom"))continue;addRenderableWidget(Button.builder(tr(type),b->{var n=SkyObjects.object("object_"+UUID.randomUUID().toString().substring(0,8),type,0,30,type.equals("dogfight")?45:type.equals("meteor")?3:type.equals("eclipse")?8:type.equals("nuclear_cloud")?30:16);objects().add(n);selected=objects().size()-1;page=0;changed();minecraft.setScreen(SkyObjectsScreen.this);}).bounds(width/2-155+(typeIndex%2)*158,42+(typeIndex/2)*23,152,18).build());typeIndex++;}addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(width/2-125,height-28,250,20).build());}
        @Override public void onClose(){minecraft.setScreen(SkyObjectsScreen.this);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float pt){g.fill(0,0,width,height,0xD00D141E);g.drawCenteredString(font,title,width/2,15,0xFFFFFF);super.render(g,mx,my,pt);}
    }
}
