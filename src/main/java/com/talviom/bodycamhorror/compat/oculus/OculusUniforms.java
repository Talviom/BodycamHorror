package com.talviom.bodycamhorror.compat.oculus;

import com.talviom.bodycamhorror.client.ShaderState;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;

/**
 * Свои uniform-переменные для шейдера. Вызывается только из миксина в Oculus,
 * поэтому без Oculus этот класс никогда не загружается.
 *
 * В шейдере: uniform float bhFlashlight; uniform float bhFear; uniform float bhEmergency (0.9, красный свет аварийных ламп);
 * Без нашего мода шейдер получает 0 (фонарика нет, страха нет).
 */
public final class OculusUniforms {
    private OculusUniforms() {}

    public static void register(UniformHolder uniforms) {
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "bhFlashlight", (FloatSupplier) ShaderState::flashlight);
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "bhFear", (FloatSupplier) ShaderState::fear);
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "bhEmergency", (FloatSupplier) ShaderState::emergency);
    }
}
