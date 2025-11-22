package com.KFUPM.ai_indoor_nav_mobile

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.KFUPM.ai_indoor_nav_mobile.models.Floor

class FloorSelectorAdapter(
    private var floors: List<Floor>,
    private var selectedFloorId: Int? = null,
    private val onFloorSelected: (Floor) -> Unit
) : RecyclerView.Adapter<FloorSelectorAdapter.FloorViewHolder>() {

    private var userCurrentFloorId: Int? = null // Track user's actual physical floor

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
        
        // Add arrow indicator if this is the user's current physical floor
        val isUserCurrentFloor = floor.id == userCurrentFloorId
        val arrow = if (isUserCurrentFloor) "→ " else ""
        val displayText = "${arrow}F${floor.floorNumber}"
        
        holder.floorName.text = displayText
        Log.d("FloorSelector", "Binding floor: id=${floor.id}, floorNumber=${floor.floorNumber}, name=${floor.name}, displayText=$displayText, userCurrentFloor=$isUserCurrentFloor")
        
        // Set selection state
        val isSelected = floor.id == selectedFloorId
        holder.itemView.isSelected = isSelected
        holder.floorName.isSelected = isSelected
        
        // Set background using selector
        holder.floorName.setBackgroundResource(R.drawable.floor_item_selector)
        holder.floorName.setTextColor(Color.WHITE)
        
        // Set click listeners on the item view
        holder.itemView.setOnClickListener {
            Log.d("FloorSelector", "Floor clicked: ${floor.name} (id: ${floor.id})")
            if (selectedFloorId != floor.id) {
                selectedFloorId = floor.id
                notifyDataSetChanged()
                onFloorSelected(floor)
            }
        }
    }

    override fun getItemCount(): Int = floors.size

    fun updateFloors(newFloors: List<Floor>, selectedId: Int? = null) {
        floors = newFloors
        selectedFloorId = selectedId
        notifyDataSetChanged()
    }
    
    fun setSelectedFloor(floorId: Int) {
        selectedFloorId = floorId
        notifyDataSetChanged()
    }
    
    fun setUserCurrentFloor(floorId: Int) {
        if (userCurrentFloorId != floorId) {
            userCurrentFloorId = floorId
            notifyDataSetChanged()
        }
    }
}