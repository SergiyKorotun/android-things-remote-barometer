package com.korotun.iot.iotclient

import java.util.*

class Bmp180Data {

    var temperature: Int = 0
    var pressure: Int = 0
    var altitude: Int = 0
    var date: Long = 0

    constructor() {
    }

    constructor(temperature: Int, pressure: Int, altitude: Int, date: Long = Date().time) {
        this.temperature = temperature
        this.pressure = pressure
        this.altitude = altitude
        this.date = date
    }

    override fun toString(): String {
        return "Bmp180Data(temperature=$temperature, pressure=$pressure, altitude=$altitude, date=${Date(date)})"
    }

}
