package com.seibel.distanthorizons.lwjgl;

import com.seibel.distanthorizons.lwjgl.lwjgl2.LWJGL2Service;

/** Minecraft 1.7.10 uses the OpenGL context owned by its native LWJGL 2 runtime. */
public final class LWJGLServiceProvider
{
    public static final ILWJGLService LWJGL = LWJGL2Service.create();
    public static final int POINTER_SIZE = LWJGL.getPointerSize();
    public static final long NULL = 0L;

    private LWJGLServiceProvider() { }
}
