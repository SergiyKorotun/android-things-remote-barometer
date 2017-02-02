package com.korotun.iot.raspberryiot

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.samgol.driver.bmp180.Bmp180
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import java.io.IOException
import java.util.concurrent.TimeUnit


class IOTActivity : Activity() {

    private val I2C_BUS = "I2C1"
    private val SENSOR_DATA_REFERENCE = "bmp180"
    private val mBmp180: Bmp180 by lazy { Bmp180(I2C_BUS) }
    private val TAG = IOTActivity::class.java.simpleName
    private var disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startSensorPolling()
    }

    private fun startSensorPolling() {
        disposable = getSensorDataAsFlowable()
                .repeatWhen { it.delay(10, TimeUnit.MINUTES) }
                .retryWhen { it.delay(10, TimeUnit.MINUTES) }
                .subscribe({ storeToDB(it) },
                        { Log.e(TAG, "Can't read data from sensor:", it) })
    }

    private fun storeToDB(data: Bmp180Data?) {
        val firebase = FirebaseDatabase.getInstance()
        val reference: DatabaseReference = firebase.getReference(SENSOR_DATA_REFERENCE)
        reference.push().setValue(data)
        Log.d(TAG, "${data.toString()}  saved into firebase")
    }

    private fun getSensorDataAsFlowable(): Flowable<Bmp180Data> {
        return Flowable.fromCallable { getSensorData() }
    }

    private fun getSensorData(): Bmp180Data {
        val temp = mBmp180.readTemperature()
        val press = mBmp180.readPressure()
        val alt = mBmp180.readAltitude()
        return Bmp180Data(temp.toInt(), press, alt.toInt())
    }


    private fun closeSensor() {
        disposable?.dispose()
        try {
            mBmp180.close()
        } catch (e: IOException) {
            Log.e(TAG, "closeSensor  error: ", e)
        }
    }

    override fun onDestroy() {
        closeSensor()
        super.onDestroy()
    }


}