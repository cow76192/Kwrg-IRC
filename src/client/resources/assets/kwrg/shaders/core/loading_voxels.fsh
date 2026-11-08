#version 330 core

#moj_import <kwrg:loading_scene.glsl>

in vec2 faceUv;
in vec3 toEye;
flat in vec3 faceNormal;
flat in vec4 voxelColor;
flat in float emission;
out vec4 fragColor;

void main() {
    vec2 edges = min(faceUv, 1.0 - faceUv);
    float seam = 1.0 - smoothstep(0.0, 0.14, min(edges.x, edges.y));
    float rim = fresnel(faceNormal, toEye) * (0.09 + 0.51 * emission);
    vec3 color = voxelColor.rgb * (0.92 + emission * 0.86) + (rim + seam * emission * 0.26) * vec3(0.35, 0.75, 0.97);

    if (Burst > 0.0 && Burst < 0.30) {
        vec2 screen = gl_FragCoord.xy / Viewport.xy * 2.0 - 1.0;
        float amount = smoothstep(0.49, 1.05, length(screen)) * releasePulse();
        vec2 offset = normalize(screen + vec2(0.001)) * (0.14 * amount);
        vec2 warm = abs(faceUv - 0.5 - offset);
        vec2 cool = abs(faceUv - 0.5 + offset);
        vec2 split = smoothstep(vec2(0.38), vec2(0.5), vec2(max(warm.x, warm.y), max(cool.x, cool.y)));
        color += amount * vec3(split.x * 0.65, split.y * 0.14, split.y * 0.76);
    }
    fragColor = vec4(expose(color), voxelColor.a);
}
