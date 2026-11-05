package com.seibel.distanthorizons.common.backports;

#if MC_VER <= MC_1_7_10
import com.seibel.distanthorizons.core.util.math.CameraRelativeTransform;
import com.seibel.distanthorizons.core.util.math.DhMat4f;
import net.minecraft.entity.EntityLivingBase;

import org.joml.Vector3d;
#endif

/**
 * Recreation of MC's 1.12+ camera object
 * so 1.7.10 can use similar code to 1.12.
 */
public class Camera
{
	#if MC_VER <= MC_1_7_10
	
	public static final Camera INSTANCE = new Camera();
	
    private final Vector3d pos = new Vector3d();
	private final CameraRelativeTransform transform = new CameraRelativeTransform();

	/** Called with the captured vanilla view, before DH publishes its camera-relative copy. */
	public void captureModelView(DhMat4f view) { this.transform.capture(view); }

	public void update(EntityLivingBase entity, float partialTicks)
	{
		// the render view entity is briefly null while changing dimensions,
		// keep the last position until it's available again
		if (entity == null)
		{
			return;
		}

		final double camX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks + this.transform.offset.x;
		final double camY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks + this.transform.offset.y;
		final double camZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks + this.transform.offset.z;
		this.pos.set(camX, camY, camZ);
	}
	
	public Vector3d getPos() { return this.pos; }
	
	#endif
}
