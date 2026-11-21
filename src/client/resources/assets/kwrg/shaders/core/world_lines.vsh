#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;
in float LineWidth;

// Match the color-only fragment shader's interface, including on Vulkan.
out vec4 vertexColor;

vec4 projectLinePoint(vec3 position) {
    vec4 view = ModelViewMat * vec4(position, 1.0);
    // Match vanilla's slight camera-space shrink so outlines sit in front of surfaces.
    view.xyz *= 255.0 / 256.0;
    return ProjMat * view;
}

void main() {
    vec4 clip = projectLinePoint(Position);
    vec4 next = projectLinePoint(Position + Normal);
    vec2 direction = normalize((next.xy / next.w - clip.xy / clip.w) * ScreenSize);
    vec2 offset = vec2(-direction.y, direction.x) * LineWidth / ScreenSize;
    if (offset.x < 0.0) offset = -offset;
    if ((gl_VertexID & 1) != 0) offset = -offset;
    gl_Position = vec4(clip.xy + offset * clip.w, clip.zw);
    vertexColor = Color;
}
