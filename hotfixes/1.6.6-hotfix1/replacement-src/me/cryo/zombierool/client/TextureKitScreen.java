package me.cryo.zombierool.client;

import me.cryo.zombierool.maptexture.MapTextures;
import me.cryo.zombierool.hotfix.HotfixTextureFolders;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

public class TextureKitScreen extends AbstractContainerScreen<MapTextures.TextureKitMenu> {
    private final List<Integer> shown=new ArrayList<>();
    private final List<String> folders=new ArrayList<>();
    private int selected=-1,page;
    private String folder="",selectionWorld;
    private EditBox destination;
    private boolean restored;
    private float uiScale=1;
    private int dragSlot=-1;
    private double dragX,dragY;
    private boolean dragging;
    private static final int PAGE_SIZE=8;
    public TextureKitScreen(MapTextures.TextureKitMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);imageWidth=600;imageHeight=366;inventoryLabelY=10000;
    }
    private static String parent(String name){int slash=name.lastIndexOf('/');return slash<0?"":name.substring(0,slash);}
    @Override protected void init(){
        String typedPath=destination==null?"":destination.getValue();
        int realWidth=minecraft.getWindow().getGuiScaledWidth(),realHeight=minecraft.getWindow().getGuiScaledHeight();
        uiScale=Math.min(1,Math.min((realWidth-16f)/imageWidth,(realHeight-16f)/imageHeight));
        width=(int)(realWidth/uiScale);height=(int)(realHeight/uiScale);
        super.init();
        if(!restored){selectionWorld=TextureKitSelection.worldKey();selected=menu.preferredSlot>=0?menu.preferredSlot:menu.names.indexOf(TextureKitSelection.texture(selectionWorld));if(selected>=menu.names.size())selected=-1;if(menu.preferredFolder!=null)folder=menu.preferredFolder;else if(selected>=0)folder=parent(menu.names.get(selected));restored=true;remember();}
        shown.clear();folders.clear();var dirs=new TreeSet<String>();
        for(int slot=0;slot<menu.names.size();slot++){
            String name=menu.names.get(slot);if(name==null||name.isEmpty())continue;
            if(parent(name).equals(folder))shown.add(slot);
            String prefix=folder.isEmpty()?"":folder+"/";
            if(name.startsWith(prefix)){String rest=name.substring(prefix.length());int slash=rest.indexOf('/');if(slash>=0)dirs.add(prefix+rest.substring(0,slash));}
        }
        if(MapTextureClient.worldRoot()!=null){var root=MapTextureClient.worldRoot().resolve("zombierool/custom_blocks").resolve(folder);try(var children=java.nio.file.Files.list(root)){children.filter(java.nio.file.Files::isDirectory).filter(p->!p.getFileName().toString().equals("import")).forEach(p->dirs.add((folder.isEmpty()?"":folder+"/")+p.getFileName()));}catch(java.io.IOException ignored){}}
        folders.addAll(dirs);
        String prefix=folder.isEmpty()?"":folder+"/";
        for(String path:menu.folders)if(path.startsWith(prefix)){String rest=path.substring(prefix.length());if(!rest.isEmpty())dirs.add(prefix+rest.split("/")[0]);}
        folders.clear();folders.addAll(dirs);
        if(selected<0&&!shown.isEmpty())selected=shown.get(0);
        page=Math.max(0,Math.min(page,pages()-1));
        addRenderableWidget(Button.builder(Component.translatable("message.zombierool.maptex.apply"),b->MapTextureClient.requestRefresh()).bounds(leftPos+438,topPos+6,154,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.texture_kit.parent"),b->{folder=parent(folder);page=0;rebuild();}).bounds(leftPos+8,topPos+43,62,18).build()).active=!folder.isEmpty();
        int first=page*PAGE_SIZE;
        for(int row=0;row<PAGE_SIZE&&first+row<folders.size()+shown.size();row++){
            int entry=first+row;
            if(entry<folders.size()){
                String path=folders.get(entry);
                addRenderableWidget(Button.builder(Component.literal("[+] "+MapTextures.humanize(path)),b->{folder=path;page=0;rebuild();}).bounds(leftPos+8,topPos+67+row*20,186,18).build());
            }else{
                int slot=shown.get(entry-folders.size());
                addRenderableWidget(Button.builder(Component.literal((slot==selected?"> ":"  ")+MapTextures.humanize(menu.names.get(slot))),b->{selected=slot;remember();rebuild();}).bounds(leftPos+8,topPos+67+row*20,186,18).build());
            }
        }
        var previous=addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;rebuild();}).bounds(leftPos+8,topPos+231,24,18).build());previous.active=page>0;
        var next=addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;rebuild();}).bounds(leftPos+170,topPos+231,24,18).build());next.active=page+1<pages();
        for(int shape=0;shape<MapTextures.SHAPES.length;shape++){
            int id=shape;var button=addRenderableWidget(Button.builder(Component.translatable("shape.zombierool."+MapTextures.SHAPES[shape]),b->give(id)).bounds(leftPos+210+(shape%4)*96,topPos+66+(shape/4)*24,92,20).build());button.active=selected>=0;
        }
        if(MapTextureClient.worldRoot()!=null)addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.texture_editor"),b->minecraft.setScreen(new TexturePixelEditor(this,selected>=0?menu.names.get(selected):"new_texture"))).bounds(leftPos+210,topPos+120,376,20).build());
        for(int i=0;i<MapTextures.SOUND_IDS.length;i++){
            int sound=i;String id=MapTextures.SOUND_IDS[i];Component label=Component.translatable("sound.zombierool."+id);if(id.equals(currentSound()))label=Component.literal("> ").append(label);
            var button=addRenderableWidget(Button.builder(label,b->pickSound(sound)).bounds(leftPos+210+(i%5)*76,topPos+167+(i/5)*16,73,15).build());button.active=selected>=0;
        }
        destination=new EditBox(font,leftPos+8,topPos+281,186,18,Component.translatable("gui.zombierool.texture_kit.folder"));destination.setMaxLength(128);destination.setHint(Component.translatable("gui.zombierool.texture_kit.folder_hint"));destination.setValue(typedPath);addRenderableWidget(destination);
        addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.texture_kit.new_folder"),b->folderAction(false)).bounds(leftPos+8,topPos+306,90,20).build());
        var move=addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.texture_kit.move"),b->folderAction(true)).bounds(leftPos+104,topPos+306,90,20).build());move.active=selected>=0;
        addRenderableWidget(Button.builder(Component.literal("?"),b->{}).bounds(leftPos+570,topPos+30,20,20).tooltip(Tooltip.create(Component.translatable("gui.zombierool.texture_kit.helper"))).build());
    }
    private void folderAction(boolean move){String path=destination.getValue().trim();if(path.isEmpty()){if(!move)return;path=folder.isEmpty()?"/":folder;}HotfixTextureFolders.CHANNEL.sendToServer(new HotfixTextureFolders.Request(move?1:0,selected,path));}
    private int listEntryAt(double x,double y){if(x<leftPos+8||x>=leftPos+194||y<topPos+67)return -1;int row=(int)((y-topPos-67)/20);if(row>=PAGE_SIZE||y>=topPos+67+row*20+18)return -1;int entry=page*PAGE_SIZE+row;return entry<folders.size()+shown.size()?entry:-1;}
    private String dropFolderAt(double x,double y){if(x>=leftPos+8&&x<leftPos+70&&y>=topPos+43&&y<topPos+61&&!folder.isEmpty())return parent(folder);int entry=listEntryAt(x,y);if(entry>=0&&entry<folders.size())return folders.get(entry);if(x>=leftPos+8&&x<leftPos+428&&y>=topPos+28&&y<topPos+41)return folder;return null;}
    private int pages(){return Math.max(1,(shown.size()+folders.size()+PAGE_SIZE-1)/PAGE_SIZE);}
    private void rebuild(){clearWidgets();init();}
    private void remember(){if(selected>=0&&selectionWorld!=null)TextureKitSelection.save(selectionWorld,menu.names.get(selected),page);}
    private String currentSound(){return selected>=0&&selected<menu.sounds.size()?menu.sounds.get(selected):"stone";}
    private void give(int shape){if(selected>=0)HotfixTextureFolders.CHANNEL.sendToServer(new HotfixTextureFolders.Request(3,selected,Integer.toString(shape)));}
    private void pickSound(int index){if(selected<0)return;while(menu.sounds.size()<=selected)menu.sounds.add("stone");menu.sounds.set(selected,MapTextures.SOUND_IDS[index]);HotfixTextureFolders.CHANNEL.sendToServer(new HotfixTextureFolders.Request(2,selected,Integer.toString(index)));rebuild();}
    @Override public boolean mouseClicked(double x,double y,int button){x/=uiScale;y/=uiScale;if(button==0){int entry=listEntryAt(x,y);dragSlot=entry>=folders.size()&&entry>=0?shown.get(entry-folders.size()):-1;dragX=x;dragY=y;dragging=false;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&dragSlot>=0){int slot=dragSlot;boolean moved=dragging;dragSlot=-1;dragging=false;if(moved){setDragging(false);setFocused(null);String target=dropFolderAt(x/uiScale,y/uiScale);if(target!=null)HotfixTextureFolders.CHANNEL.sendToServer(new HotfixTextureFolders.Request(1,slot,target.isEmpty()?"/":target));return true;}}return super.mouseReleased(x/uiScale,y/uiScale,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&dragSlot>=0){double rx=x/uiScale-dragX,ry=y/uiScale-dragY;if(rx*rx+ry*ry>=16)dragging=true;return true;}return super.mouseDragged(x/uiScale,y/uiScale,button,dx/uiScale,dy/uiScale);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(destination!=null&&destination.isFocused()&&key!=org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE){destination.keyPressed(key,scan,modifiers);return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseScrolled(double x,double y,double delta){x/=uiScale;y/=uiScale;if(x>=leftPos+8&&x<leftPos+196&&y>=topPos+66&&y<topPos+250){page=Math.max(0,Math.min(pages()-1,page-(int)Math.signum(delta)));rebuild();return true;}return super.mouseScrolled(x,y,delta);}
    @Override protected void renderBg(GuiGraphics g,float tick,int x,int y){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xF0101010);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+4,0xFF6B2D3C);
        g.fill(leftPos+6,topPos+63,leftPos+198,topPos+252,0xFF202020);g.fill(leftPos+205,topPos+63,leftPos+592,topPos+263,0xFF202020);
        for(var slot:menu.slots)g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xFF444444);
    }
    @Override public void render(GuiGraphics g,int x,int y,float tick){g.pose().pushPose();g.pose().scale(uiScale,uiScale,1);x=(int)(x/uiScale);y=(int)(y/uiScale);renderBackground(g);super.render(g,x,y,tick);
        g.drawString(font,title,leftPos+8,topPos+10,0xFFFFFF,false);g.drawString(font,font.plainSubstrByWidth(folder.isEmpty()?"/":"/"+folder,420),leftPos+8,topPos+30,0xAAAAAA,false);
        g.drawString(font,Component.translatable("message.zombierool.maptex.shape"),leftPos+210,topPos+47,0xE0B080,false);
        g.drawString(font,Component.translatable("message.zombierool.maptex.sound_for"),leftPos+210,topPos+151,0xE0B080,false);
        g.drawCenteredString(font,Component.translatable("message.zombierool.maptex.page",page+1,pages()),leftPos+100,topPos+235,0xFFFFFF);
        g.drawString(font,Component.translatable("gui.zombierool.texture_kit.folder"),leftPos+8,topPos+264,0xE0B080,false);
        g.drawString(font,Component.translatable("container.inventory"),leftPos+220,topPos+267,0xAAAAAA,false);
        if(selected>=0)g.drawString(font,font.plainSubstrByWidth(Component.translatable("gui.zombierool.texture_kit.selected",MapTextures.humanize(menu.names.get(selected))).getString(),186),leftPos+8,topPos+338,0xAAAAAA,false);
        if(dragging&&dragSlot>=0){String target=dropFolderAt(x,y);if(target!=null){int entry=listEntryAt(x,y);int row=entry-page*PAGE_SIZE;int bx=leftPos+8,by=entry>=0?topPos+67+row*20:y>=topPos+43?topPos+43:topPos+28;int bw=entry>=0?186:y>=topPos+43?62:420;g.renderOutline(bx,by,bw,18,0xFF66DD88);}String label=MapTextures.humanize(menu.names.get(dragSlot));int tw=font.width(label);g.fill(x+10,y-2,x+tw+20,y+13,0xEE101010);g.drawString(font,label,x+15,y+2,target==null?0xFFFFFF:0x66DD88,false);}else renderTooltip(g,x,y);g.pose().popPose();
    }
    @Override public void removed(){remember();super.removed();MapTextureClient.requestRefresh();}
    @Override public boolean isPauseScreen(){return false;}
}
