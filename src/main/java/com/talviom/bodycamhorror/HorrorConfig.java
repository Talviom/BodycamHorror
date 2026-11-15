package com.talviom.bodycamhorror;

import net.minecraftforge.common.ForgeConfigSpec;

/** Настройки хоррора и саботажа: config/bodycamhorror-horror.toml и config/bodycamhorror-fx.toml. */
public final class HorrorConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue QUIET_SECONDS;
    public static final ForgeConfigSpec.IntValue MIN_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue FLICKER_RADIUS;
    public static final ForgeConfigSpec.BooleanValue HEARTBEAT;

    public static final ForgeConfigSpec.IntValue BREAKER_RADIUS;
    public static final ForgeConfigSpec.IntValue SWITCH_COOLDOWN_SECONDS;

    public static final ForgeConfigSpec.BooleanValue HEALTH_FEEDBACK;
    public static final ForgeConfigSpec.BooleanValue VIGNETTE;
    public static final ForgeConfigSpec.BooleanValue GLITCH;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("horror");
        ENABLED = b.comment("Horror director on/off").define("enabled", true);
        QUIET_SECONDS = b.comment("Seconds of silence at the start of each round").defineInRange("quietSeconds", 20, 0, 600);
        MIN_INTERVAL_SECONDS = b.comment("Shortest pause between events (late in the round)").defineInRange("minIntervalSeconds", 10, 2, 600);
        MAX_INTERVAL_SECONDS = b.comment("Longest pause between events (early in the round)").defineInRange("maxIntervalSeconds", 30, 2, 600);
        FLICKER_RADIUS = b.comment("Radius of lamp flicker around a player").defineInRange("flickerRadius", 16, 4, 64);
        HEARTBEAT = b.comment("Heartbeat for wounded players and the last one alive").define("heartbeat", true);
        b.pop();
        b.push("sabotage");
        BREAKER_RADIUS = b.comment("Radius (blocks) of lamps controlled by one breaker box").defineInRange("breakerRadius", 24, 4, 128);
        SWITCH_COOLDOWN_SECONDS = b.comment("Cooldown before a breaker or generator can be toggled again").defineInRange("switchCooldownSeconds", 15, 0, 600);
        b.pop();
        SERVER_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("fx");
        HEALTH_FEEDBACK = c.comment("Show damage as a red screen edge (hearts are hidden)").define("healthFeedback", true);
        VIGNETTE = c.comment("Dark screen edges. Leave off if the shader already has a vignette").define("vignette", false);
        GLITCH = c.comment("Bodycam signal interference effect").define("glitch", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private HorrorConfig() {}
}
