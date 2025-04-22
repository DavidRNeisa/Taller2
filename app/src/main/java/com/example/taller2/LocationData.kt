package com.example.taller2

import org.json.JSONException
import org.json.JSONObject
import java.util.Date

class LocationData(var date: Date, var latitude: Double, var longitude: Double) {
    fun toJSON(): JSONObject {
        val obj = JSONObject()
        try {
            obj.put("latitude", latitude)
            obj.put("longitude", longitude)
            obj.put("date", date.time)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return obj
    }
}