#version 330 core

#moj_import <kwrg:loading_scene.glsl>

out vec2 faceUv;
flat out vec3 normal;
flat out vec3 tint;
flat out float emitter;
flat out float isTile;

const int TILES = GRID_SIZE * GRID_SIZE;
const int MODEL_WIDTH = 9;
const int MODEL_HEIGHT = 11;
const int MODEL_VOLUME = MODEL_WIDTH * MODEL_WIDTH * MODEL_HEIGHT;
const int BEACON_SIZE = 13;

void cube(Camera camera, vec3 center, vec3 radius, vec3 color, float light, float tile) {
    int face = gl_VertexID / 6;
    normal = NORMALS[face];
    vec3 tangent = TANGENTS[face];
    faceUv = FACE_CORNERS[gl_VertexID % 6];
    vec2 uv = faceUv * 2.0 - 1.0;
    vec3 vertex = normal + tangent * uv.x + cross(normal, tangent) * uv.y;
    gl_Position = project(camera, center + vertex * radius);
    tint = color;
    emitter = light;
    isTile = tile * step(0.5, normal.y);
}

void skipVoxel() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
}

void deck(Camera camera, int index, float lift) {
    ivec2 cell = ivec2(index & 15, index >> 4);
    vec2 xz = cellCenter(cell);
    vec3 seed = randomSeed(float(index + 312));
    float matrix = Browser.y * lift;
    float hardcore = step(2.5, Browser.z) * Browser.w * lift;
    vec3 grass = terrainColor(ivec3(cell.x, GRID_SIZE - 1, cell.y), NORMALS[2], cellSeed(ivec3(cell.x, GRID_SIZE - 1, cell.y)));
    vec3 color = mix(grass, mix(vec3(0.035, 0.030, 0.085), vec3(0.11, 0.082, 0.23), seed.z), matrix);
    color = mix(color, mix(vec3(0.043, 0.023, 0.052), vec3(0.39, 0.047, 0.093), seed.x), hardcore);
    float wave = sin(xz.x * 5.0 + xz.y * 3.0 - Time * 2.4) * (1.0 - matrix) * (1.0 - hardcore) * 0.032;
    if (Flight.y >= 0.0 && Browser.y > 0.5) {
        float radius = length(xz - cellCenter(beaconCell(int(Flight.y))));
        float ripple = fract(radius * 1.2 - Time * 0.75);
        float pulse = pow(1.0 - abs(ripple * 2.0 - 1.0), 9.0) * exp(-radius * 0.60);
        color += vec3(0.04, 0.71, 0.84) * pulse * lift;
    }
    float stepSize = GRID_STEP * blockScale();
    cube(camera, vec3(xz.x, boardDeck() + (wave - 0.01) * lift, xz.y),
            vec3(stepSize * mix(0.496, 0.454, lift), 0.067 * lift, stepSize * mix(0.496, 0.454, lift)), color, 0.04, 1.0);
}

vec3 modelTint(int model) {
    if (model == 1) return vec3(0.48, 1.0, 0.26);
    if (model == 2) return vec3(0.33, 0.76, 1.46);
    if (model == 3) return vec3(1.50, 0.10, 0.27);
    return vec3(0.22, 0.93, 1.35);
}

void modelVoxel(Camera camera, int index, float lift) {
    int model = int(Browser.z);
    float scale = mix(0.73, 1.0, blockScale());
    float stepSize = 0.125 * scale;
    vec3 p = vec3(index % MODEL_WIDTH - 4, index / (MODEL_WIDTH * MODEL_WIDTH),
            index / MODEL_WIDTH % MODEL_WIDTH - 4);
    vec3 seed = randomSeed(float(index) + 914.0);
    vec3 color = modelTint(model);
    float emission = 0.50;
    float radius = stepSize * 0.47;
    bool visible;
    float azimuth = Time * (model == 2 ? 0.45 : 0.32);
    mat2 rotation = mat2(cos(azimuth), sin(azimuth), -sin(azimuth), cos(azimuth));

    if (model == 1) {
        float crown = length(vec3(p.x, (p.y - 7.0) * 1.15, p.z));
        bool trunk = abs(p.x) + abs(p.z) < 0.5 && p.y < 8.0;
        bool branch = p.y >= 5.0 && p.y <= 7.0 && abs(p.x) + abs(p.z) == p.y - 4.0 && abs(p.z) < 1.0;
        visible = trunk || branch || (crown < 3.65 && (crown < 2.5 || seed.z > 0.17));
        color = trunk || branch ? mix(vec3(0.27, 0.14, 0.055), vec3(0.67, 0.36, 0.12), seed.y)
                : mix(vec3(0.14, 0.48, 0.12), vec3(0.55, 1.03, 0.25), seed.x);
        emission = 0.10 + seed.z * 0.13;
    } else if (model == 2) {
        float outline = abs(p.x) + abs(p.z);
        float profile = 4.6 - abs(p.y - 5.0) * 0.89;
        visible = p.y > 0.0 && p.y < 10.0 && outline < profile && (outline + 1.25 > profile || outline == 0.0);
        p.xz = rotation * p.xz;
        p.y += 1.0 + 0.44 * sin(Time * 2.5);
        color = mix(vec3(0.15, 1.08, 1.53), vec3(0.86, 0.22, 1.58), seed.y);
        emission = 0.55 + 0.35 * sin(Time * 3.7 - p.y * 0.61);
    } else if (model == 3) {
        float y = p.y - 5.0;
        float lobe = length(vec2(abs(p.x) - 1.9, y - 1.6));
        bool upper = y >= 0.0 && lobe < 2.52;
        bool lower = y < 0.0 && y > -5.0 && abs(p.x) < y + 4.9;
        visible = (upper || lower) && abs(p.z) <= 1.0;
        float beat = pow(max(0.0, sin(Time * 5.6)), 9.0);
        p = (p - vec3(0.0, 4.5, 0.0)) * (1.0 + 0.14 * beat) + vec3(0.0, 6.5, 0.0);
        float turn = 0.38 * sin(Time * 1.2);
        vec2 facing = camera.right.xz;
        vec2 angled = mat2(cos(turn), sin(turn), -sin(turn), cos(turn)) * p.xz;
        p.xz = facing * angled.x + vec2(-facing.y, facing.x) * angled.y;
        color = mix(vec3(0.56, 0.018, 0.09), vec3(1.65, 0.12, 0.25), seed.x);
        emission = 0.47 + beat * 0.69;
    } else {
        vec3 sphere = p - vec3(0.0, 5.5, 0.0);
        float shell = length(sphere);
        float meridian = min(abs(sphere.x), abs(sphere.z));
        float diagonal = abs(abs(sphere.x) - abs(sphere.z));
        visible = abs(shell - 3.45) < 0.50 && (meridian < 0.55 || diagonal < 0.55 || abs(sphere.y) < 0.55);
        p.xz = rotation * p.xz;
        p.y += 0.65 * sin(Time * 1.8);
        radius *= 0.62;
        emission = 0.75;
    }

    float growth = lift * Browser.w;
    if (!visible || growth < 0.001) {
        skipVoxel();
        return;
    }
    float rise = smoothstep(0.0, 0.83, growth - p.y * 0.017);
    vec3 center = vec3(p.x * stepSize, boardDeck() + 0.10 + (p.y + 0.4) * stepSize * rise, p.z * stepSize);
    cube(camera, center, vec3(radius * rise), color, emission, 0.0);
}

void modelAccent(Camera camera, int index, float lift) {
    int model = int(Browser.z);
    vec3 seed = randomSeed(float(index) + 8472.0);
    float angle = (float(index) / 64.0) * TAU + Time * (0.26 + 0.35 * seed.y);
    float radius = index < 64 ? 0.71 : mix(0.21, 0.76, seed.z);
    float height = index < 64 ? 0.38 : 0.18 + fract(seed.x + Time * 0.31) * 1.15;
    if (model != 0 && index < 64) {
        radius = mix(0.26, 0.94, seed.z);
        height = 0.22 + fract(seed.x + Time * (model == 2 ? 0.63 : 0.24)) * 1.18;
    }
    float size = (index < 64 && model == 0 ? 0.010 : 0.012 + 0.017 * seed.x) * lift;
    vec3 color = modelTint(model);
    if (model == 1) color = mix(color, vec3(1.46, 0.78, 0.21), seed.z);
    vec3 position = vec3(radius * cos(angle), boardDeck() + height * lift, radius * sin(angle));
    cube(camera, position, vec3(size), color, 0.45 + 0.32 * sin(Time * 4.3 + seed.x * TAU), 0.0);
}

vec3 signalTint(int signal) {
    if (signal == 1) return vec3(0.13, 1.24, 0.69);
    if (signal == 2) return vec3(1.48, 0.73, 0.18);
    if (signal == 3) return vec3(1.54, 0.21, 0.09);
    if (signal == 4) return vec3(0.18, 0.21, 0.30);
    return vec3(0.23, 0.66, 1.19);
}

float signalJitter(int slot, int signal) {
    return signal == 2 || signal == 3 ? 0.77 + 0.23 * sin(Time * 13.0 + float(slot) * 3.4) : 1.0;
}

float selectedHeight(Camera camera, vec2 xz, float lift) {
    float height = 1.34 * lift;
    vec3 top = vec3(xz.x, boardDeck() + 0.13 + height + 0.44 * lift, xz.y);
    vec4 clip = project(camera, top);
    vec4 below = project(camera, top - vec3(0, 1, 0));
    float overflow = clip.y - clip.w * 0.91;
    float perUnit = overflow - (below.y - below.w * 0.91);
    return max(0.55 * lift, height - max(0.0, overflow) / max(0.001, perUnit));
}

void beacon(Camera camera, int slot, int section, float lift) {
    int signal = beaconSignal(slot);
    bool selected = slot == int(Flight.y);
    bool launching = slot == int(ServerFlight.y);
    vec2 xz = cellCenter(beaconCell(slot));
    vec3 color = signalTint(signal);
    float scale = selected ? 1.8 : 1.0;
    float jitter = signalJitter(slot, signal);
    float height = (selected ? selectedHeight(camera, xz, lift) : mix(0.63, 0.91, randomSeed(float(slot + 62)).x) * lift) * jitter;
    if (launching) {
        color = mix(color, vec3(0.41, 0.97, 1.7), 0.65);
    }
    if (section == 0) {
        cube(camera, vec3(xz.x, boardDeck() + 0.075, xz.y), vec3(0.054, 0.024, 0.054), color, signal == 4 ? 0.0 : 0.52, 0.0);
    } else if (signal == 4 && !launching) {
        skipVoxel();
    } else if (section == 1) {
        cube(camera, vec3(xz.x, boardDeck() + 0.10 + height * 0.5, xz.y),
                vec3(0.013 * scale, height * 0.5, 0.013 * scale), color, jitter * 0.84, 0.0);
    } else {
        vec3 seed = randomSeed(float(slot * BEACON_SIZE + section + 470));
        float phase = fract(float(section - 2) / float(BEACON_SIZE - 2) + Time * (0.71 + seed.z * 0.13));
        vec2 offset = (seed.xy - 0.5) * mix(0.05, 0.12, phase) * scale;
        cube(camera, vec3(xz.x + offset.x, boardDeck() + 0.11 + height * phase, xz.y + offset.y),
                vec3(0.017 * scale * (1.0 - 0.48 * phase)), color, jitter, 0.0);
    }
}

void portal(Camera camera, int index, float lift) {
    int slot = int(Flight.y);
    int signal = beaconSignal(slot);
    if (signal == 4) {
        skipVoxel();
        return;
    }
    vec2 xz = cellCenter(beaconCell(slot));
    vec3 seed = randomSeed(float(index) + 3183.0);
    float phase = fract(float(index) / 48.0 + Time * 0.39);
    float angle = phase * TAU * 1.6 + float(index / 48) * 3.14159 + Time * 1.4;
    float radius = (0.10 + 0.33 * phase) * lift;
    vec3 center = vec3(xz.x, boardDeck() + 0.13 + selectedHeight(camera, xz, lift) * signalJitter(slot, signal), xz.y);
    vec3 axis = normalize(vec3(camera.right.x, 0.0, camera.right.z));
    vec3 position = center + axis * (cos(angle) * radius) + vec3(0.0, sin(angle) * radius, 0.0)
            + cross(axis, vec3(0, 1, 0)) * ((seed.x - 0.5) * 0.15 + (1.0 - phase) * 0.22);
    vec3 color = mix(vec3(0.72, 0.18, 1.60), vec3(0.28, 0.83, 1.63), seed.x);
    cube(camera, position, vec3(mix(0.013, 0.035, seed.z) * lift), color, 0.71 + 0.23 * sin(angle), 0.0);
}

void main() {
    Camera camera = sceneCamera();
    int index = gl_InstanceID;
    float lift = smoothstep(0.0, 0.84, Browser.x);
    if (index < TILES) {
        deck(camera, index, lift);
        return;
    }
    index -= TILES;
    if (Browser.y > 0.5) {
        if (index < int(Flight.z) * BEACON_SIZE) {
            beacon(camera, index / BEACON_SIZE, index % BEACON_SIZE, lift);
        } else {
            portal(camera, index - int(Flight.z) * BEACON_SIZE, lift);
        }
    } else if (index < MODEL_VOLUME) {
        modelVoxel(camera, index, lift);
    } else {
        modelAccent(camera, index - MODEL_VOLUME, lift);
    }
}
