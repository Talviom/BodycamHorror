package com.talviom.bodycamhorror;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Настройки мода. COMMON лежит в config/bodycamhorror-common.toml, CLIENT в config/bodycamhorror-client.toml.
 */
public final class BhConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.IntValue PREP_SECONDS;
    public static final ForgeConfigSpec.IntValue ROUND_SECONDS;
    public static final ForgeConfigSpec.IntValue ROUND_END_SECONDS;
    public static final ForgeConfigSpec.IntValue ROUNDS_TO_WIN;
    public static final ForgeConfigSpec.DoubleValue LOOT_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LOOT_TABLE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> START_KIT;
    public static final ForgeConfigSpec.IntValue SWAP_SIDES_AFTER_ROUNDS;
    public static final ForgeConfigSpec.BooleanValue LIMIT_INVENTORY;
    public static final ForgeConfigSpec.BooleanValue SLOT_STRIP;

    public static final ForgeConfigSpec.BooleanValue HIDE_VANILLA_HUD;
    public static final ForgeConfigSpec.BooleanValue BLOCK_INVENTORY;
    public static final ForgeConfigSpec.BooleanValue BODYCAM_OVERLAY;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("match");
        PREP_SECONDS = b.comment("Seconds players are frozen at spawn before a round").defineInRange("prepSeconds", 10, 0, 120);
        ROUND_SECONDS = b.comment("Round length in seconds").defineInRange("roundSeconds", 180, 30, 1800);
        ROUND_END_SECONDS = b.comment("Pause between rounds in seconds").defineInRange("roundEndSeconds", 6, 1, 60);
        ROUNDS_TO_WIN = b.comment("Round wins needed to win the match").defineInRange("roundsToWin", 5, 1, 30);
        SWAP_SIDES_AFTER_ROUNDS = b.comment("Teams swap spawn sides every N rounds (0 = never)").defineInRange("swapSidesAfterRounds", 3, 0, 30);
        LIMIT_INVENTORY = b.comment("Players carry only 20 slots (2 rows of 10); the rest of the inventory is locked (not in creative)")
                .define("limitInventory", true);
        START_KIT = b.comment(
                        "Items every player gets at the start of each round: item_id;count;nbt (nbt optional).",
                        "Empty list = players start with nothing and rely on loot.")
                .defineListAllowEmpty(List.of("startKit"), () -> List.of(
                        "bodycamhorror:flashlight;1",
                        "tacz:modern_kinetic_gun;1;{GunId:\"tacz:glock_17\",GunFireMode:\"SEMI\",GunCurrentAmmoCount:17,HasBulletInBarrel:1b}",
                        "tacz:ammo;34;{AmmoId:\"tacz:9mm\"}"
                ), o -> o instanceof String);
        b.pop();

        b.push("loot");
        LOOT_CHANCE = b.comment("Chance that a loot point spawns something each round").defineInRange("lootChance", 0.6, 0.0, 1.0);
        LOOT_TABLE = b.comment(
                        "Loot entries: weight;item_id;count;nbt (nbt optional).",
                        "TACZ guns: tacz:modern_kinetic_gun with GunId, GunFireMode and GunCurrentAmmoCount; ammo: tacz:ammo with AmmoId.")
                .defineListAllowEmpty(List.of("lootTable"), () -> List.of(
                        "6;tacz:modern_kinetic_gun;1;{GunId:\"tacz:ak47\",GunFireMode:\"AUTO\",GunCurrentAmmoCount:30,HasBulletInBarrel:1b}",
                        "6;tacz:modern_kinetic_gun;1;{GunId:\"tacz:m4a1\",GunFireMode:\"AUTO\",GunCurrentAmmoCount:30,HasBulletInBarrel:1b}",
                        "3;tacz:modern_kinetic_gun;1;{GunId:\"tacz:m870\",GunFireMode:\"SEMI\",GunCurrentAmmoCount:5,HasBulletInBarrel:1b}",
                        "4;bodycamhorror:flashlight;1",
                        "14;tacz:ammo;30;{AmmoId:\"tacz:9mm\"}",
                        "10;tacz:ammo;30;{AmmoId:\"tacz:762x39\"}",
                        "10;tacz:ammo;30;{AmmoId:\"tacz:556x45\"}",
                        "5;tacz:ammo;8;{AmmoId:\"tacz:12g\"}",
                        "8;minecraft:golden_apple;1"
                ), o -> o instanceof String);
        b.pop();
        COMMON_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("hud");
        HIDE_VANILLA_HUD = c.comment("Hide vanilla hotbar, hearts, hunger, xp (not in creative)").define("hideVanillaHud", true);
        BLOCK_INVENTORY = c.comment("Replace the vanilla inventory screen with the bodycam inventory (not in creative)").define("blockInventory", true);
        BODYCAM_OVERLAY = c.comment("Draw the REC / timecode bodycam overlay").define("bodycamOverlay", true);
        SLOT_STRIP = c.comment("Show carried items as a strip in the bottom right corner").define("slotStrip", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private BhConfig() {}
}
