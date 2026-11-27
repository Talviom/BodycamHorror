package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.gear.ModGear;
import com.talviom.bodycamhorror.lobby.BriefSheetBlock;
import com.talviom.bodycamhorror.lobby.BriefSheetBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Лобби 1.0: рисует лист на стене. Бумага из textures/block/brief_*.png, текст из lang-файлов на языке игры,
 * поэтому перевод работает сам. Текст подбирает размер так, чтобы влезть в лист.
 * Мониторы: чёрный экран, живые помехи и «НЕТ СИГНАЛА» с неровным мерцанием.
 * Свет чуть приподнят, чтобы в тусклом лобби листы читались, но не светились.
 */
public class BriefSheetRenderer implements BlockEntityRenderer<BriefSheetBlockEntity> {
    private static final ResourceLocation MEMO = Vhs.rl("textures/block/brief_memo.png");
    private static final ResourceLocation NOTE = Vhs.rl("textures/block/brief_note.png");
    private static final ResourceLocation PHOTO = Vhs.rl("textures/block/brief_photo.png");
    private static final ResourceLocation PLAN = Vhs.rl("textures/block/brief_plan.png");
    private static final ResourceLocation LABEL = Vhs.rl("textures/block/brief_label.png");
    private static final int MIN_LIGHT = 8;
    private static final int INK = 0x2A2620;
    private static final int PENCIL = 0x23283A;
    private static final int RED_PENCIL = 0x6E1A14;
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Подписи на схеме больницы: ключ и место в координатах плана (x, z), как в HospitalBuilder. */
    private static final Object[][] PLAN_LABELS = {
            {"bh.brief.plan.yard", 44, 30}, {"bh.brief.plan.admissions", 18, 18}, {"bh.brief.plan.kitchen", 70, 18},
            {"bh.brief.plan.exit", 6, 26}, {"bh.brief.plan.exit", 82, 26}, {"bh.brief.plan.outbuilding", 45, -1},
            {"bh.brief.plan.entrance", 44, 56}};

    private final Font font;
    private final Map<String, Layout> cache = new HashMap<>();

    private record Line(FormattedCharSequence text, float scale, int color) {}

    private record Layout(float scale, List<Line> lines, float heightPx) {}

    public BriefSheetRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(BriefSheetBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(BriefSheetBlock.FACING);
        int lit = LightTexture.pack(Math.max(LightTexture.block(light), MIN_LIGHT), LightTexture.sky(light));
        float w = be.w;
        float h = be.h;

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // Левый нижний угол листа у самой стены; +X вправо (для смотрящего), +Y вверх, +Z от стены.
        float lift = be.style == BriefSheetBlockEntity.NOTE || be.style == BriefSheetBlockEntity.PHOTO ? 0.018f : 0.010f;
        pose.translate(-0.5 + be.x, be.y, -0.5 + lift);
        if (be.tilt != 0f) {
            pose.translate(w / 2, h / 2, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(be.tilt));
            pose.translate(-w / 2, -h / 2, 0);
        }

        switch (be.style) {
            case BriefSheetBlockEntity.MONITOR -> monitor(be, pose, buffers, w, h);
            case BriefSheetBlockEntity.NOTE -> {
                quad(pose, buffers, NOTE, w, h, lit, 255);
                text(pose, buffers, key(be, "note"), List.of(para(BriefText.note(be.topic), false, PENCIL)),
                        0.10f * w, h - 0.17f * h, 0.80f * w, 0.74f * h, lit);
            }
            case BriefSheetBlockEntity.PHOTO -> {
                quad(pose, buffers, PHOTO, w, h, lit, 255);
                List<Para> cap = new ArrayList<>();
                for (String s : Vhs.trList("bh.brief.photo." + be.topic)) cap.add(para(s, false, PENCIL));
                text(pose, buffers, key(be, "photo"), cap, 0.08f * w, 0.25f * h, 0.84f * w, 0.22f * h, lit);
            }
            case BriefSheetBlockEntity.PLAN -> plan(be, pose, buffers, w, h, lit);
            case BriefSheetBlockEntity.LABEL -> {
                quad(pose, buffers, LABEL, w, h, lit, 255);
                List<Para> ls = new ArrayList<>();
                String[] parts = Vhs.trList("bh.brief.label." + be.topic);
                for (int i = 0; i < parts.length; i++) ls.add(para(parts[i], i == 0, i == 0 ? INK : 0x4E4A42));
                text(pose, buffers, key(be, "label"), ls, 0.06f * w, h - 0.12f * h, 0.88f * w, 0.76f * h, lit);
            }
            default -> {
                quad(pose, buffers, MEMO, w, h, lit, 255);
                List<Para> ps = new ArrayList<>();
                ps.add(para(BriefText.title(be.topic), true, INK));
                for (String s : BriefText.body(be.topic)) ps.add(para(s, false, INK));
                text(pose, buffers, key(be, "memo"), ps, 0.07f * w, h - 0.05f * h, 0.86f * w, 0.84f * h, lit);
            }
        }
        pose.popPose();
    }

    private String key(BriefSheetBlockEntity be, String kind) {
        return kind + "|" + be.topic + "|" + be.w + "|" + be.h + "|" + Minecraft.getInstance().getLanguageManager().getSelected();
    }

    // ---------- бумага ----------

    private static void quad(PoseStack pose, MultiBufferSource buffers, ResourceLocation tex, float w, float h, int light, int shade) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        vertex(vc, m, n, 0, 0, 0, 1, light, shade, 255);
        vertex(vc, m, n, w, 0, 1, 1, light, shade, 255);
        vertex(vc, m, n, w, h, 1, 0, light, shade, 255);
        vertex(vc, m, n, 0, h, 0, 0, light, shade, 255);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, int light, int shade, int alpha) {
        vc.vertex(m, x, y, 0).color(shade, shade, shade, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, 0, 0, 1).endVertex();
    }

    // ---------- текст ----------

    private record Para(Component text, boolean title, int color) {}

    private static Para para(String s, boolean title, int color) {
        Component c = title ? Vhs.title(s) : Component.literal(s);
        return new Para(c, title, color);
    }

    /** Рисует абзацы в прямоугольнике: left, top (верхний край), ширина и высота в блоках. */
    private void text(PoseStack pose, MultiBufferSource buffers, String key, List<Para> paras, float left, float top,
                      float width, float height, int light) {
        Layout layout = cache.computeIfAbsent(key, k -> fit(paras, width, height));
        pose.pushPose();
        pose.translate(left, top, 0.004f);
        float s = layout.scale();
        pose.scale(s, -s, s);
        float y = 0;
        for (Line line : layout.lines()) {
            if (line.text() == null) {
                y += 4;
                continue;
            }
            pose.pushPose();
            pose.scale(line.scale(), line.scale(), 1f);
            font.drawInBatch(line.text(), 0, y / line.scale(), line.color(), false, pose.last().pose(), buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, light);
            pose.popPose();
            y += 10 * line.scale();
        }
        pose.popPose();
    }

    /** Подбирает такой размер шрифта, чтобы все абзацы влезли в лист. */
    private Layout fit(List<Para> paras, float width, float height) {
        float s = 0.0125f;
        Layout best = null;
        for (int i = 0; i < 16; i++) {
            float maxPx = width / s;
            List<Line> lines = new ArrayList<>();
            float px = 0;
            for (int p = 0; p < paras.size(); p++) {
                Para para = paras.get(p);
                float k = para.title() ? 1.3f : 1f;
                for (FormattedCharSequence seq : font.split(para.text(), (int) (maxPx / k))) {
                    lines.add(new Line(seq, k, para.color()));
                    px += 10 * k;
                }
                if (p < paras.size() - 1) {
                    lines.add(new Line(null, 1f, 0));
                    px += 4;
                }
            }
            best = new Layout(s, lines, px);
            if (px * s <= height) break;
            s *= 0.92f;
        }
        return best;
    }

    // ---------- схема и мониторы ----------

    private void plan(BriefSheetBlockEntity be, PoseStack pose, MultiBufferSource buffers, float w, float h, int lit) {
        quad(pose, buffers, PLAN, w, h, lit, 255);
        text(pose, buffers, key(be, "plan"), List.of(para(Vhs.tr("bh.brief.plan.title"), true, INK)),
                0.06f * w, h - 0.015f * h, 0.88f * w, 0.06f * h, lit);
        float s = w / 256f * 0.62f;
        for (Object[] l : PLAN_LABELS) {
            float u = (18 + ((Integer) l[1]) * 2.4f * 0.92f) / 256f;
            float v = (12 + (((Integer) l[2]) + 8) * 2.4f * 0.95f) / 192f;
            Component c = Component.literal(Vhs.tr((String) l[0]));
            int tw = font.width(c);
            pose.pushPose();
            pose.translate(u * w, (1 - v) * h, 0.004f);
            pose.scale(s, -s, s);
            font.drawInBatch(c, -tw / 2f, -4, l[0].equals("bh.brief.plan.exit") ? RED_PENCIL : PENCIL, false, pose.last().pose(), buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, lit);
            pose.popPose();
        }
    }

    private void monitor(BriefSheetBlockEntity be, PoseStack pose, MultiBufferSource buffers, float w, float h) {
        long now = System.currentTimeMillis();
        int seed = be.getBlockPos().hashCode();
        // Чёрный кинескоп, по краю тёмная рамка корпуса.
        quad(pose, buffers, MEMO, w, h, FULL_BRIGHT, 8);
        // Помехи: та же текстура зерна, что в интерфейсе, со случайным сдвигом раз в 60 мс.
        long frame = now / 60 + seed;
        float ou = (frame * 37 % 64) / 128f;
        float ov = (frame * 53 % 64) / 128f;
        int a = 70 + (int) ((frame * 7919) % 50);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(Vhs.NOISE));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float inset = 0.06f;
        pose.pushPose();
        pose.translate(0, 0, 0.002f);
        m = pose.last().pose();
        int g = 150;
        vc.vertex(m, inset, inset, 0).color(g, g, g, a).uv(ou, ov + 0.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, w - inset, inset, 0).color(g, g, g, a).uv(ou + 0.5f, ov + 0.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, w - inset, h - inset, 0).color(g, g, g, a).uv(ou + 0.5f, ov).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, inset, h - inset, 0).color(g, g, g, a).uv(ou, ov).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
        pose.popPose();

        // Подписи: номер камеры в углу и «НЕТ СИГНАЛА», которое неровно гаснет.
        double wave = Math.sin(now / 700.0 + seed) * 0.5 + 0.5;
        boolean drop = ((now / 90 + seed) % 23) == 0;
        int alpha = drop ? 30 : 110 + (int) (wave * 110);
        float s = w / 120f;
        pose.pushPose();
        pose.translate(inset + 0.04f, h - inset - 0.05f, 0.006f);
        pose.scale(s, -s, s);
        font.drawInBatch(Vhs.text(Vhs.tr("bh.brief.monitor.cam", be.topic)), 0, 0, (150 << 24) | Vhs.ASH, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, FULL_BRIGHT);
        pose.popPose();
        Component ns = Vhs.text(Vhs.tr("bh.deathfx.no_signal"));
        float s2 = Math.min(w * 0.8f / Math.max(1, font.width(ns)), h * 0.18f / 9f);
        pose.pushPose();
        pose.translate(w / 2, h / 2 + 4.5f * s2, 0.006f);
        pose.scale(s2, -s2, s2);
        font.drawInBatch(ns, -font.width(ns) / 2f, 0, (Math.max(8, alpha) << 24) | Vhs.BONE, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(BriefSheetBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 40;
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class BriefSheetRendererSetup {
        @SubscribeEvent
        public static void onBhRegisterBriefSheetRenderer(EntityRenderersEvent.RegisterRenderers e) {
            e.registerBlockEntityRenderer(ModGear.BRIEF_SHEET_BE.get(), BriefSheetRenderer::new);
        }
    }
}
