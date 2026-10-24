import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import me.cryo.zombierool.client.TacZCamoOverlay;

/** Execute the real overlay traversal without a GL context. */
public final class TacZCamoTraversalTest {
    static class Part extends BedrockPart {
        int draws;
        Part(String name){super(name);}
        @Override public void compile(PoseStack.Pose pose,VertexConsumer target,int light,int overlay,float r,float g,float b,float a){draws++;}
    }
    public static void main(String[] args)throws Exception{
        var root=new Part("righthand");var receiver=new Part("receiver");root.addChild(receiver);
        var barrel=new Part("barrel");receiver.addChild(barrel);
        var anonymous=new Part("octagon4");barrel.addChild(anonymous);
        var guard=new Part("handguard_tactical");barrel.addChild(guard);
        var guardChild=new Part("bone42");guard.addChild(guardChild);
        var rail=new Part("side_rail");guard.addChild(rail);
        var hand=new Part("righthand_pos");root.addChild(hand);
        var handChild=new Part("body");hand.addChild(handChild);
        var lens=new Part("scope_lens");receiver.addChild(lens);
        var glow=new Part("panel");glow.illuminated=true;receiver.addChild(glow);
        var hidden=new Part("stock");hidden.visible=false;receiver.addChild(hidden);
        var render=TacZCamoOverlay.class.getDeclaredMethod("renderPart",BedrockPart.class,PoseStack.class,VertexConsumer.class,int.class,int.class);
        render.setAccessible(true);var pose=new PoseStack();var before=new org.joml.Matrix4f(pose.last().pose());
        render.invoke(null,root,pose,null,0,0);
        for(var p:new Part[]{root,receiver,guard,guardChild})if(p.draws!=1)throw new AssertionError("Missing paint: "+p.name);
        for(var p:new Part[]{barrel,anonymous,rail,hand,handChild,lens,glow,hidden})if(p.draws!=0)throw new AssertionError("Paint leaked: "+p.name);
        if(!before.equals(pose.last().pose()))throw new AssertionError("Pose stack not restored");
        System.out.println("PASS: real overlay traversal preserves furniture, excludes metal/optics/hands, respects visibility and restores transforms");
    }
}
