package com.talviom.bodycamhorror.match;

public enum MatchState {
    /** Матч не идёт, мир в обычном режиме. */
    LOBBY,
    /** Игроки заморожены на спавне, лут разложен. */
    PREP,
    /** Раунд идёт. */
    LIVE,
    /** Раунд закончился, пауза перед следующим. */
    ROUND_END
}
