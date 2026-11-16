#moj_import <kwrg:voxel_geometry.glsl>
#moj_import <kwrg:grass_palette.glsl>
#moj_import <kwrg:portal_stage.glsl>

layout(std140) uniform LoadingFrame {
    float Time;
    float Progress;
    float Opacity;
    float Burst;
    vec4 Viewport; // Framebuffer size, followed by normalized pointer coordinates.
    float Charge;
    float PointerMotion;
    float LayerPhase;
    float RingLines;
    ivec4 Groups; // Finished rows, active layer, inflow, and hologram lines.
    vec4 Menu; // Title scene, pan, accumulated orbit offset, compact layout.
    vec4 Theme; // Solo, network, settings, and quit weights.
    vec4 Browser; // Camera push, network mode, world model, growth.
    vec4 Flight; // Dive, selected beacon, beacon count, projection grid line count.
    vec4 Switch; // Camera approach, reverse assembly (-1 outside reverse), panorama zoom, panorama collapse.
    vec4 Warp; // RGB separation, shake, time since the camera locked.
    vec4 ServerFlight; // Launch progress and target beacon slot.
    ivec4 Signals[8]; // Four bits per beacon. Each ivec4 describes 32 cells.
};

const float GRID_STEP = 0.16;
const float HALF_EDGE = float(GRID_SIZE) * GRID_STEP * 0.5;
const float EYE_DISTANCE = 6.5;
struct Camera {
    vec3 back;
    vec3 right;
    vec3 up;
    vec2 shift;
    vec2 focal;
    vec3 eye;
};

float releasePulse() {
    if (Switch.y >= 0.0) return 0.0;
    return smoothstep(0.0, 0.028, Burst) * exp2(-9.0 * Burst);
}

vec2 screenShift() {
    vec2 shake = vec2(sin(Time * 113.0), sin(Time * 151.0 + 0.6));
    vec2 menuOffset = mix(vec2(0.43, -0.07), vec2(0.38, 0.17), Menu.w) * Menu.y;
    vec2 base = vec2(0.0, 0.11) + menuOffset;
    vec2 board = mix(vec2(0.52, -0.07), vec2(0.52, -0.16), Menu.w);
    vec2 shift = mix(mix(base, board, Browser.x), vec2(0.0), smoothstep(0.0, 0.9, Flight.x))
            - Viewport.zw * mix(vec2(0.033, 0.025), vec2(0.013), Browser.x)
            + shake * (0.016 * releasePulse());
    float lockOn = Switch.y >= 0.0 ? Switch.x / 0.82 : max(Switch.x, smoothstep(0.0, 0.14, Warp.z));
    return mix(shift, vec2(0.0), max(lockOn, smoothstep(0.0, 0.24, ServerFlight.x)));
}

float chargeStrength() {
    return smoothstep(0.0, 1.0, Charge) * (1.0 - smoothstep(0.0, 0.17, Burst));
}

float blockScale() {
    float scale = (1.0 - 0.15 * chargeStrength()) * (1.0 - Menu.y * mix(0.0, 0.40, Menu.w));
    return mix(scale, 1.0, smoothstep(0.0, 1.0, ServerFlight.x));
}

float boardDeck();

Camera sceneCamera() {
    float cameraTime = Time - Warp.z;
    float orbit = 0.64 + cameraTime * 0.22 + Menu.z + Viewport.z * mix(0.15, 0.087, Menu.y);
    orbit += 0.38 * Browser.x;
    float pitch = mix(atan(0.43 + 0.045 * sin(cameraTime * 0.37) + Viewport.w * mix(0.12, 0.087, Menu.y)),
            0.785398 + Viewport.w * 0.025, Browser.x);
    float dive = smoothstep(0.0, 1.0, Flight.x);
    pitch = mix(pitch, 1.50, dive);
    vec3 back = vec3(sin(orbit) * cos(pitch), sin(pitch), cos(orbit) * cos(pitch));
    vec3 right = normalize(vec3(back.z, 0.0, -back.x));
    float aspect = Viewport.x / Viewport.y;
    float lens = 2.13 * min(1.0, aspect / 0.83) * (1.0 + 0.11 * chargeStrength() - 0.24 * releasePulse());
    float distance = mix(mix(EYE_DISTANCE, mix(5.65, 6.18, Menu.w), Browser.x), 0.47, dive);
    float targetY = mix(0.64 * Browser.x, HALF_EDGE * blockScale() + 0.28, dive);
    distance = mix(distance, Switch.y >= 0.0 ? 1.70 : 0.14, Switch.x);
    targetY = mix(targetY, Menu.y * sin(cameraTime * 0.94) * 0.065, Switch.x);
    vec3 eye = back * distance + vec3(0.0, targetY, 0.0);
    if (ServerFlight.x > 0.0) {
        float push = smoothstep(0.0, 1.0, ServerFlight.x);
        eye = mix(eye, PORTAL_STAGE_EYE + vec3(0.0, boardDeck(), 0.0), push);
        back = normalize(mix(back, normalize(PORTAL_STAGE_EYE - PORTAL_STAGE_FOCUS), push));
        right = normalize(cross(vec3(0.0, 1.0, 0.0), back));
    }
    return Camera(back, right, cross(back, right), screenShift(), vec2(lens / aspect, lens), eye);
}

vec3 menuPalette(float hue) {
    vec3 color = mix(vec3(0.33, 0.67, 0.94), vec3(0.60, 0.92, 0.66), hue);
    color = mix(color, mix(vec3(0.31, 0.79, 1.0), vec3(0.29, 1.0, 0.47), hue), Theme.x);
    color = mix(color, mix(vec3(0.26, 0.91, 1.0), vec3(0.76, 0.32, 1.0), hue), Theme.y);
    color = mix(color, mix(vec3(0.84, 0.18, 0.18), vec3(1.0, 0.69, 0.22), hue), Theme.z);
    return color * (1.0 - 0.73 * Theme.w);
}

vec4 project(Camera camera, vec3 position) {
    vec3 relative = position - camera.eye;
    float depth = -dot(relative, camera.back);
    vec2 view = vec2(dot(relative, camera.right), dot(relative, camera.up));
    return vec4(view * camera.focal + camera.shift * depth, depth - 0.20, depth);
}

float boardDeck() {
    return HALF_EDGE * blockScale() + Menu.y * sin(Time * 0.94) * 0.065;
}

ivec2 beaconCell(int slot) {
    int cell = (slot * 73 + 119) & 255;
    return ivec2(cell & 15, cell >> 4);
}

vec2 cellCenter(ivec2 cell) {
    return (vec2(cell) + 0.5 - float(GRID_SIZE) * 0.5) * GRID_STEP * blockScale();
}

int beaconSignal(int slot) {
    int word = Signals[slot >> 5][(slot >> 3) & 3];
    return (word >> ((slot & 7) * 4)) & 15;
}

vec3 starColor(float seed) {
    return mix(vec3(0.26, 1.16, 1.42), vec3(1.72, 0.81, 0.28), smoothstep(0.31, 0.82, seed));
}

float fresnel(vec3 normal, vec3 toEye) {
    float grazing = 1.0 - clamp(dot(normal, normalize(toEye)), 0.0, 1.0);
    float square = grazing * grazing;
    return square * square;
}

vec3 expose(vec3 color) {
    return color / (1.0 + 0.47 * color);
}
