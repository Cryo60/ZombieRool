package me.cryo.zombierool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;
import java.lang.reflect.Proxy;

/** CPU regression: coating coordinates must stay attached during gun animations. */
public final class TacZCamoProjectionTest {
    public static void main(String[] args) throws Exception {
        var ids = new java.util.ArrayList<String>();
        try (var files = java.nio.file.Files.list(java.nio.file.Path.of("src/main/resources/assets/zombierool/textures/item/camos"))) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".png"))
                .map(n -> n.substring(0, n.length() - 4)).sorted().forEach(ids::add);
        }
        if (ids.isEmpty()) throw new AssertionError("No camo assets found");
        // Ensure no bundled camo silently falls through to the resource-pack default.
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/me/cryo/zombierool/client/TacZCamoOverlay.java"));
        String profiles = source.substring(source.indexOf("return switch(id)"), source.indexOf("default ->"));
        for (String id : ids) {
            if (!profiles.contains("\"" + id + "\"")) throw new AssertionError("Missing explicit camo profile: " + id);
        }
        int assets = ids.size();
        ids.add("_pap");
        ids.add("custom_resource_pack_camo");
        for (String id : ids) {
            float scale = TacZCamoOverlay.patternScale(id);
            if (!Float.isFinite(scale) || scale <= 0) throw new AssertionError("Invalid scale for " + id);
            for (int axis = 0; axis < 3; axis++) {
                float[] uv = new float[2];
                VertexConsumer sink = (VertexConsumer) Proxy.newProxyInstance(
                    TacZCamoProjectionTest.class.getClassLoader(), new Class<?>[]{VertexConsumer.class},
                    (proxy, method, values) -> {
                        if (method.getName().equals("uv")) {
                            uv[0] = (float) values[0]; uv[1] = (float) values[1];
                        }
                        return method.getReturnType() == VertexConsumer.class ? proxy : null;
                    });
                PoseStack bind = new PoseStack();
                bind.translate(0.25, -0.125, 0.5);
                Vector3f local = new Vector3f(0.125f, 0.25f, -0.25f);
                Vector3f rest = new Vector3f(local).mulPosition(bind.last().pose());
                float expectedU = (axis == 0 ? rest.z : rest.x) * scale;
                float expectedV = (axis == 1 ? rest.z : rest.y) * scale;
                for (float angle : new float[]{0, 0.4f, 1.8f}) {
                    PoseStack animated = new PoseStack();
                    animated.translate(2, -3, 4);
                    animated.mulPose(com.mojang.math.Axis.YP.rotation(angle));
                    animated.scale(1.2f, 0.8f, 1.5f);
                    Vector3f point = new Vector3f(local).mulPosition(animated.last().pose());
                    Vector3f normal = new Vector3f(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0)
                        .mul(animated.last().normal());
                    var consumer = new TacZCamoOverlay.SurfaceConsumer(sink, animated.last(), bind.last(), scale);
                    consumer.vertex(point.x, point.y, point.z).uv(99, 99)
                        .normal(normal.x, normal.y, normal.z).endVertex();
                    if (Math.abs(uv[0] - expectedU) > 0.0001f || Math.abs(uv[1] - expectedV) > 0.0001f)
                        throw new AssertionError(id + ": coating moved during animation on axis " + axis);
                }
            }
        }
        System.out.println("PASS: " + assets + " explicit camo profiles; " + ids.size() * 9
            + " projections including Pack-a-Punch and resource-pack fallback.");
    }
}
