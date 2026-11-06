// Completed normal chunk meshes own their volume, including the empty halves of partial blocks.
uniform sampler2D dhc_Sections;
uniform vec3 dhc_Offset;
uniform bool dhc_Enabled = false;

void dhc_clip(vec3 cameraRelativePosition)
{
    if (!dhc_Enabled) return;
    // Sample just inside the LOD surface, so a face on an exact chunk border belongs
    // to its source voxel, not the neighbor on the other side of that face.
    vec3 inward = normalize(cross(dFdx(cameraRelativePosition), dFdy(cameraRelativePosition)));
    if (!gl_FrontFacing) inward = -inward;
    ivec3 section = ivec3(floor((cameraRelativePosition - inward * 0.02 - dhc_Offset) / 16.0));
    ivec2 size = textureSize(dhc_Sections, 0);
    if (section.y < 0 || section.y >= 16 || any(lessThan(section.xz, ivec2(0))) || any(greaterThanEqual(section.xz, size))) return;
    uvec2 bits = uvec2(round(texelFetch(dhc_Sections, section.xz, 0).rg * 255.0));
    uint mask = bits.x | (bits.y << 8u);
    if ((mask & (1u << uint(section.y))) != 0u) discard;
}
