package com.siris.voxyvulkan.mixin;

import com.siris.voxyvulkan.VoxyFrame;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** This frame's terrain matrices, camera and fog, known before the game's opaque world pass runs. */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class SodiumWorldRendererMixin {

    @Shadow
    private FogParameters lastFogParameters;

    @Inject(method = "prepareChunkRendering", at = @At("RETURN"))
    private void voxyvulkan$capture(ChunkRenderMatrices matrices, double x, double y, double z, CallbackInfo ci) {
        VoxyFrame.capture(matrices.projection(), matrices.modelView(), x, y, z, lastFogParameters);
    }
}
