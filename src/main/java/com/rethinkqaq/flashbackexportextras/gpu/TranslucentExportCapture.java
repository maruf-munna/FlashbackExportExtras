/*
 * Flashback Export Extras
 * Copyright (C) RethinkQAQ
 *
 * This file is part of Flashback Export Extras.
 *
 * Flashback Export Extras is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Flashback Export Extras is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along
 * with Flashback Export Extras. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.rethinkqaq.flashbackexportextras.gpu;

//? if >=26.3 {
/*import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.rethinkqaq.flashbackexportextras.FlashbackExportExtras;
import com.rethinkqaq.flashbackexportextras.exporting.DepthCaptureState;
import com.rethinkqaq.flashbackexportextras.exporting.TranslucentCaptureState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/^*
 * Export Transparent (26.3, Improved Transparency = OIT): splits glass / water / particles / weather / clouds out of
 * each EXR frame, the way FlashPlus' live take does it:
 * <ul>
 *   <li>start of OIT: the solid colour and solid depth are copied (the EXR's Depth then has no glass in it),</li>
 *   <li>end of OIT: the OIT composite is drawn a second time into a cleared target = Trans (premultiplied RGBA) and
 *       its depth = TransDepth,</li>
 *   <li>after the level (hand included, no GUI): Combined = the final picture where something was drawn after the
 *       glass (hand, screen effects), else the solid snapshot.</li>
 * </ul>
 * The game's own picture is not changed. Render thread only; GL state is restored after each step.
 ^/
public final class TranslucentExportCapture {
    public static final TranslucentExportCapture INSTANCE = new TranslucentExportCapture();

    private static final int SLOTS = 3;
    private static final long WAIT_NANOS = 1_000_000_000L;
    /^* Min difference (0..1) between final and solid-under-glass that counts as drawn after the glass. ^/
    private static final float RESOLVE_THRESHOLD = 4.0f / 255.0f;

    private static final String VERTEX_SHADER = """
            #version 330 core
            void main() {
                vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String RESOLVE_FRAGMENT_SHADER = """
            #version 330 core
            uniform sampler2D uOpaque;
            uniform sampler2D uLayer;
            uniform sampler2D uFinal;
            uniform float uThreshold;
            out vec4 fragColor;
            void main() {
                ivec2 p = ivec2(gl_FragCoord.xy);
                vec3 opaque = texelFetch(uOpaque, p, 0).rgb;
                vec4 layer = texelFetch(uLayer, p, 0);
                vec3 finalColor = texelFetch(uFinal, p, 0).rgb;
                vec3 predicted = opaque * (1.0 - layer.a) + layer.rgb;
                vec3 diff = abs(finalColor - predicted);
                bool drawnLater = max(diff.r, max(diff.g, diff.b)) > uThreshold;
                fragColor = vec4(drawnLater ? finalColor : opaque, 1.0);
            }
            """;

    private int width;
    private int height;
    private boolean broken;
    private TextureTarget solidDepthTarget;
    private TextureTarget layerTarget;
    private int opaqueTexture;
    private int opaqueFbo;
    private int resolvedTexture;
    private int resolvedFbo;
    private int readFbo;
    private int depthReadFbo;
    private int depthDrawFbo;
    private int resolveProgram;
    private int resolveOpaqueLoc = -1;
    private int resolveLayerLoc = -1;
    private int resolveFinalLoc = -1;
    private int resolveThresholdLoc = -1;
    private int vao;
    private int sampler;

    // export frame whose solid snapshot is taken (-1 = none); its layer was drawn
    private long splitFrame = -1L;
    private boolean layerDrawn;
    // last export frame handed to the EXR writer (one TransFrame per frame)
    private long lastSubmittedFrame = -1L;
    private boolean warnedNoSplit;

    private final int[][] pbos = new int[SLOTS][3];
    private final long[] fences = new long[SLOTS];
    private final long[] slotFrames = new long[SLOTS];
    private final float[] slotFar = new float[SLOTS];
    private int pboWidth = -1;
    private int pboHeight = -1;
    private int writeSlot;

    private TranslucentExportCapture() {
    }

    /^* Start of OIT: copies the solid colour and depth of the frame being exported. ^/
    public void beginOit(RenderTarget main) {
        if (!TranslucentCaptureState.active || broken || main == null) {
            return;
        }
        long frameId = DepthCaptureState.pendingCaptureFrameId();
        if (frameId < 0L) {
            return;
        }
        int color = glId(main.getColorTexture());
        int depth = glId(main.getDepthTexture());
        if (color == 0 || depth == 0 || !ensure(main.width, main.height)) {
            return;
        }
        blit(color, opaqueFbo, GL11.GL_COLOR_BUFFER_BIT, false);
        blitDepth(depth, glId(solidDepthTarget.getDepthTexture()));
        splitFrame = frameId;
        layerDrawn = false;
    }

    /^* The cleared target the OIT composite is drawn into a second time, or null when this frame is not split. ^/
    public RenderTarget layerTargetForOit() {
        return splitFrame >= 0L && splitFrame == DepthCaptureState.pendingCaptureFrameId() ? layerTarget : null;
    }

    public void finishOit(boolean ok) {
        layerDrawn = ok && splitFrame >= 0L;
        if (!ok) {
            splitFrame = -1L;
        }
    }

    /^* The solid-only depth of the frame being captured (no glass), or null to read the main depth. ^/
    public RenderTarget solidDepthFor(long frameId) {
        return TranslucentCaptureState.active && layerDrawn && splitFrame == frameId ? solidDepthTarget : null;
    }

    /^* After the level (hand drawn, GUI not yet): resolves the solid colour and reads back the frame's layers. ^/
    public void afterLevel(RenderTarget main) {
        if (!TranslucentCaptureState.active) {
            return;
        }
        collect(0L);
        long frameId = DepthCaptureState.submittedCaptureFrameId();
        if (frameId < 0L || frameId <= lastSubmittedFrame) {
            return;
        }
        lastSubmittedFrame = frameId;
        boolean split = layerDrawn && splitFrame == frameId && !broken && main != null
                && main.width == width && main.height == height;
        splitFrame = -1L;
        layerDrawn = false;
        if (!split) {
            if (!warnedNoSplit) {
                warnedNoSplit = true;
                FlashbackExportExtras.LOGGER.warn("Export Transparent: no translucent layer for frame {} (Video"
                        + " Settings > Improved Transparency off, or an Iris shader pack): glass stays in Combined"
                        + " and Depth, Trans is empty", frameId);
            }
            TranslucentCaptureState.submit(new TranslucentCaptureState.TransFrame(frameId, null, null, null,
                    0.05f, DepthCaptureState.depthFar));
            return;
        }
        try {
            resolve(glId(main.getColorTexture()));
            issueReadback(frameId);
        } catch (RuntimeException e) {
            broken = true;
            TranslucentCaptureState.fail(e);
            FlashbackExportExtras.LOGGER.error("Export Transparent readback failed for frame " + frameId, e);
        }
    }

    /^* Waits for every readback in flight (before the EXR writer finishes). ^/
    public void flush() {
        if (!collect(WAIT_NANOS)) {
            TranslucentCaptureState.fail(new IllegalStateException("Timed out flushing the translucent layer"));
        }
    }

    /^* Frees everything (export end). ^/
    public void reset() {
        collect(WAIT_NANOS);
        for (int i = 0; i < SLOTS; i++) {
            if (fences[i] != 0L) {
                GL32.glDeleteSync(fences[i]);
                fences[i] = 0L;
            }
        }
        deletePbos();
        if (layerTarget != null) {
            layerTarget.destroyBuffers();
            layerTarget = null;
        }
        if (solidDepthTarget != null) {
            solidDepthTarget.destroyBuffers();
            solidDepthTarget = null;
        }
        for (int fbo : new int[] {opaqueFbo, resolvedFbo, readFbo, depthReadFbo, depthDrawFbo}) {
            if (fbo != 0) GlStateManager._glDeleteFramebuffers(fbo);
        }
        opaqueFbo = resolvedFbo = readFbo = depthReadFbo = depthDrawFbo = 0;
        if (opaqueTexture != 0) GlStateManager._deleteTexture(opaqueTexture);
        if (resolvedTexture != 0) GlStateManager._deleteTexture(resolvedTexture);
        opaqueTexture = resolvedTexture = 0;
        if (resolveProgram != 0) GlStateManager.glDeleteProgram(resolveProgram);
        resolveProgram = 0;
        if (sampler != 0) GL33.glDeleteSamplers(sampler);
        sampler = 0;
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        vao = 0;
        width = height = 0;
        splitFrame = -1L;
        layerDrawn = false;
        lastSubmittedFrame = -1L;
        warnedNoSplit = false;
        broken = false;
    }

    // --- readback -------------------------------------------------------------------------------------------------

    private void issueReadback(long frameId) {
        ensurePbos();
        int slot = writeSlot;
        if (fences[slot] != 0L && !collectSlot(slot, WAIT_NANOS)) {
            throw new IllegalStateException("Timed out waiting for translucent readback slot " + slot);
        }
        int oldPbo = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            read(pbos[slot][0], resolvedTexture, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
            read(pbos[slot][1], glId(layerTarget.getColorTexture()), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
            read(pbos[slot][2], glId(layerTarget.getDepthTexture()), GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, oldPbo);
        }
        slotFrames[slot] = frameId;
        slotFar[slot] = DepthCaptureState.depthFar;
        fences[slot] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        writeSlot = (slot + 1) % SLOTS;
    }

    private static void read(int pbo, int texture, int format, int type) {
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, format, type, 0L);
    }

    /^* Hands every finished readback to the EXR writer, in frame order; false if one is still running after the wait. ^/
    private boolean collect(long timeoutNanos) {
        boolean done = true;
        for (int n = 0; n < SLOTS; n++) {
            int slot = (writeSlot + n) % SLOTS;
            if (fences[slot] != 0L && !collectSlot(slot, timeoutNanos)) {
                done = false;
            }
        }
        return done;
    }

    private boolean collectSlot(int slot, long timeoutNanos) {
        long fence = fences[slot];
        int result = GL32.glClientWaitSync(fence, timeoutNanos > 0L ? GL32.GL_SYNC_FLUSH_COMMANDS_BIT : 0, timeoutNanos);
        if (result == GL32.GL_WAIT_FAILED) {
            throw new IllegalStateException("GPU fence wait failed for the translucent layer");
        }
        if (result != GL32.GL_ALREADY_SIGNALED && result != GL32.GL_CONDITION_SATISFIED) {
            return false;
        }
        GL32.glDeleteSync(fence);
        fences[slot] = 0L;
        long pixels = (long) pboWidth * pboHeight;
        ByteBuffer solid = copyPbo(pbos[slot][0], pixels * 4);
        ByteBuffer layer = copyPbo(pbos[slot][1], pixels * 4);
        ByteBuffer depth = copyPbo(pbos[slot][2], pixels * 4);
        TranslucentCaptureState.submit(new TranslucentCaptureState.TransFrame(slotFrames[slot], solid, layer, depth,
                0.05f, slotFar[slot]));
        return true;
    }

    private static ByteBuffer copyPbo(int pbo, long bytes) {
        int oldPbo = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo);
        try {
            ByteBuffer mapped = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY, bytes, null);
            if (mapped == null) {
                throw new IllegalStateException("Could not map the translucent layer readback");
            }
            ByteBuffer copy = MemoryUtil.memAlloc((int) bytes);
            MemoryUtil.memCopy(MemoryUtil.memAddress(mapped), MemoryUtil.memAddress(copy), bytes);
            GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            return copy;
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, oldPbo);
        }
    }

    private void ensurePbos() {
        if (pboWidth == width && pboHeight == height && pbos[0][0] != 0) {
            return;
        }
        if (!collect(WAIT_NANOS)) {
            throw new IllegalStateException("Translucent readback still running while the frame size changed");
        }
        deletePbos();
        long bytes = (long) width * height * 4;
        int oldPbo = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        for (int[] slot : pbos) {
            for (int i = 0; i < slot.length; i++) {
                slot[i] = GL15.glGenBuffers();
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot[i]);
                GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, bytes, GL15.GL_STREAM_READ);
            }
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, oldPbo);
        pboWidth = width;
        pboHeight = height;
        writeSlot = 0;
    }

    private void deletePbos() {
        for (int[] slot : pbos) {
            for (int i = 0; i < slot.length; i++) {
                if (slot[i] != 0) GL15.glDeleteBuffers(slot[i]);
                slot[i] = 0;
            }
        }
        pboWidth = pboHeight = -1;
    }

    // --- GPU work -------------------------------------------------------------------------------------------------

    /^* resolved = final where something was drawn after the glass (hand, screen effects), else the solid snapshot. ^/
    private void resolve(int finalColor) {
        if (finalColor == 0 || !ensureResolveProgram()) {
            throw new IllegalStateException("Translucent resolve is unavailable");
        }
        State state = State.save(3);
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, resolvedFbo);
            GlStateManager._viewport(0, 0, width, height);
            GlStateManager._disableScissorTest();
            GlStateManager._disableCull();
            GlStateManager._disableDepthTest();
            GlStateManager._disableBlend(0);
            GlStateManager._colorMask(0, 0xF);
            GlStateManager._glUseProgram(resolveProgram);
            int[] ids = {opaqueTexture, glId(layerTarget.getColorTexture()), finalColor};
            int[] locs = {resolveOpaqueLoc, resolveLayerLoc, resolveFinalLoc};
            for (int i = 0; i < 3; i++) {
                GlStateManager._activeTexture(GL14.GL_TEXTURE0 + i);
                GlStateManager._bindTexture(ids[i]);
                GL33.glBindSampler(i, sampler);
                GlStateManager._glUniform1i(locs[i], i);
            }
            GL20.glUniform1f(resolveThresholdLoc, RESOLVE_THRESHOLD);
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._drawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            state.restore();
        }
    }

    private void blit(int sourceTexture, int destinationFbo, int mask, boolean depth) {
        int prevRead = GlStateManager.getFrameBuffer(GL30.GL_READ_FRAMEBUFFER);
        int prevDraw = GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GlStateManager._disableScissorTest();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,
                depth ? GL30.GL_DEPTH_ATTACHMENT : GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, sourceTexture, 0);
        if (!depth) {
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destinationFbo);
        GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height, mask, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        if (prevScissor) {
            GlStateManager._enableScissorTest();
        }
    }

    /^* main depth -> solid depth snapshot (both D32F). ^/
    private void blitDepth(int sourceDepth, int destinationDepth) {
        int prevRead = GlStateManager.getFrameBuffer(GL30.GL_READ_FRAMEBUFFER);
        int prevDraw = GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GlStateManager._disableScissorTest();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, depthReadFbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, sourceDepth, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthDrawFbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, destinationDepth, 0);
        GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT,
                GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        if (prevScissor) {
            GlStateManager._enableScissorTest();
        }
    }

    private boolean ensure(int newWidth, int newHeight) {
        if (broken) {
            return false;
        }
        if (vao == 0) {
            vao = GlStateManager._glGenVertexArrays();
        }
        if (sampler == 0) {
            sampler = GL33.glGenSamplers();
            GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL33.glSamplerParameteri(sampler, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        }
        if (readFbo == 0) readFbo = GlStateManager.glGenFramebuffers();
        if (depthReadFbo == 0) depthReadFbo = GlStateManager.glGenFramebuffers();
        if (depthDrawFbo == 0) depthDrawFbo = GlStateManager.glGenFramebuffers();
        if (layerTarget != null && width == newWidth && height == newHeight) {
            return true;
        }
        RenderSystem.assertOnRenderThread();
        if (opaqueTexture != 0) GlStateManager._deleteTexture(opaqueTexture);
        if (resolvedTexture != 0) GlStateManager._deleteTexture(resolvedTexture);
        if (layerTarget == null) {
            layerTarget = new TextureTarget("Flashback Export Extras translucent layer", newWidth, newHeight,
                    GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
            solidDepthTarget = new TextureTarget("Flashback Export Extras solid depth", newWidth, newHeight,
                    null, GpuFormat.D32_FLOAT);
        } else {
            layerTarget.resize(newWidth, newHeight);
            solidDepthTarget.resize(newWidth, newHeight);
        }
        width = newWidth;
        height = newHeight;
        opaqueTexture = createColorTexture(newWidth, newHeight);
        resolvedTexture = createColorTexture(newWidth, newHeight);
        opaqueFbo = attachColor(opaqueFbo, opaqueTexture);
        resolvedFbo = attachColor(resolvedFbo, resolvedTexture);
        if (glId(layerTarget.getColorTexture()) == 0 || glId(layerTarget.getDepthTexture()) == 0
                || glId(solidDepthTarget.getDepthTexture()) == 0 || opaqueFbo == 0 || resolvedFbo == 0) {
            FlashbackExportExtras.LOGGER.error("Export Transparent: GL targets unavailable; the translucent layer is off");
            broken = true;
            return false;
        }
        return true;
    }

    private static int createColorTexture(int w, int h) {
        int texture = GlStateManager._genTexture();
        int prevActive = GL11.glGetInteger(GL14.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL14.GL_TEXTURE0);
        int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAX_LEVEL, 0);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, null);
        GlStateManager._bindTexture(prevTexture);
        GlStateManager._activeTexture(prevActive);
        return texture;
    }

    private static int attachColor(int fbo, int texture) {
        if (fbo == 0) fbo = GlStateManager.glGenFramebuffers();
        int prevDraw = GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texture, 0);
        GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        return status == GL30.GL_FRAMEBUFFER_COMPLETE ? fbo : 0;
    }

    private boolean ensureResolveProgram() {
        if (resolveProgram != 0) {
            return true;
        }
        int vs = compile(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, RESOLVE_FRAGMENT_SHADER);
        if (vs == 0 || fs == 0) {
            if (vs != 0) GlStateManager.glDeleteShader(vs);
            if (fs != 0) GlStateManager.glDeleteShader(fs);
            broken = true;
            return false;
        }
        int p = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(p, vs);
        GlStateManager.glAttachShader(p, fs);
        GlStateManager.glLinkProgram(p);
        GlStateManager.glDeleteShader(vs);
        GlStateManager.glDeleteShader(fs);
        if (GlStateManager.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
            FlashbackExportExtras.LOGGER.error("Export Transparent resolve shader link failed: {}",
                    GlStateManager.glGetProgramInfoLog(p, 32768));
            GlStateManager.glDeleteProgram(p);
            broken = true;
            return false;
        }
        resolveProgram = p;
        resolveOpaqueLoc = GlStateManager._glGetUniformLocation(p, "uOpaque");
        resolveLayerLoc = GlStateManager._glGetUniformLocation(p, "uLayer");
        resolveFinalLoc = GlStateManager._glGetUniformLocation(p, "uFinal");
        resolveThresholdLoc = GlStateManager._glGetUniformLocation(p, "uThreshold");
        return true;
    }

    private static int compile(int type, String source) {
        int shader = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(shader, source);
        GlStateManager.glCompileShader(shader);
        if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            FlashbackExportExtras.LOGGER.error("Export Transparent shader compile failed: {}",
                    GlStateManager.glGetShaderInfoLog(shader, 32768));
            GlStateManager.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static int glId(Object texture) {
        return texture instanceof GlTexture glTexture ? Math.max(0, glTexture.glId()) : 0;
    }

    /^* GL state a fullscreen pass changes, put back afterwards. ^/
    private static final class State {
        int program;
        int vao;
        int drawFbo;
        int[] viewport = new int[4];
        boolean depthTest;
        boolean scissor;
        boolean cull;
        boolean blend;
        int colorMask;
        int activeTexture;
        int[] textures;
        int[] samplers;

        static State save(int units) {
            State s = new State();
            s.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            s.vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            s.drawFbo = GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, s.viewport);
            s.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            s.scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            s.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            s.blend = GL30.glIsEnabledi(GL11.GL_BLEND, 0);
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                ByteBuffer mask = stack.malloc(4);
                GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK, 0, mask);
                for (int i = 0; i < 4; i++) {
                    s.colorMask |= mask.get(i) != 0 ? 1 << i : 0;
                }
            }
            s.activeTexture = GL11.glGetInteger(GL14.GL_ACTIVE_TEXTURE);
            s.textures = new int[units];
            s.samplers = new int[units];
            for (int i = 0; i < units; i++) {
                GlStateManager._activeTexture(GL14.GL_TEXTURE0 + i);
                s.textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                s.samplers[i] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            }
            return s;
        }

        void restore() {
            for (int i = 0; i < textures.length; i++) {
                GlStateManager._activeTexture(GL14.GL_TEXTURE0 + i);
                GL33.glBindSampler(i, samplers[i]);
                GlStateManager._bindTexture(textures[i]);
            }
            GlStateManager._activeTexture(activeTexture);
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glUseProgram(program);
            GlStateManager._colorMask(0, colorMask);
            if (blend) GlStateManager._enableBlend(0);
            if (cull) GlStateManager._enableCull();
            if (scissor) GlStateManager._enableScissorTest();
            if (depthTest) GlStateManager._enableDepthTest();
            else GlStateManager._disableDepthTest();
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        }
    }
}
*///?}
