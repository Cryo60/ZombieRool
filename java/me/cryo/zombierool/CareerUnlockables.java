package me.cryo.zombierool.client.career;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CareerUnlockables {

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

    public static final Map<String, SkinDef> SKINS = new LinkedHashMap<>();
    public static final Map<String, CamoDef> CAMOS = new LinkedHashMap<>();

    static {
        // --- SKINS ---
        SKINS.put("golden_deagle", new SkinDef("skin.zombierool.golden_deagle", "HEADSHOTS", 100, 0, "deagle"));
        SKINS.put("silver_m1911", new SkinDef("skin.zombierool.silver_m1911", "BUY", 0, 200, "m1911"));
        SKINS.put("vandal_dragon", new SkinDef("skin.zombierool.vandal_dragon", "PAP", 100, 0, "vandal"));
        SKINS.put("royal_guard_p90", new SkinDef("skin.zombierool.royal_guard_p90", "BUY", 0, 20000, "p90"));
        SKINS.put("royal_guard_famas", new SkinDef("skin.zombierool.royal_guard_famas", "BUY", 0, 20000, "famas"));

        // --- CAMOS ---
        CAMOS.put("camo_woodland", new CamoDef("camo.zombierool.woodland", 500, false, 0, false, "common"));
        CAMOS.put("camo_desert", new CamoDef("camo.zombierool.desert", 500, false, 0, false, "common"));
        CAMOS.put("camo_arctic", new CamoDef("camo.zombierool.arctic", 500, false, 0, false, "common"));
        CAMOS.put("camo_jungle", new CamoDef("camo.zombierool.jungle", 600, false, 0, false, "common"));
        CAMOS.put("camo_urban", new CamoDef("camo.zombierool.urban", 600, false, 0, false, "common"));
        CAMOS.put("camo_sandstorm", new CamoDef("camo.zombierool.sandstorm", 500, false, 0, false, "common"));
        CAMOS.put("camo_olive", new CamoDef("camo.zombierool.olive", 500, false, 0, false, "common"));
        CAMOS.put("camo_multicam", new CamoDef("camo.zombierool.multicam", 600, false, 0, false, "common"));
        CAMOS.put("camo_tigerstripe", new CamoDef("camo.zombierool.tigerstripe", 500, false, 0, false, "common"));
		CAMOS.put("camo_brushstroke", new CamoDef("camo.zombierool.brushstroke", 500, false, 0, false, "common"));
		CAMOS.put("camo_chocolate_chip", new CamoDef("camo.zombierool.chocolate_chip", 600, false, 0, false, "common"));
		CAMOS.put("camo_erdl", new CamoDef("camo.zombierool.erdl", 600, false, 0, false, "common"));
		CAMOS.put("camo_flecktarn", new CamoDef("camo.zombierool.flecktarn", 600, false, 0, false, "common"));
		CAMOS.put("camo_strichtarn", new CamoDef("camo.zombierool.strichtarn", 500, false, 0, false, "common"));


        CAMOS.put("camo_ucp", new CamoDef("camo.zombierool.ucp", 1000, false, 0, false, "rare"));
		CAMOS.put("camo_cadpat", new CamoDef("camo.zombierool.cadpat", 1200, false, 0, false, "rare"));
		CAMOS.put("camo_marpat", new CamoDef("camo.zombierool.marpat", 1200, false, 0, false, "rare"));
		CAMOS.put("camo_ocp", new CamoDef("camo.zombierool.ocp", 1500, false, 0, false, "rare"));
		CAMOS.put("camo_surpat", new CamoDef("camo.zombierool.surpat", 1500, false, 0, false, "rare"));
		CAMOS.put("camo_auscam", new CamoDef("camo.zombierool.auscam", 1000, false, 0, false, "rare"));
		CAMOS.put("camo_disruptive_patt", new CamoDef("camo.zombierool.disruptive_patt", 1200, false, 0, false, "rare"));
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
}