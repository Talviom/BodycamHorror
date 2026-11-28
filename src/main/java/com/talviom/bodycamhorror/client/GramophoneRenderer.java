package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.gear.ModGear;
import com.talviom.bodycamhorror.lobby.TapeRecorderBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Пластинка на граммофоне лобби (блок tape_recorder): круглая текстура gramophone_record.png на диске проигрывателя,
 * крутится, пока граммофон играет (угол считает TapeRecorderBlockEntity.spinTick). Модель самого граммофона
 * обычная, из make_gramophone.py; пластинки в ней нет, поэтому она есть только здесь.
 */
public class GramophoneRenderer implements BlockEntityRenderer<TapeRecorderBlockEntity> {
    private static final ResourceLocation RECORD = new ResourceLocation(BodycamHorror.MOD_ID, "textures/block/gramophone_record.png");
    /** Высота пластинки над низом блока и её радиус, в пикселях модели (как в make_gramophone.py). */
    private static final float Y = 5.3f / 16f, R = 4.5f / 16f;

    public GramophoneRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(TapeRecorderBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, Y, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partial, be.prevSpin, be.spin)));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(RECORD));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        vertex(vc, m, n, -R, -R, 0, 0, light);
        vertex(vc, m, n, -R, R, 0, 1, light);
        vertex(vc, m, n, R, R, 1, 1, light);
        vertex(vc, m, n, R, -R, 1, 0, light);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float z, float u, float v, int light) {
        vc.vertex(m, x, 0, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, 0, 1, 0).endVertex();
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class GramophoneRendererSetup {
        @SubscribeEvent
        public static void onBhRegisterGramophoneRenderer(EntityRenderersEvent.RegisterRenderers e) {
            e.registerBlockEntityRenderer(ModGear.TAPE_RECORDER_BE.get(), GramophoneRenderer::new);
        }
    }
}
