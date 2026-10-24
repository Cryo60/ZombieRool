package me.cryo.zombierool.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Keep automatic subscriber discovery independent of optional TaCZ model classes. */
@Mod.EventBusSubscriber(modid="zombierool", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class TacZOptionalClient {
    private TacZOptionalClient() {}

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        if (ModList.get().isLoaded("tacz")) TacZCamoOverlay.reload(event);
    }
}
