package com.eaor.coffeefee.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.eaor.coffeefee.R
import android.widget.ImageButton
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.ImageView
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.eaor.coffeefee.MainActivity
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.data.User
import com.eaor.coffeefee.repository.UserRepository
import com.eaor.coffeefee.viewmodels.UserViewModel
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso
import java.util.UUID
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import com.eaor.coffeefee.utils.CircleTransform
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.BitmapShader
import android.content.Intent
import android.content.Context
import com.eaor.coffeefee.repository.FeedRepository

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private lateinit var bottomNav: View
    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository
    private lateinit var editUserName: EditText
    private lateinit var editUserEmail: EditText
    private lateinit var profileImageView: ImageView
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var changePhotoText: TextView
    private var selectedImageUri: Uri? = null
    private lateinit var storage: FirebaseStorage
    private lateinit var viewModel: UserViewModel

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            // Use Picasso to load and properly fit the image
            Picasso.get()
                .load(it)
                .fit()
                .centerInside()
                .transform(CircleTransform())
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(profileImageView)
            
            // Show a checkmark or indicator that a new image has been selected
            changePhotoText.text = "New photo selected"
            changePhotoText.setTextColor(ContextCompat.getColor(requireContext(), R.color.coffee_primary))
            
            // Hide the URL field but keep the email note visible
            view?.findViewById<EditText>(R.id.editUserPhotoUrl)?.visibility = GONE
            
            // Hide the email note
            view?.findViewById<TextView>(R.id.photoUrlLabel)?.apply {
                visibility = GONE
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize Firebase and Repository
        auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        val appDatabase = AppDatabase.getDatabase(requireContext())
        val userDao = appDatabase.userDao()
        val feedItemDao = appDatabase.feedItemDao()
        userRepository = UserRepository(userDao, db)
        val feedRepository = FeedRepository(feedItemDao, db)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[UserViewModel::class.java]
        viewModel.initialize(userRepository, feedRepository)
        
        // Initialize views
        bottomNav = requireActivity().findViewById(R.id.bottom_nav)
        editUserName = view.findViewById(R.id.editUserName)
        editUserEmail = view.findViewById(R.id.editUserEmail)
        profileImageView = view.findViewById(R.id.profileImageView)
        saveButton = view.findViewById(R.id.saveButton)
        progressBar = view.findViewById(R.id.progressBar)
        changePhotoText = view.findViewById(R.id.changePhotoText)

        // Initially hide progress bar
        progressBar.visibility = GONE
        
        // Disable email field as we're not allowing email changes
        editUserEmail.isEnabled = false
        editUserEmail.alpha = 0.5f
        
        // Remove the note about email changes not being allowed
        view.findViewById<TextView>(R.id.photoUrlLabel)?.apply {
            visibility = GONE
        }

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Edit Profile"
        
        // Setup back button
        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }

        // Set up observers
        setupObservers()
        
        // Load user data
        val userId = auth.currentUser?.uid
        if (userId != null) {
            viewModel.getUserData(userId)
        } else {
            Toast.makeText(context, "Error: Not logged in", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }

        // Set up keyboard visibility listener
        view.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            // If keyboard is showing (height > 150), hide bottom nav
            bottomNav.visibility = if (keypadHeight > 150) GONE else VISIBLE
        }

        setupClickListeners()
    }
    
    private fun setupObservers() {
        // Observe user data using LiveData following MVVM pattern
        // The UI reactively updates when the ViewModel's LiveData changes
        viewModel.userData.observe(viewLifecycleOwner) { user ->
            user?.let {
                Log.d("ProfileFragment", "Received user data update from LiveData: ${it.name}")
                editUserName.setText(it.name)
                editUserEmail.setText(it.email)
                
                // Keep the photo URL field hidden
                view?.findViewById<EditText>(R.id.editUserPhotoUrl)?.visibility = GONE
                
                // Hide the email note
                view?.findViewById<TextView>(R.id.photoUrlLabel)?.apply {
                    visibility = GONE
                }

                // Set default text for change photo
                changePhotoText.text = "Tap to change profile photo"
                changePhotoText.setTextColor(ContextCompat.getColor(requireContext(), R.color.coffee_primary))
                changePhotoText.visibility = VISIBLE

                // Load profile picture if exists
                if (!it.profilePhotoUrl.isNullOrEmpty()) {
                    Picasso.get()
                        .load(it.profilePhotoUrl)
                        .fit()
                        .centerInside()
                        .transform(CircleTransform())
                        .placeholder(R.drawable.default_avatar)
                        .error(R.drawable.default_avatar)
                        .into(profileImageView)
                } else {
                    // User has no profile image, set default
                    profileImageView.setImageResource(R.drawable.default_avatar)
                }
            }
        }
        
        // Observe other LiveData from the ViewModel
        // This is a key part of the MVVM architecture where the UI observes data changes
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) VISIBLE else GONE
            saveButton.isEnabled = !isLoading
            
            Log.d("ProfileFragment", "Loading state changed: $isLoading")
        }
        
        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        
        // Observe upload progress
        viewModel.uploadProgress.observe(viewLifecycleOwner) { progress ->
            progressBar.progress = progress
        }
        
        // Observe update success
        viewModel.updateSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    private fun setupClickListeners() {
        // Make it clear that the profile image is clickable
        profileImageView.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
        
        // Add click listener to the change photo text as well
        changePhotoText.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        saveButton.setOnClickListener {
            saveUserData()
        }
    }

    private fun saveUserData() {
        val newName = editUserName.text.toString().trim()
        // Get current email from the field, but we won't actually change it
        val currentEmail = editUserEmail.text.toString().trim()

        Log.d("ProfileFragment", "Saving user data: name=$newName")

        if (newName.isBlank()) {
            editUserName.error = "Name cannot be empty"
            return
        }

        // Show loading message
        Toast.makeText(context, "Saving profile...", Toast.LENGTH_SHORT).show()

        // If a new image was selected, upload it first
        if (selectedImageUri != null) {
            Log.d("ProfileFragment", "Uploading new profile image")
            
            // Hide the URL field
            view?.findViewById<EditText>(R.id.editUserPhotoUrl)?.visibility = GONE
            
            // Use modified method that passes name and current email
            selectedImageUri?.let { uri ->
                uploadProfileImageWithUserData(uri, newName, currentEmail)
            }
        } else {
            Log.d("ProfileFragment", "Updating user data without new image")
            // No new image, just update the name (email will remain unchanged in ViewModel)
            viewModel.updateUserData(newName, currentEmail)
        }
        
        // Notify all listening ViewModels that profile has been updated
        // This helps ensure all UI components showing user data get refreshed
        notifyProfileUpdated()
    }

    /**
     * Notify the app that the user profile has been updated
     * This will help refresh comments and other UI elements showing user data
     */
    private fun notifyProfileUpdated() {
        try {
            // This will broadcast a message that can be observed by other components
            val intent = Intent("com.eaor.coffeefee.PROFILE_UPDATED")
            intent.putExtra("userId", auth.currentUser?.uid)
            
            // Use the appropriate API based on Android version
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // For Android 13+, specify broadcast flags
                requireContext().sendBroadcast(intent, null)
            } else {
                // For older Android versions
                requireContext().sendBroadcast(intent)
            }
            
            Log.d("ProfileFragment", "Broadcast profile updated event")
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error broadcasting profile update: ${e.message}")
        }
    }

    // Helper method to handle image upload with name and email
    private fun uploadProfileImageWithUserData(imageUri: Uri, name: String, email: String) {
        val userId = auth.currentUser?.uid ?: return
        
        // Show progress indicators
        progressBar.visibility = VISIBLE
        saveButton.isEnabled = false
        
        // Create a unique filename
        val timestamp = System.currentTimeMillis()
        val filename = "profile_${timestamp}_${UUID.randomUUID()}.jpg"
        
        // Get a reference to Firebase Storage
        val storageRef = storage.reference.child("Users").child(userId).child(filename)
        
        // Start the upload
        val uploadTask = storageRef.putFile(imageUri)
        
        // Monitor progress
        uploadTask.addOnProgressListener { taskSnapshot ->
            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
            progressBar.progress = progress
        }
        
        // Handle completion
        uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                throw task.exception ?: Exception("Unknown error during upload")
            }
            storageRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val downloadUrl = task.result.toString()
                Log.d("ProfileFragment", "Image uploaded successfully. URL: $downloadUrl")
                
                // Now update the user data with all fields
                viewModel.updateUserData(name, email, downloadUrl)
            } else {
                Log.e("ProfileFragment", "Upload failed: ${task.exception?.message}")
                Toast.makeText(context, "Failed to upload image: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                saveButton.isEnabled = true
            }
        }.addOnFailureListener { e ->
            Log.e("ProfileFragment", "Upload failed: ${e.message}")
            Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            saveButton.isEnabled = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure bottom nav is visible when leaving the fragment
        bottomNav.visibility = VISIBLE
        // No navigation handling needed
    }
}