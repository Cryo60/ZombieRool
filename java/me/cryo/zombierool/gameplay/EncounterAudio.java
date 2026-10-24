package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Original enemy sound sets; missing source events remain silent. */
public final class EncounterAudio {
    /** One vocal reaction per entity, including rapid TaCZ pellets and DoT ticks. */
    public static boolean allowHurt(net.minecraft.world.entity.LivingEntity entity){
        var data=entity.getPersistentData();long now=entity.level().getGameTime();
        if(data.contains("zr_encounter_hurt_voice")&&now-data.getLong("zr_encounter_hurt_voice")<40)return false;
        data.putLong("zr_encounter_hurt_voice",now);return true;
    }
    public static SoundEvent sound(net.minecraft.world.entity.LivingEntity entity,String action){
        String kind=entity instanceof SpecialEnemyEntity s?EncounterAbilities.kind(s):commonKind(entity);
        if(kind.equals("pentagon_thief")){kind="thief";if(action.equals("hurt"))action="anger";if(action.equals("idle")&&entity.getDeltaMovement().horizontalDistanceSqr()>.001)action="sprint";}
        if(kind.equals("george_romero"))kind="romero";
        return ZombieroolModSounds.encounter(kind+"_"+action);
    }
    public static String commonKind(net.minecraft.world.entity.LivingEntity entity){
        if(!(entity instanceof ZombieEntity z))return "";String id=z.getCustomSkin();
        String model=me.cryo.zombierool.config.EnemySkinPresets.blockyModel(id);return model.equals("scourge_ghoul")?"ghoul":model;
    }
    public static void play(net.minecraft.world.entity.Entity entity,String name,float volume){
        var sound=ZombieroolModSounds.encounter(name);
        if(sound!=null&&!entity.level().isClientSide)entity.level().playSound(null,entity.getX(),entity.getY(),entity.getZ(),sound,SoundSource.HOSTILE,volume,1);
    }
    private EncounterAudio(){}
}
