package me.cryo.zombierool.util;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Shared seeded appearance: the same irregular pool and tint on every client. */
public final class BloodAppearance {
    private static final int[][] PALETTE={{85,12,18},{78,10,15},{91,13,19},{82,11,17}};
    public static int color(int seed){
        var random=RandomSource.create(Integer.toUnsignedLong(seed)^0x564AC318L);
        int[] base=PALETTE[random.nextInt(PALETTE.length)];float shade=.8F+random.nextFloat()*.35F;
        return (Math.round(base[0]*shade)<<16)|(Math.round(base[1]*shade)<<8)|Math.round(base[2]*shade);
    }
    public static float size(RandomSource random){
        float roll=random.nextFloat();return roll<.25F?.18F+random.nextFloat()*.32F:roll<.85F?.5F+random.nextFloat()*.65F:1.15F+random.nextFloat()*1.1F;
    }
    public static List<BloodSurfaceGeometry.Piece> geometry(Level level,Vec3 center,Direction face,float half,float yaw,int seed){
        // UVs address the seeded surface mask; clip the entire spray to real blocks.
        return BloodSurfaceGeometry.clip(level,center,face,half,yaw,1);
    }
    private BloodAppearance(){}
}
