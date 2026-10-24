package me.cryo.zombierool.gameplay;

import net.minecraft.nbt.*;

public final class MapActorTemplates {
    private MapActorTemplates(){}
    public static final java.util.List<String> BUILTINS=java.util.List.of("builtin_zombie","builtin_crawler","builtin_hellhound","builtin_white_knight","builtin_ghoul","builtin_scourge_zombie","builtin_skeleton","builtin_abomination","builtin_geist","builtin_panzer","builtin_brutus","builtin_pentagon_thief","builtin_napalm_minion","builtin_skeleton_minion","builtin_george_romero","builtin_astronaut");
    public static CompoundTag builtin(String id){
        if(!BUILTINS.contains(id))return new CompoundTag();
        String kind=id.substring(8);String entity=switch(kind){case "ghoul"->"scourge_ghoul";case "skeleton"->"scourge_skeleton";default->kind;};
        var n=new CompoundTag();n.putString("name",kind.replace('_',' '));n.putString("nameKey","entity.zombierool.map_actor."+kind);n.putString("baseEntity",entity);n.putString("behavior","melee");n.putDouble("health",kind.equals("abomination")?250:java.util.Set.of("panzer","brutus","george_romero","astronaut").contains(kind)?400:40);n.putDouble("speed",.23);n.putDouble("aggroRadius",16);n.putDouble("wanderRadius",6);n.putDouble("leashRadius",32);
        return MapActors.sanitize(id,n);
    }
    public static CompoundTag box(String bone,double x,double y,double z,double sx,double sy,double sz,double r,double g,double b) {
        var n=new CompoundTag();n.putString("bone",bone);String[] keys={"x","y","z","sx","sy","sz","r","g","b"};double[] values={x,y,z,sx,sy,sz,r,g,b};for(int i=0;i<keys.length;i++)n.putDouble(keys[i],values[i]);return n;
    }
    public static ListTag humanoid(){var n=new ListTag();n.add(box("head",-4,-8,-4,8,8,8,.5,.65,.45));n.add(box("body",-4,0,-2,8,12,4,.3,.35,.3));n.add(box("left_arm",4,0,-2,4,12,4,.5,.65,.45));n.add(box("right_arm",-8,0,-2,4,12,4,.5,.65,.45));n.add(box("left_leg",0,12,-2,4,12,4,.3,.25,.2));n.add(box("right_leg",-4,12,-2,4,12,4,.3,.25,.2));return n;}
    public static CompoundTag animations(){try{return TagParser.parseTag("""
        {idle:{duration:60,loop:true,frames:[{time:0,body:{y:0}},{time:30,body:{y:-0.3}},{time:60,body:{y:0}}]},
         cast:{duration:30,loop:false,frames:[{time:0,left_arm:{rx:0},right_arm:{rx:0}},{time:12,left_arm:{rx:-110},right_arm:{rx:-110}},{time:30,left_arm:{rx:-100},right_arm:{rx:-100}}]},
         death:{duration:24,loop:false,frames:[{time:0,root:{rx:0}},{time:24,root:{rx:85}}]}}
        """);}catch(Exception e){throw new IllegalStateException(e);}}
    public static CompoundTag profile(boolean boss){var n=new CompoundTag();n.putString("name",boss?"New boss":"New enemy");n.putString("behavior","melee");n.putBoolean("boss",boss);n.putString("callback",boss?"my_boss":"my_enemy");n.putDouble("health",boss?500:40);n.put("boxes",humanoid());n.put("animations",animations());return MapActors.sanitize(boss?"my_boss":"my_enemy",n);}
    public static CompoundTag marker(){var n=new CompoundTag();n.putString("profile","");n.putString("healthMode","wave");n.putString("trigger","game_start");n.putInt("firstWave",1);n.putInt("waveInterval",1);n.putInt("cooldownTicks",600);n.putInt("maxAlive",1);n.putInt("batch",1);n.putDouble("spawnRadius",32);n.putDouble("knockbackResistance",1);n.putBoolean("enabled",true);n.putBoolean("requireRedstone",false);return n;}
    public static String script(String callback){MapActors.id(callback);return "-- Map actor callbacks. Tick runs every 10 server ticks.\n-- Base navigation, melee, aggro radius and wandering are already available.\nfunction "+callback+"_spawn(uuid)\n    -- Initialise per-entity data here.\nend\n\nfunction "+callback+"_tick(uuid)\n    local actor = ZombieroolAPI:getMapActor(uuid)\n    -- actor.target, health, x/y/z, homeX/homeY/homeZ are available.\n    -- Example: ZombieroolAPI:mapActorAttack(uuid, 6, 3, \"minecraft:slowness\", 40)\nend\n\nfunction "+callback+"_death(uuid)\n    -- Rewards and quest progression.\nend\n";}
}
