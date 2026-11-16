#version 330 core

#moj_import <kwrg:loading_scene.glsl>
#moj_import <kwrg:starfield.glsl>

in vec2 screenUv;
out vec4 fragColor;

#ifdef PANORAMA_WARP
uniform sampler2D PanoramaSampler;

vec3 samplePanorama(vec2 uv, vec2 radial) {
    vec2 split = normalize(radial + vec2(0.00001)) * Warp.x * smoothstep(0.18, 0.94, length(radial));
    return vec3(texture(PanoramaSampler, uv + split).r, texture(PanoramaSampler, uv).g,
            texture(PanoramaSampler, uv - split).b);
}
#endif

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
    color += starLayer(starPosition + vec2(Time * 0.002, 0.0) - Viewport.zw * 0.018, 31.0, Time, Viewport.y) * 0.57;
    color += starLayer(starPosition - vec2(Time * 0.001, 0.08), 72.0, Time, Viewport.y) * 0.22;
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

    if (Switch.y >= 0.0) {
        vec2 singularity = screenShift() * 0.5 * smoothstep(0.0, 1.0, Switch.w);
        float distanceToSingularity = length((screenUv - 0.5 - singularity) * vec2(aspect, 1.0));
        float pulse = smoothstep(0.0, 0.12, Switch.y) * (1.0 - smoothstep(0.42, 0.90, Switch.y));
        vec3 glow = exp2(-distanceToSingularity * 19.0) * vec3(0.69, 0.18, 0.96)
                + exp2(-distanceToSingularity * 88.0) * vec3(0.44, 0.95, 0.99);
        color += pulse * glow * 0.90;
    }

#ifdef PANORAMA_WARP
    vec2 uv = screenUv - 0.5;
    if (Switch.y < 0.0) {
        fragColor = vec4(samplePanorama(0.5 + uv / Switch.z, uv * 2.0), 1.0);
        return;
    }

    // The same cubemap view shrinks toward the projected core while the surrounding starfield opens up.
    vec2 center = screenShift() * 0.5 * Switch.w;
    float windowScale = max(0.008, pow(1.0 - Switch.w, 2.0));
    vec2 aperture = (uv - center) / windowScale;
    float edge = length(aperture * vec2(1.0, 1.18));
    float opening = mix(1.0, 1.0 - smoothstep(0.44, 0.57, edge), smoothstep(0.0, 0.25, Switch.w));
    opening *= 1.0 - smoothstep(0.83, 1.0, Switch.w);
    vec2 sampleUv = 0.5 + aperture - vec2(Warp.y * 0.002, -Warp.y * 0.0012);
    vec3 panorama = samplePanorama(sampleUv, uv * 2.0);
    color = mix(color, panorama, opening);
    float rim = exp(-32.0 * abs(edge - 0.49)) * smoothstep(0.02, 0.19, Switch.w) * opening;
    color += rim * vec3(0.46, 0.47, 0.92) * 0.65;
#endif

    // Only the loading burst reveals the screen beneath this pass.
    float opacity = Menu.x > 0.5 ? 1.0 : 1.0 - smoothstep(0.0, 0.69, Burst);
    fragColor = vec4(color, opacity);
}
