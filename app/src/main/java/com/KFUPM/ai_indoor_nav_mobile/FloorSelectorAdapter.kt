package com.KFUPM.ai_indoor_nav_mobile

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.KFUPM.ai_indoor_nav_mobile.models.Floor

class FloorSelectorAdapter(
    private var floors: List<Floor>,
    private var selectedFloorId: String? = null,
    private val onFloorSelected: (Floor) -> Unit
) : RecyclerView.Adapter<FloorSelectorAdapter.FloorViewHolder>() {

    class FloorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val floorName: TextView = itemView.findViewById(R.id.floorName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FloorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_floor_selector, parent, false)
        return FloorViewHolder(view)
    }

    override fun onBindViewHolder(holder: FloorViewHolder, position: Int) {
        val floor = floors[position]
        holder.floorName.text = floor.name
        
        // Highlight selected floor
        if (floor.id == selectedFloorId) {
            holder.floorName.setBackgroundColor(Color.parseColor("#4CAF50"))
            holder.floorName.setTextColor(Color.WHITE)
        } else {
            holder.floorName.setBackgroundColor(Color.TRANSPARENT)
            holder.floorName.setTextColor(Color.WHITE)
        }
        
        holder.itemView.setOnClickListener {
            selectedFloorId = floor.id
            notifyDataSetChanged()
            onFloorSelected(floor)
        }
    }

    override fun getItemCount(): Int = floors.size

    fun updateFloors(newFloors: List<Floor>, selectedId: String? = null) {
        floors = newFloors
        selectedFloorId = selectedId
        notifyDataSetChanged()
    }
    
    fun setSelectedFloor(floorId: String) {
        selectedFloorId = floorId
        notifyDataSetChanged()
    }
}