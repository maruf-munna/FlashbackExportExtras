#version 330
#ifdef FBEE_SPIRV
// Minecraft 26.3 compiles GLSL to SPIR-V: every interface variable needs an
// explicit location.
#extension GL_ARB_separate_shader_objects : require
#define FBEE_LOCATION(n) layout(location = n)
#else
#define FBEE_LOCATION(n)
#endif

uniform sampler2D InDepth;

layout(std140) uniform DepthParameters {
    float NearPlane;
    float FarPlane;
    float ReversedZ;
    float Linearize;
};
FBEE_LOCATION(0) in vec2 texCoord;
FBEE_LOCATION(0) out float fragDepth;

void main() {
    float depth = texture(InDepth, texCoord).r;
    if (ReversedZ > 0.5) {
        depth = 1.0 - depth;
    }
    if (Linearize > 0.5) {
        float twoNearFar = 2.0 * NearPlane * FarPlane;
        float farMinusNear = FarPlane - NearPlane;
        float farPlusNear = FarPlane + NearPlane;
        depth = twoNearFar / (farPlusNear - (2.0 * depth - 1.0) * farMinusNear);
    }
    fragDepth = depth;
}
