package tests;

import com.seibel.distanthorizons.core.render.VanillaSectionCoverage;
import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class VanillaSectionCoverageTest
{
	@Test public void onlyCompletedMeshesOwnTheirSectionIncludingPartialBlockGaps()
	{
		VanillaSectionCoverage coverage = new VanillaSectionCoverage();
		Object roof = new Object();
		assertEquals(0, coverage.getMask(-43, 24));
		coverage.set(roof, -43, 4, 24, true);
		// The entire section belongs to normal meshes, including stair/slab empty halves.
		assertEquals(1 << 4, coverage.getMask(-43, 24));
		assertEquals(0, coverage.getMask(-42, 24));
		coverage.set(roof, -43, 4, 24, false);
		assertEquals(0, coverage.getMask(-43, 24));
	}

	@Test public void staleRetirementCannotRevokeReplacementAndUnchangedEventsDoNotUpload()
	{
		VanillaSectionCoverage coverage = new VanillaSectionCoverage();
		Object old = new Object(), replacement = new Object();
		coverage.set(old, 1, 15, -2, true);
		long revision = coverage.getRevision();
		coverage.set(old, 1, 15, -2, true);
		coverage.set(replacement, 1, 15, -2, true);
		coverage.set(old, 1, 15, -2, false);
		assertEquals(revision, coverage.getRevision());
		assertEquals(0x8000, coverage.getMask(1, -2));
		coverage.set(replacement, 1, 15, -2, false);
		assertEquals(0, coverage.getMask(1, -2));
	}

	@Test public void worldResetInvalidatesOwnershipAndOldCallbacks()
	{
		VanillaSectionCoverage coverage = new VanillaSectionCoverage();
		Object old = new Object(), current = new Object();
		coverage.set(old, -1, 0, -1, true);
		coverage.clear();
		assertEquals(0, coverage.getMask(-1, -1));
		coverage.set(current, -1, 0, -1, true);
		coverage.set(old, -1, 0, -1, false);
		assertEquals(1, coverage.getMask(-1, -1));
	}

	@Test public void gpuWindowKeepsVerticalBitsAndNegativeCoordinatesSeparate()
	{
		VanillaSectionCoverage coverage = new VanillaSectionCoverage();
		Object mesh = new Object();
		coverage.set(mesh, -2, 0, -3, true);
		coverage.set(mesh, -2, 15, -3, true);
		coverage.set(mesh, -1, 8, -2, true);
		coverage.set(mesh, -1, 16, -2, true);
		coverage.set(mesh, -1, -1, -2, true);
		coverage.beginVisibility();
		coverage.markVisible(-2, -3);
		coverage.markVisible(-1, -2);
		coverage.endVisibility();
		ByteBuffer bytes = ByteBuffer.allocate(16);
		coverage.writeWindow(bytes, -2, -3, 2);
		assertEquals(1, bytes.get(0) & 255);
		assertEquals(128, bytes.get(1) & 255);
		assertEquals(0, bytes.get(4));
		assertEquals(0, bytes.get(8));
		assertEquals(0, bytes.get(12));
		assertEquals(1, bytes.get(13));
		assertEquals(16, bytes.remaining());
	}

	@Test public void cachedMeshesOutsideTheCurrentDrawListCannotHideFallbackTerrain()
	{
		VanillaSectionCoverage coverage = new VanillaSectionCoverage();
		Object mesh = new Object();
		coverage.set(mesh, 0, 4, 0, true);
		coverage.set(mesh, 1, 4, 0, true);
		ByteBuffer bytes = ByteBuffer.allocate(16);
		coverage.beginVisibility();
		coverage.markVisible(0, 0);
		coverage.endVisibility();
		coverage.writeWindow(bytes, 0, 0, 2);
		assertEquals(16, bytes.get(0));
		assertEquals(0, bytes.get(4));
		long revision = coverage.getRevision();
		coverage.beginVisibility();
		coverage.markVisible(0, 0);
		coverage.endVisibility();
		assertEquals(revision, coverage.getRevision());
		// Model a render-distance reduction without evicting a cached uploaded mesh.
		coverage.beginVisibility();
		coverage.endVisibility();
		coverage.writeWindow(bytes, 0, 0, 2);
		assertEquals(0, bytes.get(0));
		assertEquals(16, coverage.getMask(0, 0));
	}
}
