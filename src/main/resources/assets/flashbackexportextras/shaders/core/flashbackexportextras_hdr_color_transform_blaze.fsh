#version 330
#ifdef FBEE_SPIRV
// Minecraft 26.3 compiles GLSL to SPIR-V: every interface variable needs an
// explicit location.
#extension GL_ARB_separate_shader_objects : require
#define FBEE_LOCATION(n) layout(location = n)
#else
#define FBEE_LOCATION(n)
#endif

uniform sampler2D InSampler;

layout(std140) uniform HdrParameters {
    float PeakBrightness;
};

FBEE_LOCATION(0) in vec2 texCoord;
FBEE_LOCATION(0) out vec4 fragColor;

const float PQ_M1 = 2610.0 / 16384.0;
const float PQ_M2 = 2523.0 / 32.0;
const float PQ_C1 = 3424.0 / 4096.0;
const float PQ_C2 = 2413.0 / 128.0;
const float PQ_C3 = 2392.0 / 128.0;

const mat3 BT709_TO_BT2020 = mat3(
    vec3(0.6274039149, 0.0690972880, 0.0163914394),
    vec3(0.3292830288, 0.9195404053, 0.0880133063),
    vec3(0.0433130674, 0.0113623152, 0.8955952525)
);

vec3 srgbDecode(vec3 color) {
    vec3 signColor = sign(color);
    vec3 absoluteColor = abs(color);
    vec3 linear = mix(
        pow((absoluteColor + vec3(0.055)) / vec3(1.055), vec3(2.4)),
        absoluteColor / vec3(12.92),
        lessThan(absoluteColor, vec3(0.04045))
    );
    return linear * signColor;
}

vec3 pqEncode(vec3 color) {
    vec3 normalized = max(color, vec3(0.0)) * PeakBrightness / 10000.0;
    vec3 power = pow(normalized, vec3(PQ_M1));
    vec3 pq = (vec3(PQ_C1) + vec3(PQ_C2) * power) / (vec3(1.0) + vec3(PQ_C3) * power);
    return clamp(pow(pq, vec3(PQ_M2)), 0.0, 1.0);
}

void main() {
    vec4 color = texture(InSampler, texCoord);
    color.rgb = pqEncode(BT709_TO_BT2020 * srgbDecode(color.rgb));
    fragColor = color;
}
