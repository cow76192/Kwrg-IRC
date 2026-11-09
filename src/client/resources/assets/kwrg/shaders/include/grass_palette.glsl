vec3 cellSeed(ivec3 cell) {
    return randomSeed(float(cell.x + GRID_SIZE * (cell.z + GRID_SIZE * cell.y)) + 1.0);
}

vec3 terrainColor(ivec3 cell, vec3 normal, vec3 seed) {
    float fringe = float(GRID_SIZE - 1 - cell.y);
    vec3 grass = mix(vec3(0.15, 0.39, 0.12), vec3(0.48, 0.72, 0.23), seed.x);
    vec3 soil = mix(vec3(0.18, 0.102, 0.088), vec3(0.48, 0.295, 0.18), seed.y);
    float top = step(0.5, normal.y) * step(fringe, 0.0);
    float edge = (1.0 - step(0.5, abs(normal.y))) * step(fringe, floor(seed.z * 3.2));
    return mix(soil, grass, top + edge * 0.83);
}

vec3 terrainLight(vec3 normal) {
    float key = max(0.0, dot(normal, vec3(-0.43, 0.79, 0.44)));
    float fill = max(0.0, dot(normal, vec3(0.70, 0.48, -0.52)));
    return vec3(0.50, 0.53, 0.61) + key * vec3(0.64, 0.57, 0.43) + fill * vec3(0.12, 0.21, 0.31);
}

float grassSurfaceShade(ivec3 cell, vec3 normal) {
    bool side = abs(normal.y) < 0.5;
    int topRow = GRID_SIZE - 1;
    bool perimeter = cell.x == 0 || cell.x == topRow || cell.z == 0 || cell.z == topRow;
    float depthShade = side ? 0.65 + 0.35 * float(cell.y) / float(GRID_SIZE) : 1.0;
    float edgeLight = cell.y == topRow && (side || (normal.y > 0.5 && perimeter)) ? 1.13 : 1.0;
    return depthShade * edgeLight;
}

