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
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> START_KIT_BRAVO;
    public static final ForgeConfigSpec.IntValue TENANT_WAKE_SECONDS;
    public static final ForgeConfigSpec.IntValue SWAP_SIDES_AFTER_ROUNDS;
    public static final ForgeConfigSpec.BooleanValue LIMIT_INVENTORY;
    public static final ForgeConfigSpec.BooleanValue SLOT_STRIP;

    public static final ForgeConfigSpec.IntValue TAPE_DELIVER_SECONDS;
    public static final ForgeConfigSpec.DoubleValue TAPE_EXIT_RADIUS;
    public static final ForgeConfigSpec.DoubleValue TAPE_CARRIER_FEAR;
    public static final ForgeConfigSpec.IntValue LOBBY_RETURN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue FORCE_SKINS;
    public static final ForgeConfigSpec.BooleanValue CUSTOM_MENU;
    public static final ForgeConfigSpec.BooleanValue LOBBY_NOTES;

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
        TENANT_WAKE_SECONDS = b.comment("Seconds after a round goes live before the Tenant wakes in the darkest room (0 = no Tenant in matches)")
                .defineInRange("tenantWakeSeconds", 30, 0, 600);
        START_KIT = b.comment(
                        "Items every ALPHA player gets at the start of each round: item_id;count;nbt (nbt optional).",
                        "Both teams start with the same class of gear (9 mm pistol, 2 magazines, flashlight, bandage).",
                        "Empty list = players start with nothing and rely on loot.")
                .defineListAllowEmpty(List.of("startKitAlpha"), () -> List.of(
                        "bodycamhorror:flashlight;1",
                        "tacz:modern_kinetic_gun;1;{GunId:\"tacz:glock_17\",GunFireMode:\"SEMI\",GunCurrentAmmoCount:17,HasBulletInBarrel:1b}",
                        "tacz:ammo;34;{AmmoId:\"tacz:9mm\"}",
                        "bodycamhorror:bandage;1"
                ), o -> o instanceof String);
        START_KIT_BRAVO = b.comment(
                        "Items every BRAVO player gets at the start of each round. Beretta M9A4: same 9 mm, 17 rounds,",
                        "damage and rate of fire as Alpha's Glock 17.")
                .defineListAllowEmpty(List.of("startKitBravo"), () -> List.of(
                        "bodycamhorror:flashlight;1",
                        "tacz:modern_kinetic_gun;1;{GunId:\"tacz:m9a4\",GunFireMode:\"SEMI\",GunCurrentAmmoCount:17,HasBulletInBarrel:1b}",
                        "tacz:ammo;34;{AmmoId:\"tacz:9mm\"}",
                        "bodycamhorror:bandage;1"
                ), o -> o instanceof String);
        b.pop();

        b.push("loot");
        LOOT_CHANCE = b.comment("Chance that a loot point spawns something each round").defineInRange("lootChance", 0.6, 0.0, 1.0);
        LOOT_TABLE = b.comment(
                        "Loot entries: weight;item_id;count;nbt (nbt optional).",
                        "On maps with mirrored loot points (/bh arena build facility) each mirrored pair gets the same item,",
                        "so both halves of the map are equal, and every gun (tacz:modern_kinetic_gun) appears at most once per side.",
                        "TACZ guns: tacz:modern_kinetic_gun with GunId, GunFireMode and GunCurrentAmmoCount; ammo: tacz:ammo with AmmoId.")
                .defineListAllowEmpty(List.of("lootItems"), () -> List.of(
                        "30;tacz:ammo;17;{AmmoId:\"tacz:9mm\"}",
                        "14;bodycamhorror:bandage;1",
                        "12;bodycamhorror:battery;1",
                        "5;tacz:ammo;5;{AmmoId:\"tacz:12g\"}",
                        "4;tacz:modern_kinetic_gun;1;{GunId:\"tacz:hk_mp5a5\",GunFireMode:\"AUTO\",GunCurrentAmmoCount:30,HasBulletInBarrel:1b}",
                        "3;tacz:modern_kinetic_gun;1;{GunId:\"tacz:m870\",GunFireMode:\"SEMI\",GunCurrentAmmoCount:5,HasBulletInBarrel:1b}"
                ), o -> o instanceof String);
        b.pop();

        b.push("tape");
        TAPE_DELIVER_SECONDS = b.comment("Seconds the tape carrier must stay at their side's exit to win the round")
                .defineInRange("deliverSeconds", 3, 0, 30);
        TAPE_EXIT_RADIUS = b.comment("How close (blocks) to the exit point the carrier must stand").defineInRange("exitRadius", 2.5, 1.0, 10.0);
        TAPE_CARRIER_FEAR = b.comment("Fear the tape carrier gains every second").defineInRange("carrierFearPerSecond", 0.6, 0.0, 10.0);
        b.pop();

        b.push("lobby");
        LOBBY_RETURN_SECONDS = b.comment("Seconds after the match result before everyone is sent back to the lobby")
                .defineInRange("returnSeconds", 8, 0, 60);
        b.pop();
        COMMON_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("hud");
        HIDE_VANILLA_HUD = c.comment("Hide vanilla hotbar, hearts, hunger, xp (not in creative)").define("hideVanillaHud", true);
        BLOCK_INVENTORY = c.comment("Replace the vanilla inventory screen with the bodycam inventory (not in creative)").define("blockInventory", true);
        BODYCAM_OVERLAY = c.comment("Draw the REC / timecode bodycam overlay").define("bodycamOverlay", true);
        SLOT_STRIP = c.comment("Show carried items as a strip in the bottom right corner").define("slotStrip", true);
        FORCE_SKINS = c.comment("Draw every player with the mod's military skin of their team instead of their own skin")
                .define("forceSkins", true);
        CUSTOM_MENU = c.comment("Replace the vanilla title screen, pause menu and loading screens with the bodycam ones")
                .define("customMenu", true);
        LOBBY_NOTES = c.comment("Show the field notes with the controls while no match is running")
                .define("lobbyNotes", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private BhConfig() {}
}
