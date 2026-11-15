package com.talviom.bodycamhorror.horror;

public enum HorrorEvent {
    /** Шаги за спиной, слышит только цель. */
    FOOTSTEPS("footsteps", 5),
    /** Шёпот и гул пещеры, слышит только цель. */
    WHISPER("whisper", 4),
    /** Стук в дверь или скрип рядом, слышит только цель. */
    KNOCK("knock", 3),
    /** Лампы вокруг цели мигают и ненадолго гаснут, видят все. */
    FLICKER("flicker", 4),
    /** Помехи на бодикаме цели. */
    GLITCH("glitch", 3),
    /** Помехи в рации команды цели: часть слов пропадает. */
    RADIO("radio", 2);

    public final String id;
    public final int weight;

    HorrorEvent(String id, int weight) {
        this.id = id;
        this.weight = weight;
    }
}
