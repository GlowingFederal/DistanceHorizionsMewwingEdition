package com.seibel.distanthorizons.core.util.math;

import org.joml.Matrix4f;

/** Converts an entity-relative legacy view into a camera-relative view. */
public final class CameraRelativeTransform
{
	public final DhVec3d offset = new DhVec3d();
	private final Matrix4f inverse = new Matrix4f();

	/** Capture the camera offset before removing its translation from the supplied view. */
	public void capture(DhMat4f view)
	{
		this.inverse.set(
			view.m00, view.m10, view.m20, view.m30,
			view.m01, view.m11, view.m21, view.m31,
			view.m02, view.m12, view.m22, view.m32,
			view.m03, view.m13, view.m23, view.m33).invert();
		this.offset.set(this.inverse.m30(), this.inverse.m31(), this.inverse.m32());
		// Vertices already subtract the actual camera position. Keeping this
		// translation would apply third-person distance (and view bobbing) twice.
		view.m03 = 0.0f;
		view.m13 = 0.0f;
		view.m23 = 0.0f;
	}
}
