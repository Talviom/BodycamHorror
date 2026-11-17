package com.talviom.bodycamhorror.mixin;

import com.talviom.bodycamhorror.compat.oculus.OculusUniforms;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Добавляет uniform-переменные мода (bhFlashlight, bhFear) ко всем программам шейдера Oculus.
 * Pseudo и необязательный конфиг: без Oculus миксин молча пропускается.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class OculusCommonUniformsMixin {
    @Inject(method = "generalCommonUniforms", at = @At("TAIL"), remap = false, require = 0)
    private static void bodycamhorror$addUniforms(UniformHolder uniforms, FrameUpdateNotifier updateNotifier,
                                                  PackDirectives directives, CallbackInfo ci) {
        OculusUniforms.register(uniforms);
    }
}
