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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Streams Flashback's interleaved OpenAL loopback PCM into a 24-bit WAV. */
final class ExrWaveWriter implements AutoCloseable {
    static final int SAMPLE_RATE = 48000;
    private static final int HEADER_SIZE = 80;
    private final Path target;
    private final Path staging;
    private final RandomAccessFile file;
    private final int channels;
    private byte[] bytes = new byte[0];
    private long samples;
    private boolean fileClosed;

    ExrWaveWriter(Path target, int channels) throws IOException {
        this.target = target;
        this.channels = channels;
        staging = Files.createTempFile(target.getParent(), ".audio-", ".wav.part");
        try {
            file = new RandomAccessFile(staging.toFile(), "rw");
        } catch (IOException e) {
            Files.deleteIfExists(staging);
            throw e;
        }
        file.seek(HEADER_SIZE);
    }

    void writeFrame(FloatBuffer pcm, long endSample) throws IOException {
        // Flashback floors its cumulative mixer clock. Preserve every sample in order;
        // rounding is only needed once at the end of the sequence.
        if (pcm == null || pcm.remaining() % channels != 0) {
            throw new IOException("Missing or incomplete Flashback audio buffer");
        }
        int count = pcm.remaining();
        if (bytes.length < count * 3) bytes = new byte[count * 3];
        int out = 0;
        for (int i = pcm.position(); i < pcm.limit(); i++) {
            float value = pcm.get(i);
            int sample = Math.round(Math.max(-1, Math.min(1, value)) * 8388607);
            bytes[out++] = (byte) sample;
            bytes[out++] = (byte) (sample >> 8);
            bytes[out++] = (byte) (sample >> 16);
        }
        file.write(bytes, 0, out);
        samples += count / channels;
        if (Math.abs(samples - endSample) > 1) {
            throw new IOException("Flashback PCM length diverged from the EXR frame clock");
        }
    }

    void publish(long endSample) throws IOException {
        long dataSize = endSample * channels * 3;
        if (endSample > samples) file.write(new byte[channels * 3]);
        file.setLength(HEADER_SIZE + dataSize);
        if ((dataSize & 1) != 0) file.setLength(HEADER_SIZE + dataSize + 1);
        long riffSize = file.length() - 8;
        boolean rf64 = riffSize > 0xffffffffL;
        file.seek(0);
        file.writeBytes(rf64 ? "RF64" : "RIFF");
        writeInt(rf64 ? -1 : (int) riffSize);
        file.writeBytes("WAVE");
        file.writeBytes(rf64 ? "ds64" : "JUNK");
        writeInt(28);
        if (rf64) {
            file.writeLong(Long.reverseBytes(riffSize));
            file.writeLong(Long.reverseBytes(dataSize));
            file.writeLong(Long.reverseBytes(endSample));
            writeInt(0);
        } else {
            file.write(new byte[28]);
        }
        file.writeBytes("fmt ");
        writeInt(16);
        file.writeShort(Short.reverseBytes((short) 1));
        file.writeShort(Short.reverseBytes((short) channels));
        writeInt(SAMPLE_RATE);
        writeInt(SAMPLE_RATE * channels * 3);
        file.writeShort(Short.reverseBytes((short) (channels * 3)));
        file.writeShort(Short.reverseBytes((short) 24));
        file.writeBytes("data");
        writeInt(rf64 ? -1 : (int) dataSize);
        closeFile();
        Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeInt(int value) throws IOException {
        file.writeInt(Integer.reverseBytes(value));
    }

    private void closeFile() throws IOException {
        if (!fileClosed) {
            fileClosed = true;
            file.close();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            closeFile();
        } finally {
            Files.deleteIfExists(staging);
        }
    }
}
