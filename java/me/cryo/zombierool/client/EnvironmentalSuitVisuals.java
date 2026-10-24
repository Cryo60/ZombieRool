package me.cryo.zombierool.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import me.cryo.zombierool.gameplay.LunarEquipment;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Original, lightweight cuboid cosmetics. Uses ordinary entity render types for shader support. */
public final class EnvironmentalSuitVisuals {
    private record Piece(ModelPart model,ResourceLocation texture){}
    private record Equipment(List<Piece> helmet,List<Piece> back){}
    private static final Map<LunarEquipment.SuitStyle,Equipment> MODELS=new EnumMap<>(LunarEquipment.SuitStyle.class);
    private static final Map<Boolean,PlayerModel<AbstractClientPlayer>> SUITS=new HashMap<>();
    private static final Map<AbstractClientPlayer,ResourceLocation> ORIGINAL_SKINS=new WeakHashMap<>();
    public static ResourceLocation texture(Player player){return new ResourceLocation("zombierool","textures/entity/suits/"+LunarEquipment.style(player).id+"_suit.png");}
    public static ResourceLocation skin(AbstractClientPlayer player,ResourceLocation original){ORIGINAL_SKINS.put(player,original);return LunarEquipment.equipped(player)?texture(player):original;}
    private static Equipment load(LunarEquipment.SuitStyle style){
        try(var raw=EnvironmentalSuitVisuals.class.getResourceAsStream("/assets/zombierool/models/equipment/"+style.id+".json");var reader=new InputStreamReader(raw,StandardCharsets.UTF_8)){
            var json=JsonParser.parseReader(reader).getAsJsonObject();var groups=new ArrayList<List<Piece>>();
            for(String group:List.of("helmet","back")){
                var pieces=new ArrayList<Piece>();
                for(var entry:json.getAsJsonArray(group)){
                    var row=entry.getAsJsonObject();var lo=row.getAsJsonArray("from");var hi=row.getAsJsonArray("to");
                    float x=lo.get(0).getAsFloat(),y=lo.get(1).getAsFloat(),z=lo.get(2).getAsFloat();
                    var mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("cube",CubeListBuilder.create().texOffs(0,0).addBox(x,y,z,hi.get(0).getAsFloat()-x,hi.get(1).getAsFloat()-y,hi.get(2).getAsFloat()-z),PartPose.ZERO);
                    pieces.add(new Piece(LayerDefinition.create(mesh,16,16).bakeRoot(),new ResourceLocation("zombierool","textures/entity/suits/"+row.get("material").getAsString()+".png")));
                }
                groups.add(List.copyOf(pieces));
            }
            return new Equipment(groups.get(0),groups.get(1));
        }catch(Exception ex){throw new IllegalStateException("Cannot load environmental suit "+style.id,ex);}
    }
    private static void draw(List<Piece> pieces,PoseStack pose,MultiBufferSource buffers,int light){
        for(var piece:pieces)piece.model.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(piece.texture)),light,OverlayTexture.NO_OVERLAY);
    }
    public static void helmet(PoseStack pose,MultiBufferSource buffers,int light,LunarEquipment.SuitStyle style){draw(MODELS.computeIfAbsent(style,EnvironmentalSuitVisuals::load).helmet,pose,buffers,light);}
    public static void outfit(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,PlayerModel<AbstractClientPlayer> parent){
        boolean slim=player.getModelName().equals("slim");
        var suit=SUITS.computeIfAbsent(slim,key->new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(.26f),key),64,64).bakeRoot(),key));
        parent.copyPropertiesTo(suit);
        if(LunarEquipment.style(player)==LunarEquipment.SuitStyle.LUNAR){
            // Keep the real face and hat inside the original transparent lunar visor.
            var original=ORIGINAL_SKINS.get(player);
            if(original!=null){var skin=buffers.getBuffer(RenderType.entityCutoutNoCull(original));parent.head.render(pose,skin,light,OverlayTexture.NO_OVERLAY);parent.hat.render(pose,skin,light,OverlayTexture.NO_OVERLAY);}
        }
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture(player)));
        // Copy the final animated poses, including TacZ and player animation layers.
        suit.body.render(pose,out,light,OverlayTexture.NO_OVERLAY);suit.rightArm.render(pose,out,light,OverlayTexture.NO_OVERLAY);suit.leftArm.render(pose,out,light,OverlayTexture.NO_OVERLAY);suit.rightLeg.render(pose,out,light,OverlayTexture.NO_OVERLAY);suit.leftLeg.render(pose,out,light,OverlayTexture.NO_OVERLAY);
        pose.pushPose();parent.body.translateAndRotate(pose);draw(MODELS.computeIfAbsent(LunarEquipment.style(player),EnvironmentalSuitVisuals::load).back,pose,buffers,light);pose.popPose();
    }
    private EnvironmentalSuitVisuals(){}
}
