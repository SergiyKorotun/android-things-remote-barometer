package com.samgol.driver.bmp180;


import android.util.Log;

import com.google.android.things.pio.I2cDevice;

import java.io.IOException;

import static android.content.ContentValues.TAG;


class I2cUtils {

    private static final boolean DEBUG = false;

    static int readU8(I2cDevice device, int reg) throws IOException {
        int result = 0;
        try {
            result = device.readRegByte(reg);
            if (DEBUG)
                Log.d(TAG, "readU8: (0x" + Integer.toHexString(result) +
                        ") from reg (0x" + Integer.toHexString(reg) + ")");
        } catch (Exception e) {
            Log.e(TAG, "readU8: ", e);
        }
        return result;
    }

    static int readS8(I2cDevice device, int reg) throws IOException {
        int result = readU8(device, reg);
        result = result > 127 ? result - 256 : result;

        if (DEBUG)
            Log.d(TAG, "returned  signed val" + result);

        return result;
    }

    static int readU16BE(I2cDevice device, int register) throws IOException {
        int hi = readU8(device, register);
        int lo = readU8(device, register + 1);
        return (hi << 8) + lo;
    }

    static int readS16BE(I2cDevice device, int register) throws IOException {
        int hi = readS8(device, register);
        int lo = readU8(device, register + 1);
        return ((hi << 8) + lo);
    }

//    public static int readU16LE(I2cDevice device, int register) throws IOException {
//        int hi = readU8(device,  register);
//        int lo = readU8(device, register + 1);
//        return (lo << 8) + hi;
//    }


//    public static int readS16LE(I2cDevice device, int register) throws IOException {
//        int lo = readU8(device,  register);
//        int hi = readS8(device, register + 1);
//        return ((hi << 8) + lo);
//    }
}