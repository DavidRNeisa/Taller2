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
    private val LIGHT_THRESHOLD = 20f // ajusta según necesidad


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

        // Inicializar sensor de luz
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)!!

        // Inicializar request de ubicación
        mLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        // Inicializar Geocoder
        mGeocoder = Geocoder(baseContext)

        // Configurar el EditText para capturar cuando el usuario termine de escribir la dirección
        binding.texto.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val addressString = binding.texto.text.toString()
                if (!addressString.isEmpty()) {
                    try {
                        val addresses = mGeocoder.getFromLocationName(addressString, 2)
                        if (addresses != null && addresses.isNotEmpty()) {
                            val address: Address = addresses[0]
                            val position = LatLng(address.latitude, address.longitude)
                            if (mMap != null) {
                                mMap.addMarker(
                                    MarkerOptions().position(position).title(address.locality)
                                )
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 16f))
                            } else {
                                Toast.makeText(
                                    this,
                                    "No se pudo encontrar la dirección",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GEOCODER", "Error al obtener la dirección: ${e.message}")
                    }
                }
                true // Indica que manejaste la acción
            } else {
                false // Indica que no manejaste la acción
            }

        }

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val latLng = LatLng(location.latitude, location.longitude)
                mMap.clear()
                mMap.addMarker(MarkerOptions().position(latLng).title("Mi ubicación"))
                //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            }
        }

        // Verificar permisos al iniciar
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Mostrar razonamiento si lo deseas
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        lightSensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                val lightLevel = event.values[0]

                val style = if (lightLevel < LIGHT_THRESHOLD) {
                    R.raw.style1
                } else {
                    R.raw.retro
                }

                try {
                    val success = mMap.setMapStyle(
                        com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(
                            this@MapsActivity, style
                        )
                    )

                    if (!success) {
                        Log.e("MAP_STYLE", "Style parsing failed.")
                    }
                } catch (e: Exception) {
                    Log.e("MAP_STYLE", "Can't find style. Error: ", e)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            lightSensorListener,
            lightSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )

    }


    private fun requestLocationPermission() {
        when {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                enableUserLocation()
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true // Habilita la localización del usuario en el mapa
            mMap.uiSettings.isZoomControlsEnabled = true // Muestra los controles de zoom
            mMap.uiSettings.isMyLocationButtonEnabled = true // Muestra el botón de mi ubicación

            // Mover la cámara a la ubicación actual del usuario
            mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f)) // Mueve la cámara al lugar con un nivel de zoom
                    mMap.addMarker(MarkerOptions().position(latLng).title("Tu ubicación")) // Coloca el marcador en la ubicación
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback,
                mainLooper
            )
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

                val marker = mMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title(addressText)
                )

                marker?.showInfoWindow()

                // Obtener la última ubicación conocida del usuario
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
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

                            val distanceInMeters = userLocation.distanceTo(markerLocation)
                            val distanceInKm = distanceInMeters / 1000

                            Toast.makeText(
                                this,
                                "Distancia hasta el marcador: %.2f km".format(distanceInKm),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GEOCODER", "Error al obtener dirección desde lat/lng: ${e.message}")
                Toast.makeText(this, "No se pudo obtener la dirección", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
