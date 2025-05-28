package com.example.gammaplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class VideoAdapter(private val items: List<File>, private val listener: OnItemClickListener) : RecyclerView.Adapter<VideoAdapter.ViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(file: File)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val videoName: TextView = itemView.findViewById(R.id.videoName)
        val icon: ImageView = itemView.findViewById(R.id.icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.videoName.text = item.name

        if (item.isDirectory) {
            holder.icon.setImageResource(R.drawable.ic_folder) // your folder icon
        } else {
            holder.icon.setImageResource(R.drawable.ic_play) // your play/video icon
        }

        holder.itemView.setOnClickListener {
            listener.onItemClick(item)
        }

        val layoutParams = holder.itemView.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.topMargin = if (position == 0) 30 else 30
        holder.itemView.layoutParams = layoutParams
    }

    override fun getItemCount() = items.size
}
