package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.tenant.ModEntities;
import com.talviom.bodycamhorror.tenant.TenantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.Random;

/**
 * Клиент: Жилец на экране. Модель GeckoLib и скример: вспышка цвета кости, тряска кадра, сильные помехи.
 * Сам Жилец в этот момент стоит вплотную перед камерой (это делает сервер), поэтому лицо видно настоящее.
 */
public final class TenantFx {
    private static final Random RND = new Random();
    private static int scareTicks;
    private static int scareLength = 1;

    private TenantFx() {}

    public static void start(int ticks) {
        scareTicks = ticks;
        scareLength = Math.max(1, ticks);
        ClientEffects.startGlitch(ticks, 1.0f);
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
            e.registerAboveAll("tenant_scare", (gui, g, partialTick, width, height) -> {
                if (scareTicks <= 0) return;
                int age = scareLength - scareTicks;
                // Первые три кадра: вспышка цвета кости, потом рваные тёмные полосы.
                if (age < 3) {
                    int a = (int) (200 * (1 - age / 3f));
                    g.fill(0, 0, width, height, (a << 24) | 0xC4BCAA);
                }
                Random r = new Random(System.currentTimeMillis() / 30);
                for (int i = 0; i < 6; i++) {
                    int y = r.nextInt(Math.max(1, height));
                    g.fill(0, y, width, y + 2 + r.nextInt(6), (90 + r.nextInt(120)) << 24);
                }
            });
        }
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class TenantFxForgeEvents {
        @SubscribeEvent
        public static void onTenantFxTick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.END && scareTicks > 0) scareTicks--;
        }

        @SubscribeEvent
        public static void onTenantCamera(ViewportEvent.ComputeCameraAngles e) {
            Minecraft mc = Minecraft.getInstance();
            if (scareTicks <= 0 || mc.isPaused() || mc.level == null || mc.player == null) return;
            float k = scareTicks / (float) scareLength;
            // Камера рывком смотрит ему в лицо: голова выше глаз игрока.
            Vec3 cam = e.getCamera().getPosition();
            TenantEntity near = null;
            double best = 25;
            for (TenantEntity t : mc.level.getEntitiesOfClass(TenantEntity.class, mc.player.getBoundingBox().inflate(5))) {
                double d = t.distanceToSqr(mc.player);
                if (d < best) {
                    best = d;
                    near = t;
                }
            }
            if (near != null) {
                // Голова свисает вперёд от ног примерно на 0,6 блока, к игроку.
                Vec3 feet = near.getPosition((float) e.getPartialTick());
                Vec3 toCam = new Vec3(cam.x - feet.x, 0, cam.z - feet.z);
                if (toCam.lengthSqr() > 1e-4) toCam = toCam.normalize();
                Vec3 face = feet.add(toCam.scale(0.6)).add(0, 2.35, 0);
                Vec3 d = face.subtract(cam);
                float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
                float pitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
                float snap = Math.min(1f, (scareLength - scareTicks + 1) / 2f);
                e.setYaw(Mth.rotLerp(snap, e.getYaw(), yaw));
                e.setPitch(Mth.lerp(snap, e.getPitch(), pitch));
            }
            e.setRoll(e.getRoll() + (RND.nextFloat() - 0.5f) * 10f * k);
            e.setYaw(e.getYaw() + (RND.nextFloat() - 0.5f) * 3f * k);
            e.setPitch(e.getPitch() + (RND.nextFloat() - 0.5f) * 3f * k);
        }
    }
}
