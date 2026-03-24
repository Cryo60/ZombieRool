package me.cryo.zombierool.mixins;

import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import com.mojang.blaze3d.audio.OggAudioStream;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import me.cryo.zombierool.client.DynamicSoundLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {
    @Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
    private void zombierool_getCompleteBuffer(ResourceLocation location, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        if (location.getNamespace().equals("zombierool")) {
            String path = location.getPath();
            if (path.startsWith("dynamic_") || path.startsWith("sounds/dynamic_")) {
                String originalName = path;
                if (originalName.startsWith("sounds/")) {
                    originalName = originalName.substring("sounds/".length());
                }
                if (originalName.endsWith(".ogg")) {
                    originalName = originalName.substring(0, originalName.length() - 4);
                }
                if (originalName.startsWith("dynamic_")) {
                    originalName = originalName.substring("dynamic_".length());
                }

                File file = DynamicSoundLoader.getDynamicSoundFileByName(originalName);
                if (file != null && file.exists()) {
                    cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
                        try (InputStream is = new FileInputStream(file);
                             OggAudioStream ogg = new OggAudioStream(is)) {
                            ByteBuffer buffer = ogg.readAll();
                            return new SoundBuffer(buffer, ogg.getFormat());
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    }, Util.backgroundExecutor()));
                }
            }
        }
    }

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void zombierool_getStream(ResourceLocation location, boolean isWrapper, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (location.getNamespace().equals("zombierool")) {
            String path = location.getPath();
            if (path.startsWith("dynamic_") || path.startsWith("sounds/dynamic_")) {
                String originalName = path;
                if (originalName.startsWith("sounds/")) {
                    originalName = originalName.substring("sounds/".length());
                }
                if (originalName.endsWith(".ogg")) {
                    originalName = originalName.substring(0, originalName.length() - 4);
                }
                if (originalName.startsWith("dynamic_")) {
                    originalName = originalName.substring("dynamic_".length());
                }

                File file = DynamicSoundLoader.getDynamicSoundFileByName(originalName);
                if (file != null && file.exists()) {
                    cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
                        try {
                            InputStream is = new FileInputStream(file);
                            return isWrapper ? new LoopingAudioStream(OggAudioStream::new, is) : new OggAudioStream(is);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    }, Util.backgroundExecutor()));
                }
            }
        }
    }
}