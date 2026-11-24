package com.talviom.bodycamhorror.mixin;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.0, правка пользователя: на корточках край блока держит только полсекунды. Если игрок продолжает идти
 * за край дольше 10 тиков, игра перестаёт его останавливать, и он соскальзывает вниз (звук приземления
 * по блоку даёт movement/SneakSounds). Выглянуть из-за края можно, случайно упасть нельзя.
 *
 * Имя метода дано двумя способами: как в dev-клиенте (официальные имена) и как в собранной игре (SRG),
 * refmap у мода не собирается. Найдено то, что есть, второе пропускается (require = 0).
 */
@Mixin(Player.class)
public abstract class PlayerEdgeMixin {
    @Unique
    private int bodycamhorror$edgeTicks;

    @Inject(method = {"maybeBackOffFromEdge", "m_5763_"}, at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void bodycamhorror$slipOffEdge(Vec3 wanted, MoverType type, CallbackInfoReturnable<Vec3> cir) {
        Vec3 held = cir.getReturnValue();
        if (held.x == wanted.x && held.z == wanted.z) {
            bodycamhorror$edgeTicks = 0;
            return;
        }
        if (++bodycamhorror$edgeTicks > 10) cir.setReturnValue(wanted);
    }
}
