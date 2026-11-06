package com.seibel.distanthorizons.forge17.modAccessor;

import com.seibel.distanthorizons.common.render.openGl.glObject.shader.GlShader;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Screen-space only: shader pack lighting, materials and water computations stay authoritative. */
public final class ShaderPackLodPresentation
{
    private static final Pattern MAIN = Pattern.compile("void\\s+main\\s*\\(\\s*(?:void)?\\s*\\)");
    public static Map<PatchShaderType, String> patch(Map<PatchShaderType, String> original)
    {
        if (original == null) return null;
        String fragment = original.get(PatchShaderType.FRAGMENT);
        if (fragment == null || fragment.contains("dhp_packMain") || !MAIN.matcher(fragment).find()) return original;
        Map<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
        result.putAll(original);
        result.put(PatchShaderType.FRAGMENT, MAIN.matcher(fragment).replaceFirst("void dhp_packMain()")
            + "\n" + GlShader.loadFile("assets/distanthorizons/shaders/terrain/gl/presentation.glsl", false)
            + "\nvoid main() { dhp_present(); dhp_packMain(); }\n");
        String vertex = original.get(PatchShaderType.VERTEX);
        if (vertex != null && MAIN.matcher(vertex).find()
            && original.get(PatchShaderType.GEOMETRY) == null
            && original.get(PatchShaderType.TESS_CONTROL) == null && original.get(PatchShaderType.TESS_EVAL) == null)
        {
            result.put(PatchShaderType.VERTEX, MAIN.matcher(vertex).replaceFirst("void dhc_packVertex()")
                + "\nout vec3 dhc_Position;\nvoid main() { dhc_packVertex(); dhc_Position = vec3(vPosition.xyz) + modelOffset; }\n");
            result.put(PatchShaderType.FRAGMENT, MAIN.matcher(result.get(PatchShaderType.FRAGMENT)).replaceFirst("void dhc_packFragment()")
                + "\nin vec3 dhc_Position;\n"
                + GlShader.loadFile("assets/distanthorizons/shaders/terrain/gl/vanilla_coverage.glsl", false)
                + "\nvoid main() { dhc_clip(dhc_Position); dhc_packFragment(); }\n");
        }
        return result;
    }
}
