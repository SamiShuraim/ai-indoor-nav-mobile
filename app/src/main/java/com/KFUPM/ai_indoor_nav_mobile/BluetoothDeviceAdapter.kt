package com.KFUPM.ai_indoor_nav_mobile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BluetoothDeviceAdapter(
    private val onDeviceClick: (BluetoothDeviceInfo) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {
    
    private var devices = listOf<BluetoothDeviceInfo>()
    
    fun updateDevices(newDevices: List<BluetoothDeviceInfo>) {
        devices = newDevices
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return DeviceViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }
    
    override fun getItemCount() = devices.size
    
    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.deviceName)
        private val addressText: TextView = itemView.findViewById(R.id.deviceAddress)
        private val rssiText: TextView = itemView.findViewById(R.id.deviceRssi)
        private val distanceText: TextView = itemView.findViewById(R.id.deviceDistance)
        
        fun bind(device: BluetoothDeviceInfo) {
            nameText.text = device.name
            addressText.text = device.address
            rssiText.text = "RSSI: ${device.rssi} dBm"
            distanceText.text = "Distance: ${device.distance}"
            
            itemView.setOnClickListener {
                onDeviceClick(device)
            }
        }
    }
}