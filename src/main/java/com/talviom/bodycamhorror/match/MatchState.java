package com.talviom.bodycamhorror.match;

import java.util.Locale;

import net.minecraft.network.chat.Component;

public enum MatchState {
    /** Матч не идёт, мир в обычном режиме. */
    LOBBY,
    /** Игроки заморожены на спавне, лут разложен. */
    PREP,
    /** Раунд идёт. */
    LIVE,
    /** Раунд закончился, пауза перед следующим. */
    ROUND_END;

    /** Название состояния на языке игрока (bh.state.*). */
    public Component label() {
        return Component.translatable("bh.state." + name().toLowerCase(Locale.ROOT));
    }
}
