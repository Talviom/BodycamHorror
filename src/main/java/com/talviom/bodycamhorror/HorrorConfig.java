package com.talviom.bodycamhorror;

import net.minecraftforge.common.ForgeConfigSpec;

/** Настройки хоррора и саботажа: config/bodycamhorror-horror.toml и config/bodycamhorror-fx.toml. */
public final class HorrorConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue QUIET_SECONDS;
    public static final ForgeConfigSpec.IntValue DARK_LIGHT_LEVEL;
    public static final ForgeConfigSpec.DoubleValue FEAR_DARK_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue FEAR_ALONE_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue FEAR_CALM_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue FEAR_RELIEF_PER_SECOND;
    public static final ForgeConfigSpec.IntValue COMBAT_SECONDS;
    public static final ForgeConfigSpec.IntValue FLICKER_RADIUS;
    public static final ForgeConfigSpec.BooleanValue HEARTBEAT;

    public static final ForgeConfigSpec.IntValue BATTERY_SECONDS;
    public static final ForgeConfigSpec.IntValue RECHARGE_SECONDS;
    public static final ForgeConfigSpec.IntValue BEAM_RANGE;

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
        FLICKER_RADIUS = b.comment("Radius of lamp flicker around a player").defineInRange("flickerRadius", 16, 4, 64);
        HEARTBEAT = b.comment("Heartbeat sound when fear is high").define("heartbeat", true);
        b.pop();
        // Раздел переименован в v0.5.2, чтобы у всех подхватились новые значения баланса, а не старые из файла.
        b.push("fearBalance");
        DARK_LIGHT_LEVEL = b.comment("Light level at eye height that counts as darkness (0..15)").defineInRange("darkLightLevel", 2, 0, 15);
        // 0.9: ключ с новым именем, чтобы у всех подхватилось 3.0, а не старое 2.0 из файла.
        FEAR_DARK_PER_SECOND = b.comment("Fear gained per second in darkness without a flashlight").defineInRange("darkPerSecond09", 3.0, 0.0, 100.0);
        FEAR_ALONE_PER_SECOND = b.comment("Fear gained per second with no teammate nearby").defineInRange("alonePerSecond", 0.6, 0.0, 100.0);
        FEAR_CALM_PER_SECOND = b.comment("Fear lost per second in lamp light (the flashlight no longer calms) or next to a teammate").defineInRange("calmPerSecond", 2.5, 0.0, 100.0);
        FEAR_RELIEF_PER_SECOND = b.comment("After a scare fear stops growing for a while and drops by this much per second")
                .defineInRange("reliefPerSecond", 1.5, 0.0, 100.0);
        COMBAT_SECONDS = b.comment("No horror events for this long after a player hits or gets hit").defineInRange("combatSeconds", 8, 0, 120);
        b.pop();
        b.push("flashlight");
        BATTERY_SECONDS = b.comment("Seconds a full battery lasts").defineInRange("batterySeconds", 90, 5, 3600);
        RECHARGE_SECONDS = b.comment("Seconds to recharge an empty battery while the flashlight is off").defineInRange("rechargeSeconds", 45, 1, 3600);
        BEAM_RANGE = b.comment("Beam length in blocks").defineInRange("beamRange", 10, 3, 24);
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
