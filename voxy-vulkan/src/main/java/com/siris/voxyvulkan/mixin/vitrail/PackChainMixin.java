package com.siris.voxyvulkan.mixin.vitrail;

import com.siris.voxyvulkan.VoxyFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Voxy's LOD gbuffer data goes into the pack's targets before its deferred passes run. */
@Mixin(targets = "dev.vitrail.render.PackChain", remap = false)
public class PackChainMixin {

    @Inject(method = "drawBeforeTranslucents", at = @At("HEAD"))
    private static void voxyvulkan$lodGbuffer(CallbackInfo ci) {
        VoxyFrame.packComposite();
    }
}
