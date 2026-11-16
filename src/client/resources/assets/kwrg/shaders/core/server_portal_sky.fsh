#version 330 core

#moj_import <kwrg:server_portal.glsl>
#moj_import <kwrg:starfield.glsl>

in vec2 screenUv;
out vec4 fragColor;

void main() {
    vec2 p = (screenUv * 2.0 - 1.0) * vec2(Viewport.x / Viewport.y, 1.0);
    float radius = length(p);
    if (Arrival.x >= 0.0) {
        float edge = smoothstep(0.25, 1.35, radius);
        float fade = 1.0 - smoothstep(0.0, 0.50, Arrival.x);
        fragColor = vec4(mix(vec3(0.61, 0.23, 0.90), vec3(0.88, 0.61, 1.0), edge), 0.42 * fade);
        return;
    }
    float nebula = exp(-3.9 * abs(p.y + 0.23 * p.x + 0.19 * sin(2.2 * p.x)));
    float radial = exp(-1.9 * dot(p - vec2(0.0, 0.11), p - vec2(0.0, 0.11)));
    vec3 color = vec3(0.020, 0.023, 0.053) + nebula * vec3(0.027, 0.043, 0.073);
    color += radial * vec3(0.090, 0.060, 0.163);
    color += 0.48 * starLayer(p + vec2(Viewport.w * 0.002, 0.0), 31.0, Viewport.w, Viewport.y);
    color *= 1.0 - 0.42 * smoothstep(0.35, 1.55, radius);
    color += exp(-3.0 * radius) * portalOpen() * vec3(0.18, 0.034, 0.30);
    color += ignitionFlash() * exp(-4.8 * length(p + vec2(0.0, 0.26))) * vec3(0.68, 0.40, 0.25);
    color = mix(color, vec3(0.44, 0.13, 0.67), 0.82 * smoothstep(0.73, 1.0, Arrival.z) * step(State.w, -0.001));
    if (State.w >= 0.0) {
        color *= 1.0 - 0.15 * pressureGlow();
        color += ruptureFlash() * exp(-3.8 * radius) * vec3(0.30, 0.07, 0.40);
        color = mix(color, vec3(0.55, 0.08, 0.15), 0.29 * ruptureFlash());
    }
    if (Journey.x >= 0.0) {
        color = mix(color, vec3(0.64, 0.25, 0.90), 0.82 * (1.0 - smoothstep(0.0, 0.20, Journey.x)));
    }
    fragColor = vec4(color, State.x);
}
