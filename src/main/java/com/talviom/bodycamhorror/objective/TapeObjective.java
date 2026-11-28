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
 * Цель раунда «Вынести ОБРАЗЕЦ №7» (до 0.9.8 это была кассета; имена классов, id и команды /bh tape остались).
 *
 * В начале раунда колба с существом появляется в случайной точке карты (/bh tape add). Постоянного звука нет:
 * существо раз в 30-40 секунд стучит по стеклу, по этому стуку её и ищут в темноте. Подобрать может любой живой игрок.
 * При смерти несущего колба падает той же 3D-моделью, и существо несколько секунд бьётся о стекло.
 * Команда, чей игрок донесёт колбу до выхода своей стороны (/bh exit set) и простоит там несколько секунд,
 * выигрывает раунд. Перебить другую команду по-прежнему тоже победа.
 *
 * Звук: удар телом о стекло или стон из колбы (на полу раз в 30-40 с, у несущего раз в ~15 с; рядом с Жильцом
 * только удары и громче). Со звуком существо на пару секунд проступает светом, глаз тлеет всегда.
 * Несущий: страх растёт.
 * Жилец чувствует колбу: несущий для него всегда «шумит» (TenantHearing), поэтому охотится он чаще на него.
 * Другие видят колбу у несущего за спиной (SpecimenSync, клиент: SpecimenBackLayer).
 * Если на карте нет выходов или точек, режим выключен и раунд идёт как раньше.
 */
public final class TapeObjective {
    public static final TapeObjective INSTANCE = new TapeObjective();

    public static final int BONE = 0xC4BCAA;
    public static final int ASH = 0x787266;

    private static final int SCAN_EVERY = 5;
    /** Стук на полу и у несущего без Жильца рядом: раз в 30-40 с. */
    private static final int KNOCK_MIN = 600;
    private static final int KNOCK_RAND = 200;
    /** У несущего: раз в ~15 с (решение пользователя 28.11: частые удары раздражали). */
    private static final int CARRIER_KNOCK_MIN = 260;
    private static final int CARRIER_KNOCK_RAND = 80;
    private static final double TENANT_NEAR = 15;

    private final Random rnd = new Random();
    private boolean active;
    private int ticks;
    private UUID carrier;
    private int deliverTicks;
    private int nextGroundKnock;
    private int nextCarrierKnock;

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
        MinecraftServer srv = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (carrier != null && srv != null) SpecimenSync.carrier(srv, null);
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
        // Первый стук скоро после старта, чтобы было за что зацепиться.
        nextGroundKnock = 160 + rnd.nextInt(200);
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
        if (ticks >= nextGroundKnock) {
            nextGroundKnock = ticks + KNOCK_MIN + rnd.nextInt(KNOCK_RAND);
            knockOnGround(level);
        }
        if (ticks % SCAN_EVERY != 0) return null;

        ServerPlayer holder = findCarrier(server);
        if (holder == null) {
            if (carrier != null) SpecimenSync.carrier(server, null);
            carrier = null;
            deliverTicks = 0;
            rescueFallen(level);
            return null;
        }
        if (!holder.getUUID().equals(carrier)) {
            carrier = holder.getUUID();
            deliverTicks = 0;
            nextCarrierKnock = ticks + CARRIER_KNOCK_MIN + rnd.nextInt(CARRIER_KNOCK_RAND);
            SpecimenSync.carrier(server, holder);
        } else if (ticks % 100 == 0) {
            SpecimenSync.carrier(server, holder); // вошедшие позже тоже увидят колбу за спиной
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
        // Жилец чувствует колбу: несущий для него всегда слышен издалека.
        if (ticks % 40 == 0) {
            com.talviom.bodycamhorror.tenant.TenantHearing.noise(level, holder.position(), 1.5f, 32, holder, false, "specimen");
        }
        boolean tenantNear = !level.getEntitiesOfClass(com.talviom.bodycamhorror.tenant.TenantEntity.class,
                holder.getBoundingBox().inflate(TENANT_NEAR)).isEmpty();
        if (ticks >= nextCarrierKnock) {
            nextCarrierKnock = ticks + CARRIER_KNOCK_MIN + rnd.nextInt(CARRIER_KNOCK_RAND);
            // Рядом Жилец: существо бьётся телом о стекло (громче), иначе удар или стон вперемешку.
            knock(level, holder.position(), tenantNear ? 1.2f : 0.8f, holder, tenantNear);
            SpecimenSync.pulse(server, holder);
        }

        int need = Math.max(1, BhConfig.TAPE_DELIVER_SECONDS.get() * 20);
        if (atExit) {
            deliverTicks += SCAN_EVERY;
            if (deliverTicks >= need) {
                removeTapes(holder);
                SpecimenSync.carrier(server, null);
                play(level, holder.position(), ModSounds.SPECIMEN_OUT.get(), 2.5f, 1.0f);
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

    /** Кто-то поднял колбу. */
    public void onPickedUp(ServerPlayer p) {
        if (!active) return;
        com.talviom.bodycamhorror.match.MatchReport.tapePickedUp(p.server, p);
        ServerLevel level = p.serverLevel();
        play(level, p.position(), ModSounds.SPECIMEN_PICKUP.get(), 1.0f, 1.0f);
        // Только ему: будто что-то шепчет из колбы.
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

    private void knockOnGround(ServerLevel level) {
        for (ItemEntity e : tapesOnGround(level)) knock(level, e.position(), 0.8f, null, false);
        for (BlockPos p : tapeBlocks(level)) {
            knock(level, Vec3.atCenterOf(p), 0.8f, null, false);
            TapeBlock.pulse(level, p);
        }
    }

    /** Существо стучит по стеклу. Жилец тоже слышит этот стук. */
    private void knock(ServerLevel level, Vec3 at, float volume, ServerPlayer carrier, boolean thumpOnly) {
        play(level, at, (thumpOnly ? ModSounds.SPECIMEN_THUMP : ModSounds.SPECIMEN_KNOCK).get(), volume, 0.9f + rnd.nextFloat() * 0.15f);
        com.talviom.bodycamhorror.tenant.TenantHearing.noise(level, at, 2f, 20, carrier, false, "specimen_knock");
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

    static void placeTapeBlock(ServerLevel level, BlockPos pos, int turn) {
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
        // 0.9.8: колба упала, существо несколько секунд бьётся о стекло: место слышно всем и Жильцу.
        play(level, Vec3.atCenterOf(spot), ModSounds.SPECIMEN_FRENZY.get(), 1.6f, 1.0f);
        TapeBlock.pulse(level, spot);
        com.talviom.bodycamhorror.tenant.TenantHearing.noise(level, Vec3.atCenterOf(spot), 3f, 28, null, false, "specimen_drop");
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
