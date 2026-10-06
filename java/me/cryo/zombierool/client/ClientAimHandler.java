package me.cryo.zombierool.client;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.system.WeaponImplementations;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.C2SSyncAimPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientAimHandler {
    private static boolean aiming;
    private static boolean lastSent;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean next = false;
        if (mc.player != null && mc.screen == null && mc.options.keyUse.isDown()) {
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof WeaponSystem.BaseGunItem gun
                    && !(gun instanceof WeaponImplementations.MeleeWeaponItem)
                    && !gun.isAkimbo(stack)) {
                WeaponSystem.Definition def = gun.getDefinition();
                boolean scoped = def != null && def.scoped != null && def.scoped.isScoped;
                next = !scoped;
            }
        }
        aiming = next;
        if (mc.player != null) {
            mc.player.getPersistentData().putBoolean("zr_ads", aiming);
        }
        if (mc.player != null && aiming != lastSent) {
            lastSent = aiming;
            NetworkHandler.INSTANCE.sendToServer(new C2SSyncAimPacket(aiming));
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (aiming && !ClientSniperHandler.isScoping()) {
            event.setFOV(event.getFOV() * 0.85D);
        }
    }
}
