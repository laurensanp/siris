package com.siris.voxyvulkan.mixin;

import com.siris.voxyvulkan.AtlasReader;
import com.siris.voxyvulkan.VoxyVulkan;
import com.mojang.renderpearl.api.textures.GpuTexture;
import me.cortex.voxy.client.core.model.bakery.SoftwareModelTextureBakery;
import me.cortex.voxy.client.core.model.bakery.SoftwareRasterizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Voxy's model baker gets the block atlas from the sprites instead of reading a GL texture. */
@Mixin(value = SoftwareModelTextureBakery.class, remap = false)
public class SoftwareModelTextureBakeryMixin {

    @Shadow
    @Final
    private SoftwareRasterizer rasterizer;

    @Inject(method = "setupTexture", at = @At("HEAD"), cancellable = true)
    private void voxyvulkan$atlasFromSprites(CallbackInfo ci) {
        if (!VoxyVulkan.isVulkan()) {
            return;
        }
        GpuTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTexture();
        int width = atlas.getWidth(0);
        int height = atlas.getHeight(0);
        rasterizer.setSamplerTexture(AtlasReader.read(width, height), width, height);
        ci.cancel();
    }
}
