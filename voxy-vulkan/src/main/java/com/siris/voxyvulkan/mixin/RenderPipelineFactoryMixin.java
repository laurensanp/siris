package com.siris.voxyvulkan.mixin;

import com.siris.voxyvulkan.VoxyFrame;
import com.siris.voxyvulkan.pack.PackPipeline;
import java.util.function.BooleanSupplier;
import me.cortex.voxy.client.core.AbstractRenderPipeline;
import me.cortex.voxy.client.core.IrisVoxyRenderPipeline;
import me.cortex.voxy.client.core.RenderPipelineFactory;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical.NodeCleaner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With a Vitrail shader pack that supports Voxy, Voxy gets its shader pack pipeline as it would under Iris. */
@Mixin(value = RenderPipelineFactory.class, remap = false)
public class RenderPipelineFactoryMixin {

    @Inject(method = "createPipeline", at = @At("HEAD"), cancellable = true)
    private static void voxyvulkan$packPipeline(RenderProperties properties, AsyncNodeManager nodeManager, NodeCleaner nodeCleaner,
        HierarchicalOcclusionTraverser traversal, BooleanSupplier frexSupplier, CallbackInfoReturnable<AbstractRenderPipeline> cir) {
        PackPipeline pack = VoxyFrame.packPipelineForFactory();
        if (pack != null && pack.data().thePipeline == null) {
            cir.setReturnValue(new IrisVoxyRenderPipeline(properties, pack.data(), nodeManager, nodeCleaner, traversal, frexSupplier));
        }
    }
}
