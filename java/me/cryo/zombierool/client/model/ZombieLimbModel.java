package me.cryo.zombierool.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Zombie mesh split into real pieces: cranium, jaw, chest, abdomen,
 * upper arm, forearm and hand. Each piece keeps the vanilla skin UVs.
 */
public final class ZombieLimbModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("zombierool", "zombie_limbs"), "main");
    public static final ModelLayerLocation GIB_LAYER = new ModelLayerLocation(new ResourceLocation("zombierool", "gore_gib"), "main");

    private ZombieLimbModel() {}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        head.addOrReplaceChild("cranium",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 4.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        head.addOrReplaceChild("jaw",
                CubeListBuilder.create().texOffs(0, 4).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 4.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        head.addOrReplaceChild("stump",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.4F, -4.0F, 8.0F, 2.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        root.addOrReplaceChild("hat",
                CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new net.minecraft.client.model.geom.builders.CubeDeformation(0.5F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 6.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        body.addOrReplaceChild("abdomen",
                CubeListBuilder.create().texOffs(16, 22).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 6.0F, 4.0F),
                PartPose.offset(0.0F, 6.0F, 0.0F));
        body.addOrReplaceChild("stump",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 2.0F, 4.0F),
                PartPose.offset(0.0F, 5.6F, 0.0F));

        addArm(root, "right_arm", 40, 16, -3.0F, -5.0F, false);
        addArm(root, "left_arm", 40, 16, -1.0F, 5.0F, true);

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(1.9F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void addArm(PartDefinition root, String name, int u, int v, float boxX, float pivotX, boolean mirror) {
        PartDefinition arm = root.addOrReplaceChild(name,
                limbBox(u, v, boxX, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, mirror),
                PartPose.offset(pivotX, 2.0F, 0.0F));
        PartDefinition forearm = arm.addOrReplaceChild("forearm",
                limbBox(u, v + 4, boxX, 0.0F, -2.0F, 4.0F, 4.0F, 4.0F, mirror),
                PartPose.offset(0.0F, 2.0F, 0.0F));
        forearm.addOrReplaceChild("hand",
                limbBox(u, v + 8, boxX, 0.0F, -2.0F, 4.0F, 4.0F, 4.0F, mirror),
                PartPose.offset(0.0F, 4.0F, 0.0F));
        arm.addOrReplaceChild("stump",
                CubeListBuilder.create().texOffs(0, 0).addBox(boxX - 0.2F, 0.0F, -2.2F, 4.4F, 2.0F, 4.4F),
                PartPose.offset(0.0F, 2.0F, 0.0F));
        forearm.addOrReplaceChild("stump",
                CubeListBuilder.create().texOffs(0, 0).addBox(boxX - 0.2F, 0.0F, -2.2F, 4.4F, 2.0F, 4.4F),
                PartPose.offset(0.0F, 4.0F, 0.0F));
    }

    private static CubeListBuilder limbBox(int u, int v, float x, float y, float z, float w, float h, float d, boolean mirror) {
        CubeListBuilder box = CubeListBuilder.create().texOffs(u, v);
        if (mirror) box.mirror();
        return box.addBox(x, y, z, w, h, d);
    }

    public static LayerDefinition createGibLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("right_upper", CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
        root.addOrReplaceChild("right_fore", CubeListBuilder.create().texOffs(40, 20).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
        root.addOrReplaceChild("right_hand", CubeListBuilder.create().texOffs(40, 24).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
        root.addOrReplaceChild("left_upper", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
        root.addOrReplaceChild("left_fore", CubeListBuilder.create().texOffs(40, 20).mirror().addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
        root.addOrReplaceChild("left_hand", CubeListBuilder.create().texOffs(40, 24).mirror().addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
        root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(16, 22).addBox(-4.0F, -3.0F, -2.0F, 8.0F, 6.0F, 4.0F), PartPose.ZERO);
        root.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -2.0F, -4.0F, 8.0F, 4.0F, 8.0F), PartPose.ZERO);
        root.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 4).addBox(-4.0F, -2.0F, -4.0F, 8.0F, 4.0F, 8.0F), PartPose.ZERO);
        root.addOrReplaceChild("meat", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.0F, -2.0F, 6.0F, 4.0F, 4.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
