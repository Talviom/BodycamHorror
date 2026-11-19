package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.talviom.bodycamhorror.gear.BandageItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Клиент: как выглядит перевязка. В первом лице рулон поднимается к центру кадра и ходит кругами,
 * будто мотаешь бинт на другую руку, а кисть проворачивается, подавая бинт; в конце рывок, затягиваешь узел.
 * В третьем лице обе руки перед грудью, рука с рулоном описывает те же круги.
 * Один оборот занимает {@link #TURN_TICKS} тиков, в такт шороху ткани из BandageItem.
 */
public final class BandageClient {
    /** Тиков на один оборот бинта (как WRAP_SOUND_TICKS в BandageItem). */
    private static final float TURN_TICKS = 9f;

    private static final HumanoidModel.ArmPose WRAP_POSE = HumanoidModel.ArmPose.create("BODYCAMHORROR_BANDAGE", true,
            (model, entity, arm) -> poseArms(model, entity));

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return entity.isUsingItem() && entity.getUsedItemHand() == hand ? WRAP_POSE : null;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                               float partialTick, float equipProcess, float swingProcess) {
            if (!player.isUsingItem() || player.getUseItem() != stack) return false;
            wrapInHand(pose, player, arm, partialTick, equipProcess);
            return true;
        }
    };

    private BandageClient() {}

    /** Доля пройденной перевязки, 0..1. */
    public static float progress(LivingEntity e, float partialTick) {
        float used = BandageItem.USE_TICKS - e.getUseItemRemainingTicks() + partialTick;
        return Mth.clamp(used / BandageItem.USE_TICKS, 0f, 1f);
    }

    private static void wrapInHand(PoseStack pose, LocalPlayer player, HumanoidArm arm, float partialTick, float equip) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float p = progress(player, partialTick);
        float used = BandageItem.USE_TICKS - player.getUseItemRemainingTicks() + partialTick;
        // Подъём к центру за первые 0,3 секунды, опускание в последние 0,15.
        float raise = smooth(Mth.clamp(used / 6f, 0f, 1f)) * (1f - smooth(Mth.clamp((p - 0.92f) / 0.08f, 0f, 1f)));
        float angle = used / TURN_TICKS * Mth.TWO_PI;
        // Последняя пятая часть: не круги, а рывок, затягиваешь.
        float tug = p > 0.8f ? Mth.sin((p - 0.8f) / 0.2f * Mth.PI) : 0f;
        float circle = raise * (1f - tug);

        // Обычное место предмета в руке (как ItemInHandRenderer.applyItemArmTransform).
        pose.translate(side * 0.56f, -0.52f + equip * -0.6f, -0.72f);
        // К центру и чуть выше: там «вторая рука», на которую мотаем бинт.
        pose.translate(-side * 0.30f * raise, 0.16f * raise, 0.06f * raise);
        pose.translate(0f, Mth.sin(angle) * 0.07f * circle, Mth.cos(angle) * 0.05f * circle);
        pose.translate(side * 0.05f * tug, -0.03f * tug, 0.04f * tug);
        pose.mulPose(Axis.YP.rotationDegrees(side * 18f * raise));
        pose.mulPose(Axis.ZP.rotationDegrees(side * (-12f * raise + 10f * tug)));
        // Рулон покачивается в такт оборотам: кисть проворачивается, подавая бинт.
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(angle) * 14f * circle));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.cos(angle) * 8f * circle));
    }

    /** Третье лицо: обе руки перед грудью, рука с рулоном ходит кругами. */
    private static void poseArms(HumanoidModel<?> model, LivingEntity entity) {
        boolean mainRight = entity.getMainArm() == HumanoidArm.RIGHT;
        boolean usingRight = (entity.getUsedItemHand() == InteractionHand.MAIN_HAND) == mainRight;
        float used = BandageItem.USE_TICKS - entity.getUseItemRemainingTicks();
        float angle = used / TURN_TICKS * Mth.TWO_PI;
        var wrapping = usingRight ? model.rightArm : model.leftArm;
        var held = usingRight ? model.leftArm : model.rightArm;
        float side = usingRight ? 1f : -1f;
        // Рука, на которую мотают: вытянута вперёд и чуть внутрь.
        held.xRot = -1.05f + model.head.xRot * 0.3f;
        held.yRot = side * 0.45f;
        held.zRot = 0f;
        // Рука с рулоном: над ней, круги вокруг.
        wrapping.xRot = -1.25f + Mth.sin(angle) * 0.22f + model.head.xRot * 0.3f;
        wrapping.yRot = -side * 0.55f + Mth.cos(angle) * 0.18f;
        wrapping.zRot = 0f;
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }
}
