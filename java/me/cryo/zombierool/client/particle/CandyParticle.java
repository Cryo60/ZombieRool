package me.cryo.zombierool.client.particle;
import net.minecraft.client.particle.BreakingItemParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.*;
public final class CandyParticle extends BreakingItemParticle {
    private static final ItemStack[] SWEETS={new ItemStack(Items.COOKIE),new ItemStack(Items.SUGAR),new ItemStack(Items.RED_DYE),new ItemStack(Items.PINK_DYE),new ItemStack(Items.YELLOW_DYE),new ItemStack(Items.LIME_DYE),new ItemStack(Items.COCOA_BEANS)};
    private static final java.util.Map<ClientLevel,Budget> BUDGETS=new java.util.WeakHashMap<>();
    private static final class Budget {long tick=Long.MIN_VALUE;int spawned,live;}
    private final Budget budget;
    private boolean released;
    public static CandyParticle create(ClientLevel level,double x,double y,double z,double vx,double vy,double vz){
        var player=net.minecraft.client.Minecraft.getInstance().player;
        if(player==null||player.distanceToSqr(x,y,z)>48*48)return null;
        var b=BUDGETS.computeIfAbsent(level,l->new Budget());long tick=level.getGameTime();
        if(b.tick!=tick){b.tick=tick;b.spawned=0;}
        if(b.live>=128||b.spawned>=24)return null;
        b.spawned++;b.live++;return new CandyParticle(level,x,y,z,vx,vy,vz,b);
    }
    private CandyParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,Budget budget){
        super(level,x,y,z,SWEETS[level.random.nextInt(SWEETS.length)]);this.budget=budget;
        xd=vx*.6+(random.nextDouble()-.5)*.08;yd=Math.max(.05,vy*.6)+random.nextDouble()*.12;zd=vz*.6+(random.nextDouble()-.5)*.08;
        // Cosmetic fragments never need block collision queries or server-side entities.
        gravity=.7f;lifetime=18+random.nextInt(9);quadSize=.07f+random.nextFloat()*.07f;hasPhysics=false;roll=random.nextFloat()*6.28f;
    }
    @Override public void remove(){super.remove();if(!released){released=true;budget.live--;}}
    @Override public void tick(){super.tick();oRoll=roll;if(!onGround)roll+=.15f;}
}
