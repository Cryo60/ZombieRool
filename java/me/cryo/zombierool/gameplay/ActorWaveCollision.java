package me.cryo.zombierool.gameplay;

import net.minecraft.world.entity.Entity;
import me.cryo.zombierool.entity.AbstractZombieRoolEntity;

public final class ActorWaveCollision {
    private ActorWaveCollision(){}
    public static boolean ignores(Entity first,Entity second){
        return MapActors.owns(first)&&second instanceof AbstractZombieRoolEntity&&!MapActors.owns(second)
            ||MapActors.owns(second)&&first instanceof AbstractZombieRoolEntity&&!MapActors.owns(first);
    }
}
