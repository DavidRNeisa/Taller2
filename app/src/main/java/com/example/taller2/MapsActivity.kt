package com.example.taller2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.taller2.databinding.ActivityMapsBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import org.json.JSONArray
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Date

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var mGeocoder: Geocoder
    private lateinit var mLocationRequest: LocationRequest
    private lateinit var mLocationCallback: LocationCallback
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private lateinit var lightSensor: Sensor
    private lateinit var lightSensorListener: SensorEventListener
    private val LIGHT_THRESHOLD = 20f
    private val DISTANCE_THRESHOLD = 30f // 30 metros para guardar nueva ubicación
    private var lastSavedLocation: Location? = null
    private var locationsArray = JSONArray()
    private val FILENAME = "locations.json"
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableUserLocation()
            startLocationUpdates()
        } else {
            Log.e("PERMISSIONS", "Permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)!!

        mLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        mGeocoder = Geocoder(baseContext)

        binding.texto.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val addressString = binding.texto.text.toString()
                if (addressString.isNotEmpty()) {
                    try {
                        val addresses = mGeocoder.getFromLocationName(addressString, 2)
                        if (!addresses.isNullOrEmpty()) {
                            val address: Address = addresses[0]
                            val position = LatLng(address.latitude, address.longitude)
                            mMap.addMarker(MarkerOptions().position(position).title(address.locality))
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 16f))
                        } else {
                            Toast.makeText(this, "No se pudo encontrar la dirección", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("GEOCODER", "Error al obtener la dirección: ${e.message}")
                    }
                }
                true
            } else {
                false
            }
        }

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val latLng = LatLng(location.latitude, location.longitude)

                // Verificar si nos hemos movido más de 30 metros desde la última ubicación guardada
                if (shouldSaveLocation(location)) {
                    // Guardar la nueva ubicación
                    saveLocationToFile(location)

                    // Actualizar el marcador en el mapa
                    mMap.clear()
                    mMap.addMarker(MarkerOptions().position(latLng).title("Mi ubicación"))
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))

                    // Actualizar la última ubicación guardada
                    lastSavedLocation = location
                }
            }
        }

        lightSensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                val lightLevel = event.values[0]
                val style = if (lightLevel < LIGHT_THRESHOLD) R.raw.style1 else R.raw.retro
                try {
                    val success = mMap.setMapStyle(
                        com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(
                            this@MapsActivity, style
                        )
                    )
                    if (!success) Log.e("MAP_STYLE", "Style parsing failed.")
                } catch (e: Exception) {
                    Log.e("MAP_STYLE", "Can't find style. Error: ", e)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(lightSensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isZoomControlsEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true

            mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    mMap.addMarker(MarkerOptions().position(latLng).title("Tu ubicación"))

                    // Establecer la primera ubicación guardada
                    if (lastSavedLocation == null) {
                        lastSavedLocation = it
                        saveLocationToFile(it)
                    }
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, mainLooper)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        requestLocationPermission()
        enableUserLocation()

        mMap.setOnMapLongClickListener { latLng ->
            try {
                val addresses = mGeocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                val addressText = if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val street = address.thoroughfare ?: ""
                    val city = address.locality ?: ""
                    val country = address.countryName ?: ""
                    "$street, $city, $country".trim().replace(", ,", ",").replace(" ,", "")
                } else {
                    "Dirección no disponible"
                }

                val marker = mMap.addMarker(MarkerOptions().position(latLng).title(addressText))
                marker?.showInfoWindow()

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mFusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            val userLocation = Location("").apply {
                                latitude = it.latitude
                                longitude = it.longitude
                            }
                            val markerLocation = Location("").apply {
                                latitude = latLng.latitude
                                longitude = latLng.longitude
                            }
                            val distanceInKm = userLocation.distanceTo(markerLocation) / 1000
                            Toast.makeText(this, "Distancia hasta el marcador: %.2f km".format(distanceInKm), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GEOCODER", "Error al obtener dirección desde lat/lng: ${e.message}")
                Toast.makeText(this, "No se pudo obtener la dirección", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSavedLocations() {
        try {
            val file = File(getExternalFilesDir(null), FILENAME)
            if (file.exists()) {
                val jsonString = file.readText()
                locationsArray = JSONArray(jsonString)
                Log.i("LOCATION", "Loaded ${locationsArray.length()} locations from file")
            }
        } catch (e: Exception) {
            Log.e("LOCATION", "Error loading locations: ${e.message}")
        }
    }


    private fun shouldSaveLocation(newLocation: Location): Boolean {
        if (lastSavedLocation == null) return true

        val distance = lastSavedLocation!!.distanceTo(newLocation)
        return distance > DISTANCE_THRESHOLD
    }


    private fun saveLocationToFile(location: Location) {
        val locationData = LocationData(
            Date(System.currentTimeMillis()),
            location.latitude,
            location.longitude
        )

        locationsArray.put(locationData.toJSON())

        try {
            val file = File(getExternalFilesDir(null), FILENAME)
            Log.i("LOCATION", "Saving location to: $file")
            val output = BufferedWriter(FileWriter(file))
            output.write(locationsArray.toString())
            output.close()
            Toast.makeText(this, "Location saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("LOCATION", "Error saving location: ${e.message}")
        }
    }
}