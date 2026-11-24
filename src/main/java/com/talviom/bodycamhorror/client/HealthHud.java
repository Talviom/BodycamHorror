package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.gear.BandageItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиент: здоровье и перевязка вместо ванильных сердечек.
 * <ul>
 *   <li>Здоровье: тонкая пробирка с кровью в левом нижнем углу, без цифр. Уровень крови показывает,
 *       сколько здоровья осталось; при низком здоровье кровь темнеет и неровно мерцает. Потерянная
 *       кровь ещё мгновение видна бледным следом на стекле, потом гаснет. Чат сдвинут вправо, чтобы
 *       пробирка его не закрывала.</li>
 *   <li>Под прицелом, пока держишь ПКМ с бинтом: «ПЕРЕВЯЗКА» и тонкая полоска хода.</li>
 * </ul>
 * Текстура пробирки: tools/assets/make_hud.py (без неё рисуется простой запасной вариант).
 */
@Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HealthHud {
    private static final ResourceLocation VIAL = Vhs.rl("textures/gui/vial.png");
    /** Текстура: три слоя 20x88 в ряд (стекло сзади, кровь, стекло спереди), рисуется вдвое мельче. */
    private static final int TEX_W = 20;
    private static final int TEX_H = 88;
    private static final int SHEET_W = TEX_W * 3;
    /** Внутренность пробирки в текстуре: строки 7..87, ширина 4..15. */
    private static final int INNER_TOP = 7;
    private static final int INNER_X0 = 4;
    private static final int INNER_X1 = 16;
    /** Размер пробирки на экране в пикселях интерфейса: тонкая, «как палец». */
    public static final int WIDTH = TEX_W / 2;
    public static final int HEIGHT = TEX_H / 2;
    /** Насколько сдвинуть чат вправо, пока видна пробирка. */
    private static final int CHAT_SHIFT = 8 + WIDTH + 6;

    private static final float LOW = 0.35f;
    private static final float BANDAGING_TEXT_SCALE = 1.6f;

    /** Что сейчас показано (плавно догоняет настоящее здоровье) и след потери. */
    private static float shown = -1f;
    private static float lost = 0f;
    private static long lostAt;

    private HealthHud() {}

    /** Видна ли пробирка сейчас (те же условия, что у FearHud и drawHealth). */
    static boolean visible() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && !mc.options.hideGui && BhConfig.HIDE_VANILLA_HUD.get()
                && !mc.player.isSpectator() && !ClientHud.creativeOrSpectator();
    }

    /**
     * Пробирка с кровью, левый край x, низ bottom. Вызывается из FearHud.
     * @return true, если пробирка нарисована
     */
    static boolean drawHealth(GuiGraphics g, int x, int bottom) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible()) return false;
        float hp = Mth.clamp(mc.player.getHealth() / Math.max(1f, mc.player.getMaxHealth()), 0f, 1f);
        long now = System.currentTimeMillis();
        if (shown < 0f) shown = hp;
        if (hp < shown - 0.001f) {
            // Удар: уровень падает сразу, а потерянная кровь ещё видна бледным следом.
            lost = Math.max(lost, shown);
            lostAt = now;
            shown = hp;
        } else {
            shown = Math.min(hp, shown + 0.02f); // лечение наполняет пробирку постепенно
        }
        float lostAlpha = lost > shown ? 1f - Mth.clamp((now - lostAt - 350) / 650f, 0f, 1f) : 0f;
        if (lostAlpha <= 0f) lost = 0f;

        // Кровь темнеет при низком здоровье и неровно мерцает, как умирающая лампа.
        float dark = 0.65f + 0.35f * Mth.clamp((shown - 0.1f) / 0.5f, 0f, 1f);
        float alpha = 1f;
        if (shown < LOW) {
            double t = now / (140.0 + shown * 400.0);
            alpha = 0.6f + 0.4f * (float) Math.abs(Math.sin(t) * Math.sin(t * 0.37 + 1.3));
        }

        int y = bottom - HEIGHT;
        if (!Vhs.has(VIAL)) {
            g.fill(x, y, x + WIDTH, bottom, 0xB01C1A17);
            int fill = Math.round((HEIGHT - 4) * shown);
            g.fill(x + 2, bottom - 1 - fill, x + WIDTH - 2, bottom - 1, ((int) (alpha * 230) << 24) | Vhs.DRIED_BLOOD);
            return true;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(0.5f, 0.5f, 1f);
        Vhs.blitAlpha(g, VIAL, 0, 0, TEX_W, TEX_H, 0, 0, TEX_W, TEX_H, SHEET_W, TEX_H, 1f);
        int top = fillTop(shown);
        int lostTop = fillTop(lost);
        if (lostAlpha > 0f && lostTop < top) {
            layer(g, lostTop, top - lostTop, 1f, 1f, 1f, 0.4f * lostAlpha);
        }
        if (top < TEX_H) {
            layer(g, top, TEX_H - top, dark, dark, dark, alpha);
            // Поверхность крови: светлая полоска мениска.
            int a = Math.round(alpha * 200) << 24;
            g.fill(INNER_X0, top, INNER_X1, top + 1, a | 0x963428);
        }
        Vhs.blitAlpha(g, VIAL, 0, 0, TEX_W, TEX_H, TEX_W * 2, 0, TEX_W, TEX_H, SHEET_W, TEX_H, 1f);
        g.pose().popPose();
        return true;
    }

    /** Верхняя строка крови в текстуре для доли здоровья f. */
    private static int fillTop(float f) {
        return TEX_H - Math.round((TEX_H - INNER_TOP) * Mth.clamp(f, 0f, 1f));
    }

    /** Кусок слоя крови со строки v высотой rows, окрашенный (r, gr, b) с прозрачностью alpha. */
    private static void layer(GuiGraphics g, int v, int rows, float r, float gr, float b, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, gr, b, alpha);
        g.blit(VIAL, 0, v, TEX_W, rows, TEX_W, v, TEX_W, rows, SHEET_W, TEX_H);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Чат сдвигается вправо, чтобы пробирка в левом нижнем углу не закрывала сообщения. */
    @SubscribeEvent
    public static void onVialChatShift(CustomizeGuiOverlayEvent.Chat e) {
        if (visible()) e.setPosX(e.getPosX() + CHAT_SHIFT);
    }

    /** «ПЕРЕВЯЗКА» под прицелом и полоска хода, пока держишь ПКМ с бинтом. Отпустил: перевязка отменена. */
    @SubscribeEvent
    public static void onBandagingRender(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) return;
        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;
        if (!mc.player.isUsingItem() || !(mc.player.getUseItem().getItem() instanceof BandageItem)) return;
        float p = BandageClient.progress(mc.player, e.getPartialTick());
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth();
        int h = e.getWindow().getGuiScaledHeight();
        int cx = w / 2;
        int y = h / 2 + 18;
        // С 0.8.2 крупнее: надпись в 1,6 раза, полоса хода 120x4 (на 1920x1080 прежние были еле видны).
        Vhs.ghostCentered(g, Vhs.title(Vhs.tr("bh.hud.bandaging")), cx, y, 0xD8000000 | Vhs.BONE, BANDAGING_TEXT_SCALE, 0.2f);
        int bw = 120;
        int bh = 4;
        int bx = cx - bw / 2;
        int by = y + Math.round(9 * BANDAGING_TEXT_SCALE) + 5;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xA0000000 | 0x1C1A17);
        g.fill(bx, by, bx + Math.round(bw * p), by + bh, 0xD8000000 | Vhs.BONE);
        // Тусклый двойник полоски, как у надписей: плёнка чуть «плывёт».
        float gx = (float) Math.sin(System.currentTimeMillis() / 900.0) * 1.5f + 1.5f;
        g.fill(bx + Math.round(gx), by + 2, bx + Math.round(gx) + Math.round(bw * p), by + bh + 2, 0x30000000 | Vhs.BONE);
    }
}
