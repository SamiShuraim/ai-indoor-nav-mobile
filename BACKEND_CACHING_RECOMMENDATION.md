# Backend Caching Support Recommendation

## Summary
To optimize the caching system, the backend should provide a data versioning endpoint.

## Recommended Backend Endpoint

### Option 1: Simple Version Endpoint (Recommended)
```
GET /api/data-version
```

**Response:**
```json
{
  "version": "2024-11-23T16:30:00Z",  // ISO timestamp or version number
  "buildings_updated": "2024-11-23T10:00:00Z",
  "floors_updated": "2024-11-23T12:00:00Z",
  "beacons_updated": "2024-11-23T14:00:00Z",
  "route_nodes_updated": "2024-11-23T15:00:00Z"
}
```

### Option 2: ETag Support
Add `ETag` headers to existing endpoints:
- `/api/buildings/` → Returns `ETag: "abc123"`
- `/api/floors/{building_id}/` → Returns `ETag: "def456"`

Client sends `If-None-Match: "abc123"` and gets `304 Not Modified` if unchanged.

## Current Implementation (Without Backend Support)

The app currently uses **time-based caching**:
- Cache expires after **24 hours**
- Data is fetched fresh after expiration
- Works well but doesn't detect immediate backend changes

## Benefits of Backend Versioning

1. **Instant Updates**: Detect backend changes immediately
2. **Reduced Bandwidth**: Only fetch when data actually changed
3. **Better UX**: Faster startup when data is unchanged
4. **Flexibility**: Can invalidate cache for specific data types

## Integration Example

With version endpoint, the app flow would be:
1. App starts → Check cache validity (timestamp)
2. If cache valid → Fetch `/api/data-version`
3. Compare with cached version
4. If versions match → Use cached data (instant startup!)
5. If different → Fetch fresh data and update cache

**Startup time improvement:**
- Cold start: ~5-10 seconds (full API fetch)
- Warm start with cache: ~500ms (version check only)
- Hot start (cache + version match): ~100ms (no network needed)
