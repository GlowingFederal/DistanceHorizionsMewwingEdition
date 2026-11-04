/* Foliage shader integration adapted from R-Besson/DistantHorizonsRetro,
 * notably f9560b9 and 6269a69, under the GNU LGPL v3. Existing DH notices apply. */
package com.seibel.distanthorizons.forge17.modAccessor;

import com.seibel.distanthorizons.common.render.openGl.glObject.shader.GlShader;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;

/** Adds only foliage cutouts to Angelica's DH terrain program; water programs keep their code. */
public final class ShaderPackFoliage
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	private static final Pattern MAIN = Pattern.compile("void\\s+main\\s*\\(\\s*(?:void)?\\s*\\)");
	private static final Pattern NORMAL = Pattern.compile("irisExtra\\s*\\.\\s*y");
	private static final Pattern OUTPUT = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*0\\s*\\)\\s*out\\s+vec4\\s+(\\w+)\\s*;");
	private static final String NORMAL_INDEX = "((irisExtra.y & 7u) >= 6u ? 1u : (irisExtra.y & 7u))";
	private static final String RESOURCE = "assets/distanthorizons/shaders/terrain/gl/foliage.glsl";

	public static Map<PatchShaderType, String> patch(Map<PatchShaderType, String> original)
	{
		if (original == null) return null;
		String vertex = original.get(PatchShaderType.VERTEX), fragment = original.get(PatchShaderType.FRAGMENT);
		if (vertex == null || fragment == null || vertex.contains("dhf_packMain")) return original;
		Map<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
		result.putAll(original);
		// Always keep Angelica's six-entry normal lookup in bounds, including on fallback.
		String safeVertex = NORMAL.matcher(vertex).replaceAll(NORMAL_INDEX);
		if (!MAIN.matcher(safeVertex).find()) return original;
		String fallback = MAIN.matcher(safeVertex).replaceFirst("void dhf_packMain()")
			+ "\nvoid main() { dhf_packMain(); if (irisExtra.y == 6u || irisExtra.y == 7u) gl_Position = vec4(2.0, 2.0, 2.0, 1.0); }\n";
		result.put(PatchShaderType.VERTEX, fallback);
		if (original.get(PatchShaderType.GEOMETRY) != null || original.get(PatchShaderType.TESS_CONTROL) != null
			|| original.get(PatchShaderType.TESS_EVAL) != null || !MAIN.matcher(fragment).find())
		{
			LOGGER.info("LOD foliage cutouts unavailable for this shader program; using opaque leaves and omitting plants.");
			return result;
		}
		try
		{
			String patchedVertex = MAIN.matcher(safeVertex).replaceFirst("void dhf_packMain()")
				+ "\nout vec3 dhf_Pos;\nflat out uint dhf_Normal;\nflat out uint dhf_Tile;\nout vec3 dhf_ViewPos;\n"
				+ "void main() { dhf_packMain(); dhf_Pos = vec3(vPosition.xyz); dhf_Normal = irisExtra.y;"
				+ " dhf_Tile = irisExtra.z | (irisExtra.w << 8u); dhf_ViewPos = vec3(vPosition.xyz) + modelOffset; }\n";
			// Only change the opaque DH terrain output. Shader pack lighting and all water code remain intact.
			java.util.regex.Matcher output = OUTPUT.matcher(fragment);
			String color = output.find() ? output.group(1) : null;
			String patchedFragment = MAIN.matcher(fragment).replaceFirst("void dhf_packMain()")
				+ "\nin vec3 dhf_Pos;\nflat in uint dhf_Normal;\nflat in uint dhf_Tile;\nin vec3 dhf_ViewPos;\n"
				+ GlShader.loadFile(RESOURCE, false)
				+ "\nvoid main() { vec4 dhf_tile = vec4(0.5, 0.5, 0.5, 0.0);"
				// Shader packs may disable face culling. Mirrored far-side leaf faces
				// would then fill the near-side mask, unlike the native culled pass.
				+ " if (dhf_Normal >= 8u && !gl_FrontFacing) discard;"
				+ " if (dhf_Normal >= 6u) dhf_tile = dhf_sample(dhf_Pos, dhf_Normal, dhf_Tile, length(dhf_ViewPos));"
				+ " dhf_packMain();"
				+ (color == null ? "" : " if (dhf_Normal >= 6u) " + color + ".rgb = mix(" + color
					+ ".rgb, clamp(" + color + ".rgb * dhf_tile.rgb * 2.0, 0.0, max(1.0, max(" + color
					+ ".r, max(" + color + ".g, " + color + ".b)))), dhf_tile.a);")
				+ " }\n";
			if (compiles(patchedVertex, patchedFragment))
			{
				result.put(PatchShaderType.VERTEX, patchedVertex);
				result.put(PatchShaderType.FRAGMENT, patchedFragment);
			}
		}
		catch (RuntimeException ex) { LOGGER.warn("Unable to add shader pack LOD foliage; using safe fallback.", ex); }
		return result;
	}

	private static boolean compiles(String vertex, String fragment)
	{
		int program = GL20.glCreateProgram(), vert = GL20.glCreateShader(GL20.GL_VERTEX_SHADER), frag = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
		try
		{
			GL20.glShaderSource(vert, vertex); GL20.glCompileShader(vert);
			GL20.glShaderSource(frag, fragment); GL20.glCompileShader(frag);
			if (GL20.glGetShaderi(vert, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE
				|| GL20.glGetShaderi(frag, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE)
			{
				LOGGER.warn("LOD foliage shader rejected: " + GL20.glGetShaderInfoLog(vert, 4096) + GL20.glGetShaderInfoLog(frag, 4096));
				return false;
			}
			GL20.glAttachShader(program, vert); GL20.glAttachShader(program, frag);
			GL20.glBindAttribLocation(program, 0, "vPosition");
			GL20.glBindAttribLocation(program, 1, "iris_color");
			GL20.glBindAttribLocation(program, 2, "irisExtra");
			GL20.glLinkProgram(program);
			if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE) return true;
			LOGGER.warn("LOD foliage shader link rejected: " + GL20.glGetProgramInfoLog(program, 4096));
			return false;
		}
		finally { GL20.glDeleteShader(vert); GL20.glDeleteShader(frag); GL20.glDeleteProgram(program); }
	}

	/** Let Iris allocate and own a sampler; never assume an unused texture unit or leak bindings. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static boolean registerSampler(Object builder)
	{
		try
		{
			// Angelica versions differ in their sampler overloads. Resolve this optional boundary once per program.
			for (Method method : builder.getClass().getMethods())
			{
				Class<?>[] types = method.getParameterTypes();
				if (!method.getName().equals("addDynamicSampler") || types.length != 4
					|| !types[0].isEnum() || types[1] != IntSupplier.class || types[3] != String[].class) continue;
				Object texture2d = Enum.valueOf((Class) types[0], "TEXTURE_2D");
				return (Boolean) method.invoke(builder, texture2d,
					(IntSupplier) () -> Math.max(0, DhApiRenderProxy.getDhBlockRatioAtlasTextureGlId), null, new String[]{"dhf_BlockAtlas"});
			}
		}
		catch (ReflectiveOperationException | RuntimeException ex) { LOGGER.warn("Unable to register optional LOD foliage sampler.", ex); }
		return false;
	}
}
