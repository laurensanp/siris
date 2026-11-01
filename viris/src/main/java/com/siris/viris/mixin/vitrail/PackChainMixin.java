package com.siris.viris.mixin.vitrail;

import com.siris.viris.VoxyFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Voxy's LOD gbuffer data goes into the pack's targets before its deferred passes run. */
@Mixin(targets = "dev.vitrail.render.PackChain", remap = false)
public class PackChainMixin {

    @Inject(method = "drawBeforeTranslucents", at = @At("HEAD"))
    private static void viris$lodGbuffer(CallbackInfo ci) {
        VoxyFrame.packComposite();
    }
}
