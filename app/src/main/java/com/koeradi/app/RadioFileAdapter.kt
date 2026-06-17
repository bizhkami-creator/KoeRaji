package com.koeradi.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RadioFileAdapter(private val radioFiles: List<RadioFile>) :
    RecyclerView.Adapter<RadioFileAdapter.RadioFileViewHolder>() {

    class RadioFileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStationName: TextView = view.findViewById(R.id.tvStationName)
        val tvProgramName: TextView = view.findViewById(R.id.tvProgramName)
        val tvBroadcastDate: TextView = view.findViewById(R.id.tvBroadcastDate)
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RadioFileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_radio_file, parent, false)
        return RadioFileViewHolder(view)
    }

    override fun onBindViewHolder(holder: RadioFileViewHolder, position: Int) {
        val radioFile = radioFiles[position]
        holder.tvStationName.text = radioFile.stationName
        holder.tvProgramName.text = radioFile.programName
        holder.tvBroadcastDate.text = radioFile.broadcastDate
        holder.tvFileName.text = radioFile.fileName
    }

    override fun getItemCount(): Int = radioFiles.size
}
