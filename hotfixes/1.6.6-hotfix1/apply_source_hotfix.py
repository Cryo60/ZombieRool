"""Port the verified release hooks into readable sources, preserving development features."""
from pathlib import Path
import re
import sys

HERE = Path(__file__).resolve().parent
target = Path(sys.argv[1]).resolve()
development = '--development' in sys.argv
base = target / 'java/me/cryo/zombierool'
assert (base / 'ZombieroolMod.java').is_file()

def edit(relative, transform):
    path = base / relative
    before = path.read_text(encoding='utf-8')
    after = transform(before)
    if before != after:
        path.write_text(after, encoding='utf-8')

def replace(text, old, new):
    if new in text: return text
    assert old in text, old
    return text.replace(old, new)

def body(text, signature, replacement=None, prefix='', suffix=''):
    start = text.index('{', text.index(signature))
    depth = 1
    cursor = start + 1
    while depth:
        if text[cursor] == '{': depth += 1
        elif text[cursor] == '}': depth -= 1
        cursor += 1
    original = text[start + 1:cursor - 1]
    if prefix and prefix.strip() in original: prefix = ''
    if suffix and suffix.strip() in original: suffix = ''
    return text[:start + 1] + prefix + (original if replacement is None else replacement) + suffix + text[cursor - 1:]

for source in (HERE / 'replacement-src/me/cryo/zombierool').rglob('*.java'):
    path = base / source.relative_to(HERE / 'replacement-src/me/cryo/zombierool')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(source.read_bytes())
for source in (HERE / 'src/me/cryo/zombierool').rglob('*.java'):
    relative = source.relative_to(HERE / 'src/me/cryo/zombierool')
    if development and relative.as_posix() in ('hotfix/HotfixGameplay.java','hotfix/HotfixMatchConfig.java'):
        continue
    path = base / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(source.read_text(encoding='utf-8').replace('me.cryo.zombierool.shadow.luaj', 'org.luaj'), encoding='utf-8')
(base / 'scripting/LegacySoundBindings.java').write_bytes((HERE / 'LegacySoundBindings.java').read_bytes())

edit('mixins/PlayerMixin.java', lambda s: replace(s, 'player.heal(0.01F);', 'player.heal(0.015F);'))
edit('client/gui/MainMenuExtensions.java', lambda s: body(s, 'void onOpening(', prefix='\n        if (ModList.get().isLoaded("essential")) return;'))
edit('client/gui/WaWPauseScreen.java', lambda s: replace(s, '        this.entries.add(new Entry("gui.zombierool.pause.quit", this::quit));', '        if (me.cryo.zombierool.hotfix.HotfixMenuCompat.canOpenLan())\n            this.entries.add(new Entry("menu.shareToLan", me.cryo.zombierool.hotfix.HotfixMenuCompat.lanAction(this)));\n        this.entries.add(new Entry("gui.zombierool.pause.quit", this::quit));') if 'menu.shareToLan' not in s else s)
edit('player/PlayerDownManager.java', lambda s: body(s, 'void resetAll()', suffix='\n        me.cryo.zombierool.hotfix.HotfixDownReset.resetAll();\n    '))
cleanup = '\n        var uuid=event.getEntity().getUUID();\n        playersDown.remove(uuid);soloQuickRevivePlayers.remove(uuid);savedPlayerInventories.remove(uuid);lastKnownHandgun.remove(uuid);\n        me.cryo.zombierool.hotfix.HotfixDownReset.resetPlayer(event.getEntity());'
edit('player/PlayerDownManager.java', lambda s: body(s, 'void onPlayerRespawn(', prefix=cleanup))
for entity in ('ZombieEntity','CrawlerEntity'):
    edit('entity/' + entity + '.java', lambda s: replace(s, 'new NearestAttackableTargetGoal<>(this, Player.class, false, false)', 'new me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal(this, Player.class, false, false)'))
edit('mixins/CustomMobMixin.java', lambda s: replace(s, 'mob.setTarget(chosenTarget);', 'me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.chooseAndSet(mob, chosenTarget);'))
edit('core/block/ZRSandbagBlock.java', lambda s: s if 'getBlockPathType(' in s else s[:-s[::-1].index('}')-1] + '''    @Override public net.minecraft.world.level.pathfinder.BlockPathTypes getBlockPathType(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.Mob mob) {
        return net.minecraft.world.level.pathfinder.BlockPathTypes.BLOCKED;
    }
}
''')
edit('event/ServerEventHandler.java', lambda s: body(s, 'boolean isMatchInteractionLocked(', replacement='\n        return false;\n    '))
edit('mixins/HopperBlockEntityMixin.java', lambda s: body(body(s, 'void zombierool$blockPrivateDrops(', replacement='\n    '), 'void zombierool$blockMapWeapons(', replacement='\n    '))

helper = 'me.cryo.zombierool.gameplay.CompatibilityFixes' if development else 'me.cryo.zombierool.hotfix.HotfixGameplay'
edit('block/RestrictBlock.java', lambda s: body(s, 'VoxelShape getCollisionShape(', replacement=f'\n        return {helper}.restrictCollision(context);\n    '))
for relative in ('core/manager/BallisticManager.java','procedures/MeleeAttackHandler.java'):
    edit(relative, lambda s: re.sub(r'(?<![\w.])(shooter\.level\(\)|level)\.clip\(new ClipContext\(', lambda m: helper + '.clip(' + m.group(1) + ', new ClipContext(', s))
edit('integration/TacZImpl.java', lambda s: body(s, 'void onAmmoHitBlock(', prefix='\n            if (event.getState().getBlock() instanceof me.cryo.zombierool.block.RestrictBlock) {event.setCanceled(true);return;}'))
edit('scripting/LuaScriptManager.java', lambda s: replace(s, 'CoerceJavaToLua.coerce(new ZombieroolAPI(level))', 'LegacySoundBindings.wrap(new ZombieroolAPI(level))'))
edit('scripting/LuaScriptManager.java', lambda s: replace(s, 'globals.loadfile(script.getAbsolutePath())', helper + '.loadScript(globals,script.getAbsolutePath())') if 'globals.loadfile(script.getAbsolutePath())' in s else s)
edit('scripting/LuaScriptManager.java', lambda s: replace(s, 'scriptDir.listFiles((dir, name) -> name.endsWith(".lua"));', helper + '.sortScripts(scriptDir.listFiles((dir, name) -> name.endsWith(".lua")));') if '.sortScripts(' not in s else s)
edit('client/network/ClientPacketEffects.java', lambda s: replace(s, '        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundLocation);', '        soundLocation = ' + helper + '.resolveLegacySound(soundLocation);\n        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundLocation);') if '.resolveLegacySound(' not in s else s)
overloads='''    public void playGlobalSound(String id) { playGlobalSound(id,1,1); }
    public void playGlobalSound(String id,float volume) { playGlobalSound(id,volume,1); }
    public void playSoundForPlayer(String uuid,String id) { playSoundForPlayer(uuid,id,1,1); }
    public void playSoundForPlayer(String uuid,String id,float volume) { playSoundForPlayer(uuid,id,volume,1); }
    public void playDynamicSoundForPlayer(String uuid,String id) { playDynamicSoundForPlayer(uuid,id,1,1); }
    public void playDynamicSoundForPlayer(String uuid,String id,float volume) { playDynamicSoundForPlayer(uuid,id,volume,1); }
    public void playSound(double x,double y,double z,String id) { playSound(x,y,z,id,1,1); }
    public void playSound(double x,double y,double z,String id,float volume) { playSound(x,y,z,id,volume,1); }
'''
edit('scripting/ZombieroolAPI.java', lambda s: s if re.search(r'void playGlobalSound\(String \w+\)',s) else s.replace('    public void playGlobalSound(String soundId, float volume, float pitch)',overloads+'    public void playGlobalSound(String soundId, float volume, float pitch)'))
snapshot = 'MatchConfigSnapshot' if development else 'me.cryo.zombierool.hotfix.HotfixMatchConfig'
edit('gameplay/WaveManager.java', lambda s: replace(s, '        MatchWorldJournal.restore(level);', '        MatchWorldJournal.restore(level);\n        ' + snapshot + '.begin(level);') if snapshot+'.begin(level);' not in s else s)
edit('gameplay/WaveManager.java', lambda s: body(s, 'void endMatch(', prefix='\n        '+snapshot+'.restore(level);') if snapshot+'.restore(level);' not in s else s)
edit('gameplay/WaveManager.java', lambda s: replace(s, '            MatchWorldJournal.restore(overworld);', '            MatchWorldJournal.restore(overworld);\n            '+snapshot+'.restore(overworld);') if snapshot+'.restore(overworld);' not in s else s)
edit('gameplay/WaveManager.java', lambda s: replace(s, '        LuaScriptManager.callEvent("OnGameStart");', '        LuaScriptManager.loadScripts(level);\n        LuaScriptManager.callEvent("OnGameStart");') if 'LuaScriptManager.loadScripts(level);' not in s else s)
edit('gameplay/WaveManager.java', lambda s: re.sub(r'ube\.getMobType\(\) == UniversalSpawnerSystem\.SpawnerMobType\.PLAYER(?! && ube\.isActive\(level\))', 'ube.getMobType() == UniversalSpawnerSystem.SpawnerMobType.PLAYER && ube.isActive(level)',s))

if development:
    for source in base.rglob('*.java'):
        mirror=target/'src/main/java/me/cryo/zombierool'/source.relative_to(base)
        mirror.parent.mkdir(parents=True,exist_ok=True)
        mirror.write_bytes(source.read_bytes())
print('Integrated authorized hotfix source changes into', target)
