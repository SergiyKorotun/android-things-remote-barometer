package com.samgol.driver.bmp180;

import android.support.annotation.IntDef;
import android.util.Log;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;


public class Bmp180 implements AutoCloseable {
    private static final String TAG = Bmp180.class.getSimpleName();


    public final static int BMP180_ADDRESS = 0x77;

    @Retention(SOURCE)
    @IntDef({BMP180_ULTRA_LOW_POWER, BMP180_STANDARD, BMP180_HIGH_RES, BMP180_ULTRA_HIGH_RES})
    public @interface Mode {
    }

    private static final double POW_FACT = 0.1903;

    public final static int BMP180_ULTRA_LOW_POWER = 0;
    public final static int BMP180_STANDARD = 1;
    public final static int BMP180_HIGH_RES = 2;
    public final static int BMP180_ULTRA_HIGH_RES = 3;


    private static final int modeDelay[] = {5, 8, 14, 26};

    static final float MAX_FREQ_HZ = 181f;
    static final float MIN_FREQ_HZ = 23.1f;

    static final float MAX_POWER_CONSUMPTION_TEMP_UA = 325f;
    static final float MAX_POWER_CONSUMPTION_PRESSURE_UA = 720f;

    public static final float MIN_TEMP_C = -40f;
    static final float MAX_TEMP_C = 85f;

    static final float MAX_PRESSURE_HPA = 1100f;

    private final static int BMP180_CAL_AC1 = 0xAA;
    private final static int BMP180_CAL_AC2 = 0xAC;
    private final static int BMP180_CAL_AC3 = 0xAE;
    private final static int BMP180_CAL_AC4 = 0xB0;
    private final static int BMP180_CAL_AC5 = 0xB2;
    private final static int BMP180_CAL_AC6 = 0xB4;
    private final static int BMP180_CAL_B1 = 0xB6;
    private final static int BMP180_CAL_B2 = 0xB8;
    private final static int BMP180_CAL_MC = 0xBC;
    private final static int BMP180_CAL_MD = 0xBE;

    private final static int BMP180_CONTROL = 0xF4;
    private final static int BMP180_TEMPERATURE_DATA = 0xF6;
    private final static int BMP180_PRESSURE_DATA = 0xF6;
    private final static int BMP180_READ_TEMPERATURE_CMD = 0x2E;
    private final static int BMP180_READ_PRESSURE_CMD = 0x34;

    private int AC1 = 0;
    private int AC2 = 0;
    private int AC3 = 0;
    private int AC4 = 0;
    private int AC5 = 0;
    private int AC6 = 0;
    private int B1 = 0;
    private int B2 = 0;
    private int MC = 0;
    private int MD = 0;


    private I2cDevice mDevice;

    private int mode = BMP180_ULTRA_HIGH_RES;

    private LastRead lastRawTemp = new LastRead();
    private LastRead lastRawPressure = new LastRead();
    private LastRead lastPressure = new LastRead();
    private LastRead lastTemperature = new LastRead();


    private float standardSeaLevelPressure = 101500;

    public Bmp180(String i2cName) {
        try {
            mDevice = new PeripheralManagerService().openI2cDevice(i2cName, BMP180_ADDRESS);
            try {
                readCalibrationData();
            } catch (Exception e) {
                Log.e(TAG, "Bmp180 Error: ", e);
            }
        } catch (IOException e) {
            Log.e(TAG, "Bmp180 Error: ", e);
        }
    }

    public Bmp180(I2cDevice i2cDevice) {
        mDevice = i2cDevice;
        try {
            readCalibrationData();
        } catch (Exception e) {
            Log.e(TAG, "Bmp180 Error: ", e);
        }
    }


    public synchronized void setMode(@Mode int mode) {
        this.mode = mode;
    }

    private int readU16(int register) throws IOException {
        return I2cUtils.readU16BE(mDevice, register);
    }

    private int readS16(int register) throws IOException {
        return I2cUtils.readS16BE(mDevice, register);
    }

    private synchronized void readCalibrationData() throws Exception {
        // Reads the calibration data from the IC
        AC1 = readS16(BMP180_CAL_AC1);
        AC2 = readS16(BMP180_CAL_AC2);
        AC3 = readS16(BMP180_CAL_AC3);
        AC4 = readU16(BMP180_CAL_AC4);
        AC5 = readU16(BMP180_CAL_AC5);
        AC6 = readU16(BMP180_CAL_AC6);
        B1 = readS16(BMP180_CAL_B1);
        B2 = readS16(BMP180_CAL_B2);
        MC = readS16(BMP180_CAL_MC);
        MD = readS16(BMP180_CAL_MD);

    }

    /**
     * Reads the raw (uncompensated) temperature from the sensor
     *
     * @return Reads the raw (uncompensated) temperature from the sensor
     * @throws IOException if there was communication problem
     */
    private int readRawTemp() throws IOException {

        if (lastRawTemp.isValid())
            return lastRawTemp.val;

        mDevice.writeRegByte(BMP180_CONTROL, (byte) BMP180_READ_TEMPERATURE_CMD);
        waitFor(5);
        int raw = readU16(BMP180_TEMPERATURE_DATA);

        lastRawTemp.setVal(raw);
        return raw;
    }

    /**
     * Reads the raw (uncompensated) pressure level from the sensor
     *
     * @return the raw (uncompensated) pressure level from the sensor
     * @throws IOException if there was communication problem
     */
    private int readRawPressure() throws IOException {

        if (lastRawPressure.isValid())
            return lastRawPressure.val;

        mDevice.writeRegByte(BMP180_CONTROL, (byte) (BMP180_READ_PRESSURE_CMD + (mode << 6)));
        waitFor(modeDelay[mode]);
        int msb = mDevice.readRegByte(BMP180_PRESSURE_DATA);
        int lsb = mDevice.readRegByte(BMP180_PRESSURE_DATA + 1);
        int xlsb = mDevice.readRegByte(BMP180_PRESSURE_DATA + 2);
        int raw = ((msb << 16) + (lsb << 8) + xlsb) >> (8 - mode);

        lastRawPressure.setVal(raw);
        return raw;
    }

    /**
     * Returns the temperature in degrees Celsius.
     *
     * @return the temperature in degrees Celsius.
     * @throws IOException if there was communication problem
     */
    public synchronized float readTemperature() throws IOException {
        if (lastTemperature.isValid())
            return lastTemperature.val / 10.0F;

        int UT = readRawTemp();
        int X1 = ((UT - AC6) * AC5) >> 15;
        int X2 = (MC << 11) / (X1 + MD);
        int B5 = X1 + X2;
        lastTemperature.setVal(((B5 + 8) >> 4));

        return lastTemperature.val / 10.0F;
    }

    /**
     * Returns the pressure in Pascal.
     *
     * @return the pressure in Pascal.
     * @throws IOException if there was communication problem
     */
    public synchronized int readPressure() throws IOException {
        if (lastPressure.isValid())
            return lastPressure.val;
        long p;
        int UT = readRawTemp();
        int UP = readRawPressure();

        //Temperature Calculations
        int X1 = ((UT - AC6) * AC5) >> 15;
        int X2 = (MC << 11) / (X1 + MD);
        int B5 = X1 + X2;

        lastTemperature.setVal((B5 + 8) >> 4);

        //Pressure Calculations
        int B6 = B5 - 4000;
        X1 = (B2 * (B6 * B6) >> 12) >> 11;
        X2 = (AC2 * B6) >> 11;
        int X3 = X1 + X2;
        int B3 = (((AC1 * 4 + X3) << mode) + 2) / 4;

        X1 = (AC3 * B6) >> 13;
        X2 = (B1 * ((B6 * B6) >> 12)) >> 16;
        X3 = ((X1 + X2) + 2) >> 2;
        long B4 = (AC4 * (X3 + 32768)) >> 15;
        long B7 = (UP - B3) * (50000 >> mode);

        if (B7 < 0x80000000) {
            p = (B7 * 2) / B4;
        } else {
            p = (B7 / B4) * 2;
        }

        X1 = (int) ((p >> 8) * (p >> 8));
        X1 = (X1 * 3038) >> 16;
        X2 = (int) (-7357 * p) >> 16;
        p = p + ((X1 + X2 + 3791) >> 4);

        lastPressure.setVal((int) p);
        return lastPressure.val;
    }

    /**
     * Returns the barometric altitude above sea level in meters.
     *
     * @return the barometric altitude above sea level in meters.
     * @throws IOException if there was communication problem
     */
    public synchronized float readAltitude() throws IOException {
        float pressure = readPressure();
        return (float) (44330.0 * (1.0 - Math.pow(pressure / standardSeaLevelPressure, POW_FACT)));
    }

    /**
     * Returns the array with: the pressure in Pascal,the temperature in degrees Celsius,the barometric altitude above sea level in meters
     *
     * @return the array with: the pressure in Pascal,the temperature in degrees Celsius,the barometric altitude above sea level in meters
     * @throws IOException if there was communication problem
     */
    public synchronized float[] readAllValues() throws IOException {
        int pressure = readPressure();
        float temperature = readTemperature();
        float altitude = readAltitude();
        return new float[]{pressure, temperature, altitude};
    }

    /**
     * Set the standard sea level pressure for altitude calculation
     *
     * @param standardSeaLevelPressure the standard sea level pressure for your location
     */
    public synchronized void setStandardSeaLevelPressure(float standardSeaLevelPressure) {
        this.standardSeaLevelPressure = standardSeaLevelPressure;
    }

    private static void waitFor(long howMuch) {
        try {
            MILLISECONDS.sleep(howMuch);
        } catch (InterruptedException e) {
            Log.e(TAG, "waitFor error: ", e);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (mDevice != null) {
            try {
                mDevice.close();
            } finally {
                mDevice = null;
            }
        }
    }
}

class LastRead {
    private static final int MIN_PERIOD_US = 50;
    long time;
    int val;

    boolean isValid() {
        return System.currentTimeMillis() - time < MIN_PERIOD_US;
    }

    void setVal(int val) {
        this.val = val;
        time = System.currentTimeMillis();
    }

}