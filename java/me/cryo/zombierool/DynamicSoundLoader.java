package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DynamicSoundLoader {

    private static final String CACHE_DIR_NAME = "zombierool_sounds_cache";
    private static final Map<String, File> loadedSounds = new ConcurrentHashMap<>();
    private static final Map<String, TransferState> pendingTransfers = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        clearLoadedSounds();
    }

    @SubscribeEvent
    public static void onClientConnect(ClientPlayerNetworkEvent.LoggingIn event) {
        clearLoadedSounds();
    }

    public static void beginTransfer(String soundEventName, String category, int totalSize) {
        pendingTransfers.put(soundEventName, new TransferState(category, totalSize));
    }

    public static void receiveChunk(String soundEventName, int chunkIndex, byte[] chunk) {
        TransferState state = pendingTransfers.get(soundEventName);
        if (state != null) {
            state.chunks.put(chunkIndex, chunk);
        }
    }

    public static void finalizeTransfer(String soundEventName) {
        TransferState state = pendingTransfers.remove(soundEventName);
        if (state == null) return;

        int totalWritten = 0;
        byte[] assembled = new byte[state.totalSize];

        for (Map.Entry<Integer, byte[]> entry : state.chunks.entrySet()) {
            byte[] chunk = entry.getValue();
            System.arraycopy(chunk, 0, assembled, totalWritten, chunk.length);
            totalWritten += chunk.length;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        File cacheFile = getCacheFile(mc, soundEventName);
        if (cacheFile != null && writeCacheFile(cacheFile, assembled)) {
            loadedSounds.put(soundEventName, cacheFile);
            System.out.println("ZombieRool DynamicSoundLoader: sound ready → " + soundEventName);
        }
    }

    public static boolean hasDynamicSound(String soundEventName) {
        return loadedSounds.containsKey(soundEventName);
    }

    public static File getDynamicSoundFileByName(String dynamicName) {
        for (File file : loadedSounds.values()) {
            if (file.getName().replace(".ogg", "").equals(dynamicName)) {
                return file;
            }
        }
        return null;
    }

    public static SoundInstance getSoundByPrefix(String prefix, SoundSource source, float volume, float pitch, boolean looping, double x, double y, double z) {
        java.util.List<File> choices = new java.util.ArrayList<>();
        for (Map.Entry<String, File> entry : loadedSounds.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                choices.add(entry.getValue());
            }
        }
        if (!choices.isEmpty()) {
            File cacheFile = choices.get(new java.util.Random().nextInt(choices.size()));
            if (cacheFile.exists()) {
                return new FileSoundInstance(cacheFile, source, volume, pitch, looping, x, y, z, null);
            }
        }
        return null;
    }

    public static SoundInstance getSoundByExactName(String name, SoundSource source, float volume, float pitch, boolean looping, net.minecraft.world.entity.Entity entity) {
        File cacheFile = loadedSounds.get(name);
        if (cacheFile != null && cacheFile.exists()) {
            return new FileSoundInstance(cacheFile, source, volume, pitch, looping, 0, 0, 0, entity);
        }
        return null;
    }

    public static void clearLoadedSounds() {
        loadedSounds.clear();
        pendingTransfers.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        Runnable clearTask = () -> {
            File cacheDir = new File(mc.gameDirectory, CACHE_DIR_NAME);
            deleteDirectory(cacheDir);
        };

        if (mc.isSameThread()) mc.execute(clearTask);
        else mc.execute(clearTask);
    }

    public static void playDynamicSound(String soundEventName, SoundSource source,
                                        float volume, float pitch, boolean looping,
                                        double x, double y, double z) {
        File cacheFile = loadedSounds.get(soundEventName);
        if (cacheFile == null || !cacheFile.exists()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        mc.execute(() -> mc.getSoundManager().play(
                new FileSoundInstance(cacheFile, source, volume, pitch, looping, x, y, z, null)
        ));
    }

    @OnlyIn(Dist.CLIENT)
    public static class FileSoundInstance extends AbstractSoundInstance implements net.minecraft.client.resources.sounds.TickableSoundInstance {
        private final net.minecraft.world.entity.Entity entity;
        private boolean stopped = false;

        public FileSoundInstance(File file, SoundSource source, float volume, float pitch,
                                 boolean looping, double x, double y, double z, net.minecraft.world.entity.Entity entity) {
            super(
                    new ResourceLocation("zombierool", "dynamic_" + file.getName().replace(".ogg", "")),
                    source,
                    RandomSource.create()
            );
            this.volume    = volume;
            this.pitch     = pitch;
            this.looping   = looping;
            this.x         = entity != null ? entity.getX() : x;
            this.y         = entity != null ? entity.getY() : y;
            this.z         = entity != null ? entity.getZ() : z;
            this.entity    = entity;
            this.attenuation = (this.x == 0 && this.y == 0 && this.z == 0 && entity == null)
                    ? SoundInstance.Attenuation.NONE
                    : SoundInstance.Attenuation.LINEAR;
            this.relative  = (this.x == 0 && this.y == 0 && this.z == 0 && entity == null);
        }

        @Override
        public void tick() {
            if (this.entity != null) {
                if (this.entity.isRemoved()) {
                    this.stop();
                } else {
                    this.x = this.entity.getX();
                    this.y = this.entity.getY();
                    this.z = this.entity.getZ();
                }
            }
        }

        @Override
        public boolean isStopped() {
            return this.stopped;
        }

        public void stop() {
            this.stopped = true;
        }
    }

    private static File getCacheFile(Minecraft mc, String soundEventName) {
        String path = soundEventName.replace(':', File.separatorChar)
                .replace('/', File.separatorChar);
        if (path.contains("..")) return null;

        File cacheDir = new File(mc.gameDirectory, CACHE_DIR_NAME);
        File file = new File(cacheDir, path + "_" + System.currentTimeMillis() + ".ogg");

        try {
            String canonicalCacheDirPath = cacheDir.getCanonicalPath();
            String canonicalFilePath = file.getCanonicalPath();

            if (!canonicalFilePath.startsWith(canonicalCacheDirPath + File.separator)) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }

        file.getParentFile().mkdirs();
        return file;
    }

    private static boolean writeCacheFile(File file, byte[] data) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void deleteDirectory(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else {
                    try { f.delete(); } catch (Exception ignored) {}
                }
            }
        }
        dir.delete();
    }

    private static class TransferState {
        final String category;
        final int totalSize;
        final TreeMap<Integer, byte[]> chunks = new TreeMap<>();

        TransferState(String category, int totalSize) {
            this.category  = category;
            this.totalSize = totalSize;
        }
    }
}
