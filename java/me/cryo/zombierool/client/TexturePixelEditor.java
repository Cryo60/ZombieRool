package me.cryo.zombierool.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;

/** Small pixel canvas; edits are saved alongside the map, outside resource packs. */
public final class TexturePixelEditor extends Screen {
    private final Screen parent;
    private String texture;
    private NativeImage pixels;
    private final java.util.Map<String,NativeImage> drafts=new java.util.HashMap<>();
    private final ArrayDeque<int[]> undo = new ArrayDeque<>();
    private EditBox color, importName, name;
    private boolean headImport;
    private float uiScale=1;
    private int size = 32, brush = 1, tool, originX, originY, scale;
    private String face = "side";
    private Component status = Component.empty();
    private static final String[] FACES = {"side", "top", "bottom", "lower", "upper", "north", "south", "east", "west"};

    public TexturePixelEditor(Screen parent, String texture) {
        super(Component.translatable("gui.zombierool.texture_editor"));
        this.parent = parent;
        this.texture = texture;
        load();
    }
    private Path directory() { return MapTextureClient.worldRoot().resolve("zombierool/custom_blocks"); }
    private Path target() { return directory().resolve(texture + (face.equals("side") ? "" : "_" + face) + ".png"); }
    private void remember(){NativeImage copy=new NativeImage(size,size,true);for(int y=0;y<size;y++)for(int x=0;x<size;x++)copy.setPixelRGBA(x,y,pixels.getPixelRGBA(x,y));NativeImage previous=drafts.put(face,copy);if(previous!=null)previous.close();}
    private void clearDrafts(){drafts.values().forEach(NativeImage::close);drafts.clear();}
    private void load() {
        if (pixels != null) pixels.close();
        pixels = new NativeImage(size, size, true);
        try {
            NativeImage draft=drafts.get(face);
            if(draft!=null){for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels.setPixelRGBA(x,y,draft.getPixelRGBA(x*draft.getWidth()/size,y*draft.getHeight()/size));undo.clear();return;}
            Path path = target();
            if (!Files.isRegularFile(path)) path = directory().resolve(texture + ".png");
            if (Files.isRegularFile(path)) try (NativeImage source = NativeImage.read(me.cryo.zombierool.maptexture.MapTexturePixels.read(directory(),texture)[java.util.Arrays.asList(me.cryo.zombierool.maptexture.MapTexturePixels.FACES).indexOf(face)])) {
                for (int y=0;y<size;y++) for(int x=0;x<size;x++) pixels.setPixelRGBA(x,y,source.getPixelRGBA(x*source.getWidth()/size,y*source.getHeight()/size));
            }
        } catch (Exception e) { status = Component.translatable("gui.zombierool.texture_error"); }
        undo.clear();
    }
    @Override protected void init() {
        int realWidth=minecraft.getWindow().getGuiScaledWidth(),realHeight=minecraft.getWindow().getGuiScaledHeight();
        uiScale=Math.min(1,Math.min(realWidth/430f,realHeight/370f));width=(int)Math.ceil(realWidth/uiScale);height=(int)Math.ceil(realHeight/uiScale);
        scale = Math.max(2, Math.min(7, (height-115)/size));
        originX = Math.max(8, (width-size*scale-160)/2); originY = 42;
        int x=originX+size*scale+12, y=42;
        String previousFile=importName==null?"":importName.getValue();String previousColor=color==null?"AA2222FF":color.getValue();String previousName=name==null?texture:name.getValue();
        name=new EditBox(font,originX,24,size*scale,16,Component.translatable("gui.zombierool.texture_name"));name.setMaxLength(128);name.setValue(previousName);addRenderableWidget(name);
        color = new EditBox(font,x,y,130,18,Component.translatable("gui.zombierool.texture_color"));
        color.setMaxLength(8); color.setValue(previousColor); addRenderableWidget(color); y+=24;
        for(int i=0;i<4;i++) { final int t=i;
            addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.texture_tool"+i),b->tool=t).bounds(x+(i%2)*66,y+(i/2)*21,64,18).build());
        }
        y+=44;
        addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.texture_brush",brush),b->{brush=brush%8+1;b.setMessage(Component.translatable("gui.zombierool.texture_brush",brush));}).bounds(x,y,130,18).build()); y+=21;
        addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.texture_face_"+face),b->{remember();face=FACES[(Arrays.asList(FACES).indexOf(face)+1)%FACES.length];load();b.setMessage(Component.translatable("gui.zombierool.texture_face_"+face));}).bounds(x,y,130,18).build()); y+=21;
        addRenderableWidget(Button.builder(Component.literal(size+" px"),b->{int next=size==32?16:32;NativeImage scaled=new NativeImage(next,next,true);for(int py=0;py<next;py++)for(int px=0;px<next;px++)scaled.setPixelRGBA(px,py,pixels.getPixelRGBA(px*size/next,py*size/next));pixels.close();pixels=scaled;size=next;undo.clear();rebuildWidgets();}).bounds(x,y,62,18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.texture_undo"),b->undo()).bounds(x+66,y,64,18).build()); y+=24;
        importName=new EditBox(font,x,y,130,18,Component.translatable("gui.zombierool.texture_import_file"));importName.setHint(Component.literal("atlas.png"));importName.setMaxLength(80);importName.setValue(previousFile);addRenderableWidget(importName);y+=21;
        addRenderableWidget(Button.builder(Component.translatable(headImport?"gui.zombierool.texture_import_head":"gui.zombierool.texture_import"),b->importAtlas()).bounds(x,y,130,18).build());
        int[] palette={0x000000,0xFFFFFF,0x777777,0xC0C0C0,0xAA2222,0xEE6633,0xEEDD44,0x228833,0x114455,0x2299CC,0x3344AA,0x8855AA,0xCC5599,0x775533,0xCCA977,0x446633};
        addRenderableWidget(Button.builder(Component.translatable("shape.zombierool.head"),b->{headImport=!headImport;rebuildWidgets();}).bounds(x,y+21,130,18).build());
        for(int i=0;i<palette.length;i++){final int rgb=palette[i];addRenderableWidget(Button.builder(Component.literal("■").withStyle(style->style.withColor(rgb)),b->color.setValue(String.format("%06XFF",rgb))).bounds(originX+(i%8)*(size*scale/8),originY+size*scale+5+(i/8)*17,size*scale/8-1,16).build());}
        addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.texture_save"),b->save()).bounds(originX,height-28,105,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(originX+110,height-28,105,20).build());
    }
    private void checkpoint() {
        int[] copy=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)copy[y*size+x]=pixels.getPixelRGBA(x,y);
        if(undo.size()==32)undo.removeFirst();undo.addLast(copy);
    }
    private void undo() { if(undo.isEmpty())return;int[] copy=undo.removeLast();for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels.setPixelRGBA(x,y,copy[y*size+x]); }
    private int selectedColor() {
        try { String hex=color.getValue();long rgba=Long.parseUnsignedLong(hex,16);if(hex.length()==6)rgba=(rgba<<8)|255;return (int)(((rgba&255)<<24)|((rgba>>8&255)<<16)|((rgba>>16&255)<<8)|(rgba>>24&255)); }
        catch(Exception e) { return 0xFF2222AA; }
    }
    private boolean paint(double mx,double my, boolean first) {
        int x=(int)Math.floor((mx-originX)/scale),y=(int)Math.floor((my-originY)/scale);if(x<0||y<0||x>=size||y>=size)return false;
        if(tool==3){int p=pixels.getPixelRGBA(x,y);color.setValue(String.format("%02X%02X%02X%02X",p&255,(p>>8)&255,(p>>16)&255,(p>>>24)));return true;}
        if(first)checkpoint();int replacement=tool==1?0:selectedColor();
        if(tool==2){int old=pixels.getPixelRGBA(x,y);if(old==replacement)return true;ArrayDeque<Integer> work=new ArrayDeque<>();work.add(y*size+x);while(!work.isEmpty()){int n=work.removeFirst(),px=n%size,py=n/size;if(pixels.getPixelRGBA(px,py)!=old)continue;pixels.setPixelRGBA(px,py,replacement);if(px>0)work.add(n-1);if(px+1<size)work.add(n+1);if(py>0)work.add(n-size);if(py+1<size)work.add(n+size);} }
        else for(int py=y-brush/2;py<y-brush/2+brush;py++)for(int px=x-brush/2;px<x-brush/2+brush;px++)if(px>=0&&py>=0&&px<size&&py<size)pixels.setPixelRGBA(px,py,replacement);
        return true;
    }
    @Override public boolean mouseClicked(double x,double y,int button){x/=uiScale;y/=uiScale;return button==0&&paint(x,y,true)||super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){x/=uiScale;y/=uiScale;return button==0&&(tool==0||tool==1)&&paint(x,y,false)||super.mouseDragged(x,y,button,dx/uiScale,dy/uiScale);}
    @Override public boolean mouseReleased(double x,double y,int button){return super.mouseReleased(x/uiScale,y/uiScale,button);}
    private boolean acceptName(){String value=me.cryo.zombierool.maptexture.MapTextures.sanitize(name.getValue());if(value.isEmpty()||value.equals("import")||value.startsWith("import/")||minecraft.player==null||!minecraft.player.isCreative()){status=Component.translatable("gui.zombierool.texture_error");return false;}texture=value;name.setValue(value);return true;}
    private void refreshSlots(){var server=minecraft.getSingleplayerServer();if(server==null)return;var uuid=minecraft.player.getUUID();server.execute(()->{var names=me.cryo.zombierool.maptexture.MapTextures.syncFolder(server.overworld());me.cryo.zombierool.maptexture.MapTextureSync.refresh(server.overworld(),null);var player=server.getPlayerList().getPlayer(uuid);if(player!=null&&player.containerMenu instanceof me.cryo.zombierool.maptexture.MapTextures.TextureKitMenu menu)for(int i=0;i<names.size();i++)menu.names.set(i,names.get(i));minecraft.tell(()->{if(parent instanceof TextureKitScreen screen){screen.getMenu().names.clear();screen.getMenu().names.addAll(names);}MapTextureClient.rebuildIfChanged();});});}
    private void save(){if(!acceptName())return;try{Files.createDirectories(target().getParent());
        Path base=directory().resolve(texture+".png");
        if(Files.isRegularFile(base)){byte[] original=Files.readAllBytes(base);if(original.length>=24){var header=java.nio.ByteBuffer.wrap(original);if(header.getInt(16)!=header.getInt(20)){var prepared=me.cryo.zombierool.maptexture.MapTexturePixels.read(directory(),texture);for(int i=1;i<prepared.length;i++){String convertedFace=me.cryo.zombierool.maptexture.MapTexturePixels.FACES[i];if(!convertedFace.equals("north"))Files.write(directory().resolve(texture+"_"+convertedFace+".png"),prepared[i]);}}}}
        remember();for(var entry:drafts.entrySet()){entry.getValue().writeToFile(directory().resolve(texture+(entry.getKey().equals("side")?"":"_"+entry.getKey())+".png"));if(entry.getKey().equals("side"))entry.getValue().writeToFile(directory().resolve(texture+"_side.png"));}if(!Files.exists(directory().resolve(texture+".png")))pixels.writeToFile(directory().resolve(texture+".png"));refreshSlots();status=Component.translatable("gui.zombierool.texture_saved");}catch(Exception e){status=Component.translatable("gui.zombierool.texture_error");}}
    private void importAtlas(){
        if(!acceptName())return;
        String name=importName.getValue();if(!name.matches("[A-Za-z0-9_-]+\\.png")){status=Component.translatable("gui.zombierool.texture_error");return;}
        Path sourcePath=directory().resolve("import").resolve(name);
        try{if(Files.size(sourcePath)>1024*1024)throw new IllegalArgumentException();
            var faces=headImport?me.cryo.zombierool.maptexture.TextureAtlasImport.convertHead(Files.readAllBytes(sourcePath),size):me.cryo.zombierool.maptexture.TextureAtlasImport.convert(Files.readAllBytes(sourcePath),size);
            Files.createDirectories(target().getParent());
            for(String convertedFace:me.cryo.zombierool.maptexture.MapTexturePixels.FACES){String suffix=convertedFace.equals("side")||convertedFace.equals("north")?"":"_"+convertedFace;var image=faces.getOrDefault(suffix,faces.get(""));javax.imageio.ImageIO.write(image,"png",directory().resolve(texture+(convertedFace.equals("side")?"":"_"+convertedFace)+".png").toFile());if(convertedFace.equals("side"))javax.imageio.ImageIO.write(image,"png",directory().resolve(texture+"_side.png").toFile());}
            clearDrafts();load();refreshSlots();status=Component.translatable("gui.zombierool.texture_saved");
        }catch(Exception e){status=Component.translatable("gui.zombierool.texture_error");}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){g.pose().pushPose();g.pose().scale(uiScale,uiScale,1);mx=(int)(mx/uiScale);my=(int)(my/uiScale);renderBackground(g);super.render(g,mx,my,delta);g.drawString(font,title,originX,16,0xFFFFFF);for(int y=0;y<size;y++)for(int x=0;x<size;x++){int p=pixels.getPixelRGBA(x,y);int argb=(p&0xFF00FF00)|((p&255)<<16)|((p>>16)&255);g.fill(originX+x*scale,originY+y*scale,originX+(x+1)*scale,originY+(y+1)*scale,((x+y)%2==0)?0xFF777777:0xFFAAAAAA);g.fill(originX+x*scale,originY+y*scale,originX+(x+1)*scale,originY+(y+1)*scale,argb);}g.drawString(font,status,originX,height-42,0xFFFFFF);g.pose().popPose();}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void removed(){pixels.close();clearDrafts();}
    @Override public boolean isPauseScreen(){return false;}
}
