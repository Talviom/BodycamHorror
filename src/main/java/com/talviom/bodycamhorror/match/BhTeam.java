package com.talviom.bodycamhorror.match;

import net.minecraft.ChatFormatting;

public enum BhTeam {
    ALPHA("alpha", "Альфа", ChatFormatting.BLUE),
    BRAVO("bravo", "Браво", ChatFormatting.RED);

    public final String id;
    public final String displayName;
    public final ChatFormatting color;

    BhTeam(String id, String displayName, ChatFormatting color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
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
