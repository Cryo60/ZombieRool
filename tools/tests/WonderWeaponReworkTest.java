package local.wondertests;

import me.cryo.zombierool.core.system.*;
import me.cryo.zombierool.item.*;
import me.cryo.zombierool.init.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Real TaCZ integration and first-person rendering in a disposable hidden client. */
@Mod("wondertests")
public final class WonderWeaponReworkTest {
    private boolean requested,done,started;
    private int age;
    private volatile boolean passed;
    private volatile Throwable failure;
    public WonderWeaponReworkTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);System.out.println("WONDER CHECK: "+label);}
    private static void invoke(String name,Class<?> type,Object event)throws Exception{
        var method=Class.forName("me.cryo.zombierool.integration.TacZImpl$TacZEventHandlers").getDeclaredMethod(name,type);method.setAccessible(true);method.invoke(null,event);
    }
    private static ItemStack tacz(String id,int ammo){
        var stack=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:modern_kinetic_gun")));
        stack.getOrCreateTag().putString("GunId","tacz:ak47");stack.getOrCreateTag().putString("zombierool:weapon_id",id);
        stack.getOrCreateTag().putBoolean("zombierool:is_tacz",true);
        com.tacz.guns.api.item.IGun.getIGunOrNull(stack).setCurrentAmmoCount(stack,ammo);return stack;
    }
    private void gameplay(ServerPlayer player){try{
        var level=player.serverLevel();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.teleportTo(level,400.5,65,.5,0,0);
        for(int x=397;x<=407;x++)for(int z=-2;z<=14;z++){level.setBlock(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState(),2);for(int y=65;y<70;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        var sync=Class.forName("me.cryo.zombierool.integration.TacZImpl").getDeclaredMethod("syncTaczGunData");sync.setAccessible(true);sync.invoke(null);
        for(String gun:java.util.List.of("raygun_bo6","ray_gun_mk2","wonder_waffle")){
            var data=com.tacz.guns.api.TimelessAPI.getCommonGunIndex(new ResourceLocation("elitex",gun)).orElseThrow().getGunData();
            check(data.getBulletData().getExplosionData()==null,"Gunpack explosive payload removed: "+gun);
            if(gun.equals("ray_gun_mk2"))check(data.getBulletData().getPierce()==5&&data.getBulletData().getGravity()==0&&data.getBurstData().getCount()==3&&data.getBurstData().getBpm()==600,"Actual Mark II gunpack has five-target penetration, zero gravity and separated burst rounds");
        }
        var ray=tacz("raygun",9);player.setItemInHand(InteractionHand.MAIN_HAND,ray);
        var shoot=new com.tacz.guns.api.event.common.GunShootEvent(player,ray,LogicalSide.SERVER);invoke("onGunShoot",shoot.getClass(),shoot);
        check(!shoot.isCanceled(),"Ray Gun preserves TaCZ shoot animation, cooldown and ammo finalization");
        var fire=new com.tacz.guns.api.event.common.GunFireEvent(player,ray,LogicalSide.SERVER);invoke("onWonderFire",fire.getClass(),fire);
        check(fire.isCanceled()&&com.tacz.guns.api.item.IGun.getIGunOrNull(ray).getCurrentAmmoCount(ray)==9,"Only gunpack projectile is replaced; no second ammo deduction");
        var arrows=level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Arrow.class,player.getBoundingBox().inflate(3));check(arrows.size()==1,"One Ray Gun projectile per actual shot");arrows.forEach(net.minecraft.world.entity.Entity::discard);
        com.tacz.guns.api.item.IGun.getIGunOrNull(ray).setCurrentAmmoCount(ray,0);
        fire=new com.tacz.guns.api.event.common.GunFireEvent(player,ray,LogicalSide.SERVER);invoke("onWonderFire",fire.getClass(),fire);
        arrows=level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Arrow.class,player.getBoundingBox().inflate(3));check(arrows.size()==1,"Final already-consumed TaCZ round still creates its projectile");arrows.forEach(net.minecraft.world.entity.Entity::discard);
        var target=ZombieroolModEntities.ZOMBIE.get().create(level);target.setPos(404.5,65,4.5);level.addFreshEntity(target);target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);target.setEmergenceTicks(0);target.setNoAi(true);
        var arrow=new net.minecraft.world.entity.projectile.Arrow(level,player);arrow.setPos(404.5,66,4.5);arrow.tickCount=1;
        var def=WeaponSystem.Loader.LOADED_DEFINITIONS.get("raygun");PackAPunchProjectiles.applyCustom(arrow.getPersistentData(),def.stats.damage,false);PackAPunchProjectiles.applyExplosive(arrow.getPersistentData(),def,false);
        var impact=new net.minecraftforge.event.entity.ProjectileImpactEvent(arrow,new BlockHitResult(arrow.position(),Direction.NORTH,arrow.blockPosition(),false));
        me.cryo.zombierool.handlers.CustomProjectileHandler.onImpact(impact);
        check(target.getHealth()<1000&&impact.isCanceled()&&arrow.isRemoved(),"Ray Gun contact blast works at tick 1, without grenade arming delay");target.discard();
        var mk2=tacz("raygunmarkii",18);player.setItemInHand(InteractionHand.MAIN_HAND,mk2);
        for(int i=0;i<3;i++){var round=new com.tacz.guns.api.event.common.GunFireEvent(player,mk2,LogicalSide.SERVER);invoke("onWonderFire",round.getClass(),round);check(!round.isCanceled(),"Mark II round "+(i+1)+" retains TaCZ bullet penetration");}
        def=WeaponSystem.Loader.LOADED_DEFINITIONS.get("raygunmarkii");check(def.burst.count==3&&def.burst.delay==2&&def.explosion.radius==0,"Mark II keeps three separated energy shots and no explosive payload");
        var zombies=new java.util.ArrayList<me.cryo.zombierool.entity.ZombieEntity>();
        for(int i=0;i<3;i++){var z=ZombieroolModEntities.ZOMBIE.get().create(level);z.setPos(400.5+i*1.5,65,6.5+i);level.addFreshEntity(z);z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);z.setHealth(1000);z.setEmergenceTicks(0);z.setNoAi(true);zombies.add(z);}
        var waffe=tacz("wunderwaffedg2",2);player.setItemInHand(InteractionHand.MAIN_HAND,waffe);
        var discharge=new com.tacz.guns.api.event.common.GunFireEvent(player,waffe,LogicalSide.SERVER);invoke("onWonderFire",discharge.getClass(),discharge);
        check(discharge.isCanceled()&&com.tacz.guns.api.item.IGun.getIGunOrNull(waffe).getCurrentAmmoCount(waffe)==2,"Wunderwaffe replaces explosive gunpack round without consuming ammo twice");
        for(var z:zombies)check(z.getHealth()<1000&&z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)&&z.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),"Wunderwaffe chains damage, electric stun and weakness to target "+z.getId());
        check(level.getEntitiesOfClass(com.tacz.guns.entity.EntityKineticBullet.class,player.getBoundingBox().inflate(20)).isEmpty(),"Wunderwaffe creates no TaCZ explosive projectile");zombies.forEach(net.minecraft.world.entity.Entity::discard);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.teleportTo(level,400.5,66,.5,0,0);passed=true;
        System.out.println("WONDER GAMEPLAY PASS");
    }catch(Throwable error){failure=error;error.printStackTrace();}}
    private void tick(TickEvent.ClientTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        try{
            if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"FeedbackFixture");return;}
            if(failure!=null)throw new AssertionError(failure);
            if(mc.level==null||mc.player==null)return;
            if(!started){started=true;mc.getSingleplayerServer().execute(()->gameplay(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0)));}
            if(!passed)return;age++;
            if(age==10){me.cryo.zombierool.client.ClientEnvironmentEffects.setFogPreset("none",0,0,0,1,512);mc.player.setXRot(-5);}
            if(age==10||age==35){
                String id=age==10?"raygun":"raygunmarkii";mc.getSingleplayerServer().execute(()->{var sp=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);sp.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool:"+id))));sp.serverLevel().setDayTime(6000);sp.serverLevel().setWeatherParameters(6000,0,false,false);});
            }
            if(age==20||age==45){
                var stack=mc.player.getMainHandItem();
                check(me.cryo.zombierool.client.RayGunFeel.kick(.85f,.08f),"Dedicated viewmodel recoil accepts "+stack.getItem());
                Vec3 start=mc.player.getEyePosition().add(mc.player.getLookAngle().scale(1)),end=start.add(mc.player.getLookAngle().scale(5));
                me.cryo.zombierool.client.ClientWeaponVfxHandler.handleVfx(age==20?"RAYGUN_TRAIL":"RAYGUN_MK2",start,end,false,false);
                me.cryo.zombierool.client.ClientWeaponVfxHandler.handleVfx("RAYGUN_IMPACT",end,end,age==45,false);
            }
            if(age==21||age==46){try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("feedback.output"),age==21?"raygun.png":"mark-ii.png"));}}
            if(age==60){done=true;System.out.println("WONDER CLIENT PASS");mc.stop();}
        }catch(Throwable error){done=true;error.printStackTrace();mc.stop();}
    }
}
