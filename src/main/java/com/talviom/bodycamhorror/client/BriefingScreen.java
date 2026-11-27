package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Лобби 1.0: папка инструктажа на весь экран. Жёлтый лист в полутьме, машинописная памятка, поверх записки от руки.
 * Листы: обложка, 8 тем (как на стендах в лобби), вырванный последний лист. Стрелки, A/D, колёсико или клик листают.
 */
public class BriefingScreen extends Screen {
    private static final ResourceLocation PAGE = Vhs.rl("textures/gui/briefing_page.png");
    private static final int INK = 0xFF2A2620;
    private static final int PENCIL = 0xFF23283A;
    private static final int FADED = 0xFF5A544A;

    private record Page(String title, String[] body, String[] notes, boolean cover) {}

    private final List<Page> pages = new ArrayList<>();
    private int index;

    public BriefingScreen() {
        this(0);
    }

    public BriefingScreen(int page) {
        super(Component.translatable("item.bodycamhorror.briefing_folder"));
        pages.add(new Page(Vhs.tr("bh.brief.folder.cover"), Vhs.trList("bh.brief.folder.cover_sub"), new String[0], true));
        for (String t : BriefText.TOPICS) pages.add(new Page(BriefText.title(t), BriefText.body(t), BriefText.notes(t), false));
        pages.add(new Page(Vhs.tr("bh.brief.folder.last_title"), Vhs.trList("bh.brief.folder.last"), BriefText.notes("missing"), false));
        index = Math.max(0, Math.min(pages.size() - 1, page));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new BriefingScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void turn(int d) {
        int next = Math.max(0, Math.min(pages.size() - 1, index + d));
        if (next == index) return;
        index = next;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.9f + (float) Math.random() * 0.2f, 0.6f));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            turn(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_PAGE_UP) {
            turn(-1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        turn(delta < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        turn(x > width / 2.0 ? 1 : -1);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xE0080706);
        Font font = Minecraft.getInstance().font;

        // Лист: пропорции 256 x 320, по высоте экрана с полями.
        int ph = Math.min(height - 40, 480);
        int pw = ph * 256 / 320;
        int px = (width - pw) / 2;
        int py = (height - ph) / 2 - 6;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(0.86f, 0.84f, 0.8f, 1f);
        g.blit(PAGE, px, py, pw, ph, 0, 0, 256, 320, 256, 320);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        Page page = pages.get(index);
        float k = ph / 320f;
        int left = px + Math.round(34 * k);
        int textW = pw - Math.round(52 * k);
        int y = py + Math.round(26 * k);

        if (page.cover()) {
            float ts = 2.2f * k / 1.5f;
            Component t = Vhs.title(page.title());
            Vhs.ghostCentered(g, t, px + pw / 2, py + ph / 3, INK, ts, 0f);
            int yy = py + ph / 3 + Math.round(36 * k);
            for (String s : page.body()) {
                for (FormattedCharSequence seq : font.split(Component.literal(s), textW)) {
                    g.drawString(font, seq, px + pw / 2 - font.width(seq) / 2, yy, FADED, false);
                    yy += 11;
                }
                yy += 4;
            }
        } else {
            float ts = 1.4f * Math.max(1f, k * 0.9f);
            g.pose().pushPose();
            g.pose().translate(left, y, 0);
            g.pose().scale(ts, ts, 1f);
            g.drawString(font, Vhs.title(page.title()), 0, 0, INK, false);
            g.pose().popPose();
            y += Math.round(14 * ts) + 6;
            for (String s : page.body()) {
                for (FormattedCharSequence seq : font.split(Component.literal(s), textW)) {
                    g.drawString(font, seq, left, y, INK, false);
                    y += 11;
                }
                y += 5;
            }
            // Записки от руки: приколоты внизу листа, чуть криво.
            int ny = Math.max(y + 8, py + ph - Math.round(110 * k));
            int i = 0;
            for (String id : page.notes()) {
                String note = BriefText.note(id);
                List<FormattedCharSequence> lines = font.split(Component.literal(note), Math.round(textW * 0.8f));
                int boxH = lines.size() * 11 + 10;
                if (ny + boxH > py + ph - 14) break;
                int nx = left + (i % 2 == 0 ? 6 : Math.round(textW * 0.16f));
                g.pose().pushPose();
                g.pose().translate(nx, ny, 0);
                g.pose().mulPose(Axis.ZP.rotationDegrees(i % 2 == 0 ? -1.6f : 1.2f));
                g.fill(-4, -4, Math.round(textW * 0.8f) + 6, boxH - 2, 0xC8CFC6AA);
                g.fill(-4, -4, Math.round(textW * 0.8f) + 6, -2, 0x60000000);
                int ly = 1;
                for (FormattedCharSequence seq : lines) {
                    g.drawString(font, seq, 0, ly, PENCIL, false);
                    ly += 11;
                }
                g.pose().popPose();
                ny += boxH + 6;
                i++;
            }
        }

        // Поверх: зерно плёнки, тёмные края, неровное дыхание света.
        Vhs.noise(g, 0, 0, width, height, 0.10f);
        Vhs.vignette(g, width, height, 0.75f);
        Vhs.flicker(g, width, height);

        String counter = Vhs.tr("bh.brief.screen.page", index + 1, pages.size());
        Vhs.ghostCentered(g, Vhs.text(counter), width / 2, py + ph + 6, 0xFF787266, 1f, 0f);
        Vhs.plain(g, Vhs.tr("bh.brief.screen.hint"), 8, height - 12, 0xFF4E4A42);
    }
}
