package it.palsoftware.pastiera

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for Unicode character RecyclerView.
 * Optimized for performance using classic RecyclerView.
 */
class UnicodeCharacterRecyclerViewAdapter(
    private val characters: List<String>,
    private val onCharacterClick: (String) -> Unit
) : RecyclerView.Adapter<UnicodeCharacterRecyclerViewAdapter.CharacterViewHolder>() {

    class CharacterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val characterText: TextView = itemView.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        val holder = CharacterViewHolder(view)
        
        // Configure TextView to center character, white and bold
        holder.characterText.apply {
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 0)
            minHeight = (40 * parent.context.resources.displayMetrics.density).toInt()
            minWidth = (40 * parent.context.resources.displayMetrics.density).toInt()
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        
        // Click listener setup
        view.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && position < characters.size) {
                onCharacterClick(characters[position])
            }
        }
        
        return holder
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        holder.characterText.text = characters[position]
    }

    override fun getItemCount(): Int = characters.size
}


