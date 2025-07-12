package com.KFUPM.ai_indoor_nav_mobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.KFUPM.ai_indoor_nav_mobile.R
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize MapLibre properly with no API key and using the DEMO tile server
        MapLibre.getInstance(
            this,
            "", // Empty API key (you don't need a key for open tile server)
            WellKnownTileServer.MapLibre // Or choose other tile servers here
        )

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { maplibreMap ->
            maplibreMap.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) {
                // Map is ready
            }
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
