#version 150

uniform sampler2D Sampler0;

in vec2 vUv;

out vec4 fragColor;

const float TAU = 6.28318530718;
const float DIRECTIONS = 16.0;
const float QUALITY = 4.0;

float sdRoundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

vec2 sdRoundedBoxNormal(vec2 p, vec2 b, float r) {
    vec2 e = vec2(0.5, 0.0);
    vec2 g = vec2(
    sdRoundedBox(p + e.xy, b, r) - sdRoundedBox(p - e.xy, b, r),
    sdRoundedBox(p + e.yx, b, r) - sdRoundedBox(p - e.yx, b, r)
    );
    float len = length(g);
    return len > 0.0001 ? (g / len) : vec2(0.0, -1.0);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    float aStep = floor(vUv.x * 0.25);
    float rStep = floor(vUv.y * 0.25);
    float alpha = clamp(aStep * 0.01, 0.0, 1.0);
    float radiusRatio = clamp(rStep * 0.01, 0.0, 0.5);

    vec2 normPos = clamp(vec2(
                         vUv.x - (aStep * 4.0 + 1.0),
                         vUv.y - (rStep * 4.0 + 1.0)
                         ), vec2(0.0), vec2(1.0));

    // 通过屏幕空间导数直接算出卡片在屏幕上的真实物理像素宽高
    float dx = length(vec2(dFdx(vUv.x), dFdy(vUv.x)));
    float dy = length(vec2(dFdx(vUv.y), dFdy(vUv.y)));
    vec2 cardPxSize = vec2(1.0 / max(dx, 1e-5), 1.0 / max(dy, 1e-5));

    // 圆角矩形 SDF,抗锯齿
    vec2 halfSize = cardPxSize * 0.5;
    vec2 p = normPos * cardPxSize - halfSize;
    float rPx = min(cardPxSize.y * radiusRatio, min(halfSize.x, halfSize.y));
    float dist = sdRoundedBox(p, halfSize, rPx);

    float mask = 1.0 - smoothstep(-0.75, 0.75, dist);
    if (mask <= 0.001 || alpha <= 0.001) {
        discard;
    }

    vec2 texSize = max(vec2(textureSize(Sampler0, 0)), vec2(1.0));
    vec2 edgeNormal = sdRoundedBoxNormal(p, halfSize, rPx);

    // 玻璃边缘折射偏移
    float bevelWidth = max(4.0, cardPxSize.y * 0.22);
    float bevelBand = smoothstep(-bevelWidth, 0.0, dist);
    vec2 baseUv = gl_FragCoord.xy / texSize;
    vec2 refractDir = vec2(edgeNormal.x, -edgeNormal.y);
    vec2 uv = clamp(baseUv - refractDir * (bevelBand * bevelWidth * 0.35) / texSize, vec2(0.0), vec2(1.0));

    // 径向毛玻璃模糊
    float blurPx = max(8.0, cardPxSize.y * 0.22);
    vec2 radiusStep = vec2(blurPx) / texSize;
    vec3 blurred = texture(Sampler0, uv).rgb;
    float stepAngle = TAU / DIRECTIONS;
    for (float d = 0.0; d < TAU; d += stepAngle) {
        vec2 dir = vec2(cos(d), sin(d)) * radiusStep;
        for (float i = 1.0 / QUALITY; i <= 1.0001; i += 1.0 / QUALITY) {
            blurred += texture(Sampler0, clamp(uv + dir * i, vec2(0.0), vec2(1.0))).rgb;
        }
    }
    blurred /= (QUALITY * DIRECTIONS + 1.0);

    // 色散
    if (bevelBand > 0.01) {
        vec2 chromaShift = refractDir * (bevelBand * max(1.5, bevelWidth * 0.14)) / texSize;
        float rCh = texture(Sampler0, clamp(uv + chromaShift, vec2(0.0), vec2(1.0))).r;
        float bCh = texture(Sampler0, clamp(uv - chromaShift, vec2(0.0), vec2(1.0))).b;
        blurred = mix(blurred, vec3(rCh, blurred.g, bCh), 0.35 * bevelBand);
    }

    // 背景饱和度
    float bgLuma = dot(blurred, vec3(0.299, 0.587, 0.114));
    vec3 vibrantBg = mix(vec3(bgLuma), blurred, 1.18);
    float tintStrength = mix(0.12, 0.28, smoothstep(0.35, 0.85, bgLuma));
    vec3 glassColor = mix(vibrantBg, vec3(0.06, 0.09, 0.14), tintStrength);

    // 顶部微光
    float topSheen = pow(1.0 - normPos.y, 3.8) * 0.05;
    float diagGlare = smoothstep(0.55, 0.0, normPos.x * 0.45 + normPos.y) * 0.025;
    glassColor += vec3(0.90, 0.96, 1.0) * (topSheen + diagGlare);

    // 倒角细线
    float rimWidth = max(1.0, cardPxSize.y * 0.035);
    float rimMask = smoothstep(-rimWidth, -0.35, dist) * (1.0 - smoothstep(-0.35, 0.45, dist));
    vec2 lightDir = normalize(vec2(-0.25, -0.97));
    float rimLight = clamp(dot(edgeNormal, lightDir) * 0.5 + 0.5, 0.0, 1.0);
    float rimIntensity = mix(0.03, 0.14, pow(rimLight, 1.8));
    glassColor += vec3(0.92, 0.97, 1.0) * (rimMask * rimIntensity);

    // 磨砂颗粒
    float grain = (hash12(gl_FragCoord.xy) - 0.5) * 0.014;
    glassColor = clamp(glassColor + grain, 0.0, 1.0);

    fragColor = vec4(glassColor, mask * alpha);
}