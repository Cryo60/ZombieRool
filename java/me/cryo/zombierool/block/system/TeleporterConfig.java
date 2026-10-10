package me.cryo.zombierool.block.system;

import net.minecraft.nbt.CompoundTag;

/** Map-author settings; transient countdowns never replace these values. */
public final class TeleporterConfig {
    public enum Mode { DIRECT, DER_RIESE, KINO, MOON, DEFCON, DENIZEN, ROCKET }
    public enum Role { SOURCE, RECEIVER, SWITCH }
    private TeleporterConfig(){}
    public static CompoundTag defaults(Mode mode) {
        CompoundTag n=new CompoundTag();
        n.putString("mode",mode.name());n.putString("role",Role.SOURCE.name());n.putString("id","");n.putString("channel","main");
        for(String key:new String[]{"destination","returnId","secretIds","condition","blockedCondition"})n.putString(key,"");
        n.putBoolean("invisible",false);n.putInt("chargeTime",6);n.putBoolean("enabled",true);n.putBoolean("requiresPower",true);n.putBoolean("requiresLink",false);n.putBoolean("allPlayers",false);
        n.putBoolean("automatic",false);n.putBoolean("relink",false);n.putBoolean("sharedCooldown",true);n.putBoolean("jumpOnly",false);
        n.putInt("cost",0);n.putInt("linkTimeout",30);n.putInt("stay",0);n.putInt("secretStay",5);n.putInt("cooldown",30);n.putInt("papLinks",0);n.putInt("requiredSwitches",4);
        n.putDouble("secretChance",.5);n.putDouble("yaw",0);
        switch(mode) {
            case DER_RIESE -> {n.putBoolean("requiresLink",true);n.putInt("cost",1500);n.putInt("papLinks",3);}
            case KINO -> {n.putBoolean("requiresLink",true);n.putBoolean("relink",true);n.putInt("cost",1500);n.putInt("stay",30);n.putInt("cooldown",60);}
            case MOON -> {n.putBoolean("requiresPower",false);n.putBoolean("allPlayers",true);n.putBoolean("automatic",true);n.putInt("cooldown",15);}
            case DEFCON -> {n.putBoolean("automatic",true);n.putInt("stay",30);}
            case DENIZEN -> {n.putBoolean("requiresPower",false);n.putBoolean("automatic",true);n.putBoolean("jumpOnly",true);}
            case ROCKET -> {n.putInt("cost",500);n.putString("blockedCondition","rocket_test");}
            default -> {}
        }
        return n;
    }
    public static CompoundTag sanitize(CompoundTag input) {
        Mode mode;try{mode=Mode.valueOf(input.getString("mode"));}catch(Exception e){mode=Mode.DIRECT;}
        CompoundTag n=defaults(mode);
        for(String key:new String[]{"id","channel","destination","returnId","secretIds","condition","blockedCondition"})if(input.contains(key))n.putString(key,input.getString(key).trim().substring(0,Math.min(128,input.getString(key).trim().length())));
        try{n.putString("role",Role.valueOf(input.getString("role")).name());}catch(Exception ignored){}
        for(String key:new String[]{"invisible","enabled","requiresPower","requiresLink","allPlayers","automatic","relink","sharedCooldown","jumpOnly"})if(input.contains(key))n.putBoolean(key,input.getBoolean(key));
        for(String key:new String[]{"chargeTime","cost","linkTimeout","stay","secretStay","cooldown","papLinks","requiredSwitches"})if(input.contains(key)) {
            int max=key.equals("chargeTime")?10:key.equals("cost")?1000000:key.equals("papLinks")||key.equals("requiredSwitches")?64:3600;
            int min=key.equals("chargeTime")?1:key.equals("linkTimeout")||key.equals("secretStay")||key.equals("requiredSwitches")?1:0;
            n.putInt(key,Math.max(min,Math.min(max,input.getInt(key))));
        }
        for(String key:new String[]{"secretChance","yaw"})if(input.contains(key)){
            double v=input.getDouble(key);if(Double.isFinite(v))n.putDouble(key,key.equals("secretChance")?Math.max(0,Math.min(1,v)):v%360);
        }
        return n;
    }
}
