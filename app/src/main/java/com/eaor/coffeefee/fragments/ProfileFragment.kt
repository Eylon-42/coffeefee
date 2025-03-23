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
import com.eaor.coffeefee.MainActivity
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.data.User
import com.eaor.coffeefee.repositories.UserRepository
import com.eaor.coffeefee.repositories.FeedRepository
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
import com.eaor.coffeefee.GlobalState
import android.content.SharedPreferences
import com.google.android.material.button.MaterialButton
import com.squareup.picasso.Callback
import java.net.URLDecoder

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
    private lateinit var removePhotoButton: MaterialButton
    private var selectedImageUri: Uri? = null
    private lateinit var storage: FirebaseStorage
    private lateinit var viewModel: UserViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private var isRemovingPhoto = false

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
            
            // Show the remove button since we now have a photo
            removePhotoButton.visibility = View.VISIBLE
            
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
        
        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        
        // Initialize shared preferences
        sharedPreferences = requireActivity().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        
        // Initialize database, repositories and ViewModel
        val appDb = AppDatabase.getDatabase(requireContext())
        userRepository = UserRepository(appDb.userDao(), db)
        val feedRepository = FeedRepository(appDb.feedItemDao(), db, userRepository)

        // Initialize ViewModel BEFORE setting up UI
        viewModel = ViewModelProvider(this)[UserViewModel::class.java]
        viewModel.setRepository(feedRepository)
        viewModel.setUserRepository(userRepository)
        
        // Initialize UI elements
        setupUI(view)
        
        // Observe the user data from the ViewModel
        setupObservers()
    }
    
    private fun setupUI(view: View) {
        bottomNav = requireActivity().findViewById(R.id.bottom_nav)
        editUserName = view.findViewById(R.id.editUserName)
        editUserEmail = view.findViewById(R.id.editUserEmail)
        profileImageView = view.findViewById(R.id.profileImageView)
        saveButton = view.findViewById(R.id.saveButton)
        progressBar = view.findViewById(R.id.progressBar)
        changePhotoText = view.findViewById(R.id.changePhotoText)
        removePhotoButton = view.findViewById(R.id.removePhotoButton)

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

        // Check for existing user data in arguments
        val args = arguments
        if (args != null && args.containsKey("userName") && args.containsKey("userEmail")) {
            // Use the data passed from UserProfileFragment
            val userName = args.getString("userName", "")
            val userEmail = args.getString("userEmail", "")
            val userPhotoUrl = args.getString("userPhotoUrl", "")
            
            // Populate UI with passed data
            editUserName.setText(userName)
            editUserEmail.setText(userEmail)
            
            // Store the photo URL in the ViewModel so loadProfilePicture can access it
            viewModel.updateUserDataValue(User(
                uid = auth.currentUser?.uid ?: "",
                name = userName,
                email = userEmail,
                profilePhotoUrl = userPhotoUrl
            ))
            
            // Load the profile picture with proper remove button visibility
            loadProfilePicture()
            
            Log.d("ProfileFragment", "Using prefilled user data: $userName, $userEmail, $userPhotoUrl")
        } else {
            // Load user data from repository if not passed in arguments
            val userId = auth.currentUser?.uid
            if (userId != null) {
                Log.d("ProfileFragment", "Loading user data from repository")
                viewModel.getUserData(userId)
            } else {
                Toast.makeText(context, "Error: Not logged in", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
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

                // Load and display the user's profile picture
                loadProfilePicture()
            }
        }
        
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
                
                // Set global refresh flags to ensure all views are updated
                GlobalState.shouldRefreshFeed = true
                GlobalState.shouldRefreshProfile = true
                
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

        // Add click listener for remove photo button
        removePhotoButton.setOnClickListener {
            // Set selectedImageUri to null to indicate photo removal
            selectedImageUri = null
            // Update UI to show default image
            profileImageView.setImageResource(R.drawable.default_avatar)
            // Hide remove button when default is shown
            removePhotoButton.visibility = View.GONE
            // Update the button text to reflect the removal
            changePhotoText.text = "Tap to add profile photo"
            // Set a flag to indicate we're removing the photo
            isRemovingPhoto = true
        }

        saveButton.setOnClickListener {
            saveUserData()
        }
    }

    private fun saveUserData() {
        val newName = editUserName.text.toString().trim()
        // Get current email from the field, but we won't actually change it
        val currentEmail = editUserEmail.text.toString().trim()
        
        Toast.makeText(context, "Saving profile...", Toast.LENGTH_SHORT).show()
        
        if (selectedImageUri != null) {
            // Upload new image and update user data
            uploadProfileImageWithUserData(selectedImageUri!!, newName, currentEmail)
        } else if (isRemovingPhoto) {
            // Remove profile photo and update user data
            viewModel.removeProfilePhoto(newName, currentEmail)
            // Reset flag
            isRemovingPhoto = false
        } else {
            // Just update user data without changing photo
            viewModel.updateUserData(newName, currentEmail, null)
        }
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
        
        // First check if user has existing profile image to delete
        val currentUserPhotoUrl = viewModel.userData.value?.profilePhotoUrl
        if (currentUserPhotoUrl != null && currentUserPhotoUrl.isNotEmpty() && !currentUserPhotoUrl.contains("default_avatar")) {
            try {
                // Delete old image from storage
                val storageRef = storage.reference
                
                // Handle both https and gs URLs
                val oldPhotoRef = if (currentUserPhotoUrl.startsWith("https://")) {
                    // Extract path from https URL
                    val path = currentUserPhotoUrl.substringAfter("o/").substringBefore("?")
                    // URL decode path
                    val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
                    storageRef.child(decodedPath)
                } else if (currentUserPhotoUrl.startsWith("gs://")) {
                    // Extract path from gs URL
                    val bucket = currentUserPhotoUrl.substringAfter("gs://").substringBefore("/")
                    val path = currentUserPhotoUrl.substringAfter(bucket + "/")
                    storage.getReferenceFromUrl(currentUserPhotoUrl)
                } else {
                    // Not a valid URL to delete
                    null
                }
                
                oldPhotoRef?.delete()?.addOnSuccessListener {
                    Log.d("ProfileFragment", "Successfully deleted old profile photo")
                }?.addOnFailureListener { e ->
                    Log.e("ProfileFragment", "Failed to delete old profile photo: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error during old photo deletion: ${e.message}")
            }
        }
        
        // Create a unique filename for the image
        val filename = "profile_${userId}_${System.currentTimeMillis()}.jpg"
        val storageRef = storage.reference.child("Users").child(userId).child(filename)
        
        // Set visibility
        progressBar.visibility = View.VISIBLE
        saveButton.isEnabled = false
        
        // Start the upload
        val uploadTask = storageRef.putFile(imageUri)
        
        // Monitor progress
        uploadTask.addOnProgressListener { taskSnapshot ->
            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
            progressBar.progress = progress
        }
        
        // Handle success
        uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                throw task.exception ?: Exception("Unknown error during upload")
            }
            storageRef.downloadUrl
        }.addOnSuccessListener { downloadUri ->
            // Now update the user data with the new photo URL
            viewModel.updateUserData(name, email, downloadUri.toString())
            saveButton.isEnabled = true
            progressBar.visibility = View.GONE
        }.addOnFailureListener { e ->
            // Handle failure
            progressBar.visibility = View.GONE
            saveButton.isEnabled = true
            Log.e("ProfileFragment", "Error uploading image: ${e.message}")
            Toast.makeText(context, "Failed to upload image: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure bottom nav is visible when leaving the fragment
        bottomNav.visibility = VISIBLE
        // No navigation handling needed
    }

    override fun onResume() {
        super.onResume()
        
        // Check if we should refresh the profile
        val shouldRefresh = GlobalState.shouldRefreshProfile ||
                           sharedPreferences.getBoolean("should_refresh_profile", false)
        
        if (shouldRefresh) {
            // Reset the flags
            GlobalState.shouldRefreshProfile = false
            sharedPreferences.edit().putBoolean("should_refresh_profile", false).apply()
            
            Log.d("ProfileFragment", "Refreshing profile data due to global flag")
            
            // Only reload user data, the user profile should update automatically
            val currentUser = auth.currentUser
            if (currentUser != null) {
                // Force refresh - but make sure we only pass parameters the method accepts
                viewModel.getUserData(currentUser.uid)
                
                // Invalidate profile photo cache if we have a URL
                val photoUrl = currentUser.photoUrl?.toString()
                if (photoUrl != null && photoUrl.isNotEmpty()) {
                    try {
                        Picasso.get().invalidate(photoUrl)
                        Log.d("ProfileFragment", "Invalidated cache for photo URL: $photoUrl")
                    } catch (e: Exception) {
                        Log.e("ProfileFragment", "Error invalidating Picasso cache: ${e.message}")
                    }
                }
            }
        }
    }

    private fun loadProfilePicture() {
        val userPhotoUrl = viewModel.userData.value?.profilePhotoUrl ?: ""
        Log.d("ProfileFragment", "Loading profile picture with URL: $userPhotoUrl")

        if (userPhotoUrl.isEmpty() || userPhotoUrl.endsWith("default_avatar")) {
            // If no photo URL or it's the default avatar, use default and hide remove button
            profileImageView.setImageResource(R.drawable.default_avatar)
            removePhotoButton.visibility = View.GONE
            changePhotoText.text = "Tap to add profile photo"
        } else {
            // Otherwise, load photo from URL and show remove button
            removePhotoButton.visibility = View.VISIBLE
            changePhotoText.text = "Tap to change photo"
            
            Picasso.get()
                .load(userPhotoUrl)
                .fit()
                .centerInside()
                .transform(CircleTransform())
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(profileImageView, object : Callback {
                    override fun onSuccess() {
                        // Photo loaded successfully
                        removePhotoButton.visibility = View.VISIBLE
                    }

                    override fun onError(e: Exception?) {
                        // Show error state - hide remove button if image failed to load
                        profileImageView.setImageResource(R.drawable.default_avatar)
                        removePhotoButton.visibility = View.GONE
                        changePhotoText.text = "Tap to add profile photo"
                        e?.let { Log.e("ProfileFragment", "Error loading profile image", it) }
                    }
                })
        }
    }
}