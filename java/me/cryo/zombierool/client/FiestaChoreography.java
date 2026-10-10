package me.cryo.zombierool.client;
/** Shared eight-second phrase: watch, slow turn, watch, fast turns. */
public final class FiestaChoreography {
    private FiestaChoreography() {}
    public static float turn(float ticks) {
        float t=Math.max(0,ticks)%160;
        if(t<30)return 0;
        if(t<80)return (t-30)*7.2f;
        if(t<100)return 360;
        if(t<140)return 360+(t-100)*36;
        return 1800;
    }
}
