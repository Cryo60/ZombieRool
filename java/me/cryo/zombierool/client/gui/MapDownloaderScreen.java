package me.cryo.zombierool.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import me.cryo.zombierool.client.*;
import me.cryo.zombierool.configuration.ZRClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.google.gson.JsonParser;

/** Responsive discovery shelves and accessible thumbnail cards. No map installation is performed here. */
public class MapDownloaderScreen extends Screen {
    static final int BG=0xFF101218,PANEL=0xFF1B202A,TEXT=0xFFF1F4FA,MUTED=0xFF9DA9BC,GREEN=0xFF8EE8A4,GOLD=0xFFFFD580;
    private final Screen parent;
    final List<MapCatalogue.Entry> maps=new ArrayList<>();
    private final List<MapCard> cards=new ArrayList<>();
    private final List<Heading> headings=new ArrayList<>();
    private final Map<String,ResourceLocation> textures=new HashMap<>();
    private final Set<String> imageRequests=new HashSet<>();
    private final List<String> failures=new ArrayList<>();
    private EditBox search;
    private boolean requested,openingChild,disposed,cached;
    private int tab=1;
    private int pending,sort,viewportTop=93,viewportBottom,contentHeight,cols,cardW,cardH,generation;
    private double scroll;
    private String query="";
    private record Heading(String text,int y){}
    public MapDownloaderScreen(Screen parent){super(tr("title"));this.parent=parent;}
    static Component tr(String key,Object...args){return Component.translatable("gui.zombierool.catalog."+key,args);}
    static String label(String key,Object...args){return tr(key,args).getString();}
    public static boolean validMapLink(String value){return MapCommunityClient.validMapLink(value);}
    static String modVersion(){return net.minecraftforge.fml.ModList.get().getModContainerById("zombierool").map(m->m.getModInfo().getVersion().toString()).orElse("26.7");}
    static int compatibility(MapCatalogue.Entry map){return MapCatalogue.compatibility(map.zrVersion,modVersion());}
    static String compatibilityText(MapCatalogue.Entry map){return label(switch(compatibility(map)){case -1->"outdated";case 0->"compatible";case 1->"newer_mod";default->"unknown_version";},map.zrVersion);}
    @Override protected void init(){
        openingChild=false;disposed=false;viewportBottom=height-34;
        int margin=16,searchW=Math.min(230,Math.max(110,width/3));
        search=addRenderableWidget(new EditBox(font,width-margin-searchW,15,searchW,20,tr("search")));
        search.setHint(tr("search"));search.setValue(query);search.setResponder(value->{query=value;rebuild(true);});
        int tw=Math.min(100,(width-40)/5);
        String[] tabs={"discover","official","community","installed_tab"};
        for(int i=0;i<tabs.length;i++){final int index=i;var b=addRenderableWidget(Button.builder(tr(tabs[i]),button->{tab=index;rebuild(true);}).bounds(margin+i*(tw+4),49,tw,20).build());b.setTooltip(Tooltip.create(tr(tabs[i]+"_hint")));}
        int sw=Math.min(116,width-margin-(margin+4*(tw+4)));
        addRenderableWidget(Button.builder(tr("sort_"+sort),b->{sort=(sort+1)%3;b.setMessage(tr("sort_"+sort));rebuild(true);}).bounds(width-margin-sw,49,sw,20).build());
        int bw=Math.min(112,(width-44)/4);
        addRenderableWidget(Button.builder(tr("publish"),b->net.minecraft.Util.getPlatform().openUri(MapCommunityClient.SERVICE)).bounds(16,height-25,bw,20).build());
        addRenderableWidget(Button.builder(tr("launcher"),b->net.minecraft.Util.getPlatform().openUri("https://github.com/Cryo60/ZR-Launcher/releases")).bounds(20+bw,height-25,bw,20).build());
        addRenderableWidget(Button.builder(tr("refresh"),b->{if(pending==0){generation++;for(var loc:textures.values())minecraft.getTextureManager().release(loc);textures.clear();imageRequests.clear();maps.clear();requested=false;cached=false;failures.clear();rebuild(true);startLoading();}}).bounds(width-20-bw*2,height-25,bw,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(width-16-bw,height-25,bw,20).build());
        rebuild(false);if(!requested)startLoading();
    }
    private void startLoading(){
        requested=true;pending=2;int ticket=generation;
        for(String source:List.of("official","community"))MapCommunityClient.async(()->MapCommunityClient.load(source)).whenComplete((result,error)->minecraft.execute(()->{
            if(disposed||ticket!=generation)return;pending--;
            if(error!=null)failures.add(source);else{cached|=result.cached();maps.addAll(result.entries());for(var map:result.entries())installed(map);}
            rebuild(false);
        }));
    }
    private void installed(MapCatalogue.Entry map){
        Path saves=minecraft.gameDirectory.toPath().resolve("saves").toAbsolutePath().normalize();
        try{
            Path dir=saves.resolve(map.name).normalize();if(!dir.startsWith(saves)||dir.equals(saves)||!Files.isDirectory(dir))return;
            map.installed=1;Path manifest=dir.resolve("zombierool_map.json");
            if(Files.isRegularFile(manifest)&&Files.size(manifest)<65536){var json=JsonParser.parseString(Files.readString(manifest,StandardCharsets.UTF_8)).getAsJsonObject();String version=MapCatalogue.text(json,"version","");if(!version.isBlank()&&!map.version.equalsIgnoreCase(version))map.installed=2;}
        }catch(Exception ignored){}
    }
    private void loadImage(MapCatalogue.Entry map){
        if(map.imageUrl.isBlank()||!ZRClientConfig.allowNetworkRequests()||!imageRequests.add(map.key()))return;
        int ticket=generation;
        MapCommunityClient.async(()->{
            byte[] data=MapCommunityClient.image(map.imageUrl);
            var thumbnail=CatalogueImageDecoder.decode(data);
            try{
                var nativeImage=new NativeImage(thumbnail.getWidth(),thumbnail.getHeight(),true);
                try{for(int y=0;y<thumbnail.getHeight();y++)for(int x=0;x<thumbnail.getWidth();x++)nativeImage.setPixelRGBA(x,y,CatalogueImageDecoder.abgr(thumbnail.getRGB(x,y)));return nativeImage;}
                catch(Throwable failure){nativeImage.close();throw failure;}
            }finally{thumbnail.flush();}
        }).whenComplete((image,error)->{if(error!=null){me.cryo.zombierool.ZombieroolMod.LOGGER.warn("[Catalogue] Thumbnail failed for {}: {}",map.id,error.toString());return;}minecraft.execute(()->{if(disposed||ticket!=generation){image.close();return;}var texture=new DynamicTexture(image);var loc=minecraft.getTextureManager().register("zr_catalogue",texture);var old=textures.put(map.key(),loc);if(old!=null)minecraft.getTextureManager().release(old);});});
    }
    void reviewsChanged(){rebuild(false);}
    private List<MapCatalogue.Entry> filtered(){return maps.stream().filter(m->(tab!=1||m.source.equals("official"))&&(tab!=2||m.source.equals("community"))&&(tab!=3||m.installed>0)&&m.matches(query)).toList();}
    private Comparator<MapCatalogue.Entry> order(){return switch(sort){case 1->Comparator.<MapCatalogue.Entry>comparingInt(m->m.downloads).reversed().thenComparing(MapCatalogue.Entry::key);case 2->Comparator.<MapCatalogue.Entry>comparingLong(m->m.timestamp).reversed().thenComparing(MapCatalogue.Entry::key);default->Comparator.comparingDouble(MapCatalogue.Entry::ratingScore).reversed().thenComparing(MapCatalogue.Entry::key);};}
    private void rebuild(boolean reset){
        for(var card:cards)removeWidget(card);cards.clear();headings.clear();
        cols=Math.max(2,Math.min(6,(width-32)/152));cardW=(width-32-(cols-1)*10)/cols;cardH=cardW*9/16+55;
        var entries=new ArrayList<>(filtered());int y=0;
        if(tab==0&&query.isBlank()){
            y=shelf("featured",MapCatalogue.featured(entries,modVersion(),System.currentTimeMillis(),cols),y);
            y=shelf("popular",entries.stream().sorted(Comparator.<MapCatalogue.Entry>comparingInt(e->e.downloads).reversed().thenComparing(MapCatalogue.Entry::key)).limit(cols).toList(),y);
            y=shelf("new",entries.stream().filter(e->e.timestamp>0).sorted(Comparator.<MapCatalogue.Entry>comparingLong(e->e.timestamp).reversed().thenComparing(MapCatalogue.Entry::key)).limit(cols).toList(),y);
        }
        entries.sort(order());y=shelf(query.isBlank()?switch(tab){case 1->"official_maps";case 2->"community_maps";case 3->"installed_maps";default->"all";}:"all",entries,y);contentHeight=y;scroll=reset?0:Math.min(scroll,maxScroll());positionCards();
    }
    private int shelf(String key,List<MapCatalogue.Entry> entries,int y){
        if(entries.isEmpty())return y;headings.add(new Heading(label(key),y));y+=22;
        for(int i=0;i<entries.size();i++){var card=new MapCard(entries.get(i),16+(i%cols)*(cardW+10),y+(i/cols)*(cardH+10));cards.add(card);addWidget(card);}
        return y+((entries.size()+cols-1)/cols)*(cardH+10)+12;
    }
    private double maxScroll(){return Math.max(0,contentHeight-(viewportBottom-viewportTop));}
    private void positionCards(){for(var card:cards){card.setY(viewportTop+card.relativeY-(int)scroll);card.visible=card.getY()+card.getHeight()>viewportTop&&card.getY()<viewportBottom;if(card.visible)loadImage(card.map);}}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(y>=viewportTop&&y<viewportBottom){scroll=Math.max(0,Math.min(maxScroll(),scroll-delta*36));positionCards();return true;}return super.mouseScrolled(x,y,delta);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(!search.isFocused()&&(key==266||key==267||key==268||key==269)){scroll=key==268?0:key==269?maxScroll():Math.max(0,Math.min(maxScroll(),scroll+(key==267?1:-1)*(viewportBottom-viewportTop)));positionCards();return true;}
        boolean result=super.keyPressed(key,scan,mods);if(getFocused() instanceof MapCard card){if(card.getY()<viewportTop)scroll=Math.max(0,card.relativeY);else if(card.getY()+cardH>viewportBottom)scroll=Math.min(maxScroll(),card.relativeY+cardH-(viewportBottom-viewportTop));positionCards();}return result;
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,BG);g.fill(0,0,width,77,0xFF151922);
        g.drawString(font,tr("title"),16,13,TEXT);g.drawString(font,font.plainSubstrByWidth(label("subtitle"),Math.max(1,search.getX()-28)),16,29,MUTED);
        for(int i=0;i<4;i++)if(i==tab){int tw=Math.min(100,(width-40)/5);g.fill(16+i*(tw+4),71,16+i*(tw+4)+tw,73,GREEN);}
        String status=pending>0?label("loading"):!failures.isEmpty()?label("partial"):cached?label("cached"):label("count",filtered().size());
        g.drawString(font,font.plainSubstrByWidth(status,width-32),16,80,pending>0?MUTED:failures.isEmpty()?MUTED:GOLD);
        g.enableScissor(8,viewportTop,width-8,viewportBottom);
        for(var heading:headings){int y=viewportTop+heading.y-(int)scroll;if(y>viewportTop-14&&y<viewportBottom)g.drawString(font,heading.text,16,y,TEXT);}
        for(var card:cards)if(card.visible)card.render(g,mx,my,partial);
        if(cards.isEmpty()&&pending==0){String key=!ZRClientConfig.allowNetworkRequests()?"network_disabled":!failures.isEmpty()?"unavailable":"empty";g.drawCenteredString(font,tr(key),width/2,viewportTop+35,MUTED);}
        g.disableScissor();
        if(maxScroll()>0){int h=viewportBottom-viewportTop,thumb=Math.max(20,(int)(h*h/(double)contentHeight)),sy=viewportTop+(int)((h-thumb)*scroll/maxScroll());g.fill(width-5,sy,width-3,sy+thumb,0xFF556276);}
        g.fill(0,height-31,width,height,BG);super.render(g,mx,my,partial);
    }
    private final class MapCard extends Button {
        final MapCatalogue.Entry map;final int relativeY;
        MapCard(MapCatalogue.Entry map,int x,int y){super(x,viewportTop+y-(int)scroll,cardW,cardH,Component.literal(map.name+". "+compatibilityText(map)),b->open(map),DEFAULT_NARRATION);this.map=map;relativeY=y;}
        @Override protected boolean clicked(double x,double y){return y>=viewportTop&&y<viewportBottom&&super.clicked(x,y);}
        @Override public boolean isMouseOver(double x,double y){return y>=viewportTop&&y<viewportBottom&&super.isMouseOver(x,y);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            int x=getX(),y=getY(),w=getWidth(),thumb=w*9/16;boolean hover=isHoveredOrFocused();
            g.fill(x,y,x+w,y+getHeight(),hover?0xFF293344:PANEL);
            thumbnail(g,map,x+2,y+2,w-4,thumb-2);
            g.fill(x+2,y+thumb,x+w-2,y+thumb+1,0xFF46536A);
            g.renderOutline(x,y,w,getHeight(),hover?GREEN:0xFF56647A);
            if(hover){g.renderOutline(x+1,y+1,w-2,getHeight()-2,0xFF3F735B);}
            g.fill(x,y,x+14,y+2,hover?GREEN:0xFF8497B5);
            g.fill(x+w-14,y+getHeight()-2,x+w,y+getHeight(),hover?GREEN:0xFF8497B5);
            String badge=map.installed==2?label("update"):map.installed==1?label("installed"):label(map.source);
            int badgeW=Math.min(w-10,font.width(badge)+10);g.fill(x+5,y+5,x+5+badgeW,y+18,0xDD10141B);g.drawString(font,font.plainSubstrByWidth(badge,badgeW-8),x+9,y+8,0xFFF4F7FA);
            g.drawString(font,font.plainSubstrByWidth(map.name,w-14),x+7,y+thumb+7,TEXT);
            g.drawString(font,font.plainSubstrByWidth(map.author,w-14),x+7,y+thumb+19,MUTED);
            String rating=map.reviews.isEmpty()?label("no_rating"):String.format(Locale.ROOT,"%.1f ★ (%d)",map.average(),map.reviews.size());
            g.drawString(font,font.plainSubstrByWidth(rating,w-50),x+7,y+thumb+32,GOLD);
            String dl=compact(map.downloads)+" ↓";g.drawString(font,dl,x+w-7-font.width(dl),y+thumb+32,MUTED);
            int c=compatibility(map);if(c!=0)g.drawString(font,font.plainSubstrByWidth(label(c==-1?"outdated_badge":c==1?"newer_badge":"unknown_badge"),w-12),x+6,y+thumb-13,c==-1?GOLD:MUTED);
        }
    }
    private void open(MapCatalogue.Entry map){openingChild=true;minecraft.setScreen(new MapDetailsScreen(this,map));}
    void thumbnail(GuiGraphics g,MapCatalogue.Entry map,int x,int y,int w,int h){
        ResourceLocation loc=textures.get(map.key());
        if(loc!=null){g.blit(loc,x,y,0,0,w,h,w,h);return;}
        int hash=map.key().hashCode(),r=24+(hash&31),green=33+((hash>>>5)&31),blue=45+((hash>>>10)&31),color=0xFF000000|(r<<16)|(green<<8)|blue;
        g.fillGradient(x,y,x+w,y+h,color,0xFF111720);
        for(int i=0;i<5;i++){int bx=x+(i*w/5),bh=h/4+Math.floorMod(hash+i*3741,Math.max(1,h/2));g.fill(bx,y+h-bh,bx+w/7,y+h,0x44232D3A);}
        var stack=g.pose();stack.pushPose();stack.translate(x+12,y+h/2.0-6,0);stack.scale(1.4f,1.4f,1);g.drawString(font,font.plainSubstrByWidth(map.name,Math.max(1,(int)((w-24)/1.4f))),0,0,0xFFD3E2F0);stack.popPose();
    }
    static String compact(int number){return number>=1000000?String.format(Locale.ROOT,"%.1fM",number/1000000f):number>=1000?String.format(Locale.ROOT,"%.1fk",number/1000f):Integer.toString(number);}
    @Override public void onClose(){openingChild=false;minecraft.setScreen(parent);}
    @Override public void removed(){if(!openingChild){disposed=true;generation++;for(var loc:textures.values())minecraft.getTextureManager().release(loc);textures.clear();imageRequests.clear();}}
}
