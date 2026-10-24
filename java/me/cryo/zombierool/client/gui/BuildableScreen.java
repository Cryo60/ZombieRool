package me.cryo.zombierool.client.gui;

import me.cryo.zombierool.gameplay.BuildablePackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Guided authoring: choose a result, then obtain configured component supports to place. */
public final class BuildableScreen extends Screen {
    private CompoundTag data;
    private String selected="",mode="item",picker="",afterSave="";
    private CompoundTag pendingPiece;
    private boolean capture,advanced,allItems,presetsOpen;
    private EditBox name,part,limit,search;
    private String draftName,draftPart,draftLimit,query="",choiceItem="";
    private int choiceSlot=-1,left,top,w,page,itemPage,presetPage;
    private ItemStack choice=ItemStack.EMPTY;
    private final List<ItemStack> pickerItems=new ArrayList<>();
    private final List<Integer> pickerSlots=new ArrayList<>();
    private static Component tr(String key,Object... args){return Component.translatable("gui.zombierool.buildable."+key,args);}
    public BuildableScreen(CompoundTag n){super(tr(n.getBoolean("partEditor")?"support_title":"title"));data=n;selected=n.getString("selected");capture=false;readMode();}
    private void readMode(){mode=data.getCompound("recipes").getCompound(selected).getString("mode");if(mode.isEmpty())mode="item";}
    public void update(CompoundTag n){data=n;if(n.contains("selected"))selected=n.getString("selected");if(!data.getCompound("recipes").contains(selected))selected="";draftName=draftPart=draftLimit=null;capture=false;choice=ItemStack.EMPTY;choiceSlot=-1;choiceItem="";picker="";readMode();rebuildWidgets();
        String next=afterSave;afterSave="";
        if(!n.getString("statusKey").equals("message.zombierool.buildable.saved"))return;
        if(next.equals("support"))openPicker("support");else if(next.equals("destination"))BuildablePackets.request("destination",request());else if(next.equals("duplicate")){var out=request();out.putString("part",pendingPiece.getString("part"));out.putBoolean("duplicate",true);BuildablePackets.request("support",out);}
    }
    private Button button(int x,int y,int width,Component label,Runnable action){return addRenderableWidget(Button.builder(label,b->action.run()).bounds(x,y,width,20).build());}
    private EditBox field(int x,int y,int width,Component label,String value){var box=new EditBox(font,x,y,width,18,label);box.setMaxLength(80);box.setValue(value);addRenderableWidget(box);return box;}
    private int presetRows(){return Math.max(1,(height-top-137)/60);}
    private boolean support(){return data.getBoolean("partEditor");}
    private void select(String id){presetsOpen=false;selected=id;capture=false;choice=ItemStack.EMPTY;choiceSlot=-1;choiceItem="";advanced=false;draftName=draftPart=draftLimit=null;readMode();rebuildWidgets();}
    private ItemStack preview(){return capture?choice:ItemStack.of(support()?data.getCompound("visual"):data.getCompound("recipes").getCompound(selected).getCompound("reward"));}
    @Override protected void init(){
        w=Math.min(600,width-16);left=(width-w)/2;top=12;name=part=limit=search=null;
        int side=Math.min(150,w/3),x=left+side+12,fw=w-side-20;
        var recipes=data.getCompound("recipes");var recipe=recipes.getCompound(selected);
        if(!picker.isEmpty()){initPicker(x,fw);button(left+4,top+30,side-4,tr("back"),()->{picker="";rebuildWidgets();});return;}
        button(left+4,top+25,side-4,tr("create_object"),()->{if(support())BuildablePackets.request("recipes",request());else select("");});
        if(!support())button(left+4,top+49,side-4,tr(presetsOpen?"map_recipes":"presets_library"),()->{captureDraft();presetsOpen=!presetsOpen;rebuildWidgets();});
        if(presetsOpen&&!support()){
            var templates=data.getList("presets",10);presetPage=Math.min(presetPage,Math.max(0,(templates.size()-1)/presetRows()));
            for(int i=0;i<presetRows()&&presetPage*presetRows()+i<templates.size();i++){var template=templates.getCompound(presetPage*presetRows()+i);String id=template.getString("id");var label=id.equals("zombie_shield")?tr("preset_shield"):id.equals("turbine")?tr("preset_turbine"):Component.literal("Preset : "+template.getString("name"));button(x,top+69+i*60,fw,label,()->preset(id));}
            button(left+4,height-52,side/2-4,Component.literal("<"),()->{presetPage=Math.max(0,presetPage-1);rebuildWidgets();});button(left+side/2+4,height-52,side/2-4,Component.literal(">"),()->{if((presetPage+1)*presetRows()<templates.size())presetPage++;rebuildWidgets();});
            button(x,height-27,fw,tr("close"),this::onClose);return;
        }
        int listStart=support()?51:89;var keys=new ArrayList<>(recipes.getAllKeys());Collections.sort(keys);int visible=Math.max(1,(height-top-listStart-90)/24);page=Math.min(page,Math.max(0,(keys.size()-1)/visible));
        for(int i=page*visible;i<Math.min(keys.size(),(page+1)*visible);i++){String id=keys.get(i);button(left+4,top+listStart+(i%visible)*24,side-4,Component.literal((id.equals(selected)?"> ":"")+recipes.getCompound(id).getString("name")),()->select(id));}
        button(left+4,height-52,side/2-4,Component.literal("<"),()->{captureDraft();page=Math.max(0,page-1);rebuildWidgets();});button(left+side/2+4,height-52,side/2-4,Component.literal(">"),()->{captureDraft();if((page+1)*visible<keys.size())page++;rebuildWidgets();});
        if(support()&&data.getBoolean("destinationEditor")&&!selected.isEmpty()){
            button(x,top+42,fw,tr("save_part"),()->send("part"));
        }else if(support()&&!selected.isEmpty()){
            button(x,top+42,fw,tr("choose_component"),()->openPicker("visual"));
            part=field(x,top+91,fw,tr("piece_name"),draftPart==null?(data.getString("partId").contains(":")?"":data.getString("partId")):draftPart);part.setHint(tr("automatic_part"));
            button(x,height-52,fw,tr("save_part"),()->send("part"));
        }else if(!support()){
            if(!advanced){
                name=field(x,top+40,fw,tr("name"),draftName==null?recipe.getString("name"):draftName);
                button(x,top+65,fw,tr("choose_result"),()->openPicker("reward"));
                limit=field(x+fw-70,top+110,70,tr("limit"),draftLimit==null?Integer.toString(recipe.getInt("limit")):draftLimit);limit.setFilter(s->s.matches("[0-9]{0,5}"));
                if(!selected.isEmpty()){
                    var pieces=pieces();int count=Math.min(pieces.size(),Math.max(1,(fw-42)/23));
                    for(int i=0;i<count;i++){var piece=pieces.get(i);int px=x+i*23;button(px,top+151,22,Component.empty(),()->duplicate(piece));}
                    button(x,top+176,fw,tr("add_component"),()->saveThen("support"));
                    if(ItemStack.of(recipe.getCompound("reward")).getItem() instanceof net.minecraft.world.item.BlockItem)button(left+4,height-77,side-4,tr("set_destination"),()->{mode="deploy";saveThen("destination");});
                }
            }else{
                button(x,top+40,fw,tr("mode",mode),()->{mode=switch(mode){case "item"->"block";case "block"->"deploy";case "deploy"->"event";default->"item";};rebuildWidgets();});
                limit=field(x,top+88,fw,tr("limit"),draftLimit==null?Integer.toString(recipe.getInt("limit")):draftLimit);
            }
            if(!selected.isEmpty())button(left+4,height-27,side-4,tr(advanced?"back":"advanced"),()->{captureDraft();advanced=!advanced;rebuildWidgets();});
            button(x,height-52,fw/2-3,tr(selected.isEmpty()?"save_step":"save"),()->send("save"));
            button(x+fw/2+3,height-52,fw/2-3,tr("delete"),()->send("remove")).active=!selected.isEmpty();
        }
        button(x,height-27,fw,tr("close"),this::onClose);
    }
    private List<CompoundTag> pieces(){
        var result=new ArrayList<CompoundTag>();var seen=new HashSet<String>();
        for(var entry:data.getCompound("recipes").getCompound(selected).getList("presetPieces",10)){var n=(CompoundTag)entry;if(seen.add(n.getString("part")))result.add(n);}
        for(var entry:data.getList("anchors",10)){var n=(CompoundTag)entry;if(n.getString("recipe").equals(selected)&&!n.getString("part").isEmpty()&&seen.add(n.getString("part")))result.add(n);}
        return result;
    }
    private void preset(String kind){presetsOpen=false;var n=request();n.putString("preset",kind);BuildablePackets.request("preset",n);}
    private void captureDraft(){if(name!=null)draftName=name.getValue();if(part!=null)draftPart=part.getValue();if(limit!=null)draftLimit=limit.getValue();}
    private CompoundTag request(){captureDraft();var n=new CompoundTag();n.putString("id",selected);if(data.contains("target"))n.putLong("target",data.getLong("target"));return n;}
    private void putChoice(CompoundTag n,String prefix){if(choiceSlot>=0)n.putInt(prefix+"Slot",choiceSlot);else if(!choiceItem.isEmpty())n.putString(prefix+"Item",choiceItem);}
    private void duplicate(CompoundTag piece){pendingPiece=piece;saveThen("duplicate");}
    private void saveThen(String next){afterSave=next;send("save");}
    private void send(String action){
        var n=request();n.putString("name",draftName==null?data.getCompound("recipes").getCompound(selected).getString("name"):draftName);n.putString("part",draftPart==null?(data.getString("partId").contains(":")?"":data.getString("partId")):draftPart);n.putString("mode",mode);n.putBoolean("capture",capture);putChoice(n,support()?"visual":"reward");
        if(action.equals("save")&&(n.getString("name").isBlank()||(!mode.equals("event")&&preview().isEmpty()))){data.putString("statusKey","gui.zombierool.buildable.need_result");return;}
        try{n.putInt("limit",draftLimit!=null?Integer.parseInt(draftLimit):data.getCompound("recipes").getCompound(selected).getInt("limit"));}catch(NumberFormatException e){data.putString("statusKey","gui.zombierool.buildable.numbers");return;}
        BuildablePackets.request(action,n);
    }
    private void openPicker(String purpose){captureDraft();picker=purpose;query="";itemPage=0;allItems=false;rebuildWidgets();}
    private void initPicker(int x,int fw){
        search=field(x,top+43,fw,tr("search_item"),query);search.setHint(tr("search_item"));search.setResponder(s->{query=s;itemPage=0;refreshPicker();});
        button(x,top+18,fw/2-2,tr("inventory"),()->{query=search.getValue();allItems=false;itemPage=0;rebuildWidgets();});button(x+fw/2+2,top+18,fw/2-2,tr("all_items"),()->{query=search.getValue();allItems=true;itemPage=0;rebuildWidgets();});
        refreshPicker();int cols=Math.max(1,fw/23),rows=Math.max(1,(height-top-116)/23),per=cols*rows;
        for(int i=0;i<per;i++){final int cell=i;button(x+(i%cols)*23,top+70+(i/cols)*23,22,Component.empty(),()->choose(itemPage*per+cell));}
        button(x,height-52,fw/2-3,Component.literal("<"),()->{itemPage=Math.max(0,itemPage-1);});button(x+fw/2+3,height-52,fw/2-3,Component.literal(">"),()->{if((itemPage+1)*per<pickerItems.size())itemPage++;});button(x,height-27,fw,tr("back"),()->{picker="";rebuildWidgets();});
    }
    private void refreshPicker(){
        pickerItems.clear();pickerSlots.clear();String q=query.toLowerCase(Locale.ROOT);
        if(minecraft.player==null)return;
        if(allItems){for(var item:ForgeRegistries.ITEMS){var stack=new ItemStack(item);if(!stack.isEmpty()&&(stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)||ForgeRegistries.ITEMS.getKey(item).toString().contains(q))){pickerItems.add(stack);pickerSlots.add(-1);}}}
        else for(int i=0;i<36;i++){var stack=minecraft.player.getInventory().getItem(i);if(!stack.isEmpty()&&stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)){pickerItems.add(stack.copy());pickerSlots.add(i);}}
    }
    private void choose(int index){
        if(index>=pickerItems.size())return;choice=pickerItems.get(index).copy();choiceSlot=pickerSlots.get(index);choiceItem=ForgeRegistries.ITEMS.getKey(choice.getItem()).toString();
        if(picker.equals("support")){var n=request();putChoice(n,"visual");BuildablePackets.request("support",n);}else{if(picker.equals("reward")&&choice.getItem() instanceof net.minecraft.world.item.BlockItem)mode="deploy";capture=true;picker="";rebuildWidgets();}
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);g.fill(left,top-7,left+w,height-4,0xF214202C);g.drawCenteredString(font,title,width/2,top,0xE5EEF8);
        int side=Math.min(150,w/3),x=left+side+12,fw=w-side-20;
        if(presetsOpen&&!support()){
            g.drawString(font,tr("presets_library"),x,top+27,0xE9C990);g.drawWordWrap(font,tr("presets_help"),x,top+43,fw,0xB9CDDF);
            var templates=data.getList("presets",10);
            for(int row=0;row<presetRows()&&presetPage*presetRows()+row<templates.size();row++){var parts=templates.getCompound(presetPage*presetRows()+row).getList("presetPieces",10);for(int i=0;i<parts.size()&&i<fw/30;i++){var stack=ItemStack.of(parts.getCompound(i).getCompound("visual"));int px=x+i*30,py=top+97+row*60;g.renderItem(stack,px,py);if(mx>=px&&mx<px+20&&my>=py&&my<py+20)g.renderTooltip(font,stack,mx,my);}}
            g.drawWordWrap(font,tr("components_inventory"),x,height-56,fw,0xB9CDDF);super.render(g,mx,my,partial);return;
        }
        if(!support()&&picker.isEmpty())g.drawString(font,tr("map_recipes"),left+5,top+76,0xB9CDDF);
        if(!picker.isEmpty()){
            super.render(g,mx,my,partial);int cols=Math.max(1,fw/23),rows=Math.max(1,(height-top-116)/23),per=cols*rows;
            for(int i=0;i<per&&itemPage*per+i<pickerItems.size();i++){var stack=pickerItems.get(itemPage*per+i);int px=x+(i%cols)*23+3,py=top+73+(i/cols)*23;g.renderItem(stack,px,py);if(mx>=px-3&&mx<px+19&&my>=py-3&&my<py+19)g.renderTooltip(font,stack,mx,my);}
            return;
        }
        if(support()&&data.getBoolean("destinationEditor")){
            g.drawWordWrap(font,tr("destination_help"),x,top+75,fw,0xB9CDDF);
        }else if(support()&&!selected.isEmpty()){
            g.drawString(font,font.plainSubstrByWidth(tr("chosen_recipe",data.getCompound("recipes").getCompound(selected).getString("name")).getString(),fw),x,top+27,0xE9C990);g.drawString(font,tr("piece_name"),x,top+78,0xB9CDDF);
            if(height>290)g.drawWordWrap(font,tr("simple_support_help"),x,top+126,fw,0xB9CDDF);
        }else if(support()){
            g.drawWordWrap(font,tr("select_recipe_help"),x,top+40,fw,0xE9C990);
        }else if(!advanced){
            g.drawString(font,tr("step_result"),x,top+26,0xE9C990);
            g.drawString(font,tr("copies_visible"),x,top+115,0xB9CDDF);
            if(!selected.isEmpty()){
                g.drawString(font,tr("step_parts",pieces().size()),x,top+138,0xE9C990);
                if(height>380)g.drawWordWrap(font,tr("place_help"),x,top+208,fw,0xB9CDDF);
            }else if(height>290)g.drawWordWrap(font,tr("create_help"),x,top+145,fw,0xB9CDDF);
        }else g.drawString(font,tr("limit"),x,top+75,0xB9CDDF);
        super.render(g,mx,my,partial);
        if(!advanced&&!selected.isEmpty()&&!support()){var pieces=pieces();int count=Math.min(pieces.size(),Math.max(1,(fw-42)/23));for(int i=0;i<count;i++){int px=x+i*23+3,py=top+153;var icon=ItemStack.of(pieces.get(i).getCompound("visual"));g.renderItem(icon,px,py);if(mx>=px-3&&mx<px+19&&my>=py-2&&my<py+18)g.renderTooltip(font,tr("duplicate_piece",icon.getHoverName()),mx,my);}}
        if(!advanced&&(!support()||!selected.isEmpty())){var stack=preview();int py=top+(support()?64:87);g.renderItem(stack,x,py);g.drawString(font,font.plainSubstrByWidth(stack.isEmpty()?tr("nothing_chosen").getString():stack.getHoverName().getString(),fw-23),x+23,py+4,stack.isEmpty()?0xCC7777:0xFFFFFF);}
        if(!data.getString("statusKey").isBlank())g.drawString(font,font.plainSubstrByWidth(Component.translatable(data.getString("statusKey")).getString(),w-12),left+6,height-65,0xF3CE87);
    }
}
