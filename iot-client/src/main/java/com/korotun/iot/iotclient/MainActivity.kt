package com.korotun.iot.iotclient

import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.view.View.GONE
import android.view.View.VISIBLE
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.main_content.*
import kotlinx.android.synthetic.main.view_error.*
import kotlinx.android.synthetic.main.view_loading.*
import net.danlew.android.joda.JodaTimeAndroid
import org.joda.time.DateTime

class MainActivity : AppCompatActivity(), ChildEventListener {

    private val bmp180Reference: DatabaseReference by lazy { fireBase.getReference(CONNECTION_DATA_REFERENCE) }
    private val connectionReference: DatabaseReference by lazy { fireBase.getReference(SENSOR_DATA_REFERENCE) }
    private var valueListener: ValueEventListener? = null
    private var handler: Handler? = null
    private val delayCallBack by lazy { Runnable { checkFirebaseConnection() } }
    private val fireBase by lazy { FirebaseDatabase.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JodaTimeAndroid.init(this)
        setContentView(R.layout.activity_main)
        error_view.setOnClickListener { checkFirebaseConnectionWithDelay() }
    }

    override fun onStart() {
        super.onStart()
        checkFirebaseConnectionWithDelay()
        registerFirebaseListener()
    }


    private fun checkFirebaseConnectionWithDelay() {
        showLoading()
        handler = Handler()
        handler?.postDelayed(delayCallBack, 2000)
    }


    private fun checkFirebaseConnection() {
        valueListener = object : ValueEventListener {
            override fun onCancelled(p0: DatabaseError?) {

            }

            override fun onDataChange(snapShot: DataSnapshot?) {
                val connected = snapShot?.getValue(Boolean::class.java)!!
                if (connected) {
                } else {
                    showError(getString(R.string.connection_error))
                }
            }
        }
        connectionReference.addValueEventListener(valueListener)
    }

    private fun showLoading() {
        main_content.visibility = GONE
        loading_view.visibility = VISIBLE
        error_view.visibility = GONE
    }

    private fun showError(message: String?) {
        main_content.visibility = GONE
        loading_view.visibility = GONE
        error_view.visibility = VISIBLE
        message?.let { error_view.text = it }
    }

    private fun showContent() {
        main_content.visibility = VISIBLE
        loading_view.visibility = GONE
        error_view.visibility = GONE
    }

    private fun registerFirebaseListener() {
        bmp180Reference.orderByChild("date").limitToLast(1).addChildEventListener(this)
    }

    override fun onChildMoved(dataSnapshot: DataSnapshot?, p1: String?) {
    }

    override fun onChildChanged(dataSnapshot: DataSnapshot?, p1: String?) {
    }

    override fun onChildAdded(dataSnapshot: DataSnapshot?, p1: String?) {
        showContent()
        fillUI(dataSnapshot?.getValue(Bmp180Data::class.java))
    }


    override fun onChildRemoved(dataSnapshot: DataSnapshot?) {
    }

    override fun onCancelled(error: DatabaseError?) {
        showError(error?.message)
    }


    private fun fillUI(bmp180Data: Bmp180Data?) {
        val dateTime = DateTime(bmp180Data?.date)
        var minuteOfHour = dateTime.minuteOfHour.toString()
        if (minuteOfHour.length < 2) {
            minuteOfHour = "0$minuteOfHour"
        }
        txv_time.text = "${dateTime.hourOfDay}:$minuteOfHour"
        txv_date.text = "${dateTime.year}/${dateTime.monthOfYear}/${dateTime.dayOfMonth}"
        bmp180Data?.pressure?.let { it.div(PASCAL_TO_MM_HG_CONST).toInt().toString() } //convert to mm of mercury
                ?.let { txv_press.text = it }
        bmp180Data?.temperature?.let { txv_temp.text = it.toString() }
    }

    override fun onStop() {
        super.onStop()
        unregisterListeners()
    }

    private fun unregisterListeners() {
        valueListener?.let { connectionReference.removeEventListener(it) }
        bmp180Reference.removeEventListener(this)
        handler?.removeCallbacks { delayCallBack }
    }

    companion object {

        private val SENSOR_DATA_REFERENCE = ".info/connected"
        private val CONNECTION_DATA_REFERENCE = "bmp180"
        private val PASCAL_TO_MM_HG_CONST = 133.322368
    }
}
