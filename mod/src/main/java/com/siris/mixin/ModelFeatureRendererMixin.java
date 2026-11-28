package com.siris.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.siris.EntityMotion;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Posed model cubes of the entities drawn this frame (after setupAnim), for per-part motion vectors. */
@Mixin(ModelFeatureRenderer.class)
public class ModelFeatureRendererMixin {

    @WrapOperation(method = "prepareModel", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void siris$recordParts(Model<?> model, PoseStack poseStack, VertexConsumer buffer, int light, int overlay,
        int color, Operation<Void> original, @Local(argsOnly = true) ModelFeatureRenderer.Submit<?> submit) {
        EntityMotion.recordModel(submit.state(), model, poseStack);
        original.call(model, poseStack, buffer, light, overlay, color);
    }
}
