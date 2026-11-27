package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Клиент: полоска слотов «бирки улик» в правом нижнем углу.
 * Каждый занятый быстрый слот (и выбранный, даже пустой) висит картонной биркой на крученой бечёвке:
 * слева бечёвка завязана узлом со свинцовой пломбой, справа намотана на ржавый гвоздь с паутиной.
 * На бирке предмет, «№N» и количество чернилами. Выбранная висит ровно, ниже и светлее, остальные темнее и
 * чуть повёрнуты. При смене предмета бирки коротко покачиваются.
 * Текстуры: tools/assets/make_tags.py, нарисованы вдвое крупнее и рисуются в масштабе 0,5.
 */
final class EvidenceStrip {
    private static final ResourceLocation TAG = Vhs.rl("textures/gui/evidence_tag.png");
    private static final ResourceLocation TWINE = Vhs.rl("textures/gui/evidence_twine.png");
    private static final ResourceLocation PARTS = Vhs.rl("textures/gui/evidence_parts.png");

    /** Размеры в пикселях текстуры (на экране вдвое мельче). */
    private static final int TAG_W = 64;
    private static final int TAG_H = 84;
    private static final int GAP = 10;
    private static final int SAG = 10;
    /** Нитка от бечёвки до люверса и насколько ниже висит выбранная бирка. */
    private static final int THREAD = 6;
    private static final int DROP = 10;
    /** Люверс в текстуре бирки. */
    private static final int EYELET_Y = 9;
    private static final int INK = 0x221D18;

    private static final float[] TILT = {3f, -2f, 2.5f, -3f, 1.5f, -2.5f, 2f, -1.5f, 3f};

    private static int lastSelected = -1;
    private static long swayAt;

    private EvidenceStrip() {}

    static boolean ready() {
        return Vhs.has(TAG) && Vhs.has(TWINE) && Vhs.has(PARTS);
    }

    /** Нарисовать полоску. Правый край width - 8, низ около height - 6 (в пикселях интерфейса). */
    static void draw(GuiGraphics g, Minecraft mc, int width, int height) {
        Inventory inv = mc.player.getInventory();
        int n = 0;
        for (int i = 0; i < 9; i++) {
            if (!inv.getItem(i).isEmpty() || i == inv.selected) n++;
        }
        if (inv.selected != lastSelected) {
            if (lastSelected >= 0) swayAt = System.currentTimeMillis();
            lastSelected = inv.selected;
        }
        float swayT = (System.currentTimeMillis() - swayAt) / 1000f;
        float sway = swayT < 1.4f ? (float) Math.sin(swayT * 11f) * 4f * (1f - swayT / 1.4f) : 0f;

        // Всё ниже в пикселях текстуры: левый конец бечёвки (узел с пломбой) в L, гвоздь в R.
        int tagsW = n * (TAG_W + GAP) - GAP;
        int left = 22;
        int right = left + tagsW + 12;
        int artW = right + 10;
        int artH = SAG + THREAD + DROP + TAG_H + 4;
        float originX = width - 8 - artW / 2f;
        float originY = height - 6 - artH / 2f;

        g.pose().pushPose();
        g.pose().translate(originX, originY, 0f);
        g.pose().scale(0.5f, 0.5f, 1f);

        drawTwine(g, left, right);
        // Пломба на узле слева, гвоздь с паутиной справа.
        Vhs.blitAlpha(g, PARTS, left - 8, -2, 16, 48, 48, 0, 16, 48, 64, 48, 0.85f);
        Vhs.blitAlpha(g, PARTS, right - 8, -7, 16, 16, 0, 0, 16, 16, 64, 48, 0.9f);
        Vhs.blitAlpha(g, PARTS, right - 24, -13, 32, 32, 16, 0, 32, 32, 64, 48, 0.8f);

        int x = left + 12;
        int shown = 0;
        Font font = mc.font;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            boolean selected = i == inv.selected;
            if (s.isEmpty() && !selected) continue;
            int cx = x + TAG_W / 2;
            int hook = sagAt(cx, left, right);
            float tilt = selected ? sway * 0.6f : TILT[i] + sway * (shown % 2 == 0 ? 1f : -1f);
            int top = hook + THREAD + (selected ? DROP : 0);
            drawThread(g, cx, hook, top + EYELET_Y - 3);
            drawTag(g, font, s, i, selected, cx, top, tilt);
            x += TAG_W + GAP;
            shown++;
        }
        g.pose().popPose();

        ItemStack held = inv.getItem(inv.selected);
        if (!held.isEmpty()) {
            String name = held.getHoverName().getString();
            Vhs.plain(g, name, width - 8 - Vhs.width(name), Math.round(originY) - 14, 0xC8000000 | Vhs.BONE);
        }
    }

    private static int sagAt(int x, int left, int right) {
        float u = Mth.clamp((x - left) / (float) (right - left), 0f, 1f);
        return Math.round(SAG * 4f * u * (1f - u));
    }

    /** Провисающая бечёвка: по столбцу кусочек текстуры 1x2, под ней мягкая тень. */
    private static void drawTwine(GuiGraphics g, int left, int right) {
        for (int x = left; x <= right; x++) {
            int y = sagAt(x, left, right);
            g.fill(x + 2, y + 4, x + 3, y + 6, 0x40000000);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int x = left; x <= right; x++) {
            int y = sagAt(x, left, right);
            g.blit(TWINE, x, y, 1, 2, (float) Math.floorMod(x, 64), 3f, 1, 2, 64, 8);
        }
        RenderSystem.disableBlend();
    }

    /** Нитка от бечёвки до люверса с узелком. */
    private static void drawThread(GuiGraphics g, int cx, int from, int to) {
        g.fill(cx, from, cx + 1, to, 0xFF605644);
        g.fill(cx + 1, from, cx + 2, to, 0xFF3C362C);
        g.fill(cx - 2, from, cx + 4, from + 3, 0xFF50483A);
    }

    private static void drawTag(GuiGraphics g, Font font, ItemStack s, int slot, boolean selected, int cx, int top, float tilt) {
        float k = selected ? 0.78f : 0.58f;
        g.pose().pushPose();
        // Бирка качается вокруг люверса.
        g.pose().translate(cx, top + EYELET_Y, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(tilt));
        g.pose().translate(-TAG_W / 2f, -EYELET_Y, 0f);

        g.fill(4, 6, TAG_W + 4, TAG_H + 6, 0x50000000);              // тень бирки
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(k, k, k, 1f);
        int variant = slot % 4;
        g.blit(TAG, 0, 0, TAG_W, TAG_H, variant * TAG_W, 0, TAG_W, TAG_H, TAG_W * 4, TAG_H);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        if (!s.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(8f, 14f, 0f);
            g.pose().scale(3f, 3f, 1f);
            g.renderItem(s, 0, 0);
            g.pose().popPose();
            // Невыбранные бирки в тени: предмет притушен вместе с картоном.
            if (!selected) {
                g.pose().pushPose();
                g.pose().translate(0f, 0f, 200f);
                g.fill(8, 14, 56, 62, 0x70000000);
                g.pose().popPose();
            }
        }
        // Номер и количество чернилами, поверх предмета.
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 210f);
        // На притушенном картоне чернила почти чёрные, иначе «№N» и «xN» сливаются с бумагой.
        int ink = selected ? 0xE6000000 | INK : 0xFF0E0B08;
        g.drawString(font, Vhs.text("№" + (slot + 1)), 7, 64, ink, false);
        if (s.getCount() > 1) {
            String c = "x" + s.getCount();
            g.drawString(font, Vhs.text(c), TAG_W - 8 - font.width(Vhs.text(c)), 64, ink, false);
        }
        g.pose().popPose();
        g.pose().popPose();
    }
}
