#version 320 es
/**
 * Geometry shader to output 3 lines for the TBN vectors
 */
precision highp float;
//Put array declaration after name for GLSL compatibility

layout(points) in;
layout(line_strip, max_vertices = 6) out;

uniform vec4 _EMISSIVE[3];
uniform mat4 uModelMatrix[3];

in vec4 vNormal[];
in vec4 vTangent[];
in vec4 vBitangent[];

out vec4 fColor;

/**
 * Used to draw vector from a position
 */
void main() {
    fColor = _EMISSIVE[0];
    vec4 pos = gl_in[0].gl_Position;
    
    gl_Position = pos;
    EmitVertex();
    gl_Position = pos + vNormal[0];
    EmitVertex();
    EndPrimitive();
    
    fColor = _EMISSIVE[1];
    gl_Position = pos;
    EmitVertex();
    gl_Position = pos + vTangent[0];
    EmitVertex();
    EndPrimitive();
    
    fColor = _EMISSIVE[2];
    gl_Position = pos;
    EmitVertex();
    gl_Position = pos + vBitangent[0];
    EmitVertex();
    EndPrimitive();
    
}
