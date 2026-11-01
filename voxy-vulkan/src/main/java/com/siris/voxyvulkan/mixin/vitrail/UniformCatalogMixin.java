package com.siris.voxyvulkan.mixin.vitrail;

import com.siris.voxyvulkan.pack.VoxyPackUniforms;
import com.llamalad7.mixinextras.sugar.Local;
import dev.vitrail.uniform.UniformCatalog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Voxy's vx* uniforms join Vitrail's engine uniforms, next to Distant Horizons' dh* ones. */
@Mixin(value = UniformCatalog.class, remap = false)
public class UniformCatalogMixin {

    @Inject(method = "engine", at = @At(value = "INVOKE",
        target = "Ldev/vitrail/uniform/values/DhValues;register(Ldev/vitrail/uniform/UniformCatalog$Builder;)V", shift = At.Shift.AFTER))
    private static void voxyvulkan$voxyUniforms(CallbackInfoReturnable<UniformCatalog> cir, @Local UniformCatalog.Builder builder) {
        VoxyPackUniforms.register(builder);
    }
}
