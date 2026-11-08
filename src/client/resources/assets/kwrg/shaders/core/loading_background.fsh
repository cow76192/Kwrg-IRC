#version 330 core

#moj_import <kwrg:loading_scene.glsl>

in vec2 screenUv;
out vec4 fragColor;

float hash(vec2 cell) {
    vec3 p = fract(vec3(cell.xyx) * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

vec3 starLayer(vec2 p, float scale, float time) {
    vec2 cell = floor(p * scale);
    float seed = hash(cell);
    if (seed < 0.73) {
        return vec3(0.0);
    }
    vec2 offset = vec2(hash(cell + 17.0), hash(cell + 49.0));
    float distanceToStar = length(fract(p * scale) - mix(vec2(0.2), vec2(0.8), offset));
    float radius = mix(0.018, 0.063, seed * seed);
    float pixel = scale * 2.0 / Viewport.y;
    float star = 1.0 - smoothstep(radius, radius + pixel, distanceToStar);
    float halo = exp(-distanceToStar * 28.0) * seed * seed;
    float twinkle = 0.72 + 0.28 * sin(time * (0.7 + seed * 1.8) + seed * 56.0);
    vec3 tint = mix(vec3(0.36, 0.48, 0.82), vec3(0.85, 0.96, 1.0), offset.x);
    return tint * (star + halo) * twinkle * smoothstep(0.73, 1.0, seed);
}

float roundedTrack(vec2 p, float halfLength, float radius) {
    p.x -= clamp(p.x, -halfLength, halfLength);
    return 1.0 - smoothstep(radius, radius + 1.0 / Viewport.y, length(p));
}

void main() {
    float aspect = Viewport.x / Viewport.y;
    vec2 p = (screenUv * 2.0 - 1.0 - screenShift()) * vec2(aspect, 1.0);
    vec2 starPosition = p + vec2(Menu.y * aspect * 0.23, 0.0);
    float radius = length(p);
    float nebula = exp(-4.4 * abs(p.y + 0.23 * p.x + 0.19 * sin(2.2 * p.x)))
            * exp(-0.48 * dot(p, p));
    float vignette = 1.0 - 0.62 * smoothstep(0.18, 1.5, radius);
    vec3 color = vec3(0.014, 0.022, 0.042) + nebula * vec3(0.022, 0.054, 0.078);
    color += starLayer(starPosition + vec2(Time * 0.002, 0.0) - Viewport.zw * 0.018, 31.0, Time) * 0.57;
    color += starLayer(starPosition - vec2(Time * 0.001, 0.08), 72.0, Time) * 0.22;
    color *= vignette;

    float charge = chargeStrength();
    float core = (1.0 - smoothstep(0.26, 0.71, Progress)) * (0.82 + 0.18 * sin(Time * 4.0));
    color += exp(-7.2 * radius) * (core + charge * 0.77) * vec3(0.77, 0.38, 0.20);
    color += exp(-3.9 * radius) * (0.32 + 0.68 * core + charge * 0.80) * vec3(0.055, 0.29, 0.39);
    color += exp(-4.7 * radius) * Menu.y * menuPalette(0.62) * 0.16;
    color *= 1.0 - Menu.y * (1.0 - smoothstep(0.15, 0.56, screenUv.x)) * 0.45;

    if (Burst > 0.0 && Burst < 0.46) {
        float separation = 0.035 * releasePulse() * smoothstep(0.55, 1.4, radius);
        vec3 ring = exp(-abs(vec3(radius) + vec3(-separation, 0.0, separation) - 4.9 * Burst) * 27.0);
        color += ring * vec3(0.46, 0.53, 0.62) * smoothstep(0.0, 0.055, Burst)
                * (1.0 - smoothstep(0.12, 0.46, Burst));
    }

    float halfLength = min(0.27, 0.33 * aspect);
    vec2 track = (screenUv - vec2(0.5, 0.145)) * vec2(aspect, 1.0);
    float rail = roundedTrack(track, halfLength, 0.0035);
    if (Menu.y < 0.99 && abs(track.y) < 0.039 && abs(track.x) < halfLength + 0.040) {
        float tip = mix(-halfLength, halfLength, Progress);
        float lit = rail * (1.0 - smoothstep(tip, tip + 1.0 / Viewport.y, track.x)) * step(0.001, Progress);
        vec3 accent = mix(vec3(0.42, 0.72, 0.97), vec3(0.66, 0.95, 0.55), smoothstep(BUILD_START, BUILD_END, Progress));
        float tipGlow = exp(-135.0 * length(track - vec2(tip, 0.0))) * smoothstep(0.0, 0.04, Progress);
        float trackFade = Opacity * (1.0 - smoothstep(0.0, 0.15, Burst)) * (1.0 - Menu.y);
        color += accent * (rail * 0.17 + lit * 0.80 + tipGlow * (0.42 + 0.40 * charge)) * trackFade;
    }

    // Only the burst reveals the screen beneath the loading scene.
    float opacity = Menu.x > 0.5 ? 1.0 : 1.0 - smoothstep(0.0, 0.69, Burst);
    fragColor = vec4(color, opacity);
}
