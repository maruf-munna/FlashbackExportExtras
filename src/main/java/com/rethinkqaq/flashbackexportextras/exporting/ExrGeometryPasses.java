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
package com.rethinkqaq.flashbackexportextras.exporting;

import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryUtil;

/** Visible-surface data reconstructed from the same depth attachment used by the EXR. */
final class ExrGeometryPasses implements AutoCloseable {
    final FloatBuffer mist, nx, ny, nz;
    final int mistDistance;
    private final int width, height;
    private final float[] depth;
    private final float[] rayX, rayY;

    ExrGeometryPasses(int width, int height, int mistDistance, boolean normal) {
        this.width = width;
        this.height = height;
        this.mistDistance = mistDistance;
        int size = width * height;
        mist = mistDistance > 0 ? MemoryUtil.memAllocFloat(size) : null;
        nx = normal ? MemoryUtil.memAllocFloat(size) : null;
        ny = normal ? MemoryUtil.memAllocFloat(size) : null;
        nz = normal ? MemoryUtil.memAllocFloat(size) : null;
        depth = normal || mist != null ? new float[size] : null;
        rayX = depth == null ? null : new float[width];
        rayY = depth == null ? null : new float[height];
    }

    void fill(DepthCaptureState.DepthFrame frame, TranslucentCaptureState.CameraInfo camera) {
        if (depth == null) return;
        if (frame == null || frame.data == null || camera == null || camera.fov() <= 0 || camera.fov() >= 180) {
            throw new IllegalArgumentException("Geometry passes need valid depth and a perspective camera");
        }
        float slope = (float) Math.tan(Math.toRadians(camera.fov() * 0.5));
        for (int x = 0; x < width; x++) rayX[x] = (2 * x + 1 - width) * slope / height;
        for (int y = 0; y < height; y++) rayY[y] = (height - 2 * y - 1) * slope / height;
        float near = frame.zNear, far = frame.zFar;
        for (int y = 0; y < height; y++) {
            int source = (height - 1 - y) * width;
            for (int x = 0; x < width; x++) {
                float z = frame.data.get(source + x);
                z = switch (frame.encoding) {
                    case LINEAR_WORLD_METERS -> z;
                    case REVERSED_NDC -> near * far / (near + z * (far - near));
                    case STANDARD_NDC -> near * far / (far - z * (far - near));
                };
                int index = y * width + x;
                boolean surface = Float.isFinite(z) && z > near * 1.01f && z < far * 0.999f;
                depth[index] = surface ? z : 0;
                if (mist != null) {
                    double rayLength = Math.sqrt(1 + rayX[x] * rayX[x] + rayY[y] * rayY[y]);
                    mist.put(index, surface ? (float) Math.min(1, z * rayLength / mistDistance)
                            : z > 0 && z <= near * 1.01f ? 0 : 1);
                }
            }
        }
        if (nx != null) fillNormals(camera);
    }

    private void fillNormals(TranslucentCaptureState.CameraInfo camera) {
        double yaw = Math.toRadians(camera.yaw()), pitch = Math.toRadians(camera.pitch());
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                float z = depth[index];
                int dx = closestNeighbor(z, x > 0 ? depth[index - 1] : 0,
                        x + 1 < width ? depth[index + 1] : 0);
                int dy = closestNeighbor(z, y > 0 ? depth[index - width] : 0,
                        y + 1 < height ? depth[index + width] : 0);
                if (z == 0 || dx == 0 || dy == 0) {
                    nx.put(index, 0);
                    ny.put(index, 0);
                    nz.put(index, 0);
                    continue;
                }
                float zx = depth[index + dx], zy = depth[index + dy * width];
                float ax = (rayX[x + dx] * zx - rayX[x] * z) * dx;
                float ay = rayY[y] * (zx - z) * dx;
                float az = (z - zx) * dx;
                float bx = rayX[x] * (zy - z) * -dy;
                float by = (rayY[y + dy] * zy - rayY[y] * z) * -dy;
                float bz = (z - zy) * -dy;
                float cx = ay * bz - az * by, up = az * bx - ax * bz, cz = ax * by - ay * bx;
                float inverse = (float) (1 / Math.sqrt(cx * cx + up * up + cz * cz));
                // Camera right/up/back -> Minecraft world -> Blender (X, -Z, Y).
                float forward = up * sp - cz * cp;
                nx.put(index, (-cy * cx - sy * forward) * inverse);
                ny.put(index, (sy * cx - cy * forward) * inverse);
                nz.put(index, (up * cp + cz * sp) * inverse);
            }
        }
    }

    private static int closestNeighbor(float base, float previous, float next) {
        float a = previous == 0 ? Float.POSITIVE_INFINITY : Math.abs(previous - base);
        float b = next == 0 ? Float.POSITIVE_INFINITY : Math.abs(next - base);
        if (Math.min(a, b) > Math.max(0.05f, base * 0.10f)) return 0;
        return a < b ? -1 : 1;
    }

    @Override
    public void close() {
        MemoryUtil.memFree(mist);
        MemoryUtil.memFree(nx);
        MemoryUtil.memFree(ny);
        MemoryUtil.memFree(nz);
    }
}
