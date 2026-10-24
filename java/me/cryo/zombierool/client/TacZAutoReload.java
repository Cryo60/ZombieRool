package me.cryo.zombierool.client;

import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Uses TacZ's regular client action, so animation and server reload stay synchronized. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class TacZAutoReload {
    private TacZAutoReload() {}
    private static ItemStack loading=ItemStack.EMPTY;
    private static boolean heldTrigger,sawReload;
    private static int slot;
    private static String gunId="";
    private static int expires;
    public static boolean blocksHeldShot(){
        var mc=Minecraft.getInstance();return heldTrigger&&mc.player!=null&&matches(mc.player)&&mc.player.tickCount<expires&&mc.options.keyAttack.isDown();
    }
    private static boolean matches(net.minecraft.world.entity.player.Player player){return !loading.isEmpty()&&slot==player.getInventory().selected&&gunId.equals(player.getMainHandItem().getOrCreateTag().getString("GunId"));}
    private static void clear(){loading=ItemStack.EMPTY;heldTrigger=false;sawReload=false;}

    public static boolean shouldReload(ItemStack stack) {
        if(!ModList.get().isLoaded("tacz")||!WeaponFacade.isTaczWeapon(stack))return false;
        var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
        return gun!=null&&!gun.useInventoryAmmo(stack)&&WeaponFacade.getAmmo(stack)<=0&&WeaponFacade.getReserve(stack)>0;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!ModList.get().isLoaded("tacz"))return;
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||mc.level==null||mc.isPaused()||mc.screen!=null||!player.isAlive()||player.isSpectator()
                ||player.tickCount%5!=0||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID()))return;
        var held=player.getMainHandItem();
        boolean reloading=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().isReloading();
        if(!matches(player)||!mc.options.keyAttack.isDown()||player.tickCount>=expires||sawReload&&!reloading)clear();
        if(!loading.isEmpty()&&reloading)sawReload=true;
        if(!loading.isEmpty()&&WeaponFacade.getAmmo(held)>=WeaponFacade.getMaxAmmo(held))clear();
        if(shouldReload(held)
                &&!com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().isReloading())
        {
            loading=held;gunId=held.getOrCreateTag().getString("GunId");slot=player.getInventory().selected;sawReload=false;heldTrigger=mc.options.keyAttack.isDown();expires=player.tickCount+400;
            com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(player).reload();
        }
    }
}
