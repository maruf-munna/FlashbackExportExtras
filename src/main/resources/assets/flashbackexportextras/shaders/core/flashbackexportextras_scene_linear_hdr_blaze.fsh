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

FBEE_LOCATION(0) in vec2 texCoord;
FBEE_LOCATION(0) out vec4 fragColor;

vec3 srgbDecodeSafe(vec3 color) {
    vec3 signColor = sign(color);
    vec3 absoluteColor = abs(color);
    vec3 linear = mix(
        pow((absoluteColor + vec3(0.055)) / vec3(1.055), vec3(2.4)),
        absoluteColor / vec3(12.92),
        lessThan(absoluteColor, vec3(0.04045))
    );
    return linear * signColor;
}

void main() {
    vec4 color = texture(InSampler, texCoord);
    fragColor = vec4(srgbDecodeSafe(color.rgb), 1.0);
}
