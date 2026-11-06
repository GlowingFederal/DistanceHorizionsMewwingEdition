/* This file is part of Distant Horizons, licensed under the GNU LGPL v3. */
package com.seibel.distanthorizons.common.render.openGl.terrain;

import com.seibel.distanthorizons.core.render.VanillaSectionCoverage;
import com.seibel.distanthorizons.core.util.math.DhVec3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL21;
import java.nio.ByteBuffer;
import static com.seibel.distanthorizons.lwjgl.LWJGLServiceProvider.LWJGL;

/** Small mesh-ownership texture, updated on uploads/retirement, never by reading block neighbors. */
public final class GlVanillaSectionCoverage
{
	public static final GlVanillaSectionCoverage INSTANCE = new GlVanillaSectionCoverage();
	// Covers the full maximum legacy render distance with a guard around its edges.
	public static final int WIDTH = 131;
	public static final int TEXTURE_UNIT = 2;
	private final ByteBuffer pixels = ByteBuffer.allocateDirect(WIDTH * WIDTH * 4);
	private int textureId;
	private long uploadedRevision = -1;
	private int minX = Integer.MIN_VALUE, minZ = Integer.MIN_VALUE;
	public float offsetX, offsetY, offsetZ;

	public int getTextureId() { return this.textureId; }

	public void update(DhVec3d camera)
	{
		int x = ((int)Math.floor(camera.x) >> 4) - WIDTH / 2;
		int z = ((int)Math.floor(camera.z) >> 4) - WIDTH / 2;
		this.offsetX = (float)(x * 16.0 - camera.x);
		this.offsetY = (float)-camera.y;
		this.offsetZ = (float)(z * 16.0 - camera.z);
		long revision = VanillaSectionCoverage.INSTANCE.getRevision();
		if (revision == this.uploadedRevision && x == this.minX && z == this.minZ) return;
		VanillaSectionCoverage.INSTANCE.writeWindow(this.pixels, x, z, WIDTH);
		int active = LWJGL.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		int binding = LWJGL.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		int unpackBuffer = LWJGL.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
		int[] names = { GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_PIXELS, GL11.GL_UNPACK_SKIP_ROWS, GL11.GL_UNPACK_ALIGNMENT };
		int[] values = new int[names.length];
		for (int i = 0; i < names.length; i++) values[i] = LWJGL.glGetInteger(names[i]);
		try
		{
			LWJGL.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
			for (int i = 0; i < names.length; i++) LWJGL.glPixelStorei(names[i], i == 3 ? 1 : 0);
			if (this.textureId == 0)
			{
				this.textureId = LWJGL.glGenTextures();
				LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
				LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
				LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
				LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL, 0);
				LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, WIDTH, WIDTH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, this.pixels);
			}
			else
			{
				LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
				LWJGL.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, WIDTH, WIDTH, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, this.pixels);
			}
			this.uploadedRevision = revision;
			this.minX = x;
			this.minZ = z;
		}
		finally
		{
			for (int i = 0; i < names.length; i++) LWJGL.glPixelStorei(names[i], values[i]);
			LWJGL.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
			LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, binding);
			LWJGL.glActiveTexture(active);
		}
	}

	public void bind()
	{
		LWJGL.glActiveTexture(GL13.GL_TEXTURE0 + TEXTURE_UNIT);
		LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
	}

	public void free()
	{
		if (this.textureId != 0) LWJGL.glDeleteTextures(this.textureId);
		this.textureId = 0;
		this.uploadedRevision = -1;
	}
}
