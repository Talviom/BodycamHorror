package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.net.ScreamerPacket;
import com.talviom.bodycamhorror.tenant.ModEntities;
import com.talviom.bodycamhorror.tenant.TenantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Клиент: Жилец на экране и эффекты его атаки у жертвы.
 * Скример и удушье: камера смотрит ему в лицо. Удушье: экран краснеет от краёв (цвет запёкшейся крови 7A1612),
 * двигаться и стрелять нельзя. Затихание: чернота, потом красное медленно уходит.
 */
public final class TenantFx {
    private static final Random RND = new Random();
    private static final int BLOOD = 0x7A1612;

    private static int flashTicks;
    private static int flashLength = 1;
    private static int strangleTicks;
    private static int strangleLength = 1;
    private static int quietTicks;
    private static int quietLength = 1;

    private TenantFx() {}

    public static void on(int kind, int ticks) {
        switch (kind) {
            case ScreamerPacket.FLASH -> {
                flashTicks = flashLength = Math.max(1, ticks);
                ClientEffects.startGlitch(ticks, 1.0f);
            }
            case ScreamerPacket.BLINK -> ClientEffects.startGlitch(ticks, 0.9f);
            case ScreamerPacket.STRANGLE -> {
                strangleTicks = strangleLength = Math.max(1, ticks);
                ClientEffects.startGlitch(6, 1.0f);
            }
            case ScreamerPacket.QUIET -> {
                strangleTicks = 0;
                quietTicks = quietLength = Math.max(1, ticks);
            }
            case ScreamerPacket.FREED -> {
                strangleTicks = 0;
                quietTicks = quietLength = 30;
            }
            default -> {}
        }
    }

    /** Жертву душат: клиент не стреляет (TaczCompat) и не двигается. */
    public static boolean strangled() {
        return strangleTicks > 0;
    }

    @Nullable
    private static TenantEntity nearest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        TenantEntity near = null;
        double best = 36;
        for (TenantEntity t : mc.level.getEntitiesOfClass(TenantEntity.class, mc.player.getBoundingBox().inflate(6))) {
            double d = t.distanceToSqr(mc.player);
            if (d < best) {
                best = d;
                near = t;
            }
        }
        return near;
    }

    /** Красные края, сходящиеся к центру: k от 0 (нет) до 1 (почти весь экран). */
    private static void bloodEdges(GuiGraphics g, int w, int h, float k) {
        if (k <= 0.01f) return;
        int steps = 16;
        int reachX = (int) (w * 0.5f * k);
        int reachY = (int) (h * 0.5f * k);
        for (int i = 0; i < steps; i++) {
            float f = 1f - i / (float) steps;
            int a = (int) (230 * f * f * Math.min(1f, k * 1.4f));
            if (a <= 0) continue;
            int c = (a << 24) | BLOOD;
            int x1 = i * reachX / steps;
            int x2 = (i + 1) * reachX / steps;
            int y1 = i * reachY / steps;
            int y2 = (i + 1) * reachY / steps;
            g.fill(x1, y1, w - x1, y2, c);
            g.fill(x1, h - y2, w - x1, h - y1, c);
            g.fill(x1, y2, x2, h - y2, c);
            g.fill(w - x2, y2, w - x1, h - y2, c);
        }
        g.fill(0, 0, w, h, ((int) (70 * k) << 24) | BLOOD);
    }

    private static void tears(GuiGraphics g, int width, int height) {
        Random r = new Random(System.currentTimeMillis() / 30);
        for (int i = 0; i < 6; i++) {
            int y = r.nextInt(Math.max(1, height));
            g.fill(0, y, width, y + 2 + r.nextInt(6), (90 + r.nextInt(120)) << 24);
        }
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class TenantFxModEvents {
        @SubscribeEvent
        public static void onTenantRenderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(ModEntities.TENANT.get(), ctx -> new GeoEntityRenderer<TenantEntity>(ctx,
                    new DefaultedEntityGeoModel<>(new ResourceLocation(BodycamHorror.MOD_ID, "tenant"))));
        }

        @SubscribeEvent
        public static void onTenantOverlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("tenant_attack", (gui, g, partialTick, width, height) -> {
                if (flashTicks > 0) {
                    int age = flashLength - flashTicks;
                    if (age < 3) g.fill(0, 0, width, height, ((int) (200 * (1 - age / 3f)) << 24) | 0xC4BCAA);
                    tears(g, width, height);
                }
                if (strangleTicks > 0) {
                    float k = 1f - strangleTicks / (float) strangleLength;
                    bloodEdges(g, width, height, 0.25f + 0.75f * k);
                    if (RND.nextInt(5) == 0) tears(g, width, height);
                }
                if (quietTicks > 0) {
                    int age = quietLength - quietTicks;
                    // Сначала чернота, потом красное медленно уходит.
                    float dark = age < 12 ? 1f : Math.max(0f, 1f - (age - 12) / 20f);
                    bloodEdges(g, width, height, 0.8f * quietTicks / (float) quietLength);
                    if (dark > 0) g.fill(0, 0, width, height, ((int) (255 * dark) << 24));
                }
            });
        }
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class TenantFxForgeEvents {
        @SubscribeEvent
        public static void onTenantFxTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            if (flashTicks > 0) flashTicks--;
            if (strangleTicks > 0) strangleTicks--;
            if (quietTicks > 0) quietTicks--;
        }

        @SubscribeEvent
        public static void onTenantInput(MovementInputUpdateEvent e) {
            if (strangleTicks <= 0) return;
            e.getInput().forwardImpulse = 0;
            e.getInput().leftImpulse = 0;
            e.getInput().jumping = false;
            e.getInput().up = false;
            e.getInput().down = false;
            e.getInput().left = false;
            e.getInput().right = false;
        }

        @SubscribeEvent
        public static void onTenantCamera(ViewportEvent.ComputeCameraAngles e) {
            Minecraft mc = Minecraft.getInstance();
            boolean lock = flashTicks > 0 || strangleTicks > 0;
            if (!lock || mc.isPaused() || mc.player == null) return;
            float k = flashTicks > 0 ? flashTicks / (float) flashLength : 0.6f;
            TenantEntity near = nearest();
            if (near != null) {
                // Камера смотрит ему в лицо. Голова свисает вперёд от ног примерно на 0,6 блока, к игроку.
                Vec3 cam = e.getCamera().getPosition();
                Vec3 feet = near.getPosition((float) e.getPartialTick());
                Vec3 toCam = new Vec3(cam.x - feet.x, 0, cam.z - feet.z);
                if (toCam.lengthSqr() > 1e-4) toCam = toCam.normalize();
                Vec3 face = feet.add(toCam.scale(0.6)).add(0, 2.35, 0);
                Vec3 d = face.subtract(cam);
                float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
                float pitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
                float snap = flashTicks > 0 ? Math.min(1f, (flashLength - flashTicks + 1) / 2f) : 1f;
                e.setYaw(Mth.rotLerp(snap, e.getYaw(), yaw));
                e.setPitch(Mth.lerp(snap, e.getPitch(), pitch));
            }
            e.setRoll(e.getRoll() + (RND.nextFloat() - 0.5f) * 10f * k);
            e.setYaw(e.getYaw() + (RND.nextFloat() - 0.5f) * 3f * k);
            e.setPitch(e.getPitch() + (RND.nextFloat() - 0.5f) * 3f * k);
        }
    }
}
