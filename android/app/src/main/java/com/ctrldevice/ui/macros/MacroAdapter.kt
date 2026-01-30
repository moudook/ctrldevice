package com.ctrldevice.ui.macros

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ctrldevice.R
import com.ctrldevice.features.agent_engine.skills.Macro

class MacroAdapter(
    private val macros: List<Macro>,
    private val onMacroClick: (Macro) -> Unit
) : RecyclerView.Adapter<MacroAdapter.MacroViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MacroViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_macro, parent, false)
        return MacroViewHolder(view)
    }

    override fun onBindViewHolder(holder: MacroViewHolder, position: Int) {
        holder.bind(macros[position])
    }

    override fun getItemCount(): Int = macros.size

    inner class MacroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.macroName)
        private val descriptionText: TextView = itemView.findViewById(R.id.macroDescription)
        private val triggersText: TextView = itemView.findViewById(R.id.macroTriggers)

        fun bind(macro: Macro) {
            nameText.text = macro.name
            descriptionText.text = macro.description.ifEmpty { "No description" }
            triggersText.text = "Triggers: ${macro.triggers.joinToString(", ")}"

            itemView.setOnClickListener {
                onMacroClick(macro)
            }
        }
    }
}
