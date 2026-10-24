package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.config.MapConfigExtensions;
import me.cryo.zombierool.config.WorldConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Optional, unnumbered survival opening. Rounds begin only when explicitly requested. */
public final class IntroPhase {
    public static final String ID="zombierool:intro";
    private IntroPhase(){}
    public static void register(){
        var n=new CompoundTag();n.putBoolean("enabled",false);n.putInt("rampSeconds",90);n.putInt("houndStartSeconds",45);n.putString("spawnerChannels","0");n.putInt("healthRoundSeconds",10);n.putDouble("sprintThreshold",.5);
        n.putInt("initialIntervalTicks",100);n.putInt("finalIntervalTicks",10);n.putInt("initialAliveCap",6);n.putInt("finalAliveCap",32);
        n.putDouble("houndChance",.15);
        MapConfigExtensions.register(new ResourceLocation(ID),"general",n);
    }
    public static CompoundTag settings(ServerLevel level){return WorldConfig.get(level).getAddonConfig().getCompound(ID);}
    public static void configure(ServerLevel level,boolean enabled,int rampSeconds,int houndsAfterSeconds){
        var config=WorldConfig.get(level);var addons=config.getAddonConfig();var n=addons.getCompound(ID);n.putBoolean("enabled",enabled);n.putInt("rampSeconds",Math.max(1,Math.min(3600,rampSeconds)));n.putInt("houndStartSeconds",Math.max(0,Math.min(3600,houndsAfterSeconds)));addons.put(ID,n);var patch=new CompoundTag();patch.put("addonConfig",addons);config.loadEditable(patch);config.setDirty();
    }
    public static double progress(ServerLevel level,int elapsedTicks){return Math.min(1,elapsedTicks/(20.0*Math.max(1,settings(level).getInt("rampSeconds"))/Math.max(1,WaveManager.getCurrentWave()/5.0)));}
    public static int interval(ServerLevel level,int elapsedTicks){var n=settings(level);return (int)Math.max(2,Math.min(1200,n.getInt("initialIntervalTicks")+(n.getInt("finalIntervalTicks")-n.getInt("initialIntervalTicks"))*progress(level,elapsedTicks)));}
    public static int cap(ServerLevel level,int elapsedTicks){var n=settings(level);return (int)Math.max(1,Math.min(200,n.getInt("initialAliveCap")+(n.getInt("finalAliveCap")-n.getInt("initialAliveCap"))*progress(level,elapsedTicks)));}
    public static boolean sprinting(ServerLevel level,int ticks){return progress(level,ticks)>=Math.max(0,Math.min(1,settings(level).getDouble("sprintThreshold")));}
    public static double speed(ServerLevel level,int elapsedTicks){return sprinting(level,elapsedTicks)?WorldConfig.get(level).getZombieSprintSpeed():me.cryo.zombierool.entity.ZombieEntity.DEFAULT_MOVEMENT_SPEED;}
    public static int healthWave(ServerLevel level,int ticks){
        if(!sprinting(level,ticks))return 1;
        double threshold=Math.max(0,Math.min(1,settings(level).getDouble("sprintThreshold")));
        double ramp=20.0*Math.max(1,settings(level).getInt("rampSeconds"))/Math.max(1,WaveManager.getCurrentWave()/5.0);
        double secondsPerRound=Math.max(1,settings(level).getInt("healthRoundSeconds"));
        long extra=(long)Math.max(0,(ticks-threshold*ramp)/(20.0*secondsPerRound));
        return (int)Math.min(Integer.MAX_VALUE,Math.max(2,WaveManager.getCurrentWave())+extra);
    }
    public static boolean accepts(ServerLevel level,me.cryo.zombierool.block.system.UniversalSpawnerSystem.UniversalSpawnerBlockEntity spawner){
        var channels=new java.util.HashSet<Integer>();for(String part:settings(level).getString("spawnerChannels").split(","))try{channels.add(Integer.parseInt(part.trim()));}catch(NumberFormatException ignored){}
        return spawner.getParsedChannels(spawner.getStartChannels()).stream().anyMatch(channels::contains);
    }
    public static java.util.Optional<net.minecraft.core.BlockPos> spawner(ServerLevel level,me.cryo.zombierool.block.system.UniversalSpawnerSystem.SpawnerMobType type){
        var choices=me.cryo.zombierool.spawner.SpawnerRegistry.getSpawners(level).stream().filter(s->s.getMobType()==type&&s.isActive(level)&&accepts(level,s)).toList();
        int total=choices.stream().mapToInt(s->Math.max(1,s.getSpawnWeight())).sum();if(total==0)return java.util.Optional.empty();int roll=level.random.nextInt(total);
        for(var s:choices){roll-=Math.max(1,s.getSpawnWeight());if(roll<0)return java.util.Optional.of(s.getBlockPos());}return java.util.Optional.empty();
    }
    public static void updateMob(net.minecraft.world.entity.Mob mob,ServerLevel level,int ticks){
        int wave=healthWave(level,ticks),old=mob.getPersistentData().getInt("zr_intro_health_wave");
        if(wave>old){float health=mob.getHealth(),max=mob.getMaxHealth();me.cryo.zombierool.gameplay.wave.WaveMobScaling.applyHealth(mob,wave,level);if(old>0)mob.setHealth(Math.min(mob.getMaxHealth(),mob.getMaxHealth()*(health/Math.max(1,max))));mob.getPersistentData().putInt("zr_intro_health_wave",wave);}
        if(mob instanceof me.cryo.zombierool.entity.ZombieEntity zombie)zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(speed(level,ticks));
    }
}
