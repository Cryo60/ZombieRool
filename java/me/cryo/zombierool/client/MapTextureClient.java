package me.cryo.zombierool.client;

import com.mojang.blaze3d.platform.NativeImage;
import me.cryo.zombierool.maptexture.MapTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Updates reserved atlas pixels, never reloads gunpacks or rebuilds models. */
@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT)
public final class MapTextureClient {
    private static final Map<String, Long> uploaded = new HashMap<>();
    private static String worldKey = "";
    private MapTextureClient() {}
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        uploaded.clear();
        Minecraft.getInstance().tell(MapTextureClient::rebuildIfChanged);
    }
    public static Path worldRoot() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? null : server.getWorldPath(LevelResource.ROOT);
    }
    public static void invalidate() { uploaded.clear(); }
    public static void rebuildIfChanged() {
        Minecraft mc = Minecraft.getInstance();
        Path world = worldRoot();
        if (world == null) return;
        if (!world.toString().equals(worldKey)) { uploaded.clear(); worldKey = world.toString(); }
        var repository = mc.getResourcePackRepository();
        var ids = new java.util.ArrayList<>(repository.getSelectedIds());
        if (ids.remove("file/zombierool_maptex")) {
            repository.setSelected(ids);
            mc.options.updateResourcePacks(repository);
            mc.options.save();
        }
        String[] names = MapTextures.readSlots(world);
        Path dir = world.resolve("zombierool/custom_blocks");
        mc.getTextureManager().bindForSetup(TextureAtlas.LOCATION_BLOCKS);
        for (int slot = 0; slot < MapTextures.SLOTS; slot++) {
            for (String face : new String[]{"side", "top", "bottom", "lower", "upper", "north", "south", "east", "west"}) {
                String name = names[slot] == null ? "" : names[slot];
                Path file = dir.resolve(name + (face.equals("side") ? "" : "_" + face) + ".png");
                if (!Files.isRegularFile(file)) file = dir.resolve(name + ".png");
                String key = slot + "_" + face;
                try {
                    byte[] bytes = name.isEmpty() || !Files.isRegularFile(file) ? null : Files.readAllBytes(file);
                    if (bytes != null) {
                        if (bytes.length < 24 || bytes.length > 1048576) throw new java.io.IOException("Texture file size limit");
                        var header=java.nio.ByteBuffer.wrap(bytes);int w=header.getInt(16),h=header.getInt(20);
                        if(w<1||h<1||w>512||h>512)throw new java.io.IOException("Texture dimension limit");
                    }
                    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                    if (bytes != null) crc.update(bytes);
                    long fingerprint = crc.getValue();
                    if (uploaded.getOrDefault(key, -1L) == fingerprint) continue;
                    TextureAtlasSprite sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                            .apply(new ResourceLocation("zombierool", "block/maptex/" + key));
                    if (!sprite.contents().name().getPath().equals("block/maptex/" + key)) continue;
                    try (NativeImage source = bytes == null ? null : NativeImage.read(bytes)) {
                        for (NativeImage target : sprite.contents().byMipLevel) {
                            for (int y = 0; y < target.getHeight(); y++) for (int x = 0; x < target.getWidth(); x++) {
                                int color = source == null ? (((x / 4 + y / 4) % 2 == 0) ? 0xFF777777 : 0xFF444444)
                                        : source.getPixelRGBA(x * source.getWidth() / target.getWidth(), y * source.getHeight() / target.getHeight());
                                target.setPixelRGBA(x, y, color);
                            }
                        }
                        sprite.uploadFirstFrame();
                    }
                    uploaded.put(key, fingerprint);
                } catch (Exception error) {
                    me.cryo.zombierool.ZombieroolMod.LOGGER.warn("Cannot upload map texture {}", file, error);
                }
            }
        }
    }
}
