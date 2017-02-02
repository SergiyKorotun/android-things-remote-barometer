package com.samgol.driver.bmp180;

import android.hardware.Sensor;
import android.util.Log;

import com.google.android.things.userdriver.UserDriverManager;
import com.google.android.things.userdriver.UserSensor;
import com.google.android.things.userdriver.UserSensorDriver;
import com.google.android.things.userdriver.UserSensorReading;

import java.io.IOException;
import java.util.UUID;


public class Bmp180SensorDriver implements AutoCloseable {
    private static final String TAG = "Bmp180SensorDriver";
    public static final String BAROMETER_SENSOR = "com.samgol.barometer";
    // DRIVER parameters
    // documented at https://source.android.com/devices/sensors/hal-interface.html#sensor_t
    private static final String DRIVER_VENDOR = "Bosch";
    private static final String DRIVER_NAME = "BMP180";
    private static final int DRIVER_MIN_DELAY_US = Math.round(1000000.f / Bmp180.MAX_FREQ_HZ);
    private static final int DRIVER_MAX_DELAY_US = Math.round(1000000.f / Bmp180.MIN_FREQ_HZ);

    private Bmp180 mDevice;

    private TemperatureUserDriver mTemperatureUserDriver;
    private PressureUserDriver mPressureUserDriver;
    private BarometerUserDriver mBarometerUserDriver;

    /**
     * Create a new framework sensor driver connected on the given bus.
     * The driver emits {@link Sensor} with pressure and temperature data when
     * registered.
     *
     * @param bus I2C bus the sensor is connected to.
     * @throws IOException
     * @see #registerPressureSensor()
     * @see #registerTemperatureSensor()
     */
    public Bmp180SensorDriver(String bus) throws IOException {
        mDevice = new Bmp180(bus);
    }




    /**
     * Close the driver and the underlying device.
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        unregisterTemperatureSensor();
        unregisterPressureSensor();
        unregisterBarometerSensor();
        if (mDevice != null) {
            try {
                mDevice.close();
            } finally {
                mDevice = null;
            }
        }
    }

    /**
     * Register a {@link UserSensor} that pipes temperature readings into the Android SensorManager.
     *
     * @see #unregisterBarometerSensor() ()
     */
    public void registerBarometerSensor() {
        if (mDevice == null) {
            throw new IllegalStateException("cannot register closed driver");
        }

        if (mBarometerUserDriver == null) {
            mBarometerUserDriver = new BarometerUserDriver();
            UserDriverManager.getManager().registerSensor(mBarometerUserDriver.getUserSensor());
        }
    }

    /**
     * Register a {@link UserSensor} that pipes temperature readings into the Android SensorManager.
     *
     * @see #unregisterTemperatureSensor()
     */
    public void registerTemperatureSensor() {
        if (mDevice == null) {
            throw new IllegalStateException("cannot register closed driver");
        }

        if (mTemperatureUserDriver == null) {
            mTemperatureUserDriver = new TemperatureUserDriver();
            UserDriverManager.getManager().registerSensor(mTemperatureUserDriver.getUserSensor());
        }
    }

    /**
     * Register a {@link UserSensor} that pipes pressure readings into the Android SensorManager.
     *
     * @see #unregisterPressureSensor()
     */
    public void registerPressureSensor() {
        if (mDevice == null) {
            throw new IllegalStateException("cannot register closed driver");
        }

        if (mPressureUserDriver == null) {
            mPressureUserDriver = new PressureUserDriver();
            UserDriverManager.getManager().registerSensor(mPressureUserDriver.getUserSensor());
        }
    }

    /**
     * Unregister the temperature {@link UserSensor}.
     */
    public void unregisterTemperatureSensor() {
        if (mTemperatureUserDriver != null) {
            UserDriverManager.getManager().unregisterSensor(mTemperatureUserDriver.getUserSensor());
            mTemperatureUserDriver = null;
        }
    }

    /**
     * Unregister the pressure {@link UserSensor}.
     */
    public void unregisterPressureSensor() {
        if (mPressureUserDriver != null) {
            UserDriverManager.getManager().unregisterSensor(mPressureUserDriver.getUserSensor());
            mPressureUserDriver = null;
        }
    }

    /**
     * Unregister the barometer {@link UserSensor}.
     */
    public void unregisterBarometerSensor() {
        if (mBarometerUserDriver != null) {
            UserDriverManager.getManager().unregisterSensor(mBarometerUserDriver.getUserSensor());
            mBarometerUserDriver = null;
        }
    }


    private class PressureUserDriver extends UserSensorDriver {
        // DRIVER parameters
        // documented at https://source.android.com/devices/sensors/hal-interface.html#sensor_t
        private static final float DRIVER_MAX_RANGE = Bmp180.MAX_PRESSURE_HPA;
        private static final float DRIVER_RESOLUTION = .0262f;
        private static final float DRIVER_POWER = Bmp180.MAX_POWER_CONSUMPTION_PRESSURE_UA / 1000.f;
        private static final int DRIVER_VERSION = 1;
        private static final String DRIVER_REQUIRED_PERMISSION = "";

        private boolean mEnabled;
        private UserSensor mUserSensor;

        private UserSensor getUserSensor() {
            if (mUserSensor == null) {
                mUserSensor = UserSensor.builder()
                        .setType(Sensor.TYPE_PRESSURE)
                        .setName(DRIVER_NAME)
                        .setVendor(DRIVER_VENDOR)
                        .setVersion(DRIVER_VERSION)
                        .setMaxRange(DRIVER_MAX_RANGE)
                        .setResolution(DRIVER_RESOLUTION)
                        .setPower(DRIVER_POWER)
                        .setMinDelay(500000)
//                        .setMinDelay(DRIVER_MIN_DELAY_US)
                        .setRequiredPermission(DRIVER_REQUIRED_PERMISSION)
//                        .setMaxDelay(DRIVER_MAX_DELAY_US)
                        .setUuid(UUID.randomUUID())
                        .setDriver(this)
                        .build();
            }
            return mUserSensor;
        }

        @Override
        public UserSensorReading read() throws IOException {
            return new UserSensorReading(new float[]{mDevice.readPressure()});
        }

        @Override
        public void setEnabled(boolean enabled) throws IOException {
            Log.d(TAG, "setEnabled() called with: enabled = [" + enabled + "]");
            mEnabled = enabled;
        }

        private boolean isEnabled() {
            return mEnabled;
        }


    }


    private class BarometerUserDriver extends UserSensorDriver {
        private static final float DRIVER_RESOLUTION = 0.005f;
        private static final float DRIVER_POWER = Bmp180.MAX_POWER_CONSUMPTION_TEMP_UA / 1000.f;
        private static final int DRIVER_VERSION = 1;
        private static final String DRIVER_REQUIRED_PERMISSION = "";
        private boolean mEnabled;
        private UserSensor mUserSensor;

        private UserSensor getUserSensor() {
            if (mUserSensor == null) {
                mUserSensor = UserSensor.builder()
                        .setCustomType(Sensor.TYPE_DEVICE_PRIVATE_BASE,
                                BAROMETER_SENSOR,
                                Sensor.REPORTING_MODE_CONTINUOUS)
                        .setName(DRIVER_NAME)
                        .setVendor(DRIVER_VENDOR)
                        .setVersion(DRIVER_VERSION)
                        .setResolution(DRIVER_RESOLUTION)
                        .setMinDelay(500000)
                        .setPower(DRIVER_POWER)
                        .setRequiredPermission(DRIVER_REQUIRED_PERMISSION)
                        .setUuid(UUID.randomUUID())
                        .setDriver(this)
                        .build();
            }
            return mUserSensor;
        }

        @Override
        public UserSensorReading read() throws IOException {
            return new UserSensorReading(mDevice.readAllValues());
        }

        @Override
        public void setEnabled(boolean enabled) throws IOException {
            Log.d(TAG, "setEnabled() called with: enabled = [" + enabled + "]");
            mEnabled = enabled;
        }

        private boolean isEnabled() {
            return mEnabled;
        }

    }

    private class TemperatureUserDriver extends UserSensorDriver {
        // DRIVER parameters
        // documented at https://source.android.com/devices/sensors/hal-interface.html#sensor_t
        private static final float DRIVER_MAX_RANGE = Bmp180.MAX_TEMP_C;
        private static final float DRIVER_RESOLUTION = 0.005f;
        private static final float DRIVER_POWER = Bmp180.MAX_POWER_CONSUMPTION_TEMP_UA / 1000.f;
        private static final int DRIVER_VERSION = 1;
        private static final String DRIVER_REQUIRED_PERMISSION = "";

        private boolean mEnabled;
        private UserSensor mUserSensor;

        private UserSensor getUserSensor() {
            if (mUserSensor == null) {
                mUserSensor = UserSensor.builder()
                        .setType(Sensor.TYPE_AMBIENT_TEMPERATURE)
                        .setName(DRIVER_NAME)
                        .setVendor(DRIVER_VENDOR)
                        .setVersion(DRIVER_VERSION)
                        .setMaxRange(DRIVER_MAX_RANGE)
                        .setResolution(DRIVER_RESOLUTION)
                        .setPower(DRIVER_POWER)
                        .setMinDelay(500000)
//                        .setMinDelay(DRIVER_MIN_DELAY_US)
                        .setRequiredPermission(DRIVER_REQUIRED_PERMISSION)
//                        .setMaxDelay(DRIVER_MAX_DELAY_US)
                        .setUuid(UUID.randomUUID())
                        .setDriver(this)
                        .build();
            }
            return mUserSensor;
        }

        @Override
        public UserSensorReading read() throws IOException {
            return new UserSensorReading(new float[]{mDevice.readTemperature()});
        }

        @Override
        public void setEnabled(boolean enabled) throws IOException {
            Log.d(TAG, "setEnabled() called with: enabled = [" + enabled + "]");
            mEnabled = enabled;
        }

        private boolean isEnabled() {
            return mEnabled;
        }
    }

}