package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class ClientWeaponVfxHandler {

    private static void snowBlast(Minecraft mc,Vec3 start,Vec3 forward,double distance,boolean pap){
        Vec3 right=forward.cross(Math.abs(forward.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),up=right.cross(forward).normalize();
        var random=mc.level.random;double reach=Math.max(.25,Math.min(20,distance));
        // A short, dense expanding blast; bounded particle cost regardless of range.
        for(int i=0;i<(pap?110:85);i++){
            double along=.15+random.nextDouble()*reach,angle=random.nextDouble()*Math.PI*2,spread=(.12+along*.14)*Math.sqrt(random.nextDouble());
            Vec3 radial=right.scale(Math.cos(angle)*spread).add(up.scale(Math.sin(angle)*spread));Vec3 p=start.add(forward.scale(along)).add(radial);
            var flake=mc.particleEngine.createParticle(ParticleTypes.SNOWFLAKE,p.x,p.y,p.z,0,0,0);
            if(flake!=null){Vec3 speed=forward.scale(.35+random.nextDouble()*.5).add(radial.scale(.025));flake.setParticleSpeed(speed.x,speed.y,speed.z);flake.scale(1.4f+random.nextFloat()*1.4f);flake.setLifetime(8+random.nextInt(9));}
            if(i%5==0){var cloud=mc.particleEngine.createParticle(ParticleTypes.POOF,p.x,p.y,p.z,forward.x*.15,forward.y*.15,forward.z*.15);if(cloud!=null){cloud.setColor(.7f,.87f,1);cloud.scale(.6f);cloud.setLifetime(8);}}
        }
    }

    private static void grenadeBlast(Minecraft mc,Vec3 at){
        var random=mc.level.random;
        // One expanding flash, short sparks/debris and a rising smoke crown: bounded cost.
        mc.level.addParticle(ParticleTypes.FLASH,at.x,at.y+.15,at.z,0,0,0);
        for(int i=0;i<34;i++){double angle=random.nextDouble()*Math.PI*2,r=.12+random.nextDouble()*.8;double vx=Math.cos(angle)*r,vy=.12+random.nextDouble()*.55,vz=Math.sin(angle)*r;
            var p=mc.particleEngine.createParticle(i<10?ParticleTypes.FLAME:i<18?ParticleTypes.CRIT:ParticleTypes.CAMPFIRE_COSY_SMOKE,at.x+vx*.25,at.y+.2,at.z+vz*.25,vx*(i<18?.6:.12),vy*(i<18?1:.3),vz*(i<18?.6:.12));
            if(p!=null){p.setLifetime(i<18?8+random.nextInt(8):22+random.nextInt(15));p.scale(i<10?2.5f:i<18?1.2f:2.3f);if(i>=18)p.setColor(.25f,.24f,.22f);}
        }
        for(int i=0;i<10;i++){double a=i*Math.PI/5;var p=mc.particleEngine.createParticle(ParticleTypes.POOF,at.x+Math.cos(a)*.4,at.y+.12,at.z+Math.sin(a)*.4,Math.cos(a)*.24,.025,Math.sin(a)*.24);if(p!=null){p.scale(1.8f);p.setColor(.42f,.39f,.34f);p.setLifetime(12);}}
    }
    public static void handleVfx(String type, Vec3 start, Vec3 end, boolean isPap, boolean isHeadshot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Si le joueur local vise (scope) et est en vue Ã  la premiÃ¨re personne,
        // on fait partir les particules depuis le bas/centre de l'Ã©cran pour lui.
        if (ClientSniperHandler.isScoping() && mc.options.getCameraType().isFirstPerson()) {
            if (mc.player.distanceToSqr(start) < 4.0) {
                Vec3 eyePos = mc.player.getEyePosition(1.0f);
                Vec3 lookVec = mc.player.getViewVector(1.0f);
                start = eyePos.add(lookVec.scale(0.5)).subtract(0, 0.15, 0);
            }
        }

        Vec3 dir = end.subtract(start);
        double dist = dir.length();
        Vec3 norm = dist > 0 ? dir.normalize() : Vec3.ZERO;
        RandomSource rand = mc.level.random;

        switch (type) {
            case "POWDER": {
                Vec3 direction=end.subtract(start);double distance=direction.length();if(distance>0){var unit=direction.normalize();for(double d=.2;d<Math.min(distance,80);d+=.6)mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.65f,.56f,.38f),.65f),start.x+unit.x*d,start.y+unit.y*d,start.z+unit.z*d,0,0,0);}break;
            }

            case "WINTERS_HOWL":
                snowBlast(mc,start,norm,dist,isPap);
                break;
            case "VR11":
                var color=new DustParticleOptions(new Vector3f(1,.25f,.06f),1.1f);
                for(double i=0;i<dist;i+=.35){Vec3 p=start.add(norm.scale(i));mc.level.addParticle(color,p.x,p.y,p.z,0,0,0);}
                break;
            case "RICOCHET":
                if (isPap) {
                    // Ligne grise simple, claire et sans fumÃ©e pour Ã©viter le lag et l'obstruction
                    Vector3f tracerColor = new Vector3f(0.5f, 0.5f, 0.5f);
                    DustParticleOptions tracerDust = new DustParticleOptions(tracerColor, 1.0f);

                    for (double i = 0; i < dist; i += 0.15) {
                        Vec3 p = start.add(norm.scale(i));
                        mc.level.addParticle(tracerDust, p.x, p.y, p.z, 0, 0, 0);
                    }
                }
                break;

            case "INCENDIARY":
                for (double i = 0; i < dist; i += 0.5) {
                    Vec3 p = start.add(norm.scale(i));
                    mc.level.addParticle(ParticleTypes.FLAME, p.x, p.y, p.z,
                        (rand.nextDouble() - 0.5) * 0.05, (rand.nextDouble() - 0.5) * 0.05, (rand.nextDouble() - 0.5) * 0.05);
                }
                for (int i = 0; i < 5; i++) {
                    mc.level.addParticle(ParticleTypes.LAVA, end.x, end.y, end.z,
                        (rand.nextDouble() - 0.5) * 0.2, (rand.nextDouble() - 0.5) * 0.2, (rand.nextDouble() - 0.5) * 0.2);
                }
                break;

            case "FIRE_BURST":
                for(int i=0;i<22;i++){double a=rand.nextDouble()*Math.PI*2,r=rand.nextDouble()*.3;var p=mc.particleEngine.createParticle(i<16?ParticleTypes.FLAME:ParticleTypes.SMOKE,end.x,end.y+.2,end.z,Math.cos(a)*r,.08+rand.nextDouble()*.2,Math.sin(a)*r);if(p!=null){p.scale(2);p.setLifetime(12+rand.nextInt(12));}}
                break;
            case "EXPLOSION":
                grenadeBlast(mc,end);
                break;

            case "RAYGUN_TRAIL":
            case "RAYGUN_MK2":
            case "RAYGUN_IMPACT":
            case "RAYGUN_MK2_IMPACT":
                RayGunVisuals.add(type,start,end,isPap);
                if(type.endsWith("IMPACT"))for(int i=0;i<8;i++){
                    var spark=mc.particleEngine.createParticle(ParticleTypes.END_ROD,end.x,end.y,end.z,
                        (rand.nextDouble()-.5)*.24,(rand.nextDouble()-.5)*.24,(rand.nextDouble()-.5)*.24);
                    if(spark!=null){spark.setColor(isPap?1:.1f,isPap?.15f:1,.15f);spark.scale(.4f);spark.setLifetime(5+rand.nextInt(4));}
                }
                break;

            case "COVENANT_LASER":
                Vector3f covColor = isPap ? new Vector3f(1.0f, 0.2f, 0.6f) : new Vector3f(0.1f, 0.8f, 0.2f);
                DustParticleOptions covDust = new DustParticleOptions(covColor, 1.5f);
                ParticleOptions coreParticle = isPap ? ParticleTypes.WITCH : ParticleTypes.GLOW;

                for (double i = 0; i < dist; i += 0.1) {
                    Vec3 p = start.add(norm.scale(i));
                    mc.level.addParticle(covDust, p.x, p.y, p.z, 0, 0, 0);
                    if (i % 0.5 < 0.1) {
                        mc.level.addParticle(coreParticle, p.x, p.y, p.z, 0, 0, 0);
                    }
                }

                for (int i = 0; i < 8; i++) {
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z,
                        (rand.nextDouble() - 0.5) * 0.3, (rand.nextDouble() - 0.5) * 0.3, (rand.nextDouble() - 0.5) * 0.3);
                }
                break;

            case "STORM":
                ParticleOptions trailStorm = isPap ? ParticleTypes.FLAME : ParticleTypes.GLOW;
                ParticleOptions hitStorm = isPap ? ParticleTypes.LAVA : ParticleTypes.ELECTRIC_SPARK;

                for (double i = 0; i < dist; i += 0.5) {
                    Vec3 p = start.add(norm.scale(i));
                    mc.level.addParticle(trailStorm, p.x, p.y, p.z, 0, 0, 0);
                }

                for (int i = 0; i < 5; i++) {
                    mc.level.addParticle(hitStorm, end.x, end.y, end.z,
                        (rand.nextDouble() - 0.5) * 0.2, (rand.nextDouble() - 0.5) * 0.2, (rand.nextDouble() - 0.5) * 0.2);
                }
                break;

            case "FLAMETHROWER":
                ParticleOptions flame = isPap ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
                ParticleOptions smoke = ParticleTypes.CAMPFIRE_COSY_SMOKE;
                int particleCount = (int) (dist * 4);

                for (int i = 0; i < particleCount; i++) {
                    double d = rand.nextDouble() * dist;
                    Vec3 p = start.add(norm.scale(d));

                    double spreadX = (rand.nextDouble() - 0.5) * 0.6;
                    double spreadY = (rand.nextDouble() - 0.5) * 0.6;
                    double spreadZ = (rand.nextDouble() - 0.5) * 0.6;

                    mc.level.addParticle(flame, p.x + spreadX, p.y + spreadY, p.z + spreadZ,
                        norm.x * 0.1, norm.y * 0.1, norm.z * 0.1);

                    if (rand.nextDouble() < 0.2) {
                        mc.level.addParticle(smoke, p.x + spreadX, p.y + spreadY + 0.2, p.z + spreadZ,
                            norm.x * 0.05, 0.05, norm.z * 0.05);
                    }
                }
                break;

            case "PLASMA_IMPACT":
            case "PLASMA_IMPACT_OVERCHARGE":
                ParticleOptions plasmaHit = isPap ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.GLOW;
                int pCount = type.equals("PLASMA_IMPACT_OVERCHARGE") ? 40 : 15;
                double pSpeed = type.equals("PLASMA_IMPACT_OVERCHARGE") ? 0.25 : 0.1;
                for (int i = 0; i < pCount; i++) {
                    mc.level.addParticle(plasmaHit, end.x, end.y, end.z,
                        (rand.nextDouble() - 0.5) * pSpeed * 3, (rand.nextDouble() - 0.5) * pSpeed * 3, (rand.nextDouble() - 0.5) * pSpeed * 3);
                }
                break;

            case "SNIPER":
                for (double i = 0; i < dist; i += 0.5) {
                    Vec3 p = start.add(norm.scale(i));
                    mc.level.addParticle(ParticleTypes.CRIT, p.x, p.y, p.z, 0, 0, 0);
                }
                if (!isHeadshot) {
                    mc.level.addParticle(ParticleTypes.SMOKE, end.x, end.y, end.z, 0, 0, 0);
                }
                break;

            case "SAW":
                if (isPap) {
                    for (double i = 0; i < dist; i += 0.5) {
                        Vec3 p = start.add(norm.scale(i));
                        mc.level.addParticle(ParticleTypes.ASH, p.x, p.y, p.z, 0, 0, 0);
                        if (rand.nextDouble() < 0.25) {
                            mc.level.addParticle(ParticleTypes.FLAME, p.x, p.y, p.z,
                                (rand.nextDouble() - 0.5) * 0.05, 0.01, (rand.nextDouble() - 0.5) * 0.05);
                        }
                        if (rand.nextDouble() < 0.1) {
                            mc.level.addParticle(ParticleTypes.LAVA, p.x, p.y, p.z, 0, 0, 0);
                        }
                    }
                }
                break;

            case "THUNDERGUN":
                for (int i = 0; i < 50; i++) {
                    mc.level.addParticle(ParticleTypes.CLOUD, start.x, start.y, start.z,
                        norm.x * 2.0 + (rand.nextDouble() - 0.5),
                        norm.y * 2.0 + (rand.nextDouble() - 0.5),
                        norm.z * 2.0 + (rand.nextDouble() - 0.5));
                    if (isPap && rand.nextBoolean()) {
                        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, start.x, start.y, start.z,
                            norm.x * 3.0 + (rand.nextDouble() - 0.5),
                            norm.y * 3.0 + (rand.nextDouble() - 0.5),
                            norm.z * 3.0 + (rand.nextDouble() - 0.5));
                    }
                }
                break;

            case "SCAR_CHAIN":
                // A rifle fires continuously: leave the sight line clear and never accumulate smoke.
                int steps=Math.min(10,Math.max(1,(int)Math.ceil(dist/1.4)));
                var chainDust=new DustParticleOptions(new Vector3f(.38f,.68f,1),.35f);
                Vec3 eye=mc.gameRenderer.getMainCamera().getPosition();
                for(int j=0;j<=steps;j++){
                    Vec3 at=start.lerp(end,j/(double)steps);
                    if(at.distanceToSqr(eye)<2.25)continue;
                    var particle=mc.particleEngine.createParticle(chainDust,at.x,at.y,at.z,0,0,0);
                    if(particle!=null){particle.setLifetime(3);}
                }
                if(end.distanceToSqr(eye)>2.25)for(int j=0;j<2;j++){
                    var spark=mc.particleEngine.createParticle(ParticleTypes.ELECTRIC_SPARK,end.x,end.y,end.z,(rand.nextDouble()-.5)*.08,.02,(rand.nextDouble()-.5)*.08);
                    if(spark!=null)spark.setLifetime(4);
                }
                break;
            case "WUNDERWAFFE":
                me.cryo.zombierool.client.render.WunderwaffeLightning.add(start,end,isPap);
                Vector3f lightningColor = isPap ? new Vector3f(1.0f, 0.2f, 0.2f) : new Vector3f(0.5f, 0.8f, 1.0f);
                DustParticleOptions lightningDust = new DustParticleOptions(lightningColor, 1.5f);

                int lightningSteps=Math.min(36,Math.max(2,(int)Math.ceil(dist/.35)));
                for (int segment = 0; segment <= lightningSteps; segment++) {
                    double i=dist*segment/lightningSteps;
                    Vec3 p = start.add(norm.scale(i));
                    mc.level.addParticle(lightningDust, p.x, p.y, p.z, 0, 0, 0);
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x + (rand.nextDouble() - 0.5) * 0.2, p.y + (rand.nextDouble() - 0.5) * 0.2, p.z + (rand.nextDouble() - 0.5) * 0.2, 0, 0, 0);
                }

                for (int i = 0; i < 6; i++) {
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z,
                            (rand.nextDouble() - 0.5) * 0.5, (rand.nextDouble() - 0.5) * 0.5, (rand.nextDouble() - 0.5) * 0.5);
                }
                break;
        }

        if (isHeadshot) {
            for (int i = 0; i < 8; i++) {
                mc.level.addParticle(ParticleTypes.CRIT, end.x, end.y, end.z,
                    (rand.nextDouble() - 0.5) * 0.3, rand.nextDouble() * 0.3, (rand.nextDouble() - 0.5) * 0.3);
            }
        }
    }
}
