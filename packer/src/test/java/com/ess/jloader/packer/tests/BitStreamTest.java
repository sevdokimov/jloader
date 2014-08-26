package com.ess.jloader.packer.tests;

import com.ess.jloader.packer.PackUtils;
import com.ess.jloader.utils.BitOutputStream;
import com.ess.jloader.utils.BitInputStream;
import com.ess.jloader.utils.OpenByteOutputStream;
import com.ess.jloader.utils.Utils;
import com.google.common.base.Throwables;
import com.google.common.primitives.Bytes;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;

/**
 * @author Sergey Evdokimov
 */
public class BitStreamTest {

    @Before
    public void setUp() throws Exception {
        if (!BitStreamTest.class.desiredAssertionStatus()) {
            throw new RuntimeException();
        }
    }

    @Test
    public void test1() throws IOException {
        doTest(3,3,  1,7,  5,4, 0,1, 7205,16);
    }

    @Test
    public void test2() throws IOException {
        doTest(1,3, 1,1,  54,8, 77,8, 231,8);
    }

    @Test
    public void testEmptyStream() throws IOException {
        BitInputStream in = new BitInputStream(new byte[0], 0, 0);

        assert in.read() == -1;

        byte[] data = new byte[4];

        assert in.read(data) == -1;
        assert in.read(data, 0, data.length) == -1;

        assert in.readBitsSoft(3) == -1;

        try {
            in.readBits(1);
            assert false;
        } catch (EOFException e) {
            assert true;
        }
    }

    @Test
    public void testReadWriteSort() throws IOException {
        TestBitOutputStream out = new TestBitOutputStream();

        int value = 0x43AA;

        out.writeBits(1, 2);
        out.writeShort(value);

        BitInputStream in = out.toInputStream();
        assert in.readBits(2) == 1;
        assert in.readUnsignedShort() == value;
    }

    @Test
    public void testReadWriteInt() throws IOException {
        TestBitOutputStream out = new TestBitOutputStream();

        int value = 0x43AB0972;

        out.writeBits(1, 2);
        out.writeInt(value);

        BitInputStream in = out.toInputStream();
        assert in.readBits(2) == 1;
        assert in.readInt() == value;
    }

    private void doTest(int ... data) throws IOException {
        assert (data.length & 1) == 0;

        TestBitOutputStream out = new TestBitOutputStream();

        for (int i = 0; i < data.length; i += 2) {
            int value = data[i];
            int bitCount = data[i + 1];
            out.writeBits(value, bitCount);
        }

        BitInputStream in = out.toInputStream();

        for (int i = 0; i < data.length; i += 2) {
            int value = data[i];
            int bitCount = data[i + 1];

            assert in.readBits(bitCount) == value;
        }
    }

    @Test
    public void testWriteSmall_0_3_8_16() throws IOException {
        int[] data = {0, 1, 4, 7, 9, 20, 127, 255, 300};
        TestBitOutputStream out = new TestBitOutputStream();

        for (int x : data) {
            out.writeSmall_0_3_8_16(x);
        }

        BitInputStream in = out.toInputStream();

        for (int x : data) {
            int read = in.readSmall_0_3_8_16();
            assert read == x;
        }
    }

    @Test
    public void testReadClassSize() throws IOException {
        int[] data = {0, 12, 4000, 32000, 50000, 1000000};
        TestBitOutputStream out = new TestBitOutputStream();

        for (int x : data) {
            PackUtils.writeShortInt(out, x);
        }

        BitInputStream in = out.toInputStream();

        for (int x : data) {
            int read = Utils.readShortInt(in);
            assert read == x;
        }
    }

    @Test
    public void testWriteByBytesReadArray() throws IOException {
        TestBitOutputStream out = new TestBitOutputStream();

        out.writeBits(7, 4);

        byte[] data = "1234567890".getBytes();

        out.write(data);

        BitInputStream in = out.toInputStream();

        assert in.readBitsSoft(4) == 7;

        ByteArrayOutputStream res = new ByteArrayOutputStream();

        byte[] buffer = new byte[7];

        int len;
        while ((len = in.read(buffer)) >= 0) {
            res.write(buffer, 0, len);
        }

        assert  Arrays.equals(res.toByteArray(), data) : new String(res.toByteArray());
    }

    @Test
    public void testWriteByBytesReadArray1() throws IOException {
        TestBitOutputStream out = new TestBitOutputStream();

        byte[] data = "123".getBytes();

        out.write(data);

        BitInputStream in = out.toInputStream();

        byte[] buffer = new byte[data.length + 10];

        int len = in.read(buffer);
        assert len == data.length;

        assert Bytes.indexOf(buffer, data) == 0;

        assert in.read() == -1;
    }

    private static class TestBitOutputStream extends BitOutputStream {

        public TestBitOutputStream() {
            super(new OpenByteOutputStream());
        }

        public BitInputStream toInputStream() {
            try {
                finish();
            } catch (IOException e) {
                Throwables.propagate(e);
            }

            OpenByteOutputStream out = (OpenByteOutputStream) this.out;

            return new BitInputStream(out.getBuffer(), 0, out.size());
        }
    }
}
