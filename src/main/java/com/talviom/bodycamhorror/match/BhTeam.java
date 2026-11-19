package com.talviom.bodycamhorror.match;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum BhTeam {
    ALPHA("alpha", "Alpha", ChatFormatting.BLUE),
    BRAVO("bravo", "Bravo", ChatFormatting.RED);

    public final String id;
    public final String displayName;
    public final ChatFormatting color;

    BhTeam(String id, String displayName, ChatFormatting color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }

    /** Название команды на языке игрока (lang bh.team.alpha / bh.team.bravo). */
    public Component title() {
        return Component.translatable("bh.team." + id);
    }

    public String scoreboardName() {
        return "bh_" + id;
    }

    public BhTeam other() {
        return this == ALPHA ? BRAVO : ALPHA;
    }

    public static BhTeam byId(String id) {
        for (BhTeam t : values()) if (t.id.equalsIgnoreCase(id)) return t;
        return null;
    }
}
