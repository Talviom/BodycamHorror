package com.talviom.bodycamhorror.tenant;

import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.ModSounds;
import com.talviom.bodycamhorror.flashlight.Flashlight;
import com.talviom.bodycamhorror.horror.FearSystem;
import com.talviom.bodycamhorror.net.BhNetwork;
import com.talviom.bodycamhorror.net.ScreamerPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Жилец (THE TENANT). Слепой, живёт в темноте, идёт на свет и шум (TenantHearing), к горящим лампам не подходит.
 *
 * Атака (решение пользователя 2026-11-18), всё вместе не дольше 5 с:
 *   1,0 с  смотрит на жертву с 8-10 блоков; луч в лицо или уход на свет его спугивают
 *   0,35 с прыжок на 2 шага влево, 0,35 с на 2 шага вправо
 *   0,75 с пять быстрых прыжков к лицу
 *   2,2 с  душит: камера заперта на его лице, экран краснеет, двигаться и стрелять нельзя
 * Концовки:
 *   рядом союзник          — душит, 4 сердца; союзник отбивает двумя попаданиями или лучом в лицо
 *   один и страшно (60+)   — душит, потом «утащить» в «Затёртую запись» (пока заглушка: чернота и пробуждение)
 *   один и ещё не страшно  — у самого лица исчезает со скримером, не трогая
 *   последний живой (1 на 1) — душит, 4 сердца, отбить некому (0.9)
 *
 * 0.9: без шума не стоит, а бродит по тёмным точкам карты (PATROL); в 9 блоках от него лампы
 * заикаются и гудят, фонарик срывается. В начале любого нападения он снова видим.
 * Во время удушья жертву могут добить враги (решение 2026-11-18): урон от игроков проходит как обычно.
 */
public class TenantEntity extends PathfinderMob implements GeoEntity {
    /** PATROL (0.9) в конце, чтобы номера прежних состояний у клиента не сдвинулись. */
    public enum State { WAIT, FOLLOW, STALK, STARE, BLINK, RUSH, STRANGLE, VANISH, STUNNED, RETREAT, PATROL }

    /** Чем кончится атака: решается в момент, когда он доходит до лица. */
    public enum Ending { SCARE, STRANGLE, TAKE }

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(TenantEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.tenant.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.tenant.walk");
    private static final RawAnimation STALK_ANIM = RawAnimation.begin().thenLoop("animation.tenant.stalk");
    private static final RawAnimation STARE_ANIM = RawAnimation.begin().thenLoop("animation.tenant.stare");
    private static final RawAnimation REACH_ANIM = RawAnimation.begin().thenPlay("animation.tenant.scare");
    private static final RawAnimation STRANGLE_ANIM = RawAnimation.begin().thenLoop("animation.tenant.strangle");
    private static final RawAnimation STUN_ANIM = RawAnimation.begin().thenLoop("animation.tenant.stunned");

    private static final double FOLLOW_SPEED = 1.4;
    private static final double PATROL_SPEED = 0.9;
    /** Без шума постоит 6-12 с и пойдёт бродить. */
    private static final int PATROL_IDLE_MIN = 6 * 20;
    private static final int PATROL_IDLE_RAND = 6 * 20;
    private static final int PATROL_MAX_TICKS = 25 * 20;
    private static final double PATROL_RANGE = 32.0;
    /** Рядом с ним лампы заикаются, фонарик срывается. */
    public static final double DREAD_RANGE = 9.0;
    private static final double STALK_SPEED = 1.0;
    private static final double STALK_RANGE = 10.0;
    private static final double ATTACK_RANGE = 10.0;

    // Время атаки в тиках от её начала.
    private static final int T_LEFT = 20;
    private static final int T_RIGHT = 27;
    private static final int T_RUSH = 34;
    private static final int RUSH_STEPS = 5;
    private static final int RUSH_STEP_TICKS = 3;
    private static final int T_FACE = T_RUSH + RUSH_STEPS * RUSH_STEP_TICKS;
    private static final int STRANGLE_TICKS = 44;
    /** Руки дотягиваются до горла, а лицо ещё в кадре. */
    private static final double STRANGLE_DIST = 1.35;
    private static final int VANISH_TICKS = 10;
    private static final int STARE_BEAM_TO_ESCAPE = 8;

    private static final int STUN_TICKS = 60;
    private static final int STUN_IMMUNE_TICKS = 160;
    private static final int BEAM_TICKS_TO_STUN = 30;
    private static final int BEAM_TICKS_TO_FREE = 15;
    private static final float STRANGLE_DAMAGE_HALF = 4f;
    private static final float DAMAGE_TO_RETREAT = 40f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private BlockPos home;
    private boolean frozen;
    private int stateTicks;
    private int retreatTicks;
    @Nullable private UUID target;
    @Nullable private Vec3 goal;
    private float damageTaken;
    private int beamTicks;
    private long stunImmuneUntil;
    private String lastOutcome = "-";
    private int patrolIdle = PATROL_IDLE_MIN;

    // Атака.
    private int attackTicks;
    private Vec3 stareFrom;
    private Vec3 holdPos;
    private Ending ending;
    @Nullable private Ending forcedEnding;
    private int rescueHits;
    private int rescueBeam;

    public TenantEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STATE, State.WAIT.ordinal());
    }

    public State state() {
        return State.values()[entityData.get(STATE)];
    }

    private void setState(State s) {
        entityData.set(STATE, s.ordinal());
        stateTicks = 0;
    }

    public void setHome(BlockPos pos) {
        home = pos.immutable();
    }

    /** Для скриншотов: застыть в позе нужного состояния. */
    public void pose(State s) {
        setFrozen(true);
        setState(s);
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
        if (frozen) getNavigation().stop();
    }

    /** Жертва сейчас в его руках: стоит, не стреляет. */
    public boolean holds(ServerPlayer p) {
        return state() == State.STRANGLE && p.getUUID().equals(target);
    }

    public String info() {
        ServerPlayer t = targetPlayer();
        return String.format("state=%s target=%s dist=%s damage=%.0f beam=%d last=%s frozen=%s home=%s",
                state().name().toLowerCase(), t == null ? "-" : t.getGameProfile().getName(),
                t == null ? "-" : String.format("%.1f", t.distanceTo(this)), damageTaken, beamTicks, lastOutcome, frozen,
                home == null ? "-" : home.toShortString());
    }

    // ---------- поведение ----------

    @Override
    protected void customServerAiStep() {
        if (frozen) return;
        ServerLevel level = (ServerLevel) level();
        long now = level.getGameTime();
        if (home == null) home = blockPosition();
        stateTicks++;

        checkBeam(level, now);
        if (tickCount % 10 == 0) pressure(level);

        State s = state();
        boolean attacking = s == State.STARE || s == State.BLINK || s == State.RUSH || s == State.STRANGLE || s == State.VANISH;
        if (attacking) {
            attackTick(level, now);
            return;
        }
        // На свету ему нельзя: уходит в ближайшее тёмное место карты и живёт там.
        // Если тёмного места нет (весь свет горит), остаётся где есть и слушает: иначе он бесконечно
        // «отступал» бы к той же освещённой точке и весь раунд стоял на месте (было в 0.7.1).
        if (s != State.RETREAT && tickCount % 20 == 0 && TenantRules.litNear(level, position(), TenantRules.LIGHT_ZONE)) {
            BlockPos dark = TenantRules.darkSpot(level, position());
            if (dark != null && !dark.equals(home)) {
                home = dark;
                lastOutcome = "light->" + dark.toShortString();
                retreat(15 * 20);
                return;
            }
        }

        switch (s) {
            case WAIT -> {
                getNavigation().stop();
                if (tickCount % 10 == 0) listen(now);
                if (state() == State.WAIT && stateTicks >= patrolIdle) startPatrol(level);
            }
            case PATROL -> patrol(now);
            case FOLLOW -> follow(now);
            case STALK -> stalk(level, now);
            case STUNNED -> {
                getNavigation().stop();
                if (stateTicks >= STUN_TICKS) retreat(25 * 20);
            }
            case RETREAT -> {
                retreatTicks--;
                if (tickCount % 20 == 0) getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, FOLLOW_SPEED);
                if (retreatTicks <= 0) {
                    if (distanceToSqr(Vec3.atBottomCenterOf(home)) > 9) teleportHome();
                    target = null;
                    goal = null;
                    setInvisible(false);
                    setState(State.WAIT);
                }
            }
            default -> {}
        }
    }

    private void listen(long now) {
        TenantHearing.Noise n = TenantHearing.best(this, now);
        if (n == null) return;
        goal = n.pos();
        target = n.source();
        setState(State.FOLLOW);
    }

    /** Пойти бродить: случайная тёмная точка карты в 32 блоках (точки лута и кассеты). */
    private void startPatrol(ServerLevel level) {
        patrolIdle = PATROL_IDLE_MIN + random.nextInt(PATROL_IDLE_RAND);
        BlockPos to = TenantRules.randomDarkSpot(level, position(), PATROL_RANGE, random);
        if (to == null || to.closerToCenterThan(position(), 3)) {
            stateTicks = 0;
            return;
        }
        goal = Vec3.atBottomCenterOf(to);
        target = null;
        setState(State.PATROL);
        lastOutcome = "patrol->" + to.toShortString();
        getNavigation().moveTo(goal.x, goal.y, goal.z, PATROL_SPEED);
    }

    private void patrol(long now) {
        if (tickCount % 10 != 0) return;
        listen(now);
        if (state() != State.PATROL) return;
        if (goal == null || goal.distanceToSqr(position()) < 4 || stateTicks > PATROL_MAX_TICKS) {
            // Дошёл: теперь живёт здесь и снова слушает.
            home = blockPosition();
            setState(State.WAIT);
            return;
        }
        if (getNavigation().isDone()) getNavigation().moveTo(goal.x, goal.y, goal.z, PATROL_SPEED);
    }

    private void follow(long now) {
        if (tickCount % 10 != 0) return;
        TenantHearing.Noise n = TenantHearing.best(this, now);
        if (n != null) {
            goal = n.pos();
            target = n.source();
        }
        ServerPlayer p = targetPlayer();
        if (p != null && p.distanceTo(this) <= STALK_RANGE * 1.5 && n != null && now - n.time() < 60) {
            setState(State.STALK);
            return;
        }
        if (goal != null) getNavigation().moveTo(goal.x, goal.y, goal.z, FOLLOW_SPEED);
        // Дошёл до места, а там тихо: возвращается ждать.
        if (n == null && (goal == null || goal.distanceToSqr(position()) < 4) && stateTicks > TenantHearing.FORGET_TICKS) {
            retreat(5 * 20);
        }
    }

    private void stalk(ServerLevel level, long now) {
        ServerPlayer p = targetPlayer();
        if (p == null || p.distanceTo(this) > STALK_RANGE * 2.5 || TenantRules.litNear(level, p.position(), TenantRules.LIGHT_ZONE)) {
            setState(State.FOLLOW);
            return;
        }
        getLookControl().setLookAt(p, 30f, 30f);
        if (p.distanceTo(this) <= ATTACK_RANGE && hasLineOfSight(p)) {
            if (TenantRules.canAttack(p, now)) {
                startAttack(p, now, null);
                return;
            }
            // Нападать ещё рано: держится на расстоянии в темноте.
            getNavigation().stop();
            return;
        }
        if (tickCount % 10 == 0) getNavigation().moveTo(p, STALK_SPEED);
    }

    // ---------- атака ----------

    /** Начать атаку: смотрит, прыжки влево-вправо, рывок к лицу, концовка. forced: для проверки. */
    public void startAttack(ServerPlayer p, long now, @Nullable Ending forced) {
        target = p.getUUID();
        forcedEnding = forced;
        TenantRules.attacked(p, now);
        getNavigation().stop();
        // 0.9 (пункт 12): после прошлого нападения он невидим до конца отдыха; команда или новое
        // нападение раньше срока показывали анимацию и звуки без него самого.
        setInvisible(false);
        stareFrom = position();
        attackTicks = 0;
        rescueHits = 0;
        rescueBeam = 0;
        faceTowards(p);
        setState(State.STARE);
        FearSystem.INSTANCE.add(p, 15f);
        BodycamHorror.LOGGER.info("[tenant] attack on {} from {} blocks", p.getGameProfile().getName(), String.format("%.1f", p.distanceTo(this)));
    }

    private void attackTick(ServerLevel level, long now) {
        ServerPlayer p = targetPlayer();
        if (p == null) {
            ServerPlayer any = victimAny();
            lastOutcome = any == null ? "victim-gone" : "victim-killed";
            abortAttack(any);
            return;
        }
        com.talviom.bodycamhorror.match.MatchState ms = com.talviom.bodycamhorror.match.MatchManager.INSTANCE.state();
        if (ms == com.talviom.bodycamhorror.match.MatchState.ROUND_END || ms == com.talviom.bodycamhorror.match.MatchState.PREP) {
            lastOutcome = "round-end";
            abortAttack(p);
            return;
        }
        attackTicks++;
        getNavigation().stop();
        State s = state();

        if (s == State.STARE) {
            faceTowards(p);
            // Спастись можно, пока он только смотрит: луч в лицо или уйти на свет.
            if (beamTicks >= STARE_BEAM_TO_ESCAPE || TenantRules.litNear(level, p.position(), TenantRules.LIGHT_ZONE)) {
                lastOutcome = "escaped";
                BhNetwork.sendScreamer(p, ScreamerPacket.BLINK, 4);
                vanish(20 * 20);
                return;
            }
            if (attackTicks >= T_LEFT) {
                setState(State.BLINK);
                blinkTo(p, sidePoint(p, 2.0));
            }
            return;
        }
        if (s == State.BLINK) {
            faceTowards(p);
            if (attackTicks == T_RIGHT) blinkTo(p, sidePoint(p, -2.0));
            if (attackTicks >= T_RUSH) {
                setState(State.RUSH);
                p.playNotifySound(ModSounds.TENANT_RUSH.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
                rushStep(p, 1);
            }
            return;
        }
        if (s == State.RUSH) {
            int step = (attackTicks - T_RUSH) / RUSH_STEP_TICKS + 1;
            if ((attackTicks - T_RUSH) % RUSH_STEP_TICKS == 0 && step <= RUSH_STEPS) rushStep(p, step);
            if (attackTicks >= T_FACE) atFace(p);
            return;
        }
        if (s == State.STRANGLE) {
            strangleTick(p);
            return;
        }
        if (s == State.VANISH) {
            placeInFront(p, 1.6);
            if (stateTicks >= VANISH_TICKS) {
                Flashlight.blackout(p, 40);
                vanish(25 * 20);
            }
        }
    }

    /** Точка сбоку от линии «жертва — он» (плюс: левее для жертвы). */
    private Vec3 sidePoint(ServerPlayer p, double side) {
        Vec3 toMe = stareFrom.subtract(p.position());
        Vec3 flat = new Vec3(toMe.x, 0, toMe.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        return stareFrom.add(right.scale(side));
    }

    private void rushStep(ServerPlayer p, int step) {
        Vec3 face = frontOf(p, 1.1);
        double f = step / (double) RUSH_STEPS;
        Vec3 pos = position().lerp(face, step == RUSH_STEPS ? 1.0 : 1.0 / (RUSH_STEPS - step + 1));
        Vec3 dir = face.subtract(stareFrom);
        Vec3 right = new Vec3(-dir.z, 0, dir.x);
        if (right.lengthSqr() > 1e-4) right = right.normalize();
        double zig = step == RUSH_STEPS ? 0 : (step % 2 == 0 ? 0.5 : -0.5) * (1 - f);
        blinkTo(p, pos.add(right.scale(zig)));
    }

    /** Прыжок: мгновенно оказывается в точке, скачок кадра у жертвы, глухой удар для всех рядом. */
    private void blinkTo(ServerPlayer p, Vec3 to) {
        // Если точка в стене, пробуем ближе к нынешнему месту, потом в другую сторону.
        Vec3 from = position();
        Vec3 off = to.subtract(from);
        Vec3 safe = null;
        for (double k : new double[]{1.0, 0.7, 0.45, -0.7}) {
            safe = safeSpot(from.add(off.scale(k)));
            if (safe != null) break;
        }
        if (safe != null) moveTo(safe.x, safe.y, safe.z, getYRot(), 0);
        faceTowards(p);
        BhNetwork.sendSnap(this);
        level().playSound(null, getX(), getY(), getZ(), ModSounds.TENANT_BLINK.get(), SoundSource.HOSTILE, 1.4f,
                0.9f + random.nextFloat() * 0.2f);
        BhNetwork.sendScreamer(p, ScreamerPacket.BLINK, 3);
    }

    /** Свободное место на полу рядом с точкой (не в стене). */
    @Nullable
    private Vec3 safeSpot(Vec3 at) {
        for (int dy : new int[]{0, 1, -1}) {
            Vec3 c = new Vec3(at.x, Math.floor(at.y) + dy, at.z);
            if (level().noCollision(this, getType().getDimensions().makeBoundingBox(c))
                    && !level().getBlockState(BlockPos.containing(c).below()).isAir()) return c;
        }
        return null;
    }

    private void atFace(ServerPlayer p) {
        Ending e = forcedEnding;
        if (e == null) {
            if (TenantRules.allyNear(p)) e = Ending.STRANGLE;
            else if (TenantRules.canTake(p)) e = Ending.TAKE;
            // 0.9: в 1 на 1 (последний живой своей команды) душит с уроном, а не только пугает.
            else if (TenantRules.lastAlive(p)) e = Ending.STRANGLE;
            else e = Ending.SCARE;
        }
        ending = e;
        if (e == Ending.SCARE) {
            lastOutcome = "scare";
            setState(State.VANISH);
            placeInFront(p, 1.6);
            BhNetwork.sendSnap(this);
            BhNetwork.sendScreamer(p, ScreamerPacket.FLASH, 14);
            p.playNotifySound(ModSounds.TENANT_SCARE.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
            level().playSound(p, getX(), getY(), getZ(), ModSounds.TENANT_SCARE.get(), SoundSource.HOSTILE, 0.7f, 1.0f);
            FearSystem.INSTANCE.add(p, 35f);
            return;
        }
        lastOutcome = e == Ending.TAKE ? "take" : "strangle";
        setState(State.STRANGLE);
        holdPos = p.position();
        placeInFront(p, STRANGLE_DIST);
        BhNetwork.sendSnap(this);
        BhNetwork.sendScreamer(p, ScreamerPacket.STRANGLE, STRANGLE_TICKS);
        p.playNotifySound(ModSounds.TENANT_STRANGLE.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
        level().playSound(p, getX(), getY(), getZ(), ModSounds.TENANT_STRANGLE.get(), SoundSource.HOSTILE, 0.8f, 1.0f);
        FearSystem.INSTANCE.setFear(p, 100f);
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o != p && o.distanceToSqr(p) < 100) FearSystem.INSTANCE.add(o, 25f);
        }
    }

    private void strangleTick(ServerPlayer p) {
        if (p.position().distanceToSqr(holdPos) > 0.01) p.teleportTo(holdPos.x, holdPos.y, holdPos.z);
        placeInFront(p, STRANGLE_DIST);
        if (rescueHits >= 2 || rescueBeam >= BEAM_TICKS_TO_FREE) {
            lastOutcome = "freed";
            BhNetwork.sendScreamer(p, ScreamerPacket.FREED, 1);
            stun();
            return;
        }
        if (ending == Ending.STRANGLE && stateTicks == STRANGLE_TICKS / 2) p.hurt(tenantDamage(), STRANGLE_DAMAGE_HALF);
        if (stateTicks < STRANGLE_TICKS) return;

        if (ending == Ending.STRANGLE) p.hurt(tenantDamage(), STRANGLE_DAMAGE_HALF);
        if (!p.isAlive()) {
            vanish(25 * 20);
            return;
        }
        BhNetwork.sendScreamer(p, ScreamerPacket.QUIET, 80);
        p.playNotifySound(ModSounds.TENANT_QUIET.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
        if (ending == Ending.TAKE) {
            // «Затёртая запись» ещё не сделана: пока только чернота, игрок приходит в себя на месте.
            TenantRules.taken(p);
            FearSystem.INSTANCE.setFear(p, 40f);
            vanish(40 * 20);
        } else {
            vanish(25 * 20);
        }
    }

    /** Исчезает: возвращается к себе и некоторое время не охотится. */
    private void vanish(int retreatFor) {
        teleportHome();
        BhNetwork.sendSnap(this);
        setInvisible(true);
        retreat(retreatFor);
    }

    private void teleportHome() {
        teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
    }

    private Vec3 frontOf(ServerPlayer p, double dist) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(0, 0, 1);
        return p.position().add(flat.normalize().scale(dist));
    }

    /**
     * Встать лицом к игроку на расстоянии dist перед его взглядом. 0.9 (пункт 12): если там стена
     * (игрок смотрит в стену), ищет свободное место рядом: чуть правее и левее взгляда, ближе, дальше.
     */
    private void placeInFront(ServerPlayer p, double dist) {
        Vec3 at = freeSpotNear(p, dist);
        moveTo(at.x, at.y, at.z, getYRot(), 0);
        faceTowards(p);
    }

    private Vec3 freeSpotNear(ServerPlayer p, double dist) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        for (double d : new double[]{dist, dist * 0.8, dist * 1.25}) {
            for (int deg : new int[]{0, 25, -25, 50, -50, 80, -80, 120, -120, 180}) {
                double a = Math.toRadians(deg);
                double dx = flat.x * Math.cos(a) - flat.z * Math.sin(a);
                double dz = flat.x * Math.sin(a) + flat.z * Math.cos(a);
                Vec3 safe = safeSpot(new Vec3(p.getX() + dx * d, p.getY(), p.getZ() + dz * d));
                if (safe != null && seesFrom(p, safe)) return safe;
            }
        }
        Vec3 at = frontOf(p, dist);
        return new Vec3(at.x, p.getY(), at.z);
    }

    /** Между глазами игрока и лицом в точке нет стены. */
    private boolean seesFrom(ServerPlayer p, Vec3 feet) {
        net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(p.getEyePosition(),
                feet.add(0, 1.5, 0), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p);
        return level().clip(ctx).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private void faceTowards(ServerPlayer p) {
        double dx = p.getX() - getX();
        double dz = p.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    /** Свой тип урона: не зависит от сложности (на «Мирной» обычный урон моба был бы 0). */
    private DamageSource tenantDamage() {
        return new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,
                        new ResourceLocation(BodycamHorror.MOD_ID, "tenant"))), this);
    }

    private void retreat(int ticks) {
        retreatTicks = ticks;
        setState(State.RETREAT);
    }

    private void stun() {
        stunImmuneUntil = level().getGameTime() + STUN_TICKS + STUN_IMMUNE_TICKS;
        beamTicks = 0;
        setState(State.STUNNED);
        playSound(ModSounds.TENANT_CLICK.get(), 1.0f, 0.7f);
    }

    /**
     * Луч фонарика в лицо с 6 блоков: 1,5 с оглушают на охоте; во время удушья союзник жертвы освобождает её за 0,75 с;
     * пока он только смотрит, жертве хватает 0,4 с, чтобы спугнуть его.
     */
    private void checkBeam(ServerLevel level, long now) {
        boolean lit = false;
        boolean allyLit = false;
        ServerPlayer victim = targetPlayer();
        Vec3 eye = getEyePosition();
        for (ServerPlayer p : level.players()) {
            // Пока он смотрит, жертва спугивает его лучом с любого расстояния атаки (до 10 блоков), иначе с 6.
            double reach = state() == State.STARE && p == victim ? ATTACK_RANGE + 1 : 6;
            if (!Flashlight.isLit(p) || p.distanceToSqr(this) > reach * reach) continue;
            Vec3 to = eye.subtract(p.getEyePosition()).normalize();
            if (to.dot(p.getLookAngle()) > 0.978 && p.hasLineOfSight(this)) {
                if (state() == State.STRANGLE) {
                    if (victim != null && p != victim && TenantRules.sameSide(victim, p)) allyLit = true;
                } else if (state() == State.STARE) {
                    if (p == victim) lit = true;
                } else {
                    lit = true;
                }
            }
        }
        rescueBeam = allyLit ? rescueBeam + 1 : Math.max(0, rescueBeam - 1);
        beamTicks = lit ? beamTicks + 1 : Math.max(0, beamTicks - 1);
        State s = state();
        if (beamTicks >= BEAM_TICKS_TO_STUN && now >= stunImmuneUntil && (s == State.STALK || s == State.FOLLOW)) {
            lastOutcome = "stunned";
            stun();
        }
    }

    /**
     * Рядом с ним страшно: страх растёт, бодикам ловит помехи. 0.9 (пункт 3): в 9 блоках от него
     * лампы заикаются и гудят, а фонарик срывается, так что его можно почувствовать, даже не видя.
     * Пока он невидим после нападения (отдыхает), свет не трогает.
     */
    private void pressure(ServerLevel level) {
        boolean dread = !isInvisible();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double d2 = p.distanceToSqr(this);
            // Чем выше страх, тем слабее добавка: у самого порога страх «пилит», а не стоит на 100 весь раунд.
            if (d2 < 12 * 12) FearSystem.INSTANCE.add(p, 1.5f * Math.max(0.2f, 1f - FearSystem.INSTANCE.fear(p) / 130f));
            if (d2 < 8 * 8 && tickCount % 40 == 0) BhNetwork.sendGlitch(p, 10, 0.25f);
            if (dread && d2 < DREAD_RANGE * DREAD_RANGE && Flashlight.isLit(p) && random.nextFloat() < 0.35f) {
                Flashlight.blackout(p, 2 + random.nextInt(5));
            }
        }
        if (dread && tickCount % 30 == 0 && random.nextFloat() < 0.6f) {
            com.talviom.bodycamhorror.sabotage.Sabotage.stutterAround(level, blockPosition(), (int) DREAD_RANGE);
        }
    }

    /** Жертва на сервере, даже если она уже наблюдатель (погибла). */
    @Nullable
    private ServerPlayer victimAny() {
        if (target == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(target);
    }

    /** Атака оборвалась: у жертвы (если она ещё на сервере) снимаются красный экран и запертая камера. */
    private void abortAttack(@Nullable ServerPlayer victim) {
        if (victim != null) BhNetwork.sendScreamer(victim, ScreamerPacket.FREED, 1);
        BodycamHorror.LOGGER.info("[tenant] attack stopped: {}", lastOutcome);
        vanish(25 * 20);
    }

    @Nullable
    private ServerPlayer targetPlayer() {
        if (target == null || !(level() instanceof ServerLevel sl)) return null;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(target);
        return p == null || p.isSpectator() || !p.isAlive() ? null : p;
    }

    // ---------- урон: убить нельзя, можно прогнать ----------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.GENERIC_KILL)) return super.hurt(source, amount);
        if (level().isClientSide || !(source.getEntity() instanceof ServerPlayer shooter)) return false;
        if (state() == State.STRANGLE) {
            // Отбить жертву могут только её союзники; попадания врагов он не замечает.
            ServerPlayer victim = targetPlayer();
            if (victim == null || shooter == victim || !TenantRules.sameSide(victim, shooter)) return false;
            rescueHits++;
        }
        damageTaken += amount;
        playSound(ModSounds.TENANT_CLICK.get(), 0.8f, 0.8f + random.nextFloat() * 0.3f);
        if (damageTaken >= DAMAGE_TO_RETREAT && state() != State.STRANGLE) {
            damageTaken = 0;
            lastOutcome = "shot-away";
            retreat(30 * 20);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.TENANT_BREATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected float getSoundVolume() {
        return 0.7f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (random.nextInt(3) == 0) playSound(ModSounds.TENANT_CLICK.get(), 0.3f, 0.9f + random.nextFloat() * 0.2f);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.putLong("TenantHome", home.asLong());
        tag.putBoolean("TenantFrozen", frozen);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TenantHome")) home = BlockPos.of(tag.getLong("TenantHome"));
        frozen = tag.getBoolean("TenantFrozen");
    }

    // ---------- GeckoLib ----------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, st -> switch (state()) {
            case STARE, BLINK -> st.setAndContinue(STARE_ANIM);
            case RUSH -> st.setAndContinue(STALK_ANIM);
            case VANISH -> st.setAndContinue(REACH_ANIM);
            case STRANGLE -> st.setAndContinue(STRANGLE_ANIM);
            case STUNNED -> st.setAndContinue(STUN_ANIM);
            case STALK -> st.setAndContinue(STALK_ANIM);
            case FOLLOW, RETREAT, PATROL -> st.setAndContinue(st.isMoving() ? WALK : IDLE);
            default -> st.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
