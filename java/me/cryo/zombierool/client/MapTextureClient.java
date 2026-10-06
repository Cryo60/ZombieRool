package me.cryo.zombierool.client;

import me.cryo.zombierool.maptexture.MapTexturePack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT)
public final class MapTextureClient {
    private static final String PACK_ID = "file/zombierool_maptex";
    private static boolean busy;

    private MapTextureClient() {}

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft.getInstance().tell(MapTextureClient::rebuildIfChanged);
    }

    public static void rebuildIfChanged() {
        if (busy) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() == null) return;
        try {
            busy = true;
            Path world = minecraft.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            Path pack = minecraft.getResourcePackDirectory().resolve("zombierool_maptex");
            boolean changed = MapTexturePack.install(world, pack);
            if (!changed) return;
            PackRepository repository = minecraft.getResourcePackRepository();
            repository.reload();
            Pack found = repository.getPack(PACK_ID);
            if (found == null) return;
            List<String> ids = new ArrayList<>(repository.getSelectedIds());
            if (!ids.contains(PACK_ID)) ids.add(PACK_ID);
            repository.setSelected(ids);
            minecraft.options.updateResourcePacks(repository);
            minecraft.options.save();
            minecraft.reloadResourcePacks();
            if (changed && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("message.zombierool.maptex.reload"), true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            busy = false;
        }
    }
}
