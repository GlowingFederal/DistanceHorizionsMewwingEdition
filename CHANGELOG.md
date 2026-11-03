2026-11-03 05:44 — Standalone Java 8 / LWJGL 2 runtime and LOD coverage fixes

- Select the native LWJGL 2 backend directly on Forge 1.7.10 and require UniMixins 0.2.1 or newer. Angelica remains optional.
- Keep the remapped release separate from unnecessary bytecode downgrade outputs, exclude multi-release library classes that Forge's legacy scanner cannot read, and verify Java 8 release contents. Restrict GTNH dependency resolution to its owning groups without changing pinned versions.
- Free temporary native shader-source memory after compilation and enable the OpenGL capability check for the legacy client.
- Defer Angelica's generic DH shader vertex pointer setup when no array buffer is bound. Its existing draw-time buffer binding supplies the real pointer, avoiding LWJGL 2's unbound-offset exception during shader pipeline creation.
- Replace vanilla's completed depth renderbuffer with a depth texture after all framebuffer setup calls finish. This prevents vanilla from reattaching a renderbuffer over DH's texture and fixes the blue-water fade seam. Track attachment ownership per framebuffer for splash transition, resizing, and deletion; retain requested stencil support and leave Angelica's framebuffer ownership intact.
- Resolve the integrated-server save directory without accessing an incompletely initialized dimension array.
- Refresh generation candidates after filling the available queue slots. Previously, one early snapshot could starve newly loaded nearby sections behind a large distant backlog. Preserve saved LOD data and existing generation settings.
- Treat unknown adjacent terrain separately from confirmed air when building LOD sides, preventing artificial coverage-edge cliffs while retaining real cliffs and island sides. Retain neighbor refreshes that arrive during the first mesh upload.
- Release assembled network payload buffers after decoding, replacement, expiration, and shutdown, including orphaned or late fragments. Serialize buffer ownership changes against decoding and close partially decoded data on failure.
