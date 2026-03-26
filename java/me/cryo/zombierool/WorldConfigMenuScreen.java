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
import me.cryo.zombierool.PerksManager;
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
    private EditBox waveBox;

    private FogPresetList fogList;
    private FogSlider sliderR, sliderG, sliderB, sliderNear, sliderFar;
    private String localFogPreset;
    private EditBox customFogNameBox;

    private ParticlePresetList particleList;
    private String localParticlePreset;

    private boolean saved = false;

    private final List<String> BUILT_IN_FOGS = Arrays.asList("normal", "dense", "clear", "dark", "blood", "nightmare", "green_acid", "none", "sunrise", "sunset", "underwater", "swamp", "volcanic", "mystic", "toxic", "dreamy", "winter", "haunted", "arctic", "prehistoric", "radioactive", "desert", "ashstorm", "eldritch", "space", "corrupted", "celestial", "storm", "abyssal", "netherburn", "elderswamp", "nebula", "wasteland", "void", "festival", "temple", "stormy_night", "obsidian");

    private enum Tab { GENERAL, MOBS, WAVES, WEAPONS, EXTRAS, FOG, WEATHER, WHITELIST, SYSTEM }
    enum ItemState { ENABLED, MANUALLY_DISABLED, TAG_DISABLED }

    private final Set<String> expandedCategories = new HashSet<>(Arrays.asList("TacZ", "Normal", "Wonder"));

    public WorldConfigMenuScreen(S2COpenConfigMenuPacket data) {
        super(Component.translatable("gui.zombierool.config.title"));
        this.initData = data;
        this.workingData = data.configData.copy();
        
        this.localFogPreset = workingData.getString("fogPreset");
        if (this.localFogPreset.isEmpty()) this.localFogPreset = "normal";

        this.localParticlePreset = workingData.getString("particleTypeId");
        if (this.localParticlePreset.isEmpty()) this.localParticlePreset = "none";
        
        CustomFogManager.load();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int tabsPerRow = (this.width > 600) ? 9 : 5;
        int btnWidth = Math.min(65, (this.width - 20) / tabsPerRow);
        int startX = (this.width - (btnWidth * tabsPerRow)) / 2;
        int startY = 5;

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int row = i / tabsPerRow;
            int col = i % tabsPerRow;
            int xPos = startX + (btnWidth * col);
            int yPos = startY + (row * 22);

            String tabLabel = Component.translatable("config.zombierool.tab." + t.name().toLowerCase(Locale.ROOT)).getString();
            Button b = Button.builder(Component.literal(tabLabel), btn -> {
                currentTab = t; this.rebuildWidgets();
            }).bounds(xPos, yPos, btnWidth - 2, 20).build();
            b.active = currentTab != t;
            this.addRenderableWidget(b);
        }

        int listTopMargin = startY + ((tabs.length / tabsPerRow) + 1) * 22 + 5;

        if (currentTab == Tab.SYSTEM) {
            initGameControl(listTopMargin);
        } else if (currentTab == Tab.FOG) {
            initFog(listTopMargin);
        } else if (currentTab == Tab.WEATHER) {
            initWeather(listTopMargin);
        } else {
            this.listWidget = new ConfigListWidget(this.minecraft, this.width, this.height, listTopMargin, this.height - 35, 25);
            this.addWidget(this.listWidget);
            buildListContent();
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.config.save"), btn -> {
            saved = true;
            CompoundTag toSend = workingData.copy();
            toSend.putString("action", "save_all");
            NetworkHandler.INSTANCE.sendToServer(new C2SUpdateConfigPacket(toSend));
            this.onClose();
        }).bounds(this.width / 2 - 105, this.height - 28, 100, 20).build());

        if (currentTab != Tab.SYSTEM) {
            String resetTarget = currentTab.name();
            if (currentTab == Tab.WEAPONS || currentTab == Tab.EXTRAS) resetTarget = "LOOT";
            final String finalResetTarget = resetTarget;
            String resetLabel = Component.translatable("gui.zombierool.config.reset", finalResetTarget).getString();
            this.addRenderableWidget(Button.builder(Component.literal(resetLabel), btn -> {
                CompoundTag toSend = new CompoundTag();
                toSend.putString("action", "reset_" + finalResetTarget.toLowerCase(Locale.ROOT));
                NetworkHandler.INSTANCE.sendToServer(new C2SUpdateConfigPacket(toSend));
                this.onClose();
            }).bounds(this.width / 2 + 5, this.height - 28, 100, 20).build());
        }
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
        switch (currentTab) {
            case GENERAL -> {
                String mapIdRaw = workingData.getString("mapId");
                String mapIdDisplay = mapIdRaw.startsWith("zr_") ? mapIdRaw.substring(3) : mapIdRaw;
                listWidget.addEntry(new StringEntry(Component.translatable("config.zombierool.mapId"), mapIdDisplay, newVal -> {
                    workingData.putString("mapId", "zr_" + newVal);
                }, Component.translatable("config.zombierool.mapId.tooltip")));

                addStringRow("resourcePackUrl", "config.zombierool.resourcePackUrl", "config.zombierool.resourcePackUrl.tooltip");
                addStringRow("resourcePackName", "config.zombierool.resourcePackName", "config.zombierool.resourcePackName.tooltip");
                
                addBoolRow("spookyAmbience", "config.zombierool.spookyAmbience", "config.zombierool.spookyAmbience.tooltip");
                addBoolRow("forceHalloween", "config.zombierool.forceHalloween", "config.zombierool.forceHalloween.tooltip");

                addCycleRow("dayNightMode", "config.zombierool.dayNightMode", Arrays.asList("day", "night", "cycle"), "config.zombierool.dayNightMode.tooltip");
                addCycleRow("musicPreset", "config.zombierool.musicPreset", Arrays.asList("default", "damned", "none"), "config.zombierool.musicPreset.tooltip");
                addCycleRow("eyeColorPreset", "config.zombierool.eyeColorPreset", Arrays.asList("red", "blue", "green", "default"), "config.zombierool.eyeColorPreset.tooltip");
                addCycleRow("voicePreset", "config.zombierool.voicePreset", Arrays.asList("uk", "us", "ru", "fr", "ger", "none"), "config.zombierool.voicePreset.tooltip");
                addCycleRow("deathPenalty", "config.zombierool.deathPenalty", Arrays.asList("respawn", "spectator", "kick"), "config.zombierool.deathPenalty.tooltip");
                
                addBoolRow("coldWaterEffectEnabled", "config.zombierool.coldWaterEffectEnabled", "config.zombierool.coldWaterEffectEnabled.tooltip");
                addBoolRow("sprintBgSounds", "config.zombierool.sprintBgSounds", "config.zombierool.sprintBgSounds.tooltip");
                
                addStringRow("starterItem", "config.zombierool.starterItem", "config.zombierool.starterItem.tooltip");
                
                // CORRECTION : Uniquement grenade et stielhandgranate ! On ne choisit pas la monkey bomb en arme de départ.
                addCycleRow("startingLethal", "config.zombierool.startingLethal", Arrays.asList("zombierool:grenade", "zombierool:stielhandgranate"), "config.zombierool.startingLethal.tooltip");
            }
            case MOBS -> {
                addFloatRow("zombieBaseHealth", "config.zombierool.zombieBaseHealth", 1f, 100f, "config.zombierool.zombieBaseHealth.tooltip");
                addFloatRow("zombieMaxHealth", "config.zombierool.zombieMaxHealth", 100f, 5000f, "config.zombierool.zombieMaxHealth.tooltip");
                addFloatRow("crawlerBaseHealth", "config.zombierool.crawlerBaseHealth", 1f, 100f, "config.zombierool.crawlerBaseHealth.tooltip");
                addFloatRow("crawlerMaxHealth", "config.zombierool.crawlerMaxHealth", 100f, 5000f, "config.zombierool.crawlerMaxHealth.tooltip");
                addFloatRow("hellhoundBaseHealth", "config.zombierool.hellhoundBaseHealth", 1f, 100f, "config.zombierool.hellhoundBaseHealth.tooltip");
                addFloatRow("hellhoundMaxHealth", "config.zombierool.hellhoundMaxHealth", 100f, 5000f, "config.zombierool.hellhoundMaxHealth.tooltip");

                addBoolRow("zombiesCanSprint", "config.zombierool.zombiesCanSprint", "config.zombierool.zombiesCanSprint.tooltip");
                addIntRow("zombieSprintWave", "config.zombierool.zombieSprintWave", 1, 100, "config.zombierool.zombieSprintWave.tooltip");
                addFloatRow("zombieSprintChance", "config.zombierool.zombieSprintChance", 0.0f, 1.0f, "config.zombierool.zombieSprintChance.tooltip");
                addFloatRow("zombieSprintSpeed", "config.zombierool.zombieSprintSpeed", 0.1f, 1.0f, "config.zombierool.zombieSprintSpeed.tooltip");

                addBoolRow("superSprintersEnabled", "config.zombierool.superSprintersEnabled", "config.zombierool.superSprintersEnabled.tooltip");
                addIntRow("superSprinterActivationWave", "config.zombierool.superSprinterActivationWave", 1, 100, "config.zombierool.superSprinterActivationWave.tooltip");
                addFloatRow("superSprinterChance", "config.zombierool.superSprinterChance", 0f, 1f, "config.zombierool.superSprinterChance.tooltip");
                addFloatRow("superSprinterSpeed", "config.zombierool.superSprinterSpeed", 0.1f, 1.5f, "config.zombierool.superSprinterSpeed.tooltip");

                addBoolRow("hellhoundFireVariant", "config.zombierool.hellhoundFireVariant", "config.zombierool.hellhoundFireVariant.tooltip");
                addBoolRow("crawlerGasExplosion", "config.zombierool.crawlerGasExplosion", "config.zombierool.crawlerGasExplosion.tooltip");
            }
            case WAVES -> {
                addIntRow("baseZombies", "config.zombierool.baseZombies", 1, 100, "config.zombierool.baseZombies.tooltip");
                addIntRow("maxActiveMobsPerPlayer", "config.zombierool.maxActiveMobsPerPlayer", 1, 100, "config.zombierool.maxActiveMobsPerPlayer.tooltip");
                
                addBoolRow("specialWavesEnabled", "config.zombierool.specialWavesEnabled", "config.zombierool.specialWavesEnabled.tooltip");
                addIntRow("specialWaveStart", "config.zombierool.specialWaveStart", 1, 100, "config.zombierool.specialWaveStart.tooltip");
                addIntRow("specialWaveInterval", "config.zombierool.specialWaveInterval", 1, 100, "config.zombierool.specialWaveInterval.tooltip");
                
                addBoolRow("hellhoundsInNormalWaves", "config.zombierool.hellhoundsInNormalWaves", "config.zombierool.hellhoundsInNormalWaves.tooltip");
                addIntRow("hellhoundsInNormalWavesStart", "config.zombierool.hellhoundsInNormalWavesStart", 1, 100, "config.zombierool.hellhoundsInNormalWavesStart.tooltip");
                
                addCycleRow("spawnIntensity", "config.zombierool.spawnIntensity", Arrays.asList("normal", "flood", "chaos"), "config.zombierool.spawnIntensity.tooltip");
            }
            case WEAPONS -> {
                boolean tagsExpanded = expandedCategories.contains("Tags");
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

                boolean customExpanded = expandedCategories.contains("Custom");
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
                    ItemStack displayStack = WeaponFacade.createWeaponStack(def.id, false, this.minecraft.player);
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

                // Ajout manuel des explosifs au pool d'armes normales pour la box (y compris les nouveaux)
                List<Item> throwables = Arrays.asList(
                    me.cryo.zombierool.core.registry.ZRThrowableRegistry.GRENADE_ITEM.get(), 
                    me.cryo.zombierool.core.registry.ZRThrowableRegistry.MOLOTOV_ITEM.get(),
                    me.cryo.zombierool.core.registry.ZRThrowableRegistry.STIELHANDGRANATE_ITEM.get(),
                    me.cryo.zombierool.core.registry.ZRThrowableRegistry.MONKEY_BOMB_ITEM.get()
                );

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

                boolean wonderExpanded = expandedCategories.contains("Wonder");
                listWidget.addEntry(new CategoryHeaderEntry(Component.translatable("gui.zombierool.config.wonder_weapons").getString(), wonderExpanded, () -> toggleCategory("Wonder")));
                if (wonderExpanded) addGridRowsWithColumnControls(wonderIcons);

                boolean normalExpanded = expandedCategories.contains("Normal");
                listWidget.addEntry(new CategoryHeaderEntry(Component.translatable("gui.zombierool.config.normal_weapons").getString(), normalExpanded, () -> toggleCategory("Normal")));
                if (normalExpanded) addGridRowsWithColumnControls(normalIcons);

                List<ResourceLocation> unmappedGuns = WeaponFacade.getUnmappedTaczGuns();
                if (!unmappedGuns.isEmpty()) {
                    boolean taczExpanded = expandedCategories.contains("TacZ");
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
                addBoolRow("allowDownMovement", "config.zombierool.allowDownMovement", "config.zombierool.allowDownMovement.tooltip");

                listWidget.addEntry(new TitleEntry("--- " + Component.translatable("gui.zombierool.config.perks").getString() + " ---"));
                List<GridItem> perkIcons = new ArrayList<>();
                Set<String> disPerks = getSet("disabledRandomPerks");
                for (String perkId : PerksManager.ALL_PERKS.keySet()) {
                    PerksManager.Perk p = PerksManager.ALL_PERKS.get(perkId);
                    ResourceLocation tex = new ResourceLocation(p.getIconPath());
                    ItemState state = !disPerks.contains(perkId) ? ItemState.ENABLED : ItemState.MANUALLY_DISABLED;

                    List<Component> tp = new ArrayList<>();
                    tp.add(Component.literal("Toggle Perk: " + p.getName()));
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
                    tp.add(Component.literal("Toggle Power-Up: " + bonusId));
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
            case WHITELIST -> {
                boolean mobsExpanded = expandedCategories.contains("WhiteMobs");
                listWidget.addEntry(new CategoryHeaderEntry(Component.translatable("gui.zombierool.config.allowed_mobs").getString(), mobsExpanded, () -> toggleCategory("WhiteMobs")));
                if (mobsExpanded) {
                    listWidget.addEntry(new CustomStringAddEntry("allowedMobs", "Add Mob ID (e.g. minecraft:pig)"));
                    Set<String> set = getSet("allowedMobs");
                    for (String s : set) {
                        listWidget.addEntry(new RemovableStringEntry(s, () -> {
                            Set<String> ns = getSet("allowedMobs");
                            ns.remove(s);
                            saveSet("allowedMobs", ns);
                            refreshList();
                        }));
                    }
                }

                boolean itemsExpanded = expandedCategories.contains("WhiteItems");
                listWidget.addEntry(new CategoryHeaderEntry(Component.translatable("gui.zombierool.config.allowed_items").getString(), itemsExpanded, () -> toggleCategory("WhiteItems")));
                if (itemsExpanded) {
                    listWidget.addEntry(new CustomStringAddEntry("allowedItems", "Add Item ID (e.g. minecraft:apple)"));
                    Set<String> set = getSet("allowedItems");
                    for (String s : set) {
                        listWidget.addEntry(new RemovableStringEntry(s, () -> {
                            Set<String> ns = getSet("allowedItems");
                            ns.remove(s);
                            saveSet("allowedItems", ns);
                            refreshList();
                        }));
                    }
                }
            }
            default -> {}
        }
    }

    private void addGridRowsWithColumnControls(List<GridItem> items) {
        if (items.isEmpty()) return;
        int itemsPerRow = 12;
        listWidget.addEntry(new GridColumnControlsEntry(items));
        for (int i = 0; i < items.size(); i += itemsPerRow) {
            int end = Math.min(i + itemsPerRow, items.size());
            listWidget.addEntry(new GridRowEntry(items.subList(i, end)));
        }
    }

    private void initFog(int startY) {
        int btnWidth = 150;
        int leftX = (this.width / 2) - btnWidth - 10;
        int rightX = (this.width / 2) + 10;
        int yOffset = 24;

        List<String> fogs = new ArrayList<>(BUILT_IN_FOGS);
        fogs.addAll(CustomFogManager.getCustomPresets().keySet());
        
        fogList = new FogPresetList(this.minecraft, btnWidth, this.height, startY, this.height - 35, 20, leftX, fogs);
        this.addWidget(fogList);

        sliderR = new FogSlider(rightX, startY, btnWidth, 20, "Red", 0, 1, workingData.getFloat("customFogR"));
        startY += yOffset;
        sliderG = new FogSlider(rightX, startY, btnWidth, 20, "Green", 0, 1, workingData.getFloat("customFogG"));
        startY += yOffset;
        sliderB = new FogSlider(rightX, startY, btnWidth, 20, "Blue", 0, 1, workingData.getFloat("customFogB"));
        startY += yOffset;
        sliderNear = new FogSlider(rightX, startY, btnWidth, 20, "Near Dist", -10, 100, workingData.getFloat("customFogNear"));
        startY += yOffset;
        sliderFar = new FogSlider(rightX, startY, btnWidth, 20, "Far Dist", 1, 500, workingData.getFloat("customFogFar"));

        this.addRenderableWidget(sliderR);
        this.addRenderableWidget(sliderG);
        this.addRenderableWidget(sliderB);
        this.addRenderableWidget(sliderNear);
        this.addRenderableWidget(sliderFar);

        startY += yOffset + 10;
        customFogNameBox = new EditBox(this.font, rightX, startY, btnWidth, 20, Component.literal("Preset Name"));
        if (!BUILT_IN_FOGS.contains(localFogPreset)) {
            customFogNameBox.setValue(localFogPreset);
        }
        this.addRenderableWidget(customFogNameBox);

        startY += yOffset;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.config.save_preset"), btn -> {
            String name = customFogNameBox.getValue().trim();
            if (!name.isEmpty() && !BUILT_IN_FOGS.contains(name)) {
                CustomFogManager.savePreset(name, (float)sliderR.getRealValue(), (float)sliderG.getRealValue(), (float)sliderB.getRealValue(), (float)sliderNear.getRealValue(), (float)sliderFar.getRealValue());
                localFogPreset = name;
                updateWorkingDataFog();
                applyFogLive();
                this.rebuildWidgets();
            }
        }).bounds(rightX, startY, btnWidth / 2 - 2, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.config.delete"), btn -> {
            String name = customFogNameBox.getValue().trim();
            if (CustomFogManager.getCustomPresets().containsKey(name)) {
                CustomFogManager.deletePreset(name);
                localFogPreset = "normal";
                updateWorkingDataFog();
                applyFogLive();
                this.rebuildWidgets();
            }
        }).bounds(rightX + btnWidth / 2 + 2, startY, btnWidth / 2 - 2, 20).build());
    }

    private void initWeather(int startY) {
        int btnWidth = 150;
        int leftX = (this.width / 2) - btnWidth - 10;
        int rightX = (this.width / 2) + 10;
        int yOffset = 24;

        List<String> allParticles = ForgeRegistries.PARTICLE_TYPES.getKeys().stream()
                .map(ResourceLocation::toString).sorted().collect(Collectors.toList());
        if (allParticles.isEmpty()) allParticles.add("none");

        particleList = new ParticlePresetList(this.minecraft, btnWidth, this.height, startY, this.height - 35, 20, leftX, allParticles);
        this.addWidget(particleList);

        this.addRenderableWidget(CycleButton.onOffBuilder(workingData.getBoolean("particlesEnabled"))
            .create(rightX, startY, btnWidth, 20, Component.translatable("gui.zombierool.config.enable_particles"), (b, val) -> {
                workingData.putBoolean("particlesEnabled", val);
                applyWeatherLive();
            }));

        startY += yOffset;
        this.addRenderableWidget(CycleButton.builder((String s) -> Component.literal(s))
            .withValues(Arrays.asList("sparse", "normal", "dense", "very_dense"))
            .withInitialValue(workingData.getString("particleDensity"))
            .create(rightX, startY, btnWidth, 20, Component.translatable("gui.zombierool.config.density"), (b, val) -> {
                workingData.putString("particleDensity", val);
                applyWeatherLive();
            }));

        startY += yOffset;
        this.addRenderableWidget(CycleButton.builder((String s) -> Component.literal(s))
            .withValues(Arrays.asList("global", "atmospheric"))
            .withInitialValue(workingData.getString("particleMode"))
            .create(rightX, startY, btnWidth, 20, Component.translatable("gui.zombierool.config.mode"), (b, val) -> {
                workingData.putString("particleMode", val);
                applyWeatherLive();
            }));
    }

    private void applyFogLive() {
        ClientEnvironmentEffects.setFogPreset(localFogPreset, 
            workingData.getFloat("customFogR"), workingData.getFloat("customFogG"), workingData.getFloat("customFogB"),
            workingData.getFloat("customFogNear"), workingData.getFloat("customFogFar"));
    }

    private void applyWeatherLive() {
        boolean enabled = workingData.getBoolean("particlesEnabled");
        String pType = workingData.getString("particleTypeId");
        String pDens = workingData.getString("particleDensity");
        String pMode = workingData.getString("particleMode");
        ClientEnvironmentEffects.handleWeatherSync(enabled, pType, pDens, pMode);
    }

    @Override
    public void onClose() {
        if (!saved) {
            ClientEnvironmentEffects.setFogPreset(initData.configData.getString("fogPreset"),
                initData.configData.getFloat("customFogR"), initData.configData.getFloat("customFogG"), initData.configData.getFloat("customFogB"),
                initData.configData.getFloat("customFogNear"), initData.configData.getFloat("customFogFar"));

            ClientEnvironmentEffects.handleWeatherSync(
                initData.configData.getBoolean("particlesEnabled"),
                initData.configData.getString("particleTypeId"),
                initData.configData.getString("particleDensity"),
                initData.configData.getString("particleMode")
            );
        }
        super.onClose();
    }

    private void updateWorkingDataFog() {
        workingData.putString("fogPreset", localFogPreset);
        workingData.putFloat("customFogR", (float)sliderR.getRealValue());
        workingData.putFloat("customFogG", (float)sliderG.getRealValue());
        workingData.putFloat("customFogB", (float)sliderB.getRealValue());
        workingData.putFloat("customFogNear", (float)sliderNear.getRealValue());
        workingData.putFloat("customFogFar", (float)sliderFar.getRealValue());
    }

    private void initGameControl(int startY) {
        int startX = this.width / 2 - 100;
        
        Button startBtn = Button.builder(Component.translatable("gui.zombierool.config.start_game"), btn -> {
            sendWaveCmd("start");
            this.onClose();
        }).bounds(startX, startY, 200, 20).build();
        startBtn.active = !initData.isGameRunning;
        this.addRenderableWidget(startBtn);

        startY += 30;
        Button endBtn = Button.builder(Component.translatable("gui.zombierool.config.end_game"), btn -> {
            sendWaveCmd("end");
            this.onClose();
        }).bounds(startX, startY, 200, 20).build();
        endBtn.active = initData.isGameRunning;
        this.addRenderableWidget(endBtn);

        startY += 50;
        waveBox = new EditBox(this.font, startX, startY, 100, 20, Component.empty());
        waveBox.setValue(String.valueOf(initData.currentWave));
        this.addRenderableWidget(waveBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.config.set_wave"), btn -> {
            sendWaveCmd("setWave " + waveBox.getValue());
            this.onClose();
        }).bounds(startX + 110, startY, 90, 20).build());
    }

    private void sendWaveCmd(String cmd) {
        CompoundTag data = new CompoundTag();
        data.putString("action", "wave_cmd");
        data.putString("cmd", cmd);
        NetworkHandler.INSTANCE.sendToServer(new C2SUpdateConfigPacket(data));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (currentTab == Tab.FOG || currentTab == Tab.WEATHER) {
            graphics.fillGradient(0, 0, this.width, this.height, 0x33000000, 0x55000000);
        } else {
            graphics.fillGradient(0, 0, this.width, this.height, 0x99000000, 0xBB000000);
        }

        if (currentTab == Tab.SYSTEM) {
            Component statusText = initData.isGameRunning ? Component.translatable("gui.zombierool.config.status.running").withStyle(ChatFormatting.GREEN) : Component.translatable("gui.zombierool.config.status.stopped").withStyle(ChatFormatting.RED);
            int tabsPerRow = (this.width > 600) ? 9 : 5;
            int titleY = 5 + ((Tab.values().length / tabsPerRow) + 1) * 22 + 5;
            graphics.drawCenteredString(this.font, Component.translatable("gui.zombierool.config.game_status").append(" ").append(statusText), this.width / 2, titleY - 20, 0xFFFFFF);
        } else if (currentTab == Tab.FOG) {
            if (fogList != null) fogList.render(graphics, mouseX, mouseY, partialTick);
        } else if (currentTab == Tab.WEATHER) {
            if (particleList != null) particleList.render(graphics, mouseX, mouseY, partialTick);
        } else if (listWidget != null) {
            listWidget.render(graphics, mouseX, mouseY, partialTick);
        }
        
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

    class ConfigListWidget extends ContainerObjectSelectionList<ConfigEntry> {
        public ConfigListWidget(net.minecraft.client.Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
        }
        public int addEntry(ConfigEntry entry) { return super.addEntry(entry); }
        public void clearAllEntries() { this.clearEntries(); }
        @Override protected void renderBackground(GuiGraphics graphics) { graphics.fill(this.x0, this.y0, this.x1, this.y1, 0x44000000); }
        @Override public int getRowWidth() { return Math.min(300, this.width - 20); }
        @Override protected int getScrollbarPosition() { return this.width / 2 + getRowWidth() / 2 + 5; }
    }

    abstract class ConfigEntry extends ContainerObjectSelectionList.Entry<ConfigEntry> {}

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
            this.btn = Button.builder(Component.literal((expanded ? "▼ " : "▶ ") + title), b -> onToggle.run()).bounds(0, 0, 200, 20).build();
        }
        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            int w = Math.min(200, width);
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
            this.btn = CycleButton.onOffBuilder(initial).create(0, 0, 100, 20, Component.empty(), (b, val) -> onChange.accept(val));
        }
        @Override public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            g.drawString(minecraft.font, label, left + 10, top + 6, 0xFFFFFF);
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
            g.drawString(minecraft.font, label, left + 10, top + 6, 0xFFFFFF);
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
                String clean = s.contains(":") ? s.substring(s.indexOf(":")+1) : s;
                if (clean.length() > 14) clean = clean.substring(0, 14) + "..";
                return Component.literal(clean);
            }).withValues(values).withInitialValue(initial).create(0, 0, 100, 20, Component.empty(), (b, val) -> onChange.accept(val));
        }
        @Override public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float pt) {
            g.drawString(minecraft.font, label, left + 10, top + 6, 0xFFFFFF);
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
            g.drawString(minecraft.font, label, left + 10, top + 6, 0xFFFFFF);
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

    class FogSlider extends AbstractSliderButton {
        private final String prefix; private final double min, max;
        public FogSlider(int x, int y, int w, int h, String prefix, double min, double max, double current) {
            super(x, y, w, h, Component.empty(), (current - min) / (max - min));
            this.prefix = prefix; this.min = min; this.max = max; updateMessage();
        }
        @Override protected void updateMessage() { this.setMessage(Component.literal(prefix + " " + String.format("%.2f", getRealValue()))); }
        @Override protected void applyValue() {
            if (BUILT_IN_FOGS.contains(localFogPreset)) {
                localFogPreset = "custom"; workingData.putString("fogPreset", "custom");
                if (customFogNameBox != null) customFogNameBox.setValue("custom");
            }
            updateWorkingDataFog(); applyFogLive();
        }
        public double getRealValue() { return min + this.value * (max - min); }
        public void setRealValue(double val) { this.value = (val - min) / (max - min); updateMessage(); }
    }

    class FogPresetList extends ContainerObjectSelectionList<FogEntry> {
        private final int internalLeftX;
        public FogPresetList(net.minecraft.client.Minecraft mc, int width, int height, int top, int bottom, int itemHeight, int leftX, List<String> fogs) {
            super(mc, width, height, top, bottom, itemHeight);
            this.internalLeftX = leftX; this.setLeftPos(leftX); this.setRenderBackground(false); this.setRenderTopAndBottom(false);
            for (String fog : fogs) this.addEntry(new FogEntry(fog));
        }
        @Override protected void renderBackground(GuiGraphics graphics) { graphics.fill(this.internalLeftX, this.y0, this.internalLeftX + this.width, this.y1, 0x44000000); }
        @Override public int getRowWidth() { return this.width - 20; }
        @Override protected int getScrollbarPosition() { return this.internalLeftX + this.width - 6; }
    }

    class FogEntry extends ContainerObjectSelectionList.Entry<FogEntry> {
        private final String presetName;
        public FogEntry(String presetName) { this.presetName = presetName; }
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int color = presetName.equals(localFogPreset) ? 0x00FF00 : 0xFFFFFF;
            graphics.drawString(minecraft.font, presetName, left + 5, top + 5, color);
        }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            fogList.setSelected(this); localFogPreset = presetName;
            if (CustomFogManager.getCustomPresets().containsKey(presetName)) {
                CustomFogManager.FogData data = CustomFogManager.getCustomPresets().get(presetName);
                sliderR.setRealValue(data.r); sliderG.setRealValue(data.g); sliderB.setRealValue(data.b);
                sliderNear.setRealValue(data.near); sliderFar.setRealValue(data.far);
                if (customFogNameBox != null) customFogNameBox.setValue(presetName);
            }
            updateWorkingDataFog(); applyFogLive(); return true;
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        @Override public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
    }

    class ParticlePresetList extends ContainerObjectSelectionList<ParticleEntry> {
        private final int internalLeftX;
        public ParticlePresetList(net.minecraft.client.Minecraft mc, int width, int height, int top, int bottom, int itemHeight, int leftX, List<String> particles) {
            super(mc, width, height, top, bottom, itemHeight);
            this.internalLeftX = leftX; this.setLeftPos(leftX); this.setRenderBackground(false); this.setRenderTopAndBottom(false);
            for (String p : particles) this.addEntry(new ParticleEntry(p));
        }
        @Override protected void renderBackground(GuiGraphics graphics) { graphics.fill(this.internalLeftX, this.y0, this.internalLeftX + this.width, this.y1, 0x44000000); }
        @Override public int getRowWidth() { return this.width - 20; }
        @Override protected int getScrollbarPosition() { return this.internalLeftX + this.width - 6; }
    }

    class ParticleEntry extends ContainerObjectSelectionList.Entry<ParticleEntry> {
        private final String particleName;
        public ParticleEntry(String particleName) { this.particleName = particleName; }
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int color = particleName.equals(localParticlePreset) ? 0x00FF00 : 0xFFFFFF;
            String display = particleName.contains(":") ? particleName.substring(particleName.indexOf(":") + 1) : particleName;
            graphics.drawString(minecraft.font, display, left + 5, top + 5, color);
        }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            particleList.setSelected(this); localParticlePreset = particleName; workingData.putString("particleTypeId", particleName); applyWeatherLive(); return true;
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        @Override public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
    }
}