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
        while (data.get(data.position()) != 0) {
            String name = cstring(data);
            String type = cstring(data);
            int length = data.getInt();
            int end = data.position() + length;
            if (name.equals("channels")) {
                assertEquals("chlist", type);
                List<String> names = new ArrayList<>();
                while (data.get(data.position()) != 0) {
                    names.add(cstring(data));
                    data.position(data.position() + 16);
                }
                return names;
            }
            data.position(end);
        }
        fail("No EXR channel header");
        return List.of();
    }
}
