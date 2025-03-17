package com.eaor.coffeefee.fragments

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.ImageAdapter
import com.eaor.coffeefee.utils.GeminiService
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class AddPostFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var bottomNav: View
    private var selectedLocation: LatLng? = null
    private val selectedImages = mutableListOf<Uri>()
    private lateinit var imageAdapter: ImageAdapter

    // Activity result launcher for image picker
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImages.clear()
            selectedImages.add(it)
            updateImagesRecyclerView()
        }
    }

    // Autocomplete location result handler
    private val startAutocomplete = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val place = Autocomplete.getPlaceFromIntent(result.data!!)
            selectedLocation = place.latLng
            view?.findViewById<TextView>(R.id.selectedLocationText)?.text = place.name
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            Log.d("AddPostFragment", "User canceled autocomplete")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_add_post, container, false)

        // Initialize Firebase and Places
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        Places.createClient(requireContext())

        bottomNav = requireActivity().findViewById(R.id.bottom_nav)
        bottomNav.visibility = View.GONE

        setupToolbar(view)
        setupLocationButton(view)
        setupImagePicker(view)
        setupPostButton(view)
        setupSearchView(view)

        return view
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayShowTitleEnabled(false)
        }

        view.findViewById<TextView>(R.id.toolbarTitle).text = "New Post"
        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupLocationButton(view: View) {
        view.findViewById<Button>(R.id.selectLocationButton).setOnClickListener {
            val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields).build(requireContext())
            startAutocomplete.launch(intent)
        }
    }

    private fun setupImagePicker(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.imagesRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = ImageAdapter(selectedImages) { position ->
                selectedImages.removeAt(position)
                updateImagesRecyclerView()
            }.also { imageAdapter = it }
            visibility = View.GONE
        }

        view.findViewById<Button>(R.id.addImageButton).setOnClickListener {
            getContent.launch("image/*")
        }
    }

    private fun updateImagesRecyclerView() {
        imageAdapter.notifyDataSetChanged()
        view?.findViewById<RecyclerView>(R.id.imagesRecyclerView)?.visibility =
            if (selectedImages.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setupSearchView(view: View) {
        val searchView = view.findViewById<SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?) = false
        })
    }

    private fun setupPostButton(view: View) {
        view.findViewById<Button>(R.id.postButton).setOnClickListener {
            val descriptionText = view.findViewById<EditText>(R.id.experienceDescription).text.toString()
            val locationText = view.findViewById<TextView>(R.id.selectedLocationText).text.toString()

            when {
                descriptionText.isBlank() -> {
                    showToast("Please enter a description")
                }
                selectedLocation == null -> {
                    showToast("Please select a location")
                }
                else -> {
                    generateAIContent(descriptionText, locationText)
                    handleImageUpload(descriptionText, locationText)
                }
            }
        }
    }

    private fun handleImageUpload(description: String, locationName: String) {
        val userId = auth.currentUser?.uid

        if (selectedImages.isNotEmpty()) {
            uploadImageToStorage(selectedImages.first(),
                onSuccess = { imageUrl ->
                    createPostInFirestore(description, userId, locationName, imageUrl)
                },
                onFailure = {
                    Log.e("AddPostFragment", "Image upload failed: ${it.message}")
                    showToast("Image upload failed")
                }
            )
        } else {
            createPostInFirestore(description, userId, locationName, null)
        }
    }

    private fun uploadImageToStorage(uri: Uri, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val photoRef = FirebaseStorage.getInstance()
            .reference.child("Posts/${UUID.randomUUID()}.jpg")

        photoRef.putFile(uri)
            .addOnSuccessListener {
                photoRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    onSuccess(downloadUri.toString())
                }.addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    private fun generateAIContent(userDescription: String, locationName: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tags = listOf("coffee", "cafe", "urban", locationName.split(",")[0])
                val result = GeminiService.getInstance().generateCoffeeExperience(userDescription, locationName, tags)

                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = {
                            Log.d("AddPostFragment", "AI Response: $it")
                        },
                        onFailure = {
                            Log.e("AddPostFragment", "AI Error", it)
                            showToast("AI generation failed: ${it.message}")
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.d("AddPostFragment", "Coroutine cancelled")
                    } else {
                        Log.e("AddPostFragment", "Error calling AI", e)
                        showToast("AI error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun createPostInFirestore(description: String, userId: String?, locationName: String, imageUrl: String?) {
        val postData = mapOf(
            "UserId" to userId,
            "experienceDescription" to description,
            "location" to mapOf(
                "name" to locationName,
                "latitude" to selectedLocation?.latitude,
                "longitude" to selectedLocation?.longitude
            ),
            "timestamp" to System.currentTimeMillis(),
            "photoUrl" to imageUrl
        )

        db.collection("Posts")
            .add(postData)
            .addOnSuccessListener {
                Log.d("AddPostFragment", "Post added: ${it.id}")
                showToast("Post added successfully")
                findNavController().navigateUp()
            }
            .addOnFailureListener {
                Log.e("AddPostFragment", "Post failed: ${it.message}")
                showToast("Error adding post")
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
