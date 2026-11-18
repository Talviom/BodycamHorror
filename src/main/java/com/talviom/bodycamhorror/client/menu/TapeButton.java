package com.talviom.bodycamhorror.client.menu;

import com.talviom.bodycamhorror.client.Vhs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.function.Supplier;

/**
 * Клиент: пункт меню в стиле плёнки (главное меню, меню паузы). Просто текст печатной машинкой:
 * обычный выцветший пепел, под мышью или с клавиатуры светлее, с мигающей кареткой.
 * Никаких ярких рамок. Неактивный пункт цвета сажи.
 */
public final class TapeButton extends Button {
    private final String label;
    private final Supplier<Float> alpha;

    /**
     * @param cx    центр пункта по горизонтали
     * @param y     верх строки текста
     * @param alpha прозрачность надписи 0..1 в текущем кадре (для проявления из черноты)
     */
    public TapeButton(int cx, int y, String label, Supplier<Float> alpha, OnPress press) {
        super(cx - (Vhs.width(Vhs.title(label)) + 24) / 2, y - 2, Vhs.width(Vhs.title(label)) + 24, 13,
                Vhs.title(label), press, DEFAULT_NARRATION);
        this.label = label;
        this.alpha = alpha;
    }

    private int a(int max) {
        return Mth.clamp((int) (max * alpha.get()), 4, 255) << 24;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (alpha.get() <= 0.01f) return;
        int cx = getX() + getWidth() / 2;
        int y = getY() + 2;
        if (!active) {
            Vhs.ghostCentered(g, Vhs.title(label), cx, y, a(110) | Vhs.SOOT, 1f, 0f);
            return;
        }
        if (isHoveredOrFocused()) {
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), a(70));
            Vhs.ghostCentered(g, Vhs.title(label), cx, y, a(240) | Vhs.BONE, 1f, 0.3f);
            if ((System.currentTimeMillis() / 450) % 2 == 0) {
                int w = Vhs.width(Vhs.title(label));
                Vhs.ghost(g, Vhs.title("_"), cx + w / 2 + 2, y, a(200) | Vhs.BONE, 1f, 0f);
            }
        } else {
            Vhs.ghostCentered(g, Vhs.title(label), cx, y, a(175) | Vhs.ASH, 1f, 0f);
        }
    }

    /** Глухой щелчок кассетной деки вместо звонкого ванильного. */
    @Override
    public void playDownSound(SoundManager sounds) {
        sounds.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8f));
    }
}
