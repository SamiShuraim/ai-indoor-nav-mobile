package com.KFUPM.ai_indoor_nav_mobile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.maplibre.geojson.Feature

class POIAdapter(
    private val onPOIClick: (Feature) -> Unit
) : RecyclerView.Adapter<POIAdapter.POIViewHolder>() {
    
    private var pois = listOf<Feature>()
    
    fun updatePOIs(newPOIs: List<Feature>) {
        pois = newPOIs
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): POIViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poi, parent, false)
        return POIViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: POIViewHolder, position: Int) {
        holder.bind(pois[position])
    }
    
    override fun getItemCount() = pois.size
    
    inner class POIViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.poiName)
        private val typeText: TextView = itemView.findViewById(R.id.poiType)
        private val descriptionText: TextView = itemView.findViewById(R.id.poiDescription)
        
        fun bind(poi: Feature) {
            val name = poi.getStringProperty("name") ?: "Unknown POI"
            val type = poi.getStringProperty("type") ?: "Unknown Type"
            val description = poi.getStringProperty("description") ?: "No description available"
            
            nameText.text = name
            typeText.text = type
            descriptionText.text = description
            
            itemView.setOnClickListener {
                onPOIClick(poi)
            }
        }
    }
}