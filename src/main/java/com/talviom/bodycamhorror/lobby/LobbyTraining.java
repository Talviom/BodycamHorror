package com.talviom.bodycamhorror.lobby;

import com.talviom.bodycamhorror.ModBlocks;
import com.talviom.bodycamhorror.ModSounds;
import com.talviom.bodycamhorror.flashlight.Flashlight;
import com.talviom.bodycamhorror.gear.ModGear;
import com.talviom.bodycamhorror.match.MapData;
import com.talviom.bodycamhorror.match.MatchManager;
import com.talviom.bodycamhorror.objective.TapeObjective;
import com.talviom.bodycamhorror.sabotage.PowerSwitchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Лобби 1.0: всё живое в пункте сбора, пока матч не идёт.
 * <ul>
 *   <li>Папка инструктажа у каждого (только в лобби: на старте раунда инвентарь очищается).</li>
 *   <li>Выдача учебного набора (фонарик, батарейка, учебный бинт) по ПКМ.</li>
 *   <li>Учебная кассета шипит; донёс до учебного выхода и простоял 3 секунды, она возвращается на стол.</li>
 *   <li>Учебный щиток гасит две лампы угла; пока он выключен, горит красная аварийная лампа угла.</li>
 *   <li>Одна лампа дневного света неровно мигает и гудит, рация дежурного иногда шипит.</li>
 *   <li>Пульт «Запись»: хозяин мира жмёт кнопку, всем идёт отсчёт 5 секунд, потом старт матча.</li>
 * </ul>
 * Точки берутся из MapData (lobbyPoint), их ставит LobbyBuilder.
 */
public final class LobbyTraining {
    private static final int DELIVER_TICKS = 60;
    private static final double EXIT_RADIUS = 2.5;

    /** Аварийные лампы, которые сейчас должны гореть независимо от генератора (лампа учебного угла). */
    private static final Set<BlockPos> FORCED_EMERGENCY = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> DELIVER = new HashMap<>();
    private static final Set<UUID> HINTED = new HashSet<>();
    private static long tick;
    private static long nextRadio;
    private static int recCountdown = -1;

    private LobbyTraining() {}

    /** Для EmergencyLampBlock: гореть ли лампе, даже если генератор работает. */
    public static boolean forcedLit(BlockPos pos) {
        return FORCED_EMERGENCY.contains(pos);
    }

    /** Матч пошёл: подсказки про папку покажем снова, когда все вернутся. */
    public static void onMatchRunning() {
        HINTED.clear();
        DELIVER.clear();
        recCountdown = -1;
    }

    /** Каждый тик, пока матч не идёт (из LobbyManager). */
    public static void tick(MinecraftServer server) {
        tick++;
        ServerLevel level = server.overworld();
        MapData map = MapData.get(level);
        if (map.lobby() == null) return;
        if (tick % 40 == 0) folders(server);
        if (tick % 10 == 0) emergency(level, map);
        flicker(level, map);
        ambience(level, map);
        tape(server, level, map);
        countdown(server);
    }

    // ---------- папка ----------

    private static void folders(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isCreative() || p.isSpectator() || !LobbyManager.inLobby(p)) continue;
            if (p.getInventory().contains(new ItemStack(ModGear.BRIEFING_FOLDER.get()))) continue;
            ItemStack folder = new ItemStack(ModGear.BRIEFING_FOLDER.get());
            // В первую ячейку, чтобы папка сразу была в руке.
            if (p.getInventory().getItem(0).isEmpty()) p.getInventory().setItem(0, folder);
            else p.getInventory().add(folder);
            if (HINTED.add(p.getUUID())) {
                p.sendSystemMessage(TapeObjective.text(Component.translatable("bh.lobby.folder_hint"), TapeObjective.ASH));
            }
        }
    }

    // ---------- выдача ----------

    public static void giveKit(ServerPlayer p) {
        if (Flashlight.hasFlashlight(p)) {
            p.displayClientMessage(TapeObjective.text(Component.translatable("bh.lobby.kit_have"), TapeObjective.ASH), true);
            return;
        }
        p.getInventory().add(new ItemStack(ModBlocks.FLASHLIGHT.get()));
        p.getInventory().add(new ItemStack(ModGear.BATTERY.get()));
        ItemStack bandage = new ItemStack(ModGear.BANDAGE.get());
        bandage.getOrCreateTag().putBoolean("bh_training", true);
        p.getInventory().add(bandage);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8f, 0.9f);
        p.displayClientMessage(TapeObjective.text(Component.translatable("bh.lobby.kit_given"), TapeObjective.BONE), true);
    }

    // ---------- щиток угла ----------

    private static void emergency(ServerLevel level, MapData map) {
        BlockPos kitLamp = map.lobbyPoint("emergency_kit");
        if (kitLamp != null) FORCED_EMERGENCY.add(kitLamp);  // лампа над выдачей набора горит всегда
        BlockPos breaker = map.lobbyPoint("breaker");
        BlockPos lamp = map.lobbyPoint("emergency");
        if (breaker == null || lamp == null || !level.isLoaded(breaker)) return;
        BlockState s = level.getBlockState(breaker);
        boolean off = s.getBlock() instanceof PowerSwitchBlock && !s.getValue(PowerSwitchBlock.POWERED);
        if (off) FORCED_EMERGENCY.add(lamp);
        else FORCED_EMERGENCY.remove(lamp);
    }

    // ---------- свет и звук ----------

    /** Свет в лобби ровный (просьба пользователя 2026-11-27): лампа на точке flicker больше не мигает, только горит. */
    private static void flicker(ServerLevel level, MapData map) {
        if (tick % 20 != 0) return;
        BlockPos pos = map.lobbyPoint("flicker");
        if (pos == null || !level.isLoaded(pos)) return;
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof LobbyLampBlock && !s.getValue(LobbyLampBlock.LIT)) level.setBlock(pos, s.setValue(LobbyLampBlock.LIT, true), 3);
    }

    /** Редкие помехи рации на столе дежурного; гудения ламп больше нет. */
    private static void ambience(ServerLevel level, MapData map) {
        RandomSource r = level.getRandom();
        BlockPos radio = map.lobbyPoint("radio");
        if (radio != null && tick >= nextRadio) {
            nextRadio = tick + 900 + r.nextInt(900);
            if (tick > 200) play(level, radio, ModSounds.RADIO_STATIC.get(), 0.2f, 0.85f + r.nextFloat() * 0.2f);
        }
    }

    // ---------- учебный образец (раньше кассета) ----------

    private static void tape(MinecraftServer server, ServerLevel level, MapData map) {
        BlockPos spot = map.lobbyPoint("tape");
        BlockPos exit = map.lobbyPoint("exit");
        if (spot == null || exit == null || !level.isLoaded(spot)) return;
        boolean onTable = level.getBlockState(spot).is(ModBlocks.TAPE_BLOCK.get());
        // 0.9.8: учебный образец изредка бьётся о стекло и стонет (как в матче, только тише), колба при этом проступает.
        if (onTable && tick % 700 == 0) {
            play(level, spot, ModSounds.SPECIMEN_KNOCK.get(), 0.35f, 0.9f + level.getRandom().nextFloat() * 0.15f);
            com.talviom.bodycamhorror.objective.TapeBlock.pulse(level, spot);
        }
        boolean someoneHasIt = false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!LobbyManager.inLobby(p) || !TapeObjective.hasTape(p)) {
                DELIVER.remove(p.getUUID());
                continue;
            }
            someoneHasIt = true;
            double dx = p.getX() - (exit.getX() + 0.5);
            double dz = p.getZ() - (exit.getZ() + 0.5);
            if (dx * dx + dz * dz > EXIT_RADIUS * EXIT_RADIUS) {
                DELIVER.remove(p.getUUID());
                continue;
            }
            int t = DELIVER.merge(p.getUUID(), 1, Integer::sum);
            if (t % 20 == 1) {
                int left = (DELIVER_TICKS - t) / 20 + 1;
                p.displayClientMessage(TapeObjective.text(Component.translatable("bh.lobby.tape_hold", left), TapeObjective.BONE), true);
            }
            if (t >= DELIVER_TICKS) {
                DELIVER.remove(p.getUUID());
                TapeObjective.removeTapes(p);
                TapeObjective.placeTapeBlock(level, spot, 0);
                play(level, p.blockPosition(), ModSounds.SPECIMEN_OUT.get(), 0.8f, 1f);
                p.displayClientMessage(TapeObjective.text(Component.translatable("bh.lobby.tape_done"), TapeObjective.BONE), true);
            }
        }
        // Кассету унесли и бросили где-то, а потом она пропала (вышли из игры с ней): вернуть на стол.
        if (!onTable && !someoneHasIt && tick % 200 == 0 && !tapeBlockInLobby(level, map)) {
            TapeObjective.placeTapeBlock(level, spot, 0);
        }
    }

    private static boolean tapeBlockInLobby(ServerLevel level, MapData map) {
        BlockPos min = map.lobbyPoint("min");
        BlockPos max = map.lobbyPoint("max");
        if (min == null || max == null) return false;
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(p).is(ModBlocks.TAPE_BLOCK.get())) return true;
        }
        return false;
    }

    // ---------- пульт «Запись» ----------

    public static boolean isHost(ServerPlayer p) {
        return p.server.isSingleplayerOwner(p.getGameProfile()) || p.hasPermissions(2);
    }

    public static void pressRecord(ServerPlayer p) {
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, 0.7f);
        if (MatchManager.INSTANCE.running() || recCountdown >= 0) return;
        if (!isHost(p)) {
            p.displayClientMessage(TapeObjective.text(Component.translatable("bh.lobby.host_only"), TapeObjective.ASH), true);
            return;
        }
        recCountdown = 100;
    }

    private static void countdown(MinecraftServer server) {
        if (recCountdown < 0) return;
        if (recCountdown % 20 == 0 && recCountdown > 0) {
            Component msg = TapeObjective.text(Component.translatable("bh.lobby.rec_in", recCountdown / 20), 0x7A1612);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.displayClientMessage(msg, true);
                p.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 0.5f, 0.6f);
            }
        }
        if (recCountdown-- > 0) return;
        recCountdown = -1;
        Component err = MatchManager.INSTANCE.start(server);
        if (err != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (LobbyManager.inLobby(p)) p.displayClientMessage(TapeObjective.text(err, TapeObjective.ASH), true);
            }
        }
    }

    private static void play(ServerLevel level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }
}
