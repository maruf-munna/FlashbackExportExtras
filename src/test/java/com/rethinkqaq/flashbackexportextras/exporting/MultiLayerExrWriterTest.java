package com.rethinkqaq.flashbackexportextras.exporting;

import com.rethinkqaq.flashbackexportextras.FlashbackExportExtrasConfig.ExrCompression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MultiLayerExrWriterTest {
    @TempDir Path output;

    /** Exercise the real native writer, including the UI-on/Trans-off combination that crashed the client. */
    @Test void nativeWriterSupportsEveryLayerCombination() throws Exception {
        ByteBuffer color = MemoryUtil.memCalloc(2 * 2 * 8).order(ByteOrder.LITTLE_ENDIAN);
        FloatBuffer depth = MemoryUtil.memCallocFloat(4);
        ByteBuffer ui = MemoryUtil.memCalloc(16);
        try {
            for (int pixel = 0; pixel < 4; pixel++) {
                for (int c = 0; c < 4; c++) color.putShort(pixel * 8 + c * 2, (short) 0x3c00);
                depth.put(pixel, 0.5f);
            }
            for (boolean trans : new boolean[]{false, true}) {
                for (boolean hud : new boolean[]{false, true}) {
                    for (boolean includeDepth : new boolean[]{false, true}) {
                        Path dir = output.resolve("t" + trans + "u" + hud + "z" + includeDepth);
                        try (var writer = new MultiLayerExrWriter(dir, 2, 2, true, false,
                                ExrCompression.ZIP, trans, hud, 16, includeDepth)) {
                            writer.writeHdrFrame(color, new DepthCaptureState.DepthFrame(0, depth),
                                    new TranslucentCaptureState.HudFrame(0, ui), null, 0);
                        }
                        List<String> names = channels(Files.readAllBytes(dir.resolve("0000.exr")));
                        assertEquals(4 + (includeDepth ? 1 : 0) + (trans ? 4 : 0)
                                + (trans && includeDepth ? 1 : 0) + (hud ? 4 : 0), names.size());
                        assertEquals(includeDepth, names.contains("View Layer.Depth.Z"));
                        assertEquals(trans && includeDepth, names.contains("View Layer.TransDepth.Z"));
                        assertEquals(hud, names.contains("View Layer.UI.A"));
                        assertEquals(trans, names.contains("View Layer.Trans.A"));
                    }
                }
            }
        } finally {
            MemoryUtil.memFree(color); MemoryUtil.memFree(depth); MemoryUtil.memFree(ui);
        }
    }

    @Test void geometryPassesCanBeExportedWithoutTheDepthChannel() throws Exception {
        ByteBuffer color = MemoryUtil.memCalloc(3 * 3 * 8);
        FloatBuffer depth = MemoryUtil.memAllocFloat(9);
        try {
            for (int i = 0; i < 9; i++) depth.put(i, 8);
            var frame = new DepthCaptureState.DepthFrame(0, depth, 0.05f, 256,
                    DepthCaptureState.Encoding.LINEAR_WORLD_METERS, "fixture");
            var camera = new TranslucentCaptureState.CameraInfo(0, 0, 0, 0, 0, 60);
            try (var passes = new ExrGeometryPasses(3, 3, 16, true)) {
                passes.fill(frame, camera);
                assertEquals(0.5f, passes.mist.get(4), 0.001);
                assertTrue(passes.mist.get(0) > passes.mist.get(4));
                assertEquals(0, passes.nx.get(4), 0.001);
                assertEquals(1, passes.ny.get(4), 0.001);
                assertEquals(0, passes.nz.get(4), 0.001);
                depth.put(4, 256);
                passes.fill(frame, camera);
                assertEquals(1, passes.mist.get(4));
                assertEquals(0, passes.ny.get(4));
            }
            try (var writer = new MultiLayerExrWriter(output, 3, 3, true, false,
                    ExrCompression.ZIP, false, false, 23, false, 16, true)) {
                writer.writeHdrFrame(color, frame, null, camera, 0);
            }
            List<String> names = channels(Files.readAllBytes(output.resolve("0000.exr")));
            assertTrue(names.containsAll(List.of("View Layer.Mist.Z", "View Layer.Normal.X",
                    "View Layer.Normal.Y", "View Layer.Normal.Z")));
            assertFalse(names.contains("View Layer.Depth.Z"));
        } finally {
            MemoryUtil.memFree(color);
            MemoryUtil.memFree(depth);
        }
    }

    @Test void nativeWavPreservesSamplesAndRoundsToTheSequenceDuration() throws Exception {
        Path target = output.resolve("audio.wav");
        try (var wave = new ExrWaveWriter(target, 2)) {
            wave.writeFrame(FloatBuffer.wrap(new float[]{0.5f, -0.5f}), 2);
            assertFalse(Files.exists(target));
            wave.publish(2);
        }
        ByteBuffer wav = ByteBuffer.wrap(Files.readAllBytes(target)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(48000, wav.getInt(60));
        assertEquals(12, wav.getInt(76));
        assertEquals(0x40, wav.get(82));
        assertEquals((byte) 0xc0, wav.get(85));
        assertEquals(0, wav.getInt(86));
        Path cancelled = output.resolve("cancelled.wav");
        try (var wave = new ExrWaveWriter(cancelled, 1)) {
            wave.writeFrame(FloatBuffer.wrap(new float[]{0}), 1);
        }
        assertFalse(Files.exists(cancelled));
        try (var files = Files.list(output)) {
            assertTrue(files.noneMatch(path -> path.toString().endsWith(".part")));
        }
    }

    private static String cstring(ByteBuffer data) {
        StringBuilder text = new StringBuilder();
        byte next;
        while ((next = data.get()) != 0) text.append((char) next);
        return text.toString();
    }

    private static List<String> channels(byte[] file) {
        ByteBuffer data = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(20000630, data.getInt());
        data.getInt();
        List<String> names = new ArrayList<>();
        while (data.get(data.position()) != 0) {
            String name = cstring(data);
            String type = cstring(data);
            int length = data.getInt();
            int end = data.position() + length;
            if (name.equals("channels")) {
                assertEquals("chlist", type);
                while (data.get(data.position()) != 0) {
                    names.add(cstring(data));
                    data.position(data.position() + 16);
                }
            }
            data.position(end);
        }
        assertFalse(names.isEmpty(), "No EXR channel header");
        data.get();
        int chunkOffset = Math.toIntExact(data.getLong());
        assertEquals(data.position(), chunkOffset, "Single scanline chunk must follow its offset table");
        assertEquals(0, data.getInt(), "First scanline index");
        assertTrue(data.getInt() > 0, "Scanline chunk has pixels");
        return names;
    }
}
