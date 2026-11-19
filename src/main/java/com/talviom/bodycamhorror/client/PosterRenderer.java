package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.gear.ModGear;
import com.talviom.bodycamhorror.lobby.PosterBlock;
import com.talviom.bodycamhorror.lobby.PosterBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Рисует плакат 6x3 блока вплотную к стене: текстура textures/gui/poster.png (tools/assets/make_poster.py).
 * Свет чуть приподнят, чтобы в тёмном лобби текст читался, но плакат не светился.
 */
public class PosterRenderer implements BlockEntityRenderer<PosterBlockEntity> {
    private static final ResourceLocation TEX = new ResourceLocation(BodycamHorror.MOD_ID, "textures/gui/poster.png");
    private static final int MIN_LIGHT = 9;

    public PosterRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(PosterBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(PosterBlock.FACING);
        Direction right = facing.getCounterClockWise();
        int block = Math.max(LightTexture.block(light), MIN_LIGHT);
        int lit = LightTexture.pack(block, LightTexture.sky(light));

        // Левый нижний угол: середина блока, сдвинутая к стене и к левому краю.
        float ox = 0.5f - facing.getStepX() * 0.49f - right.getStepX() * 0.5f;
        float oz = 0.5f - facing.getStepZ() * 0.49f - right.getStepZ() * 0.5f;
        float rx = right.getStepX() * PosterBlock.WIDTH;
        float rz = right.getStepZ() * PosterBlock.WIDTH;
        float h = PosterBlock.HEIGHT;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float nx = facing.getStepX();
        float nz = facing.getStepZ();
        vertex(vc, m, n, ox, 0, oz, 0, 1, lit, nx, nz);
        vertex(vc, m, n, ox + rx, 0, oz + rz, 1, 1, lit, nx, nz);
        vertex(vc, m, n, ox + rx, h, oz + rz, 1, 0, lit, nx, nz);
        vertex(vc, m, n, ox, h, oz, 0, 0, lit, nx, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int light,
                               float nx, float nz) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, nx, 0, nz).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(PosterBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class PosterRendererSetup {
        @SubscribeEvent
        public static void onBhRegisterPosterRenderer(EntityRenderersEvent.RegisterRenderers e) {
            e.registerBlockEntityRenderer(ModGear.POSTER_BE.get(), PosterRenderer::new);
        }
    }
}
