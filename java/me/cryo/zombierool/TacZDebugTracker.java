package me.cryo.zombierool.client.gui;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TacZDebugTracker {

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // S'active uniquement si le joueur tient une montre dans la main gauche
        if (player.getOffhandItem().getItem() != Items.CLOCK) return;

        ItemStack stack = player.getMainHandItem();
        if (!WeaponFacade.isTaczWeapon(stack)) return;

        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) return;

        TimelessAPI.getCommonGunIndex(iGun.getGunId(stack)).ifPresent(index -> {
            GunData data = index.getGunData();
            IGunOperator operator = IGunOperator.fromLivingEntity(player);

            // Cadence de tir actuelle
            long shootInterval = data.getShootInterval(player, iGun.getFireMode(stack), stack);
            
            // Temps restant du rechargement
            ReloadState reloadState = operator.getSynReloadState();
            long reloadCountdown = reloadState.getCountDown();
            String reloadStatus = reloadState.getStateType().isReloading() ? (reloadCountdown + " ms") : "IDLE";

            int currentAmmo = iGun.getCurrentAmmoCount(stack);
            int maxAmmo = com.tacz.guns.util.AttachmentDataUtils.getAmmoCountWithAttachment(stack, data);

            boolean hasDT = player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAPE.get());
            boolean hasSC = player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get());
            boolean isPaP = WeaponFacade.isPackAPunched(stack);

            int y = 50;
            GuiGraphics g = event.getGuiGraphics();
            
            // Fond noir transparent
            g.fill(5, y - 5, 200, y + 95, 0x88000000);
            
            g.drawString(mc.font, "--- TacZ Debug Tracker ---", 10, y, 0xFFFF00); y+=12;
            g.drawString(mc.font, "Gun: " + iGun.getGunId(stack).getPath(), 10, y, 0xFFFFFF); y+=10;
            g.drawString(mc.font, "PaP: " + (isPaP ? "§aYES" : "§cNO"), 10, y, 0xFFFFFF); y+=10;
            g.drawString(mc.font, "Double Tap: " + (hasDT ? "§aYES" : "§cNO"), 10, y, 0xFFFFFF); y+=10;
            g.drawString(mc.font, "Speed Cola: " + (hasSC ? "§aYES" : "§cNO"), 10, y, 0xFFFFFF); y+=10;
            g.drawString(mc.font, "Shoot Interval: " + shootInterval + " ms", 10, y, 0x00FFFF); y+=10;
            g.drawString(mc.font, "Reload Countdown: " + reloadStatus, 10, y, 0x00FFFF); y+=10;
            g.drawString(mc.font, "Ammo: " + currentAmmo + " / " + maxAmmo, 10, y, 0x00FFFF);
        });
    }
}