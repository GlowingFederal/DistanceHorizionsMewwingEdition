// Foliage sampling adapted from R-Besson/DistantHorizonsRetro (LGPL v3),
// including derivative-safe UVs and distance-filled leaf cutouts (6269a69).
uniform sampler2D dhf_BlockAtlas;
uniform float dhf_Distance;
uniform bool dhf_Enabled;

vec2 dhf_faceUv(vec3 pos, uint normal)
{
    uint face = normal >= 8u ? normal - 8u : normal;
    if (face >= 6u) return vec2(pos.x, 1.0 - pos.y);
    if (face == 0u) return vec2(pos.x, 1.0 - pos.z);
    if (face == 1u) return vec2(pos.x, pos.z);
    if (face == 2u) return vec2(1.0 - pos.x, 1.0 - pos.y);
    if (face == 3u) return vec2(pos.x, 1.0 - pos.y);
    if (face == 4u) return vec2(pos.z, 1.0 - pos.y);
    return vec2(1.0 - pos.z, 1.0 - pos.y);
}

vec4 dhf_sample(vec3 pos, uint normal, uint tileId, float viewDistance)
{
    bool plant = normal >= 6u && normal < 8u;
    if (plant && (!dhf_Enabled || viewDistance >= dhf_Distance)) discard;
    if (!dhf_Enabled || tileId == 0u) return vec4(0.5, 0.5, 0.5, 0.0);
    vec2 atlasSize = vec2(textureSize(dhf_BlockAtlas, 0));
    vec2 origin = vec2(float(tileId % 256u), float(tileId / 256u)) * 16.0;
    vec2 wrapped = dhf_faceUv(fract(pos), normal) * 16.0;
    vec2 unwrapped = dhf_faceUv(pos, normal) * 16.0;
    // Derivatives of fract() jump at block edges and choose the wrong mipmap.
    vec2 dx = dFdx(unwrapped), dy = dFdy(unwrapped);
    vec4 tile = textureGrad(dhf_BlockAtlas, (origin + wrapped) / atlasSize, dx / atlasSize, dy / atlasSize);
    if (plant && tile.a < 0.5) discard;
    if (normal >= 8u && max(length(dx), length(dy)) < 8.0)
    {
        float gaps = 0.0;
        for (int i = 0; i < 4; i++)
        {
            vec2 offset = vec2((i & 1) == 0 ? -0.25 : 0.25, (i & 2) == 0 ? -0.25 : 0.25);
            vec2 texel = mod(floor(wrapped + dx * offset.x + dy * offset.y), 16.0);
            if (abs(texelFetch(dhf_BlockAtlas, ivec2(origin + texel), 0).a - 254.0 / 255.0) < 0.002) gaps += 0.25;
        }
        // Like mipmapped Minecraft leaves, fill gaps as several texels cover a pixel.
        if (gaps > 0.999) discard;
    }
    return tile;
}
