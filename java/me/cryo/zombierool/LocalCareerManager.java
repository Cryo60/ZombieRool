package me.cryo.zombierool.client.career;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.client.gui.CareerScreen;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.C2SSyncEquippedCamosPacket;
import me.cryo.zombierool.network.packet.C2SSyncEquippedSkinsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;

@OnlyIn(Dist.CLIENT)
public class LocalCareerManager {

    private static final File GLOBAL_DIR = new File(System.getProperty("user.home"), ".zombierool");
    private static final File CAREER_FILE = new File(GLOBAL_DIR, "zombierool_career.dat");
    private static final File LEGACY_CAREER_FILE = FMLPaths.GAMEDIR.get().resolve("zombierool_career.dat").toFile();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int MAX_PRESTIGE = 5;
    public static final int GOLD_LEVEL_REQ = 50;
    public static final int BLACK_ICE_LEVEL_REQ = 100;

    private static CareerData currentData = new CareerData();
    private static int memKey = new Random().nextInt();
    private static int memZrf = 0;
    private static int memLvl = 1 ^ memKey;
    private static int memXp = 0;
    private static int memPrest = 0;

    private static final Object FILE_LOCK = new Object();
    private static boolean isLoaded = false;

    public static int getZrf() { return memZrf ^ memKey; }
    public static int getLvl() { return memLvl ^ memKey; }
    public static int getXp() { return memXp ^ memKey; }
    public static int getPrest() { return memPrest ^ memKey; }

    private static void setZrf(int val) { memZrf = val ^ memKey; currentData.zrfBalance = val; }
    private static void setLvl(int val) { memLvl = val ^ memKey; currentData.currentLevel = val; }
    private static void setXp(int val) { memXp = val ^ memKey; currentData.currentXp = val; }
    private static void setPrest(int val) { memPrest = val ^ memKey; currentData.prestigeLevel = val; }

    public static class Notification {
        public String text;
        public int color;
        public long spawnTime;
        public Notification(String text, int color) {
            this.text = text;
            this.color = color;
            this.spawnTime = System.currentTimeMillis();
        }
    }

    public static final List<Notification> activeNotifications = new CopyOnWriteArrayList<>();

    public static void pushNotification(String text, int color) {
        activeNotifications.add(new Notification(text, color));
    }

    public static final Map<String, SkinDef> SKINS = new LinkedHashMap<>();
    public static final Map<String, CamoDef> CAMOS = new LinkedHashMap<>();

    static {
        SKINS.put("golden_deagle", new SkinDef("skin.zombierool.golden_deagle", "HEADSHOTS", 100, 0, "deagle"));
        SKINS.put("silver_m1911", new SkinDef("skin.zombierool.silver_m1911", "BUY", 0, 200, "m1911"));
        SKINS.put("vandal_dragon", new SkinDef("skin.zombierool.vandal_dragon", "PAP", 100, 0, "vandal"));
        SKINS.put("royal_guard_p90", new SkinDef("skin.zombierool.royal_guard_p90", "BUY", 0, 20000, "p90"));
        SKINS.put("royal_guard_famas", new SkinDef("skin.zombierool.royal_guard_famas", "BUY", 0, 20000, "famas"));

        CAMOS.put("camo_woodland", new CamoDef("camo.zombierool.woodland", 500, false, 0, false, "common"));
        CAMOS.put("camo_desert", new CamoDef("camo.zombierool.desert", 500, false, 0, false, "common"));
        CAMOS.put("camo_arctic", new CamoDef("camo.zombierool.arctic", 500, false, 0, false, "common"));
        CAMOS.put("camo_jungle", new CamoDef("camo.zombierool.jungle", 600, false, 0, false, "common"));
        CAMOS.put("camo_urban", new CamoDef("camo.zombierool.urban", 600, false, 0, false, "common"));
        CAMOS.put("camo_sandstorm", new CamoDef("camo.zombierool.sandstorm", 500, false, 0, false, "common"));
        CAMOS.put("camo_olive", new CamoDef("camo.zombierool.olive", 500, false, 0, false, "common"));
        CAMOS.put("camo_multicam", new CamoDef("camo.zombierool.multicam", 600, false, 0, false, "common"));

        CAMOS.put("camo_red_tiger", new CamoDef("camo.zombierool.red_tiger", 1000, false, 0, false, "rare"));
        CAMOS.put("camo_blue_tiger", new CamoDef("camo.zombierool.blue_tiger", 1000, false, 0, false, "rare"));
        CAMOS.put("camo_digital", new CamoDef("camo.zombierool.digital", 1200, false, 0, false, "rare"));
        CAMOS.put("camo_cherry_blossom", new CamoDef("camo.zombierool.cherry_blossom", 1500, false, 0, false, "rare"));
        CAMOS.put("camo_bloodshot", new CamoDef("camo.zombierool.bloodshot", 1500, false, 0, false, "rare"));
        CAMOS.put("camo_hex", new CamoDef("camo.zombierool.hex", 2000, false, 0, false, "rare"));
        CAMOS.put("camo_midnight", new CamoDef("camo.zombierool.midnight", 2000, false, 0, false, "rare"));
        CAMOS.put("camo_toxic", new CamoDef("camo.zombierool.toxic", 2500, false, 0, false, "rare"));
        CAMOS.put("camo_coral", new CamoDef("camo.zombierool.coral", 2500, false, 0, false, "rare"));

        CAMOS.put("camo_trollface", new CamoDef("camo.zombierool.trollface", 3000, false, 0, false, "epic"));
        CAMOS.put("camo_doge", new CamoDef("camo.zombierool.doge", 3000, false, 0, false, "epic"));
        CAMOS.put("camo_gigachad", new CamoDef("camo.zombierool.gigachad", 3000, false, 0, false, "epic"));
        CAMOS.put("camo_lordaeron", new CamoDef("camo.zombierool.lordaeron", 5000, false, 0, false, "epic"));
        CAMOS.put("camo_quel_thalas", new CamoDef("camo.zombierool.quel_thalas", 5000, false, 0, false, "epic"));
        CAMOS.put("camo_alliance", new CamoDef("camo.zombierool.alliance", 5000, false, 0, false, "epic"));
        CAMOS.put("camo_scarlet_crusade", new CamoDef("camo.zombierool.scarlet_crusade", 5000, false, 0, false, "epic"));
        CAMOS.put("camo_burning_legion", new CamoDef("camo.zombierool.burning_legion", 5000, false, 0, false, "epic"));
        CAMOS.put("camo_scourge", new CamoDef("camo.zombierool.scourge", 5000, false, 0, false, "epic"));
        CAMOS.put("camo_forsaken", new CamoDef("camo.zombierool.forsaken", 5000, false, 0, false, "epic"));
        CAMOS.put("camo_silvermoon", new CamoDef("camo.zombierool.silvermoon", 5000, false, 0, false, "epic"));
        CAMOS.put("camo_horde", new CamoDef("camo.zombierool.horde", 5000, false, 0, false, "epic"));
        CAMOS.put("camo_gold", new CamoDef("camo.zombierool.gold", 7500, false, 0, true, "epic")); 
        CAMOS.put("camo_diamond", new CamoDef("camo.zombierool.diamond", 10000, false, 0, true, "epic"));
        CAMOS.put("camo_damascus", new CamoDef("camo.zombierool.damascus", 12500, false, 0, true, "epic"));

        CAMOS.put("camo_dark_matter", new CamoDef("camo.zombierool.dark_matter", 15000, false, 0, false, "legendary"));
        CAMOS.put("camo_magma", new CamoDef("camo.zombierool.magma", 12500, false, 0, true, "legendary"));
        CAMOS.put("camo_matrix", new CamoDef("camo.zombierool.matrix", 15000, false, 0, true, "legendary"));
        CAMOS.put("camo_plasma", new CamoDef("camo.zombierool.plasma", 15000, false, 0, true, "legendary")); 
        CAMOS.put("camo_retro_8bit", new CamoDef("camo.zombierool.retro_8bit", 15000, false, 0, true, "legendary"));
        CAMOS.put("camo_mojang", new CamoDef("camo.zombierool.mojang", 15000, false, 0, true, "legendary"));
        CAMOS.put("camo_bedrock", new CamoDef("camo.zombierool.bedrock", 15000, false, 0, true, "legendary"));
        CAMOS.put("camo_missing_textures", new CamoDef("camo.zombierool.missing_textures", 15000, false, 0, true, "legendary"));
        CAMOS.put("camo_merc_red", new CamoDef("camo.zombierool.merc_red", 10000, false, 0, false, "legendary"));
        CAMOS.put("camo_fast_food", new CamoDef("camo.zombierool.fast_food", 10000, false, 0, false, "legendary"));
        CAMOS.put("camo_masterchief", new CamoDef("camo.zombierool.masterchief", 10000, false, 0, false, "legendary"));
        CAMOS.put("camo_asiimov", new CamoDef("camo.zombierool.asiimov", 15000, false, 0, false, "legendary"));
        CAMOS.put("camo_hyper_beast", new CamoDef("camo.zombierool.hyper_beast", 15000, false, 0, false, "legendary"));
        CAMOS.put("camo_rain_storm", new CamoDef("camo.zombierool.rain_storm", 12500, false, 0, true, "legendary"));
        CAMOS.put("camo_lightning", new CamoDef("camo.zombierool.lightning", 12500, false, 0, true, "legendary"));
        CAMOS.put("camo_red_soda", new CamoDef("camo.zombierool.red_soda", 15000, false, 0, true, "legendary"));
        CAMOS.put("camo_lava", new CamoDef("camo.zombierool.lava", 15000, false, 0, true, "legendary"));
        CAMOS.put("camo_ocean", new CamoDef("camo.zombierool.ocean", 12500, false, 0, true, "legendary"));
        CAMOS.put("camo_water", new CamoDef("camo.zombierool.water", 10000, false, 0, true, "legendary"));
        CAMOS.put("camo_bubbles", new CamoDef("camo.zombierool.bubbles", 12500, false, 0, true, "legendary"));
        CAMOS.put("camo_galaxy", new CamoDef("camo.zombierool.galaxy", 15000, false, 0, true, "legendary"));
        CAMOS.put("camo_venom", new CamoDef("camo.zombierool.venom", 15000, false, 0, true, "legendary"));
        CAMOS.put("camo_blood", new CamoDef("camo.zombierool.blood", 15000, false, 0, true, "legendary"));
        CAMOS.put("camo_marmot", new CamoDef("camo.zombierool.marmot", 15000, false, 0, true, "legendary"));

        CAMOS.put("camo_solid_gold", new CamoDef("camo.zombierool.solid_gold", 0, false, 0, false, "mastery"));
        CAMOS.put("camo_black_ice", new CamoDef("camo.zombierool.black_ice", 0, false, 0, false, "mastery"));

        CAMOS.put("camo_prestige", new CamoDef("camo.zombierool.prestige", 0, true, 1, true, "prestige"));
        CAMOS.put("camo_supernova", new CamoDef("camo.zombierool.supernova", 0, true, 2, true, "prestige"));
        CAMOS.put("camo_void", new CamoDef("camo.zombierool.void", 0, true, 3, true, "prestige"));
        CAMOS.put("camo_nebula", new CamoDef("camo.zombierool.nebula", 0, true, 4, true, "prestige"));
        CAMOS.put("camo_celestial", new CamoDef("camo.zombierool.celestial", 0, true, 5, true, "prestige"));

        CAMOS.put("camo_contributor", new CamoDef("camo.zombierool.contributor", 0, false, 0, true, "exclusive"));
        CAMOS.put("camo_glitch_cartridge", new CamoDef("camo.zombierool.glitch_cartridge", 0, false, 0, true, "exclusive"));
        CAMOS.put("camo_resprune", new CamoDef("camo.zombierool.resprune", 0, false, 0, true, "exclusive"));
        CAMOS.put("camo_cryo", new CamoDef("camo.zombierool.cryo", 0, false, 0, true, "exclusive"));
        CAMOS.put("camo_mdxu", new CamoDef("camo.zombierool.mdxu", 0, false, 0, true, "exclusive"));
        CAMOS.put("camo_anniversary", new CamoDef("camo.zombierool.anniversary", 0, false, 0, true, "exclusive"));
    }

    public enum ChallengeType { 
        HEADSHOTS, KILLS, WAVES, REVIVES, GRENADE_KILLS, OBSTACLE_BOUGHT, PERK_BOUGHT, PAP_USED, MYSTERY_BOX_USED, 
        WEAPON_KILLS, WEAPON_HEADSHOTS 
    }

    public static class ChallengeDef {
        public ChallengeType type;
        public int target;
        public int reward;
        public String context;

        public ChallengeDef(ChallengeType type, int target, int reward, String context) { 
            this.type = type; this.target = target; this.reward = reward; this.context = context;
        }

        public ChallengeDef(ChallengeType type, int target, int reward) { 
            this(type, target, reward, ""); 
        }
    }

    private static final List<ChallengeDef> POSSIBLE_CHALLENGES = Arrays.asList(
        new ChallengeDef(ChallengeType.HEADSHOTS, 25, 100),
        new ChallengeDef(ChallengeType.HEADSHOTS, 50, 250),
        new ChallengeDef(ChallengeType.HEADSHOTS, 100, 500),
        new ChallengeDef(ChallengeType.KILLS, 100, 250),
        new ChallengeDef(ChallengeType.KILLS, 300, 600),
        new ChallengeDef(ChallengeType.KILLS, 500, 900),
        new ChallengeDef(ChallengeType.WAVES, 10, 300),
        new ChallengeDef(ChallengeType.WAVES, 25, 500),
        new ChallengeDef(ChallengeType.WAVES, 50, 1300),
        new ChallengeDef(ChallengeType.REVIVES, 3, 250),
        new ChallengeDef(ChallengeType.REVIVES, 8, 600),
        new ChallengeDef(ChallengeType.REVIVES, 12, 1500),
        new ChallengeDef(ChallengeType.GRENADE_KILLS, 6, 150),
        new ChallengeDef(ChallengeType.GRENADE_KILLS, 30, 400),
        new ChallengeDef(ChallengeType.OBSTACLE_BOUGHT, 10, 200),
        new ChallengeDef(ChallengeType.PERK_BOUGHT, 10, 200),
        new ChallengeDef(ChallengeType.PAP_USED, 3, 300),
        new ChallengeDef(ChallengeType.MYSTERY_BOX_USED, 15, 200)
    );

    public static class SkinDef {
        public String langKey;
        public String unlockType; 
        public int unlockReq;
        public int price;
        public List<String> exclusiveWeapons;

        public SkinDef(String langKey, String unlockType, int unlockReq, int price, String... exclusiveWeapons) {
            this.langKey = langKey;
            this.unlockType = unlockType;
            this.unlockReq = unlockReq;
            this.price = price;
            this.exclusiveWeapons = Arrays.asList(exclusiveWeapons);
        }
    }

    public static class CamoDef {
        public String langKey;
        public int price;
        public boolean isPrestige;
        public int prestigeLevelReq;
        public boolean isGlobalUnlock;
        public String rarity; 
        public List<String> exclusiveWeapons;

        public CamoDef(String langKey, int price, boolean isPrestige, int prestigeLevelReq, boolean isGlobalUnlock, String rarity, String... exclusiveWeapons) { 
            this.langKey = langKey; 
            this.price = price; 
            this.isPrestige = isPrestige;
            this.prestigeLevelReq = prestigeLevelReq;
            this.isGlobalUnlock = isGlobalUnlock;
            this.rarity = rarity;
            this.exclusiveWeapons = Arrays.asList(exclusiveWeapons);
        }
    }

    public static class CareerData {
        public int zrfBalance = 0;
        public int currentLevel = 1;
        public int currentXp = 0;
        public int prestigeLevel = 0;
        public int lifetimeKills = 0;
        public int lifetimeHeadshots = 0;
        public int lifetimeWaves = 0;
        public int lifetimeRevives = 0;

        public Map<String, Integer> weaponKills = new HashMap<>();
        public Map<String, Integer> weaponHeadshots = new HashMap<>();
        public Map<String, Integer> weaponPaps = new HashMap<>(); 
        
        public List<String> unlockedCamos = new ArrayList<>(); 
        public List<String> globalUnlockedCamos = new ArrayList<>();
        public List<String> unlockedSkins = new ArrayList<>();
        public Map<String, List<String>> weaponUnlockedCamos = new HashMap<>(); 
        
        public Map<String, String> equippedCamos = new HashMap<>();
        public Map<String, String> equippedSkins = new HashMap<>(); 
        
        public Map<String, Integer> challengeProgress = new HashMap<>();
        public Map<String, Boolean> challengeCompleted = new HashMap<>();
        public Map<String, ChallengeDef> activeChallenges = new HashMap<>();
        public Map<String, Float> dailyDiscounts = new HashMap<>(); 
        
        public long lastChallengeResetTime = 0;
        public long lastDailyRewardTime = 0;
        public List<String> redeemedCodes = new ArrayList<>();

        public int lastAnniversaryYear = 0;
    }

    private static byte[] getMachineKey() throws Exception {
        String p1 = "Z0mB";
        String p2 = "13r0";
        String p3 = "0L_s3C";
        String base = p1 + p2 + p3;

        String machineInfo = System.getProperty("user.name") 
                           + System.getProperty("os.name") 
                           + System.getProperty("user.home")
                           + Runtime.getRuntime().availableProcessors();
        
        String envComputerName = System.getenv("COMPUTERNAME");
        if (envComputerName != null) machineInfo += envComputerName;
        String envHostName = System.getenv("HOSTNAME");
        if (envHostName != null) machineInfo += envHostName;

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest((base + machineInfo).getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] encrypt(byte[] data) throws Exception {
        byte[] keyBytes = getMachineKey();
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(data);
        byte[] result = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
        return result;
    }

    private static byte[] decrypt(byte[] encryptedAndIv) throws Exception {
        byte[] keyBytes = getMachineKey();
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        byte[] iv = new byte[16];
        System.arraycopy(encryptedAndIv, 0, iv, 0, iv.length);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = new byte[encryptedAndIv.length - 16];
        System.arraycopy(encryptedAndIv, 16, encrypted, 0, encrypted.length);
        return cipher.doFinal(encrypted);
    }

    private static String calculateSHA256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static boolean performSanityChecks(CareerData data) {
        if (data.zrfBalance > 9999999 || data.zrfBalance < 0) return false;
        if (data.currentLevel > 50 || data.currentLevel < 1) return false;
        if (data.prestigeLevel > MAX_PRESTIGE || data.prestigeLevel < 0) return false;
        if (data.prestigeLevel > 0 && data.lifetimeKills < (data.prestigeLevel * 2500)) return false;
        if (data.lifetimeHeadshots > data.lifetimeKills) return false;
        return true;
    }

    public static float getBoostMultiplier() {
        float mult = 1.0f;
        LocalDate now = LocalDate.now();
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            mult += 0.20f;
        }
        if (now.getMonth() == Month.OCTOBER && now.getDayOfMonth() == 1) {
            mult += 0.30f;
        }
        return mult;
    }

    public static float getEventDiscountMultiplier() {
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int day = now.getDayOfMonth();

        if ((month == 10 && day >= 20) || (month == 11 && day <= 5)) return 0.75f;
        if (month == 12 && day >= 20) return 0.75f;
        if (month == 11 && day >= 23 && day <= 30) return 0.60f;

        LocalDate easter = getEasterDate(now.getYear());
        if (!now.isBefore(easter.minusDays(7)) && !now.isAfter(easter.plusDays(7))) return 0.75f;

        return 1.0f;
    }

    private static LocalDate getEasterDate(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    public static String getActiveEventText() {
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int day = now.getDayOfMonth();

        if ((month == 10 && day >= 20) || (month == 11 && day <= 5)) return Component.translatable("gui.zombierool.career.event.halloween").getString();
        if (month == 12 && day >= 20) return Component.translatable("gui.zombierool.career.event.christmas").getString();
        if (month == 11 && day >= 23 && day <= 30) return Component.translatable("gui.zombierool.career.event.black_friday").getString();

        LocalDate easter = getEasterDate(now.getYear());
        if (!now.isBefore(easter.minusDays(7)) && !now.isAfter(easter.plusDays(7))) return Component.translatable("gui.zombierool.career.event.easter").getString();

        return "";
    }

    public static String getActiveBoostText() {
        LocalDate now = LocalDate.now();
        List<String> boosts = new ArrayList<>();
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            boosts.add(Component.translatable("gui.zombierool.career.boost.weekend").getString());
        }
        if (now.getMonth() == Month.OCTOBER && now.getDayOfMonth() == 1) {
            boosts.add(Component.translatable("gui.zombierool.career.boost.anniversary").getString());
        }
        if (boosts.isEmpty()) return "";
        int percent = Math.round((getBoostMultiplier() - 1.0f) * 100.0f);
        return String.join(" & ", boosts) + " (+" + percent + "%)";
    }

    private static void ensureCollectionsNotNull(CareerData data) {
        if (data.unlockedCamos == null) data.unlockedCamos = new ArrayList<>();
        if (data.globalUnlockedCamos == null) data.globalUnlockedCamos = new ArrayList<>();
        if (data.unlockedSkins == null) data.unlockedSkins = new ArrayList<>();
        if (data.weaponUnlockedCamos == null) data.weaponUnlockedCamos = new HashMap<>();
        if (data.equippedCamos == null) data.equippedCamos = new HashMap<>();
        if (data.equippedSkins == null) data.equippedSkins = new HashMap<>();
        if (data.challengeProgress == null) data.challengeProgress = new HashMap<>();
        if (data.challengeCompleted == null) data.challengeCompleted = new HashMap<>();
        if (data.activeChallenges == null) data.activeChallenges = new HashMap<>();
        if (data.dailyDiscounts == null) data.dailyDiscounts = new HashMap<>();
        if (data.weaponKills == null) data.weaponKills = new HashMap<>();
        if (data.weaponHeadshots == null) data.weaponHeadshots = new HashMap<>();
        if (data.weaponPaps == null) data.weaponPaps = new HashMap<>();
        if (data.redeemedCodes == null) data.redeemedCodes = new ArrayList<>();
    }

    public static void load() {
        if (isLoaded) return; 

        synchronized (FILE_LOCK) {
            if (!CAREER_FILE.exists() && LEGACY_CAREER_FILE.exists()) {
                try {
                    GLOBAL_DIR.mkdirs();
                    Files.copy(LEGACY_CAREER_FILE.toPath(), CAREER_FILE.toPath());
                    ZombieroolMod.LOGGER.info("[ZombieRool] Migrated legacy career data to global directory.");
                } catch (Exception e) {
                    ZombieroolMod.LOGGER.error("[ZombieRool] Failed to migrate career data", e);
                }
            }

            if (CAREER_FILE.exists()) {
                try {
                    byte[] encryptedAndIv = Files.readAllBytes(CAREER_FILE.toPath());
                    byte[] decrypted = decrypt(encryptedAndIv);
                    String payload = new String(decrypted, StandardCharsets.UTF_8);
                    String[] parts = payload.split("::", 2);

                    if (parts.length == 2) {
                        String savedHash = parts[0];
                        String json = parts[1];

                        if (calculateSHA256(json).equals(savedHash)) {
                            CareerData loaded = GSON.fromJson(json, CareerData.class);
                            if (loaded != null) {
                                ensureCollectionsNotNull(loaded);
                                if (performSanityChecks(loaded)) {
                                    currentData = loaded;
                                    if (currentData.currentLevel < 1) currentData.currentLevel = 1;
                                    if (!currentData.unlockedCamos.isEmpty()) {
                                        for (String c : currentData.unlockedCamos) {
                                            if (!currentData.globalUnlockedCamos.contains(c)) {
                                                currentData.globalUnlockedCamos.add(c);
                                            }
                                        }
                                        currentData.unlockedCamos.clear();
                                    }

                                    memKey = new Random().nextInt();
                                    setZrf(currentData.zrfBalance);
                                    setLvl(currentData.currentLevel);
                                    setXp(currentData.currentXp);
                                    setPrest(currentData.prestigeLevel);

                                    for (Map.Entry<String, CamoDef> entry : CAMOS.entrySet()) {
                                        if (entry.getValue().isPrestige && getPrest() >= entry.getValue().prestigeLevelReq) {
                                            if (!currentData.globalUnlockedCamos.contains(entry.getKey())) {
                                                currentData.globalUnlockedCamos.add(entry.getKey());
                                            }
                                        }
                                    }

                                    isLoaded = true;
                                    checkAndResetDailies();
                                    return;
                                } else {
                                    ZombieroolMod.LOGGER.warn("Career Data failed sanity checks! Data has been wiped to prevent cheating.");
                                    throw new IllegalStateException("Sanity check failed");
                                }
                            }
                        } else {
                            ZombieroolMod.LOGGER.warn("Career Data integrity check failed! File may be corrupted or manually altered.");
                        }
                    }
                } catch (Exception e) {
                    ZombieroolMod.LOGGER.error("Failed to load Career Data. Resetting profile...", e);
                }
            }
            
            memKey = new Random().nextInt();
            setZrf(0);
            setLvl(1);
            setXp(0);
            setPrest(0);
            isLoaded = true;
            checkAndResetDailies(); 
        }
    }

    public static void save() {
        currentData.zrfBalance = getZrf();
        currentData.currentLevel = getLvl();
        currentData.currentXp = getXp();
        currentData.prestigeLevel = getPrest();

        String json;
        synchronized(currentData) {
            json = GSON.toJson(currentData);
        }

        CompletableFuture.runAsync(() -> {
            synchronized(FILE_LOCK) {
                try {
                    String hash = calculateSHA256(json);
                    String payload = hash + "::" + json;
                    byte[] encrypted = encrypt(payload.getBytes(StandardCharsets.UTF_8));
                    
                    if (!GLOBAL_DIR.exists()) {
                        GLOBAL_DIR.mkdirs();
                    }
                    File tempFile = new File(GLOBAL_DIR, "zombierool_career.tmp");
                    Files.write(tempFile.toPath(), encrypted);

                    File backupFile = new File(GLOBAL_DIR, "zombierool_career.bak");
                    if (CAREER_FILE.exists()) {
                        Files.copy(CAREER_FILE.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }

                    Files.move(tempFile.toPath(), CAREER_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    ZombieroolMod.LOGGER.error("Failed to save Career Data", e);
                }
            }
        });
    }

    public static void forceSave() {
        currentData.zrfBalance = getZrf();
        currentData.currentLevel = getLvl();
        currentData.currentXp = getXp();
        currentData.prestigeLevel = getPrest();

        try {
            String json;
            synchronized(currentData) {
                json = GSON.toJson(currentData);
            }
            String hash = calculateSHA256(json);
            String payload = hash + "::" + json;
            byte[] encrypted = encrypt(payload.getBytes(StandardCharsets.UTF_8));
            
            synchronized(FILE_LOCK) {
                if (!GLOBAL_DIR.exists()) GLOBAL_DIR.mkdirs();
                File tempFile = new File(GLOBAL_DIR, "zombierool_career.tmp");
                Files.write(tempFile.toPath(), encrypted);

                File backupFile = new File(GLOBAL_DIR, "zombierool_career.bak");
                if (CAREER_FILE.exists()) {
                    Files.copy(CAREER_FILE.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                Files.move(tempFile.toPath(), CAREER_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            ZombieroolMod.LOGGER.error("Failed to synchronously save Career Data", e);
        }
    }

    public static void resetAllData() {
        synchronized(FILE_LOCK) {
            currentData = new CareerData();
            ensureCollectionsNotNull(currentData);
            memKey = new Random().nextInt();
            setZrf(0);
            setLvl(1);
            setXp(0);
            setPrest(0);
            checkAndResetDailies();
            forceSave(); 
        }

        if (Minecraft.getInstance().getConnection() != null) {
            NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedCamosPacket(new HashMap<>()));
            NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedSkinsPacket(new HashMap<>()));
        }
    }

    public static void syncWeaponStats(Map<String, Integer> kills, Map<String, Integer> headshots) {
        currentData.weaponKills.putAll(kills);
        currentData.weaponHeadshots.putAll(headshots);
        save();
    }

    public static int getWeaponLevel(String weaponId) {
        int kills = currentData.weaponKills.getOrDefault(weaponId, 0);
        int headshots = currentData.weaponHeadshots.getOrDefault(weaponId, 0);
        int weaponXp = kills + (headshots * 2);
        int level = 1;
        int xpForNext = 2;

        while (weaponXp >= xpForNext && level < 100) {
            weaponXp -= xpForNext;
            level++;
            xpForNext = level * 2;
        }
        return level;
    }

    public static int getWeaponXpInCurrentLevel(String weaponId) {
        int kills = currentData.weaponKills.getOrDefault(weaponId, 0);
        int headshots = currentData.weaponHeadshots.getOrDefault(weaponId, 0);
        int weaponXp = kills + (headshots * 2);
        int level = 1;
        int xpForNext = 2;

        while (weaponXp >= xpForNext && level < 100) {
            weaponXp -= xpForNext;
            level++;
            xpForNext = level * 2;
        }
        return weaponXp; 
    }

    public static int getWeaponXpForNextLevel(String weaponId) {
        int level = getWeaponLevel(weaponId);
        if (level >= 100) return 1; 
        return level * 2;
    }

    public static void addWeaponStat(String weaponId, int kills, int headshots, int papCount) {
        int oldLevel = getWeaponLevel(weaponId);
        currentData.weaponKills.put(weaponId, currentData.weaponKills.getOrDefault(weaponId, 0) + kills);
        currentData.weaponHeadshots.put(weaponId, currentData.weaponHeadshots.getOrDefault(weaponId, 0) + headshots);
        currentData.weaponPaps.put(weaponId, currentData.weaponPaps.getOrDefault(weaponId, 0) + papCount);

        int newLevel = getWeaponLevel(weaponId);
        if (newLevel > oldLevel) {
            checkMasteryUnlocks(weaponId, newLevel);
        }
        checkSkinUnlocks(weaponId);
        save();
    }

    private static void checkMasteryUnlocks(String weaponId, int level) {
        boolean unlockedGold = false;
        boolean unlockedBlackIce = false;

        if (level >= GOLD_LEVEL_REQ) {
            List<String> list = currentData.weaponUnlockedCamos.computeIfAbsent(weaponId, k -> new ArrayList<>());
            if (!list.contains("camo_solid_gold")) {
                list.add("camo_solid_gold");
                unlockedGold = true;
            }
        }
        if (level >= BLACK_ICE_LEVEL_REQ) {
            List<String> list = currentData.weaponUnlockedCamos.computeIfAbsent(weaponId, k -> new ArrayList<>());
            if (!list.contains("camo_black_ice")) {
                list.add("camo_black_ice");
                unlockedBlackIce = true;
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (unlockedBlackIce) {
                mc.player.displayClientMessage(Component.literal("§b⭐ MASTERY UNLOCKED: BLACK ICE FOR " + weaponId.toUpperCase() + " ⭐"), false);
                mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            } else if (unlockedGold) {
                mc.player.displayClientMessage(Component.literal("§e⭐ MASTERY UNLOCKED: SOLID GOLD FOR " + weaponId.toUpperCase() + " ⭐"), false);
                mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }
    }

    private static void checkSkinUnlocks(String weaponId) {
        for (Map.Entry<String, SkinDef> entry : SKINS.entrySet()) {
            String skinId = entry.getKey();
            SkinDef def = entry.getValue();

            if (def.exclusiveWeapons.isEmpty() || def.exclusiveWeapons.contains(weaponId)) {
                if (!isSkinUnlocked(weaponId, skinId)) {
                    boolean unlocked = false;
                    if ("HEADSHOTS".equals(def.unlockType)) {
                        if ("golden_deagle".equals(skinId) && "deagle".equals(weaponId)) {
                            unlocked = currentData.weaponHeadshots.getOrDefault("deagle", 0) >= def.unlockReq &&
                                       isUnlocked("deagle", "camo_gold") &&
                                       isUnlocked("deagle", "camo_solid_gold");
                        } else {
                            unlocked = currentData.weaponHeadshots.getOrDefault(weaponId, 0) >= def.unlockReq;
                        }
                    } else if ("PAP".equals(def.unlockType)) {
                        unlocked = currentData.weaponPaps.getOrDefault(weaponId, 0) >= def.unlockReq;
                    }

                    if (unlocked) {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            String skinName = Component.translatable(def.langKey).getString();
                            mc.player.displayClientMessage(Component.literal("§a⭐ NEW SKIN UNLOCKED: " + skinName + " FOR " + weaponId.toUpperCase() + " ⭐"), false);
                            mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
                        }
                    }
                }
            }
        }
    }

    public static boolean isSkinUnlocked(String weaponId, String skinId) {
        if (skinId == null || skinId.isEmpty()) return true;
        if ("golden_deagle".equals(skinId)) {
            return currentData.weaponHeadshots.getOrDefault("deagle", 0) >= 100 &&
                   isUnlocked("deagle", "camo_gold") &&
                   isUnlocked("deagle", "camo_solid_gold");
        }
        if ("silver_m1911".equals(skinId)) {
            return currentData.unlockedSkins.contains("silver_m1911");
        }

        SkinDef def = SKINS.get(skinId);
        if (def == null) return false;

        if ("HEADSHOTS".equals(def.unlockType)) {
            return currentData.weaponHeadshots.getOrDefault(weaponId, 0) >= def.unlockReq;
        } else if ("PAP".equals(def.unlockType)) {
            return currentData.weaponPaps.getOrDefault(weaponId, 0) >= def.unlockReq;
        } else if ("BUY".equals(def.unlockType)) {
            return currentData.unlockedSkins.contains(skinId);
        }

        return false;
    }

    public static boolean buySkin(String weaponId, String skinId) {
        SkinDef def = SKINS.get(skinId);
        if (def == null || def.price <= 0 || isSkinUnlocked(weaponId, skinId)) return false;

        if (getZrf() >= def.price) {
            setZrf(getZrf() - def.price);
            if (!currentData.unlockedSkins.contains(skinId)) {
                currentData.unlockedSkins.add(skinId);
            }
            forceSave();
            return true;
        }
        return false;
    }

    public static void equipSkin(String weaponId, String skinId) {
        if (skinId == null || skinId.isEmpty()) {
            currentData.equippedSkins.remove(weaponId);
        } else if (isSkinUnlocked(weaponId, skinId)) {
            currentData.equippedCamos.remove(weaponId); 
            currentData.equippedSkins.put(weaponId, skinId);
        }
        forceSave();
        if (Minecraft.getInstance().getConnection() != null) {
            NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedSkinsPacket(currentData.equippedSkins));
            NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedCamosPacket(currentData.equippedCamos));
        }
    }

    public static void addZRF(int amount, String reasonKey) {
        addZRF(amount, reasonKey, true);
    }

    public static void addZRF(int amount, String reasonKey, boolean applyBoosts) {
        if (applyBoosts && amount > 0) {
            amount = (int) (amount * getBoostMultiplier());
        }
        if (amount > 0) {
            amount = (int) (Math.round(amount / 5.0) * 5);
        }

        setZrf(getZrf() + amount);
        save();
        if (amount > 0) {
            pushNotification("+" + amount + " ZRF", 0xFFD700);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !reasonKey.isEmpty()) {
            mc.player.displayClientMessage(Component.translatable(reasonKey, amount).withStyle(net.minecraft.ChatFormatting.GOLD), true);
            mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.5f, 2.0f);
        }
        if (mc.screen instanceof CareerScreen careerScreen) {
            careerScreen.refreshData();
        }
    }

    public static int getXpRequiredForLevel(int level) {
        return 1000 + (level * 500);
    }

    public static void addXp(int amount) {
        addXp(amount, true);
    }

    public static void addXp(int amount, boolean applyBoosts) {
        if (getLvl() >= 50) return;

        if (applyBoosts && amount > 0) {
            amount = (int) (amount * getBoostMultiplier());
        }

        if (amount > 0 && !me.cryo.zombierool.configuration.ZRClientConfig.hideXpNotifications()) {
            pushNotification("+" + amount + " XP", 0x00FFFF);
        }

        int currentXpVal = getXp() + amount;
        int currentLvlVal = getLvl();
        boolean leveledUp = false;

        while (currentLvlVal < 50 && currentXpVal >= getXpRequiredForLevel(currentLvlVal)) {
            currentXpVal -= getXpRequiredForLevel(currentLvlVal);
            currentLvlVal++;
            int zrfReward = 100 + (currentLvlVal * 10);
            addZRF(zrfReward, "", false); 
            leveledUp = true;
        }

        if (currentLvlVal >= 50) {
            currentXpVal = 0;
        }

        setXp(currentXpVal);
        setLvl(currentLvlVal);
        save();

        Minecraft mc = Minecraft.getInstance();
        if (leveledUp && mc.player != null) {
            pushNotification("LEVEL UP! -> " + getLvl(), 0x55FF55);
            mc.player.displayClientMessage(Component.translatable("message.zombierool.career.levelup", getLvl()).withStyle(net.minecraft.ChatFormatting.AQUA), true);
            mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        if (mc.screen instanceof CareerScreen careerScreen) {
            careerScreen.refreshData();
        }
    }

    public static boolean isUnlocked(String weaponId, String camoId) {
        if (camoId == null || camoId.isEmpty()) return true; 
        if (currentData.globalUnlockedCamos.contains(camoId)) return true;
        return currentData.weaponUnlockedCamos.getOrDefault(weaponId, new ArrayList<>()).contains(camoId);
    }

    public static boolean isCamoUnlocked(String camoId) {
        if (camoId == null || camoId.isEmpty()) return true;
        if (currentData.globalUnlockedCamos.contains(camoId)) return true;

        CamoDef def = CAMOS.get(camoId);
        if (def != null && !def.exclusiveWeapons.isEmpty()) {
            for (String wpn : def.exclusiveWeapons) {
                if (currentData.weaponUnlockedCamos.getOrDefault(wpn, new ArrayList<>()).contains(camoId)) return true;
            }
        }

        return false;
    }

    public static void forceUnlockCamoGlobal(String camoId) {
        if (camoId != null && !camoId.isEmpty() && !currentData.globalUnlockedCamos.contains(camoId)) {
            currentData.globalUnlockedCamos.add(camoId);
            forceSave(); 
        }
    }

    public static int getDiscountedPrice(String camoId) {
        CamoDef def = CAMOS.get(camoId);
        if (def == null) return 0;
        float dailyMult = currentData.dailyDiscounts.getOrDefault(camoId, 1.0f);
        float eventMult = getEventDiscountMultiplier();
        int rawPrice = (int) (def.price * dailyMult * eventMult);
        return (int) (Math.round(rawPrice / 5.0) * 5);
    }

    public static boolean buyCamo(String weaponId, String camoId) {
        CamoDef def = CAMOS.get(camoId);
        if (def == null || def.isPrestige || "exclusive".equals(def.rarity) || def.price <= 0 || isUnlocked(weaponId, camoId)) return false;

        int finalPrice = getDiscountedPrice(camoId);

        if (getZrf() >= finalPrice) {
            setZrf(getZrf() - finalPrice);

            if (def.isGlobalUnlock) {
                currentData.globalUnlockedCamos.add(camoId);
            } else {
                if (!def.exclusiveWeapons.isEmpty()) {
                    for (String wpn : def.exclusiveWeapons) {
                        currentData.weaponUnlockedCamos.computeIfAbsent(wpn, k -> new ArrayList<>()).add(camoId);
                    }
                } else {
                    currentData.weaponUnlockedCamos.computeIfAbsent(weaponId, k -> new ArrayList<>()).add(camoId);
                }
            }

            forceSave(); 
            return true;
        }
        return false;
    }

    public static void equipCamo(String weaponId, String camoId) {
        if (camoId == null || camoId.isEmpty()) {
            currentData.equippedCamos.remove(weaponId);
        } else if (isUnlocked(weaponId, camoId)) {
            currentData.equippedSkins.remove(weaponId); 
            currentData.equippedCamos.put(weaponId, camoId);
        }
        forceSave(); 

        if (Minecraft.getInstance().getConnection() != null) {
            NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedCamosPacket(currentData.equippedCamos));
            NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedSkinsPacket(currentData.equippedSkins));
        }
    }

    public static void equipCamoOnAll(String camoId) {
        CamoDef def = CAMOS.get(camoId);
        if (def == null) return;
        for (String wpn : CareerScreen.getSupportedWeaponsCache()) {
            if (def.exclusiveWeapons.isEmpty() || def.exclusiveWeapons.contains(wpn)) {
                currentData.equippedSkins.remove(wpn);
                currentData.equippedCamos.put(wpn, camoId);
            }
        }
        forceSave(); 

        if (Minecraft.getInstance().getConnection() != null) {
            NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedCamosPacket(currentData.equippedCamos));
            NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedSkinsPacket(currentData.equippedSkins));
        }
    }

    public static void progressChallenge(ChallengeType type, int amount, String context) {
        boolean updated = false;

        switch (type) {
            case KILLS -> { currentData.lifetimeKills += amount; addXp(amount * 10); updated = true; }
            case HEADSHOTS -> { currentData.lifetimeHeadshots += amount; addXp(amount * 5); updated = true; } 
            case WAVES -> { currentData.lifetimeWaves += amount; addXp(amount * 100); updated = true; }
            case REVIVES -> { currentData.lifetimeRevives += amount; addXp(amount * 50); updated = true; }
            case GRENADE_KILLS -> { addXp(amount * 15); updated = true; }
            case OBSTACLE_BOUGHT -> { addXp(amount * 20); updated = true; }
            case PERK_BOUGHT -> { addXp(amount * 30); updated = true; }
            case PAP_USED -> { addXp(amount * 50); updated = true; }
            case MYSTERY_BOX_USED -> { addXp(amount * 10); updated = true; }
            case WEAPON_KILLS -> { updated = true; }
            case WEAPON_HEADSHOTS -> { updated = true; }
        }

        for (Map.Entry<String, ChallengeDef> entry : currentData.activeChallenges.entrySet()) {
            String challengeId = entry.getKey();
            ChallengeDef def = entry.getValue();

            if (def.type == type && !currentData.challengeCompleted.getOrDefault(challengeId, false)) {
                if (def.context != null && !def.context.isEmpty() && !def.context.equalsIgnoreCase(context)) {
                    continue; 
                }

                int current = currentData.challengeProgress.getOrDefault(challengeId, 0) + amount;
                currentData.challengeProgress.put(challengeId, current);
                updated = true;

                if (!me.cryo.zombierool.configuration.ZRClientConfig.hideXpNotifications()) {
                    pushNotification("Challenge: " + current + "/" + def.target, 0xFFAA00);
                }

                if (current >= def.target) {
                    currentData.challengeCompleted.put(challengeId, true);
                    pushNotification("Challenge Completed!", 0x55FF55);
                    addZRF(def.reward, "message.zombierool.career.challenge_done");

                    currentData.activeChallenges.remove(challengeId);
                    currentData.challengeProgress.remove(challengeId);
                    currentData.challengeCompleted.remove(challengeId);

                    List<ChallengeDef> shuffled = new ArrayList<>(POSSIBLE_CHALLENGES);
                    List<String> weapons = new ArrayList<>(CareerScreen.getSupportedWeaponsCache());
                    if (!weapons.isEmpty()) {
                        String w1 = weapons.get(new Random().nextInt(weapons.size()));
                        shuffled.add(new ChallengeDef(ChallengeType.WEAPON_KILLS, 100, 300, w1));
                        String w2 = weapons.get(new Random().nextInt(weapons.size()));
                        shuffled.add(new ChallengeDef(ChallengeType.WEAPON_HEADSHOTS, 50, 400, w2));
                    }
                    Collections.shuffle(shuffled);

                    String newId = "daily_" + System.currentTimeMillis() + "_" + new Random().nextInt(1000);
                    currentData.activeChallenges.put(newId, shuffled.get(0));
                    currentData.challengeProgress.put(newId, 0);
                    currentData.challengeCompleted.put(newId, false);
                }
            }
        }

        if (updated) save();
    }

    public static void progressChallenge(ChallengeType type, int amount) {
        progressChallenge(type, amount, "");
    }

    public static boolean claimDailyReward() {
        long currentDay = LocalDate.now().toEpochDay();
        if (currentData.lastDailyRewardTime > 1000000) currentData.lastDailyRewardTime = 0; 
        
        if (currentData.lastDailyRewardTime < currentDay) {
            currentData.lastDailyRewardTime = currentDay; 
            addZRF(200, "message.zombierool.career.zrf_earned", false); 
            forceSave(); 
            return true;
        }
        return false;
    }

    public static boolean canClaimDaily() {
        long currentDay = LocalDate.now().toEpochDay();
        if (currentData.lastDailyRewardTime > 1000000) currentData.lastDailyRewardTime = 0;
        return currentData.lastDailyRewardTime < currentDay;
    }

    public static boolean canPrestige() {
        return getLvl() >= 50 && getPrest() < MAX_PRESTIGE;
    }

    public static void doPrestige() {
        if (canPrestige()) {
            setPrest(getPrest() + 1);
            setLvl(1);
            setXp(0);
            setZrf(0);

            currentData.globalUnlockedCamos.removeIf(id -> CAMOS.containsKey(id) && !CAMOS.get(id).isPrestige);
            currentData.weaponUnlockedCamos.clear();
            currentData.equippedCamos.clear();
            currentData.equippedSkins.clear();

            for (Map.Entry<String, CamoDef> entry : CAMOS.entrySet()) {
                if (entry.getValue().isPrestige && getPrest() >= entry.getValue().prestigeLevelReq) {
                    if (!currentData.globalUnlockedCamos.contains(entry.getKey())) {
                        currentData.globalUnlockedCamos.add(entry.getKey());
                    }
                }
            }

            forceSave(); 

            if (Minecraft.getInstance().getConnection() != null) {
                NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedCamosPacket(currentData.equippedCamos));
                NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedSkinsPacket(currentData.equippedSkins));
            }
        }
    }

    private static void checkAndResetDailies() {
        long currentDay = LocalDate.now().toEpochDay();
        if (currentData.lastChallengeResetTime > 1000000) currentData.lastChallengeResetTime = 0; 

        long currentYear = LocalDate.now().getYear();
        if (LocalDate.now().getMonth() == Month.OCTOBER && LocalDate.now().getDayOfMonth() == 1) {
            if (currentData.lastAnniversaryYear < currentYear) {
                currentData.lastAnniversaryYear = (int)currentYear;
                forceUnlockCamoGlobal("camo_anniversary"); 
            }
        }

        if (currentData.lastChallengeResetTime < currentDay) {
            currentData.activeChallenges.clear();
            currentData.challengeProgress.clear();
            currentData.challengeCompleted.clear();
            currentData.dailyDiscounts.clear();

            List<String> weapons = new ArrayList<>();
            for (me.cryo.zombierool.core.system.WeaponSystem.Definition def : me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.values()) {
                if (!"MELEE".equalsIgnoreCase(def.type) && !"GRENADE".equalsIgnoreCase(def.type)) {
                    weapons.add(def.id.replace("zombierool:", ""));
                }
            }

            List<ChallengeDef> shuffled = new ArrayList<>(POSSIBLE_CHALLENGES);
            if (!weapons.isEmpty()) {
                String w1 = weapons.get(new Random().nextInt(weapons.size()));
                shuffled.add(new ChallengeDef(ChallengeType.WEAPON_KILLS, 100, 300, w1));
                String w2 = weapons.get(new Random().nextInt(weapons.size()));
                shuffled.add(new ChallengeDef(ChallengeType.WEAPON_HEADSHOTS, 50, 400, w2));
            }
            Collections.shuffle(shuffled);

            for (int i = 0; i < Math.min(3, shuffled.size()); i++) {
                String id = "daily_" + i;
                currentData.activeChallenges.put(id, shuffled.get(i));
                currentData.challengeProgress.put(id, 0);
                currentData.challengeCompleted.put(id, false);
            }

            List<String> camoIds = new ArrayList<>(CAMOS.keySet());
            camoIds.removeIf(id -> {
                CamoDef def = CAMOS.get(id);
                return def.isPrestige || "exclusive".equals(def.rarity) || "mastery".equals(def.rarity);
            });
            Collections.shuffle(camoIds);

            for (int i = 0; i < Math.min(2, camoIds.size()); i++) {
                float[] discounts = {0.75f, 0.5f}; 
                currentData.dailyDiscounts.put(camoIds.get(i), discounts[new Random().nextInt(discounts.length)]);
            }

            currentData.lastChallengeResetTime = currentDay;
            forceSave(); 
        }
    }

    public static CareerData getData() { 
        currentData.zrfBalance = getZrf();
        currentData.currentLevel = getLvl();
        currentData.currentXp = getXp();
        currentData.prestigeLevel = getPrest();
        return currentData; 
    }
}