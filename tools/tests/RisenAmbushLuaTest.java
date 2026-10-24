import java.nio.file.*;
import me.cryo.zombierool.shadow.luaj.vm2.*;
import me.cryo.zombierool.shadow.luaj.vm2.lib.jse.JsePlatform;

public final class RisenAmbushLuaTest {
    public static void main(String[] args)throws Exception{
        Globals globals=JsePlatform.standardGlobals();
        globals.set("SCRIPT_PATH",args[0].replace('\\','/'));
        globals.load(Files.readString(Path.of(args[1])),"risen_ambush_test").call();
    }
}
