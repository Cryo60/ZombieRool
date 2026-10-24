package me.cryo.zombierool.mixins;

import me.cryo.zombierool.init.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeCategoryMixin {
    @Shadow private static CreativeModeTab selectedTab;
    @Shadow private float scrollOffs;
    @Shadow private void refreshSearchResults(){throw new AssertionError();}
    @Unique private CreativeCatalog.Category zr$category=CreativeCatalog.Category.ALL;
    @Unique private boolean zr$selected(){return selectedTab==ZombieroolModTabs.ZB_RCT.get();}
    @Unique private int zr$left(){return (((CreativeModeInventoryScreen)(Object)this).width-195)/2-54;}
    @Unique private int zr$top(){int height=((CreativeModeInventoryScreen)(Object)this).height;return Math.max(4,Math.min((height-136)/2+8,height-((CreativeCatalog.Category.values().length+1)/2)*28-4));}
    @Unique private ItemStack zr$icon(CreativeCatalog.Category category){
        return switch(category){
            case ALL->new ItemStack(ZombieroolModItems.CREATIVE_ICON.get());
            case TOOLS->new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("zombierool:blood_brush")));
            case SPAWNERS->new ItemStack(ZombieroolModItems.ZOMBIE_SPAWN_EGG.get());
            case CRAFTING->new ItemStack(me.cryo.zombierool.block.system.BuildableBlocks.BENCH.get());
            case COMPONENTS->new ItemStack(me.cryo.zombierool.gameplay.PortableEquipment.PARTS.get(0).get());
            case QUESTS->new ItemStack(me.cryo.zombierool.core.registry.ZRBlocks.METEORITE.get());
            case BUILDING->new ItemStack(net.minecraft.world.level.block.Blocks.STONE_BRICKS);
            case NAVIGATION->new ItemStack(me.cryo.zombierool.block.system.TraversalSystem.ZIPLINE_ITEM.get());
            case MACHINES->new ItemStack(me.cryo.zombierool.block.system.PerksSystem.BLOCK.get());
            case SUITS->new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("zombierool:lunar_helmet")));
            case AMBIENCE->new ItemStack(me.cryo.zombierool.block.system.MapDeviceSystem.TYPES.get(me.cryo.zombierool.block.system.MapDeviceSystem.Kind.DCA).get());
            case MAP_WEAPONS->new ItemStack(ZombieroolModItems.SCARLET_SWORD.get());
            case WEAPONS->new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("zombierool:m1911")));
            case SIGNALS->new ItemStack(ZombieroolModBlocks.ACTIVATOR.get());
            case DECORATION->new ItemStack(ZombieroolModBlocks.BLACK_PUMPKIN.get());
            case TEXTURES->new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("zombierool:texture_kit")));
        };
    }
    @Inject(method={"selectTab","refreshSearchResults"},at=@At("RETURN"))
    private void zr$filter(CallbackInfo ci){
        if(!zr$selected())return;
        var menu=((CreativeModeInventoryScreen)(Object)this).getMenu();
        if(zr$category!=CreativeCatalog.Category.ALL)menu.items.removeIf(stack->CreativeCatalog.category(stack)!=zr$category);
        menu.items.sort(CreativeCatalog.order());
        scrollOffs=0;menu.scrollTo(0);
    }
    @Inject(method="render",at=@At("RETURN"))
    private void zr$draw(GuiGraphics g,int mx,int my,float partial,CallbackInfo ci){
        if(!zr$selected())return;
        int x=zr$left(),y=zr$top();Component tooltip=null;
        g.pose().pushPose();g.pose().translate(0,0,300);
        int i=0;for(var category:CreativeCatalog.Category.values()){
            int bx=x+(i%2)*26,by=y+(i++/2)*28;
            boolean hover=mx>=bx&&mx<bx+24&&my>=by&&my<by+26;
            g.fill(bx,by,bx+24,by+26,0xFF282828);
            g.fill(bx+1,by+1,bx+23,by+25,category==zr$category?0xFF8CC99C:0xFFAAAAAA);
            g.fill(bx+2,by+2,bx+22,by+24,hover?0xFFE4E4E4:0xFFCCCCCC);
            g.enableScissor(bx+3,by+3,bx+21,by+23);g.renderItem(zr$icon(category),bx+4,by+5);g.disableScissor();
            if(hover)tooltip=Component.translatable("gui.zombierool.creative."+category.name().toLowerCase(java.util.Locale.ROOT));
        }
        if(tooltip!=null)g.renderTooltip(Minecraft.getInstance().font,tooltip,mx,my);
        g.pose().popPose();
    }
    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true)
    private void zr$click(double mx,double my,int button,CallbackInfoReturnable<Boolean> ci){
        if(!zr$selected()||button!=0)return;
        int x=zr$left(),y=zr$top();
        if(mx<x||mx>=x+50||my<y||my>=y+((CreativeCatalog.Category.values().length+1)/2)*28)return;
        int col=(int)(mx-x)/26,row=(int)(my-y)/28;
        if((mx-x)%26>=24||(my-y)%28>=26)return;
        int i=row*2+col;if(i>=CreativeCatalog.Category.values().length)return;
        zr$category=CreativeCatalog.Category.values()[i];refreshSearchResults();ci.setReturnValue(true);
    }
}
