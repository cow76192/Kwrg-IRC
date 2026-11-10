#version 330 core

#moj_import <kwrg:loading_scene.glsl>

in vec2 faceUv;
flat in vec3 normal;
flat in vec3 tint;
flat in float emitter;
flat in float isTile;
out vec4 fragColor;

void main() {
    vec2 edges = min(faceUv, 1.0 - faceUv);
    float edge = min(edges.x, edges.y);
    float rim = 1.0 - smoothstep(0.035, 0.14, edge);
    vec3 color = tint * (terrainLight(normal) + emitter * (0.62 + 0.42 * rim));
    if (isTile > 0.5) {
        color += vec3(0.03, 0.55, 0.78) * Browser.y * Browser.x * rim;
        color *= 1.0 - 0.18 * rim * (1.0 - Browser.y);
    }
    fragColor = vec4(expose(color), 1.0 - smoothstep(0.02, 0.58, ServerFlight.x));
}
