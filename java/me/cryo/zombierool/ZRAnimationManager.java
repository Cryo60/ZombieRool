package me.cryo.zombierool.client.animation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ZRAnimationManager extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
    private static final Map<String, ZRAnimation> ANIMATIONS = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ZRAnimationManager());
    }

    public static ZRAnimation getAnimation(String name) {
        return ANIMATIONS.get(name);
    }

    @Override
    protected Map<ResourceLocation, JsonObject> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonObject> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager.listResources("animations", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = new InputStreamReader(entry.getValue().open())) {
                map.put(entry.getKey(), JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception e) {
                System.err.println("[ZombieRool] Erreur lors du parsing de l'animation: " + entry.getKey());
                e.printStackTrace();
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonObject> objectMap, ResourceManager resourceManager, ProfilerFiller profiler) {
        ANIMATIONS.clear();
        for (Map.Entry<ResourceLocation, JsonObject> entry : objectMap.entrySet()) {
            String fileName = entry.getKey().getPath().replace("animations/", "").replace(".json", "");
            JsonObject root = entry.getValue();

            if (root.has("animations")) {
                JsonObject animsObj = root.getAsJsonObject("animations");
                for (String animName : animsObj.keySet()) {
                    JsonObject animObj = animsObj.getAsJsonObject(animName);
                    float length = animObj.has("animation_length") ? animObj.get("animation_length").getAsFloat() : 1.0f;
                    
                    ZRAnimation animation = new ZRAnimation(animName, length);

                    if (animObj.has("bones")) {
                        JsonObject bonesObj = animObj.getAsJsonObject("bones");
                        for (String boneName : bonesObj.keySet()) {
                            JsonObject boneObj = bonesObj.getAsJsonObject(boneName);
                            ZRAnimation.Bone bone = new ZRAnimation.Bone();
                            
                            parseKeyframes(boneObj, "position", bone.positions, new Vector3f(0, 0, 0));
                            parseKeyframes(boneObj, "rotation", bone.rotations, new Vector3f(0, 0, 0));
                            parseKeyframes(boneObj, "scale", bone.scales, new Vector3f(1, 1, 1));
                            
                            animation.bones.put(boneName, bone);
                        }
                    }
                    // Mapping primaire par nom d'animation (ex: "knife_sweep")
                    ANIMATIONS.put(animName, animation);
                    // Fallback par nom de fichier uniquement s'il n'écrase rien
                    if (!ANIMATIONS.containsKey(fileName)) {
                        ANIMATIONS.put(fileName, animation);
                    }
                }
            }
        }
        System.out.println("[ZombieRool] Animations chargées: " + ANIMATIONS.keySet());
    }

    private void parseKeyframes(JsonObject boneObj, String type, Map<Float, Vector3f> map, Vector3f def) {
        if (!boneObj.has(type)) return;
        JsonElement elem = boneObj.get(type);

        if (elem.isJsonArray()) {
            map.put(0.0f, parseVec3(elem, def));
        } else if (elem.isJsonObject()) {
            JsonObject frames = elem.getAsJsonObject();
            for (String timeStr : frames.keySet()) {
                try {
                    float time = Float.parseFloat(timeStr);
                    map.put(time, parseVec3(frames.get(timeStr), def));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private Vector3f parseVec3(JsonElement elem, Vector3f def) {
        if (elem == null) return def;
        if (elem.isJsonArray() && elem.getAsJsonArray().size() >= 3) {
            return new Vector3f(
                elem.getAsJsonArray().get(0).getAsFloat(),
                elem.getAsJsonArray().get(1).getAsFloat(),
                elem.getAsJsonArray().get(2).getAsFloat()
            );
        } else if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            // Prise en charge de la structure Bedrock stricte (privilégie post)
            if (obj.has("post")) return parseVec3(obj.get("post"), def);
            if (obj.has("vector")) return parseVec3(obj.get("vector"), def);
            if (obj.has("pre")) return parseVec3(obj.get("pre"), def); // Fallback pre
        }
        return def;
    }

    public static class ZRAnimation {
        public final String name;
        public final float lengthSeconds;
        public final Map<String, Bone> bones = new HashMap<>();

        public ZRAnimation(String name, float lengthSeconds) {
            this.name = name;
            this.lengthSeconds = lengthSeconds;
        }

        public static class Bone {
            public final TreeMap<Float, Vector3f> positions = new TreeMap<>();
            public final TreeMap<Float, Vector3f> rotations = new TreeMap<>();
            public final TreeMap<Float, Vector3f> scales = new TreeMap<>();
        }
    }

    public static class ZRAnimationState {
        private final ZRAnimation animation;
        private int tickCount = 0;
        private boolean isPlaying = false;
        private Runnable onFinish = null;

        public ZRAnimationState(ZRAnimation animation) {
            this.animation = animation;
        }

        public ZRAnimation getAnimation() {
            return animation;
        }

        public void start(Runnable onFinish) {
            this.tickCount = 0;
            this.isPlaying = true;
            this.onFinish = onFinish;
        }

        public void tick() {
            if (!isPlaying || animation == null) return;
            tickCount++;
            
            if ((tickCount / 20.0f) >= animation.lengthSeconds) {
                isPlaying = false;
                if (onFinish != null) {
                    onFinish.run();
                    onFinish = null;
                }
            }
        }

        public boolean isPlaying() {
            return isPlaying;
        }

        public boolean hasBone(String boneName) {
            return animation != null && animation.bones.containsKey(boneName);
        }

        private float getElapsedTime() {
            float partialTicks = Minecraft.getInstance().getFrameTime();
            return (tickCount + partialTicks) / 20.0f;
        }

        public Vector3f getPos(String boneName) {
            return interpolate(boneName, 0);
        }

        public Vector3f getRot(String boneName) {
            return interpolate(boneName, 1);
        }

        public Vector3f getScale(String boneName) {
            return interpolate(boneName, 2);
        }

        private Vector3f interpolate(String boneName, int type) {
            if (animation == null || !animation.bones.containsKey(boneName)) {
                return type == 2 ? new Vector3f(1, 1, 1) : new Vector3f(0, 0, 0);
            }
            ZRAnimation.Bone bone = animation.bones.get(boneName);
            TreeMap<Float, Vector3f> map = type == 0 ? bone.positions : (type == 1 ? bone.rotations : bone.scales);

            if (map.isEmpty()) return type == 2 ? new Vector3f(1, 1, 1) : new Vector3f(0, 0, 0);
            if (map.size() == 1) return map.firstEntry().getValue();

            float time = getElapsedTime();
            Map.Entry<Float, Vector3f> floor = map.floorEntry(time);
            Map.Entry<Float, Vector3f> ceiling = map.ceilingEntry(time);

            if (floor == null) return ceiling != null ? ceiling.getValue() : new Vector3f(0, 0, 0);
            if (ceiling == null) return floor.getValue();

            if (floor.getKey().equals(ceiling.getKey())) return floor.getValue();

            float percent = (time - floor.getKey()) / (ceiling.getKey() - floor.getKey());
            Vector3f start = floor.getValue();
            Vector3f end = ceiling.getValue();

            if (type == 1) { // Lissage des angles (chemin le plus court)
                return new Vector3f(
                    lerpAngle(start.x(), end.x(), percent),
                    lerpAngle(start.y(), end.y(), percent),
                    lerpAngle(start.z(), end.z(), percent)
                );
            } else { // Position et Scale (lerp basique)
                return new Vector3f(
                    start.x + (end.x - start.x) * percent,
                    start.y + (end.y - start.y) * percent,
                    start.z + (end.z - start.z) * percent
                );
            }
        }

        private float lerpAngle(float start, float end, float pct) {
            float delta = end - start;
            while (delta < -180.0f) delta += 360.0f;
            while (delta >= 180.0f) delta -= 360.0f;
            return start + pct * delta;
        }
    }
}