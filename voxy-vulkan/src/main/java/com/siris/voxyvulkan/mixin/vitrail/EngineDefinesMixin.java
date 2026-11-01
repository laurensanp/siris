package com.siris.voxyvulkan.mixin.vitrail;

import com.siris.voxyvulkan.GlContext;
import dev.vitrail.pack.option.EngineDefines;
import java.util.Map;
import me.cortex.voxy.client.config.VoxyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Packs see VOXY (3, Voxy's patch format) as under Iris with Voxy, so their passes read Voxy's depth. */
@Mixin(value = EngineDefines.class, remap = false)
public class EngineDefinesMixin {

    @Inject(method = "table(Ldev/vitrail/pack/option/EngineDefines$Environment;)Ljava/util/Map;", at = @At("RETURN"))
    private static void voxyvulkan$voxyDefine(EngineDefines.Environment environment, CallbackInfoReturnable<Map<String, String>> cir) {
        if (GlContext.ready() && VoxyConfig.CONFIG.isRenderingEnabled()) {
            cir.getReturnValue().put("VOXY", "3");
        }
    }
}
