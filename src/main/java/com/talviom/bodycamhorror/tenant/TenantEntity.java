package com.talviom.bodycamhorror.tenant;

import com.talviom.bodycamhorror.ModSounds;
import com.talviom.bodycamhorror.flashlight.Flashlight;
import com.talviom.bodycamhorror.horror.FearSystem;
import com.talviom.bodycamhorror.net.BhNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
 * Вплотную: скример (один, страх ниже 60), хват (союзник рядом) или утащить (один, страх 60+).
 * Убить нельзя: 40 урона от игроков прогоняют его на 30 с, луч фонарика в лицо вблизи оглушает на 3 с.
 *
 * Утащить в «Затёртую запись» пока не сделано: вместо этого скример (в /bh tenant info видно «take->scare»).
 */
public class TenantEntity extends PathfinderMob implements GeoEntity {
    public enum State { WAIT, FOLLOW, STALK, SCARE, GRAB, STUNNED, RETREAT }

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(TenantEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.tenant.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.tenant.walk");
    private static final RawAnimation STALK_ANIM = RawAnimation.begin().thenLoop("animation.tenant.stalk");
    private static final RawAnimation SCARE_ANIM = RawAnimation.begin().thenPlay("animation.tenant.scare");
    private static final RawAnimation GRAB_ANIM = RawAnimation.begin().thenLoop("animation.tenant.grab");
    private static final RawAnimation STUN_ANIM = RawAnimation.begin().thenLoop("animation.tenant.stunned");

    private static final double FOLLOW_SPEED = 1.4;
    private static final double STALK_SPEED = 1.0;
    private static final double REACH = 2.2;
    private static final double STALK_RANGE = 10.0;
    private static final int SCARE_TICKS = 14;
    /** Вплотную; камера жертвы в это время сама смотрит ему в лицо (TenantFx). */
    private static final double SCARE_DIST = 2.2;
    private static final int GRAB_TICKS = 40;
    private static final int STUN_TICKS = 60;
    private static final int STUN_IMMUNE_TICKS = 160;
    private static final int BEAM_TICKS_TO_STUN = 30;
    private static final float GRAB_DAMAGE_HALF = 4f;
    private static final float DAMAGE_TO_RETREAT = 40f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private BlockPos home;
    private boolean frozen;
    private int stateTicks;
    private int retreatTicks;
    @Nullable private UUID target;
    @Nullable private Vec3 goal;
    private float damageTaken;
    private int grabHits;
    private Vec3 grabPos;
    private int beamTicks;
    private long stunImmuneUntil;
    private String lastOutcome = "-";

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
        if (tickCount % 10 == 0) pressure(level, now);

        // На свету ему нельзя: уходит, кроме середины скримера и хвата.
        State s = state();
        if (s != State.SCARE && s != State.GRAB && s != State.RETREAT && TenantRules.litNear(level, position(), TenantRules.LIGHT_ZONE)) {
            retreat(20 * 20);
            return;
        }

        switch (s) {
            case WAIT -> {
                getNavigation().stop();
                if (tickCount % 10 == 0) listen(level, now);
            }
            case FOLLOW -> follow(level, now);
            case STALK -> stalk(level, now);
            case SCARE -> scareTick();
            case GRAB -> grabTick();
            case STUNNED -> {
                getNavigation().stop();
                if (stateTicks >= STUN_TICKS) setState(targetPlayer() != null ? State.STALK : State.FOLLOW);
            }
            case RETREAT -> {
                retreatTicks--;
                if (tickCount % 20 == 0) getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, FOLLOW_SPEED);
                if (retreatTicks <= 0) {
                    if (distanceToSqr(Vec3.atBottomCenterOf(home)) > 9) teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
                    target = null;
                    goal = null;
                    setState(State.WAIT);
                }
            }
        }
    }

    private void listen(ServerLevel level, long now) {
        TenantHearing.Noise n = TenantHearing.best(this, now);
        if (n == null) return;
        goal = n.pos();
        target = n.source();
        setState(State.FOLLOW);
    }

    private void follow(ServerLevel level, long now) {
        if (tickCount % 10 == 0) {
            TenantHearing.Noise n = TenantHearing.best(this, now);
            if (n != null) {
                goal = n.pos();
                target = n.source();
            }
            ServerPlayer p = targetPlayer();
            if (p != null && p.distanceTo(this) <= STALK_RANGE && n != null && now - n.time() < 60) {
                setState(State.STALK);
                return;
            }
            if (goal != null) getNavigation().moveTo(goal.x, goal.y, goal.z, FOLLOW_SPEED);
            // Дошёл до места, а там тихо: возвращается ждать.
            if (n == null && (goal == null || goal.distanceToSqr(position()) < 4) && stateTicks > TenantHearing.FORGET_TICKS) {
                retreat(5 * 20);
            }
        }
    }

    private void stalk(ServerLevel level, long now) {
        ServerPlayer p = targetPlayer();
        if (p == null || p.distanceTo(this) > STALK_RANGE * 2 || TenantRules.litNear(level, p.position(), TenantRules.LIGHT_ZONE)) {
            setState(State.FOLLOW);
            return;
        }
        getLookControl().setLookAt(p, 30f, 30f);
        if (tickCount % 10 == 0) getNavigation().moveTo(p, STALK_SPEED);
        if (p.distanceTo(this) <= REACH) encounter(p, now);
    }

    /** Вплотную: скример, хват или утащить. */
    private void encounter(ServerPlayer p, long now) {
        target = p.getUUID();
        if (TenantRules.allyNear(p)) {
            startGrab(p);
        } else if (TenantRules.canTake(p)) {
            // «Затёртая запись» ещё не сделана: пока скример.
            lastOutcome = "take->scare";
            startScare(p, now, true);
        } else if (TenantRules.canScare(p, now)) {
            startScare(p, now, false);
        } else {
            lastOutcome = "limit";
            retreat(25 * 20);
        }
    }

    /** Скример: встаёт вплотную перед камерой жертвы, рывок, крик, фонарик гаснет. Урона нет. */
    public void startScare(ServerPlayer p, long now, boolean fromTake) {
        if (!fromTake) lastOutcome = "scare";
        target = p.getUUID();
        TenantRules.scared(p, now);
        setState(State.SCARE);
        getNavigation().stop();
        placeInFront(p, SCARE_DIST);
        BhNetwork.sendScreamer(p, 18);
        p.playNotifySound(ModSounds.TENANT_SCREAM.get(), SoundSource.HOSTILE, 1.0f, 0.95f + random.nextFloat() * 0.1f);
        level().playSound(p, getX(), getY(), getZ(), ModSounds.TENANT_SCREAM.get(), SoundSource.HOSTILE, 0.6f, 0.9f);
        FearSystem.INSTANCE.add(p, 35f);
    }

    private void scareTick() {
        ServerPlayer p = targetPlayer();
        if (p != null && stateTicks < SCARE_TICKS) {
            placeInFront(p, SCARE_DIST);
            return;
        }
        // Фонарик гаснет уже после того, как его лицо увидели, и он исчезает в темноте.
        if (p != null) Flashlight.blackout(p, 40);
        teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        retreat(25 * 20);
    }

    private void startGrab(ServerPlayer p) {
        lastOutcome = "grab";
        setState(State.GRAB);
        getNavigation().stop();
        grabHits = 0;
        grabPos = p.position();
        placeInFront(p, 0.9);
        p.hurt(tenantDamage(), GRAB_DAMAGE_HALF);
        FearSystem.INSTANCE.setFear(p, 100f);
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o != p && o.distanceToSqr(p) < 100) FearSystem.INSTANCE.add(o, 25f);
        }
        BhNetwork.sendGlitch(p, GRAB_TICKS, 0.8f);
        p.playNotifySound(ModSounds.TENANT_SCREAM.get(), SoundSource.HOSTILE, 0.8f, 0.7f);
    }

    private void grabTick() {
        ServerPlayer p = targetPlayer();
        if (p == null || !p.isAlive() || p.isSpectator()) {
            retreat(25 * 20);
            return;
        }
        if (p.position().distanceToSqr(grabPos) > 0.01) p.teleportTo(grabPos.x, grabPos.y, grabPos.z);
        placeInFront(p, 0.9);
        if (grabHits >= 2) {
            lastOutcome = "grab-freed";
            stun();
            return;
        }
        if (stateTicks >= GRAB_TICKS) {
            p.hurt(tenantDamage(), GRAB_DAMAGE_HALF);
            retreat(25 * 20);
        }
    }

    /** Свой тип урона: не зависит от сложности (на «Мирной» обычный урон моба был бы 0). */
    private DamageSource tenantDamage() {
        return new DamageSource(level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                        new net.minecraft.resources.ResourceLocation(com.talviom.bodycamhorror.BodycamHorror.MOD_ID, "tenant"))), this);
    }

    /** Встать лицом к игроку на расстоянии dist перед его взглядом. */
    private void placeInFront(ServerPlayer p, double dist) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        Vec3 at = p.position().add(flat.scale(dist));
        moveTo(at.x, p.getY(), at.z, 0, 0);
        double dx = p.getX() - at.x;
        double dz = p.getZ() - at.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
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

    /** Луч фонарика в лицо с 6 блоков дольше 1,5 с оглушает его. */
    private void checkBeam(ServerLevel level, long now) {
        boolean lit = false;
        Vec3 eye = getEyePosition();
        for (ServerPlayer p : level.players()) {
            if (!Flashlight.isLit(p) || p.distanceToSqr(this) > 36) continue;
            Vec3 to = eye.subtract(p.getEyePosition()).normalize();
            if (to.dot(p.getLookAngle()) > 0.978 && p.hasLineOfSight(this)) {
                lit = true;
                break;
            }
        }
        beamTicks = lit ? beamTicks + 1 : Math.max(0, beamTicks - 1);
        State s = state();
        if (beamTicks >= BEAM_TICKS_TO_STUN && now >= stunImmuneUntil
                && (s == State.STALK || s == State.FOLLOW || s == State.GRAB)) {
            lastOutcome = "stunned";
            stun();
        }
    }

    /** Рядом с ним страшно: страх растёт, бодикам ловит помехи. */
    private void pressure(ServerLevel level, long now) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double d2 = p.distanceToSqr(this);
            if (d2 < 12 * 12) FearSystem.INSTANCE.add(p, 1.5f);
            if (d2 < 8 * 8 && tickCount % 40 == 0) BhNetwork.sendGlitch(p, 10, 0.25f);
        }
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
        if (level().isClientSide || !(source.getEntity() instanceof ServerPlayer)) return false;
        damageTaken += amount;
        if (state() == State.GRAB) grabHits++;
        playSound(ModSounds.TENANT_CLICK.get(), 0.8f, 0.8f + random.nextFloat() * 0.3f);
        if (damageTaken >= DAMAGE_TO_RETREAT) {
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
        return 160;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (random.nextInt(4) == 0) playSound(ModSounds.TENANT_CLICK.get(), 0.25f, 0.9f + random.nextFloat() * 0.2f);
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
        controllers.add(new AnimationController<>(this, "main", 4, st -> switch (state()) {
            case SCARE -> st.setAndContinue(SCARE_ANIM);
            case GRAB -> st.setAndContinue(GRAB_ANIM);
            case STUNNED -> st.setAndContinue(STUN_ANIM);
            case STALK -> st.setAndContinue(STALK_ANIM);
            case FOLLOW, RETREAT -> st.setAndContinue(st.isMoving() ? WALK : IDLE);
            default -> st.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
