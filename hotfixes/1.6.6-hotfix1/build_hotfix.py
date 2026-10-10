"""Build only the 1.6.6 hotfix; never compile or package the current development mod."""
from pathlib import Path
import hashlib
import json
import subprocess
import shutil
from zipfile import ZipFile
from resources_hotfix import resources

REPLACEMENTS = ["maptexture/MapTextures", "maptexture/TextureAtlasImport", "client/TextureKitScreen", "client/TexturePixelEditor", "block/system/MimicSystem", "block/system/UniversalSpawnerSystem", "block/system/GlassDefenseDoorBlock", "network/packet/S2CMapTextureSlotPacket"]
def replacement(name):
    return any(name == "me/cryo/zombierool/" + root + ".class" or name.startswith("me/cryo/zombierool/" + root + "$") and name.endswith(".class") for root in REPLACEMENTS)

ROOT = Path(__file__).resolve().parents[2]
SCRATCH = ROOT / "build/hotfix-1.6.6-hotfix1"
BASE = ROOT / "build/libs/zombierool-1.6.6-published.jar"
OUTPUT = ROOT / "build/libs/zombierool-1.6.6-hotfix1.jar"
BASE_SHA256 = "6dd90c8fc2e14069a84ec77e36c980faea0b979eaa8944e2494fec887a1881ca"
JAVA = Path("C:/Java/jdk-17.0.20.1+1/bin/java.exe")
JAVAC = JAVA.with_name("javac.exe")
FART = Path.home() / ".gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/ForgeAutoRenamingTool/1.0.2/ForgeAutoRenamingTool-1.0.2-all.jar"
SRG_FORGE = Path.home() / ".gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.1.3/forge-1.20.1-47.1.3-srg.jar"

def run(command, log):
    with (SCRATCH / log).open("w", encoding="utf-8") as stream:
        subprocess.run([str(v) for v in command], cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)

def compile_sources(sources, target, classpath, label):
    target.mkdir(parents=True, exist_ok=True)
    args = ["-proc:none", "-encoding", "UTF-8", "-classpath", classpath, "-d", str(target)] + [str(p) for p in sources]
    argfile = SCRATCH / (label + ".args")
    argfile.write_text("\n".join('"' + a.replace("\\", "/") + '"' for a in args), encoding="utf-8")
    run([JAVAC, "@" + str(argfile)], label + ".log")

def remap(source, output, names, libraries, label):
    command = [JAVA, "-jar", FART, "--input", source, "--output", output, "--names", names, "--threads", "2"]
    for library in libraries:
        command += ["--lib", library]
    run(command, label + ".log")

def main():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest() == BASE_SHA256, "Wrong 1.6.6 baseline"
    SCRATCH.mkdir(parents=True, exist_ok=True)
    # This file records dependency paths already resolved by the normal project build.
    lines = (ROOT / "build/verify-fixes/javac.args").read_text(encoding="utf-8").splitlines()
    dependency_cp = lines[lines.index("-classpath") + 1].strip('"')
    dependencies = [p for p in dependency_cp.split(";") if "build/classes/java/main" not in p.replace("\\", "/") and "build/resources/main" not in p.replace("\\", "/")]
    forge = next(p for p in dependencies if "minecraft_user_repo" in p and p.endswith(".jar"))
    mapped = SCRATCH / "baseline-mapped.jar"
    remap(BASE, mapped, ROOT / "build/createSrgToMcp/output.srg", [SRG_FORGE], "remap-baseline")
    helpers = SCRATCH / "helper-classes"
    assert helpers.resolve().is_relative_to(SCRATCH.resolve())
    if helpers.exists(): shutil.rmtree(helpers)
    sources = list(Path(__file__).parent.joinpath("src").rglob("*.java"))
    sources += list(Path(__file__).parent.joinpath("replacement-src").rglob("*.java"))
    compile_sources(sources, helpers, str(mapped) + ";" + ";".join(dependencies), "helpers-javac")
    helper_jar = SCRATCH / "helpers-mapped.jar"
    with ZipFile(helper_jar, "w") as archive:
        for file in helpers.rglob("*.class"):
            archive.write(file, file.relative_to(helpers))
    remapped_helpers = SCRATCH / "helpers-srg.jar"
    remap(helper_jar, remapped_helpers, ROOT / "build/createMcpToSrg/output.tsrg", [forge, mapped], "reobf-helpers")
    tool_classes = SCRATCH / "patch-tool-classes"
    compile_sources([Path(__file__).with_name("Patch166.java")], tool_classes, dependency_cp, "patch-javac")
    run([JAVA, "-classpath", str(tool_classes) + ";" + dependency_cp, "Patch166", BASE, remapped_helpers, OUTPUT], "patch")
    with ZipFile(BASE) as original:
        resource_changes = resources(original)
    packaged = SCRATCH / "hotfix-resources.jar"
    with ZipFile(OUTPUT) as source, ZipFile(packaged, "w") as target:
        for name in source.namelist(): target.writestr(name, resource_changes.get(name, source.read(name)))
        for name, data in resource_changes.items():
            if name not in source.namelist(): target.writestr(name, data)
    shutil.copyfile(packaged, OUTPUT)
    with ZipFile(BASE) as before, ZipFile(OUTPUT) as after:
        changed = [n for n in before.namelist() if before.read(n) != after.read(n)]
        added = sorted(set(after.namelist()) - set(before.namelist()))
        assert set(before.namelist()).issubset(after.namelist())
        expected_changed = {"META-INF/MANIFEST.MF", "META-INF/mods.toml"} | {
            "me/cryo/zombierool/" + name + ".class" for name in [
                "shadow/luaj/vm2/lib/jse/JavaMember", "scripting/ZombieroolAPI", "client/network/ClientPacketEffects", "client/gui/MainMenuExtensions", "client/gui/WaWPauseScreen",
                "entity/CrawlerEntity", "entity/ZombieEntity", "mixins/PlayerMixin", "player/PlayerDownManager", "mixins/CustomMobMixin", "core/block/ZRSandbagBlock", "event/ServerEventHandler", "mixins/HopperBlockEntityMixin", "scripting/LuaScriptManager", "gameplay/WaveManager", "core/manager/BallisticManager", "procedures/MeleeAttackHandler", "block/RestrictBlock", "integration/TacZImpl$TacZEventHandlers"
            ]
        }
        expected_added = {"me/cryo/zombierool/hotfix/" + name + ".class" for name in [
            "HotfixMatchConfig", "HotfixDownReset", "HotfixMenuCompat", "ReachablePlayerTargetGoal", "HotfixGameplay", "HotfixGameplay$1", "HotfixTextureFolders", "HotfixTextureFolders$Request"
        ]}
        expected_added |= {"me/cryo/zombierool/" + name + ".class" for name in ["WaveManager", "WorldConfig", "PerksManager"]}
        with ZipFile(remapped_helpers) as compiled:
            replacements = {n for n in compiled.namelist() if replacement(n)}
        expected_changed |= {n for n in replacements if n in before.namelist() and before.read(n) != after.read(n)}
        expected_added |= replacements - set(before.namelist())
        expected_changed |= {n for n in resource_changes if n in before.namelist() and before.read(n) != after.read(n)}
        expected_added |= set(resource_changes) - set(before.namelist())
        assert set(changed) == expected_changed and set(added) == expected_added
        assert before.read("mixins.zombierool.json") == after.read("mixins.zombierool.json")
        assert before.read("me/cryo/zombierool/network/NetworkHandler.class") == after.read("me/cryo/zombierool/network/NetworkHandler.class")
        assert all(before.read(n) == after.read(n) for n in before.namelist() if n.startswith("assets/") and n not in resource_changes)
        report = {"baselineSha256": BASE_SHA256, "hotfixSha256": hashlib.sha256(OUTPUT.read_bytes()).hexdigest(), "changed": changed, "added": added, "removed": []}
    (SCRATCH / "content-diff.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(OUTPUT)
    print("Verified: explicit class/resource whitelist; no unrelated 1.7 features; core network unchanged.")

if __name__ == "__main__":
    main()
