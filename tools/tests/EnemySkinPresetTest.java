import me.cryo.zombierool.config.EnemySkinPresets;
import me.cryo.zombierool.config.WorldConfig;
import net.minecraft.nbt.CompoundTag;

public final class EnemySkinPresetTest {
    private static int checks;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    public static void main(String[] args){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var config=new WorldConfig();
        check(config.getZombieSkinPreset().equals("nazi"),"Default German zombie preset");
        check(config.getHellhoundSkinPreset().equals("auto"),"Hounds follow preset by default");
        for(String zombie:EnemySkinPresets.zombies())for(String hound:EnemySkinPresets.hounds()){
            var patch=new CompoundTag();patch.putString("zombieSkinPreset",zombie);patch.putString("hellhoundSkinPreset",hound);config.loadEditable(patch);
            var saved=new CompoundTag();config.saveEditable(saved);var restored=new WorldConfig();restored.loadEditable(saved);
            check(restored.getZombieSkinPreset().equals(zombie)&&restored.getHellhoundSkinPreset().equals(hound),"Appearance survives map serialization");
        }
        var invalid=new CompoundTag();invalid.putString("zombieSkinPreset","../missing");invalid.putString("hellhoundSkinPreset","missing");config.loadEditable(invalid);
        check(config.getZombieSkinPreset().equals("nazi")&&config.getHellhoundSkinPreset().equals("auto"),"Invalid settings fall back safely");
        config.loadEditable(new CompoundTag());check(config.getZombieSkinPreset().equals("nazi"),"Legacy partial settings keep default");
        var custom=new CompoundTag();custom.putString("zombieSkinPreset","five");config.loadEditable(custom);config.resetGeneral();
        check(config.getZombieSkinPreset().equals("nazi")&&config.getHellhoundSkinPreset().equals("auto"),"General reset restores defaults");
        for(String id:new String[]{"", "custom_map_skin", "@preset/zombies/nazi/-1", "@preset/zombies/nazi/99999", "@preset/unknown/nazi/0", "@preset/zombies/../../evil"})check(EnemySkinPresets.texture(id)==null,"Invalid/custom appearance is not interpreted as built-in");
        check(EnemySkinPresets.texture("@preset/zombies/five/0").getPath().contains("presets/five/"),"Five resolves baked skin");
        check(EnemySkinPresets.texture("@preset/hounds/t4/0").getPath().contains("presets/hounds/t4/"),"Hound resolves correct model UV texture");
        check(EnemySkinPresets.gearMesh("@preset/zombies/origins/0")!=null,"Origins carries original helmet geometry");
        check(EnemySkinPresets.gearMesh("custom_map_skin")==null,"Custom skins don't inherit preset gear");
        System.out.println("PASS: "+checks+" enemy preset persistence, validation and appearance checks");
    }
}
