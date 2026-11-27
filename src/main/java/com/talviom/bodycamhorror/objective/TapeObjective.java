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
        removeTapeBlocks(level);
        for (ItemEntity e : tapesOnGround(level)) e.discard();
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
            p.displayClientMessage(text(Component.translatable("bh.tape.find"), BONE), true);
            if (firstRound) {
                p.sendSystemMessage(text(Component.translatable("bh.tape.intro", seconds), ASH));
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
        removeTapeBlocks(level);
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
            holder.displayClientMessage(bar(Component.translatable("bh.tape.getting_out"), deliverTicks, need), true);
        } else {
            deliverTicks = 0;
            if (ticks % 20 == 0) {
                int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                holder.displayClientMessage(text(Component.translatable("bh.tape.carrying", dist), BONE), true);
            }
        }
        return null;
    }

    // ---------- события игроков ----------

    /** Кто-то поднял кассету. */
    public void onPickedUp(ServerPlayer p) {
        if (!active) return;
        com.talviom.bodycamhorror.match.MatchReport.tapePickedUp(p.server, p);
        ServerLevel level = p.serverLevel();
        play(level, p.position(), ModSounds.TAPE_PICKUP.get(), 1.0f, 1.0f);
        // Только ему: кассета будто что-то шепчет.
        p.playNotifySound(ModSounds.WHISPER.get(), SoundSource.AMBIENT, 0.5f, 0.85f);
        FearSystem.INSTANCE.add(p, 8f);
        p.displayClientMessage(text(Component.translatable("bh.tape.picked"), BONE), true);
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
        for (BlockPos p : tapeBlocks(level)) {
            play(level, Vec3.atCenterOf(p), ModSounds.TAPE_HISS.get(), 0.6f, 0.85f + rnd.nextFloat() * 0.2f);
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
        return new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    /**
     * 0.9: кассета появляется 3D-блоком. Если в точке стоит мебель (или другой твёрдый блок), кассета ложится сверху,
     * иначе на пол. Если положить некуда, она остаётся предметом на земле, как раньше.
     */
    public static void spawnTape(ServerLevel level, Vec3 at) {
        BlockPos spot = findSpot(level, BlockPos.containing(at));
        if (spot != null) {
            placeTapeBlock(level, spot, level.getRandom().nextInt(4));
            return;
        }
        ItemEntity e = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(ModBlocks.TAPE.get()), 0, 0, 0);
        protect(e);
        level.addFreshEntity(e);
    }

    /** Свободное место для кассеты: сама точка, если там воздух над опорой, иначе на опору ниже или над мебелью. */
    static BlockPos findSpot(ServerLevel level, BlockPos start) {
        BlockPos p = start;
        // В точке стоит мебель: кассета на ней (поднимаемся, пока не найдём воздух, не выше двух блоков).
        for (int i = 0; i < 3 && !free(level, p); i++) p = p.above();
        if (!free(level, p)) return null;
        // Висит в воздухе (уронили в прыжке, с галереи): опускаем до пола, не ниже 12 блоков.
        for (int i = 0; i < 12 && free(level, p.below()) && p.getY() > level.getMinBuildHeight(); i++) p = p.below();
        return free(level, p.below()) ? null : p;
    }

    private static boolean free(ServerLevel level, BlockPos p) {
        net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
        return s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty());
    }

    public static void placeTapeBlock(ServerLevel level, BlockPos pos, int turn) {
        net.minecraft.core.Direction f = net.minecraft.core.Direction.from2DDataValue(turn & 3);
        level.setBlock(pos, ModBlocks.TAPE_BLOCK.get().defaultBlockState().setValue(TapeBlock.FACING, f), 3);
        TAPE_BLOCKS.add(new TapeSpot(level.dimension(), pos.immutable()));
    }

    private record TapeSpot(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, BlockPos pos) {}

    /** Где сейчас лежат кассеты-блоки (чтобы шипеть ими и убрать их в конце раунда). */
    private static final java.util.Set<TapeSpot> TAPE_BLOCKS = new java.util.HashSet<>();

    private static List<BlockPos> tapeBlocks(ServerLevel level) {
        List<BlockPos> out = new java.util.ArrayList<>();
        for (java.util.Iterator<TapeSpot> it = TAPE_BLOCKS.iterator(); it.hasNext(); ) {
            TapeSpot s = it.next();
            if (s.dim() != level.dimension()) continue;
            if (level.isLoaded(s.pos()) && !level.getBlockState(s.pos()).is(ModBlocks.TAPE_BLOCK.get())) {
                it.remove();
                continue;
            }
            out.add(s.pos());
        }
        return out;
    }

    private static void removeTapeBlocks(ServerLevel level) {
        for (BlockPos p : tapeBlocks(level)) {
            if (level.getBlockState(p).is(ModBlocks.TAPE_BLOCK.get())) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        TAPE_BLOCKS.removeIf(s -> s.dim() == level.dimension());
    }

    /** ПКМ или F по кассете-блоку. */
    public void pickUpBlock(ServerPlayer p, BlockPos pos) {
        MatchManager m = MatchManager.INSTANCE;
        if (m.running() && (!m.isAlive(p) || m.state() != com.talviom.bodycamhorror.match.MatchState.LIVE)) return;
        ServerLevel level = p.serverLevel();
        if (!level.getBlockState(pos).is(ModBlocks.TAPE_BLOCK.get())) return;
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        TAPE_BLOCKS.remove(new TapeSpot(level.dimension(), pos));
        ItemStack tape = new ItemStack(ModBlocks.TAPE.get());
        if (!p.getInventory().add(tape)) {
            // Места нет: кассета сразу ложится обратно на то же место.
            placeTapeBlock(level, pos, 0);
            p.displayClientMessage(text(Component.translatable("bh.tape.no_room"), ASH), true);
            return;
        }
        if (m.running()) onPickedUp(p);
    }

    /**
     * 0.9: кассета-предмет попала в мир (выпала у погибшего, выброшена на G, игрок вышел): вместо плоского
     * предмета на полу ложится та же 3D-кассета. @return true, если предмет заменён блоком
     */
    public static boolean dropAsBlock(ServerLevel level, ItemEntity item) {
        BlockPos spot = findSpot(level, BlockPos.containing(item.position()));
        if (spot == null) return false;
        placeTapeBlock(level, spot, level.getRandom().nextInt(4));
        return true;
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

    /** Строка из lang-файла (Component.translatable) тем же цветом. */
    public static Component text(Component c, int rgb) {
        return c.copy().withStyle(st -> st.withColor(TextColor.fromRgb(rgb)));
    }

    private static Component bar(Component label, int value, int max) {
        int filled = Math.min(10, value * 10 / max);
        return text(label.copy().append("  " + "■".repeat(filled) + "□".repeat(10 - filled)), BONE);
    }
}
