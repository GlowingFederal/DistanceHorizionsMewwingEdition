package com.seibel.distanthorizons.core.render;

import java.util.ArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.List;
import java.util.function.Consumer;

/** Render-thread frontier handoff. Owns one lease per mesh and at most two frontiers. */
public final class LodPresentation<T> implements AutoCloseable
{
    public static float shaderMin, shaderMax = 1.0f;
    private final Consumer<T> release;
    private final ArrayList<T> current = new ArrayList<>(), outgoing = new ArrayList<>();
    private final ObjectOpenHashSet<T> currentSet = new ObjectOpenHashSet<>(), incoming = new ObjectOpenHashSet<>(), desiredSet = new ObjectOpenHashSet<>();
    private long startedNanos;
    private int durationMs;
    private boolean transitioning;
    private float progress = 1.0f;

    public LodPresentation(Consumer<T> release) { this.release = release; }

    /** Consumes the desired frontier's leases, even when a prior handoff is still finishing. */
    public void update(List<T> desired, long nowNanos, int durationMs)
    {
        if (this.transitioning)
        {
            this.progress = progress(nowNanos, this.startedNanos, this.durationMs);
            if (durationMs == 0 || this.progress >= 1.0f) this.finish();
            else { desired.forEach(this.release); return; }
        }
        this.desiredSet.clear();
        this.desiredSet.addAll(desired);
        if (this.currentSet.equals(this.desiredSet)) { desired.forEach(this.release); return; }

        this.incoming.clear();
        for (T mesh : desired) if (!this.currentSet.contains(mesh)) this.incoming.add(mesh);
        for (T mesh : this.current)
        {
            if (this.desiredSet.contains(mesh)) this.release.accept(mesh); // desired transfers its lease
            else this.outgoing.add(mesh);
        }
        this.current.clear();
        this.current.addAll(desired);
        this.currentSet.clear();
        this.currentSet.addAll(this.desiredSet);
        this.startedNanos = nowNanos;
        this.durationMs = durationMs;
        this.transitioning = true;
        this.progress = 0.0f;
        if (durationMs == 0) this.finish();
    }
    public static float progress(long nowNanos, long startedNanos, int durationMs)
    {
        if (durationMs <= 0) return 1.0f;
        float t = Math.max(0.0f, Math.min(1.0f, (nowNanos - startedNanos) / (durationMs * 1_000_000.0f)));
        return t * t * (3.0f - 2.0f * t);
    }
    private void finish()
    {
        this.outgoing.forEach(this.release);
        this.outgoing.clear();
        this.incoming.clear();
        this.transitioning = false;
        this.progress = 1.0f;
    }
    public boolean matchesCurrent(List<T> desired)
    {
        this.desiredSet.clear();
        this.desiredSet.addAll(desired);
        return this.currentSet.equals(this.desiredSet);
    }
    public List<T> current() { return this.current; }
    public List<T> outgoing() { return this.outgoing; }
    public boolean isCurrent(T mesh) { return this.currentSet.contains(mesh); }
    public boolean isIncoming(T mesh) { return this.incoming.contains(mesh); }
    public float progress() { return this.progress; }
    @Override public void close()
    {
        this.current.forEach(this.release);
        this.current.clear();
        this.currentSet.clear();
        this.finish();
        this.desiredSet.clear();
    }
}
