package local.hotfixtest;

import java.nio.file.*;
import me.cryo.zombierool.client.career.LocalCareerManager;

/** Actual client persistence in a disposable user.home, never the user's career directory. */
public final class CareerPersistenceTest {
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("CAREER CHECK: "+text);}
    private static void resetMemory()throws Exception {
        for(String name:new String[]{"isLoaded","persistBlocked"}){var f=LocalCareerManager.class.getDeclaredField(name);f.setAccessible(true);f.setBoolean(null,false);}
        var f=LocalCareerManager.class.getDeclaredField("currentData");f.setAccessible(true);f.set(null,new LocalCareerManager.CareerData());
        for(String name:new String[]{"memKey","memZrf","memXp","memPrest","memLvl"}){f=LocalCareerManager.class.getDeclaredField(name);f.setAccessible(true);f.setInt(null,name.equals("memLvl")?1:0);}
    }
    public static void run()throws Exception {
        Path home=Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        check(home.startsWith(Path.of("D:/Dev/ZombieRool/build/dev-26.7/client-render-test/test-home")),"Client tests use an isolated career directory");
        var method=LocalCareerManager.class.getDeclaredMethod("getJsonCareerFile");method.setAccessible(true);
        Path file=((java.io.File)method.invoke(null)).toPath(),backup=file.resolveSibling(file.getFileName()+".bak");Files.createDirectories(file.getParent());
        var data=new LocalCareerManager.CareerData();data.zrfBalance=4200;data.currentLevel=17;data.currentXp=131;data.lifetimeKills=123;data.weaponKills.put("m40a3",100);data.globalUnlockedCamos.add("camo_gold");data.equippedCamos.put("m40a3","camo_gold");data.lastChallengeResetTime=java.time.LocalDate.now().toEpochDay();
        String original=new com.google.gson.Gson().toJson(data);
        Files.writeString(file,original);Files.deleteIfExists(backup);resetMemory();LocalCareerManager.forceSave();
        check(Files.readString(file).equals(original)&&!Files.exists(backup),"Closing a client before career load cannot overwrite progress or its backup");
        LocalCareerManager.load();
        check(LocalCareerManager.getLvl()==17&&LocalCareerManager.getXp()==131&&LocalCareerManager.getZrf()==4200&&LocalCareerManager.getData().weaponKills.get("m40a3")==100,"Career load preserves levels, currency and weapon progression");
        check(LocalCareerManager.getData().equippedCamos.get("m40a3").equals("camo_gold"),"Career load preserves equipped and unlocked camos");
        LocalCareerManager.addXp(100,false);java.util.concurrent.ForkJoinPool.commonPool().awaitQuiescence(5,java.util.concurrent.TimeUnit.SECONDS);LocalCareerManager.forceSave();resetMemory();LocalCareerManager.load();
        check(LocalCareerManager.getXp()==231&&LocalCareerManager.getLvl()==17,"Progress survives a save and a fresh in-memory reload");
        check(Files.exists(backup)&&!Files.exists(file.resolveSibling(file.getFileName()+".tmp")),"Completed writes retain a backup and leave no partial primary file");
        Files.writeString(backup,original);Files.writeString(file,"{broken");resetMemory();LocalCareerManager.load();
        check(LocalCareerManager.getLvl()==17&&Files.readString(file).equals(original),"A damaged primary is restored from its valid backup");
        Files.writeString(file,"{broken");Files.writeString(backup,"{broken backup");resetMemory();LocalCareerManager.load();LocalCareerManager.forceSave();
        check(Files.readString(file).equals("{broken")&&Files.readString(backup).equals("{broken backup"),"Unreadable career files remain intact even when daily initialization would save defaults");
        Files.writeString(file,"{}");Files.deleteIfExists(backup);resetMemory();LocalCareerManager.load();LocalCareerManager.forceSave();
        check(Files.readString(file).equals("{}"),"An empty JSON object is not silently accepted as a fresh career");
        Files.delete(file);Path compact=file.resolveSibling(file.getFileName().toString().replace("-",""));Files.writeString(compact,original);resetMemory();LocalCareerManager.load();LocalCareerManager.forceSave();
        check(LocalCareerManager.getLvl()==17&&Files.exists(file)&&Files.readString(compact).equals(original),"Compact and hyphenated UUIDs load the same profile without removing its legacy file");
        Files.writeString(file,original);resetMemory();LocalCareerManager.load();
        var pool=LocalCareerManager.class.getDeclaredField("POSSIBLE_CHALLENGES");pool.setAccessible(true);var challenges=(java.util.List<LocalCareerManager.ChallengeDef>)pool.get(null);
        for(String name:new String[]{"SOULS_COLLECTED","CERBERUS_FED","OBJECTIVES_COMPLETED","TELEPORTER_USES","FLASHED_ENEMIES","PHEROMONE_ENEMIES","EQUIPMENT_KILLS","WINTER_FROZEN","VRIL_CONVERSIONS","ZIPLINE_TRIPS"}){
            var type=LocalCareerManager.ChallengeType.valueOf(name);var server=me.cryo.zombierool.career.CareerManager.ChallengeType.valueOf(name);
            check(challenges.stream().filter(c->c.type==type).count()==2,"New challenge has two targets and a matching server event: "+server);
        }
        var profile=LocalCareerManager.getData();profile.activeChallenges.clear();profile.challengeProgress.clear();profile.activeChallenges.put("new_267",new LocalCareerManager.ChallengeDef(LocalCareerManager.ChallengeType.CERBERUS_FED,10,300));
        LocalCareerManager.progressChallenge(LocalCareerManager.ChallengeType.CERBERUS_FED,4);LocalCareerManager.forceSave();resetMemory();LocalCareerManager.load();
        check(LocalCareerManager.getData().challengeProgress.get("new_267")==4&&LocalCareerManager.getData().activeChallenges.get("new_267").type==LocalCareerManager.ChallengeType.CERBERUS_FED,"New challenge progress survives a save and fresh career reload");
        int balance=LocalCareerManager.getZrf();LocalCareerManager.progressChallenge(LocalCareerManager.ChallengeType.CERBERUS_FED,6);
        check(LocalCareerManager.getZrf()==balance+300&&!LocalCareerManager.getData().activeChallenges.containsKey("new_267"),"New challenge grants its reward exactly once and rotates after completion");
        System.out.println("CAREER PERSISTENCE PASS");
    }
}
