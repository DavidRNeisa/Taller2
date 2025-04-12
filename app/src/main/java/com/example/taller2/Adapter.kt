package com.example.taller2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class Adapter(private val contactos: List<Contactos>) : RecyclerView.Adapter<Adapter.ContactViewHolder>() {

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nombre: TextView = itemView.findViewById(R.id.nombre)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.activity_adapter, parent, false)
        // Inflate the layout for each item in the RecyclerView
        // val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        // Return a new instance of the ViewHolder
        return ContactViewHolder(view)
    }


    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contacto = contactos[position]
        holder.nombre.text = contacto.nombre
    }

    override fun getItemCount(): Int = contactos.size
}
