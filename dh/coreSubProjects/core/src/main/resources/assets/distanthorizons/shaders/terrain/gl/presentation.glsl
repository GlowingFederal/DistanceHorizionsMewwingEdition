// Complementary intervals preserve opaque depth ownership; no alpha blending or mesh rebuilds.
uniform float dhp_Min = 0.0;
uniform float dhp_Max = 1.0;
float dhp_threshold(vec2 pixel)
{
    const float bayer[16] = float[16](0., 8., 2., 10., 12., 4., 14., 6., 3., 11., 1., 9., 15., 7., 13., 5.);
    return (bayer[int(mod(pixel.y, 4.0)) * 4 + int(mod(pixel.x, 4.0))] + 0.5) / 16.0;
}
void dhp_present()
{
    float threshold = dhp_threshold(gl_FragCoord.xy);
    if (threshold < dhp_Min || threshold >= dhp_Max) discard;
}
