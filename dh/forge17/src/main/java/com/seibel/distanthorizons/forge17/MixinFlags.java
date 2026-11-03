package com.seibel.distanthorizons.forge17;

/**
 * Simple flags class with no dependencies, safe to load from any thread
 * including the FML splash thread.
 */
public class MixinFlags 
{
	/** Enable native depth-texture conversion after splash rendering has finished. */
    public static volatile boolean framebufferMixinEnabled = false;
}
