package com.talviom.bodycamhorror.client.menu;

import com.talviom.bodycamhorror.client.Vhs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import javax.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Клиент: экран смерти вне матча (в матче погибший сразу становится наблюдателем, этого экрана там нет).
 * Вместо красного градиента и «You Died!» кадр обрывается: SIGNAL LOST печатной машинкой, причина пепельным,
 * зловещая строка запёкшейся кровью. Кнопки и их поведение ванильные (возрождение, выход в меню,
 * хардкор), только нарисованы поверх нашего кадра; сами кнопки тёмные из widgets.png.
 */
public final class BhDeathScreen extends DeathScreen {
    private static final String[] OMENS = {
            "THE CAMERA KEPT RECORDING.",
            "SOMEONE PICKED IT UP.",
            "THE FOOTAGE ENDS HERE.",
            "IT DIDN'T STOP WATCHING.",
    };

    private final long openedAt = System.currentTimeMillis();
    private final String cause;
    private final String omen = OMENS[(int) (Math.random() * OMENS.length)];

    public BhDeathScreen(@Nullable Component causeOfDeath, boolean hardcore) {
        super(causeOfDeath, hardcore);
        this.cause = causeOfDeath == null ? "" : causeOfDeath.getString();
    }

    /**
     * Причина смерти из ванильного экрана. Поле у него закрытое, а имя в собранной игре другое,
     * поэтому ищем его по типу: до init() из полей-надписей заполнено только оно (счёт появляется в init()).
     */
    @Nullable
    static Component causeOf(DeathScreen vanilla) {
        try {
            for (Field f : DeathScreen.class.getDeclaredFields()) {
                if (f.getType() != Component.class || Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v = f.get(vanilla);
                if (v != null) return (Component) v;
            }
        } catch (Exception ignored) {
            // нет причины, покажем без неё
        }
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long t = System.currentTimeMillis() - openedAt;
        float in = Mth.clamp(t / 600f, 0f, 1f);

        g.fill(0, 0, width, height, ((int) (0xE6 * in)) << 24);
        Vhs.noise(g, 0, 0, width, height, 0.1f + 0.12f * in);
        Vhs.vignette(g, width, height, in);
        Vhs.flicker(g, width, height);

        int cx = width / 2;
        int top = Math.max(16, height / 4 - 34);
        Vhs.ghostCentered(g, Vhs.title("SIGNAL LOST"), cx, top, MenuOsd.a(235, in) | Vhs.BONE, 3f, 0.45f);
        int y = top + 36;
        if (!cause.isEmpty()) {
            Vhs.ghostCentered(g, Vhs.title(cause.toUpperCase()), cx, y, MenuOsd.a(190, in) | Vhs.ASH, 1f, 0.15f);
            y += 14;
        }
        Vhs.ghostCentered(g, Vhs.title(omen), cx, y, MenuOsd.a(200, in) | Vhs.DRIED_BLOOD, 1f, 0.2f);

        MenuOsd.draw(g, width, height, in, null);
        for (Renderable r : renderables) r.render(g, mouseX, mouseY, partialTick);
    }
}
