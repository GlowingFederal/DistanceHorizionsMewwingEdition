package tests;

import com.seibel.distanthorizons.core.util.math.CameraRelativeTransform;
import com.seibel.distanthorizons.core.util.math.DhMat4f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.Test;
import static org.junit.Assert.*;

public class CameraRelativeTransformTest
{
	@Test public void cameraRelativeVerticesMatchVanillaInEveryPerspective()
	{
		// Include camera collision distances, arbitrary heading/pitch, front F5,
		// first-person translation, and hurt/bobbing scale and rotation.
		for (float distance : new float[]{0.0f, 0.1f, 1.25f, 4.0f})
		for (boolean front : new boolean[]{false, true})
		for (float yaw : new float[]{0.0f, 0.7f, 2.9f})
		{
			Matrix4f raw = new Matrix4f().translate(0.02f, -0.03f, -distance)
				.rotateX(0.3f).rotateY(yaw + (front ? (float)Math.PI : 0.0f))
				.rotateZ(0.04f).scale(1.01f, 0.99f, 1.0f);
			DhMat4f view = new DhMat4f(raw);
			CameraRelativeTransform transform = new CameraRelativeTransform();
			transform.capture(view);
			Matrix4f relative = view.createJomlMatrix();
			for (Vector3f vertex : new Vector3f[]{new Vector3f(1, -2, 3), new Vector3f(-64, 15, 128)})
			{
				Vector3f expected = raw.transformPosition(new Vector3f(vertex));
				Vector3f actual = relative.transformPosition(new Vector3f(vertex).sub(
					(float)transform.offset.x, (float)transform.offset.y, (float)transform.offset.z));
				assertEquals(expected.x, actual.x, 0.00005f);
				assertEquals(expected.y, actual.y, 0.00005f);
				assertEquals(expected.z, actual.z, 0.00005f);
			}
			assertEquals(0.0f, view.m03, 0.0f);
			assertEquals(0.0f, view.m13, 0.0f);
			assertEquals(0.0f, view.m23, 0.0f);
		}
	}

	@Test public void switchingBackToFirstPersonReplacesThePreviousOffset()
	{
		CameraRelativeTransform transform = new CameraRelativeTransform();
		transform.capture(new DhMat4f(new Matrix4f().translate(0, 0, -4)));
		assertEquals(4.0, transform.offset.z, 0.0);
		transform.capture(new DhMat4f(new Matrix4f()));
		assertEquals(0.0, transform.offset.x, 0.0);
		assertEquals(0.0, transform.offset.y, 0.0);
		assertEquals(0.0, transform.offset.z, 0.0);
	}
}
