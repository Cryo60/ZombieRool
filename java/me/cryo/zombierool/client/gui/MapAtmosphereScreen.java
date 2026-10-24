package me.cryo.zombierool.client.gui;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.client.MapAtmosphereClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Presets first; common controls fit a page. Resource IDs and timing details remain available in Advanced. */
public final class MapAtmosphereScreen extends Screen {
    private static Screen requestedParent;
    private final Screen parent;
    private CompoundTag draft;
    private int tab,page,selected,dirty;
    private boolean saved,advanced,saving,importing;
    private static MapAtmosphereScreen importingEditor;
    private String error="";
    private Button presetButton;
    private final Map<String,CompoundTag> library=new LinkedHashMap<>();
    private final Set<String> pendingPresets=new LinkedHashSet<>();
    private final Set<String> storedPresets=new HashSet<>();
    private final Set<String> deletedPresets=new LinkedHashSet<>();
    private String chosenPreset;
    private final List<String> labels=new ArrayList<>();
    private final List<java.util.function.IntConsumer> rows=new ArrayList<>();
    public static void request(Screen parent){requestedParent=parent;MapAtmospherePackets.request("open",new CompoundTag());}
    public static void open(CompoundTag n){Minecraft.getInstance().setScreen(new MapAtmosphereScreen(n,requestedParent));requestedParent=null;}
    public MapAtmosphereScreen(CompoundTag n,Screen parent){super(tr("title"));draft=MapAtmosphere.sanitize(n);for(Tag tag:n.getList("_presets",10)){var row=(CompoundTag)tag;library.put(row.getString("id"),row.copy());storedPresets.add(row.getString("id"));}chosenPreset=draft.getString("presetId");if(!presetIds().contains(chosenPreset))chosenPreset="nuketown_zombies";this.parent=parent;}
    private static Component tr(String id){return Component.translatable("gui.zombierool.atmosphere."+id);}
    public static void saveResult(boolean success){var screen=Minecraft.getInstance().screen;if(screen instanceof MapAtmosphereScreen editor){editor.saving=false;if(success){editor.saved=true;editor.onClose();}else{editor.error="saveFailed";editor.init();}}}
    private List<String> presetIds(){var ids=new ArrayList<>(MapAtmosphere.presetIds());ids.addAll(library.keySet());return ids;}
    private String presetName(String id){return library.containsKey(id)?library.get(id).getString("name"):MapAtmosphere.presetName(id);}
    private void syncPending(){String id=draft.getString("presetId");if(pendingPresets.contains(id))library.get(id).put("settings",MapAtmosphere.sanitize(draft));}
    private void newCopy(String name){syncPending();String id="custom_"+UUID.randomUUID().toString().replace("-","");draft.putString("presetId",id);chosenPreset=id;var row=new CompoundTag();row.putString("id",id);row.putString("name",name);row.put("settings",MapAtmosphere.sanitize(draft));library.put(id,row);pendingPresets.add(id);if(presetButton!=null)presetButton.setMessage(Component.literal(name+" ▾"));}
    private void changed(){
        draft.putBoolean("enabled",true);
        String current=draft.getString("presetId");if(current.matches("custom_[a-f0-9]{32}")&&library.containsKey(current))pendingPresets.add(current);
        if(!pendingPresets.contains(draft.getString("presetId"))){if(library.size()>=32){error="presetLimit";return;}String name=presetName(chosenPreset)+" - "+tr("copySuffix").getString();if(name.length()>58)name=name.substring(0,58);String base=name;int i=2;while(hasName(name))name=base+" "+i++;newCopy(name);}
        syncPending();dirty=8;
    }
    private boolean hasName(String name){for(var row:library.values())if(row.getString("name").equals(name))return true;return false;}
    private void namePreset(){if(library.size()>=32){error="presetLimit";return;}minecraft.setScreen(new NamePreset());}
    private void saveDraft(){if(saving||importing)return;if(error.equals("presetLimit"))return;syncPending();for(String id:pendingPresets)if(library.get(id).getString("name").isBlank()){error="nameRequired";return;}var n=MapAtmosphere.sanitize(draft);var rows=new ListTag();for(String id:pendingPresets)rows.add(library.get(id).copy());n.put("_presets",rows);var deleted=new ListTag();deletedPresets.forEach(id->deleted.add(StringTag.valueOf(id)));n.put("_deletedPresets",deleted);saving=true;MapAtmospherePackets.request("save",n);}

    private void deletePreset(){
        String id=chosenPreset;if(MapAtmosphere.presetIds().contains(id)||!id.matches("custom_[a-f0-9]{32}")||!library.containsKey(id))return;
        pendingPresets.remove(id);if(storedPresets.contains(id))deletedPresets.add(id);library.remove(id);chosenPreset="none";draft=MapAtmosphere.defaults();selected=0;page=0;dirty=0;error="";
        MapAtmosphereClient.receive("preview",draft);MapAtmospherePackets.request("preview",draft);init();
    }
    private void pickFile(boolean sky){
        if(importing)return;
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
            var patterns=stack.mallocPointer(1);patterns.put(stack.UTF8(sky?"*.png":"*.ogg")).flip();
            String file=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(tr(sky?"importSky":"importSound").getString(),"",patterns,sky?"PNG (panorama 2:1 / atlas 3:2 / cross 4:3)":"Ogg Vorbis (*.ogg)",false);
            if(file!=null)importFile(java.nio.file.Path.of(file),sky);
        }catch(Exception exception){error="importFailed";init();}
    }
    /** Shared by the native file picker and the real-client import regression test. */
    public void importFile(java.nio.file.Path file,boolean sky){
        if(importing)return;importing=true;importingEditor=this;error="";
        java.util.concurrent.CompletableFuture.runAsync(()->{
            try{
                long size=java.nio.file.Files.size(file);if(size<=0||size>(sky?8:15)*1024*1024)throw new java.io.IOException();
                byte[] data=java.nio.file.Files.readAllBytes(file);String name=file.getFileName().toString();
                minecraft.execute(()->{int chunk=MapAtmosphereImports.CHUNK,count=(data.length+chunk-1)/chunk;UUID token=UUID.randomUUID();for(int i=0;i<count;i++){var n=new CompoundTag();n.putUUID("token",token);n.putString("name",name);n.putString("kind",sky?"sky":"sound");n.putInt("index",i);n.putInt("count",count);n.putByteArray("data",Arrays.copyOfRange(data,i*chunk,Math.min(data.length,(i+1)*chunk)));MapAtmospherePackets.request("import",n);}});
            }catch(Exception exception){minecraft.execute(()->importResult(false,new CompoundTag()));}
        });
    }
    public static void importResult(boolean success,CompoundTag result){
        var editor=importingEditor;if(editor==null)return;importingEditor=null;editor.importing=false;
        if(!success){editor.error="importFailed";}else{
            String id=result.getString("id");
            if(result.getString("kind").equals("sky")){editor.draft.putString("skyTexture",id);editor.draft.putBoolean("skyEnabled",true);editor.draft.putString("skyLayout",result.getString("skyLayout"));editor.draft.putString("skyCoordinates",result.getString("skyLayout").equals("panorama")?"y_up":result.getString("skyCoordinates"));editor.tab=1;}
            else{var p=editor.minecraft.player;if(p==null)return;if(editor.sources().size()>=MapAtmosphere.MAX_SOURCES){editor.error="sourceLimit";editor.init();return;}editor.sources().add(MapAtmosphere.source("ambience_"+UUID.randomUUID().toString().substring(0,6),id,false,false,p.getX(),p.getY(),p.getZ(),.7,128,400,1200));editor.selected=editor.sources().size()-1;editor.tab=2;}
            editor.page=0;editor.changed();
        }
        if(editor.minecraft.screen==editor)editor.init();
    }

    private int left(){return width/2-155;}
    private void number(CompoundTag tag,String key){
        if(Set.of("x","y","z").contains(key)){row(key,y->{var box=new EditBox(font,left()+174,y,130,18,tr(key));box.setValue(String.format(Locale.ROOT,"%.2f",tag.getDouble(key)));box.setMaxLength(16);box.setResponder(value->{try{double n=Double.parseDouble(value.replace(',','.'));if(Double.isFinite(n)){tag.putDouble(key,n);changed();}}catch(NumberFormatException ignored){}});addRenderableWidget(box);});return;}
        double min=switch(key){case "skyPitch"->-180;case "exposure"->-5;case "gamma"->.1;case "vignetteSoftness"->.05;case "pitch"->.25;default->0;};
        double max=switch(key){case "skyPitch"->180;case "exposure"->5;case "vignetteSoftness","occlusion","flash"->1;case "pitchVariation"->.5;case "near"->4096;case "soundSpeed"->2000;case "fadeIn","fadeOut"->60;default->4;};
        row(key,y->addRenderableWidget(numberSlider(tag,key,min,max,left()+174,y,130,key.equals("fadeIn")||key.equals("fadeOut")?20:1)));
    }
    private void text(CompoundTag tag,String key){row(key,y->{var box=new EditBox(font,left()+174,y,130,18,tr(key));box.setMaxLength(256);box.setValue(tag.getString(key));box.setResponder(s->{tag.putString(key,s);changed();});addRenderableWidget(box);});}
    private void bool(CompoundTag tag,String key){row(key,y->addRenderableWidget(CycleButton.onOffBuilder(tag.getBoolean(key)).displayOnlyValue().create(left()+174,y,130,18,tr(key),(b,v)->{tag.putBoolean(key,v);changed();if(key.equals("loop"))init();})));}
    private void slider(CompoundTag tag,String key,double min,double max){row(key,y->addRenderableWidget(numberSlider(tag,key,min,max,left()+174,y,130,1)));}
    private AbstractSliderButton numberSlider(CompoundTag tag,String key,double min,double max,int x,int y,int w,double scale){return new AbstractSliderButton(x,y,w,18,Component.empty(),Math.max(0,Math.min(1,(tag.getDouble(key)/scale-min)/(max-min)))){
        {updateMessage();}protected void updateMessage(){boolean integer=Set.of("skyYaw","skyPitch","radius","near","soundSpeed").contains(key);setMessage(Component.literal(String.format(Locale.ROOT,integer?"%.0f":"%.2f",min+value*(max-min))));}
        protected void applyValue(){tag.putDouble(key,(min+value*(max-min))*scale);if(key.equals("intervalMin"))tag.putDouble("intervalMax",Math.max(tag.getDouble("intervalMax"),tag.getDouble(key)));if(key.equals("intervalMax"))tag.putDouble("intervalMin",Math.min(tag.getDouble("intervalMin"),tag.getDouble(key)));changed();}
        @Override public boolean mouseReleased(double mx,double my,int button){boolean result=super.mouseReleased(mx,my,button);if(key.startsWith("interval"))MapAtmosphereScreen.this.init();return result;}
    };}
    private void row(String label,java.util.function.IntConsumer widget){labels.add(label);rows.add(widget);}
    private ListTag sources(){return draft.getList("sources",10);}
    private CompoundTag source(){var list=sources();selected=Math.max(0,Math.min(selected,list.size()-1));return list.isEmpty()?null:list.getCompound(selected);}
    private int capacity(){return Math.max(2,(height-152)/23);}
    private void applyPreset(){syncPending();draft=library.containsKey(chosenPreset)?library.get(chosenPreset).getCompound("settings").copy():MapAtmosphere.preset(chosenPreset);var p=minecraft.player;if(p!=null&&!library.containsKey(chosenPreset))for(Tag t:sources()){var n=(CompoundTag)t;n.putDouble("x",n.getDouble("x")+p.getX());n.putDouble("y",n.getDouble("y")+p.getY()-80);n.putDouble("z",n.getDouble("z")+p.getZ());}selected=0;page=0;MapAtmosphereClient.receive("preview",draft);MapAtmospherePackets.request("preview",draft);init();}
    private void pickSound(boolean add){minecraft.setScreen(new SoundPicker(this,id->{if(add){if(sources().size()>=MapAtmosphere.MAX_SOURCES)return;var p=minecraft.player;var n=MapAtmosphere.source("ambience_"+UUID.randomUUID().toString().substring(0,6),id,false,false,p.getX(),p.getY(),p.getZ(),.7,128,400,1200);sources().add(n);selected=sources().size()-1;}else if(source()!=null)source().putString("sound",id);draft.putBoolean("enabled",true);page=0;changed();init();}));}
    @Override protected void init(){
        clearWidgets();rows.clear();labels.clear();String[] tabs={"presets","image","audio"};for(int i=0;i<3;i++){final int target=i;addRenderableWidget(Button.builder(tr(tabs[i]),b->{tab=target;page=0;init();}).bounds(left()+i*105,34,101,20).build());}
        if(tab==0){
            row("choosePreset",y->{presetButton=addRenderableWidget(Button.builder(Component.literal(presetName(chosenPreset)+" ▾"),b->minecraft.setScreen(new PresetPicker())).bounds(left()+124,y,180,18).build());});
            row("createPreset",y->addRenderableWidget(Button.builder(tr("createBlank"),b->namePreset()).bounds(left()+124,y,180,18).build()));
            if(!MapAtmosphere.presetIds().contains(chosenPreset)&&chosenPreset.matches("custom_[a-f0-9]{32}")&&library.containsKey(chosenPreset))row("deletePreset",y->addRenderableWidget(Button.builder(tr("deletePreset"),b->deletePreset()).bounds(left()+124,y,180,18).build()));
            if(pendingPresets.contains(chosenPreset))row("presetName",y->{var box=new EditBox(font,left()+124,y,180,18,tr("presetName"));box.setMaxLength(64);box.setValue(library.get(chosenPreset).getString("name"));box.setResponder(value->{library.get(chosenPreset).putString("name",value.trim());if(presetButton!=null)presetButton.setMessage(Component.literal(value+" ▾"));});addRenderableWidget(box);});

            if(advanced){text(draft,"skyTexture");}
        }
        if(tab==1){
            row("skyObjects",y->addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.skyObjects.title"),b->minecraft.setScreen(new SkyObjectsScreen(this,draft,objects->{draft.put("skyObjects",objects);draft.putBoolean("skyEnabled",true);changed();}))).bounds(left()+124,y,180,18).build()));
            bool(draft,"skyEnabled");row("skyFile",y->addRenderableWidget(Button.builder(tr("importSky"),b->pickFile(true)).bounds(left()+124,y,180,18).build()));
            if(advanced){text(draft,"skyTexture");row("skyLayout",y->addRenderableWidget(CycleButton.builder((String s)->tr(s)).withValues("cube","panorama").withInitialValue(draft.getString("skyLayout")).displayOnlyValue().create(left()+174,y,130,18,tr("skyLayout"),(b,v)->{draft.putString("skyLayout",v);changed();})));row("skyCoordinates",y->addRenderableWidget(CycleButton.builder((String s)->tr(s)).withValues("z_up","y_up").withInitialValue(draft.getString("skyCoordinates")).displayOnlyValue().create(left()+174,y,130,18,tr("skyCoordinates"),(b,v)->{draft.putString("skyCoordinates",v);changed();})));for(String k:List.of("skyPitch","red","green","blue","exposure","gamma","vignetteSoftness"))number(draft,k);}
            else{
                var skies=new ArrayList<String>();for(String id:MapAtmosphere.presetIds())if(!id.equals("none"))skies.add(MapAtmosphere.preset(id).getString("skyTexture"));skies.addAll(MapAtmosphereClient.skyIds().stream().sorted().toList());if(!skies.contains(draft.getString("skyTexture")))skies.add(draft.getString("skyTexture"));
                row("sky",y->addRenderableWidget(CycleButton.builder((String value)->{for(String id:MapAtmosphere.presetIds())if(!id.equals("none")&&MapAtmosphere.preset(id).getString("skyTexture").equals(value))return Component.literal(MapAtmosphere.presetName(id));return Component.literal(value.substring(value.lastIndexOf('/')+1));}).withValues(skies).withInitialValue(draft.getString("skyTexture")).displayOnlyValue().create(left()+124,y,180,18,tr("sky"),(b,v)->{draft.putString("skyTexture",v);changed();})));
                slider(draft,"skyYaw",-180,180);slider(draft,"skyBrightness",0,2);slider(draft,"saturation",0,2);slider(draft,"contrast",0,2);slider(draft,"vignette",0,1);slider(draft,"dirt",0,1);slider(draft,"grain",0,.2);
            }
        }
        if(tab==2){row("soundFile",y->addRenderableWidget(Button.builder(tr("importSound"),b->pickFile(false)).bounds(left()+124,y,180,18).build()));slider(draft,"ambientVolume",0,2);var source=source();if(source!=null){
            row("selected",y->addRenderableWidget(Button.builder(Component.literal(soundName(source.getString("sound")).getString()+" ▾"),b->minecraft.setScreen(new SoundPicker(this,true,id->{for(int i=0;i<sources().size();i++)if(sources().getCompound(i).getString("id").equals(id)){selected=i;break;}page=0;init();}))).bounds(left()+124,y,180,18).build()));
            row("position",y->addRenderableWidget(Button.builder(tr("placeHere"),b->{var p=minecraft.player;if(p==null)return;source.putDouble("x",p.getX());source.putDouble("y",p.getY());source.putDouble("z",p.getZ());source.putBoolean("global",false);changed();init();}).bounds(left()+174,y,130,18).build()));
            if(advanced){text(source,"id");text(source,"sound");for(String k:List.of("x","y","z","near","pitch","pitchVariation","occlusion","fadeIn","fadeOut","flash","soundSpeed"))number(source,k);}
            else{row("sound",y->addRenderableWidget(Button.builder(soundName(source.getString("sound")),b->pickSound(false)).bounds(left()+124,y,180,18).build()));bool(source,"enabled");bool(source,"global");bool(source,"loop");slider(source,"volume",0,2);slider(source,"radius",16,1024);
                if(!source.getBoolean("loop"))row("intervalSeconds",y->{for(int i=0;i<2;i++){String key=i==0?"intervalMin":"intervalMax";addRenderableWidget(numberSlider(source,key,.05,Math.max(120,source.getDouble(key)/20*1.25),left()+174+i*67,y,63,20));}});
            }
        }
        addRenderableWidget(Button.builder(tr("addSound"),b->pickSound(true)).bounds(left(),height-80,145,18).build());
        if(source!=null){addRenderableWidget(Button.builder(tr("listen"),b->{var n=source().copy();n.putBoolean("global",true);var cfg=MapAtmosphereClient.settings();cfg.putBoolean("enabled",true);MapAtmosphereClient.receive("preview",cfg);MapAtmosphereClient.receive("cue",n);}).bounds(left()+150,height-80,76,18).build());addRenderableWidget(Button.builder(tr("remove"),b->{sources().remove(selected);selected=0;page=0;changed();init();}).bounds(left()+231,height-80,76,18).build());}}
        page=Math.min(page,Math.max(0,(rows.size()-1)/capacity()));for(int i=page*capacity();i<Math.min(rows.size(),(page+1)*capacity());i++)rows.get(i).accept(65+(i%capacity())*23);
        int pages=Math.max(1,(rows.size()+capacity()-1)/capacity());if(pages>1){addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);init();}).bounds(left(),height-56,30,18).build());addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min(pages-1,page+1);init();}).bounds(left()+90,height-56,30,18).build());}
        addRenderableWidget(Button.builder(tr(advanced?"simple":"advanced"),b->{advanced=!advanced;page=0;init();}).bounds(left()+150,height-56,157,18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->saveDraft()).bounds(left(),height-28,150,20).build());addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(left()+158,height-28,150,20).build());
    }
    @Override public void tick(){if(dirty>0&&--dirty==0)MapAtmospherePackets.request("preview",MapAtmosphere.sanitize(draft));}
    @Override public void onClose(){if(!saved)MapAtmospherePackets.request("restore",new CompoundTag());minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xB00D141E);g.drawCenteredString(font,title,width/2,14,0xFFFFFF);super.render(g,mx,my,partial);for(int i=page*capacity();i<Math.min(labels.size(),(page+1)*capacity());i++){var label=tr(labels.get(i));int limit=List.of("choosePreset","createPreset","saveCopy","deletePreset","skyFile","soundFile","presetName","selected","sound","sky").contains(labels.get(i))?120:166;g.drawString(font,font.plainSubstrByWidth(label.getString(),limit),left(),70+(i%capacity())*23,0xFFFFFF);if(mx>=left()&&mx<left()+limit&&my>=65+(i%capacity())*23&&my<87+(i%capacity())*23)g.renderTooltip(font,tr(labels.get(i)+".help"),mx,my);}
        if(rows.size()>capacity())g.drawString(font,(page+1)+" / "+((rows.size()+capacity()-1)/capacity()),left()+37,height-51,0xAFC8DB);
        if(tab==0&&rows.size()<capacity())g.drawCenteredString(font,tr("presetHint"),width/2,Math.min(height-88,65+rows.size()*23+8),0xAFC8DB);if(importing)g.drawCenteredString(font,tr("importing"),width/2,height-91,0xAFC8DB);else if(!error.isEmpty())g.drawCenteredString(font,tr(error),width/2,height-91,0xFF8888);
        if(tab==2&&sources().isEmpty()){g.drawCenteredString(font,tr("emptySounds"),width/2,95,0xFFFFFF);g.drawCenteredString(font,tr("soundFolder"),width/2,120,0xAFC8DB);}
    }

    private final class NamePreset extends Screen {
        EditBox name;Button create;
        NamePreset(){super(tr("createPreset"));}
        @Override protected void init(){int x=width/2-150;name=new EditBox(font,x,90,300,20,tr("presetName"));name.setMaxLength(64);name.setHint(tr("presetName"));addRenderableWidget(name);create=addRenderableWidget(Button.builder(tr("createPreset"),b->{if(name.getValue().isBlank())return;syncPending();draft=MapAtmosphere.defaults();draft.putBoolean("enabled",true);draft.putBoolean("skyEnabled",false);newCopy(name.getValue().trim());error="";tab=0;page=0;MapAtmosphereClient.receive("preview",draft);MapAtmospherePackets.request("preview",draft);minecraft.setScreen(MapAtmosphereScreen.this);}).bounds(x,125,145,20).build());create.active=false;name.setResponder(value->create.active=!value.isBlank());addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(x+155,125,145,20).build());setInitialFocus(name);}
        @Override public void onClose(){minecraft.setScreen(MapAtmosphereScreen.this);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float pt){g.fill(0,0,width,height,0xE00D141E);g.drawCenteredString(font,title,width/2,45,0xFFFFFF);g.drawCenteredString(font,tr("blankHint"),width/2,67,0xAFC8DB);super.render(g,mx,my,pt);}
    }
    private final class PresetPicker extends Screen {
        PresetList list;String query="";
        PresetPicker(){super(tr("choosePreset"));}
        @Override protected void init(){int w=Math.min(440,width-32),x=(width-w)/2;var box=new EditBox(font,x,40,w,20,tr("searchPresets"));box.setMaxLength(64);box.setHint(tr("searchPresets"));addRenderableWidget(box);list=new PresetList(w,x);addWidget(list);box.setResponder(value->{query=value.toLowerCase(Locale.ROOT);refresh();});refresh();addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x,height-28,w,20).build());}
        void refresh(){list.clearRows();for(String id:presetIds())if((id+" "+presetName(id)).toLowerCase(Locale.ROOT).contains(query))list.addRow(new PresetRow(id));}
        @Override public void onClose(){minecraft.setScreen(MapAtmosphereScreen.this);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float pt){g.fill(0,0,width,height,0xE00D141E);g.drawCenteredString(font,title,width/2,16,0xFFFFFF);list.render(g,mx,my,pt);super.render(g,mx,my,pt);}
        private final class PresetList extends ObjectSelectionList<PresetRow>{
            PresetList(int w,int x){super(PresetPicker.this.minecraft,w,PresetPicker.this.height,68,PresetPicker.this.height-36,34);setLeftPos(x);setRenderBackground(false);setRenderTopAndBottom(false);}
            void clearRows(){clearEntries();}void addRow(PresetRow row){addEntry(row);}
            @Override public int getRowWidth(){return width-14;}@Override protected int getScrollbarPosition(){return getRight()-6;}
        }
        private final class PresetRow extends ObjectSelectionList.Entry<PresetRow>{
            final String id;PresetRow(String id){this.id=id;}
            @Override public Component getNarration(){return Component.literal(presetName(id));}
            @Override public void render(GuiGraphics g,int index,int top,int left,int w,int h,int mx,int my,boolean hovered,float pt){g.fill(left,top,left+w,top+h-2,hovered?0xFF303E50:0x80202B39);g.drawString(font,font.plainSubstrByWidth(presetName(id),w-12),left+6,top+4,0xFFFFFF,false);g.drawString(font,tr(library.containsKey(id)?"customPreset":"bundledPreset"),left+6,top+18,0x9AAEBF,false);}
            @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;chosenPreset=id;applyPreset();minecraft.setScreen(MapAtmosphereScreen.this);return true;}
        }
    }
    private static Component soundName(String id){if(id.startsWith("zombierool:custom/imports/"))return Component.literal(id.substring("zombierool:custom/imports/".length()).replaceFirst("_[a-f0-9]{32}$","").replace('_',' '));return id.startsWith("zombierool:ambient_")?tr("sound."+id.substring(11)):Component.literal(id.replace("zombierool:custom/",""));}
    private static final class SoundPicker extends Screen {
        private record Choice(String id,Component label,CompoundTag cue){}
        final MapAtmosphereScreen parent;final java.util.function.Consumer<String> choose;final boolean existing;
        String query="";List<Choice> visible=List.of();SoundList list;
        SoundPicker(MapAtmosphereScreen parent,java.util.function.Consumer<String> choose){this(parent,false,choose);}
        SoundPicker(MapAtmosphereScreen parent,boolean existing,java.util.function.Consumer<String> choose){super(tr(existing?"chooseSource":"addSound"));this.parent=parent;this.existing=existing;this.choose=choose;}
        @Override protected void init(){
            int w=Math.min(440,width-32),x=(width-w)/2;
            var box=new EditBox(font,x,42,w,20,tr("search"));box.setMaxLength(100);box.setValue(query);box.setHint(tr("search"));addRenderableWidget(box);
            list=new SoundList(w,x);addWidget(list);box.setResponder(s->{query=s;refresh();});refresh();
            addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x,height-28,w,20).build());setInitialFocus(box);
        }
        private void refresh(){
            var all=new ArrayList<Choice>();
            if(existing){for(Tag t:parent.sources()){var n=(CompoundTag)t;all.add(new Choice(n.getString("id"),soundName(n.getString("sound")),n.copy()));}}
            else{var ids=new LinkedHashSet<String>(MapAtmosphere.soundIds());ids.addAll(me.cryo.zombierool.client.DynamicSoundLoader.customAudioIds().stream().sorted().toList());for(String id:ids)all.add(new Choice(id,soundName(id),MapAtmosphere.source("preview",id,false,true,0,0,0,.7,128,400,1200)));}
            visible=all.stream().filter(row->(row.id+" "+row.label.getString()+" "+row.cue.getString("sound")).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();list.clearRows();for(var row:visible)list.addRow(new SoundRow(row));list.setScrollAmount(0);
        }
        private void select(Choice row){MapAtmosphereClient.receive("preview",parent.draft);minecraft.setScreen(parent);choose.accept(row.id);}
        private void listen(Choice row){var n=row.cue.copy();n.putBoolean("global",true);var cfg=parent.draft.copy();cfg.putBoolean("enabled",true);MapAtmosphereClient.receive("preview",cfg);MapAtmosphereClient.receive("cue",n);}
        @Override public void onClose(){MapAtmosphereClient.receive("preview",parent.draft);minecraft.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public boolean keyPressed(int key,int scan,int modifiers){if((key==257||key==335)&&list.getSelected()!=null){select(list.getSelected().row);return true;}return super.keyPressed(key,scan,modifiers);}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xE00D141E);g.drawCenteredString(font,title,width/2,16,0xFFFFFF);list.render(g,mx,my,partial);super.render(g,mx,my,partial);if(visible.isEmpty())g.drawCenteredString(font,tr("noSounds"),width/2,82,0xAFC8DB);g.drawCenteredString(font,tr(existing?"sourceListHint":"soundFolder"),width/2,height-48,0xAFC8DB);}
        private final class SoundList extends ObjectSelectionList<SoundRow>{
            SoundList(int w,int x){super(SoundPicker.this.minecraft,w,SoundPicker.this.height,70,SoundPicker.this.height-60,35);setLeftPos(x);setRenderBackground(false);setRenderTopAndBottom(false);}
            void clearRows(){clearEntries();}void addRow(SoundRow row){addEntry(row);}
            @Override public int getRowWidth(){return width-14;}
            @Override protected int getScrollbarPosition(){return getRight()-6;}
        }
        private final class SoundRow extends ObjectSelectionList.Entry<SoundRow>{
            final Choice row;SoundRow(Choice row){this.row=row;}
            @Override public Component getNarration(){return row.label;}
            @Override public void render(GuiGraphics g,int index,int top,int left,int w,int h,int mx,int my,boolean hovered,float pt){int previewLeft=left+w-61;g.fill(left,top,left+w,top+h-2,hovered?0xFF303E50:0x80202B39);g.drawString(font,font.plainSubstrByWidth(row.label.getString(),w-76),left+6,top+4,0xFFFFFF,false);g.drawString(font,font.plainSubstrByWidth(row.id,w-76),left+6,top+18,0x9AAEBF,false);g.fill(previewLeft,top+5,left+w-4,top+27,0xFF40546B);g.drawCenteredString(font,tr("listen"),previewLeft+28,top+12,0xFFFFFF);}
            @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;list.setSelected(this);if(x>=list.getRight()-66)listen(row);else select(row);return true;}
        }
    }
}
