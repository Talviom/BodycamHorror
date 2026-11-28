package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиент, 0.9.8: колба «ОБРАЗЕЦ №7» висит за спиной у того, кто её несёт (видно всем, кроме него самого в первом лице).
 * Кто несёт, сообщает сервер (SpecimenCarrierPacket). Стрелять несущему это не мешает: колба не в руках.
 */
public class SpecimenBackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static int carrierId = -1;
    private static ItemStack stack;

    public SpecimenBackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void setCarrier(int entityId) {
        carrierId = entityId;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.getId() != carrierId || player.isInvisible() || player.isSpectator()) return;
        if (stack == null) stack = new ItemStack(ModBlocks.TAPE.get());
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        // Пространство модели: y вниз, +z назад. Колба на ремне посередине спины, чуть наклонена.
        pose.translate(0.0, 0.22, 0.30);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.mulPose(Axis.XP.rotationDegrees(-8));
        pose.scale(1.2f, -1.2f, -1.2f);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
        pose.popPose();
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class SpecimenLayerEvents {
        @SubscribeEvent
        public static void addSpecimenLayer(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                if (e.getSkin(skin) instanceof PlayerRenderer r) r.addLayer(new SpecimenBackLayer(r));
            }
        }
    }
}
