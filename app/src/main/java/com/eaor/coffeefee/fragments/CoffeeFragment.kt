package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.eaor.coffeefee.R

class CoffeeFragment : Fragment() {
    private var isFavorite = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_coffee, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)

        // Setup back button
        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }

        // Setup favorite button
        val favoriteButton = view.findViewById<ImageButton>(R.id.favoriteButton)
        favoriteButton.setOnClickListener {
            isFavorite = !isFavorite
            favoriteButton.setImageResource(
                if (isFavorite) R.drawable.ic_heart_filled
                else R.drawable.ic_heart_outline
            )
        }

        // Get coffee shop data from arguments
        arguments?.let { args ->
            val name = args.getString("name", "")
            val description = args.getString("description", "")
            
            // Set coffee shop name in both title and content
            view.findViewById<TextView>(R.id.toolbarTitle).text = name
            view.findViewById<TextView>(R.id.coffeeName).text = name
            view.findViewById<TextView>(R.id.descriptionText).text = description
        }
    }
} 