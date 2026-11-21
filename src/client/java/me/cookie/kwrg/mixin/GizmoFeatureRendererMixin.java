package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.cookie.kwrg.render.OverlayGizmos;
import me.cookie.kwrg.render.vertex.OverlayVertexConsumer;
import net.minecraft.client.renderer.feature.GizmoFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(GizmoFeatureRenderer.class)
public abstract class GizmoFeatureRendererMixin {
    @WrapOperation(method = {"buildLines", "buildQuads"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/GizmoFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer kwrg$overlayVertices(GizmoFeatureRenderer renderer, RenderType type,
                                                 Operation<VertexConsumer> original, @Local(argsOnly = true) List<?> primitives) {
        if (primitives instanceof OverlayGizmos.Batch<?> overlay) {
            var vertices = original.call(renderer, overlay.type);
            if (overlay.opacity == 1.0f && overlay.type.primitiveTopology() != PrimitiveTopology.LINES) return vertices;
            return new OverlayVertexConsumer(vertices, overlay.opacity);
        }
        return original.call(renderer, type);
    }
}
