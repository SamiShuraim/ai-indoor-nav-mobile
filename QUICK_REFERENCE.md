# Quick Reference: Indoor Localization System

## 🚀 5-Minute Integration

### 1. Add to your Activity

```kotlin
import com.KFUPM.ai_indoor_nav_mobile.localization.LocalizationController

class MainActivity : AppCompatActivity() {
    private lateinit var locController: LocalizationController
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locController = LocalizationController(this)
        
        // Check permissions first (see LOCALIZATION_README.md)
        
        lifecycleScope.launch {
            if (locController.initialize(floorId = 1)) {
                locController.start()
                observeLocation()
            }
        }
    }
    
    private fun observeLocation() {
        lifecycleScope.launch {
            locController.localizationState.collect { state ->
                // Current node
                val node = state.currentNodeId
                val conf = state.confidence
                
                // Current position
                locController.getCurrentPosition()?.let { (x, y) ->
                    updateMapMarker(x, y)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        locController.cleanup()
    }
}
```

### 2. Required Permissions (already in manifest)

- `BLUETOOTH_SCAN` (Android 12+)
- `BLUETOOTH_CONNECT` (Android 12+)
- `ACCESS_FINE_LOCATION` (Android <12)
- `ACTIVITY_RECOGNITION` (optional, for steps)

### 3. Backend API Requirements

```
GET /api/Beacon?floor={id}
GET /api/Graph?floor={id}
GET /api/LocalizationConfig
```

---

## 📊 State Output

```kotlin
data class LocalizationState(
    currentNodeId: String?,        // Best node ID
    confidence: Double,            // 0.0 to 1.0
    pathHistory: List<String>,     // Recent nodes
    debug: DebugInfo?              // Optional telemetry
)
```

---

## 🎛️ Quick Tuning

### Problem: Jumpy/unstable position
**Solution:** Increase `hysteresisK` to 3

### Problem: Too slow to respond
**Solution:** Decrease `hysteresisK` to 1

### Problem: Low confidence
**Solution:** 
- Check beacon count (need 3+)
- Increase `bleWindowSize` to 7
- Verify beacon coordinates

### Problem: Wrong node at junctions
**Solution:**
- Increase `pairwiseWeight` to 2.0
- Add more beacons near junctions
- Check graph topology

---

## 📁 File Structure

```
localization/
├── models/LocalizationModels.kt    [Data classes]
├── BeaconScanner.kt                [BLE + smoothing]
├── ImuTracker.kt                   [Steps + heading]
├── GraphModel.kt                   [Graph ops]
├── ObservationModel.kt             [Rank + pairwise]
├── TransitionModel.kt              [Speed gate + bias]
├── HmmEngine.kt                    [Viterbi + hysteresis]
├── ConfigProvider.kt               [API]
├── LocalizationController.kt       [Main API ⭐]
└── examples/LocalizationExample.kt [Full example]
```

---

## 🧪 Testing

### Run Unit Tests
```bash
./gradlew test
```

### Check Specific Test
```bash
./gradlew test --tests "*ObservationModelTest*"
```

---

## 🐛 Debugging

### Enable Debug Logging
```kotlin
localizationState.collect { state ->
    state.debug?.let { debug ->
        Log.d("LOC", "Beacons: ${debug.visibleBeaconCount}")
        Log.d("LOC", "Top nodes: ${debug.topPosteriors}")
        Log.d("LOC", "Tick: ${debug.tickDurationMs}ms")
    }
}
```

### Common Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| No beacons detected | Bluetooth off / No permissions | Check BT + permissions |
| Confidence < 0.4 | <2 beacons visible | Move to beacon area |
| Tick > 50ms | Graph too large | Reduce `searchRadiusM` |
| Wrong floor | Multiple floors overlap | Verify floor selection |

---

## 📖 Full Documentation

- **Complete Guide**: `LOCALIZATION_README.md` (477 lines)
- **Implementation Details**: `IMPLEMENTATION_SUMMARY.md`
- **Example Code**: `localization/examples/LocalizationExample.kt`

---

## 📞 Support

For detailed information:
1. Read `LOCALIZATION_README.md` section for your issue
2. Check unit tests for usage examples
3. Review `LocalizationExample.kt` for integration patterns

---

**System Status**: ✅ Production Ready  
**Last Updated**: 2025-11-09
