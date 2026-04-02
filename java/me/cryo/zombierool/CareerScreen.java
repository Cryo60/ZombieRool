package me.cryo.zombierool.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cryo.zombierool.client.career.LocalCareerManager;
import me.cryo.zombierool.client.career.RedeemCodeManager;
import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.C2SSyncEquippedCamosPacket;
import me.cryo.zombierool.network.packet.C2SSyncEquippedSkinsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@OnlyIn(Dist.CLIENT)
public class CareerScreen extends Screen {

    private static final int C_BG_PANEL       = 0x8C000000;
    private static final int C_BG_PANEL_DARK  = 0xB8000000;
    private static final int C_BG_PANEL_XDARK = 0xD9000000;
    private static final int C_BG_HOVER       = 0x0FFFFFFF;
    private static final int C_BG_SELECTED    = 0x1A00C8FF;
    private static final int C_BG_PREVIEW     = 0x0A00C8FF;

    private static final int C_BORDER_CYAN_DIM  = 0x2E00C8FF;
    private static final int C_BORDER_CYAN_MED  = 0x5900C8FF;
    private static final int C_BORDER_CYAN_FULL = 0xFF00CCFF;
    private static final int C_BORDER_GRID      = 0x0800C8FF;

    private static final int C_TEXT_WHITE    = 0xFFFFFFFF;
    private static final int C_TEXT_LIGHT    = 0xFFCCCCCC;
    private static final int C_TEXT_MED      = 0xFF888888;
    private static final int C_TEXT_DARK     = 0xFF555555;
    private static final int C_TEXT_HINT     = 0xFF333333;
    private static final int C_TEXT_CYAN     = 0xFF00CCFF;
    private static final int C_TEXT_GOLD     = 0xFFFFCC00;
    private static final int C_TEXT_GREEN    = 0xFF44FF88;
    private static final int C_TEXT_ORANGE   = 0xFFFFAA00;

    private static final int H_TOPBAR     = 34;
    private static final int H_TABS       = 26;
    private static final int H_STATUSBAR  = 18;

    private int leftW;
    private int rightW;
    private int centerX;
    private int centerW;
    private int bodyY;
    private int bodyH;
    private int rightX;
    private int cellSize;
    private int cellSpacing = 4;

    private final Screen parent;

    private Tab       currentTab      = Tab.ARSENAL;
    private ViewMode  currentViewMode = ViewMode.CAMOS;
    private static final List<String> supportedWeaponsCache = new ArrayList<>();
    private String selectedWeapon = "m1911";
    private String selectedCamo   = "";
    private String selectedSkin   = "";

    private String currentRarityFilter = "All";
    private String currentShopSort     = "Price: High-Low";

    private long   lastGridClickTime = 0;
    private String lastClickedItem   = "";

    private WeaponList weaponList;
    private EditBox    wpnSearchBox;
    private EditBox    camoSearchBox;
    private CycleButton<String> rarityFilterButton;
    private CycleButton<String> shopSortButton;
    private Button btnCamoTab;
    private Button btnSkinTab;

    private double scrollOffset  = 0;
    private double maxScroll     = 0;
    private boolean needsLayoutUpdate = true;

    private Button resetButton;
    private int    resetConfirmTimer = 0;

    private Button dailyButton;
    private Button prestigeButton;

    private Button actionButton1;
    private Button actionButton2;
    private Button unequipAllButton;

    private EditBox redeemCodeBox;
    private Button  redeemButton;
    private String  redeemStatusMessage = "";
    private int     redeemStatusColor   = 0xFFFFFF;

    private float weaponPopScale = 1.0f;
    private float flashAlpha     = 0.0f;
    private int   flashColor     = 0xFFFFFF;

    private final List<FloatingText> floatingTexts   = new CopyOnWriteArrayList<>();
    private final List<GridElement>  currentGridLayout = new ArrayList<>();

    private static class FloatingText {
        String text; float x, y; int color;
        int ticksAlive = 0; final int maxTicks = 40;
        FloatingText(String t, float x, float y, int c) { this.text=t; this.x=x; this.y=y; this.color=c; }
    }

    private static class GridElement {
        boolean isHeader;
        boolean isCamo;
        String text, id;
        int x, y, width, height;
    }

    private enum Tab { ARSENAL, CHALLENGES, SHOP, BARRACKS }
    private enum ViewMode { CAMOS, SKINS }

    public CareerScreen(Screen parent) {
        super(Component.translatable("gui.zombierool.career.title"));
        this.parent = parent;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        super.removed();
        LocalCareerManager.forceSave();
    }

    public static List<String> getSupportedWeaponsCache() { return supportedWeaponsCache; }

    public void refreshData() {
        this.needsLayoutUpdate = true;
        this.rebuildWidgets();
    }

    public static void updateClientData(me.cryo.zombierool.career.CareerManager.CareerData data) {
        LocalCareerManager.CareerData localData = LocalCareerManager.getData();
        localData.zrfBalance             = data.zrfBalance;
        localData.challengeProgress      = data.challengeProgress;
        localData.challengeCompleted     = data.challengeCompleted;
        localData.lastChallengeResetTime = data.lastChallengeResetTime;
        
        localData.activeChallenges.clear();
        for (Map.Entry<String, me.cryo.zombierool.career.CareerManager.ChallengeDef> entry : data.activeChallenges.entrySet()) {
            me.cryo.zombierool.career.CareerManager.ChallengeDef def = entry.getValue();
            localData.activeChallenges.put(entry.getKey(),
                new LocalCareerManager.ChallengeDef(
                    LocalCareerManager.ChallengeType.valueOf(def.type.name()),
                    def.target, def.reward));
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CareerScreen cs) cs.refreshData();
    }

    @Override
    protected void init() {
        super.init();
        LocalCareerManager.load();

        scrollOffset        = 0;
        redeemStatusMessage = "";
        needsLayoutUpdate   = true;

        leftW   = clamp((int)(this.width * 0.15f), 60, 130);
        rightW  = clamp((int)(this.width * 0.22f), 110, 200);
        bodyY   = H_TOPBAR + H_TABS;
        bodyH   = this.height - bodyY - H_STATUSBAR;
        centerX = leftW;
        centerW = this.width - leftW - rightW;
        rightX  = this.width - rightW;

        cellSize = (centerW < 160) ? 40 : (centerW < 220) ? 48 : 56;

        if (supportedWeaponsCache.isEmpty()) {
            for (Map.Entry<String, WeaponSystem.Definition> entry : WeaponSystem.Loader.LOADED_DEFINITIONS.entrySet()) {
                WeaponSystem.Definition def = entry.getValue();
                if (!"MELEE".equalsIgnoreCase(def.type) && !"GRENADE".equalsIgnoreCase(def.type))
                    supportedWeaponsCache.add(entry.getKey());
            }
            Collections.sort(supportedWeaponsCache);
            if (supportedWeaponsCache.isEmpty()) supportedWeaponsCache.add("m1911");
        }
        if (!supportedWeaponsCache.contains(selectedWeapon))
            selectedWeapon = supportedWeaponsCache.get(0);

        int tabW    = Math.min(80, (this.width - 60) / 4);
        int tabsTot = tabW * 4;
        int tabSX   = (this.width - tabsTot) / 2;
        int tabY    = H_TOPBAR + 3;

        addTabButton(tabSX,              tabY, tabW, Tab.ARSENAL,    "gui.zombierool.career.tab.arsenal");
        addTabButton(tabSX + tabW,       tabY, tabW, Tab.CHALLENGES, "gui.zombierool.career.tab.challenges");
        addTabButton(tabSX + tabW * 2,   tabY, tabW, Tab.SHOP,       "gui.zombierool.career.tab.shop");
        
        this.addRenderableWidget(
            Button.builder(Component.literal("Barracks"), btn -> switchTab(Tab.BARRACKS))
                  .bounds(tabSX + tabW * 3, tabY, tabW, 20).build()
        ).active = currentTab != Tab.BARRACKS;

        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.back"), btn -> this.minecraft.setScreen(parent))
                  .bounds(6, 7, 44, 18).build()
        );

        this.wpnSearchBox = new EditBox(this.font, 4, bodyY + 16, leftW - 8, 14, Component.literal("Search"));
        
        if (currentTab == Tab.ARSENAL || currentTab == Tab.SHOP) {
            this.weaponList = new WeaponList(this.minecraft, leftW, bodyH - 34, bodyY + 34, this.height - H_STATUSBAR, 18);
            this.weaponList.setLeftPos(0);
            this.wpnSearchBox.setResponder(q -> weaponList.filter(q));
            this.addWidget(this.weaponList);
            this.addRenderableWidget(this.wpnSearchBox);

            int btnWidth = 60;
            int btnHeight = 16;
            this.btnCamoTab = Button.builder(Component.literal("CAMOS"), btn -> switchViewMode(ViewMode.CAMOS)).bounds(centerX + 4, bodyY + 4, btnWidth, btnHeight).build();
            this.btnSkinTab = Button.builder(Component.literal("SKINS"), btn -> switchViewMode(ViewMode.SKINS)).bounds(centerX + 8 + btnWidth, bodyY + 4, btnWidth, btnHeight).build();
            this.btnCamoTab.active = currentViewMode != ViewMode.CAMOS;
            this.btnSkinTab.active = currentViewMode != ViewMode.SKINS;
            this.addRenderableWidget(this.btnCamoTab);
            this.addRenderableWidget(this.btnSkinTab);

            int searchW  = Math.min(130, centerW - 10 - (btnWidth*2) - 8);
            this.camoSearchBox = new EditBox(this.font, centerX + 12 + (btnWidth*2), bodyY + 5, searchW, 14, Component.literal("Search items"));
            this.camoSearchBox.setResponder(q -> needsLayoutUpdate = true);
            this.addRenderableWidget(this.camoSearchBox);

            if (currentTab == Tab.SHOP) {
                int filterY  = bodyY + 24;
                int filterW  = Math.max(50, Math.min(80, centerW / 2 - 4));
                int sortW    = Math.max(70, Math.min(100, centerW / 2 - 4));

                this.rarityFilterButton = CycleButton.builder((String s) -> Component.literal(s))
                    .withValues(Arrays.asList("All","Common","Rare","Epic","Legendary","Mastery"))
                    .withInitialValue(currentRarityFilter)
                    .create(centerX + 4, filterY, filterW, 16, Component.empty(), (btn, val) -> {
                        currentRarityFilter = val; scrollOffset = 0; needsLayoutUpdate = true;
                    });
                this.addRenderableWidget(this.rarityFilterButton);

                this.shopSortButton = CycleButton.builder((String s) -> Component.literal(s))
                    .withValues(Arrays.asList("Price: High-Low","Price: Low-High","Name: A-Z"))
                    .withInitialValue(currentShopSort)
                    .create(centerX + 4 + filterW + 4, filterY, sortW, 16, Component.empty(), (btn, val) -> {
                        currentShopSort = val; scrollOffset = 0; needsLayoutUpdate = true;
                    });
                this.addRenderableWidget(this.shopSortButton);
                
                if (currentViewMode == ViewMode.SKINS) {
                    this.rarityFilterButton.visible = false;
                    this.shopSortButton.visible = false;
                }
            }

            int abY  = this.height - H_STATUSBAR - 22;
            int abW  = rightW - 20;
            int abX  = rightX + 10;

            this.actionButton1 = Button.builder(Component.literal("EQUIP"), btn -> handleAction1())
                                       .bounds(abX, abY, abW, 16).build();
            this.actionButton2 = Button.builder(Component.literal("EQUIP ON ALL"), btn -> handleAction2())
                                       .bounds(abX, abY - 22, abW, 16).build();
            this.unequipAllButton = Button.builder(Component.literal("UNEQUIP ALL"), btn -> doUnequipAll())
                                       .bounds(abX, abY - 44, abW, 16).build();

            this.addRenderableWidget(actionButton1);
            this.addRenderableWidget(actionButton2);
            if (currentTab == Tab.ARSENAL) this.addRenderableWidget(unequipAllButton);
            updateActionButtons();
        }

        if (currentTab == Tab.SHOP) {
            int dailyW = Math.min(80, rightW - 10);
            this.dailyButton = Button.builder(Component.literal("Daily"), btn -> {
                if (LocalCareerManager.claimDailyReward()) {
                    playSound(); triggerFlash(0x00FFFF); weaponPopScale = 1.3f;
                    addFT("Daily Reward Claimed!", dailyButton.getX() + dailyButton.getWidth()/2, dailyButton.getY(), 0x00FFFF);
                    needsLayoutUpdate = true; rebuildWidgets();
                }
            }).bounds(this.width - dailyW - 6, 7, dailyW, 18).build();
            this.dailyButton.active = LocalCareerManager.canClaimDaily();
            this.addRenderableWidget(dailyButton);
        }

        if (currentTab == Tab.BARRACKS) {
            int bCX = this.width / 2;
            int rY  = bodyY + 10;
            int boxW = Math.min(160, (this.width / 2) - 16);

            this.prestigeButton = Button.builder(Component.literal("Enter Prestige"), btn -> {
                if (LocalCareerManager.canPrestige()) {
                    playSound(); LocalCareerManager.doPrestige();
                    triggerFlash(0xFF00FF);
                    addFT("PRESTIGE ENTERED!", this.width/2, this.height/2, 0xFF00FF);
                    needsLayoutUpdate = true; rebuildWidgets();
                }
            }).bounds(bCX + 10 + boxW/2 - 50, rY + 110, 100, 18).build();
            this.prestigeButton.active = LocalCareerManager.canPrestige();
            this.addRenderableWidget(prestigeButton);

            this.resetButton = Button.builder(Component.literal("Reset Data"), btn -> {
                if (resetConfirmTimer == 0) {
                    resetConfirmTimer = 100;
                    btn.setMessage(Component.literal("Confirm!").withStyle(ChatFormatting.RED));
                } else {
                    playSound(); LocalCareerManager.resetAllData();
                    resetConfirmTimer = 0;
                    btn.setMessage(Component.literal("Reset Data"));
                    triggerFlash(0xFF0000);
                    addFT("DATA RESET!", this.width/2, this.height/2, 0xFF0000);
                    needsLayoutUpdate = true; rebuildWidgets();
                }
            }).bounds(8, this.height - H_STATUSBAR - 22, 76, 18).build();
            this.addRenderableWidget(resetButton);

            this.redeemCodeBox = new EditBox(this.font,
                bCX + 10 + boxW/2 - 48, rY + 34, 96, 14,
                Component.translatable("gui.zombierool.career.redeem_code"));
            this.redeemCodeBox.setMaxLength(32);
            this.addRenderableWidget(redeemCodeBox);

            this.redeemButton = Button.builder(
                Component.translatable("gui.zombierool.career.redeem_btn"), btn -> {
                    playSound();
                    String code = redeemCodeBox.getValue();
                    redeemButton.active = false;
                    redeemStatusMessage = "..."; redeemStatusColor = 0xAAAAAA;
                    RedeemCodeManager.redeemCode(code, resultMsg -> {
                        redeemStatusMessage = resultMsg.getString();
                        redeemButton.active = true;
                        if (resultMsg.getString().contains("Invalid") ||
                            resultMsg.getString().contains("already") ||
                            resultMsg.getString().contains("Error")) {
                            redeemStatusColor = 0xFF5555;
                        } else {
                            redeemStatusColor = 0x55FF55;
                            redeemCodeBox.setValue("");
                            triggerFlash(0x55FF55);
                        }
                    });
                }).bounds(bCX + 10 + boxW/2 - 28, rY + 54, 56, 18).build();
            this.addRenderableWidget(redeemButton);
        }
    }

    private void switchViewMode(ViewMode mode) {
        currentViewMode = mode;
        selectedCamo = ""; selectedSkin = "";
        scrollOffset = 0;
        needsLayoutUpdate = true;
        rebuildWidgets();
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private void addTabButton(int x, int y, int w, Tab tab, String langKey) {
        Button btn = this.addRenderableWidget(
            Button.builder(Component.translatable(langKey), b -> switchTab(tab))
                  .bounds(x, y, w, 20).build());
        btn.active = currentTab != tab;
    }

    private void switchTab(Tab tab) {
        currentTab = tab; scrollOffset = 0;
        if (tab == Tab.ARSENAL || tab == Tab.SHOP) { selectedCamo = ""; selectedSkin = ""; }
        needsLayoutUpdate = true; rebuildWidgets();
    }

    private void triggerFlash(int color) { this.flashColor = color; this.flashAlpha = 0.35f; }
    private void addFT(String text, int x, int y, int color) {
        this.floatingTexts.add(new FloatingText(text, x, y, color));
    }
    private void playSound() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
    }

    private void doUnequipAll() {
        playSound();
        if (currentViewMode == ViewMode.CAMOS) {
            LocalCareerManager.getData().equippedCamos.clear();
            LocalCareerManager.forceSave();
            if (Minecraft.getInstance().getConnection() != null)
                NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedCamosPacket(LocalCareerManager.getData().equippedCamos));
        } else {
            LocalCareerManager.getData().equippedSkins.clear();
            LocalCareerManager.forceSave();
            if (Minecraft.getInstance().getConnection() != null)
                NetworkHandler.INSTANCE.sendToServer(new C2SSyncEquippedSkinsPacket(LocalCareerManager.getData().equippedSkins));
        }
        triggerFlash(0xFF5555); weaponPopScale = 0.7f;
        addFT("All Unequipped!", this.width / 2, this.height / 2, 0xFF5555);
        updateActionButtons(); needsLayoutUpdate = true;
    }

    private void updateActionButtons() {
        if (actionButton1 == null || actionButton2 == null) return;
        actionButton1.visible = false;
        actionButton2.visible = false;

        int abW = rightW - 20;
        int abX = rightX + 10;

        if (unequipAllButton != null) {
            unequipAllButton.visible = (currentTab == Tab.ARSENAL);
            unequipAllButton.setMessage(Component.literal(currentViewMode == ViewMode.CAMOS ? "UNEQUIP ALL CAMOS" : "UNEQUIP ALL SKINS"));
            unequipAllButton.setWidth(abW); unequipAllButton.setX(abX);
        }

        if (currentViewMode == ViewMode.CAMOS) {
            if (selectedCamo.isEmpty() && currentTab == Tab.SHOP) return;
            boolean isUnlocked = LocalCareerManager.isUnlocked(selectedWeapon, selectedCamo);
            LocalCareerManager.CamoDef def = LocalCareerManager.CAMOS.get(selectedCamo);
            String equipped   = LocalCareerManager.getData().equippedCamos.getOrDefault(selectedWeapon, "");
            boolean isEquipped = equipped.equals(selectedCamo);
            boolean isTrulyGlobal = def != null && def.isGlobalUnlock && def.exclusiveWeapons.isEmpty();

            if (currentTab == Tab.ARSENAL || (currentTab == Tab.SHOP && isUnlocked)) {
                actionButton1.visible = true;
                actionButton1.setMessage(Component.literal(isEquipped ? "UNEQUIP" : "EQUIP"));
                actionButton1.active = true;
                if (isTrulyGlobal && !selectedCamo.isEmpty()) {
                    actionButton2.visible = true;
                    actionButton2.setMessage(Component.literal("EQUIP ON ALL"));
                }
            } else if (currentTab == Tab.SHOP && def != null && !isUnlocked
                       && !def.isPrestige && !"exclusive".equals(def.rarity) && !"mastery".equals(def.rarity)) {
                int price      = LocalCareerManager.getDiscountedPrice(selectedCamo);
                boolean canAfford = LocalCareerManager.getData().zrfBalance >= price;
                actionButton1.visible = true;
                actionButton1.setMessage(Component.literal("BUY (" + price + " ZRF)"));
                actionButton1.active = canAfford;
            }
        } else {
            if (selectedSkin.isEmpty()) return;
            boolean isUnlocked = LocalCareerManager.isSkinUnlocked(selectedWeapon, selectedSkin);
            String equipped    = LocalCareerManager.getData().equippedSkins.getOrDefault(selectedWeapon, "");
            boolean isEquipped = equipped.equals(selectedSkin);

            if (currentTab == Tab.ARSENAL || (currentTab == Tab.SHOP && isUnlocked)) {
                actionButton1.visible = true;
                actionButton1.setMessage(Component.literal(isEquipped ? "UNEQUIP" : "EQUIP"));
                actionButton1.active = true;
            } else if (currentTab == Tab.SHOP && !isUnlocked) {
                LocalCareerManager.SkinDef def = LocalCareerManager.SKINS.get(selectedSkin);
                if (def != null && "BUY".equals(def.unlockType)) {
                    int price = def.price;
                    boolean canAfford = LocalCareerManager.getData().zrfBalance >= price;
                    actionButton1.visible = true;
                    actionButton1.setMessage(Component.literal("BUY (" + price + " ZRF)"));
                    actionButton1.active = canAfford;
                }
            }
        }

        if (actionButton2.visible) {
            int half = (abW - 4) / 2;
            actionButton1.setWidth(half);  actionButton1.setX(abX);
            actionButton2.setWidth(half);  actionButton2.setX(abX + half + 4);
        } else {
            actionButton1.setWidth(abW);   actionButton1.setX(abX);
        }
    }

    private void handleAction1() {
        playSound();
        int bx = actionButton1.getX() + actionButton1.getWidth() / 2;
        int by = actionButton1.getY();

        if (currentViewMode == ViewMode.CAMOS) {
            if (currentTab == Tab.ARSENAL || (currentTab == Tab.SHOP && LocalCareerManager.isUnlocked(selectedWeapon, selectedCamo))) {
                String eq = LocalCareerManager.getData().equippedCamos.getOrDefault(selectedWeapon, "");
                if (eq.equals(selectedCamo)) {
                    LocalCareerManager.equipCamo(selectedWeapon, "");
                    weaponPopScale = 0.8f; triggerFlash(0xAAAAAA);
                    addFT("Unequipped", bx, by, 0xAAAAAA);
                } else {
                    LocalCareerManager.equipCamo(selectedWeapon, selectedCamo);
                    weaponPopScale = 1.3f; triggerFlash(0x00FF00);
                    addFT("✓ Equipped", bx, by, 0x55FF55);
                }
                needsLayoutUpdate = true; updateActionButtons();
            } else if (currentTab == Tab.SHOP && !LocalCareerManager.isUnlocked(selectedWeapon, selectedCamo)) {
                int price = LocalCareerManager.getDiscountedPrice(selectedCamo);
                if (LocalCareerManager.buyCamo(selectedWeapon, selectedCamo)) {
                    LocalCareerManager.equipCamo(selectedWeapon, selectedCamo);
                    weaponPopScale = 1.4f; triggerFlash(0xFFD700);
                    addFT("-" + price + " ZRF", bx, by, 0xFF5555);
                    addFT("Unlocked & Equipped!", bx, by - 12, 0x55FF55);
                    needsLayoutUpdate = true; updateActionButtons();
                }
            }
        } else {
            if (currentTab == Tab.ARSENAL || (currentTab == Tab.SHOP && LocalCareerManager.isSkinUnlocked(selectedWeapon, selectedSkin))) {
                String eq = LocalCareerManager.getData().equippedSkins.getOrDefault(selectedWeapon, "");
                if (eq.equals(selectedSkin)) {
                    LocalCareerManager.equipSkin(selectedWeapon, "");
                    weaponPopScale = 0.8f; triggerFlash(0xAAAAAA);
                    addFT("Unequipped", bx, by, 0xAAAAAA);
                } else {
                    LocalCareerManager.equipSkin(selectedWeapon, selectedSkin);
                    weaponPopScale = 1.3f; triggerFlash(0x00FF00);
                    addFT("✓ Equipped", bx, by, 0x55FF55);
                }
                needsLayoutUpdate = true; updateActionButtons();
            } else if (currentTab == Tab.SHOP && !LocalCareerManager.isSkinUnlocked(selectedWeapon, selectedSkin)) {
                if (LocalCareerManager.buySkin(selectedWeapon, selectedSkin)) {
                    LocalCareerManager.equipSkin(selectedWeapon, selectedSkin);
                    weaponPopScale = 1.4f; triggerFlash(0xFFD700);
                    addFT("Unlocked & Equipped!", bx, by - 12, 0x55FF55);
                    needsLayoutUpdate = true; updateActionButtons();
                }
            }
        }
    }

    private void handleAction2() {
        playSound();
        if (currentViewMode == ViewMode.CAMOS && !selectedCamo.isEmpty()
            && LocalCareerManager.isUnlocked(selectedWeapon, selectedCamo)) {
            LocalCareerManager.equipCamoOnAll(selectedCamo);
            weaponPopScale = 1.3f; triggerFlash(0x00FF00);
            addFT("✓ Equipped on All",
                  actionButton2.getX() + actionButton2.getWidth()/2, actionButton2.getY(), 0x55FF55);
            needsLayoutUpdate = true; updateActionButtons();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.minecraft != null && this.minecraft.level == null)
            this.minecraft.getTextureManager().tick();

        if (resetConfirmTimer > 0) {
            resetConfirmTimer--;
            if (resetConfirmTimer == 0 && resetButton != null)
                resetButton.setMessage(Component.literal("Reset Data"));
        }

        weaponPopScale += (1.0f - weaponPopScale) * 0.2f;
        flashAlpha     *= 0.8f;
        for (FloatingText ft : floatingTexts) { ft.ticksAlive++; ft.y -= 0.5f; }
        floatingTexts.removeIf(ft -> ft.ticksAlive > ft.maxTicks);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (super.mouseDragged(mx, my, btn, dx, dy)) return true;
        if (btn == 0 && (currentTab == Tab.ARSENAL || currentTab == Tab.SHOP)
            && mx >= centerX && mx <= rightX) {
            scrollOffset = clampScroll(scrollOffset - dy); return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (super.mouseScrolled(mx, my, delta)) return true;
        if ((currentTab == Tab.ARSENAL || currentTab == Tab.SHOP)
            && mx >= centerX && mx <= rightX) {
            scrollOffset = clampScroll(scrollOffset - delta * 16); return true;
        }
        return false;
    }

    private double clampScroll(double v) { return Math.max(0, Math.min(v, maxScroll)); }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        if ((currentTab == Tab.ARSENAL || currentTab == Tab.SHOP)
            && mx >= centerX && mx <= rightX) {
            handleGridClick(mx, my); return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        for (EditBox box : new EditBox[]{ camoSearchBox, wpnSearchBox, redeemCodeBox }) {
            if (box != null && box.isFocused()) {
                if (kc == GLFW.GLFW_KEY_ESCAPE) { box.setFocused(false); return true; }
                return box.keyPressed(kc, sc, mod);
            }
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBG(g);
        renderBGGrid(g);

        if (flashAlpha > 0.01f) {
            int a = (int)(flashAlpha * 255);
            g.fill(0, 0, this.width, this.height, (a << 24) | (flashColor & 0xFFFFFF));
        }

        super.render(g, mx, my, pt);
        renderTopBar(g);
        renderTabsBackground(g);

        if (currentTab == Tab.ARSENAL || currentTab == Tab.SHOP) {
            renderLeftPanel(g, mx, my);
            if (needsLayoutUpdate) { updateGridLayout(); needsLayoutUpdate = false; }
            renderGrid(g, mx, my);
            renderRightPanel(g);
        } else if (currentTab == Tab.CHALLENGES) {
            renderChallenges(g);
        } else if (currentTab == Tab.BARRACKS) {
            renderBarracks(g);
        }

        renderStatusBar(g);

        for (FloatingText ft : floatingTexts) {
            float alpha = 1.0f - ((float)ft.ticksAlive / ft.maxTicks);
            int a = (int)(alpha * 255);
            g.drawCenteredString(font, ft.text, (int)ft.x, (int)ft.y,
                                 (a << 24) | (ft.color & 0xFFFFFF));
        }
    }

    private void renderBG(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, 0xFF222222);
        g.fillGradient(0, 0, this.width, this.height, 0x44001122, 0xBB000000);
    }

    private void renderBGGrid(GuiGraphics g) {
        for (int gx = 0; gx < this.width;  gx += 32) g.fill(gx, 0, gx+1, this.height, C_BORDER_GRID);
        for (int gy = 0; gy < this.height; gy += 32) g.fill(0, gy, this.width, gy+1,   C_BORDER_GRID);
    }

    private void renderTopBar(GuiGraphics g) {
        g.fill(0, 0, this.width, H_TOPBAR, 0xCC000000);
        g.fill(0, H_TOPBAR - 1, this.width, H_TOPBAR, C_BORDER_CYAN_DIM);
        g.drawCenteredString(font, "C A R E E R", this.width / 2, H_TOPBAR/2 - 4, C_TEXT_GOLD);

        String zrfTxt = "⬡ " + LocalCareerManager.getData().zrfBalance + " ZRF";
        int zrfX = this.width - font.width(zrfTxt) - 8;
        if (currentTab == Tab.SHOP && dailyButton != null) zrfX -= (dailyButton.getWidth() + 6);
        g.drawString(font, zrfTxt, zrfX, H_TOPBAR/2 - 4, C_TEXT_GREEN);

        String boostTxt = LocalCareerManager.getActiveBoostText();
        String eventTxt = LocalCareerManager.getActiveEventText();
        int infoY = H_TOPBAR/2 - 4;
        if (!boostTxt.isEmpty()) g.drawString(font, boostTxt, 60, infoY, 0xFFFF55FF);
        if (!eventTxt.isEmpty() && currentTab == Tab.SHOP)
            g.drawString(font, "§l" + eventTxt + " SALE!", 60, infoY, 0xFFFF3333);
    }

    private void renderTabsBackground(GuiGraphics g) {
        g.fill(0, H_TOPBAR, this.width, bodyY, 0x8C000000);
        g.fill(0, bodyY - 1,  this.width, bodyY, C_BORDER_CYAN_DIM);
    }

    private void renderLeftPanel(GuiGraphics g, int mx, int my) {
        g.fill(0, bodyY, leftW, this.height - H_STATUSBAR, C_BG_PANEL);
        g.fill(leftW - 1, bodyY, leftW, this.height - H_STATUSBAR, C_BORDER_CYAN_DIM);
        g.drawString(font, "WEAPONS", 5, bodyY + 4, C_TEXT_CYAN);
        g.fill(4, bodyY + 14, leftW - 4, bodyY + 15, C_BORDER_CYAN_DIM);
        if (weaponList != null) weaponList.render(g, mx, my, 0);
    }

    private void renderRightPanel(GuiGraphics g) {
        g.fill(rightX, bodyY, this.width, this.height - H_STATUSBAR, C_BG_PANEL_DARK);
        g.fill(rightX, bodyY, rightX + 1, this.height - H_STATUSBAR, C_BORDER_CYAN_DIM);

        int cx  = rightX + rightW / 2;
        int sY  = bodyY + 4;  

        g.drawCenteredString(font, selectedWeapon.toUpperCase(), cx, sY, C_TEXT_WHITE);
        sY += 11;

        int level   = LocalCareerManager.getWeaponLevel(selectedWeapon);
        int curXP   = LocalCareerManager.getWeaponXpInCurrentLevel(selectedWeapon);
        int nextXP  = LocalCareerManager.getWeaponXpForNextLevel(selectedWeapon);

        g.drawCenteredString(font, "LEVEL " + level + (level >= 100 ? " (MAX)" : ""), cx, sY, C_TEXT_CYAN);
        sY += 11;

        int barW = (int)(rightW * 0.75f);
        int barX = cx - barW / 2;
        String xpLbl = (level < 100) ? curXP + " / " + nextXP + " XP" : "MAX LEVEL";
        g.drawCenteredString(font, xpLbl, cx, sY, C_TEXT_DARK);
        sY += 9;

        g.fill(barX, sY, barX + barW, sY + 4, 0xFF1A1A1A);
        g.fill(barX - 1, sY - 1, barX + barW + 1, sY + 5, 0xFF222222);
        if (level < 100) {
            float prog = (float) curXP / nextXP;
            g.fill(barX, sY, barX + (int)(barW * prog), sY + 4, 0xFF00CCFF);
        } else {
            g.fill(barX, sY, barX + barW, sY + 4, 0xFFFFCC00);
        }
        sY += 8;
        sY = renderDivider(g, sY);

        int prevW = (int)(rightW * 0.78f);
        int prevH = Math.min(80, bodyH / 5);
        int prevX = cx - prevW / 2;

        g.fill(prevX, sY, prevX + prevW, sY + prevH, C_BG_PREVIEW);
        g.renderOutline(prevX, sY, prevW, prevH, C_BORDER_CYAN_DIM);
        renderCorner(g, prevX,            sY,            true,  true);
        renderCorner(g, prevX + prevW - 8, sY,            false, true);
        renderCorner(g, prevX,            sY + prevH - 8, true,  false);
        renderCorner(g, prevX + prevW - 8, sY + prevH - 8, false, false);

        String renderCamo = selectedCamo.isEmpty()
            ? LocalCareerManager.getData().equippedCamos.getOrDefault(selectedWeapon, "") : selectedCamo;
        String renderSkin = selectedSkin.isEmpty()
            ? LocalCareerManager.getData().equippedSkins.getOrDefault(selectedWeapon, "") : selectedSkin;

        ItemStack stack = WeaponFacade.createWeaponStack("zombierool:" + selectedWeapon, false, minecraft.player);
        if (!stack.isEmpty()) {
            if (!renderSkin.isEmpty()) {
                stack.getOrCreateTag().putString("zr_skin", renderSkin);
            } else if (!renderCamo.isEmpty()) {
                stack.getOrCreateTag().putString("zr_camo", renderCamo);
            }

            PoseStack pose = g.pose();
            pose.pushPose();
            int renderCX = prevX + prevW / 2;
            int renderCY = sY + prevH / 2;
            pose.translate(renderCX, renderCY, 150);

            float scale = Math.min(prevW, prevH) * 0.65f * weaponPopScale;
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(15f));
            float angle = ZRClientConfig.animateWeaponPreview()
                ? (System.currentTimeMillis() % 4000L) / 4000.0f * 360f : 15f;
            pose.mulPose(Axis.YP.rotationDegrees(angle));

            Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.FIXED, 0xF000F0,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                pose, g.bufferSource(), minecraft.level, 0);

            pose.popPose();
        }

        sY += prevH + 2;
        sY = renderDivider(g, sY);

        int kills     = LocalCareerManager.getData().weaponKills.getOrDefault(selectedWeapon, 0);
        int headshots = LocalCareerManager.getData().weaponHeadshots.getOrDefault(selectedWeapon, 0);
        int paps      = LocalCareerManager.getData().weaponPaps.getOrDefault(selectedWeapon, 0);
        int prestige  = LocalCareerManager.getData().prestigeLevel;

        int col1X = rightX + 10;
        int col2X = rightX + rightW / 2 + 4;
        
        g.drawString(font, "KILLS",     col1X, sY,     C_TEXT_DARK);
        g.drawString(font, "HEADSHOTS", col2X, sY,     C_TEXT_DARK);
        sY += 9;
        g.drawString(font, String.valueOf(kills),     col1X, sY, C_TEXT_LIGHT);
        g.drawString(font, String.valueOf(headshots), col2X, sY, C_TEXT_LIGHT);
        sY += 12;

        g.drawString(font, "PAP",     col1X, sY, C_TEXT_DARK);
        g.drawString(font, "PRESTIGE",col2X, sY, C_TEXT_DARK);
        sY += 9;
        g.drawString(font, String.valueOf(paps),    col1X, sY, C_TEXT_LIGHT);
        g.drawString(font, String.valueOf(prestige),col2X, sY, C_TEXT_LIGHT);
        sY += 12;

        sY = renderDivider(g, sY);

        if (currentViewMode == ViewMode.CAMOS) {
            String displayCamo = selectedCamo.isEmpty() ? renderCamo : selectedCamo;
            if (displayCamo.isEmpty()) {
                g.drawCenteredString(font, "Default Camo", cx, sY, C_TEXT_MED);
            } else {
                LocalCareerManager.CamoDef def = LocalCareerManager.CAMOS.get(displayCamo);
                if (def != null) {
                    String name = Component.translatable(def.langKey).getString();
                    while (font.width(name) > rightW - 14 && name.length() > 4)
                        name = name.substring(0, name.length() - 1);
                    g.drawCenteredString(font, name, cx, sY, C_TEXT_ORANGE);
                    sY += 11;
                    
                    String sub = LocalCareerManager.isUnlocked(selectedWeapon, displayCamo)
                        ? "EQUIPPED · " + def.rarity.toUpperCase()
                        : def.rarity.toUpperCase();
                    g.drawCenteredString(font, sub, cx, sY, C_TEXT_DARK);
                    sY += 11;
                    
                    if (currentTab == Tab.SHOP && !def.isPrestige
                        && !LocalCareerManager.isUnlocked(selectedWeapon, displayCamo)
                        && !"mastery".equals(def.rarity)) {
                        int price    = LocalCareerManager.getDiscountedPrice(displayCamo);
                        boolean sale = price < def.price;
                        if (sale) {
                            int pct = Math.round((1f - (float)price / def.price) * 100f);
                            g.drawCenteredString(font, "SALE -" + pct + "%", cx, sY, 0xFFFF3333);
                            sY += 10;
                        }
                        int balance = LocalCareerManager.getData().zrfBalance;
                        int priceColor = (balance >= price) ? 0x00FF00 : 0xFF0000;
                        g.drawCenteredString(font, price + " ZRF", cx, sY, priceColor);
                    }
                }
            }
        } else {
            String displaySkin = selectedSkin.isEmpty() ? renderSkin : selectedSkin;
            if (displaySkin.isEmpty()) {
                g.drawCenteredString(font, "Default Skin", cx, sY, C_TEXT_MED);
            } else {
                LocalCareerManager.SkinDef def = LocalCareerManager.SKINS.get(displaySkin);
                if (def != null) {
                    String name = Component.translatable(def.langKey).getString();
                    while (font.width(name) > rightW - 14 && name.length() > 4)
                        name = name.substring(0, name.length() - 1);
                    g.drawCenteredString(font, name, cx, sY, 0xFFFF55FF);
                    sY += 11;

                    boolean unlocked = LocalCareerManager.isSkinUnlocked(selectedWeapon, displaySkin);
                    g.drawCenteredString(font, unlocked ? "UNLOCKED" : "LOCKED", cx, sY,
                                         unlocked ? C_TEXT_GREEN : 0xFFFF5555);
                    
                    if (currentTab == Tab.SHOP && !unlocked && "BUY".equals(def.unlockType)) {
                        sY += 11;
                        int balance = LocalCareerManager.getData().zrfBalance;
                        int priceColor = (balance >= def.price) ? 0x00FF00 : 0xFF0000;
                        g.drawCenteredString(font, def.price + " ZRF", cx, sY, priceColor);
                    }
                }
            }
        }
    }

    private int renderDivider(GuiGraphics g, int y) {
        g.fill(rightX + 6, y + 2, this.width - 6, y + 3, C_BORDER_CYAN_DIM);
        return y + 6;
    }

    private void renderCorner(GuiGraphics g, int x, int y, boolean left, boolean top) {
        int color = 0x6600C8FF;
        int len   = 7;
        g.fill(x, y, x + len, y + 1, color);
        g.fill(x, y, x + 1, y + len, color);
        if (!left)  { g.fill(x + 1, y, x + len, y + 1, color); g.fill(x + len - 1, y, x + len, y + len, color); }
        if (!top)   { g.fill(x, y + len - 1, x + len, y + len, color); }
    }

    private void renderStatusBar(GuiGraphics g) {
        int sY = this.height - H_STATUSBAR;
        g.fill(0, sY, this.width, this.height, C_BG_PANEL_XDARK);
        g.fill(0, sY, this.width, sY + 1, C_BORDER_CYAN_DIM);
        
        int hx = 8;
        hx = drawHint(g, "[CLICK] SELECT",       hx, sY + 4);
        if (this.width > 240)
            hx = drawHint(g, "[DBLCLICK] EQUIP",  hx, sY + 4);
        if (this.width > 320)
            drawHint(g, "[SCROLL] NAVIGATE",       hx, sY + 4);
    }

    private int drawHint(GuiGraphics g, String text, int x, int y) {
        int bracket1 = text.indexOf('[');
        int bracket2 = text.indexOf(']');
        if (bracket1 >= 0 && bracket2 > bracket1) {
            String key  = text.substring(bracket1, bracket2 + 1);
            String rest = text.substring(bracket2 + 1);
            g.drawString(font, key,  x, y, C_TEXT_DARK);
            g.drawString(font, rest, x + font.width(key), y, C_TEXT_HINT);
        } else {
            g.drawString(font, text, x, y, C_TEXT_HINT);
        }
        return x + font.width(text) + 14;
    }

    private void updateGridLayout() {
        currentGridLayout.clear();
        int startY = bodyY + 28;
        if (currentTab == Tab.SHOP && currentViewMode == ViewMode.CAMOS) startY += 20;

        int cols    = Math.max(1, centerW / (cellSize + cellSpacing));
        String query = (camoSearchBox != null) ? camoSearchBox.getValue().toLowerCase().trim() : "";

        if (currentViewMode == ViewMode.CAMOS) {
            Map<String, List<String>> grouped = new HashMap<>();

            if (currentTab == Tab.ARSENAL && query.isEmpty()) grouped.computeIfAbsent("basic", k -> new ArrayList<>()).add("");

            for (Map.Entry<String, LocalCareerManager.CamoDef> e : LocalCareerManager.CAMOS.entrySet()) {
                String camo = e.getKey();
                LocalCareerManager.CamoDef def = e.getValue();

                boolean weaponMatch = def.exclusiveWeapons.isEmpty() || def.exclusiveWeapons.contains(selectedWeapon);
                if (!weaponMatch) continue;

                String localName = Component.translatable(def.langKey).getString().toLowerCase();
                if (!query.isEmpty() && !localName.contains(query)) continue;

                if (currentTab == Tab.ARSENAL) {
                    if (LocalCareerManager.isUnlocked(selectedWeapon, camo)
                        || "mastery".equals(def.rarity) || "prestige".equals(def.rarity))
                        grouped.computeIfAbsent(def.rarity, k -> new ArrayList<>()).add(camo);
                } else {
                    if (def.isPrestige || "exclusive".equals(def.rarity) || "mastery".equals(def.rarity)) continue;
                    if (!currentRarityFilter.equals("All") && !def.rarity.equalsIgnoreCase(currentRarityFilter)) continue;
                    grouped.computeIfAbsent(def.rarity, k -> new ArrayList<>()).add(camo);
                }
            }

            List<String> order = Arrays.asList("basic","common","rare","epic","legendary","mastery","prestige","exclusive");
            for (String rarity : order) {
                List<String> list = grouped.get(rarity);
                if (list == null || list.isEmpty()) continue;
                if (currentTab == Tab.SHOP) sortShopList(list);
                addGridHeader(rarity);
                for (int i = 0; i < list.size(); i++) {
                    GridElement cell = new GridElement();
                    cell.isHeader = false;
                    cell.isCamo = true;
                    cell.id = list.get(i);
                    cell.x = centerX + (i % cols) * (cellSize + cellSpacing);
                    cell.y = 0; cell.width = cellSize; cell.height = cellSize;
                    currentGridLayout.add(cell);
                }
            }
        } else {
            List<String> validSkins = new ArrayList<>();
            if (currentTab == Tab.ARSENAL && query.isEmpty()) {
                validSkins.add("");
            }
            for (Map.Entry<String, LocalCareerManager.SkinDef> e : LocalCareerManager.SKINS.entrySet()) {
                String skin = e.getKey();
                LocalCareerManager.SkinDef def = e.getValue();
                boolean weaponMatch = def.exclusiveWeapons.isEmpty() || def.exclusiveWeapons.contains(selectedWeapon);
                if (!weaponMatch) continue;

                String localName = Component.translatable(def.langKey).getString().toLowerCase();
                if (!query.isEmpty() && !localName.contains(query)) continue;

                validSkins.add(skin);
            }

            if (!validSkins.isEmpty()) {
                addGridHeader("skins");
                for (int i = 0; i < validSkins.size(); i++) {
                    GridElement cell = new GridElement();
                    cell.isHeader = false;
                    cell.isCamo = false;
                    cell.id = validSkins.get(i);
                    cell.x = centerX + (i % cols) * (cellSize + cellSpacing);
                    cell.y = 0; cell.width = cellSize; cell.height = cellSize;
                    currentGridLayout.add(cell);
                }
            }
        }

        int curY = startY;
        for (int i = 0; i < currentGridLayout.size(); i++) {
            GridElement elem = currentGridLayout.get(i);
            elem.y = curY;
            if (elem.isHeader) {
                curY += 18;
            } else {
                boolean isRowEnd = (i == currentGridLayout.size() - 1)
                    || currentGridLayout.get(i + 1).isHeader
                    || (i + 1 < currentGridLayout.size() && currentGridLayout.get(i + 1).x <= elem.x);
                if (isRowEnd) curY += cellSize + cellSpacing;
            }
        }

        maxScroll = Math.max(0, curY - (this.height - H_STATUSBAR) + cellSize + 20);
        scrollOffset  = clampScroll(scrollOffset);
    }

    private void addGridHeader(String rarity) {
        GridElement h = new GridElement();
        h.isHeader = true; h.text = getRarityLabel(rarity);
        h.x = centerX + 2; h.y = 0; h.width = centerW; h.height = 15;
        currentGridLayout.add(h);
    }

    private void sortShopList(List<String> list) {
        list.sort((c1, c2) -> {
            LocalCareerManager.CamoDef d1 = LocalCareerManager.CAMOS.get(c1);
            LocalCareerManager.CamoDef d2 = LocalCareerManager.CAMOS.get(c2);
            return switch (currentShopSort) {
                case "Price: Low-High" -> Integer.compare(
                    LocalCareerManager.getDiscountedPrice(c1),
                    LocalCareerManager.getDiscountedPrice(c2));
                case "Name: A-Z" -> Component.translatable(d1.langKey).getString()
                    .compareToIgnoreCase(Component.translatable(d2.langKey).getString());
                default -> Integer.compare(
                    LocalCareerManager.getDiscountedPrice(c2),
                    LocalCareerManager.getDiscountedPrice(c1));
            };
        });
    }

    private String getRarityLabel(String r) {
        return switch (r) {
            case "basic"     -> "─ BASIC";
            case "common"    -> "─ COMMON";
            case "rare"      -> "─ RARE";
            case "epic"      -> "─ EPIC";
            case "legendary" -> "─ LEGENDARY";
            case "mastery"   -> "─ MASTERY";
            case "prestige"  -> "─ PRESTIGE";
            case "exclusive" -> "─ EXCLUSIVE";
            case "skins"     -> "─ SKINS";
            default          -> "─ " + r.toUpperCase();
        };
    }

    private int getRarityColor(String r) {
        return switch (r) {
            case "basic"     -> 0xFF888888;
            case "common"    -> 0xFFAAAAAA;
            case "rare"      -> 0xFF4488FF;
            case "epic"      -> 0xFFAA44FF;
            case "legendary" -> 0xFFFFAA00;
            case "mastery"   -> 0xFF00FFCC;
            case "prestige"  -> 0xFFFF00FF;
            case "exclusive" -> 0xFFFF4444;
            case "skins"     -> 0xFFFF55FF;
            default          -> 0xFFCCCCCC;
        };
    }

    private int getCamoBgColor(String rarity, boolean alt) {
        return switch (rarity) {
            case "basic"     -> 0xFF1C1C1C;
            case "common"    -> alt ? 0xFF2A2A2A : 0xFF1E1E1E;
            case "rare"      -> alt ? 0xFF0D1A2E : 0xFF071220;
            case "epic"      -> alt ? 0xFF1A0D2E : 0xFF0F0720;
            case "legendary" -> alt ? 0xFF2A1F00 : 0xFF1A1200;
            case "mastery"   -> alt ? 0xFF001E1A : 0xFF00120F;
            case "prestige"  -> alt ? 0xFF1A001A : 0xFF0F000F;
            case "exclusive" -> alt ? 0xFF1A0000 : 0xFF0F0000;
            default          -> 0xFF111111;
        };
    }

    private void renderGrid(GuiGraphics g, int mx, int my) {
        int startY = bodyY + 28;
        if (currentTab == Tab.SHOP && currentViewMode == ViewMode.CAMOS) startY += 20;

        String eqCamo = LocalCareerManager.getData().equippedCamos.getOrDefault(selectedWeapon, "");
        String eqSkin = LocalCareerManager.getData().equippedSkins.getOrDefault(selectedWeapon, "");

        g.enableScissor(centerX, startY, centerX + centerW, this.height - H_STATUSBAR);

        for (GridElement elem : currentGridLayout) {
            int y = (int)(elem.y - scrollOffset);
            if (y + elem.height < startY || y > this.height) continue;

            if (elem.isHeader) {
                String r = elem.text.replace("─ ", "").toLowerCase();
                int hColor = getRarityColor(r);
                g.drawString(font, elem.text, elem.x, y + 2, hColor);
                g.fill(elem.x, y + 12, elem.x + (int)(centerW * 0.9f), y + 13,
                       hColor & 0x00FFFFFF | 0x44000000);
            } else {
                if (elem.isCamo) renderCamoCell(g, elem, y, mx, my, eqCamo);
                else             renderSkinCell(g, elem, y, mx, my, eqSkin);
            }
        }
        g.disableScissor();
    }

    private void renderCamoCell(GuiGraphics g, GridElement elem, int y,
                                 int mx, int my, String eqCamo) {
        String camo       = elem.id;
        boolean isSelected = camo.equals(selectedCamo);
        boolean isHovered  = mx >= elem.x && mx < elem.x + elem.width && my >= y && my < y + elem.height;

        String rarity = "basic";
        LocalCareerManager.CamoDef def = camo.isEmpty() ? null : LocalCareerManager.CAMOS.get(camo);
        if (def != null) rarity = def.rarity;

        g.fill(elem.x, y, elem.x + elem.width, y + elem.height, getCamoBgColor(rarity, false));
        g.fill(elem.x + 2, y + 2, elem.x + elem.width - 2, y + elem.height - 2, getCamoBgColor(rarity, true));

        int borderColor = isSelected ? C_BORDER_CYAN_FULL : (isHovered ? 0x99FFFFFF : 0x1EFFFFFF);
        g.renderOutline(elem.x, y, elem.width, elem.height, borderColor);

        if (eqCamo.equals(camo) && !camo.isEmpty()) {
            g.fill(elem.x + 2, y + 2, elem.x + 8, y + 8, 0xFF44FF88);
        }

        if (currentTab == Tab.SHOP && def != null) {
            float dm = LocalCareerManager.getData().dailyDiscounts.getOrDefault(camo, 1.0f);
            float em = LocalCareerManager.getEventDiscountMultiplier();
            if (dm * em < 1.0f) {
                g.fill(elem.x, y, elem.x + 12, y + 9, 0xFFCC0000);
                g.drawString(font, "%", elem.x + 2, y + 1, 0xFFFFFFFF);
            }
        }

        if (!camo.isEmpty()) {
            try {
                ResourceLocation loc = new ResourceLocation("zombierool",
                    "item/camos/" + camo.replace("camo_", ""));
                TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager()
                    .getAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                    .getSprite(loc);
                RenderSystem.setShaderTexture(0,
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
                RenderSystem.enableBlend();
                int m = Math.max(2, cellSize / 20);
                g.blit(elem.x + m, y + m, 0, elem.width - m*2, elem.height - m*2, sprite);
                RenderSystem.disableBlend();
            } catch (Exception ignored) {}

            String name = Component.translatable(def.langKey).getString();
            while (font.width(name) > elem.width - 4 && name.length() > 3)
                name = name.substring(0, name.length() - 1);
            g.drawCenteredString(font, name, elem.x + elem.width/2, y + elem.height - 10, C_TEXT_LIGHT);

            if (def.exclusiveWeapons != null && !def.exclusiveWeapons.isEmpty()) {
                g.fill(elem.x, y + elem.height - 8, elem.x + elem.width, y + elem.height, 0x88880088);
                g.drawCenteredString(font, "EXC", elem.x + elem.width/2, y + elem.height - 7, 0xFFFFFFFF);
            }

            if (currentTab == Tab.SHOP && LocalCareerManager.isUnlocked(selectedWeapon, camo)) {
                g.fill(elem.x, y, elem.x + elem.width, y + elem.height, 0xBB000000);
                renderRotatedLabel(g, "OWNED", elem.x, y, elem.width, elem.height, C_TEXT_GREEN);
            }

            boolean arsenalLocked = currentTab == Tab.ARSENAL
                && !camo.isEmpty() && !LocalCareerManager.isUnlocked(selectedWeapon, camo);
            if (arsenalLocked) {
                g.fill(elem.x, y, elem.x + elem.width, y + elem.height, 0xBB000000);
                renderRotatedLabel(g, "LOCKED", elem.x, y, elem.width, elem.height, 0xFFFF5555);
                if (isHovered) renderLockedTooltip(g, def, camo, mx, my);
            }

        } else {
            g.drawCenteredString(font, "Default", elem.x + elem.width/2, y + elem.height/2 - 4, C_TEXT_MED);
        }
    }

    private void renderSkinCell(GuiGraphics g, GridElement elem, int y,
                                 int mx, int my, String eqSkin) {
        String skin        = elem.id;
        boolean isSelected = skin.equals(selectedSkin);
        boolean isHovered  = mx >= elem.x && mx < elem.x + elem.width && my >= y && my < y + elem.height;
        boolean isLocked   = !skin.isEmpty() && !LocalCareerManager.isSkinUnlocked(selectedWeapon, skin);

        g.fill(elem.x, y, elem.x + elem.width, y + elem.height, 0xFF0D0D1A);

        int borderColor = isSelected ? 0xFFFF55FF : (isHovered ? 0x99FFFFFF : 0x1EFFFFFF);
        g.renderOutline(elem.x, y, elem.width, elem.height, borderColor);

        if (eqSkin.equals(skin) && !skin.isEmpty())
            g.fill(elem.x + 2, y + 2, elem.x + 8, y + 8, 0xFF44FF88);

        if (!skin.isEmpty()) {
            LocalCareerManager.SkinDef def = LocalCareerManager.SKINS.get(skin);
            try {
                ResourceLocation loc = new ResourceLocation("zombierool", "item/skins/" + skin);
                TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager()
                    .getAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                    .getSprite(loc);
                RenderSystem.setShaderTexture(0, net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
                RenderSystem.enableBlend();
                int m = Math.max(2, cellSize / 20);
                g.blit(elem.x + m, y + m, 0, elem.width - m*2, elem.height - m*2, sprite);
                RenderSystem.disableBlend();
            } catch (Exception ignored) {}

            if (def != null) {
                String name = Component.translatable(def.langKey).getString();
                while (font.width(name) > elem.width - 4 && name.length() > 3)
                    name = name.substring(0, name.length() - 1);
                g.drawCenteredString(font, name, elem.x + elem.width/2, y + elem.height - 10, C_TEXT_LIGHT);
            }

            if (currentTab == Tab.SHOP && !isLocked) {
                g.fill(elem.x, y, elem.x + elem.width, y + elem.height, 0xBB000000);
                renderRotatedLabel(g, "OWNED", elem.x, y, elem.width, elem.height, C_TEXT_GREEN);
            }

            if (isLocked) {
                g.fill(elem.x, y, elem.x + elem.width, y + elem.height, 0xBB000000);
                renderRotatedLabel(g, "LOCKED", elem.x, y, elem.width, elem.height, 0xFFFF5555);
                
                if (isHovered && def != null) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable(def.langKey).withStyle(ChatFormatting.LIGHT_PURPLE));
                    if ("BUY".equals(def.unlockType)) {
                        tooltip.add(Component.literal("Can be purchased in the Shop for " + def.price + " ZRF").withStyle(ChatFormatting.YELLOW));
                    } else {
                        tooltip.add(Component.literal("Requires " + def.unlockReq + " " + def.unlockType).withStyle(ChatFormatting.RED));
                    }
                    g.renderComponentTooltip(font, tooltip, mx, my);
                }
            }

        } else {
            g.drawCenteredString(font, "Default", elem.x + elem.width/2, y + elem.height/2 - 4, C_TEXT_MED);
        }
    }

    private void renderRotatedLabel(GuiGraphics g, String text, int cx, int cy,
                                     int w, int h, int color) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx + w / 2f, cy + h / 2f, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-25f));
        g.drawCenteredString(font, text, 0, -4, color);
        pose.popPose();
    }

    private void renderLockedTooltip(GuiGraphics g, LocalCareerManager.CamoDef def,
                                      String camo, int mx, int my) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable(def.langKey).withStyle(ChatFormatting.GOLD));

        if ("mastery".equals(def.rarity)) {
            if ("camo_solid_gold".equals(camo))
                tip.add(Component.translatable("gui.zombierool.career.unlock.gold").withStyle(ChatFormatting.RED));
            else if ("camo_black_ice".equals(camo))
                tip.add(Component.translatable("gui.zombierool.career.unlock.black_ice").withStyle(ChatFormatting.RED));
        } else if ("prestige".equals(def.rarity)) {
            tip.add(Component.translatable("gui.zombierool.career.unlock.prestige", def.prestigeLevelReq)
                             .withStyle(ChatFormatting.RED));
        } else if ("exclusive".equals(def.rarity)) {
            tip.add(Component.translatable("gui.zombierool.career.unlock.exclusive").withStyle(ChatFormatting.RED));
        } else {
            tip.add(Component.translatable("gui.zombierool.career.unlock.shop").withStyle(ChatFormatting.RED));
        }

        g.renderComponentTooltip(font, tip, mx, my);
    }

    private void handleGridClick(double mx, double my) {
        int startY = bodyY + 28;
        if (currentTab == Tab.SHOP && currentViewMode == ViewMode.CAMOS) startY += 20;

        for (GridElement elem : currentGridLayout) {
            if (elem.isHeader) continue;
            int y = (int)(elem.y - scrollOffset);
            if (y + elem.height < startY || y > this.height) continue;

            if (!(mx >= elem.x && mx < elem.x + elem.width && my >= y && my < y + elem.height)) continue;

            if (elem.isCamo) {
                String clicked = elem.id;
                if (currentTab == Tab.SHOP && LocalCareerManager.isUnlocked(selectedWeapon, clicked)) {
                    this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1f));
                    return;
                }
                if (currentTab == Tab.ARSENAL && !clicked.isEmpty()
                    && !LocalCareerManager.isUnlocked(selectedWeapon, clicked)) {
                    this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1f));
                    return;
                }

                long now = System.currentTimeMillis();
                boolean dbl = clicked.equals(lastClickedItem) && (now - lastGridClickTime < 300);
                lastGridClickTime = now; lastClickedItem = clicked;

                playSound(); weaponPopScale = 1.1f;
                selectedCamo = clicked; selectedSkin = "";
                updateActionButtons();

                if (dbl && LocalCareerManager.isUnlocked(selectedWeapon, selectedCamo)) {
                    if (!LocalCareerManager.getData().equippedCamos.getOrDefault(selectedWeapon, "").equals(selectedCamo)) {
                        LocalCareerManager.equipCamo(selectedWeapon, selectedCamo);
                        weaponPopScale = 1.3f; triggerFlash(0x00FF00);
                        if (actionButton1 != null)
                            addFT("✓ Equipped", actionButton1.getX() + actionButton1.getWidth()/2,
                                  actionButton1.getY(), 0x55FF55);
                        updateActionButtons(); needsLayoutUpdate = true;
                    }
                }
            } else {
                String clicked = elem.id;
                LocalCareerManager.SkinDef def = LocalCareerManager.SKINS.get(clicked);

                if (currentTab == Tab.SHOP && LocalCareerManager.isSkinUnlocked(selectedWeapon, clicked)) {
                    this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1f));
                    return;
                }
                if (currentTab == Tab.ARSENAL && !clicked.isEmpty() && !LocalCareerManager.isSkinUnlocked(selectedWeapon, clicked)) {
                    this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1f));
                    return;
                }

                long now = System.currentTimeMillis();
                boolean dbl = clicked.equals(lastClickedItem) && (now - lastGridClickTime < 300);
                lastGridClickTime = now; lastClickedItem = clicked;

                playSound(); weaponPopScale = 1.1f;
                selectedSkin = clicked; selectedCamo = "";
                updateActionButtons();

                if (dbl && LocalCareerManager.isSkinUnlocked(selectedWeapon, selectedSkin)) {
                    if (!LocalCareerManager.getData().equippedSkins.getOrDefault(selectedWeapon, "").equals(selectedSkin)) {
                        LocalCareerManager.equipSkin(selectedWeapon, selectedSkin);
                        weaponPopScale = 1.3f; triggerFlash(0x00FF00);
                        if (actionButton1 != null)
                            addFT("✓ Equipped", actionButton1.getX() + actionButton1.getWidth()/2,
                                  actionButton1.getY(), 0x55FF55);
                        updateActionButtons(); needsLayoutUpdate = true;
                    }
                }
            }
            return;
        }
    }

    private void renderChallenges(GuiGraphics g) {
        int boxW  = Math.min(380, this.width - 24);
        int sX    = this.width / 2 - boxW / 2;
        int y     = bodyY + 10;

        for (Map.Entry<String, LocalCareerManager.ChallengeDef> entry
             : LocalCareerManager.getData().activeChallenges.entrySet()) {
            String id   = entry.getKey();
            var    def  = entry.getValue();
            int    prog = LocalCareerManager.getData().challengeProgress.getOrDefault(id, 0);
            boolean done= LocalCareerManager.getData().challengeCompleted.getOrDefault(id, false);

            g.fill(sX, y, sX + boxW, y + 44, C_BG_PANEL_DARK);
            g.renderOutline(sX, y, boxW, 44, done ? 0xFF44FF88 : C_BORDER_CYAN_MED);

            String typeStr = def.type.name().toLowerCase();
            Component text;
            if (def.context != null && !def.context.isEmpty()) {
                text = Component.translatable("gui.zombierool.career.challenge." + typeStr, def.target, def.context.toUpperCase());
            } else {
                text = Component.translatable("gui.zombierool.career.challenge." + typeStr, def.target);
            }
            g.drawString(font, text.getString(), sX + 8, y + 6, C_TEXT_WHITE);

            if (done) {
                g.drawString(font, Component.translatable("gui.zombierool.career.challenge.completed"), sX + boxW - 62, y + 6, C_TEXT_GREEN);
                g.fill(sX + 8, y + 24, sX + boxW - 8, y + 33, 0xFF00AA00);
            } else {
                g.drawString(font, prog + " / " + def.target, sX + boxW - 50, y + 6, C_TEXT_MED);
                g.fill(sX + 8, y + 24, sX + boxW - 8, y + 33, 0xFF1A1A1A);
                float ratio = Math.min(1f, (float)prog / def.target);
                g.fill(sX + 8, y + 24, sX + 8 + (int)((boxW - 16) * ratio), y + 33, C_TEXT_CYAN);
            }
            g.drawString(font, Component.translatable("gui.zombierool.career.challenge.reward", def.reward), sX + 8, y + 35, C_TEXT_GREEN);
            y += 56;
        }
    }

    private void renderBarracks(GuiGraphics g) {
        int bW  = Math.min(200, (this.width / 2) - 16);
        int bH  = 160;
        int bY  = bodyY + 10;
        int b1X = this.width / 2 - bW - 10;
        int b2X = this.width / 2 + 10;

        g.fill(b1X, bY, b1X + bW, bY + bH, C_BG_PANEL_DARK);
        g.renderOutline(b1X, bY, bW, bH, C_BORDER_CYAN_MED);
        g.drawCenteredString(font, Component.translatable("gui.zombierool.career.barracks.stats").getString(),
                             b1X + bW/2, bY + 8, C_TEXT_CYAN);

        int d = LocalCareerManager.getData().currentLevel;
        g.drawCenteredString(font, "Level: " + d + (d >= 50 ? " (MAX)" : ""), b1X + bW/2, bY + 22, C_TEXT_CYAN);

        int bW2 = (int)(bW * 0.7f);
        int bX2 = b1X + bW/2 - bW2/2;
        int bY2 = bY + 34;
        g.fill(bX2, bY2, bX2 + bW2, bY2 + 5, 0xFF1A1A1A);
        if (d < 50) {
            float xpp = (float)LocalCareerManager.getData().currentXp / LocalCareerManager.getXpRequiredForLevel(d);
            g.fill(bX2, bY2, bX2 + (int)(bW2 * xpp), bY2 + 5, 0xFF00FF00);
            g.drawCenteredString(font, LocalCareerManager.getData().currentXp + " / "
                + LocalCareerManager.getXpRequiredForLevel(d) + " XP", b1X + bW/2, bY2 + 7, C_TEXT_DARK);
        } else {
            g.fill(bX2, bY2, bX2 + bW2, bY2 + 5, 0xFFFFCC00);
            g.drawCenteredString(font, "MAX LEVEL", b1X + bW/2, bY2 + 7, C_TEXT_GOLD);
        }

        int sy = bY + 56;
        int sp = 16;
        g.drawCenteredString(font, "Prestige: "  + LocalCareerManager.getData().prestigeLevel, b1X + bW/2, sy,      C_TEXT_ORANGE); sy += sp;
        g.drawCenteredString(font, "Kills: "     + LocalCareerManager.getData().lifetimeKills,      b1X + bW/2, sy, C_TEXT_MED); sy += sp;
        g.drawCenteredString(font, "Headshots: " + LocalCareerManager.getData().lifetimeHeadshots,  b1X + bW/2, sy, C_TEXT_MED); sy += sp;
        g.drawCenteredString(font, "Waves: "     + LocalCareerManager.getData().lifetimeWaves,      b1X + bW/2, sy, C_TEXT_MED); sy += sp;
        g.drawCenteredString(font, "Revives: "   + LocalCareerManager.getData().lifetimeRevives,    b1X + bW/2, sy, C_TEXT_MED);

        g.fill(b2X, bY, b2X + bW, bY + bH, C_BG_PANEL_DARK);
        g.renderOutline(b2X, bY, bW, bH, 0x5900C8FF);
        g.drawCenteredString(font, Component.translatable("gui.zombierool.career.barracks.actions").getString(),
                             b2X + bW/2, bY + 8, C_TEXT_GOLD);

        g.drawString(font, Component.translatable("gui.zombierool.career.redeem_title"), b2X + 8, bY + 22, C_TEXT_GOLD);
        if (!redeemStatusMessage.isEmpty())
            g.drawString(font, redeemStatusMessage, b2X + 8, bY + 58, redeemStatusColor);

        if (LocalCareerManager.getPrest() >= LocalCareerManager.MAX_PRESTIGE)
            g.drawCenteredString(font, "Max Prestige Reached!", b2X + bW/2, bY + 96, 0xFFFFD700);
        else if (LocalCareerManager.canPrestige())
            g.drawCenteredString(font, "Ready for Prestige!",   b2X + bW/2, bY + 96, C_TEXT_GREEN);
        else
            g.drawCenteredString(font, "Reach Level 50 to Prestige", b2X + bW/2, bY + 96, 0xFFFF5555);
    }

    private class WeaponList extends ObjectSelectionList<WeaponEntry> {
        private final List<WeaponEntry> allEntries = new ArrayList<>();
        public WeaponList(Minecraft mc, int w, int h, int top, int bottom, int itemH) {
            super(mc, w, h, top, bottom, itemH);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);

            for (String wpn : supportedWeaponsCache) {
                WeaponEntry e = new WeaponEntry(wpn);
                allEntries.add(e); this.addEntry(e);
            }
        }
        public void filter(String query) {
            this.clearEntries();
            String q = query.toLowerCase().trim();
            for (WeaponEntry e : allEntries)
                if (q.isEmpty() || e.weaponId.toLowerCase().contains(q)) this.addEntry(e);
            this.setScrollAmount(0); needsLayoutUpdate = true;
        }
        @Override protected void renderBackground(GuiGraphics g) {  }
        @Override public int getRowWidth()          { return this.width - 10; }
        @Override protected int getScrollbarPosition() { return this.x0 + this.width - 5; }
    }

    private class WeaponEntry extends ObjectSelectionList.Entry<WeaponEntry> {
        public final String weaponId;
        WeaponEntry(String id) { this.weaponId = id; }
        @Override
        public void render(GuiGraphics g, int index, int top, int left, int w, int h,
                           int mx, int my, boolean hov, float pt) {
            boolean sel = selectedWeapon.equals(weaponId);
            if (sel) {
                g.fill(left, top, left + 2, top + h, C_BORDER_CYAN_FULL);
                g.fill(left + 2, top, left + w, top + h, C_BG_SELECTED);
            } else if (hov) {
                g.fill(left, top, left + w, top + h, C_BG_HOVER);
            }
            int color = sel ? C_TEXT_CYAN : (hov ? C_TEXT_WHITE : C_TEXT_MED);
            String name = weaponId.toUpperCase();
            while (font.width(name) > w - 18 && name.length() > 2) name = name.substring(0, name.length() - 1);
            g.drawString(font, name, left + 6, top + h/2 - 4, color);

            boolean hasStar = LocalCareerManager.CAMOS.values().stream().anyMatch(c -> c.exclusiveWeapons.contains(weaponId))
                           || LocalCareerManager.SKINS.values().stream().anyMatch(s -> s.exclusiveWeapons.contains(weaponId));
            if (hasStar) g.drawString(font, "★", left + w - 12, top + h/2 - 4, C_TEXT_ORANGE);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            weaponPopScale = 0.9f;
            selectedWeapon = weaponId;
            selectedCamo = ""; selectedSkin = "";
            needsLayoutUpdate = true; updateActionButtons();
            scrollOffset = 0; playSound();
            return true;
        }
        @Override public Component getNarration() { return Component.literal(weaponId); }
    }
}