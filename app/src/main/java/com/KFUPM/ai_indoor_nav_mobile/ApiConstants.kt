package com.KFUPM.ai_indoor_nav_mobile

/**
 * API Configuration Constants
 */
object ApiConstants {
    // API Configuration - Use 10.0.2.2 for Android emulator to access host localhost
    const val API_BASE_URL = "http://192.168.128.223:5090"
    
    // MapTiler Configuration (if needed)
    const val MAPTILER_API_KEY = ""
    const val MAPTILER_STYLE_URL = ""
    
    // API Endpoints
    object Endpoints {
        const val LOGIN = "/api/Login"
        const val VALIDATE = "/api/Login/validate"
        const val DASHBOARD = "/dashboard"
        
        // Buildings
        const val BUILDINGS = "/api/Building"
        fun buildingById(id: Int) = "/api/Building/$id"
        
        // Floors
        const val FLOORS = "/api/Floor"
        fun floorsByBuilding(buildingId: Int) = "/api/Floor?building=$buildingId"
        fun floorById(id: Int) = "/api/Floor/$id"
        
        // Beacons
        const val BEACONS = "/api/Beacon"
        fun beaconById(id: Int) = "/api/Beacon/$id"
        fun beaconsByFloor(floorId: Int) = "/api/Beacon?floor=$floorId"
        fun beaconByUuid(uuid: String) = "/api/Beacon/uuid/$uuid"
        const val BEACONS_ACTIVE = "/api/Beacon/active"
        fun beaconsLowBattery(threshold: Int) = "/api/Beacon/low-battery/$threshold"
        fun beaconBattery(id: Int, level: Int) = "/api/Beacon/$id/battery/$level"
        fun beaconHeartbeat(id: Int) = "/api/Beacon/$id/heartbeat"
        
        // Beacon Types
        const val BEACON_TYPES = "/api/BeaconType"
        fun beaconTypeById(id: Int) = "/api/BeaconType/$id"
        fun beaconTypeByName(name: String) = "/api/BeaconType/name/$name"
        
        // POIs
        const val POIS = "/api/Poi"
        fun poiById(id: Int) = "/api/Poi/$id"
        fun poisByFloor(floorId: Int) = "/api/Poi?floor=$floorId"
        
        // POI Categories
        const val POI_CATEGORIES = "/api/PoiCategory"
        fun poiCategoryById(id: Int) = "/api/PoiCategory/$id"
        fun poiCategoryByName(name: String) = "/api/PoiCategory/name/$name"
        
        // Route Nodes
        const val ROUTE_NODES = "/api/RouteNode"
        fun routeNodeById(id: Int) = "/api/RouteNode/$id"
        fun routeNodesByFloor(floorId: Int) = "/api/RouteNode?floor=$floorId"
    }
    
    // Storage Keys
    object StorageKeys {
        const val JWT_TOKEN = "jwtToken"
    }
    
    // Request Headers
    object Headers {
        const val CONTENT_TYPE = "Content-Type"
        const val CONTENT_TYPE_JSON = "application/json"
        const val AUTHORIZATION = "Authorization"
    }
}