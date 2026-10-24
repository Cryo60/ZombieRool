package me.cryo.zombierool.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem;
import net.minecraft.resources.ResourceLocation;
/** Combat HUD: points over ammo, compact equipment, subdued blood ribbons. */
public final class ClassicZombieHUD {
    private static LocalPlayer owner;
    private static int lastIngots;
    private static long advertUntil;
    public static boolean enabled(){var mc=Minecraft.getInstance();return mc.player!=null&&WaveManager.isGameRunning()&&!mc.player.isCreative()&&!mc.player.isSpectator();}
    public static void reset(){owner=null;lastIngots=0;advertUntil=0;}
    private static void right(GuiGraphics g,Minecraft mc,String text,int x,int y,float scale,int color){g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.drawString(mc.font,text,-mc.font.width(text),0,color,true);g.pose().popPose();}
    private static void centered(GuiGraphics g,Minecraft mc,Component text,int x,int y,int maxWidth,int color){
        float scale=Math.min(1,maxWidth/(float)Math.max(1,mc.font.width(text)));g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.drawCenteredString(mc.font,text,0,0,color);g.pose().popPose();
    }
    public static void render(GuiGraphics g,Minecraft mc,LocalPlayer p,int w,int h){
        ItemStack held=p.getMainHandItem();
        var statusGun=held.getItem() instanceof me.cryo.zombierool.core.system.WeaponSystem.BaseGunItem gun?gun:null;
        int statusRows=statusGun==null?0:(statusGun.hasDurability()?1:0)+(statusGun.hasOverheat()?1:0);
        int base=h;h-=statusRows*15;
        int x=w-12;long now=mc.level.getGameTime();
        g.blit(new ResourceLocation("zombierool:textures/gui/classic_blood.png"),w-185,h-98,0,0,180,90,180,90);
        right(g,mc,String.valueOf(PointManager.getScore(p)),x-8,h-85,1.55f,0xFFF0E7D6);
        var gain=PointManager.getLastPointGain(p);
        if(gain!=null&&now-gain.timestamp<30)right(g,mc,(gain.amount>0?"+":"")+gain.amount,x-75,h-90-(int)((now-gain.timestamp)*.3),.8f,gain.amount>0?0xFFEDDCC1:0xFFE5726D);
        if(WeaponFacade.isWeapon(held)){
            right(g,mc,held.getHoverName().getString(),x,h-38,.7f,0xFFB6B5AA);
            if(statusRows==0){
                String clip=String.valueOf(WeaponFacade.getAmmo(held));
                if(held.getItem() instanceof me.cryo.zombierool.core.system.WeaponSystem.BaseGunItem gun&&gun.isAkimbo(held))clip=gun.getAmmoLeft(held)+" | "+clip;
                String reserve=held.getItem() instanceof me.cryo.zombierool.api.IReloadable r&&r.isInfinite(held)?"\u221e":String.valueOf(WeaponFacade.getReserve(held));
                right(g,mc,clip+" / "+reserve,x,h-24,1.8f,WeaponFacade.getAmmo(held)>0?0xFFBFD7D9:0xFFE86B65);
            }
            int barY=h-7;
            if(statusGun!=null&&statusGun.hasDurability()){
                statusBar(g,mc,x,barY,Component.translatable("zombierool.hud.energy"),statusGun.getDurability(held),statusGun.getMaxDurability(held),false);barY+=15;
            }
            if(statusGun!=null&&statusGun.hasOverheat())statusBar(g,mc,x,barY,Component.translatable("zombierool.hud.overheat"),statusGun.getOverheat(held),statusGun.getMaxOverheat(),true);
        }
        final int hudBottom=h;
        p.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap->{
            String[] types={cap.getLethalType(),cap.getTacticalType(),cap.getEquipmentType()};int[] counts={cap.getLethalCount(),cap.getTacticalCount(),cap.getEquipmentCount()};
            var keys=new net.minecraft.client.KeyMapping[]{me.cryo.zombierool.init.KeyBindings.LETHAL_GRENADE_KEY,me.cryo.zombierool.init.KeyBindings.TACTICAL_GRENADE_KEY,me.cryo.zombierool.init.KeyBindings.EQUIPMENT_KEY};
            for(int i=0;i<3;i++){var id=ResourceLocation.tryParse(types[i]);var item=id==null?null:net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);if(item==null||item==net.minecraft.world.item.Items.AIR)continue;int ex=x-126+i*44;g.renderItem(new ItemStack(item),ex,hudBottom-62);g.drawString(mc.font,String.valueOf(counts[i]),ex+17,hudBottom-52,counts[i]>0?0xFFE2DFCC:0xFF777777,true);right(g,mc,keys[i].getTranslatedKeyMessage().getString(),ex+22,hudBottom-72,.65f,0xFFBAB8AF);}
        });
        LunarEquipmentClient.equipmentIcon(g,mc,p,x-170,hudBottom-62,true);
        int perkX=12;for(var perk:PerksManager.ALL_PERKS.values()){
            var effect=perk.getAssociatedEffect();if(effect==null||!p.hasEffect(effect))continue;var icon=ResourceLocation.tryParse(perk.getIconPath());if(icon==null)continue;
            g.blit(icon,perkX,base-65,0,0,18,18,18,18);perkX+=21;
        }
        if(!me.cryo.zombierool.configuration.ZRClientConfig.HIDE_HEALTH.get()){
        float health=net.minecraft.util.Mth.clamp(p.getHealth()/Math.max(1,p.getMaxHealth()),0,1);
        float absorption=Math.max(0,p.getAbsorptionAmount());
        g.fill(11,11,125,absorption>0?52:36,0xBE161414);
        g.drawString(mc.font,Component.translatable("zombierool.hud.health",(int)Math.ceil(p.getHealth()),(int)Math.ceil(p.getMaxHealth())),15,14,0xFFF1E7DA,true);
        g.fill(15,26,121,32,0xFF492927);
        g.fill(15,26,15+(int)(106*health),32,health>.35?0xFFD6C5AC:0xFFDB4239);
        if(absorption>0){
            g.drawString(mc.font,Component.translatable("zombierool.hud.absorption",Math.round(absorption*10)/10.0F),15,36,0xFFFFD36A,true);
            g.fill(15,46,121,49,0xFF4B3B1F);
            g.fill(15,46,15+Math.max(1,(int)(106*Math.min(1,absorption/Math.max(1,p.getMaxHealth())))),49,0xFFFFD36A);
        }
        }
        var vest=p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);if(vest.getItem() instanceof me.cryo.zombierool.item.BulletVestTier1Item.Chestplate)g.drawString(mc.font,Component.translatable("zombierool.hud.armor",vest.getOrCreateTag().getInt("BulletVestArmorPoints")),12,base-19,0xFFD3D0C1,true);
        int allyY=h-103;if(mc.getConnection()!=null)for(var info:mc.getConnection().getListedOnlinePlayers()){if(info.getProfile().getId().equals(p.getUUID()))continue;var stat=ZombieCapabilitySystem.ClientTabListRenderer.clientStats.get(info.getProfile().getId());right(g,mc,info.getProfile().getName()+"  "+(stat==null?0:stat.score),x,allyY,.8f,0xFFCBC5B3);allyY-=12;}
        int count=p.getInventory().items.stream().filter(s->s.getItem() instanceof me.cryo.zombierool.item.IngotSaleItem).mapToInt(ItemStack::getCount).sum();
        if(owner!=p){reset();owner=p;}if(count>lastIngots)advertUntil=now+120;lastIngots=count;
        if(count>0){
            if(now<advertUntil){int aw=Math.min(w-20,300),ax=(w-aw)/2,ay=Math.max(30,base/5);g.fillGradient(ax,ay,ax+aw,ay+49,0xEA38250C,0xD5171209);g.fill(ax,ay,ax+aw,ay+2,0xFFFFD464);
                centered(g,mc,Component.translatable("zombierool.hud.ingot.headline"),w/2,ay+8,aw-16,0xFFFFDF76);centered(g,mc,Component.translatable("zombierool.hud.ingot.offer"),w/2,ay+23,aw-16,0xFFFFFFE1);centered(g,mc,Component.translatable("zombierool.hud.ingot.terms"),w/2,ay+36,aw-16,0xFFBEB491);
            }else right(g,mc,Component.translatable("zombierool.hud.ingot.ready",count).getString(),x,h-117,.85f,0xFFFFD46C);
        }
    }
    private static void statusBar(GuiGraphics g,Minecraft mc,int right,int y,Component label,int value,int maximum,boolean heat){
        float ratio=net.minecraft.util.Mth.clamp(value/(float)Math.max(1,maximum),0,1);
        int color=heat?(ratio<.5?0xFFF0BC62:0xFFE4573E):(ratio>.3?0xFF96DAD1:0xFFE4573E);
        g.pose().pushPose();g.pose().translate(right-128,y,0);g.pose().scale(.65f,.65f,1);
        g.drawString(mc.font,label,0,0,0xFFE3D9C9,true);
        String number=heat?Math.round(ratio*100)+"%":value+" / "+maximum;
        g.drawString(mc.font,number,197-mc.font.width(number),0,color,true);g.pose().popPose();
        g.fill(right-129,y+7,right+1,y+13,0xBE161414);g.fill(right-128,y+8,right,y+12,0xFF433938);
        g.fill(right-128,y+8,right-128+Math.round(128*ratio),y+12,color);
    }

}
