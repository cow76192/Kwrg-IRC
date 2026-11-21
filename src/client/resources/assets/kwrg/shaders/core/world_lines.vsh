#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

in vec3 Position;
in vec4 Color;
in vec2 DirectionXY;
in ivec2 DirectionZBits;
in float LineWidth;

// Match the color-only fragment shader's interface, including on Vulkan.
out vec4 vertexColor;

vec4 projectLineVector(vec4 vector) {
    vec4 view = ModelViewMat * vector;
    // Match vanilla's slight camera-space shrink so outlines sit in front of surfaces.
    view.xyz *= 255.0 / 256.0;
    return ProjMat * view;
}

void main() {
    vec4 clip = projectLineVector(vec4(Position, 1.0));
    uint zBits = (uint(DirectionZBits.x) & 0xffffu) | (uint(DirectionZBits.y) << 16);
    vec4 delta = projectLineVector(vec4(DirectionXY, uintBitsToFloat(zBits), 0.0));
    // The perspective derivative has the same direction at both ends without dividing by w.
    vec2 tangent = (delta.xy * clip.w - clip.xy * delta.w) * ScreenSize;
    float tangentLength = length(tangent);
    vec2 offset = tangentLength > 0.0
            ? vec2(-tangent.y, tangent.x) * (LineWidth / tangentLength) / ScreenSize
            : vec2(0.0);
    if ((gl_VertexID & 1) != 0) offset = -offset;
    gl_Position = vec4(clip.xy + offset * clip.w, clip.zw);
    vertexColor = Color;
}
