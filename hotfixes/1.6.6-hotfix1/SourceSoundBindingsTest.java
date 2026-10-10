import me.cryo.zombierool.scripting.*;
import org.luaj.vm2.lib.jse.JsePlatform;

public final class SourceSoundBindingsTest {
    public static class Spy extends ZombieroolAPI {
        float volume,pitch;String id;
        public Spy(){super(null);}
        public String passthrough(String value){return "ok:"+value;}
        @Override public void playGlobalSound(String id,float volume,float pitch){this.id=id;this.volume=volume;this.pitch=pitch;}
        @Override public void playSoundForPlayer(String uuid,String id,float volume,float pitch){playGlobalSound(id,volume,pitch);}
        @Override public void playDynamicSoundForPlayer(String uuid,String id,float volume,float pitch){playGlobalSound(id,volume,pitch);}
        @Override public void playSound(double x,double y,double z,String id,float volume,float pitch){playGlobalSound(id,volume,pitch);}
    }
    public static void main(String[] ignored){
        var spy=new Spy();var globals=JsePlatform.standardGlobals();globals.set("api",LegacySoundBindings.wrap(spy));int checked=0;
        for(String call:new String[]{"playGlobalSound('s'","playSoundForPlayer('u','s'","playDynamicSoundForPlayer('u','s'","playSound(1,2,3,'s'"}){
            for(String suffix:new String[]{")",",0)",",0.25,0.75)"}){
                globals.load("api:"+call+suffix).call();float volume=suffix.equals(")")?1:suffix.equals(",0)")?0:.25f;float pitch=suffix.equals(",0.25,0.75)")?.75f:1;
                if(spy.volume!=volume||spy.pitch!=pitch||!spy.id.equals("s"))throw new AssertionError(call+suffix);checked++;
            }
        }
        if(!globals.load("return api:passthrough('legacy')").call().tojstring().equals("ok:legacy"))throw new AssertionError("Other API methods changed");
        System.out.println("PASS: "+checked+" Lua sound calls preserve defaults, explicit zero and pitch; other Java methods still bind.");
    }
}
