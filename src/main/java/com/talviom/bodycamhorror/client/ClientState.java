package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.net.HudStatePacket;

import java.util.Random;

/** Клиент: последнее состояние HUD с сервера и короткие реплики персонажа при пороге страха. */
public final class ClientState {
    public static float fear;
    public static float battery = 1f;
    public static boolean hasFlashlight;
    public static boolean flashlightOn;
    public static boolean flashlightLit;
    public static boolean inMatch;
    public static boolean dark;
    public static boolean combat;

    /** Реплика персонажа и когда она появилась. */
    public static String comment = "";
    public static long commentAt;

    private static final float[] THRESHOLDS = {35f, 60f, 85f};
    private static final boolean[] ARMED = {true, true, true};
    private static final Random RND = new Random();

    private ClientState() {}

    public static void onHud(HudStatePacket m) {
        fear = m.fear;
        battery = m.battery;
        hasFlashlight = m.hasFlashlight;
        flashlightOn = m.flashlightOn;
        flashlightLit = m.flashlightLit;
        inMatch = m.inMatch;
        dark = m.dark;
        combat = m.combat;

        if (!inMatch) {
            for (int i = 0; i < ARMED.length; i++) ARMED[i] = true;
            return;
        }
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (ARMED[i] && fear >= THRESHOLDS[i]) {
                ARMED[i] = false;
                say(i);
            } else if (!ARMED[i] && fear < THRESHOLDS[i] - 15f) {
                ARMED[i] = true;
            }
        }
    }

    private static void say(int tier) {
        String[] lines = Vhs.trList(switch (tier) {
            case 0 -> dark ? "bh.fear.dark" : "bh.fear.uneasy";
            case 1 -> "bh.fear.scared";
            default -> "bh.fear.panic";
        });
        comment = lines[RND.nextInt(lines.length)];
        commentAt = System.currentTimeMillis();
    }
}
