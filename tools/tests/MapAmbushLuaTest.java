import java.nio.file.*;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Standalone Lua encounter checks; requires libs/luaj-jse-3.0.1.jar. */
public final class MapAmbushLuaTest {
    public static void main(String[] args) throws Exception {
        Globals globals = JsePlatform.standardGlobals();
        globals.set("SCRIPT_PATH", args[0].replace('\\', '/'));
        globals.load(Files.readString(Path.of(args[1])), "map_ambush_test").call();
    }
}
