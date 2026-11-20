package com.talviom.bodycamhorror.radio;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 0.9: голос союзника из рации звучит как дешёвая УКВ-рация: полоса 400..2800 Гц, лёгкий хрип и шипение.
 * Работает на клиенте, в потоке голосового чата, над сырым звуком Simple Voice Chat (48 кГц, моно).
 * Без классов Minecraft: просто математика над short[]. Заодно отмечает время, когда последний раз
 * пришёл голос по рации (значок в HUD у слушателя).
 */
public final class RadioVoiceFilter {
    private static final double SR = 48000.0;
    private static final Biquad HIGH = Biquad.highpass(400, 0.7);
    private static final Biquad HIGH2 = Biquad.highpass(400, 0.7);
    private static final Biquad LOW = Biquad.lowpass(2800, 0.7);
    private static final Biquad LOW2 = Biquad.lowpass(2800, 0.7);
    private static volatile long lastHeardMs;

    private RadioVoiceFilter() {}

    /** Сколько миллисекунд назад пришёл голос по рации (большое число, если давно). */
    public static long msSinceHeard() {
        return System.currentTimeMillis() - lastHeardMs;
    }

    public static synchronized short[] apply(short[] in) {
        if (in == null) return null;
        lastHeardMs = System.currentTimeMillis();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        short[] out = new short[in.length];
        for (int i = 0; i < in.length; i++) {
            double x = in[i] / 32768.0;
            x = LOW2.run(LOW.run(HIGH2.run(HIGH.run(x))));
            x = Math.tanh(x * 2.2) * 0.75;
            x += (rnd.nextDouble() - 0.5) * 0.02;
            out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(x * 32767.0)));
        }
        return out;
    }

    /** Простой биквадратный фильтр (RBJ). */
    private static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        static Biquad lowpass(double f, double q) {
            double w = 2 * Math.PI * f / SR, c = Math.cos(w), alpha = Math.sin(w) / (2 * q);
            return new Biquad((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha);
        }

        static Biquad highpass(double f, double q) {
            double w = 2 * Math.PI * f / SR, c = Math.cos(w), alpha = Math.sin(w) / (2 * q);
            return new Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha);
        }

        double run(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }
    }
}
