package me.cryo.zombierool.client;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/** One unobtrusive update notice when entering a world, with no game-thread I/O. */
@Mod.EventBusSubscriber(modid="zombierool", value=Dist.CLIENT)
public final class ReleaseUpdateNotice {
    private record Release(String version, String url) {}
    private static CompletableFuture<Release> cached;
    private static long expires;
    private ReleaseUpdateNotice() {}

    @SubscribeEvent
    public static void joined(ClientPlayerNetworkEvent.LoggingIn event) {
        var player=event.getPlayer();
        String installed=ModList.get().getModContainerById("zombierool").orElseThrow().getModInfo().getVersion().toString();
        latest().thenAccept(release -> Minecraft.getInstance().execute(() -> {
            var mc=Minecraft.getInstance();
            if(release==null || mc.player!=player || !me.cryo.zombierool.util.ReleaseVersion.newer(release.version,installed)) return;
            var message=Component.translatable("message.zombierool.update.available",installed,release.version).withStyle(ChatFormatting.YELLOW);
            message.append(Component.translatable("message.zombierool.update.download").withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,release.url))));
            player.sendSystemMessage(message);
        }));
    }

    private static synchronized CompletableFuture<Release> latest() {
        long now=System.nanoTime();
        if(cached==null || now>=expires) {
            expires=now+java.util.concurrent.TimeUnit.HOURS.toNanos(1);
            cached=CompletableFuture.supplyAsync(ReleaseUpdateNotice::fetch);
        }
        return cached;
    }

    private static Release fetch() {
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)URI.create("https://api.github.com/repos/Cryo60/ZombieRool/releases/latest").toURL().openConnection();
            connection.setConnectTimeout(3000);connection.setReadTimeout(3000);
            connection.setRequestProperty("Accept","application/vnd.github+json");
            connection.setRequestProperty("User-Agent","ZombieRool-update-check");
            if(connection.getResponseCode()!=200)return null;
            try(var input=connection.getInputStream()) {
                var json=JsonParser.parseString(new String(input.readNBytes(262144),StandardCharsets.UTF_8)).getAsJsonObject();
                if(json.get("draft").getAsBoolean() || json.get("prerelease").getAsBoolean())return null;
                String version=me.cryo.zombierool.util.ReleaseVersion.normalize(json.get("tag_name").getAsString());
                String url=json.get("html_url").getAsString();
                if(version.isEmpty() || !url.startsWith("https://github.com/Cryo60/ZombieRool/releases/"))return null;
                return new Release(version,url);
            }
        } catch(Exception ignored) {return null;}
        finally {if(connection!=null)connection.disconnect();}
    }
}
