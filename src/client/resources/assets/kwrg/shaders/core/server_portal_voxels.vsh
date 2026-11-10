#version 330 core

#moj_import <kwrg:server_portal.glsl>
#moj_import <kwrg:voxel_geometry.glsl>
#moj_import <kwrg:portal_stage.glsl>

out vec2 faceUv;
out vec3 localPosition;
flat out vec4 tint;
flat out int material;
flat out int faceIndex;
flat out float facet;
flat out uvec4 contactFootprints;

const float BLOCK = 0.29;
const float HALF_STAGE = 1.28;
const float TENSION_HELD = 0.15;
const ivec2 TETHER_EDGES[11] = ivec2[11](
        ivec2(10, 11), ivec2(11, 12), ivec2(12, 13),
        ivec2(0, 4), ivec2(4, 6), ivec2(6, 8), ivec2(8, 10),
        ivec2(3, 5), ivec2(5, 7), ivec2(7, 9), ivec2(9, 13));

struct FrameBlockPose {
    vec3 center;
    vec3 halfSize;
    vec3 rotation;
    float opacity;
};

vec3 portalBlockSeed(int index) {
    uint hash = uint(Palette.x) ^ (uint(index + 1) * 0x9e3779b9u);
    hash = (hash ^ (hash >> 16u)) * 0x7feb352du;
    hash = (hash ^ (hash >> 15u)) * 0x846ca68bu;
    return vec3(hash & 1023u, (hash >> 10u) & 1023u, (hash >> 20u) & 1023u) / 1024.0;
}

int blockMaterial(int index) {
    return (Palette.y & (1 << index)) == 0 ? 1 : 5;
}

int blockRow(int index) {
    return index < 4 ? 0 : index < 10 ? 1 + (index - 4) / 2 : 4;
}

int blockColumn(int index) {
    return index < 4 ? index : index < 10 ? (index - 4) % 2 * 3 : index - 10;
}

vec3 frameSlot(int index) {
    return vec3((float(blockColumn(index)) - 1.5) * BLOCK, (float(blockRow(index)) + 0.5) * BLOCK, 0.0);
}

float orbitSpeed(vec3 seed) {
    return mix(0.45, 0.63, seed.z);
}

float blockBreakTime(int index) {
    return BREAK_START + float(4 - blockRow(index)) * 0.03;
}

vec3 frameJitter(int index, float time, float stop) {
    float frame = time * 94.0;
    float key = float(index) * 127.3 + floor(frame) * 17.1;
    vec3 sampleA = randomSeed(key);
    vec3 sampleB = randomSeed(key + 17.1);
    float envelope = smoothstep(0.0, 0.018, time) * (1.0 - smoothstep(stop - 0.035, stop, time));
    return (2.0 * mix(sampleA, sampleB, fract(frame)) - 1.0) * envelope;
}

float tetherTension(float time) {
    return smoothstep(0.075, TENSION_HELD, time);
}

vec3 strainedSlot(int index, vec3 seed, float time) {
    vec3 slot = frameSlot(index);
    if (index < ANCHOR_BLOCKS) return slot;
    vec3 outward = normalize(slot - PORTAL_CORE + vec3(0.0, 0.0, (seed.z - 0.5) * 0.25));
    return slot + outward * (0.5 * BLOCK * tetherTension(time));
}

vec3 strainedJitter(int index, float time, vec3 jitter) {
    float amplitude = BLOCK * mix(0.08, 0.19, tetherTension(time));
    return jitter * amplitude * (index < ANCHOR_BLOCKS ? vec3(0.55, 0.0, 0.55) : vec3(1.0));
}

vec3 orbitPosition(int index, vec3 seed, float time, float extraAngle, float extraRadius) {
    int rank = index < 10 ? (index - 4) / 2 : 3 + (index - 10) % 2;
    float startAngle = (blockColumn(index) < 2 ? TAU * 0.5 : 0.0) - 1.13
            + float(rank) * 0.56 + (seed.x - 0.5) * 0.35;
    float release = blockBreakTime(index);
    float age = max(0.0, time - release);
    float captured = ORBIT_CAPTURE - release;
    float peak = mix(0.26, 0.30, seed.x);
    float turnDuration = captured - peak;
    float turn = clamp((age - peak) / turnDuration, 0.0, 1.0);
    // Integrate the angular acceleration so the tangent is continuous at capture.
    float angularTravel = turnDuration * turn * turn * turn * (1.0 - 0.5 * turn) + max(0.0, age - captured);
    float direction = (Palette.x & 1) == 0 ? 1.0 : -1.0;
    float angle = startAngle + direction * (orbitSpeed(seed) * angularTravel + extraAngle);
    float radius = mix(3.04, mix(2.24, 2.64, seed.y), smoothstep(peak, captured, age)) + extraRadius;
    vec2 ring = vec2(cos(angle), sin(angle));
    vec3 slot = strainedSlot(index, seed, time);
    float remaining = 1.0 - clamp(age / peak, 0.0, 1.0);
    vec2 horizontal = mix(slot.xz, ring * radius, 1.0 - remaining * remaining * remaining);
    float ringHeight = -0.20 + 0.66 * ring.x + 0.19 * ring.y;
    float lift = clamp(age / 0.54, 0.0, 1.0);
    float rise = 3.51 * lift * (1.0 - lift) * (1.0 - lift);
    float height = mix(slot.y, ringHeight, smoothstep(0.10, captured, age)) + rise;
    return vec3(horizontal.x, height, horizontal.y);
}

vec3 rotateX(vec3 p, float a) {
    return vec3(p.x, cos(a) * p.y - sin(a) * p.z, sin(a) * p.y + cos(a) * p.z);
}

vec3 rotateZ(vec3 p, float a) {
    return vec3(cos(a) * p.x - sin(a) * p.y, sin(a) * p.x + cos(a) * p.y, p.z);
}

vec3 rotateBlock(vec3 p, vec3 rotation) {
    p = rotateX(p, rotation.x);
    float c = cos(rotation.y);
    float s = sin(rotation.y);
    p = vec3(c * p.x + s * p.z, p.y, -s * p.x + c * p.z);
    return rotateZ(p, rotation.z);
}

vec3 rotatedExtents(vec3 halfSize, vec3 rotation) {
    vec3 x = abs(rotateBlock(vec3(halfSize.x, 0.0, 0.0), rotation));
    vec3 y = abs(rotateBlock(vec3(0.0, halfSize.y, 0.0), rotation));
    vec3 z = abs(rotateBlock(vec3(0.0, 0.0, halfSize.z), rotation));
    return x + y + z;
}

float easeOutBack(float t) {
    float x = t - 1.0;
    return 1.0 + 2.70 * x * x * x + 1.70 * x * x;
}

vec3 stageView(vec3 world) {
    vec3 back = normalize(PORTAL_STAGE_EYE - PORTAL_STAGE_FOCUS);
    float retreat = 0.44 * smoothstep(0.0, 0.23, Arrival.z);
    float dash = smoothstep(0.21, 1.0, Arrival.z);
    vec3 eye = mix(PORTAL_STAGE_EYE + back * retreat, vec3(0.0, 0.74, -0.36), dash);
    back = normalize(mix(back, vec3(0.0, 0.0, 1.0), dash));
    if (Journey.x >= 0.0) {
        float pop = easeOutBack(clamp(Journey.x / 0.35, 0.0, 1.0));
        eye = mix(vec3(0.0, 0.74, 0.09), PORTAL_STAGE_EYE, pop);
        back = normalize(mix(vec3(0.0, 0.0, 1.0), normalize(PORTAL_STAGE_EYE - PORTAL_STAGE_FOCUS), pop));
    } else if (State.w >= 0.0) {
        float pop = easeOutBack(clamp(State.w / 0.31, 0.0, 1.0));
        eye = mix(eye - back * 0.50 * (1.0 - dash), PORTAL_STAGE_EYE, pop);
        back = normalize(mix(back, normalize(PORTAL_STAGE_EYE - PORTAL_STAGE_FOCUS), pop));
        float shake = step(BREAK_START, State.w) * (1.0 - smoothstep(BREAK_START, BREAK_START + 0.20, State.w)) * 0.071;
        eye.xy += shake * vec2(sin(State.w * 124.0), sin(State.w * 168.0 + 0.52));
    }
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), back));
    vec3 relative = world - eye;
    return vec3(dot(relative, right), dot(relative, cross(back, right)), dot(relative, back));
}

void box(vec3 center, vec3 size, vec3 rotation, vec4 color, int kind) {
    faceIndex = gl_VertexID / 6;
    vec3 normal = NORMALS[faceIndex];
    vec3 tangent = TANGENTS[faceIndex];
    faceUv = FACE_CORNERS[gl_VertexID % 6];
    vec2 corner = 2.0 * faceUv - 1.0;
    localPosition = (normal + tangent * corner.x + cross(normal, tangent) * corner.y) * size;
    vec3 position = center + rotateBlock(localPosition, rotation);
    vec3 p = Arrival.x >= 0.0 ? (LandingView * vec4(position, 1.0)).xyz : stageView(position);
    float depth = -p.z;
    gl_Position = vec4(p.xy * vec2(Viewport.y / Viewport.x, 1.0) * Arrival.y, depth - 0.12, depth);
    tint = color;
    vec3 litNormal = rotateBlock(normal, rotation);
    facet = 0.63 + 0.37 * max(0.0, dot(litNormal, vec3(-0.33, 0.81, 0.46)));
    material = kind;
}

void box(vec3 center, vec3 size, vec2 rotation, vec4 color, int kind) {
    box(center, size, vec3(rotation.x, 0.0, rotation.y), color, kind);
}

FrameBlockPose frameBlockPose(int index, vec3 seed) {
    int row = blockRow(index);
    float age = Viewport.z - float(index) * 0.042;
    if (age <= 0.0 && State.w < 0.0) return FrameBlockPose(vec3(0.0), vec3(0.0), vec3(0.0), 0.0);

    vec3 center = frameSlot(index);
    float drop = clamp(age / 0.26, 0.0, 1.0);
    float landed = max(0.0, age - 0.26);
    float bounce = sin(landed * 43.0) * exp(-landed * 17.0);
    float stretch = 0.18 * sin(drop * 3.14159) - 0.28 * bounce;
    center.y += (0.51 + 0.46 * seed.x) * (1.0 - drop * drop) + max(0.0, -bounce) * 0.074;
    center.x += (seed.y - 0.5) * 0.19 * (1.0 - drop);
    vec3 rotation = vec3(seed.x - 0.5, 0.0, seed.y - 0.5) * 0.5 * (1.0 - drop);
    vec3 halfSize = vec3(BLOCK * 0.485) * vec3(1.0 - stretch * 0.46, 1.0 + stretch, 1.0 - stretch * 0.46);
    center.y += halfSize.y - BLOCK * 0.485;
    float jiggle = step(0.0, State.y) * sin(State.y * 53.0) * exp(-max(0.0, State.y) * 18.0);
    center.x += jiggle * (0.025 + float(row) * 0.009);
    rotation.z -= jiggle * 0.066;

    float alpha = 1.0;
    if (Journey.x >= 0.0) {
        float pack = clamp((Journey.x - 0.36 - float(index) * 0.008) / 0.25, 0.0, 1.0);
        center.y += 0.31 * sin(pack * 3.14159);
        center.y = mix(center.y, -0.08, smoothstep(0.38, 1.0, pack));
        halfSize *= 1.0 - 0.95 * smoothstep(0.22, 1.0, pack);
        rotation += pack * (seed - 0.5) * 3.0;
        alpha *= 1.0 - smoothstep(0.60, 1.0, pack);
    } else if (State.w >= 0.0) {
        vec3 slot = frameSlot(index);
        halfSize = vec3(BLOCK * 0.485);
        float release = max(0.0, Journey.y);
        float retry = max(0.0, Journey.z);
        if (index < ANCHOR_BLOCKS) {
            float tilt = smoothstep(BREAK_START, 0.36, State.w) * (index == 0 || index == 3 ? 0.23 : 0.0);
            tilt *= 1.0 - smoothstep(0.22, 0.55, retry);
            vec3 jitter = frameJitter(index, State.w, BREAK_START + 0.07);
            rotation = vec3(tilt * seed.y, 0.0, tilt * (seed.x > 0.5 ? 1.0 : -1.0)) + jitter * 0.09;
            center = vec3(slot.x, rotatedExtents(halfSize, rotation).y, 0.0);
            center += strainedJitter(index, State.w, jitter);
            float bury = smoothstep(0.32, 0.66, release);
            center.y -= 0.26 * bury;
            halfSize *= 1.0 - 0.92 * bury;
            alpha *= 1.0 - bury;
        } else {
            float extraAngle = orbitSpeed(seed) * (release + 2.0 * retry);
            center = orbitPosition(index, seed, State.w, extraAngle, 0.83 * release);
            float breakAt = blockBreakTime(index);
            float ageInFlight = max(0.0, State.w - breakAt);
            vec3 jitter = frameJitter(index, State.w, breakAt);
            center += strainedJitter(index, State.w, jitter);
            float spinImpulse = 0.105 * (1.0 - exp(-ageInFlight / 0.105));
            vec3 spinDirection = sign(seed - vec3(0.5));
            rotation = jitter * 0.13 + spinDirection * mix(vec3(51.0), vec3(78.0), seed.zxy) * spinImpulse;
            rotation += (seed - 0.5) * 0.46 * tetherTension(State.w) * (1.0 - smoothstep(0.0, 0.065, ageInFlight));
            rotation += spinDirection * vec3(0.47, 0.31, 0.40) * (ageInFlight + release + retry);
            float gust = smoothstep(0.0, 0.042, ageInFlight) * (1.0 - smoothstep(0.042, 0.20, ageInFlight));
            vec3 outward = abs(normalize(vec3(slot.x, max(0.1, slot.y - PORTAL_CORE.y), 0.21)));
            halfSize *= 1.0 + gust * (0.38 * outward - 0.09);
            float dissolve = smoothstep(0.02, 0.57, release);
            halfSize *= 1.0 - 0.95 * dissolve;
            alpha *= 1.0 - dissolve;

            if (Journey.z >= 0.0) {
                float dock = clamp((retry - seed.y * 0.09) / 0.55, 0.0, 1.0);
                float snap = smoothstep(0.0, 1.0, dock);
                center = mix(center, slot, snap * snap);
                rotation *= 1.0 - snap;
                float settle = max(0.0, retry - (0.55 + seed.y * 0.09));
                float squash = 0.24 * sin(53.0 * settle) * exp(-27.0 * settle);
                halfSize *= vec3(1.0 + 0.52 * squash, 1.0 - squash, 1.0 + 0.52 * squash);
            }
        }
        if (age <= 0.0) alpha *= smoothstep(0.0, 0.16, State.w);
    }
    return FrameBlockPose(center, halfSize, rotation, alpha);
}

void frameBlock(int index) {
    vec3 seed = portalBlockSeed(index);
    FrameBlockPose block = frameBlockPose(index, seed);
    if (block.opacity <= 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
    box(block.center, block.halfSize, block.rotation, vec4(1.0, 1.0, 1.0, State.x * block.opacity), blockMaterial(index));
}

void debris(int index) {
    int parent = index / DEBRIS_PER_BLOCK;
    vec3 parentSeed = portalBlockSeed(parent);
    vec3 seed = portalBlockSeed(FRAME_BLOCKS + index);
    FrameBlockPose block = frameBlockPose(parent, parentSeed);
    float release = max(0.0, Journey.y);
    float burst = parent < ANCHOR_BLOCKS ? BREAK_START + 0.08 : blockBreakTime(parent);
    float elapsed = (Journey.y < 0.0 ? State.w - burst : release) - seed.y * 0.08;
    if (elapsed <= 0.0 || block.opacity <= 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
    float motion = clamp(elapsed / 0.41, 0.0, 1.0);
    float angle = TAU * seed.x;
    vec2 direction = vec2(cos(angle), sin(angle));
    float scatter = 0.07 + mix(0.19, 0.43, seed.z) * (1.0 - (1.0 - motion) * (1.0 - motion));
    vec3 size = mix(0.021, 0.054, seed.y) * vec3(1.0, 0.48 + seed.z * 0.41, 0.63 + seed.x * 0.46);
    vec2 rotation = vec2(angle, seed.z * TAU) + vec2(4.0, -6.0) * motion;
    vec3 position = block.center + vec3(direction.x * scatter, (seed.y - 0.35) * motion * 0.4, direction.y * scatter);
    position.y = parent < ANCHOR_BLOCKS
            ? max(position.y - BLOCK * 0.33, rotatedExtents(size, vec3(rotation.x, 0.0, rotation.y)).y) : position.y;
    float alpha = State.x * smoothstep(0.0, 0.05, elapsed) * (1.0 - smoothstep(0.22, 0.56, elapsed));
    alpha *= 1.0 - smoothstep(0.0, 0.26, Journey.z);
    vec3 color = Journey.y >= 0.0 ? mix(vec3(0.15, 0.06, 0.26), vec3(0.59, 0.27, 0.87), seed.z) : vec3(1.0);
    box(position, size * (1.0 - motion * 0.62), rotation, vec4(color, alpha), Journey.y >= 0.0 ? 2 : blockMaterial(parent));
    faceUv = faceUv * 0.42 + seed.xy * 0.56;
}

void cryingTear(int index) {
    int parent = ANCHOR_BLOCKS + index / 6;
    int emitter = index % 6;
    float lane = float(emitter % 2);
    vec3 seed = portalBlockSeed(parent);
    float first = ORBIT_CAPTURE + float(emitter / 2) * 0.50 + lane * 0.072 + seed.x * 0.24;
    float cycle = floor((State.w - first) / 1.50);
    float birth = first + cycle * 1.50;
    float age = State.w - birth + max(max(0.0, Journey.y), Journey.z);
    bool second = mod(cycle * 3.0 + float(emitter / 2) + float(parent), 2.0) == 0.0;
    if (blockMaterial(parent) != 5 || cycle < 0.0 || (lane > 0.0 && !second) || age > 1.24) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
    // Sample the orbit at birth, so old drops fall in place as the parent keeps moving.
    vec3 position = orbitPosition(parent, seed, birth, 0.0, 0.0);
    position.y -= 0.12 + 0.28 * age + 0.28 * age * age;
    position.x += (lane - 0.5) * 0.105;
    float alpha = State.x * smoothstep(0.0, 0.06, age) * (1.0 - smoothstep(0.43, 1.24, age));
    box(position, vec3(mix(0.034, 0.019, age / 1.24)), vec2(seed.x * TAU + age, seed.y * TAU),
            vec4(1.10, 0.29, 1.52, alpha), 2);
}

void collectContactFootprints() {
    contactFootprints = uvec4(0u);
    if (State.w < 0.0 || faceIndex != 2) return;
    // The top face shares one packed footprint per block across its 16 by 16 ground cells.
    for (int index = 0; index < ANCHOR_BLOCKS; index++) {
        FrameBlockPose block = frameBlockPose(index, portalBlockSeed(index));
        if (block.opacity <= 0.0) continue;
        vec3 extents = rotatedExtents(block.halfSize, block.rotation);
        float gap = block.center.y - extents.y;
        float contact = block.opacity * (1.0 - smoothstep(0.01, 0.24, gap));
        float radius = max(extents.x, extents.z);
        vec2 center = (block.center.xz + HALF_STAGE) / (2.0 * HALF_STAGE);
        uvec4 bytes = uvec4(round(clamp(vec4(center, radius / CONTACT_MAX_RADIUS, contact), 0.0, 1.0) * 255.0));
        contactFootprints[index] = bytes.x | (bytes.y << 8u) | (bytes.z << 16u) | (bytes.w << 24u);
    }
}

void spark() {
    float t = clamp((State.y - 0.12) / 0.25, 0.0, 1.0);
    float alive = step(0.12, State.y) * (1.0 - smoothstep(0.38, 0.46, State.y))
            * max(step(State.w, -0.001), step(0.0, Journey.z));
    vec3 center = vec3(mix(-0.69, 0.0, t), 0.31 + 0.36 * sin(t * 3.14159), 0.12 * (1.0 - t));
    vec3 color = mix(vec3(1.0, 0.39, 0.035), vec3(1.0, 0.98, 0.84), ignitionFlash());
    box(center, vec3(0.073, 0.087, 0.073), vec2(t * 0.65, t * 2.2), vec4(color, alive * State.x), 2);
}

void fizzleSmoke(int index) {
    // Six pairs cover overlapping lifetimes without a CPU emitter.
    const float INTERVAL = 0.30;
    const float CYCLE = 1.80;
    float lane = float(index % 2);
    float first = 0.42 + float(index / 2) * INTERVAL;
    float cycle = floor((State.w - first) / CYCLE);
    float emission = cycle * 6.0 + float(index / 2);
    float age = State.w - first - cycle * CYCLE - lane * 0.065;
    float alive = step(0.0, cycle) * step(0.0, age);
    alive *= 1.0 - lane * (1.0 - step(1.0, mod(emission, 3.0)));
    age += max(0.0, Journey.y);
    vec3 seed = randomSeed(mod(emission, 1024.0) * 2.0 + lane + 271.0);
    float progress = clamp(age / mix(1.15, 1.53, seed.y), 0.0, 1.0);
    float opacity = alive * smoothstep(0.0, 0.12, progress) * (1.0 - smoothstep(0.26, 1.0, progress));
    opacity *= 1.0 - smoothstep(0.0, 0.42, Journey.y);
    vec2 drift = (seed.xz - 0.5) * (0.27 + progress * 0.44);
    vec3 position = vec3(drift.x + sin(progress * 3.0 + seed.y * 6.28) * progress * 0.14,
            0.07 + progress * (0.76 + seed.z * 0.35), 0.17 + drift.y);
    vec3 ash = mix(vec3(0.33, 0.29, 0.35), vec3(0.47, 0.16, 0.20), seed.x);
    box(position, vec3(mix(0.023, 0.087, progress)), seed.xy * 4.0 + progress * vec2(1.35, -1.60),
            vec4(ash, opacity * State.x * 0.64), 2);
}

vec3 portalTileCenter(int index) {
    return vec3((float(index % 3) - 1.0) * 0.177, 0.36 + float(index / 3) * 0.143, -0.109);
}

vec3 innerMoteCenter(vec3 seed, float angle, float height) {
    return vec3(cos(angle) * (0.13 + seed.y * 0.11), 0.35 + height * 0.76, sin(angle) * 0.13);
}

void implodePortal(int index, vec3 seed) {
    float charge = smoothstep(0.0, 0.10, State.w);
    float clock = Viewport.w - State.w;
    float angle = seed.x * TAU + clock * 2.45;
    float height = fract(seed.z + clock * (0.26 + 0.20 * seed.y));
    vec3 origin = index < 18 ? portalTileCenter(index) : innerMoteCenter(seed, angle, height);
    vec3 shell = normalize(seed - vec3(0.51)) * mix(0.036, 0.14, seed.x);
    vec3 center = mix(origin, PORTAL_CORE + shell, charge);
    vec3 violet = mix(vec3(0.58, 0.12, 0.96), vec3(0.97, 0.38, 0.96), seed.y);
    vec3 color = mix(violet, vec3(1.43, 0.64, 1.55), charge);
    float size = mix(0.027 + 0.022 * seed.y, 0.035, charge);
    float alpha = mix(0.54, 0.79, charge) * State.x;
    if (index == 0) {
        center = PORTAL_CORE;
        color = vec3(1.62, 1.26, 1.76);
        size = mix(0.039, 0.095, charge);
        alpha = smoothstep(0.022, 0.095, State.w) * State.x;
    }
    box(center, vec3(size), vec2(angle, angle * 0.52), vec4(color, alpha), 2);
}

vec3 tetherEndpoint(int index, float time) {
    float stop = index < ANCHOR_BLOCKS ? BREAK_START + 0.07 : blockBreakTime(index);
    return strainedSlot(index, portalBlockSeed(index), time) + strainedJitter(index, time, frameJitter(index, time, stop));
}

void tetherSpark(int index, vec3 seed) {
    ivec2 edge = TETHER_EDGES[index / 4];
    float snap = blockBreakTime(edge.y);
    float time = min(State.w, snap);
    float recoil = max(0.0, State.w - snap);
    vec3 a = tetherEndpoint(edge.x, time);
    vec3 b = tetherEndpoint(edge.y, time);
    vec3 axis = b - a;
    vec3 across = normalize(vec3(-axis.y, axis.x, 0.0));
    float flicker = 0.5 + 0.5 * sin(time * 165.0 + seed.x * TAU);
    float lane = (float(index % 4) + mix(0.16, 0.84, flicker)) * 0.25;
    float inset = min(BLOCK * 0.38 / length(axis), 0.41);
    vec3 center = mix(a, b, mix(inset, 1.0 - inset, lane));
    // Offset the arc toward the camera so sparks still read across touching faces.
    center += across * (flicker - 0.5) * 0.087 + vec3(0.0, 0.0, 0.15 + seed.z * 0.05);
    center += normalize(center - PORTAL_CORE) * recoil * mix(2.0, 4.3, seed.z);
    float alive = smoothstep(0.12, TENSION_HELD, State.w) * (1.0 - smoothstep(0.0, 0.065, recoil));
    vec3 color = mix(vec3(0.77, 0.16, 1.42), vec3(1.53, 0.79, 1.74), flicker);
    float size = mix(0.014, 0.026, flicker);
    box(center, vec3(size), vec2(seed.x * TAU, seed.y * TAU), vec4(color, alive * State.x), 2);
}

void coreBurst(vec3 seed, bool ash) {
    float elapsed = State.w - BREAK_START;
    float progress = clamp(elapsed / (ash ? 0.53 : 0.34), 0.0, 1.0);
    float angle = seed.x * TAU;
    float radial = (1.0 - (1.0 - progress) * (1.0 - progress)) * mix(0.35, 1.28, seed.y);
    vec3 offset = vec3(cos(angle) * radial, (seed.z - 0.29) * radial + progress * (ash ? 0.44 : 0.16),
            sin(angle) * radial);
    vec3 color = ash ? mix(vec3(0.26, 0.23, 0.30), vec3(0.44, 0.24, 0.33), seed.z)
            : mix(vec3(0.76, 0.13, 1.29), vec3(1.36, 0.56, 1.50), seed.y);
    float size = ash ? mix(0.042, 0.125, progress) : mix(0.039, 0.015, progress);
    float alpha = step(0.0, elapsed) * (1.0 - smoothstep(0.11, 1.0, progress)) * State.x * (ash ? 0.72 : 1.0);
    box(PORTAL_CORE + offset, vec3(size), vec2(angle + progress * 5.1, progress * 7.2), vec4(color, alpha), 2);
}

void portalParticle(int index) {
    vec3 seed = randomSeed(float(index) + 3873.0);
    float t = Viewport.w;
    if (Journey.x >= 0.42 && index >= 72 && index < 85) {
        float age = clamp((Journey.x - 0.42 - seed.z * 0.08) / 0.19, 0.0, 1.0);
        vec3 position = vec3((seed.x - 0.5) * 0.91 * (1.0 - 0.58 * age), (0.65 + 0.86 * seed.y) * (1.0 - age * age), 0.08);
        float light = smoothstep(0.0, 0.18, age) * (1.0 - smoothstep(0.70, 1.0, age));
        box(position, vec3(0.020, 0.042 + age * 0.064, 0.020), vec2(0.0),
                vec4(0.90, 0.59, 1.0, light * State.x), 2);
        return;
    }
    if (State.w >= 0.12 && State.w < BREAK_START + 0.16 && index >= 16 && index < 60) {
        tetherSpark(index - 16, seed);
        return;
    }
    if (State.w >= 0.0 && State.w < BREAK_START && index < 60) {
        implodePortal(index, seed);
        return;
    }
    if (State.w >= BREAK_START && State.w < BREAK_START + 0.53
            && (index < 16 || (index >= 60 && index < 72))) {
        coreBurst(seed, index >= 60);
        return;
    }
    if (State.w >= 0.0 && index >= 72 && index < 84) {
        fizzleSmoke(index - 72);
        return;
    }
    if (index < 18) {
        vec2 tile = vec2(float(index % 3) - 1.0, float(index / 3));
        vec3 center = portalTileCenter(index);
        float pulse = 0.5 + 0.5 * sin(t * 5.5 + length(tile - vec2(0.0, 2.5)) * 1.8);
        vec3 purple = mix(vec3(0.34, 0.035, 0.63), vec3(0.84, 0.15, 0.79), pulse);
        float alpha = portalOpen() * State.x * mix(0.28, 0.49, pulse);
        box(center, vec3(0.085, 0.069, 0.012), vec2(0.0), vec4(purple, alpha), 2);
        return;
    }
    bool orbit = index >= 60;
    bool xp = index >= 85;
    float angle = seed.x * TAU + t * (orbit ? 1.4 : 2.45);
    float height = fract(seed.z + t * (0.26 + 0.20 * seed.y));
    vec3 center = orbit
            ? vec3(cos(angle) * (0.61 + seed.y * 0.28), 0.31 + height * 1.18, sin(angle) * 0.44)
            : innerMoteCenter(seed, angle, height);
    vec3 purple = mix(vec3(0.55, 0.14, 0.90), vec3(0.99, 0.37, 0.87), seed.y);
    vec3 color = xp ? mix(vec3(0.55, 0.99, 0.15), vec3(1.0, 0.89, 0.26), seed.x) : purple;
    float envelope = smoothstep(0.0, 0.14, height) * (1.0 - smoothstep(0.81, 1.0, height));
    float open = portalOpen();
    float idle = orbit && !xp && State.w < 0.0 && Journey.x < 0.0 ? 1.0 - open : 0.0;
    vec3 ambientPosition = vec3(cos(angle) * (0.72 + seed.y * 1.17), 0.17 + height * 1.50, sin(angle) * (0.54 + seed.x * 0.93));
    center = mix(center, ambientPosition, idle);
    color = mix(color, mix(vec3(0.32, 0.43, 0.77), vec3(0.68, 0.65, 0.96), seed.y), idle);
    float alpha = (open * (orbit ? 0.90 : 0.67) + idle * 0.58) * envelope * State.x;
    float size = orbit ? mix(0.016, 0.038, seed.y) : mix(0.029, 0.066, seed.y);
    center.xy += (center.xy - vec2(0.0, 0.72)) * 1.6 * smoothstep(0.76, 1.0, Arrival.z);
    if (Journey.x >= 0.0) {
        float burst = 1.0 - smoothstep(0.0, 0.26, Journey.x);
        center.xy += vec2(cos(angle), sin(angle)) * Journey.x * burst * (4.0 + 9.0 * seed.z);
        size *= mix(0.22, 1.0, 1.0 - smoothstep(0.36, 0.53, Journey.x));
        center.y = mix(0.34, center.y, 1.0 - smoothstep(0.36, 0.53, Journey.x));
    }
    box(center, vec3(size), vec2(angle, angle * 0.68), vec4(color, alpha), 2);
}

void arrivalShard(int index) {
    vec3 seed = randomSeed(float(index) + 896.0);
    float a = seed.x * TAU;
    vec2 direction = vec2(cos(a), sin(a));
    float t = Arrival.x;
    vec3 center = vec3(direction * (0.18 + seed.y * 1.19 + t * (0.72 + 4.4 * seed.z)), -1.0 - 5.4 * seed.z + t);
    float alpha = (1.0 - smoothstep(0.03, 1.0, t)) * (0.35 + 0.54 * seed.y);
    vec3 color = mix(vec3(0.62, 0.15, 0.94), vec3(0.99, 0.70, 1.0), seed.y);
    box(center, vec3(mix(0.027, 0.083, seed.z) * (1.0 - 0.90 * t)),
            vec2(a + t * 5.0, seed.y * 2.4 + t * 6.0), vec4(color, alpha), 2);
}

void ambientMote(int index) {
    vec3 seed = randomSeed(float(index) + 6338.0);
    float phase = fract(seed.z + Viewport.w * (0.14 + seed.x * 0.15));
    float angle = seed.x * TAU + Viewport.w * (0.14 + 0.14 * seed.y);
    vec2 direction = vec2(cos(angle), sin(angle));
    float radius = mix(1.35, 2.95, seed.y);
    vec3 position = vec3(direction.x * radius, mix(-0.86, 1.95, phase), direction.y * radius);
    float alpha = 0.55 * smoothstep(0.0, 0.19, phase) * (1.0 - smoothstep(0.72, 1.0, phase));
    if (Arrival.x >= 0.0) {
        position = vec3(direction * (0.40 + radius * (0.43 + Arrival.x * 0.75)), -1.6 - 4.2 * seed.z);
        alpha *= 1.0 - smoothstep(0.04, 0.92, Arrival.x);
    }
    vec3 color = mix(vec3(0.35, 0.52, 0.93), vec3(0.84, 0.68, 1.0), seed.y);
    box(position, vec3(mix(0.010, 0.031, seed.z)), vec2(angle, phase * TAU), vec4(color, State.x * alpha), 2);
}

void decoration(int index) {
    if (index < 12) {
        int corner = index / 3;
        int part = index % 3;
        vec3 seed = randomSeed(float(corner) + 83.0);
        vec2 side = vec2(corner % 2 == 0 ? -1.0 : 1.0, corner < 2 ? -1.0 : 1.0);
        vec2 roots = side * (vec2(0.92, 0.84) + 0.20 * seed.xy);
        bool flower = corner % 2 == 0;
        bool petal = flower && part == 2;
        float y = 0.048 + float(part) * 0.056;
        vec3 color = mix(vec3(0.21, 0.42, 0.08), vec3(0.49, 0.72, 0.19), float(part) * 0.45);
        if (petal) color = corner == 0 ? vec3(0.98, 0.19, 0.18) : vec3(1.0, 0.75, 0.12);
        vec3 size = petal ? vec3(0.071, 0.047, 0.067) : vec3(flower ? 0.024 : 0.047, 0.059, 0.034);
        box(vec3(roots.x + float(part) * (flower ? 0.0 : 0.027), y, roots.y), size,
                vec2(0.0, 0.16 * sin(Viewport.w * 2.2 + float(corner))), vec4(color, State.x), 3);
    } else if (index < 17) {
        int part = index - 12;
        vec3 origin = vec3(0.87, 0.105, 0.32);
        vec3 center = origin + vec3(part % 2 == 0 ? -0.071 : 0.071, part < 2 ? -0.052 : 0.052, 0.0);
        vec3 color = part < 2 ? vec3(0.46, 0.24, 0.092) : vec3(0.72, 0.43, 0.14);
        vec3 size = vec3(0.073, 0.052, 0.094);
        if (part == 4) {
            center = origin + vec3(0.0, 0.009, 0.103);
            color = vec3(0.99, 0.76, 0.26);
            size = vec3(0.025, 0.031, 0.014);
        }
        box(center, size, vec2(0.0), vec4(color, State.x), 3);
    }
}

void cloudPiece(int index) {
    int cloud = index / 5;
    int piece = index % 5;
    vec3 seed = randomSeed(float(cloud) + 1913.0);
    float side = cloud % 2 == 0 ? -1.0 : 1.0;
    vec3 center = vec3(side * (1.93 + seed.x * 0.68), 1.50 + float(cloud / 2) * 1.24, -1.52 - 1.21 * seed.z);
    center.x += 0.50 * sin(Viewport.w * 0.29 + float(cloud) * 1.6) + (float(piece) - 2.0) * 0.30;
    center.y += piece == 1 || piece == 2 ? 0.17 : 0.0;
    vec3 pastel = mix(vec3(0.69, 0.72, 0.97), vec3(0.98, 0.93, 1.0), seed.y);
    // Stagger front and back planes so overlapping pieces cannot both pass the depth comparison.
    float depth = 0.18 + float(piece) * 0.016;
    box(center, vec3(0.188, 0.122 + 0.025 * float(piece % 2), depth), vec2(0.0), vec4(pastel, State.x * 0.57), 4);
}

void main() {
    int id = gl_InstanceID;
#ifdef PORTAL_CLOUDS
    cloudPiece(id);
#else
    if (Arrival.x >= 0.0) {
        if (id < PORTAL_ARRIVAL_SHARDS) arrivalShard(id);
        else ambientMote(id - PORTAL_ARRIVAL_SHARDS);
    } else if (id == 0) {
        box(vec3(0.0, -HALF_STAGE, 0.0), vec3(HALF_STAGE), vec2(0.0), vec4(1.0, 1.0, 1.0, State.x), 0);
        collectContactFootprints();
    } else if (id <= FRAME_BLOCKS) {
        frameBlock(id - 1);
    } else if (id == 15) {
        spark();
    } else if (id < 111) {
        portalParticle(id - 16);
    } else if (id < PORTAL_AMBIENT_START) {
        decoration(id - 111);
    } else if (id < PORTAL_DEBRIS_START) {
        ambientMote(id - PORTAL_AMBIENT_START);
    } else if (id < PORTAL_TEARS_START) {
        debris(id - PORTAL_DEBRIS_START);
    } else {
        cryingTear(id - PORTAL_TEARS_START);
    }
#endif
}
