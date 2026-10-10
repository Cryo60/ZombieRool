package me.cryo.zombierool.client.particle;
import net.minecraft.client.particle.BreakingItemParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.*;
public final class CandyParticle extends BreakingItemParticle {
    private static final Item[] SWEETS={Items.COOKIE,Items.SUGAR,Items.RED_DYE,Items.PINK_DYE,Items.YELLOW_DYE,Items.LIME_DYE,Items.COCOA_BEANS};
    public CandyParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz){
        super(level,x,y,z,new ItemStack(SWEETS[level.random.nextInt(SWEETS.length)]));
        xd=vx*.6+(random.nextDouble()-.5)*.08;yd=Math.max(.05,vy*.6)+random.nextDouble()*.12;zd=vz*.6+(random.nextDouble()-.5)*.08;
        gravity=.7f;lifetime=35+random.nextInt(30);quadSize=.07f+random.nextFloat()*.07f;hasPhysics=true;roll=random.nextFloat()*6.28f;
    }
    @Override public void tick(){super.tick();oRoll=roll;if(!onGround)roll+=.15f;}
}
