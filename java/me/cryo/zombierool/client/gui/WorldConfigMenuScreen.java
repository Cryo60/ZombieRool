package me.cryo.zombierool.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.gameplay.PerksManager;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2COpenConfigMenuPacket;
import me.cryo.zombierool.network.packet.C2SUpdateConfigPacket;
import me.cryo.zombierool.network.packet.C2SGenerateWeaponMappingPacket;
import me.cryo.zombierool.client.ClientEnvironmentEffects;
import me.cryo.zombierool.configuration.CustomFogManager;

import net.minecraft.ChatFormatting;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

public class WorldConfigMenuScreen extends Screen {
    private final S2COpenConfigMenuPacket initData;
    private final CompoundTag workingData;
    private Tab currentTab = Tab.GENERAL;
    private ConfigListWidget listWidget;

    private int sidebarWidth;
    private final EnumMap<Tab,Double> scrollPositions = new EnumMap<>(Tab.class);
    private String localFogPreset;
    private String customFogName = "";
    private String searchQuery="";

    private boolean saved = false;

    private final List<String> BUILT_IN_FOGS = Arrays.asList("normal", "dense", "clear", "dark", "blood", "nightmare", "green_acid", "none", "sunrise", "sunset", "underwater", "swamp", "volcanic", "mystic", "toxic", "dreamy", "winter", "haunted", "arctic", "prehistoric", "radioactive", "desert", "ashstorm", "eldritch", "space", "corrupted", "celestial", "storm", "abyssal", "netherburn", "elderswamp", "nebula", "wasteland", "void", "festival", "temple", "stormy_night", "obsidian");

    private enum Tab { GENERAL, PLAYER, WAVES, MOBS, ENEMIES, WEAPONS, EXTRAS, CRAFTING, ENVIRONMENT, FOG, SCRIPTING, SYSTEM }
    enum ItemState { ENABLED, MANUALLY_DISABLED, TAG_DISABLED }

    private final Set<String> expandedCategories = new HashSet<>(Arrays.asList("TacZ", "Normal", "Wonder", "enemy:normal:zombie", "enemy:addon:zombierool:water_enemies", "enemy:addon:zombierool:lunar", "enemy:addon:zombierool:intro"));

    public WorldConfigMenuScreen(S2COpenConfigMenuPacket data) {
        super(Component.translatable("gui.zombierool.config.title"));
        this.initData = data;
        this.workingData = data.configData.copy();if(data.configData.getString("focusTab").equals("enemies"))currentTab=Tab.ENEMIES;
        this.localFogPreset = workingData.getString("fogPreset");
        if (this.localFogPreset.isEmpty()) this.localFogPreset = "normal";
        CustomFogManager.load();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        sidebarWidth=Math.max(94,Math.min(140,width/4));
        int buttonHeight=Math.min(20,Math.max(8,(height-106)/Tab.values().length-1));
        for(int i=0;i<Tab.values().length;i++){
            Tab tab=Tab.values()[i];Component label=Component.translatable("config.zombierool.tab."+tab.name().toLowerCase(Locale.ROOT));
            String shortKey="config.zombierool.tab."+tab.name().toLowerCase(Locale.ROOT)+".short";
            String shortLabel=net.minecraft.client.resources.language.I18n.exists(shortKey)?Component.translatable(shortKey).getString():label.getString();
            Button button=Button.builder(Component.literal(font.plainSubstrByWidth(shortLabel,sidebarWidth-20)),b->{scrollPositions.put(currentTab,listWidget.getScrollAmount());currentTab=tab;rebuildWidgets();}).bounds(8,70+i*(buttonHeight+1),sidebarWidth-12,buttonHeight).tooltip(net.minecraft.client.gui.components.Tooltip.create(label)).build();
            button.active=currentTab!=tab;addRenderableWidget(button);
        }
        listWidget=new ConfigListWidget(minecraft,width-sidebarWidth-16,height,60,height-35,25);
        listWidget.setLeftPos(sidebarWidth+8);addWidget(listWidget);
        var search=new EditBox(font,10,40,sidebarWidth-20,20,Component.translatable("config.zombierool.search"));
        search.setMaxLength(128);search.setValue(searchQuery);search.setHint(Component.translatable("config.zombierool.search"));
        search.setResponder(value->{searchQuery=value;refreshList();listWidget.setScrollAmount(0);});addRenderableWidget(search);buildListContent();
        listWidget.setScrollAmount(scrollPositions.getOrDefault(currentTab,0.0));

        this.addRenderableWidget(Button.builder(Component.translatable("config.zombierool.apply"), btn -> {
            saved = true;
            CompoundTag toSend = workingData.copy();
            toSend.putString("action", "save_all");
            NetworkHandler.INSTANCE.sendToServer(new C2SUpdateConfigPacket(toSend));
            this.onClose();
        }).bounds(this.width / 2 - 154, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(width/2+54,height-28,100,20).build());
        if(currentTab!=Tab.SYSTEM&&currentTab!=Tab.SCRIPTING)addRenderableWidget(Button.builder(Component.translatable("config.zombierool.defaults"),b->{resetCurrentSection();refreshList();}).bounds(width/2-50,height-28,100,20).build());
    }

    private void resetCurrentSection(){
        CompoundTag defaults=workingData.getCompound("mapConfigDefaults");
        for(String key:sectionKeys(currentTab)){if(defaults.contains(key))workingData.put(key,defaults.get(key).copy());else workingData.remove(key);}
        if(currentTab==Tab.ENEMIES)workingData.put("specialEnemies",me.cryo.zombierool.config.SpecialEnemyConfig.defaults());
        var schemas=workingData.getCompound("mapConfigSchemas");var addons=workingData.getCompound("addonConfig");
        for(String id:schemas.getAllKeys())if(extensionTab(id,schemas.getCompound(id).getString("category"))==currentTab)addons.put(id,schemas.getCompound(id).getCompound("defaults").copy());
        workingData.put("addonConfig",addons);
        if(currentTab==Tab.PLAYER){var lunar=addons.getCompound("zombierool:lunar");lunar.putString("startingSuit","none");addons.put("zombierool:lunar",lunar);}
        if(currentTab==Tab.FOG){localFogPreset=workingData.getString("fogPreset");applyFogLive();}
    }
    private static List<String> sectionKeys(Tab tab){return switch(tab){
        case GENERAL -> List.of("mapId");
        case PLAYER -> List.of("deathPenalty","starterItem","starterItemTag","startingEquipment","startingTactical","startingLethal","allowDownMovement");
        case MOBS -> List.of("zombieSkinPreset","hellhoundSkinPreset","eyeColorPreset","zombieBaseHealth","zombieMaxHealth","zombiesCanSprint","zombieSprintWave","zombieSprintChance","zombieSprintSpeed","superSprintersEnabled","superSprinterActivationWave","superSprinterChance","superSprinterSpeed","crawlerBaseHealth","crawlerMaxHealth","crawlerGasExplosion","hellhoundBaseHealth","hellhoundMaxHealth","hellhoundFireVariant","allowedMobs");
        case WAVES -> List.of("baseZombies","maxActiveMobsPerPlayer","specialWavesEnabled","specialWaveStart","specialWaveInterval","hellhoundsInNormalWaves","hellhoundsInNormalWavesStart","spawnIntensity");
        case WEAPONS -> List.of("disabledBoxWeapons","customBoxWeapons","customWonderWeapons","mysteryBoxTags","enabledUnmappedWeapons");
        case EXTRAS -> List.of("maxPerks","bonusDropsEnabled","disabledBonuses","disabledRandomPerks");
        case ENVIRONMENT -> List.of("sprintBgSounds","dayNightMode","musicPreset","voicePreset","spookyAmbience","forceHalloween","coldWaterEffectEnabled","particlesEnabled","particleTypeId","particleDensity","particleMode");
        case FOG -> List.of("fogPreset","customFogR","customFogG","customFogB","customFogNear","customFogFar","fogOutdoorsOnly","fogOptions");
        default -> List.of();
    };}
    private static Tab extensionTab(String id,String category){
        if(id.equals("zombierool:lunar"))return Tab.ENVIRONMENT;
        if(id.equals("zombierool:water_enemies"))return Tab.MOBS;
        if(id.equals("zombierool:intro"))return Tab.WAVES;
        return switch(category){case "enemies"->Tab.MOBS;case "weapons"->Tab.WEAPONS;case "extras"->Tab.EXTRAS;case "environment"->Tab.ENVIRONMENT;case "scripting"->Tab.SCRIPTING;case "crafting"->Tab.CRAFTING;default->Tab.GENERAL;};
    }
    private void openTab(Tab tab){scrollPositions.put(currentTab,listWidget.getScrollAmount());currentTab=tab;rebuildWidgets();}
    private void shortcut(Tab tab){listWidget.addEntry(new ActionEntry(Component.translatable("config.zombierool.tab."+tab.name().toLowerCase(Locale.ROOT)),()->openTab(tab),true));}
    private void section(String key){listWidget.addEntry(new TitleEntry(Component.translatable("config.zombierool.section."+key).getString()));}

    private CompoundTag enemies(){if(!workingData.contains("specialEnemies",10))workingData.put("specialEnemies",me.cryo.zombierool.config.SpecialEnemyConfig.defaults());return workingData.getCompound("specialEnemies");}
    private boolean enemyGroup(String id,Component label){String key="enemy:"+id;boolean expanded=!searchQuery.isBlank()||expandedCategories.contains(key);listWidget.addEntry(new CategoryHeaderEntry(label.getString(),expanded,()->toggleCategory(key)));return expanded;}
    private List<String> bossTypes(){return me.cryo.zombierool.config.MiniBossDefinitions.ids();}
    private Component bossLabel(String id){return id.equals("panzer")||id.equals("brutus")?Component.translatable("entity.zombierool."+id):Component.translatable(me.cryo.zombierool.config.MiniBossDefinitions.get(id).getString("name"));}
    private void enemySection(String name){listWidget.addEntry(new TitleEntry(Component.translatable("config.zombierool.enemies.section."+name).getString()));}
    private void enemyNumber(String key){
        var settings=enemies();listWidget.addEntry(new NumberEntry(Component.translatable("config.zombierool.enemies."+key),settings.get(key) instanceof net.minecraft.nbt.IntTag?String.valueOf(settings.getInt(key)):String.valueOf(settings.getDouble(key)),text->{try{double value=Double.parseDouble(text.replace(',','.'));if(Double.isFinite(value)){var next=settings.copy();next.putDouble(key,value);settings.merge(me.cryo.zombierool.config.SpecialEnemyConfig.sanitize(next));}}catch(NumberFormatException ignored){}},Component.translatable("config.zombierool.enemies."+key+".tooltip")));
    }
    private void enemyBool(String key){listWidget.addEntry(new BoolEntry(Component.translatable("config.zombierool.enemies."+key),enemies().getBoolean(key),v->enemies().putBoolean(key,v),Component.translatable("config.zombierool.enemies."+key+".tooltip")));}
    private void enemyLoot(String key){listWidget.addEntry(new ItemSlotEntry(key));listWidget.addEntry(new LootEntry(key));}
    private void wardenKeyLoot(){var values=enemies().getCompound("wardenKeyDrops");enemies().put("wardenKeyDrops",values);String type=enemies().getString("type");listWidget.addEntry(new BoolEntry(Component.translatable("config.zombierool.enemies.wardenKeyLoot"),values.getBoolean(type),v->values.putBoolean(type,v),Component.translatable("config.zombierool.enemies.wardenKeyLoot.tooltip")));}
    class ItemSlotEntry extends ConfigEntry {
        final String key;final Button button;
        ItemStack stack(){if(key.equals("starterItem")){var stack=ItemStack.of(workingData.getCompound("starterItemTag"));if(!stack.isEmpty())return stack;var id=ResourceLocation.tryParse(workingData.getString("starterItem"));var item=id==null?null:ForgeRegistries.ITEMS.getValue(id);return item==null?ItemStack.EMPTY:new ItemStack(item);}return ItemStack.of(enemies().getCompound(key+"Stack"));}
        Component label(){return Component.translatable(key.equals("starterItem")?"config.zombierool.starterItem":"config.zombierool.enemies."+key);}
        ItemSlotEntry(String key){this.key=key;button=Button.builder(Component.empty(),b->minecraft.setScreen(new MapItemSlotScreen(WorldConfigMenuScreen.this,label(),stack(),item->{if(key.equals("starterItem")){item.setCount(item.isEmpty()?0:1);workingData.put("starterItemTag",item.save(new CompoundTag()));workingData.putString("starterItem",item.isEmpty()?"minecraft:air":ForgeRegistries.ITEMS.getKey(item.getItem()).toString());}else{enemies().put(key+"Stack",item.save(new CompoundTag()));enemies().putString(key,"");}}))).bounds(0,0,100,20).build();}
        @Override public void render(GuiGraphics g,int i,int top,int left,int w,int h,int mx,int my,boolean hover,float pt){g.drawString(font,label(),left+4,top+5,0xE5ECF5,false);button.setX(left+w-110);button.setY(top+2);button.render(g,mx,my,pt);var item=stack();if(item.isEmpty())g.drawString(font,Component.translatable("config.zombierool.slot.choose"),button.getX()+5,top+7,0xFFFFFF,false);else{g.renderItem(item,button.getX()+4,top+4);g.renderItemDecorations(font,item,button.getX()+4,top+4);g.drawString(font,font.plainSubstrByWidth(item.getHoverName().getString(),73),button.getX()+23,top+7,0xFFFFFF,false);if(button.isHovered())g.renderTooltip(font,item,mx,my);}}
        @Override public List<? extends GuiEventListener> children(){return List.of(button);}
        @Override public List<? extends NarratableEntry> narratables(){return List.of(button);}
    }
    class LootEntry extends ConfigEntry {
        final String key;final Button button;
        LootEntry(String key){this.key=key;button=Button.builder(Component.translatable("config.zombierool.enemies.chooseLoot"),b->{var ids=new ArrayList<String>();var tags=workingData.getList("availableBossLootTables",8);for(int i=0;i<tags.size();i++)ids.add(tags.getString(i));minecraft.setScreen(new BossLootTableScreen(WorldConfigMenuScreen.this,ids,enemies().getString(key),id->{enemies().putString(key,id);enemies().put(key+"Stack",new CompoundTag());}));}).bounds(0,0,100,20).build();}
        @Override public void render(GuiGraphics g,int i,int top,int left,int w,int h,int mx,int my,boolean hover,float pt){g.drawString(font,Component.translatable("config.zombierool.slot.table"),left+4,top+5,0xE5ECF5,false);String id=enemies().getString(key);button.setMessage(id.isBlank()?Component.translatable("config.zombierool.enemies.noLoot"):Component.literal(font.plainSubstrByWidth(id,88)));button.setX(left+w-110);button.setY(top+2);button.render(g,mx,my,pt);if(button.isHovered())g.renderTooltip(font,id.isBlank()?Component.translatable("config.zombierool.enemies.noLoot"):Component.literal(id),mx,my);}
        @Override public List<? extends GuiEventListener> children(){return List.of(button);}
        @Override public List<? extends NarratableEntry> narratables(){return List.of(button);}
    }

    private void refreshList() {
        if (listWidget == null) return;
        double scroll = listWidget.getScrollAmount();
        listWidget.clearAllEntries();
        buildListContent();
        listWidget.setScrollAmount(scroll);
    }

    private void toggleCategory(String cat) {
        if (expandedCategories.contains(cat)) expandedCategories.remove(cat);
        else expandedCategories.add(cat);
        refreshList();
    }

    private void buildListContent() {
        if(searchQuery.isBlank()){buildTabContent();return;}
        Tab previous=currentTab;
        try {for(Tab tab:Tab.values()){currentTab=tab;buildTabContent();}}
        finally {currentTab=previous;}
    }
    private void buildTabContent() {
        switch (currentTab) {
            case GENERAL -> {
                section("identity");
                String mapIdRaw = workingData.getString("mapId");
                String mapIdDisplay = mapIdRaw.startsWith("zr_") ? mapIdRaw.substring(3) : mapIdRaw;
                listWidget.addEntry(new StringEntry(Component.translatable("config.zombierool.mapId"), mapIdDisplay, newVal -> {
                    workingData.putString("mapId", "zr_" + newVal);
                }, Component.translatable("config.zombierool.mapId.tooltip")));
                section("quick_start");for(Tab tab:List.of(Tab.PLAYER,Tab.WAVES,Tab.MOBS,Tab.ENEMIES,Tab.CRAFTING,Tab.ENVIRONMENT,Tab.SCRIPTING,Tab.SYSTEM))shortcut(tab);
            }
            case PLAYER -> {
                section("survival");
                addCycleRow("deathPenalty","config.zombierool.deathPenalty",Arrays.asList("respawn","spectator","kick"),"config.zombierool.deathPenalty.tooltip");
                addBoolRow("allowDownMovement","config.zombierool.allowDownMovement","config.zombierool.allowDownMovement.tooltip");
                section("loadout");
                startingSuitRow();
                listWidget.addEntry(new ItemSlotEntry("starterItem"));
                addCycleRow("startingEquipment", "config.zombierool.startingEquipment", Arrays.asList("none", "zombierool:c4", "zombierool:claymore", "zombierool:bouncing_betty"), "config.zombierool.startingEquipment.tooltip");
                addCycleRow("startingTactical", "config.zombierool.startingTactical", Arrays.asList("none", "zombierool:flash", "zombierool:pheromone"), "config.zombierool.startingTactical.tooltip");
                addCycleRow("startingLethal", "config.zombierool.startingLethal", Arrays.asList("zombierool:grenade", "zombierool:stielhandgranate", "zombierool:semtex"), "config.zombierool.startingLethal.tooltip");
            }
            case ENEMIES -> {
                listWidget.addEntry(new ActionEntry(Component.translatable("gui.zombierool.actor_studio"),()->minecraft.setScreen(new MapActorStudioScreen(this,null)),true));
                if(enemyGroup("encounters",Component.translatable("config.zombierool.enemies.section.encounters"))){
                    for(String key:List.of("specialWaveEnemy","crawlerVariant"))listWidget.addEntry(new CycleEntry(Component.translatable("config.zombierool.enemies."+key),enemies().getString(key),key.equals("specialWaveEnemy")?List.of("hellhound","pentagon_thief"):List.of("nova","geist"),v->enemies().putString(key,v),Component.translatable("config.zombierool.enemies."+key+".tooltip")));
                    listWidget.addEntry(new CycleEntry(Component.translatable("config.zombierool.enemies.crawlerBehavior"),enemies().getString("crawlerBehavior"),List.of("gas","leaper"),v->enemies().putString("crawlerBehavior",v),Component.translatable("config.zombierool.enemies.crawlerBehavior.tooltip")));
                    for(String key:List.of("thiefEscapeTicks","romeroReturnRounds","romeroRageTicks","romeroRageSpeed","romeroProvokeRadius","astronautDamage","astronautStealPerk","abominationPullDistance","geistLeapDamage"))enemyNumber(key);
                }
                if(enemyGroup("boss",Component.translatable("config.zombierool.enemies.section.boss"))){
                    listWidget.addEntry(new CycleEntry(Component.translatable("config.zombierool.enemies.type"),enemies().getString("type"),bossTypes(),v->{enemies().putString("type",v);refreshList();},Component.translatable("config.zombierool.enemies.type.tooltip")));
                    if(!enemies().getString("type").equals("zombierool:george_romero"))for(String key:List.of("firstWave","interval"))enemyNumber(key);
                }
                if(enemyGroup("transition",Component.translatable("config.zombierool.enemies.section.transition"))){
                    listWidget.addEntry(new CycleEntry(Component.translatable("config.zombierool.enemies.bossSwitchTrigger"),enemies().getString("bossSwitchTrigger"),List.of("none","wave","activator","power","soul_checker"),v->{enemies().putString("bossSwitchTrigger",v);refreshList();},Component.translatable("config.zombierool.enemies.bossSwitchTrigger.tooltip")));
                    String trigger=enemies().getString("bossSwitchTrigger");if(!trigger.equals("none")){listWidget.addEntry(new CycleEntry(Component.translatable("config.zombierool.enemies.bossSwitchTarget"),enemies().getString("bossSwitchTarget"),bossTypes(),v->enemies().putString("bossSwitchTarget",v),Component.translatable("config.zombierool.enemies.bossSwitchTarget.tooltip")));if(trigger.equals("wave"))enemyNumber("bossSwitchWave");if(trigger.equals("activator")||trigger.equals("soul_checker"))for(String key:List.of("bossSwitchX","bossSwitchY","bossSwitchZ"))enemyNumber(key);if(trigger.equals("soul_checker"))listWidget.addEntry(new StringEntry(Component.translatable("config.zombierool.enemies.bossSwitchSoulIds"),enemies().getString("bossSwitchSoulIds"),v->enemies().putString("bossSwitchSoulIds",v),Component.translatable("config.zombierool.enemies.bossSwitchSoulIds.tooltip")));}
                }
                if(enemyGroup("loot",Component.translatable("config.zombierool.enemies.section.loot"))){wardenKeyLoot();enemyLoot("firstLoot");enemyLoot("recurringLoot");enemyBool("recurringOnFirst");}
                if(enemyGroup("balance",Component.translatable("config.zombierool.enemies.section.balance")))for(String key:List.of("waveHealthGrowth","extraPlayerHealth","bodyDamage","teleporterDamage","screenShake"))enemyNumber(key);
                for(String type:bossTypes())if(enemyGroup("registered:"+type,bossLabel(type))){
                    if(type.equals("panzer"))for(String key:List.of("panzerHealth","panzerSpeed","panzerFlameDamage","panzerHoldTicks","clawSpeed","clawRadius","clawWindup"))enemyNumber(key);
                    else if(type.equals("brutus"))for(String key:List.of("brutusHealth","brutusSpeed","brutusUnlockCost","brutusUnlockTicks"))enemyNumber(key);
                    else {var values=me.cryo.zombierool.config.MiniBossDefinitions.get(type);values.merge(enemies().getCompound("bossOverrides").getCompound(type));var overrides=enemies().getCompound("bossOverrides");enemies().put("bossOverrides",overrides);var saved=new CompoundTag();for(String key:List.of("health","speed","damage")){saved.putDouble(key,values.getDouble(key));listWidget.addEntry(new NumberEntry(Component.translatable("config.zombierool.enemies.custom."+key),String.valueOf(values.getDouble(key)),text->{try{double value=Double.parseDouble(text.replace(',','.'));if(Double.isFinite(value))saved.putDouble(key,value);}catch(NumberFormatException ignored){}},Component.translatable("config.zombierool.enemies.custom."+key)));}overrides.put(type,saved);}
                }
                if(enemyGroup("napalm",Component.translatable("entity.zombierool.napalm_minion"))){enemyBool("napalmUseRadius");for(String key:List.of("napalmFirstWave","napalmRadius","napalmCooldown","napalmMaxAlive","napalmBatch","napalmHealth","napalmSpeed","napalmEnragedSpeed","napalmWarningRadius","napalmDetonationRadius","fireDuration"))enemyNumber(key);}
                if(enemyGroup("skeleton",Component.translatable("entity.zombierool.skeleton_minion"))){enemyBool("skeletonUseRadius");for(String key:List.of("skeletonFirstWave","skeletonRadius","skeletonCooldown","skeletonMaxAlive","skeletonBatch","skeletonHealth","skeletonSpeed"))enemyNumber(key);}
            }
            case MOBS -> {
                section("appearance");
                addCycleRow("zombieSkinPreset", "config.zombierool.zombieSkinPreset", me.cryo.zombierool.config.EnemySkinPresets.zombies(), "config.zombierool.zombieSkinPreset.tooltip");
                var houndPresets = new java.util.ArrayList<String>(); houndPresets.add("auto"); houndPresets.addAll(me.cryo.zombierool.config.EnemySkinPresets.hounds());
                addCycleRow("hellhoundSkinPreset", "config.zombierool.hellhoundSkinPreset", houndPresets, "config.zombierool.hellhoundSkinPreset.tooltip");
                addCycleRow("eyeColorPreset","config.zombierool.eyeColorPreset",Arrays.asList("red","blue","green","default"),"config.zombierool.eyeColorPreset.tooltip");
                addExtensionRows();
                section("attributes");

                if(enemyGroup("normal:zombie",Component.translatable("entity.zombierool.zombie"))){
                    addFloatRow("zombieBaseHealth","config.zombierool.zombieBaseHealth",1,100,"config.zombierool.zombieBaseHealth.tooltip");addFloatRow("zombieMaxHealth","config.zombierool.zombieMaxHealth",100,5000,"config.zombierool.zombieMaxHealth.tooltip");
                    addBoolRow("zombiesCanSprint","config.zombierool.zombiesCanSprint","config.zombierool.zombiesCanSprint.tooltip");addIntRow("zombieSprintWave","config.zombierool.zombieSprintWave",1,100,"config.zombierool.zombieSprintWave.tooltip");addFloatRow("zombieSprintChance","config.zombierool.zombieSprintChance",0,1,"config.zombierool.zombieSprintChance.tooltip");addFloatRow("zombieSprintSpeed","config.zombierool.zombieSprintSpeed",.1f,1,"config.zombierool.zombieSprintSpeed.tooltip");addBoolRow("superSprintersEnabled","config.zombierool.superSprintersEnabled","config.zombierool.superSprintersEnabled.tooltip");addIntRow("superSprinterActivationWave","config.zombierool.superSprinterActivationWave",1,100,"config.zombierool.superSprinterActivationWave.tooltip");addFloatRow("superSprinterChance","config.zombierool.superSprinterChance",0,1,"config.zombierool.superSprinterChance.tooltip");addFloatRow("superSprinterSpeed","config.zombierool.superSprinterSpeed",.1f,1.5f,"config.zombierool.superSprinterSpeed.tooltip");
                }
                if(enemyGroup("normal:crawler",Component.translatable("entity.zombierool.crawler"))){addFloatRow("crawlerBaseHealth","config.zombierool.crawlerBaseHealth",1,100,"config.zombierool.crawlerBaseHealth.tooltip");addFloatRow("crawlerMaxHealth","config.zombierool.crawlerMaxHealth",100,5000,"config.zombierool.crawlerMaxHealth.tooltip");addBoolRow("crawlerGasExplosion","config.zombierool.crawlerGasExplosion","config.zombierool.crawlerGasExplosion.tooltip");}
                if(enemyGroup("normal:hellhound",Component.translatable("entity.zombierool.hellhound"))){addFloatRow("hellhoundBaseHealth","config.zombierool.hellhoundBaseHealth",1,100,"config.zombierool.hellhoundBaseHealth.tooltip");addFloatRow("hellhoundMaxHealth","config.zombierool.hellhoundMaxHealth",100,5000,"config.zombierool.hellhoundMaxHealth.tooltip");addBoolRow("hellhoundFireVariant","config.zombierool.hellhoundFireVariant","config.zombierool.hellhoundFireVariant.tooltip");}
                for(var type:ForgeRegistries.ENTITY_TYPES.getValues().stream().filter(e->ForgeRegistries.ENTITY_TYPES.getKey(e).getNamespace().equals("zombierool")&&e.getCategory()!=net.minecraft.world.entity.MobCategory.MISC).sorted(Comparator.comparing(e->ForgeRegistries.ENTITY_TYPES.getKey(e).toString())).toList()){
                    String id=ForgeRegistries.ENTITY_TYPES.getKey(type).toString();if(id.equals("zombierool:mr_chief"))continue;
                    if(enemyGroup("entity:"+id,type.getDescription()))listWidget.addEntry(new BoolEntry(Component.translatable("config.zombierool.enemies.allowed"),getSet("allowedMobs").contains(id),enabled->{var set=getSet("allowedMobs");if(enabled)set.add(id);else set.remove(id);saveSet("allowedMobs",set);},Component.translatable("config.zombierool.enemies.allowed.tooltip")));
                }

            }
            case WAVES -> {
                section("round_flow");
                addIntRow("baseZombies", "config.zombierool.baseZombies", 1, 100, "config.zombierool.baseZombies.tooltip");
                addIntRow("maxActiveMobsPerPlayer", "config.zombierool.maxActiveMobsPerPlayer", 40, 50, "config.zombierool.maxActiveMobsPerPlayer.tooltip");

                addBoolRow("specialWavesEnabled", "config.zombierool.specialWavesEnabled", "config.zombierool.specialWavesEnabled.tooltip");
                addIntRow("specialWaveStart", "config.zombierool.specialWaveStart", 1, 100, "config.zombierool.specialWaveStart.tooltip");
                addIntRow("specialWaveInterval", "config.zombierool.specialWaveInterval", 1, 100, "config.zombierool.specialWaveInterval.tooltip");

                addBoolRow("hellhoundsInNormalWaves", "config.zombierool.hellhoundsInNormalWaves", "config.zombierool.hellhoundsInNormalWaves.tooltip");
                addIntRow("hellhoundsInNormalWavesStart", "config.zombierool.hellhoundsInNormalWavesStart", 1, 100, "config.zombierool.hellhoundsInNormalWavesStart.tooltip");

                addCycleRow("spawnIntensity", "config.zombierool.spawnIntensity", Arrays.asList("normal", "flood", "chaos"), "config.zombierool.spawnIntensity.tooltip");
            }
            case WEAPONS -> {
                boolean tagsExpanded = !searchQuery.isBlank() || expandedCategories.contains("Tags");
                listWidget.addEntry(new CategoryHeaderEntry(Component.translatable("gui.zombierool.config.tags_filter").getString(), tagsExpanded, () -> toggleCategory("Tags")));
                Set<String> activeTags = getSet("mysteryBoxTags");
                if (tagsExpanded) {
                    Set<String> allTags = new TreeSet<>();
                    for (WeaponSystem.Definition def : WeaponSystem.Loader.LOADED_DEFINITIONS.values()) {
                        if (def.tags != null) allTags.addAll(def.tags);
                    }
                    List<TagItem> currentTagRow = new ArrayList<>();
                    for (String tag : allTags) {
                        currentTagRow.add(new TagItem(tag, activeTags.contains(tag), () -> {
                            Set<String> s = getSet("mysteryBoxTags");
                            if (s.contains(tag)) s.remove(tag); else s.add(tag);
                            saveSet("mysteryBoxTags", s);
                            refreshList();
                        }));
                        if (currentTagRow.size() == 4) {
                            listWidget.addEntry(new TagGridRowEntry(new ArrayList<>(currentTagRow)));
                            currentTagRow.clear();
                        }
                    }
                    if (!currentTagRow.isEmpty()) listWidget.addEntry(new TagGridRowEntry(new ArrayList<>(currentTagRow)));
                }

                boolean customExpanded = !searchQuery.isBlank() || expandedCategories.contains("Custom");
                listWidget.addEntry(new CategoryHeaderEntry(Component.translatable("gui.zombierool.config.custom_weapons").getString(), customExpanded, () -> toggleCategory("Custom")));

                if (customExpanded) {
                    listWidget.addEntry(new CustomItemAddEntry());

                    List<GridItem> customIcons = new ArrayList<>();
                    Set<String> customWpns = getSet("customBoxWeapons");
                    Set<String> customWonder = getSet("customWonderWeapons");

                    for (String idStr : customWpns) {
                        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(idStr));
                        if (item != null && item != net.minecraft.world.item.Items.AIR) {
                            List<Component> tp = new ArrayList<>();
                            tp.add(Component.translatable("gui.zombierool.config.remove_custom"));
                            tp.add(item.getDescription());
                            if (customWonder.contains(idStr)) tp.add(Component.translatable("gui.zombierool.config.wonder_weapon").withStyle(ChatFormatting.LIGHT_PURPLE));

                            customIcons.add(new GridItem(
                                new ItemStack(item), null, ItemState.ENABLED,
                                () -> {
                                    Set<String> s = getSet("customBoxWeapons");
                                    s.remove(idStr);
                                    saveSet("customBoxWeapons", s);

                                    Set<String> w = getSet("customWonderWeapons");
                                    w.remove(idStr);
                                    saveSet("customWonderWeapons", w);

                                    refreshList();
                                },
                                null, tp
                            ));
                        }
                    }
                    if (!customIcons.isEmpty()) addGridRowsWithColumnControls(customIcons);
                }

                Set<String> disWpns = getSet("disabledBoxWeapons");
                List<GridItem> normalIcons = new ArrayList<>();
                List<GridItem> wonderIcons = new ArrayList<>();

                for (WeaponSystem.Definition def : WeaponSystem.Loader.LOADED_DEFINITIONS.values()) {
                    ItemStack displayStack = WeaponFacade.createWeaponStack(def.id, false, null);
                    if (displayStack.isEmpty()) continue;

                    boolean isWonder = def.is_wonder_weapon || "WONDER".equalsIgnoreCase(def.type);
                    boolean matchesTag = activeTags.isEmpty();
                    if (!matchesTag && def.tags != null) {
                        for (String t : activeTags) {
                            if (def.tags.contains(t)) {
                                matchesTag = true;
                                break;
                            }
                        }
                    }

                    String identifier = def.id.contains(":") ? def.id : "zombierool:" + def.id;
                    boolean manuallyDisabled = disWpns.contains(identifier);
                    ItemState state = manuallyDisabled ? ItemState.MANUALLY_DISABLED : (!matchesTag ? ItemState.TAG_DISABLED : ItemState.ENABLED);

                    List<Component> tp = new ArrayList<>();
                    tp.add(Component.literal((state == ItemState.ENABLED ? Component.translatable("gui.zombierool.config.disable").getString() : Component.translatable("gui.zombierool.config.enable").getString()) + displayStack.getHoverName().getString()));
                    if (state == ItemState.ENABLED) tp.add(Component.translatable("gui.zombierool.config.status.enabled").withStyle(ChatFormatting.GREEN));
                    else if (state == ItemState.MANUALLY_DISABLED) tp.add(Component.translatable("gui.zombierool.config.status.disabled.manual").withStyle(ChatFormatting.RED));
                    else if (state == ItemState.TAG_DISABLED) tp.add(Component.translatable("gui.zombierool.config.status.disabled.tag").withStyle(ChatFormatting.GRAY));

                    GridItem gItem = new GridItem(
                        displayStack, null, state,
                        () -> {
                            Set<String> s = getSet("disabledBoxWeapons");
                            if (s.contains(identifier)) s.remove(identifier); else s.add(identifier);
                            saveSet("disabledBoxWeapons", s);
                            refreshList();
                        },
                        null, tp
                    );

                    if (isWonder) wonderIcons.add(gItem);
                    else normalIcons.add(gItem);
                }

                var throwableTag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,new ResourceLocation("zombierool","map_throwables"));
                List<Item> throwables = ForgeRegistries.ITEMS.getValues().stream().filter(item->item instanceof me.cryo.zombierool.item.throwable.ThrowableCore.BaseThrowableItem || new ItemStack(item).is(throwableTag)).sorted(Comparator.comparing(item->ForgeRegistries.ITEMS.getKey(item).toString())).toList();

                for (Item throwable : throwables) {
                    ResourceLocation reg = ForgeRegistries.ITEMS.getKey(throwable);
                    String identifier = reg.toString();
                    boolean manuallyDisabled = disWpns.contains(identifier);
                    ItemState state = manuallyDisabled ? ItemState.MANUALLY_DISABLED : ItemState.ENABLED;

                    List<Component> tp = new ArrayList<>();
                    tp.add(Component.literal((state == ItemState.ENABLED ? Component.translatable("gui.zombierool.config.disable").getString() : Component.translatable("gui.zombierool.config.enable").getString()) + new ItemStack(throwable).getHoverName().getString()));
                    if (state == ItemState.ENABLED) tp.add(Component.translatable("gui.zombierool.config.status.enabled").withStyle(ChatFormatting.GREEN));
                    else if (state == ItemState.MANUALLY_DISABLED) tp.add(Component.translatable("gui.zombierool.config.status.disabled.manual").withStyle(ChatFormatting.RED));

                    GridItem gItem = new GridItem(new ItemStack(throwable), null, state, () -> {
                        Set<String> s = getSet("disabledBoxWeapons");
                        if (s.contains(identifier)) s.remove(identifier); else s.add(identifier);
                        saveSet("disabledBoxWeapons", s);
                        refreshList();
                    }, null, tp);

                    normalIcons.add(gItem);
                }

                boolean wonderExpanded = !searchQuery.isBlank() || expandedCategories.contains("Wonder");
                listWidget.addEntry(new CategoryHeaderEntry(Component.translatable("gui.zombierool.config.wonder_weapons").getString(), wonderExpanded, () -> toggleCategory("Wonder")));
                if (wonderExpanded) addGridRowsWithColumnControls(wonderIcons);

                boolean normalExpanded = !searchQuery.isBlank() || expandedCategories.contains("Normal");
                listWidget.addEntry(new CategoryHeaderEntry(Component.translatable("gui.zombierool.config.normal_weapons").getString(), normalExpanded, () -> toggleCategory("Normal")));
                if (normalExpanded) addGridRowsWithColumnControls(normalIcons);

                List<ResourceLocation> unmappedGuns = WeaponFacade.getUnmappedTaczGuns();
                if (!unmappedGuns.isEmpty()) {
                    boolean taczExpanded = !searchQuery.isBlank() || expandedCategories.contains("TacZ");
                    listWidget.addEntry(new CategoryHeaderEntry(Component.translatable("gui.zombierool.config.tacz_guns").getString(), taczExpanded, () -> toggleCategory("TacZ")));

                    if (taczExpanded) {
                        List<GridItem> taczIcons = new ArrayList<>();
                        Set<String> enabledUnmapped = getSet("enabledUnmappedWeapons");

                        for (ResourceLocation taczId : unmappedGuns) {
                            ItemStack displayStack = WeaponFacade.createUnmappedTaczWeaponStack(taczId, false);
                            boolean isEnabled = enabledUnmapped.contains(taczId.toString());
                            ItemState state = isEnabled ? ItemState.ENABLED : ItemState.MANUALLY_DISABLED;

                            List<Component> tp = new ArrayList<>();
                            tp.add(Component.literal(taczId.toString()).withStyle(ChatFormatting.GOLD));
                            if (state == ItemState.ENABLED) tp.add(Component.translatable("gui.zombierool.config.status.enabled").withStyle(ChatFormatting.GREEN));
                            else tp.add(Component.translatable("gui.zombierool.config.status.disabled.default").withStyle(ChatFormatting.RED));
                            tp.add(Component.translatable("gui.zombierool.config.click.toggle").withStyle(ChatFormatting.GRAY));
                            tp.add(Component.translatable("gui.zombierool.config.click.json").withStyle(ChatFormatting.AQUA));

                            taczIcons.add(new GridItem(
                                displayStack, null, state,
                                () -> {
                                    Set<String> s = getSet("enabledUnmappedWeapons");
                                    if (s.contains(taczId.toString())) s.remove(taczId.toString()); else s.add(taczId.toString());
                                    saveSet("enabledUnmappedWeapons", s);
                                    refreshList();
                                },
                                () -> {
                                    NetworkHandler.INSTANCE.sendToServer(new C2SGenerateWeaponMappingPacket(taczId));
                                    refreshList();
                                },
                                tp
                            ));
                        }
                        addGridRowsWithColumnControls(taczIcons);
                    }
                }
            }
            case EXTRAS -> {
                addBoolRow("bonusDropsEnabled", "config.zombierool.bonusDropsEnabled", "config.zombierool.bonusDropsEnabled.tooltip");

                listWidget.addEntry(new TitleEntry("--- " + Component.translatable("gui.zombierool.config.perks").getString() + " ---"));
                addIntRow("maxPerks", "config.zombierool.maxPerks", -1, 256, "config.zombierool.maxPerks.tooltip");
                List<GridItem> perkIcons = new ArrayList<>();
                Set<String> disPerks = getSet("disabledRandomPerks");

                for (String perkId : PerksManager.ALL_PERKS.keySet()) {
                    PerksManager.Perk p = PerksManager.ALL_PERKS.get(perkId);
                    ResourceLocation tex = new ResourceLocation(p.getIconPath());
                    ItemState state = !disPerks.contains(perkId) ? ItemState.ENABLED : ItemState.MANUALLY_DISABLED;

                    List<Component> tp = new ArrayList<>();
                    tp.add(Component.translatable("config.zombierool.toggle_perk",p.getName()));
                    if (state == ItemState.ENABLED) tp.add(Component.translatable("gui.zombierool.config.status.enabled").withStyle(ChatFormatting.GREEN));
                    else tp.add(Component.translatable("gui.zombierool.config.status.disabled").withStyle(ChatFormatting.RED));

                    perkIcons.add(new GridItem(
                        ItemStack.EMPTY, tex, state,
                        () -> {
                            Set<String> s = getSet("disabledRandomPerks");
                            if (s.contains(perkId)) s.remove(perkId); else s.add(perkId);
                            saveSet("disabledRandomPerks", s);
                            refreshList();
                        },
                        null, tp
                    ));
                }
                addGridRowsWithColumnControls(perkIcons);

                listWidget.addEntry(new TitleEntry("--- " + Component.translatable("gui.zombierool.config.powerups").getString() + " ---"));
                List<GridItem> bonusIcons = new ArrayList<>();
                Set<String> disBonuses = getSet("disabledBonuses");

                for (String bonusId : BonusManager.ALL_BONUSES.keySet()) {
                    String tex = BonusManager.ALL_BONUSES.get(bonusId).iconPath;
                    ItemState state = !disBonuses.contains(bonusId) ? ItemState.ENABLED : ItemState.MANUALLY_DISABLED;

                    List<Component> tp = new ArrayList<>();
                    tp.add(Component.translatable("config.zombierool.toggle_bonus",bonusId));
                    if (state == ItemState.ENABLED) tp.add(Component.translatable("gui.zombierool.config.status.enabled").withStyle(ChatFormatting.GREEN));
                    else tp.add(Component.translatable("gui.zombierool.config.status.disabled").withStyle(ChatFormatting.RED));

                    bonusIcons.add(new GridItem(
                        ItemStack.EMPTY, new ResourceLocation(tex), state,
                        () -> {
                            Set<String> s = getSet("disabledBonuses");
                            if (s.contains(bonusId)) s.remove(bonusId); else s.add(bonusId);
                            saveSet("disabledBonuses", s);
                            refreshList();
                        },
                        null, tp
                    ));
                }
                addGridRowsWithColumnControls(bonusIcons);
            }
            case ENVIRONMENT -> {
                listWidget.addEntry(new ActionEntry(Component.translatable("gui.zombierool.atmosphere.title"),()->MapAtmosphereScreen.request(this),true));
                section("world");shortcut(Tab.FOG);
                addCycleRow("dayNightMode","config.zombierool.dayNightMode",Arrays.asList("day","night","cycle"),"config.zombierool.dayNightMode.tooltip");
                addBoolRow("coldWaterEffectEnabled","config.zombierool.coldWaterEffectEnabled","config.zombierool.coldWaterEffectEnabled.tooltip");
                section("audio");
                addBoolRow("sprintBgSounds","config.zombierool.sprintBgSounds","config.zombierool.sprintBgSounds.tooltip");
                addCycleRow("musicPreset","config.zombierool.musicPreset",Arrays.asList("default","damned","none"),"config.zombierool.musicPreset.tooltip");
                addCycleRow("voicePreset","config.zombierool.voicePreset",Arrays.asList("uk","us","ru","fr","ger","none"),"config.zombierool.voicePreset.tooltip");
                addBoolRow("spookyAmbience","config.zombierool.spookyAmbience","config.zombierool.spookyAmbience.tooltip");
                addBoolRow("forceHalloween","config.zombierool.forceHalloween","config.zombierool.forceHalloween.tooltip");
                section("particles");
                addBoolRow("particlesEnabled","config.zombierool.particlesEnabled","config.zombierool.particlesEnabled");
                listWidget.addEntry(new ActionEntry(Component.translatable("config.zombierool.particleTypeId").append(": ").append(workingData.getString("particleTypeId")),()->minecraft.setScreen(new ParticlePickerScreen(this,workingData.getString("particleTypeId"),value->{workingData.putString("particleTypeId",value);refreshList();})),true));
                addCycleRow("particleDensity","config.zombierool.particleDensity",List.of("sparse","normal","dense","very_dense"),"config.zombierool.particleDensity");
                addCycleRow("particleMode","config.zombierool.particleMode",List.of("global","atmospheric"),"config.zombierool.particleMode");
            }
            case FOG -> buildFogRows();
            case CRAFTING -> {
                section("crafting");listWidget.addEntry(new ActionEntry(Component.translatable("gui.zombierool.buildable.title"),()->me.cryo.zombierool.gameplay.BuildablePackets.request("open",new net.minecraft.nbt.CompoundTag()),true));
                section("deliveries");listWidget.addEntry(new ActionEntry(Component.translatable("gui.zombierool.delivery.sky_title"),()->MachineDeliveryScreen.requestSky(this),true));
            }
            case SCRIPTING -> {
                listWidget.addEntry(new TitleEntry(Component.translatable("config.zombierool.scripting.title").getString()));
                listWidget.addEntry(new TitleEntry(Component.translatable("config.zombierool.scripting.help").getString()));
                listWidget.addEntry(new ActionEntry(Component.translatable("config.zombierool.scripting.guided"),()->minecraft.setScreen(new LuaWorkbenchScreen(this,0)),true));
                listWidget.addEntry(new ActionEntry(Component.translatable("config.zombierool.scripting.code"),()->minecraft.setScreen(new LuaWorkbenchScreen(this,1)),true));
                listWidget.addEntry(new ActionEntry(Component.translatable("config.zombierool.scripting.api"),()->minecraft.setScreen(new LuaWorkbenchScreen(this,2)),true));
                listWidget.addEntry(new ActionEntry(Component.translatable("config.zombierool.scripting.console"),()->minecraft.setScreen(new LuaWorkbenchScreen(this,3)),true));
            }
            case SYSTEM -> {
                section("match");
                listWidget.addEntry(new TitleEntry(Component.translatable(initData.isGameRunning?"gui.zombierool.config.status.running":"gui.zombierool.config.status.stopped").getString()));
                listWidget.addEntry(new ActionEntry(Component.translatable("gui.zombierool.config.start_game"),()->{sendWaveCmd("start");onClose();},!initData.isGameRunning));
                listWidget.addEntry(new ActionEntry(Component.translatable("gui.zombierool.config.end_game"),()->{sendWaveCmd("end");onClose();},initData.isGameRunning));
                listWidget.addEntry(new NumberEntry(Component.translatable("config.zombierool.selectedWave"),String.valueOf(workingData.contains("_selectedWave")?workingData.getInt("_selectedWave"):initData.currentWave),value->{try{workingData.putInt("_selectedWave",Math.max(1,Integer.parseInt(value)));}catch(NumberFormatException ignored){}},Component.translatable("config.zombierool.selectedWave")));
                listWidget.addEntry(new ActionEntry(Component.translatable("gui.zombierool.config.set_wave"),()->{sendWaveCmd("setWave "+Math.max(1,workingData.getInt("_selectedWave")));onClose();},initData.isGameRunning));
            }
            default -> {}
        }
        if(currentTab!=Tab.MOBS)addExtensionRows();
    }

    private void startingSuitRow(){
        var addons=workingData.getCompound("addonConfig");var lunar=addons.getCompound("zombierool:lunar");
        addons.put("zombierool:lunar",lunar);workingData.put("addonConfig",addons);
        String selected=lunar.getString("startingSuit");if(!List.of("none","lunar","diving","hazmat").contains(selected))selected="none";
        var choices=new ListTag();for(String id:List.of("none","lunar","diving","hazmat")){var row=new CompoundTag();row.putString("id",id);row.putString("name",Component.translatable("config.zombierool.startingSuit."+id).getString());choices.add(row);}
        listWidget.addEntry(new ActionEntry(Component.translatable("config.zombierool.startingSuit").append(": ").append(Component.translatable("config.zombierool.startingSuit."+selected)),()->minecraft.setScreen(new AtmospherePresetPickerScreen(this,choices,lunar.getString("startingSuit"),id->{lunar.putString("startingSuit",id);refreshList();},Component.translatable("config.zombierool.startingSuit"),false)),true));
    }

    private void addExtensionRows(){
        var schemas=workingData.getCompound("mapConfigSchemas");var configs=workingData.getCompound("addonConfig");workingData.put("addonConfig",configs);
        for(String id:new TreeSet<>(schemas.getAllKeys())){var schema=schemas.getCompound(id);if(extensionTab(id,schema.getString("category"))!=currentTab)continue;var values=schema.getCompound("defaults").copy();values.merge(configs.getCompound(id));configs.put(id,values);if(!enemyGroup("addon:"+id,(net.minecraft.client.resources.language.I18n.exists("config."+id.replace(':','.'))?Component.translatable("config."+id.replace(':','.')):Component.literal(id))))continue;
            for(String key:extensionKeys(id,values)){if(id.equals("zombierool:lunar")&&key.equals("startingSuit"))continue;var tag=values.get(key);String labelKey="config."+id.replace(':','.')+"."+key;Component label=net.minecraft.client.resources.language.I18n.exists(labelKey)?Component.translatable(labelKey):Component.literal(key);if(tag instanceof net.minecraft.nbt.ByteTag)listWidget.addEntry(new BoolEntry(label,values.getBoolean(key),v->values.putBoolean(key,v),label));else if(tag instanceof net.minecraft.nbt.NumericTag)listWidget.addEntry(new NumberEntry(label,tag instanceof net.minecraft.nbt.IntTag?String.valueOf(values.getInt(key)):String.valueOf(values.getDouble(key)),v->{try{double n=Double.parseDouble(v);if(Double.isFinite(n)){if(tag instanceof net.minecraft.nbt.IntTag)values.putInt(key,(int)n);else values.putDouble(key,n);}}catch(NumberFormatException ignored){}},label));else if(tag instanceof StringTag)listWidget.addEntry(new StringEntry(label,values.getString(key),v->values.putString(key,v),label));}
        }
    }

    private static List<String> extensionKeys(String id,CompoundTag values){
        var result=new ArrayList<String>();
        if(id.equals("zombierool:lunar"))result.addAll(List.of("gravity","vacuum","oxygenSeconds"));
        if(id.equals("zombierool:intro"))result.addAll(List.of("enabled","spawnerChannels","rampSeconds","sprintThreshold","healthRoundSeconds","houndStartSeconds","initialIntervalTicks","finalIntervalTicks","initialAliveCap","finalAliveCap","houndChance"));
        for(String key:new TreeSet<>(values.getAllKeys()))if(!result.contains(key))result.add(key);
        result.removeIf(key->!values.contains(key));return result;
    }

    private void addGridRowsWithColumnControls(List<GridItem> items) {
        if (items.isEmpty()) return;
        int itemsPerRow = Math.max(1,Math.min(12,listWidget.getRowWidth()/22));

        listWidget.addEntry(new GridColumnControlsEntry(items));

        for (int i = 0; i < items.size(); i += itemsPerRow) {
            int end = Math.min(i + itemsPerRow, items.size());
            listWidget.addEntry(new GridRowEntry(items.subList(i, end)));
        }
    }

    private void buildFogRows(){
        section("fog");var presets=new ArrayList<>(BUILT_IN_FOGS);presets.addAll(CustomFogManager.getCustomPresets().keySet());if(!presets.contains(localFogPreset))presets.add(localFogPreset);
        listWidget.addEntry(new ActionEntry(Component.translatable("config.zombierool.fogPreset").append(": ").append(localFogPreset),()->minecraft.setScreen(new FogPresetPickerScreen(this,presets,localFogPreset,preset->{localFogPreset=preset;workingData.putString("fogPreset",preset);var data=CustomFogManager.getCustomPresets().get(preset);if(data!=null){workingData.putFloat("customFogR",data.r);workingData.putFloat("customFogG",data.g);workingData.putFloat("customFogB",data.b);workingData.putFloat("customFogNear",data.near);workingData.putFloat("customFogFar",data.far);workingData.put("fogOptions",me.cryo.zombierool.config.FogOptions.sanitize(CustomFogManager.options(data)));}applyFogLive();})),true));
        listWidget.addEntry(new BoolEntry(Component.translatable("config.zombierool.fogOutdoorsOnly"),workingData.getBoolean("fogOutdoorsOnly"),value->{workingData.putBoolean("fogOutdoorsOnly",value);applyFogLive();},Component.translatable("config.zombierool.fogOutdoorsOnly")));
        for(String key:List.of("customFogR","customFogG","customFogB","customFogNear","customFogFar"))listWidget.addEntry(new NumberEntry(Component.translatable("config.zombierool."+key),String.valueOf(workingData.getFloat(key)),text->{try{float value=Float.parseFloat(text.replace(',','.'));if(Float.isFinite(value)){workingData.putFloat(key,value);localFogPreset="custom";workingData.putString("fogPreset",localFogPreset);applyFogLive();}}catch(NumberFormatException ignored){}},Component.translatable("config.zombierool."+key)));
        var options=me.cryo.zombierool.config.FogOptions.sanitize(workingData.getCompound("fogOptions"));workingData.put("fogOptions",options);
        listWidget.addEntry(new BoolEntry(Component.literal("Spherical fog (off: cylindrical)"),options.getString("shape").equals("sphere"),v->{options.putString("shape",v?"sphere":"cylinder");applyFogLive();},Component.literal("Spherical includes vertical distance; cylindrical follows horizontal distance.")));
        for(String key:List.of("heightEnabled","pulseOverride","pulse"))listWidget.addEntry(new BoolEntry(Component.translatable("config.zombierool.fog."+key),options.getBoolean(key),v->{options.putBoolean(key,v);applyFogLive();},Component.translatable("config.zombierool.fog."+key)));
        for(String key:List.of("opacity","height","heightFade","pulseAmount","pulseSeconds"))listWidget.addEntry(new NumberEntry(Component.translatable("config.zombierool.fog."+key),String.valueOf(options.getDouble(key)),text->{try{double v=Double.parseDouble(text.replace(',','.'));if(Double.isFinite(v)){options.putDouble(key,v);applyFogLive();}}catch(NumberFormatException ignored){}},Component.translatable("config.zombierool.fog."+key)));
        section("presets");listWidget.addEntry(new StringEntry(Component.translatable("config.zombierool.presetName"),customFogName,value->customFogName=value,Component.translatable("config.zombierool.presetName")));
        listWidget.addEntry(new ActionEntry(Component.translatable("gui.zombierool.config.save_preset"),()->{String name=customFogName.trim();if(!name.isEmpty()&&!BUILT_IN_FOGS.contains(name)){CustomFogManager.savePreset(name,workingData.getFloat("customFogR"),workingData.getFloat("customFogG"),workingData.getFloat("customFogB"),workingData.getFloat("customFogNear"),workingData.getFloat("customFogFar"));CustomFogManager.saveOptions(name,workingData.getCompound("fogOptions"));localFogPreset=name;workingData.putString("fogPreset",name);refreshList();}},true));
        listWidget.addEntry(new ActionEntry(Component.translatable("gui.zombierool.config.delete"),()->{if(CustomFogManager.getCustomPresets().containsKey(customFogName.trim())){CustomFogManager.deletePreset(customFogName.trim());localFogPreset="normal";workingData.putString("fogPreset",localFogPreset);applyFogLive();refreshList();}},true));
    }

    private void applyFogLive() {
        ClientEnvironmentEffects.setFogPreset(localFogPreset,
            workingData.getFloat("customFogR"), workingData.getFloat("customFogG"), workingData.getFloat("customFogB"),
            workingData.getFloat("customFogNear"), workingData.getFloat("customFogFar"),
            workingData.getBoolean("fogOutdoorsOnly"));
        ClientEnvironmentEffects.setFogOptions(workingData.getCompound("fogOptions"));
    }

    @Override
    public void onClose() {
        if (!saved) {
            ClientEnvironmentEffects.setFogPreset(initData.configData.getString("fogPreset"),
                initData.configData.getFloat("customFogR"), initData.configData.getFloat("customFogG"), initData.configData.getFloat("customFogB"),
                initData.configData.getFloat("customFogNear"), initData.configData.getFloat("customFogFar"),
                initData.configData.getBoolean("fogOutdoorsOnly"));
            ClientEnvironmentEffects.setFogOptions(initData.configData.getCompound("fogOptions"));
            ClientEnvironmentEffects.handleWeatherSync(
                initData.configData.getBoolean("particlesEnabled"),
                initData.configData.getString("particleTypeId"),
                initData.configData.getString("particleDensity"),
                initData.configData.getString("particleMode")
            );
        }
        super.onClose();
    }

    private void sendWaveCmd(String cmd) {
        CompoundTag data = new CompoundTag();
        data.putString("action", "wave_cmd");
        data.putString("cmd", cmd);
        NetworkHandler.INSTANCE.sendToServer(new C2SUpdateConfigPacket(data));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0,0,width,height,currentTab==Tab.FOG?0xA018202B:0xF018202B,currentTab==Tab.FOG?0xA00C1017:0xF00C1017);
        graphics.fill(0,0,width,28,0xFF202D3E);graphics.fill(0,28,width,29,0xFFAF8844);
        graphics.drawString(font,title,12,10,0xF3D18A,false);
        String mapName=workingData.getString("mapId");graphics.drawString(font,font.plainSubstrByWidth(mapName,Math.max(0,width/2-20)),width/2+12,10,0xA9BACF,false);
        graphics.fill(0,height-34,width,height,0xFF121A25);graphics.fill(0,height-35,width,height-34,0xFF34465D);

        graphics.fill(sidebarWidth,30,sidebarWidth+1,height-35,0xFF34465D);
        graphics.drawString(font,Component.translatable(searchQuery.isBlank()?"config.zombierool.tab."+currentTab.name().toLowerCase(Locale.ROOT):"config.zombierool.search_results"),sidebarWidth+16,39,0xF3D18A,false);
        if(listWidget!=null)listWidget.render(graphics,mouseX,mouseY,partialTick);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Set<String> getSet(String key) {
        Set<String> set = new HashSet<>();
        if (workingData.contains(key, 9)) {
            ListTag list = workingData.getList(key, 8);
            for (int i=0; i<list.size(); i++) set.add(list.getString(i));
        }
        return set;
    }

    private void saveSet(String key, Set<String> set) {
        ListTag list = new ListTag();
        for (String s : set) list.add(StringTag.valueOf(s));
        workingData.put(key, list);
    }

    private void addBoolRow(String key, String labelKey, String tooltipKey) {
        boolean val = workingData.getBoolean(key);
        listWidget.addEntry(new BoolEntry(Component.translatable(labelKey), val, newVal -> {
            workingData.putBoolean(key, newVal);
        }, Component.translatable(tooltipKey)));
    }

    private void addStringRow(String key, String labelKey, String tooltipKey) {
        String val = workingData.getString(key);
        listWidget.addEntry(new StringEntry(Component.translatable(labelKey), val, newVal -> workingData.putString(key, newVal), Component.translatable(tooltipKey)));
    }

    private void addIntRow(String key, String labelKey, int min, int max, String tooltipKey) {
        int val = workingData.getInt(key);
        listWidget.addEntry(new NumberEntry(Component.translatable(labelKey), String.valueOf(val), newVal -> {
            try { workingData.putInt(key, Math.max(min, Math.min(max, Integer.parseInt(newVal)))); } catch(Exception ignored){}
        }, Component.translatable(tooltipKey)));
    }

    private void addFloatRow(String key, String labelKey, float min, float max, String tooltipKey) {
        float val = workingData.getFloat(key);
        listWidget.addEntry(new NumberEntry(Component.translatable(labelKey), String.valueOf(val), newVal -> {
            try { workingData.putFloat(key, Math.max(min, Math.min(max, Float.parseFloat(newVal)))); } catch(Exception ignored){}
        }, Component.translatable(tooltipKey)));
    }

    private void addCycleRow(String key, String labelKey, List<String> values, String tooltipKey) {
        String val = workingData.getString(key);
        if(!values.contains(val) && !values.isEmpty()) val = values.get(0);
        listWidget.addEntry(new CycleEntry(Component.translatable(labelKey), val, values, newVal -> {
            workingData.putString(key, newVal);
        }, Component.translatable(tooltipKey)));
    }

    // ==========================================
    // CLASSES INTERNES (WIDGETS)
    // ==========================================

    class ConfigListWidget extends ContainerObjectSelectionList<ConfigEntry> {
        public ConfigListWidget(net.minecraft.client.Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
        }
        public int addEntry(ConfigEntry entry) {
            if(!searchQuery.isBlank()) {
                String text=java.text.Normalizer.normalize(entry.searchText(),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
                String query=java.text.Normalizer.normalize(searchQuery.trim(),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
                if(!java.util.Arrays.stream(query.split("\\s+")).allMatch(text::contains))return -1;
            }
            return super.addEntry(entry);
        }
        public void clearAllEntries() { this.clearEntries(); }
        @Override protected void renderBackground(GuiGraphics graphics) { graphics.fill(this.x0, this.y0, this.x1, this.y1, 0x44000000); }
        @Override public int getRowWidth() { return Math.min(440, this.width - 24); }
        @Override protected int getScrollbarPosition() { return getRowLeft()+getRowWidth()+4; }
    }

    abstract class ConfigEntry extends ContainerObjectSelectionList.Entry<ConfigEntry> {
        String searchText(){
            String text=this instanceof BoolEntry e?e.label.getString()+" "+e.tooltip.getString():this instanceof StringEntry e?e.label.getString()+" "+e.tooltip.getString():this instanceof NumberEntry e?e.label.getString()+" "+e.tooltip.getString():this instanceof CycleEntry e?e.label.getString()+" "+e.tooltip.getString():this instanceof TitleEntry e?e.title:this instanceof CategoryHeaderEntry e?e.title:"";
            if(this instanceof RemovableStringEntry e)text+=" "+e.text;
            if(this instanceof TagGridRowEntry e)for(var tag:e.tags)text+=" "+tag.label;
            if(this instanceof GridRowEntry e)for(var item:e.items){text+=" "+item.stack.getHoverName().getString();if(item.tooltip!=null)for(var line:item.tooltip)text+=" "+line.getString();}
            for(var child:children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget)text+=" "+widget.getMessage().getString();
            return text;
        }
    }

    class ActionEntry extends ConfigEntry {
        final Button button;
        ActionEntry(Component label,Runnable action,boolean active){button=Button.builder(label,b->action.run()).bounds(0,0,220,20).build();button.active=active;}
        @Override public void render(GuiGraphics g,int index,int top,int left,int w,int h,int mx,int my,boolean hovered,float partial){button.setX(left+8);button.setY(top+2);button.setWidth(w-16);button.render(g,mx,my,partial);}
        @Override public List<? extends GuiEventListener> children(){return List.of(button);}
        @Override public List<? extends NarratableEntry> narratables(){return List.of(button);}
    }

    class TitleEntry extends ConfigEntry {
        String title;
        public TitleEntry(String title) { this.title = title; }
        @Override public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            g.drawCenteredString(minecraft.font, title, left + width/2, top + 5, 0xFFAA00);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        @Override public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
    }

    class CategoryHeaderEntry extends ConfigEntry {
        String title;
        boolean expanded;
        Button btn;
        public CategoryHeaderEntry(String title, boolean expanded, Runnable onToggle) {
            this.title = title;
            this.expanded = expanded;
            this.btn = Button.builder(Component.literal((expanded ? "\u25BC " : "\u25B6 ") + title), b -> onToggle.run()).bounds(0, 0, 200, 20).build();
        }
        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            int w = Math.min(width, Math.max(200, minecraft.font.width(title) + 30));
            btn.setWidth(w);
            btn.setX(left + width / 2 - w / 2);
            btn.setY(top);
            btn.render(g, mouseX, mouseY, pt);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(btn); }
        @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(btn); }
    }

    class CustomStringAddEntry extends ConfigEntry {
        EditBox box; Button btn;
        public CustomStringAddEntry(String listKey, String hint) {
            box = new EditBox(minecraft.font, 0, 0, 200, 20, Component.empty());
            box.setMaxLength(256);
            box.setSuggestion(hint);
            box.setResponder(val -> {
                if (!val.isEmpty()) box.setSuggestion("");
                else box.setSuggestion(hint);
            });
            btn = Button.builder(Component.literal("Add"), b -> {
                String val = box.getValue().trim();
                if (!val.isEmpty()) {
                    Set<String> s = getSet(listKey);
                    s.add(val);
                    saveSet(listKey, s);
                    box.setValue("");
                    refreshList();
                }
            }).bounds(0, 0, 40, 20).build();
        }
        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            int currentLeft = left + (width - 250) / 2;
            if (currentLeft < left) currentLeft = left;
            box.setX(currentLeft + 5); box.setY(top + 2); box.render(g, mouseX, mouseY, pt);
            btn.setX(currentLeft + 210); btn.setY(top + 2); btn.render(g, mouseX, mouseY, pt);
        }
        @Override public List<? extends GuiEventListener> children() { return Arrays.asList(box, btn); }
        @Override public List<? extends NarratableEntry> narratables() { return Arrays.asList(box, btn); }
    }

    class RemovableStringEntry extends ConfigEntry {
        String text; Button btn;
        public RemovableStringEntry(String text, Runnable onRemove) {
            this.text = text;
            this.btn = Button.builder(Component.translatable("gui.zombierool.config.remove"), b -> onRemove.run()).bounds(0, 0, 50, 20).build();
        }
        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            g.drawString(minecraft.font, text, left + 10, top + 6, 0xFFFFFF);
            btn.setX(left + width - 60); btn.setY(top + 2); btn.render(g, mouseX, mouseY, pt);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(btn); }
        @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(btn); }
    }

    class TagItem {
        String label; boolean selected; Runnable onToggle;
        TagItem(String label, boolean selected, Runnable onToggle) {
            this.label = label; this.selected = selected; this.onToggle = onToggle;
        }
    }

    class TagGridRowEntry extends ConfigEntry {
        List<TagItem> tags;
        int lastTop = -1; int lastLeft = -1; int lastWidth = -1;
        public TagGridRowEntry(List<TagItem> tags) { this.tags = tags; }
        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            lastTop = top; lastLeft = left; lastWidth = width;
            int spacing = 4;
            int totalSpacing = spacing * (tags.size() - 1);
            int tagWidth = (width - 20 - totalSpacing) / Math.max(1, tags.size());

            int x = left + 10;
            for (int i = 0; i < tags.size(); i++) {
                TagItem tag = tags.get(i);
                int bx = x + i * (tagWidth + spacing);
                int bgColor = tag.selected ? 0xFF008800 : 0xFF444444;
                if (mouseX >= bx && mouseX <= bx + tagWidth && mouseY >= top && mouseY <= top + 20) {
                    bgColor = tag.selected ? 0xFF00AA00 : 0xFF666666;
                }
                g.fill(bx, top, bx + tagWidth, top + 20, bgColor);
                g.renderOutline(bx, top, tagWidth, 20, 0xFFFFFFFF);
                String display = tag.label;
                if (minecraft.font.width(display) > tagWidth - 4) {
                    while (display.length() > 0 && minecraft.font.width(display + "...") > tagWidth - 4) {
                        display = display.substring(0, display.length() - 1);
                    }
                    display += "...";
                }
                g.drawCenteredString(minecraft.font, display, bx + tagWidth / 2, top + 6, 0xFFFFFF);
            }
        }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (lastTop == -1) return false;
            int spacing = 4;
            int totalSpacing = spacing * (tags.size() - 1);
            int tagWidth = (lastWidth - 20 - totalSpacing) / Math.max(1, tags.size());
            int x = lastLeft + 10;

            for (int i = 0; i < tags.size(); i++) {
                int bx = x + i * (tagWidth + spacing);
                if (mouseX >= bx && mouseX <= bx + tagWidth && mouseY >= lastTop && mouseY <= lastTop + 20) {
                    tags.get(i).onToggle.run();
                    return true;
                }
            }
            return false;
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        @Override public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
    }

    class CustomItemAddEntry extends ConfigEntry {
        EditBox box; CycleButton<Boolean> wonderBtn; Button btn;
        boolean isWonder = false; List<String> itemRegistryCache; String currentSuggestion = "";
        public CustomItemAddEntry() {
            itemRegistryCache = ForgeRegistries.ITEMS.getKeys().stream().map(ResourceLocation::toString).collect(Collectors.toList());
            box = new EditBox(minecraft.font, 0, 0, 145, 20, Component.empty()) {
                @Override
                public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                    if (keyCode == GLFW.GLFW_KEY_TAB && currentSuggestion != null && !currentSuggestion.isEmpty()) {
                        this.insertText(currentSuggestion);
                        currentSuggestion = "";
                        this.setSuggestion("");
                        return true;
                    }
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
            };
            box.setMaxLength(256);
            box.setSuggestion("minecraft:diamond_sword");
            box.setResponder(val -> {
                if (val.isEmpty()) { currentSuggestion = "minecraft:diamond_sword"; box.setSuggestion(currentSuggestion); }
                else {
                    String match = itemRegistryCache.stream().filter(s -> s.startsWith(val)).findFirst().orElse("");
                    if (!match.isEmpty() && match.length() >= val.length()) { currentSuggestion = match.substring(val.length()); box.setSuggestion(currentSuggestion); }
                    else { currentSuggestion = ""; box.setSuggestion(""); }
                }
            });
            wonderBtn = CycleButton.builder((Boolean b) -> Component.literal(b ? "W: ON" : "W: OFF")).withValues(false, true).withInitialValue(false).create(0, 0, 50, 20, Component.empty(), (b, val) -> isWonder = val);
            btn = Button.builder(Component.literal("Add"), b -> {
                String val = box.getValue();
                ResourceLocation loc = ResourceLocation.tryParse(val);
                if (loc != null && ForgeRegistries.ITEMS.containsKey(loc)) {
                    Set<String> s = getSet("customBoxWeapons"); s.add(loc.toString()); saveSet("customBoxWeapons", s);
                    Set<String> w = getSet("customWonderWeapons"); if (isWonder) w.add(loc.toString()); else w.remove(loc.toString()); saveSet("customWonderWeapons", w);
                    box.setValue(""); currentSuggestion = "minecraft:diamond_sword"; box.setSuggestion(currentSuggestion);
                    refreshList();
                }
            }).bounds(0, 0, 40, 20).build();
        }
        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            int currentLeft = left + (width - 300) / 2;
            if (currentLeft < left) currentLeft = left;
            g.drawString(minecraft.font, Component.translatable("gui.zombierool.config.add_item"), currentLeft + 5, top + 6, 0xFFFFFF);
            box.setX(currentLeft + 55); box.setY(top + 2); box.render(g, mouseX, mouseY, pt);
            wonderBtn.setX(currentLeft + 205); wonderBtn.setY(top + 2); wonderBtn.render(g, mouseX, mouseY, pt);
            btn.setX(currentLeft + 260); btn.setY(top + 2); btn.render(g, mouseX, mouseY, pt);
        }
        @Override public List<? extends GuiEventListener> children() { return Arrays.asList(box, wonderBtn, btn); }
        @Override public List<? extends NarratableEntry> narratables() { return Arrays.asList(box, wonderBtn, btn); }
    }

    class BoolEntry extends ConfigEntry {
        Component label; Component tooltip; CycleButton<Boolean> btn;
        public BoolEntry(Component label, boolean initial, java.util.function.Consumer<Boolean> onChange, Component tooltip) {
            this.label = label; this.tooltip = tooltip;
            this.btn = CycleButton.onOffBuilder(initial).displayOnlyValue().create(0, 0, 100, 20, Component.empty(), (b, val) -> onChange.accept(val));
        }
        @Override public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            g.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(label.getString(),Math.max(20,width-130)), left + 10, top + 6, 0xFFFFFF);
            btn.setX(left + width - 110); btn.setY(top + 2); btn.render(g, mouseX, mouseY, pt);
            if (mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= top + height) g.renderTooltip(minecraft.font, tooltip, mouseX, mouseY);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(btn); }
        @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(btn); }
    }

    class StringEntry extends ConfigEntry {
        Component label; Component tooltip; EditBox box;
        public StringEntry(Component label, String initial, java.util.function.Consumer<String> onChange, Component tooltip) {
            this.label = label; this.tooltip = tooltip;
            this.box = new EditBox(minecraft.font, 0, 0, 100, 20, Component.empty());
            this.box.setValue(initial); this.box.setMaxLength(1024); this.box.setResponder(onChange);
        }
        @Override public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            g.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(label.getString(),Math.max(20,width-130)), left + 10, top + 6, 0xFFFFFF);
            box.setX(left + width - 110); box.setY(top + 2); box.render(g, mouseX, mouseY, pt);
            if (mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= top + height) g.renderTooltip(minecraft.font, tooltip, mouseX, mouseY);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(box); }
        @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(box); }
    }

    class CycleEntry extends ConfigEntry {
        Component label; Component tooltip; CycleButton<String> btn;
        public CycleEntry(Component label, String initial, List<String> values, java.util.function.Consumer<String> onChange, Component tooltip) {
            this.label = label; this.tooltip = tooltip;
            this.btn = CycleButton.builder((String s) -> {
                if(net.minecraft.locale.Language.getInstance().has("config.zombierool.value."+s))return Component.translatable("config.zombierool.value."+s);
                String clean = s.contains(":") ? s.substring(s.indexOf(":")+1) : s;
                if (clean.length() > 14) clean = clean.substring(0, 14) + "..";
                return Component.literal(clean);
            }).displayOnlyValue().withValues(values).withInitialValue(initial).create(0, 0, 100, 20, Component.empty(), (b, val) -> onChange.accept(val));
        }
        @Override public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            g.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(label.getString(),Math.max(20,width-130)), left + 10, top + 6, 0xFFFFFF);
            btn.setX(left + width - 110); btn.setY(top + 2); btn.render(g, mouseX, mouseY, pt);
            if (mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= top + height) g.renderTooltip(minecraft.font, tooltip, mouseX, mouseY);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(btn); }
        @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(btn); }
    }

    class NumberEntry extends ConfigEntry {
        Component label; Component tooltip; EditBox box;
        public NumberEntry(Component label, String initial, java.util.function.Consumer<String> onChange, Component tooltip) {
            this.label = label; this.tooltip = tooltip;
            this.box = new EditBox(minecraft.font, 0, 0, 100, 20, Component.empty());
            this.box.setValue(initial); this.box.setResponder(onChange);
        }
        @Override public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            g.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(label.getString(),Math.max(20,width-130)), left + 10, top + 6, 0xFFFFFF);
            box.setX(left + width - 110); box.setY(top + 2); box.render(g, mouseX, mouseY, pt);
            if (mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= top + height) g.renderTooltip(minecraft.font, tooltip, mouseX, mouseY);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(box); }
        @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(box); }
    }

    class GridItem {
        ItemStack stack; ResourceLocation texture; ItemState state;
        Runnable onToggle; Runnable onRightClick; List<Component> tooltip;
        GridItem(ItemStack stack, ResourceLocation texture, ItemState state, Runnable onToggle, Runnable onRightClick, List<Component> tooltip) {
            this.stack = stack; this.texture = texture; this.state = state;
            this.onToggle = onToggle; this.onRightClick = onRightClick; this.tooltip = tooltip;
        }
    }

    class GridColumnControlsEntry extends ConfigEntry {
        List<GridItem> allItems;
        int itemSize = 18; int spacing = 4;
        private int lastTop = -1; private int lastLeft = -1; private int lastWidth = -1; private int columns = 12;

        public GridColumnControlsEntry(List<GridItem> allItems) {
            this.allItems = allItems;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            this.lastTop = top;
            this.lastLeft = left;
            this.lastWidth = width;
            this.columns = Math.min(12, width / (itemSize + spacing));
            int startX = left + (width - (columns * (itemSize + spacing))) / 2;

            for (int i = 0; i < columns; i++) {
                int ix = startX + i * (itemSize + spacing);
                boolean hover = mouseX >= ix && mouseX < ix + itemSize && mouseY >= top && mouseY < top + 10;
                int color = hover ? 0xFFFFFF00 : 0xFFAAAAAA;
                g.fill(ix, top, ix + itemSize, top + 10, 0xFF333333);
                g.drawCenteredString(minecraft.font, "C", ix + itemSize / 2, top + 1, color);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (lastTop == -1) return false;
            int startX = lastLeft + (lastWidth - (columns * (itemSize + spacing))) / 2;
            for (int i = 0; i < columns; i++) {
                int ix = startX + i * (itemSize + spacing);
                if (mouseX >= ix && mouseX < ix + itemSize && mouseY >= lastTop && mouseY < lastTop + 10) {
                    if (button == 0) {
                        for (int j = i; j < allItems.size(); j += columns) {
                            if (allItems.get(j).onToggle != null) {
                                allItems.get(j).onToggle.run();
                            }
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        @Override public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        @Override public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
    }

    class GridRowEntry extends ConfigEntry {
        List<GridItem> items;
        int itemSize = 18; int spacing = 4;
        private int lastTop = -1; private int lastLeft = -1; private int lastWidth = -1; private int columns = 12;

        public GridRowEntry(List<GridItem> items) { this.items = items; }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            this.lastTop = top;
            this.lastLeft = left;
            this.lastWidth = width;
            this.columns = Math.min(12, width / (itemSize + spacing));
            int startX = left + (width - (columns * (itemSize + spacing))) / 2;

            int rowBtnX = startX - 16;
            boolean rowHover = mouseX >= rowBtnX && mouseX < rowBtnX + 12 && mouseY >= top + 3 && mouseY < top + 15;
            g.fill(rowBtnX, top + 3, rowBtnX + 12, top + 15, rowHover ? 0xFFFFFF00 : 0xFFAAAAAA);
            g.drawCenteredString(minecraft.font, "R", rowBtnX + 6, top + 5, 0xFF000000);

            for (int i = 0; i < items.size(); i++) {
                GridItem item = items.get(i);
                int ix = startX + i * (itemSize + spacing);

                g.fill(ix, top, ix + itemSize, top + itemSize, 0xFF373737);
                g.fill(ix + 1, top + 1, ix + itemSize, top + itemSize, 0xFFFFFFFF);
                g.fill(ix + 1, top + 1, ix + itemSize - 1, top + itemSize - 1, 0xFF8B8B8B);

                if (item.texture != null) {
                    RenderSystem.enableBlend();
                    g.blit(item.texture, ix + 1, top + 1, 0, 0, 16, 16, 16, 16);
                    RenderSystem.disableBlend();
                } else if (!item.stack.isEmpty()) {
                    g.renderItem(item.stack, ix + 1, top + 1);
                }

                if (item.state == ItemState.MANUALLY_DISABLED) {
                    g.fill(ix, top, ix + itemSize, top + itemSize, 0x80FF0000);
                } else if (item.state == ItemState.TAG_DISABLED) {
                    g.fill(ix, top, ix + itemSize, top + itemSize, 0xAA000000);
                }

                if (mouseX >= ix && mouseX < ix + itemSize && mouseY >= top && mouseY < top + itemSize) {
                    g.renderComponentTooltip(minecraft.font, item.tooltip, mouseX, mouseY);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (lastTop == -1) return false;
            int startX = lastLeft + (lastWidth - (columns * (itemSize + spacing))) / 2;

            int rowBtnX = startX - 16;
            if (mouseX >= rowBtnX && mouseX < rowBtnX + 12 && mouseY >= lastTop + 3 && mouseY < lastTop + 15) {
                if (button == 0) {
                    for (GridItem item : items) {
                        if (item.onToggle != null) item.onToggle.run();
                    }
                    return true;
                }
            }

            for (int i = 0; i < items.size(); i++) {
                int ix = startX + i * (itemSize + spacing);
                if (mouseX >= ix && mouseX < ix + itemSize && mouseY >= lastTop && mouseY < lastTop + itemSize) {
                    if (button == 0 && items.get(i).onToggle != null) items.get(i).onToggle.run();
                    else if (button == 1 && items.get(i).onRightClick != null) items.get(i).onRightClick.run();
                    return true;
                }
            }
            return false;
        }

        @Override public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        @Override public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
    }

}
