package me.cookie.kwrg.render.vertex;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.util.ARGB;

public record OverlayVertexConsumer(VertexConsumer delegate, float opacity) implements VertexConsumer {
    public static final VertexFormat LINE_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("DirectionXY", VertexFormatElement.UV0)
            .add("DirectionZBits", VertexFormatElement.UV1)
            .add("LineWidth", VertexFormatElement.LINE_WIDTH)
            .build();

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        return setColor(ARGB.color(alpha, red, green, blue));
    }

    @Override
    public VertexConsumer setColor(int argb) {
        delegate.setColor(ARGB.multiplyAlpha(argb, opacity));
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        // Gizmos send the segment delta here. Packed normals would clamp it to [-1, 1].
        int zBits = Float.floatToRawIntBits(z);
        delegate.setUv(x, y).setUv1(zBits & 0xffff, zBits >>> 16);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }
}
