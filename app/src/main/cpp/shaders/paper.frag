// GLSL shader for paper texture generation
// This would be compiled to SPIR-V using glslc
// Command: glslc -O paper.frag -o paper.frag.spv

#version 450

// Input from vertex shader
layout(location = 0) in vec2 v_texCoord;
layout(location = 0) out vec4 fragColor;

// Uniform parameters
layout(binding = 0) uniform Uniforms {
    uint seed;
    float grainIntensity;
    float fiberDensity;
    float agingYellow;
    float fiberDirection;
    float roughness;
    vec2 resolution;
} ubo;

// Hash function for noise
uint hash(uint x, uint y, uint seed) {
    uint h = x * 374761393u + y * 668265263u + seed * 1442695041u;
    h = (h ^ (h >> 13)) * 1274126177u;
    h = h ^ (h >> 16);
    return h;
}

// Random float from hash
float randomFloat(uint x, uint y, uint seed) {
    uint h = hash(x, y, seed);
    return float(h & 0x7FFFFFFFu) / 2147483647.0;
}

// Smoothstep interpolation
float smoothstep(float t) {
    return t * t * (3.0 - 2.0 * t);
}

// Linear interpolation
float lerp(float a, float b, float t) {
    return a + (b - a) * t;
}

// 2D noise (Perlin-like)
float noise2D(vec2 uv, uint seed) {
    ivec2 i = ivec2(floor(uv));
    vec2 f = fract(uv);
    
    float sx = smoothstep(f.x);
    float sy = smoothstep(f.y);
    
    // Get random gradients at corners
    float g00 = randomFloat(i.x, i.y, seed) * 2.0 - 1.0;
    float g10 = randomFloat(i.x + 1, i.y, seed) * 2.0 - 1.0;
    float g01 = randomFloat(i.x, i.y + 1, seed) * 2.0 - 1.0;
    float g11 = randomFloat(i.x + 1, i.y + 1, seed) * 2.0 - 1.0;
    
    // Dot product with distance vectors
    vec2 v00 = f - vec2(0.0, 0.0);
    vec2 v10 = f - vec2(1.0, 0.0);
    vec2 v01 = f - vec2(0.0, 1.0);
    vec2 v11 = f - vec2(1.0, 1.0);
    
    float d00 = dot(v00, vec2(g00, g00));
    float d10 = dot(v10, vec2(g10, g10));
    float d01 = dot(v01, vec2(g01, g01));
    float d11 = dot(v11, vec2(g11, g11));
    
    // Interpolate
    float nx0 = mix(d00, d10, sx);
    float nx1 = mix(d01, d11, sx);
    
    return mix(nx0, nx1, sy);
}

// Multi-octave noise (FBM)
float fbmNoise(vec2 uv, uint seed, int octaves) {
    float total = 0.0;
    float frequency = 1.0;
    float amplitude = 1.0;
    float maxValue = 0.0;
    
    for (int i = 0; i < octaves; i++) {
        total += noise2D(uv * frequency, seed + uint(i)) * amplitude;
        maxValue += amplitude;
        frequency *= 2.0;
        amplitude *= 0.5;
    }
    
    return total / maxValue;
}

// Generate paper texture color
vec4 generatePaperColor(vec2 uv, uint seed) {
    vec2 normalizedUv = uv / ubo.resolution;
    
    // Base paper color (off-white)
    vec3 baseColor = vec3(251.0/255.0, 249.0/255.0, 242.0/255.0);
    
    // Generate noise at different scales
    float broad = fbmNoise(normalizedUv * 7.0, seed, 3);
    float medium = fbmNoise(normalizedUv * 24.0, seed + 71u, 3);
    float fine = fbmNoise(normalizedUv * 100.0, seed + 113u, 2);
    
    // Combine noise with weights
    float variation = broad * 2.4 + medium * 1.25 + fine * 0.65;
    variation *= ubo.grainIntensity;
    
    // Add aging effect
    float ageEffect = fbmNoise(normalizedUv * 5.0, seed + 200u, 2) * 0.5 + 0.5;
    ageEffect *= ubo.agingYellow;
    
    // Calculate final color
    float r = baseColor.r + variation / 255.0;
    float g = baseColor.g + variation * 0.92 / 255.0 + ageEffect * 5.0 / 255.0;
    float b = baseColor.b + variation * 0.78 / 255.0 + ageEffect * 3.0 / 255.0;
    
    return vec4(r, g, b, 1.0);
}

// Add water stains
vec4 addWaterStains(vec2 uv, vec4 color, uint seed) {
    if (int(ubo.waterStainCount) <= 0) {
        return color;
    }
    
    // Calculate water stain positions and sizes
    for (int i = 0; i < int(ubo.waterStainCount); i++) {
        uint stainSeed = seed + 1000u + uint(i);
        
        // Generate random position and size
        vec2 stainCenter = vec2(
            randomFloat(stainSeed, 0u, 0u),
            randomFloat(stainSeed + 1u, 0u, 0u)
        );
        float stainSize = randomFloat(stainSeed + 2u, 0u, 0u) * 0.15 + 0.05;
        
        // Calculate distance from stain center
        vec2 stainUv = (uv / ubo.resolution - stainCenter) * ubo.resolution;
        float dist = length(stainUv) / (stainSize * ubo.resolution.x);
        
        if (dist < 1.0) {
            float stainEffect = (1.0 - dist) * 0.3;
            color.rgb *= (1.0 - stainEffect);
            color.b *= (1.0 - stainEffect * 0.5);
        }
    }
    
    return color;
}

// Main fragment shader
void main() {
    uint seed = ubo.seed;
    vec2 uv = v_texCoord * ubo.resolution;
    
    // Generate base paper color
    vec4 color = generatePaperColor(uv, seed);
    
    // Add water stains
    color = addWaterStains(uv, color, seed);
    
    // Clamp and output
    fragColor = clamp(color, vec4(0.0), vec4(1.0));
}
