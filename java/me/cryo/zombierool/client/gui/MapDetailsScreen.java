package me.cryo.zombierool.client.gui;

import me.cryo.zombierool.client.*;
import me.cryo.zombierool.configuration.ZRClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static me.cryo.zombierool.client.gui.MapDownloaderScreen.*;

/** Map information, pageless review reading and native Minecraft review editor. */
public final class MapDetailsScreen extends Screen {
    private final MapDownloaderScreen parent;
    private final MapCatalogue.Entry map;
    private MultiLineEditBox comment;
    private Button submit;
    private final List<Button> starButtons=new ArrayList<>();
    private boolean loaded,busy,dirty,showReviews;
    private int stars,leftW,rightX,rightW,top=52,reviewBottom,editorY,leftContentHeight,reviewContentHeight;
    private double detailsScroll,reviewScroll;
    private String draft="",status="";
    private final List<DependencyLink> dependencyLinks=new ArrayList<>();
    private record DependencyLink(int x,int y,int w,int h,String url){boolean contains(double mx,double my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}}
    private static void frame(GuiGraphics g,int x,int y,int w,int h){
        g.fill(x,y,x+w,y+h,0xFF171C25);g.renderOutline(x,y,w,h,0xFF4C5C74);
        g.fill(x,y,x+Math.min(22,w),y+2,0xFF8A9DBB);
    }
    public MapDetailsScreen(MapDownloaderScreen parent,MapCatalogue.Entry map){super(Component.literal(map.name));this.parent=parent;this.map=map;prefill();}
    private boolean compact(){return width<520;}
    private void prefill(){if(dirty)return;map.reviews.stream().filter(r->r.userId().equals(MapCommunityClient.ownId())).findFirst().ifPresent(r->{stars=r.stars();draft=r.comment();});}
    @Override protected void init(){
        starButtons.clear();leftW=compact()?width-32:(width-48)*44/100;rightX=compact()?16:32+leftW;rightW=width-rightX-16;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(width-80,13,64,20).build());
        if(compact()){
            addRenderableWidget(Button.builder(tr(showReviews?"details":"reviews"),b->{capture();showReviews=!showReviews;rebuildWidgets();}).bounds(16,37,100,20).build());top=65;
        }else top=52;
        addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.downloader.get_link"),b->{capture();minecraft.setScreen(new LinkScreen(this,map));}).bounds(16,height-26,Math.min(180,width/2-22),20).build()).active=validMapLink(map.downloadUrl);
        editorY=height-132;reviewBottom=editorY-(height>=330?30:8);
        for(int i=1;i<=5;i++){
            int value=i;var button=addRenderableWidget(Button.builder(Component.literal((stars>=i?"★ ":"☆ ")+i),b->{stars=value;dirty=true;updateStars();}).bounds(rightX+(i-1)*32,editorY,30,20).build());
            button.setTooltip(Tooltip.create(tr("stars",i)));button.visible=!compact()||showReviews;starButtons.add(button);
        }
        comment=addRenderableWidget(new MultiLineEditBox(font,rightX,editorY+24,rightW,39,tr("comment_hint"),tr("comment")));
        comment.setCharacterLimit(1000);comment.setValue(draft);comment.setValueListener(value->{draft=value;dirty=true;});comment.visible=!compact()||showReviews;
        submit=addRenderableWidget(Button.builder(tr("post"),b->post()).bounds(rightX,editorY+67,Math.min(170,rightW),20).build());submit.visible=!compact()||showReviews;
        updateStars();
        if(!loaded){loaded=true;status=ZRClientConfig.allowNetworkRequests()?"loading_reviews":"network_disabled";
            if(ZRClientConfig.allowNetworkRequests())MapCommunityClient.async(()->MapCommunityClient.reviews(map)).whenComplete((reviews,error)->minecraft.execute(()->{
                if(error!=null)status=MapCommunityClient.error(error);else{map.reviews=reviews;prefill();if(!dirty&&comment!=null)comment.setValue(draft);status="";parent.reviewsChanged();}updateStars();
            }));
        }
    }
    private void capture(){if(comment!=null)draft=comment.getValue();}
    private void updateStars(){
        for(int i=0;i<starButtons.size();i++){starButtons.get(i).setMessage(Component.literal((stars>i?"★ ":"☆ ")+(i+1)));starButtons.get(i).active=!busy;starButtons.get(i).setFGColor(stars>i?GOLD:MUTED);}
        if(submit!=null){submit.active=stars>0&&!busy&&ZRClientConfig.allowNetworkRequests();submit.setMessage(tr(busy?"posting":map.reviews.stream().anyMatch(r->r.userId().equals(MapCommunityClient.ownId()))?"update_review":"post"));}
        if(comment!=null)comment.active=!busy;
    }
    private void post(){
        capture();if(busy||stars<1)return;busy=true;status="verifying";updateStars();final int selected=stars;final String text=draft;
        MapCommunityClient.async(()->MapCommunityClient.submit(map,selected,text)).whenComplete((reviews,error)->minecraft.execute(()->{
            busy=false;status=error==null?"posted":MapCommunityClient.error(error);if(error==null){map.reviews=reviews;parent.reviewsChanged();reviewScroll=0;}updateStars();
        }));
    }
    @Override public void tick(){if(comment!=null)comment.tick();}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if((!compact()&&!showReviews&&x<rightX)||compact()&&!showReviews){detailsScroll=Math.max(0,Math.min(Math.max(0,leftContentHeight-(height-34-top)),detailsScroll-delta*24));return true;}
        if(y>=top+24&&y<reviewBottom&&x>=rightX){reviewScroll=Math.max(0,Math.min(Math.max(0,reviewContentHeight-(reviewBottom-top-24)),reviewScroll-delta*24));return true;}
        return super.mouseScrolled(x,y,delta);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,BG);g.fill(0,0,width,43,0xFF151922);g.fill(0,42,width,43,0xFF354257);
        g.drawString(font,font.plainSubstrByWidth(map.name,width-112),16,12,TEXT);
        g.drawString(font,font.plainSubstrByWidth(label("by",map.author),width-112),16,27,MUTED);
        dependencyLinks.clear();
        if(!compact()||!showReviews)renderDetails(g);
        if(!compact()||showReviews){
            frame(g,rightX-8,top-7,rightW+16,Math.max(25,reviewBottom-top+10));
            frame(g,rightX-8,editorY-(height>=330?25:6),rightW+16,height-33-(editorY-(height>=330?25:6)));
            if(height>=330)g.drawString(font,tr("your_review"),rightX,editorY-18,TEXT);
            renderReviews(g);
        }
        g.fill(0,height-32,width,height,BG);
        if(!compact()||showReviews){String account=label("posting_as",minecraft.getUser().getName());g.drawString(font,font.plainSubstrByWidth(account,rightW),rightX,height-43,MUTED);}
        super.render(g,mx,my,partial);
        if((!compact()||showReviews)&&!status.isEmpty())g.drawString(font,font.plainSubstrByWidth(label(status),rightW),rightX,height-23,status.equals("posted")?GREEN:GOLD);
        for(var link:dependencyLinks)if(my>=top&&my<height-34&&link.contains(mx,my))g.renderTooltip(font,Component.literal(link.url()),mx,my);
    }
    private int wrap(GuiGraphics g,Component text,int x,int y,int w,int color){for(var line:font.split(text,Math.max(1,w))){g.drawString(font,line,x,y,color);y+=11;}return y;}
    private void renderDetails(GuiGraphics g){
        int x=16,w=leftW,y=top-(int)detailsScroll,origin=y,pad=10,inner=w-pad*2;
        g.enableScissor(x,top,x+w,height-34);
        int imageH=w*9/16;frame(g,x,y,w,imageH);parent.thumbnail(g,map,x+2,y+2,w-4,imageH-4);g.renderOutline(x,y,w,imageH,0xFF596D89);y+=imageH+10;
        var info=List.of(Component.literal(compatibilityText(map)),tr("versions",map.gameVersion,map.version),tr(map.installed==2?"update":map.installed==1?"installed":"not_installed"));
        int infoH=32;for(var line:info)infoH+=font.split(line,inner).size()*11+6;
        frame(g,x,y,w,infoH);g.drawString(font,tr("map_info"),x+pad,y+10,TEXT);int row=y+27;
        row=wrap(g,info.get(0),x+pad,row,inner,compatibility(map)==0?GREEN:GOLD)+6;
        row=wrap(g,info.get(1),x+pad,row,inner,MUTED)+6;wrap(g,info.get(2),x+pad,row,inner,map.installed>0?GREEN:MUTED);y+=infoH+10;
        Component description=Component.literal(map.description.isBlank()?label("no_description"):map.description);
        int descH=font.split(description,inner).size()*11,tagRows=0,tagX=0;
        for(String tag:map.tags){int tw=Math.min(inner,font.width(tag)+12);if(tagX>0&&tagX+tw>inner){tagRows++;tagX=0;}tagX+=tw+5;}
        if(!map.tags.isEmpty())tagRows++;
        int aboutH=36+descH+(tagRows>0?8+tagRows*18:0);
        frame(g,x,y,w,aboutH);g.drawString(font,tr("about"),x+pad,y+10,TEXT);
        int tagsY=wrap(g,description,x+pad,y+27,inner,TEXT)+8;tagX=0;
        for(String tag:map.tags){int tw=Math.min(inner,font.width(tag)+12);if(tagX>0&&tagX+tw>inner){tagsY+=18;tagX=0;}
            g.fill(x+pad+tagX,tagsY,x+pad+tagX+tw,tagsY+14,0xFF253C36);g.renderOutline(x+pad+tagX,tagsY,tw,14,0xFF416958);
            g.drawString(font,font.plainSubstrByWidth(tag,Math.max(1,tw-12)),x+pad+tagX+6,tagsY+3,GREEN);tagX+=tw+5;
        }
        y+=aboutH+10;
        int dependenciesH=34;
        if(map.mods.isEmpty()&&map.gunpacks.isEmpty())dependenciesH+=font.split(tr("no_dependencies"),inner).size()*11;
        if(!map.mods.isEmpty()){dependenciesH+=16;for(String mod:map.mods)dependenciesH+=font.split(Component.literal(dependencyName(mod)),inner-10).size()*11+6;}
        if(!map.gunpacks.isEmpty()){dependenciesH+=16;for(String pack:map.gunpacks)dependenciesH+=font.split(Component.literal(dependencyName(pack)),inner-10).size()*11+6;}
        frame(g,x,y,w,dependenciesH);g.drawString(font,tr("dependencies"),x+pad,y+10,TEXT);row=y+27;
        if(map.mods.isEmpty()&&map.gunpacks.isEmpty())wrap(g,tr("no_dependencies"),x+pad,row,inner,MUTED);
        if(!map.mods.isEmpty()){g.drawString(font,tr("mods"),x+pad,row,MUTED);row+=16;for(String mod:map.mods)row=dependency(g,mod,x+pad,row,inner);}
        if(!map.gunpacks.isEmpty()){g.drawString(font,tr("gunpacks"),x+pad,row,MUTED);row+=16;for(String pack:map.gunpacks)row=dependency(g,pack,x+pad,row,inner);}
        y+=dependenciesH;leftContentHeight=y-origin+8;g.disableScissor();
    }
    private int dependency(GuiGraphics g,String value,int x,int y,int w){
        boolean link=validMapLink(value);int end=wrap(g,Component.literal(dependencyName(value)),x+10,y,w-10,link?GREEN:TEXT);
        g.fill(x,y+3,x+3,y+6,0xFF7186A0);if(link)dependencyLinks.add(new DependencyLink(x,y,w,end-y,value));return end+6;
    }
    private static String dependencyName(String value){
        if(!validMapLink(value))return value;
        try{
            var uri=java.net.URI.create(value);var parts=Arrays.stream(uri.getPath().split("/")).filter(p->!p.isBlank()).toList();String name=uri.getHost();
            if(uri.getHost().equalsIgnoreCase("github.com")&&parts.size()>=2)return parts.get(0)+" / "+parts.get(1);
            for(int i=0;i+1<parts.size();i++)if(Set.of("mc-mods","customization","mod","resourcepack").contains(parts.get(i))){name=parts.get(i+1);break;}
            StringBuilder title=new StringBuilder();for(String word:name.replace('_',' ').replace('-',' ').split(" +"))if(!word.isBlank()){if(!title.isEmpty())title.append(' ');title.append(word.equalsIgnoreCase("tacz")?"TaCZ":Character.toUpperCase(word.charAt(0))+word.substring(1));}
            return title.toString();
        }catch(RuntimeException e){return value;}
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0&&y>=top&&y<height-34)for(var link:dependencyLinks)if(link.contains(x,y)){net.minecraft.Util.getPlatform().openUri(link.url());return true;}
        return super.mouseClicked(x,y,button);
    }
    private void renderReviews(GuiGraphics g){
        int x=rightX,w=rightW;
        String score=map.reviews.isEmpty()?label("no_rating"):String.format(Locale.ROOT,"%.1f / 5  ·  %s",map.average(),label("review_count",map.reviews.size()));
        g.drawString(font,tr("reviews"),x,top,TEXT);
        String summary=font.plainSubstrByWidth(score,Math.max(1,w-font.width(tr("reviews"))-12));
        g.drawString(font,summary,x+w-font.width(summary),top,GOLD);
        g.fill(x,top+15,x+w,top+16,0xFF354257);
        int start=top+23,y=start-(int)reviewScroll;
        g.enableScissor(x,start,x+w,Math.max(start+1,reviewBottom));
        if(map.reviews.isEmpty())g.drawString(font,tr("first_review"),x,y,MUTED);
        var ordered=new ArrayList<>(map.reviews);ordered.sort(Comparator.comparing((MapCatalogue.Review r)->!r.userId().equals(MapCommunityClient.ownId())).thenComparing(MapCatalogue.Review::date,Comparator.reverseOrder()));
        for(var review:ordered){
            int textH=review.comment().isBlank()?0:font.split(Component.literal(review.comment()),w-16).size()*11;
            int h=36+textH;g.fill(x,y,x+w,y+h,PANEL);g.renderOutline(x,y,w,h,0xFF3C495F);
            g.drawString(font,font.plainSubstrByWidth(review.user(),w-95),x+8,y+7,TEXT);
            String rating="★".repeat(review.stars());g.drawString(font,rating,x+w-8-font.width(rating),y+7,GOLD);
            g.drawString(font,review.date(),x+8,y+20,MUTED);
            if(textH>0)wrap(g,Component.literal(review.comment()),x+8,y+33,w-16,TEXT);
            y+=h+8;
        }
        reviewContentHeight=y-(start-(int)reviewScroll);g.disableScissor();
        if(reviewContentHeight>reviewBottom-start){int total=reviewBottom-start,thumb=Math.max(10,total*total/Math.max(1,reviewContentHeight)),sy=start+(int)((total-thumb)*reviewScroll/Math.max(1,reviewContentHeight-total));g.fill(x+w-2,sy,x+w,sy+thumb,0xFF556276);}
    }
    @Override public void onClose(){capture();minecraft.setScreen(parent);}
    private static final class LinkScreen extends Screen {
        private final Screen parent;private final MapCatalogue.Entry map;private boolean copied;
        LinkScreen(Screen parent,MapCatalogue.Entry map){super(Component.translatable("gui.zombierool.downloader.get_link"));this.parent=parent;this.map=map;}
        @Override protected void init(){
            int w=Math.min(460,width-32),x=(width-w)/2,y=height/2-10;
            var link=addRenderableWidget(new EditBox(font,x,y,w,20,title));link.setMaxLength(Math.max(4096,map.downloadUrl.length()));link.setValue(map.downloadUrl);link.setEditable(false);setInitialFocus(link);link.setCursorPosition(0);link.setHighlightPos(map.downloadUrl.length());
            addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.downloader.copy_link"),b->{minecraft.keyboardHandler.setClipboard(map.downloadUrl);copied=minecraft.keyboardHandler.getClipboard().equals(map.downloadUrl);}).bounds(x,y+30,w/2-3,20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x+w/2+3,y+30,w/2-3,20).build());
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,BG);g.drawCenteredString(font,title,width/2,height/2-54,TEXT);g.drawCenteredString(font,Component.literal(map.name),width/2,height/2-37,MUTED);super.render(g,mx,my,partial);g.drawCenteredString(font,Component.translatable(copied?"gui.zombierool.downloader.link_copied":"gui.zombierool.downloader.link_hint"),width/2,height/2+60,GREEN);}
        @Override public void onClose(){minecraft.setScreen(parent);}
    }
}
