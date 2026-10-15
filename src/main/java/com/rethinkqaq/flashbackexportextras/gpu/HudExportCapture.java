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
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlTexture;
import com.rethinkqaq.flashbackexportextras.FlashbackExportExtras;
import com.rethinkqaq.flashbackexportextras.exporting.DepthCaptureState;
import com.rethinkqaq.flashbackexportextras.exporting.TranslucentCaptureState;
import com.rethinkqaq.flashbackexportextras.mixins.GuiRendererHudAccessor;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/^*
 * Export Transparent, HUD layer (26.3): the HUD the export draws is replayed once more into a cleared transparent
 * target (alpha blended as premultiplied coverage, see MixinGlStateManagerHudBlend) and read back as the EXR's UI layer,
 * like a FlashPlus live take's HUD. Combined is taken before the HUD is drawn, so this is where the hotbar goes.
 ^/
public final class HudExportCapture {
    public static final HudExportCapture INSTANCE = new HudExportCapture();

    /^* True while the HUD is replayed into the capture target (the blend fix applies only then). Render thread. ^/
    public static boolean passActive = false;

    private static final int SLOTS = 3;
    private static final long WAIT_NANOS = 1_000_000_000L;
    private static final Vector4f TRANSPARENT = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);

    private TextureTarget target;
    private final int[] pbos = new int[SLOTS];
    private final long[] fences = new long[SLOTS];
    private final long[] slotFrames = new long[SLOTS];
    private int pboWidth = -1;
    private int pboHeight = -1;
    private int writeSlot;
    // last export frame whose HUD was captured / handed to the writer
    private long lastCaptured = -1L;
    private long lastFinished = -1L;

    private HudExportCapture() {
    }

    /^* GuiRenderer.draw has drawn this frame's HUD: replay it into the capture target and read it back. ^/
    public void capture(GuiRendererHudAccessor gui, int drawCount) {
        if (!TranslucentCaptureState.hudActive || drawCount <= 0) {
            return;
        }
        long frameId = DepthCaptureState.submittedCaptureFrameId();
        if (frameId < 0L || frameId <= lastCaptured) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            return;
        }
        try {
            ensureTarget(main.width, main.height);
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), TRANSPARENT);
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                    .writeTransform(new Matrix4f().setTranslation(0.0F, 0.0F, -11000.0F));
            passActive = true;
            try {
                gui.flashbackexportextras$executeDrawRange(() -> "Flashback Export Extras HUD layer", target, transforms,
                        0, drawCount);
            } finally {
                passActive = false;
            }
            issueReadback(frameId);
            lastCaptured = frameId;
        } catch (RuntimeException e) {
            TranslucentCaptureState.failHud(e);
            FlashbackExportExtras.LOGGER.error("Export Transparent: HUD layer capture failed for frame " + frameId, e);
        }
    }

    /^* The frame is done (before its colour download): a frame without a HUD gets an empty layer. ^/
    public void finishFrame(long frameId) {
        if (!TranslucentCaptureState.hudActive || frameId < 0L || frameId <= lastFinished) {
            return;
        }
        lastFinished = frameId;
        collect(0L);
        if (frameId > lastCaptured) {
            TranslucentCaptureState.submitHud(new TranslucentCaptureState.HudFrame(frameId, null));
        }
    }

    /^* Waits for every readback in flight (before the EXR writer finishes). ^/
    public void flush() {
        if (!collect(WAIT_NANOS)) {
            TranslucentCaptureState.failHud(new IllegalStateException("Timed out flushing the HUD layer"));
        }
    }

    public void reset() {
        collect(WAIT_NANOS);
        for (int i = 0; i < SLOTS; i++) {
            if (fences[i] != 0L) {
                GL32.glDeleteSync(fences[i]);
                fences[i] = 0L;
            }
            if (pbos[i] != 0) {
                GL15.glDeleteBuffers(pbos[i]);
                pbos[i] = 0;
            }
        }
        pboWidth = pboHeight = -1;
        writeSlot = 0;
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        lastCaptured = -1L;
        lastFinished = -1L;
        passActive = false;
    }

    private void ensureTarget(int width, int height) {
        if (target == null) {
            target = new TextureTarget("Flashback Export Extras HUD layer", width, height, GpuFormat.RGBA8_UNORM, null);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
    }

    private void issueReadback(long frameId) {
        int width = target.width;
        int height = target.height;
        if (pboWidth != width || pboHeight != height || pbos[0] == 0) {
            if (!collect(WAIT_NANOS)) {
                throw new IllegalStateException("HUD readback still running while the frame size changed");
            }
            int oldPbo = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            for (int i = 0; i < SLOTS; i++) {
                if (pbos[i] != 0) GL15.glDeleteBuffers(pbos[i]);
                pbos[i] = GL15.glGenBuffers();
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbos[i]);
                GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, (long) width * height * 4, GL15.GL_STREAM_READ);
            }
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, oldPbo);
            pboWidth = width;
            pboHeight = height;
            writeSlot = 0;
        }
        int slot = writeSlot;
        if (fences[slot] != 0L && !collectSlot(slot, WAIT_NANOS)) {
            throw new IllegalStateException("Timed out waiting for HUD readback slot " + slot);
        }
        int texture = target.getColorTexture() instanceof GlTexture gl ? gl.glId() : 0;
        if (texture <= 0) {
            throw new IllegalStateException("HUD layer texture is not a GL texture");
        }
        int oldPbo = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbos[slot]);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, oldPbo);
        }
        slotFrames[slot] = frameId;
        fences[slot] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        writeSlot = (slot + 1) % SLOTS;
    }

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
        int result = GL32.glClientWaitSync(fences[slot], timeoutNanos > 0L ? GL32.GL_SYNC_FLUSH_COMMANDS_BIT : 0,
                timeoutNanos);
        if (result == GL32.GL_WAIT_FAILED) {
            throw new IllegalStateException("GPU fence wait failed for the HUD layer");
        }
        if (result != GL32.GL_ALREADY_SIGNALED && result != GL32.GL_CONDITION_SATISFIED) {
            return false;
        }
        GL32.glDeleteSync(fences[slot]);
        fences[slot] = 0L;
        long bytes = (long) pboWidth * pboHeight * 4;
        int oldPbo = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbos[slot]);
        try {
            ByteBuffer mapped = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY, bytes, null);
            if (mapped == null) {
                throw new IllegalStateException("Could not map the HUD layer readback");
            }
            ByteBuffer copy = MemoryUtil.memAlloc((int) bytes);
            MemoryUtil.memCopy(MemoryUtil.memAddress(mapped), MemoryUtil.memAddress(copy), bytes);
            GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            TranslucentCaptureState.submitHud(new TranslucentCaptureState.HudFrame(slotFrames[slot], copy));
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, oldPbo);
        }
        return true;
    }
}
*///?}
