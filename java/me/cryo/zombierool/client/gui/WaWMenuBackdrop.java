package me.cryo.zombierool.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * Grayscale block diorama for the main menu. Drawn once, then scrolled.
 * Far hills are flat silhouettes, the village uses one block size and one light direction.
 */
final class WaWMenuBackdrop {
    private static final int CELL = 8;
    private static final int COLS = 128;
    private static final int ROWS = 72;
    private static final int WIDTH = COLS * CELL;
    private static final int HEIGHT = ROWS * CELL;
    private static final int GROUND = 48;

    private static final ResourceLocation FAR_ID = new ResourceLocation("zombierool", "dynamic/waw_far");
    private static final ResourceLocation MID_ID = new ResourceLocation("zombierool", "dynamic/waw_mid");
    private static final ResourceLocation SHADE_ID = new ResourceLocation("zombierool", "dynamic/waw_shade");

    private static DynamicTexture farTex;
    private static DynamicTexture midTex;
    private static DynamicTexture shadeTex;

    private WaWMenuBackdrop() {}

    static void draw(GuiGraphics g, int screenW, int screenH, float time) {
        ensure();
        int destH = screenH;
        int destW = Math.max(1, Math.round(WIDTH * (screenH / (float) HEIGHT)));
        blitTiled(g, FAR_ID, screenW, destW, destH, time * 0.42f);
        blitTiled(g, MID_ID, screenW, destW, destH, time * 1.05f);

        int shadeW = Math.min(screenW, (int) (screenW * 0.46f));
        g.blit(SHADE_ID, 0, 0, shadeW, screenH, 0f, 0f, 256, 1, 256, 1);
        g.fillGradient(0, 0, screenW, Math.max(1, screenH / 8), 0xAA000000, 0x00000000);
        g.fillGradient(0, screenH - Math.max(1, screenH / 7), screenW, screenH, 0x00000000, 0xCC000000);
    }

    private static void blitTiled(GuiGraphics g, ResourceLocation id, int screenW, int destW, int destH, float time) {
        int scroll = Math.floorMod((int) Math.floor(time), destW);
        for (int x = -scroll; x < screenW; x += destW) {
            g.blit(id, x, 0, destW, destH, 0f, 0f, WIDTH, HEIGHT, WIDTH, HEIGHT);
        }
    }

    private static void ensure() {
        if (farTex != null) {
            return;
        }
        farTex = layer(true);
        midTex = layer(true);
        paintFar(farTex.getPixels());
        paintMid(midTex.getPixels());
        farTex.upload();
        midTex.upload();
        farTex.setFilter(false, false);
        midTex.setFilter(false, false);

        shadeTex = new DynamicTexture(256, 1, true);
        var shade = shadeTex.getPixels();
        for (int x = 0; x < 256; x++) {
            int alpha = 188 - x * 188 / 255;
            shade.setPixelRGBA(x, 0, FastColor.ABGR32.color(alpha, 0, 0, 0));
        }
        shadeTex.upload();
        shadeTex.setFilter(true, false);

        var textures = Minecraft.getInstance().getTextureManager();
        textures.register(FAR_ID, farTex);
        textures.register(MID_ID, midTex);
        textures.register(SHADE_ID, shadeTex);
    }

    private static DynamicTexture layer(boolean clear) {
        return new DynamicTexture(WIDTH, HEIGHT, clear);
    }

    private static void paintFar(NativeImage img) {
        int horizon = 46 * CELL;
        for (int y = 0; y < HEIGHT; y++) {
            float t = Math.min(1f, y / (float) horizon);
            int gray = lerp(7, 18, t);
            if (y >= horizon) {
                gray = 12;
            }
            for (int x = 0; x < WIDTH; x++) {
                put(img, x, y, gray, 255);
            }
        }
        int[] hills = new int[] {
            2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 6, 5, 5, 4, 4, 3,
            3, 2, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7, 7, 7, 6, 6,
            5, 5, 4, 4, 3, 3, 2, 2, 2, 3, 4, 4, 5, 5, 6, 6,
            6, 5, 5, 4, 3, 3, 2, 2, 3, 4, 5, 5, 6, 6, 5, 4,
            4, 3, 3, 2, 2, 2, 3, 4, 4, 5, 6, 6, 7, 7, 6, 5,
            5, 4, 4, 3, 3, 2, 2, 3, 3, 4, 5, 5, 6, 6, 5, 4,
            3, 3, 2, 2, 2, 3, 4, 4, 5, 5, 4, 4, 3, 3, 2, 2,
            2, 3, 3, 4, 4, 5, 5, 6, 6, 5, 4, 4, 3, 3, 2, 2
        };
        for (int col = 0; col < COLS; col++) {
            int top = 46 - hills[col];
            fillCells(img, col, top, 1, hills[col], 30, 30, 30);
        }
    }

    private static void paintMid(NativeImage img) {
        for (int col = 0; col < COLS; col++) {
            int top = (col % 5 == 0) ? 34 : 40;
            block(img, col, GROUND, top, top + 16, top - 12);
            flat(img, col, GROUND + 1, 22);
        }
        int under = (GROUND + 2) * CELL;
        for (int y = under; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                put(img, x, y, 16, 255);
            }
        }

        house(img, 4, 6, 4, false);
        tree(img, 13);
        house(img, 17, 5, 3, false);
        fence(img, 24, 32);
        house(img, 34, 8, 5, true);
        tree(img, 45);
        tower(img, 49, 8);
        house(img, 55, 6, 4, false);
        tree(img, 64);
        house(img, 68, 7, 4, false);
        fence(img, 78, 86);
        house(img, 88, 5, 3, false);
        tree(img, 96);
        house(img, 100, 7, 4, true);
        tower(img, 110, 7);
        house(img, 116, 5, 3, false);
        tree(img, 124);
    }

    private static void house(NativeImage img, int x, int w, int h, boolean ruined) {
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                if (ruined && dy >= h - 1 && dx >= w - 2) {
                    continue;
                }
                block(img, x + dx, GROUND - 1 - dy, 58, 76, 38);
            }
        }
        int roofY = GROUND - h - 1;
        for (int dx = 0; dx < w; dx++) {
            if (ruined && dx >= w - 2) {
                continue;
            }
            block(img, x + dx, roofY, 44, 58, 30);
        }
        if (w >= 6 && !ruined) {
            for (int dx = 1; dx < w - 1; dx++) {
                block(img, x + dx, roofY - 1, 44, 58, 30);
            }
        }
        int door = x + (w / 2);
        flat(img, door, GROUND - 1, 16);
        if (h >= 3) {
            flat(img, door, GROUND - 2, 16);
        }
        if (h >= 4) {
            int winY = GROUND - 3;
            if (door != x + 1) {
                flat(img, x + 1, winY, 14);
            }
            if (door != x + w - 2) {
                flat(img, x + w - 2, winY, 14);
            }
        }
    }

    private static void tower(NativeImage img, int x, int h) {
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < 3; dx++) {
                block(img, x + dx, GROUND - 1 - dy, 52, 68, 34);
            }
            if (dy >= 2 && (dy % 2) == 0) {
                flat(img, x + 1, GROUND - 1 - dy, 12);
            }
        }
        for (int dx = 0; dx < 3; dx++) {
            block(img, x + dx, GROUND - h - 1, 40, 52, 26);
        }
    }

    private static void tree(NativeImage img, int x) {
        flat(img, x, GROUND - 1, 34);
        flat(img, x, GROUND - 2, 34);
        flat(img, x, GROUND - 3, 34);
        for (int dx = -1; dx <= 1; dx++) {
            block(img, x + dx, GROUND - 4, 40, 50, 28);
            block(img, x + dx, GROUND - 5, 40, 50, 28);
        }
        block(img, x, GROUND - 6, 40, 50, 28);
    }

    private static void fence(NativeImage img, int x0, int x1) {
        for (int x = x0; x <= x1; x += 2) {
            flat(img, x, GROUND - 1, 46);
            flat(img, x, GROUND - 2, 46);
        }
        int y1 = (GROUND - 1) * CELL + 3;
        int y2 = (GROUND - 2) * CELL + 3;
        hline(img, x0 * CELL + CELL / 2, x1 * CELL + CELL / 2, y1, 46);
        hline(img, x0 * CELL + CELL / 2, x1 * CELL + CELL / 2, y2, 46);
    }

    private static void block(NativeImage img, int bx, int by, int face, int light, int shade) {
        if (bx < 0 || by < 0 || bx >= COLS || by >= ROWS) {
            return;
        }
        int x0 = bx * CELL;
        int y0 = by * CELL;
        for (int dy = 0; dy < CELL; dy++) {
            for (int dx = 0; dx < CELL; dx++) {
                int gray = face;
                if (dy == 0 || dx == 0) {
                    gray = light;
                }
                if (dy == CELL - 1 || dx == CELL - 1) {
                    gray = shade;
                }
                put(img, x0 + dx, y0 + dy, gray, 255);
            }
        }
    }

    private static void flat(NativeImage img, int bx, int by, int gray) {
        fillCells(img, bx, by, 1, 1, gray, gray, gray);
    }

    private static void fillCells(NativeImage img, int bx, int by, int bw, int bh, int face, int light, int shade) {
        for (int dy = 0; dy < bh; dy++) {
            for (int dx = 0; dx < bw; dx++) {
                block(img, bx + dx, by + dy, face, light, shade);
            }
        }
    }

    private static void hline(NativeImage img, int x0, int x1, int y, int gray) {
        if (y < 0 || y + 1 >= HEIGHT) {
            return;
        }
        int from = Math.max(0, Math.min(x0, x1));
        int to = Math.min(WIDTH - 1, Math.max(x0, x1));
        for (int x = from; x <= to; x++) {
            put(img, x, y, gray, 255);
            put(img, x, y + 1, gray, 255);
        }
    }

    private static void put(NativeImage img, int x, int y, int gray, int alpha) {
        img.setPixelRGBA(x, y, FastColor.ABGR32.color(alpha, gray, gray, gray));
    }

    private static int lerp(int a, int b, float t) {
        return a + Math.round((b - a) * t);
    }
}
