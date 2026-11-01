package com.siris.viris.mixin;

import com.siris.viris.pack.VitrailBridge;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import me.cortex.voxy.client.core.IrisVoxyRenderPipeline;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block ids for the pack's material code come from Vitrail's block.properties table (Iris' table under Iris). */
@Mixin(value = IrisVoxyRenderPipeline.class, remap = false)
public class IrisVoxyRenderPipelineMixin {

    @Inject(method = "setupExtraModelBakeryData", at = @At("HEAD"), cancellable = true)
    private void viris$vitrailBlockIds(ModelBakerySubsystem modelService, CallbackInfo ci) {
        Object2IntMap<BlockState> ids = VitrailBridge.blockIds();
        if (ids != null) {
            modelService.factory.setCustomBlockStateMapping(ids);
        }
        ci.cancel();
    }
}
