#version 120

varying vec2 texCoord;
varying vec3 worldDir;

uniform mat4 viewRotationInverse;

void main() {
    texCoord = gl_MultiTexCoord0.xy;
    vec4 viewPosition = gl_ModelViewMatrix * gl_Vertex;
    worldDir = (viewRotationInverse * vec4(viewPosition.xyz, 0.0)).xyz;
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
}
