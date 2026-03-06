package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class ClientWeaponVfxHandler {

    public static void handleVfx(String type, Vec3 start, Vec3 end, boolean isPap, boolean isHeadshot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Si le joueur local vise (scope) et est en vue à la première personne, 
        // on fait partir les particules depuis le bas/centre de l'écran pour lui.
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
            case "RICOCHET":
                if (isPap) {
                    // Ligne grise simple, claire et sans fumée pour éviter le lag et l'obstruction
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

            case "EXPLOSION":
                mc.level.addParticle(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 0, 0, 0);
                break;

            case "RAYGUN_IMPACT":
                ParticleOptions trailParticle = isPap ? ParticleTypes.FLAME : ParticleTypes.GLOW;
                ParticleOptions hitParticle = isPap ? ParticleTypes.LAVA : ParticleTypes.ELECTRIC_SPARK;
                for (int i = 0; i < 40; i++) {
                    mc.level.addParticle(trailParticle, end.x, end.y, end.z, 
                        (rand.nextDouble() - 0.5) * 0.6, (rand.nextDouble() - 0.5) * 0.6, (rand.nextDouble() - 0.5) * 0.6);
                    if (i % 2 == 0) {
                        mc.level.addParticle(hitParticle, end.x, end.y, end.z, 
                            (rand.nextDouble() - 0.5) * 1.5, (rand.nextDouble() - 0.5) * 1.5, (rand.nextDouble() - 0.5) * 1.5);
                    }
                }
                break;

            case "RAYGUN_MK2":
                Vector3f beamColor = isPap ? new Vector3f(1.0f, 0.0f, 0.0f) : new Vector3f(0.0f, 1.0f, 0.0f);
                DustParticleOptions beamDust = new DustParticleOptions(beamColor, 1.2f);

                for (double i = 0; i < dist; i += 0.25) {
                    Vec3 p = start.add(norm.scale(i));
                    mc.level.addParticle(beamDust, p.x, p.y, p.z, 0, 0, 0);
                }

                ParticleOptions mk2Trail = isPap ? ParticleTypes.FLAME : ParticleTypes.GLOW;
                ParticleOptions mk2Hit = isPap ? ParticleTypes.LAVA : ParticleTypes.ELECTRIC_SPARK;
                for (int i = 0; i < 20; i++) {
                    mc.level.addParticle(mk2Trail, end.x, end.y, end.z, 
                        (rand.nextDouble() - 0.5) * 0.4, (rand.nextDouble() - 0.5) * 0.4, (rand.nextDouble() - 0.5) * 0.4);
                    if (i % 2 == 0) {
                        mc.level.addParticle(mk2Hit, end.x, end.y, end.z, 
                            (rand.nextDouble() - 0.5) * 0.8, (rand.nextDouble() - 0.5) * 0.8, (rand.nextDouble() - 0.5) * 0.8);
                    }
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

            case "WUNDERWAFFE":
                Vector3f lightningColor = isPap ? new Vector3f(1.0f, 0.2f, 0.2f) : new Vector3f(0.5f, 0.8f, 1.0f);
                DustParticleOptions lightningDust = new DustParticleOptions(lightningColor, 1.5f);
                
                for (double i = 0; i < dist; i += 0.2) {
                    Vec3 p = start.add(norm.scale(i));
                    mc.level.addParticle(lightningDust, p.x, p.y, p.z, 0, 0, 0);
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x + (rand.nextDouble() - 0.5) * 0.2, p.y + (rand.nextDouble() - 0.5) * 0.2, p.z + (rand.nextDouble() - 0.5) * 0.2, 0, 0, 0);
                }
                
                for (int i = 0; i < 15; i++) {
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