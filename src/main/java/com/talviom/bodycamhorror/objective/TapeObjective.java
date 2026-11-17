package com.talviom.bodycamhorror.objective;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.ModBlocks;
import com.talviom.bodycamhorror.ModSounds;
import com.talviom.bodycamhorror.horror.FearSystem;
import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MapData;
import com.talviom.bodycamhorror.match.MatchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Цель раунда «Вынести кассету».
 *
 * В начале раунда кассета появляется в случайной точке карты (/bh tape add) и тихо шипит,
 * поэтому её можно найти в темноте на слух. Подобрать её может любой живой игрок.
 * При смерти она падает вместе со всем снаряжением. Команда, чей игрок донесёт кассету до выхода
 * своей стороны (/bh exit set) и простоит там несколько секунд, выигрывает раунд.
 * Перебить другую команду по-прежнему тоже победа.
 *
 * Тот, кто несёт кассету, слышен: она шипит и у него в руках, а страх у него растёт.
 * Если на карте нет выходов или точек кассеты, режим выключен и раунд идёт как раньше.
 */
public final class TapeObjective {
    public static final TapeObjective INSTANCE = new TapeObjective();

    public static final int BONE = 0xC4BCAA;
    public static final int ASH = 0x787266;

    private static final int SCAN_EVERY = 5;
    private static final int HISS_EVERY = 50;
    private static final int CARRIER_HISS_EVERY = 90;

    private final Random rnd = new Random();
    private boolean active;
    private int ticks;
    private UUID carrier;
    private int deliverTicks;

    private TapeObjective() {}

    public boolean active() {
        return active;
    }

    /** На карте есть выходы обеих сторон и хотя бы одна точка, где появляется кассета. */
    public static boolean mapReady(ServerLevel level) {
        MapData m = MapData.get(level);
        return !m.tapePoints().isEmpty() && m.exit(BhTeam.ALPHA) != null && m.exit(BhTeam.BRAVO) != null;
    }

    public static boolean isTape(ItemStack s) {
        return !s.isEmpty() && s.is(ModBlocks.TAPE.get());
    }

    // ---------- раунд ----------

    /** Вызывается после того, как игроки расставлены по спавнам и лут разложен. */
    public void onRoundStart(MinecraftServer server) {
        reset();
        ServerLevel level = server.overworld();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) removeTapes(p);
        if (!mapReady(level)) return;
        active = true;
        spawnTape(level, randomPoint(level));
    }

    /** Старт раунда (после заморозки): подсказать, что делать. */
    public void announce(MinecraftServer server, boolean firstRound) {
        if (!active) return;
        int seconds = BhConfig.TAPE_DELIVER_SECONDS.get();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!MatchManager.INSTANCE.isAlive(p)) continue;
            p.displayClientMessage(text("FIND THE TAPE. GET IT OUT THROUGH YOUR EXIT.", BONE), true);
            if (firstRound) {
                p.sendSystemMessage(text("The tape is somewhere in the dark. Listen for the hiss. Carry it to the exit"
                        + " behind your spawn and stay there " + seconds + " s. Or leave no one alive.", ASH));
            }
        }
    }

    public void onRoundEnd() {
        active = false;
        carrier = null;
        deliverTicks = 0;
    }

    /** Матч закончился или остановлен: кассеты ни у кого не остаётся. */
    public void onMatchEnd(MinecraftServer server) {
        onRoundEnd();
        ServerLevel level = server.overworld();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) removeTapes(p);
        for (ItemEntity e : tapesOnGround(level)) e.discard();
    }

    public void reset() {
        active = false;
        ticks = 0;
        carrier = null;
        deliverTicks = 0;
    }

    /**
     * Каждый тик живого раунда.
     * @return команда, которая вынесла кассету, или null
     */
    public BhTeam tick(MinecraftServer server) {
        if (!active) return null;
        ticks++;
        ServerLevel level = server.overworld();
        if (ticks % HISS_EVERY == 0) hissOnGround(level);
        if (ticks % SCAN_EVERY != 0) return null;

        ServerPlayer holder = findCarrier(server);
        if (holder == null) {
            carrier = null;
            deliverTicks = 0;
            rescueFallen(level);
            return null;
        }
        if (!holder.getUUID().equals(carrier)) {
            carrier = holder.getUUID();
            deliverTicks = 0;
        }

        MatchManager match = MatchManager.INSTANCE;
        BhTeam team = match.teamOf(holder);
        if (team == null) return null;
        BlockPos exit = MapData.get(level).exit(match.sideOf(team));
        if (exit == null) return null;

        double dx = holder.getX() - (exit.getX() + 0.5);
        double dz = holder.getZ() - (exit.getZ() + 0.5);
        double r = BhConfig.TAPE_EXIT_RADIUS.get();
        boolean atExit = dx * dx + dz * dz <= r * r && Math.abs(holder.getY() - exit.getY()) <= 2.5;

        if (ticks % 20 == 0) FearSystem.INSTANCE.add(holder, BhConfig.TAPE_CARRIER_FEAR.get().floatValue());
        if (ticks % CARRIER_HISS_EVERY == 0) {
            play(level, holder.position(), ModSounds.TAPE_HISS.get(), 0.7f, 0.8f + rnd.nextFloat() * 0.2f);
        }

        int need = Math.max(1, BhConfig.TAPE_DELIVER_SECONDS.get() * 20);
        if (atExit) {
            deliverTicks += SCAN_EVERY;
            if (deliverTicks >= need) {
                removeTapes(holder);
                play(level, holder.position(), ModSounds.TAPE_OUT.get(), 2.5f, 1.0f);
                onRoundEnd();
                return team;
            }
            holder.displayClientMessage(bar("GETTING THE TAPE OUT", deliverTicks, need), true);
        } else {
            deliverTicks = 0;
            if (ticks % 20 == 0) {
                int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                holder.displayClientMessage(text("YOU HAVE THE TAPE  ·  EXIT " + dist + " M", BONE), true);
            }
        }
        return null;
    }

    // ---------- события игроков ----------

    /** Кто-то поднял кассету. */
    public void onPickedUp(ServerPlayer p) {
        if (!active) return;
        ServerLevel level = p.serverLevel();
        play(level, p.position(), ModSounds.TAPE_PICKUP.get(), 1.0f, 1.0f);
        // Только ему: кассета будто что-то шепчет.
        p.playNotifySound(ModSounds.WHISPER.get(), SoundSource.AMBIENT, 0.5f, 0.85f);
        FearSystem.INSTANCE.add(p, 8f);
        p.displayClientMessage(text("YOU HAVE THE TAPE. GET IT OUT.", BONE), true);
    }

    /** Игрок вышел с сервера: кассета остаётся лежать там, где он стоял. */
    public void onPlayerLeft(ServerPlayer p) {
        if (!active || !hasTape(p)) return;
        removeTapes(p);
        spawnTape(p.serverLevel(), p.position());
    }

    // ---------- вспомогательное ----------

    private ServerPlayer findCarrier(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (MatchManager.INSTANCE.isAlive(p) && hasTape(p)) return p;
        }
        return null;
    }

    public static boolean hasTape(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isTape(inv.getItem(i))) return true;
        }
        return isTape(p.containerMenu.getCarried());
    }

    public static void removeTapes(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isTape(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        }
        if (isTape(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
    }

    private static List<? extends ItemEntity> tapesOnGround(ServerLevel level) {
        return level.getEntities(EntityTypeTest.forClass(ItemEntity.class), e -> isTape(e.getItem()));
    }

    private void hissOnGround(ServerLevel level) {
        for (ItemEntity e : tapesOnGround(level)) {
            play(level, e.position(), ModSounds.TAPE_HISS.get(), 0.6f, 0.85f + rnd.nextFloat() * 0.2f);
        }
    }

    /** Кассета упала за край карты: она «находится» в другой точке. */
    private void rescueFallen(ServerLevel level) {
        for (ItemEntity e : tapesOnGround(level)) {
            if (e.getY() < level.getMinBuildHeight() + 1) {
                Vec3 to = randomPoint(level);
                e.teleportTo(to.x, to.y, to.z);
                e.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    private Vec3 randomPoint(ServerLevel level) {
        List<BlockPos> points = MapData.get(level).tapePoints();
        BlockPos p = points.get(rnd.nextInt(points.size()));
        return new Vec3(p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5);
    }

    public static ItemEntity spawnTape(ServerLevel level, Vec3 at) {
        ItemEntity e = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(ModBlocks.TAPE.get()), 0, 0, 0);
        protect(e);
        level.addFreshEntity(e);
        return e;
    }

    /** Кассета на земле не исчезает со временем и не ломается. */
    public static void protect(ItemEntity e) {
        e.setUnlimitedLifetime();
        e.setInvulnerable(true);
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch);
    }

    public static Component text(String s, int rgb) {
        return Component.literal(s).withStyle(st -> st.withColor(TextColor.fromRgb(rgb)));
    }

    private static Component bar(String label, int value, int max) {
        int filled = Math.min(10, value * 10 / max);
        return text(label + "  " + "■".repeat(filled) + "□".repeat(10 - filled), BONE);
    }
}
