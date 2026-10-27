package me.cryo.zombierool.gameplay;
/** Global ammunition balance: reserve capacity only; magazine sizes stay authored. */
public final class WeaponReserves {
    public static int capacity(int base,int bonus){
        if(base<0)return base;
        return (int)Math.max(0,Math.min(Integer.MAX_VALUE,2L*((long)base+bonus)));
    }
    public static int doubled(int value){return value<0?value:(int)Math.min(Integer.MAX_VALUE,2L*value);}
    private WeaponReserves(){}
}
