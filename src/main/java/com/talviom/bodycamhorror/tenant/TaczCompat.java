package com.talviom.bodycamhorror.tenant;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.api.distmarker.Dist;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Связь с модом оружия TACZ без жёсткой зависимости (классы ищутся по имени).
 *   GunShootEvent: жертва, которую душит Жилец, не стреляет (отмена на сервере и у неё на клиенте).
 *   GunFireEvent на сервере: выстрел слышно на 40 блоков, Жилец идёт на звук.
 */
public final class TaczCompat {
    private TaczCompat() {}

    public static void init() {
        if (!ModList.get().isLoaded("tacz")) return;
        try {
            listen("com.tacz.guns.api.event.common.GunShootEvent", TaczCompat::onShoot);
            listen("com.tacz.guns.api.event.common.GunFireEvent", TaczCompat::onFire);
            BodycamHorror.LOGGER.info("[tenant] TACZ hooks ready");
        } catch (ReflectiveOperationException ex) {
            BodycamHorror.LOGGER.warn("[tenant] TACZ events not found, gunshots will not be heard", ex);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void listen(String className, Consumer<Event> handler) throws ReflectiveOperationException {
        Class cls = Class.forName(className);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, cls, (Consumer) handler);
    }

    private static LivingEntity shooter(Event e) {
        try {
            Method m = e.getClass().getMethod("getShooter");
            return (LivingEntity) m.invoke(e);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static boolean serverSide(Event e) {
        try {
            return e.getClass().getMethod("getLogicalSide").invoke(e) == LogicalSide.SERVER;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private static void onShoot(Event e) {
        if (!e.isCancelable()) return;
        LivingEntity s = shooter(e);
        if (serverSide(e)) {
            if (s instanceof ServerPlayer p && p.level() instanceof ServerLevel level) {
                for (TenantEntity t : TenantCommand.all(level)) {
                    if (t.holds(p)) {
                        e.setCanceled(true);
                        return;
                    }
                }
            }
        } else {
            Boolean cancel = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> com.talviom.bodycamhorror.client.TenantFx.strangled());
            if (Boolean.TRUE.equals(cancel)) e.setCanceled(true);
        }
    }

    private static void onFire(Event e) {
        if (!serverSide(e) || e.isCanceled()) return;
        if (shooter(e) instanceof ServerPlayer p && p.level() instanceof ServerLevel level) {
            TenantHearing.noise(level, p.position(), 5f, 40, p, false, "shot");
        }
    }
}
