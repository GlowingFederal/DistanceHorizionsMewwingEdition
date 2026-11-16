// Completed normal chunk meshes own their volume, including the empty halves of partial blocks.
uniform sampler2D dhc_Sections;
uniform vec3 dhc_Offset;
uniform bool dhc_Enabled = false;
// Native composition owns the full mask. Shader packs need LOD background in
// their distance-faded normal terrain, outside Iris's normal near handoff.
uniform float dhc_MaxDistance = 0.0;

void dhc_clipPosition(vec3 cameraRelativePosition, vec3 inward)
{
    if (!dhc_Enabled) return;
    if (dhc_MaxDistance > 0.0 && length(cameraRelativePosition) >= dhc_MaxDistance) return;
    // Sample just inside the LOD surface, so a face on an exact chunk border belongs
    // to its source voxel, not the neighbor on the other side of that face.
    if (!gl_FrontFacing) inward = -inward;
    ivec3 section = ivec3(floor((cameraRelativePosition - inward * 0.02 - dhc_Offset) / 16.0));
    ivec2 size = textureSize(dhc_Sections, 0);
    if (section.y < 0 || section.y >= 16 || any(lessThan(section.xz, ivec2(0))) || any(greaterThanEqual(section.xz, size))) return;
    uvec2 bits = uvec2(round(texelFetch(dhc_Sections, section.xz, 0).rg * 255.0));
    uint mask = bits.x | (bits.y << 8u);
    if ((mask & (1u << uint(section.y))) != 0u) discard;
}

void dhc_clip(vec3 cameraRelativePosition)
{
    vec3 inward = normalize(cross(dFdx(cameraRelativePosition), dFdy(cameraRelativePosition)));
    dhc_clipPosition(cameraRelativePosition, inward);
}

// Evaluate section ownership on the native, unjittered camera ray. Interpolated
// position / clip-W and reciprocal clip-W are affine in window coordinates, so
// this also works for perspective, narrow FOVs and sloped/coarse LOD faces.
// Shader packs retain the one-argument path and their own projection handling.
void dhc_clip(vec3 cameraRelativePosition, vec2 pixelJitter)
{
    // Differentiate the original interpolant, not the derivative-corrected
    // position below: nested screen derivatives have undefined results.
    vec3 inward = normalize(cross(dFdx(cameraRelativePosition), dFdy(cameraRelativePosition)));
    float reciprocalW = gl_FragCoord.w;
    vec3 numerator = cameraRelativePosition * reciprocalW;
    vec3 nativePosition = (numerator + dFdx(numerator) * pixelJitter.x + dFdy(numerator) * pixelJitter.y)
        / (reciprocalW + dFdx(reciprocalW) * pixelJitter.x + dFdy(reciprocalW) * pixelJitter.y);
    dhc_clipPosition(nativePosition, inward);
}
