package com.eaor.coffeefee.fragments
import com.google.android.libraries.places.api.model.Place
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.ImageAdapter
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import android.graphics.Rect

class EditPostFragment : Fragment() {
    private lateinit var bottomNav: View
    private lateinit var imageAdapter: ImageAdapter
    private val selectedImages = mutableListOf<Uri>()
    private lateinit var currentLocation: LatLng
    private lateinit var placesClient: PlacesClient

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImages.add(it)
            updateImagesRecyclerView()
        }
    }

    private val startAutocomplete =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    val place = Autocomplete.getPlaceFromIntent(intent)
                    currentLocation = place.latLng ?: LatLng(0.0, 0.0) // Default if null
                    view?.findViewById<TextView>(R.id.currentLocationText)?.text = place.name
                }
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_edit_post, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bottomNav = requireActivity().findViewById(R.id.bottom_nav)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)

        view.findViewById<TextView>(R.id.toolbarTitle).text = "Edit Post"

        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }

        // Initialize RecyclerView for images
        val recyclerView = view.findViewById<RecyclerView>(R.id.imagesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        imageAdapter = ImageAdapter(selectedImages) { position: Int -> 
            selectedImages.removeAt(position) // Use removeAt with the position
            updateImagesRecyclerView()
        }
        recyclerView.adapter = imageAdapter

        // Set initial visibility of the RecyclerView
        recyclerView.visibility = if (selectedImages.isEmpty()) {
            View.GONE
        } else {
            View.VISIBLE
        }

        // Set up buttons
        view.findViewById<Button>(R.id.changeLocationButton).setOnClickListener {
            launchPlacePicker()
        }

        view.findViewById<Button>(R.id.addImageButton).setOnClickListener {
            getContent.launch("image/*")
        }

        view.findViewById<Button>(R.id.savePostButton).setOnClickListener {
            // Handle save logic here
            findNavController().navigateUp()
        }

        // Set up keyboard visibility listener
        view.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            // If keyboard is showing (height > 150), hide bottom nav
            bottomNav.visibility = if (keypadHeight > 150) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure bottom nav is visible when leaving the fragment
        bottomNav.visibility = View.VISIBLE
    }

    private fun updateImagesRecyclerView() {
        imageAdapter.notifyDataSetChanged()
        view?.findViewById<RecyclerView>(R.id.imagesRecyclerView)?.visibility = if (selectedImages.isEmpty()) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun launchPlacePicker() {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .build(requireActivity())
        startAutocomplete.launch(intent)
    }
}