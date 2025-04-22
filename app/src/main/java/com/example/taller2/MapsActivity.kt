package com.example.taller2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.taller2.databinding.ActivityMapsBinding
import com.google.android.gms.location.*
import org.json.JSONArray
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Date
class MapsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapsBinding
    private lateinit var mGeocoder: Geocoder
    private lateinit var mLocationRequest: LocationRequest
    private lateinit var mLocationCallback: LocationCallback
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private lateinit var lightSensor: Sensor
    private lateinit var lightSensorListener: SensorEventListener
    private lateinit var roadManager: OSRMRoadManager
    private var roadOverlay: Polyline? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var currentLocation: GeoPoint? = null
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

        // Configurar OSMDroid
        Configuration.getInstance().setUserAgentValue(applicationContext.packageName)

        // Política para permitir llamados de red síncronos (solo para pruebas)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar el mapa OSM
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(16.0)

        // Inicializar el Road Manager para rutas
        roadManager = OSRMRoadManager(this, "ANDROID")

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)!!

        mLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        mGeocoder = Geocoder(baseContext)

        // Configurar la ubicación del usuario en el mapa OSM
        setupMyLocation()

        binding.texto.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val addressString = binding.texto.text.toString()
                searchAddressAndDrawRoute(addressString)
                true
            } else {
                false
            }
        }

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                currentLocation = GeoPoint(location.latitude, location.longitude)

                // Verificar si nos hemos movido más de 30 metros desde la última ubicación guardada
                if (shouldSaveLocation(location)) {
                    // Guardar la nueva ubicación
                    saveLocationToFile(location)

                    // Actualizar la última ubicación guardada
                    lastSavedLocation = location
                }
            }
        }

        lightSensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val lightLevel = event.values[0]
                // Aquí podrías cambiar el estilo del mapa OSM si fuera necesario
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Configurar click largo en el mapa para crear un marcador y una ruta
        setupMapLongClick()

        // Solicitar permisos de ubicación
        requestLocationPermission()
    }

    private fun setupMyLocation() {
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.map)
        myLocationOverlay?.enableMyLocation()
        myLocationOverlay?.enableFollowLocation()
        binding.map.overlays.add(myLocationOverlay)
    }

    private fun setupMapLongClick() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                // Convertir punto de pantalla a coordenadas geográficas
                val x = e.x.toInt()
                val y = e.y.toInt()
                val iGeoPoint = binding.map.projection.fromPixels(x, y)
                val geoPoint = GeoPoint(iGeoPoint.latitude, iGeoPoint.longitude)

                // Crear un marcador en el punto seleccionado
                val marker = Marker(binding.map)
                marker.position = geoPoint
                marker.title = "Destino"

                // Limpiar marcadores anteriores (excepto el de ubicación)
                val overlaysToRemove = binding.map.overlays.filterIsInstance<Marker>().filter { it != myLocationOverlay }
                binding.map.overlays.removeAll(overlaysToRemove)

                // Agregar el nuevo marcador
                binding.map.overlays.add(marker)

                // Dibujar la ruta desde la posición actual hasta el punto seleccionado
                currentLocation?.let {
                    drawRoute(it, geoPoint)
                }

                // Refrescar el mapa
                binding.map.invalidate()
            }
        })

        binding.map.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            false  // Permitir que el evento se propague
        }
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
            myLocationOverlay?.enableMyLocation()

            mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    currentLocation = GeoPoint(it.latitude, it.longitude)
                    binding.map.controller.setCenter(currentLocation)

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

    private fun searchAddressAndDrawRoute(addressString: String) {
        if (addressString.isNotEmpty()) {
            try {
                val addresses = mGeocoder.getFromLocationName(addressString, 2)
                if (!addresses.isNullOrEmpty()) {
                    val address: Address = addresses[0]
                    val destinationPoint = GeoPoint(address.latitude, address.longitude)

                    // Crear un marcador en la dirección buscada
                    val marker = Marker(binding.map)
                    marker.position = destinationPoint
                    marker.title = address.locality ?: addressString

                    // Limpiar marcadores anteriores
                    val overlaysToRemove = binding.map.overlays.filterIsInstance<Marker>().filter { it != myLocationOverlay }
                    binding.map.overlays.removeAll(overlaysToRemove)

                    // Agregar el nuevo marcador
                    binding.map.overlays.add(marker)

                    // Centrar el mapa en el destino
                    binding.map.controller.animateTo(destinationPoint)

                    // Dibujar la ruta
                    currentLocation?.let {
                        drawRoute(it, destinationPoint)
                    }
                } else {
                    Toast.makeText(this, "No se pudo encontrar la dirección", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("GEOCODER", "Error al obtener la dirección: ${e.message}")
            }
        }
    }

    private fun drawRoute(start: GeoPoint, finish: GeoPoint) {
        // Crear los puntos de la ruta
        val routePoints = ArrayList<GeoPoint>()
        routePoints.add(start)
        routePoints.add(finish)

        // Obtener la ruta
        val road = roadManager.getRoad(routePoints)
        Log.i("OSM_activity", "Route length: ${road.mLength} km")
        Log.i("OSM_activity", "Duration: ${road.mDuration / 60} min")

        // Eliminar ruta anterior si existe
        roadOverlay?.let { binding.map.overlays.remove(it) }

        // Crear y configurar la nueva ruta
        roadOverlay = RoadManager.buildRoadOverlay(road)
        roadOverlay?.outlinePaint?.color = Color.RED
        roadOverlay?.outlinePaint?.strokeWidth = 10f

        // Agregar la ruta al mapa
        binding.map.overlays.add(roadOverlay)

        // Refrescar el mapa
        binding.map.invalidate()

        // Mostrar información de la ruta
        val distanceMsg = "Distancia: %.2f km".format(road.mLength)
        val durationMsg = "Tiempo estimado: %.1f min".format(road.mDuration / 60)
        Toast.makeText(this, "$distanceMsg\n$durationMsg", Toast.LENGTH_LONG).show()
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

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        sensorManager.registerListener(lightSensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
        sensorManager.unregisterListener(lightSensorListener)
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
    }
}