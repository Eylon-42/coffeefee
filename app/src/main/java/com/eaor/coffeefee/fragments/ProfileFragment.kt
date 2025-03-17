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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.ImageView
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.data.User
import com.eaor.coffeefee.repository.UserRepository
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
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(profileImageView)
            
            // Show a checkmark or indicator that a new image has been selected
            changePhotoText.text = "New photo selected"
            changePhotoText.setTextColor(ContextCompat.getColor(requireContext(), R.color.coffee_accent))
            
            // Hide the URL field since we're using the image picker
            view?.findViewById<EditText>(R.id.editUserPhotoUrl)?.visibility = GONE
            view?.findViewById<TextView>(R.id.photoUrlLabel)?.visibility = GONE
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize Firebase and Repository
        auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        val userDao = AppDatabase.getDatabase(requireContext()).userDao()
        userRepository = UserRepository(userDao, db)
        
        // Initialize views
        bottomNav = requireActivity().findViewById(R.id.bottom_nav)
        editUserName = view.findViewById(R.id.editUserName)
        editUserEmail = view.findViewById(R.id.editUserEmail)
        profileImageView = view.findViewById(R.id.profileImage)
        saveButton = view.findViewById(R.id.saveButton)
        progressBar = view.findViewById(R.id.progressBar)
        changePhotoText = view.findViewById(R.id.changePhotoText)
        val userNameDisplay = view.findViewById<TextView>(R.id.userNameDisplay)

        // Initially hide progress bar
        progressBar.visibility = GONE
        
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Edit Profile"
        
        // Setup back button
        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }

        // Load user data
        loadUserData(userNameDisplay)

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

    private fun loadUserData(userNameDisplay: TextView) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val user = userRepository.getUserData(currentUser.uid)
                    user?.let {
                        editUserName.setText(it.name)
                        editUserEmail.setText(it.email)
                        
                        // Hide URL fields since we're using image picker
                        view?.findViewById<EditText>(R.id.editUserPhotoUrl)?.visibility = GONE
                        view?.findViewById<TextView>(R.id.photoUrlLabel)?.visibility = GONE

                        // Set default text for change photo
                        changePhotoText.text = "Tap to change profile photo"
                        changePhotoText.setTextColor(ContextCompat.getColor(requireContext(), R.color.coffee_primary))
                        changePhotoText.visibility = VISIBLE

                        // Load profile picture if exists
                        if (!it.profilePictureUrl.isNullOrEmpty()) {
                            Picasso.get()
                                .load(it.profilePictureUrl)
                                .fit()
                                .centerInside()
                                .transform(CircleTransform())
                                .placeholder(R.drawable.ic_profile)
                                .error(R.drawable.ic_profile)
                                .into(profileImageView)
                        } else {
                            // Set default profile image
                            profileImageView.setImageResource(R.drawable.ic_profile)
                        }

                        userNameDisplay.text = it.name
                    }
                } catch (e: Exception) {
                    Log.e("ProfileFragment", "Error loading user data: ${e.message}")
                    Toast.makeText(context, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
                    
                    // Set default profile image in case of error
                    profileImageView.setImageResource(R.drawable.ic_profile)
                }
            }
        }
    }

    private fun saveUserData() {
        val currentUser = auth.currentUser ?: return
        val newName = editUserName.text.toString().trim()
        val newEmail = editUserEmail.text.toString().trim()

        if (newName.isBlank()) {
            editUserName.error = "Name cannot be empty"
            return
        }

        if (newEmail.isBlank()) {
            editUserEmail.error = "Email cannot be empty"
            return
        }

        // Disable save button and show progress
        saveButton.isEnabled = false
        progressBar.visibility = VISIBLE
        
        // Show loading message
        Toast.makeText(context, "Saving profile...", Toast.LENGTH_SHORT).show()

        // If a new image was selected, upload it to Firebase Storage
        if (selectedImageUri != null) {
            uploadImageToFirebaseStorage(currentUser.uid, selectedImageUri!!)
        } else {
            // No new image selected, just update the user data
            updateUserData(currentUser.uid, newName, newEmail, null)
        }
    }
    
    private fun uploadImageToFirebaseStorage(userId: String, imageUri: Uri) {
        try {
            // Compress the image before uploading
            val inputStream = requireContext().contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            
            // Apply circle transformation to the bitmap before uploading
            val circularBitmap = createCircularBitmap(originalBitmap)
            
            val baos = ByteArrayOutputStream()
            
            // Compress the circular image to JPEG with 80% quality
            circularBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val compressedData = baos.toByteArray()
            
            // Create a unique filename with timestamp
            val timestamp = System.currentTimeMillis()
            val filename = "profile_${timestamp}_${UUID.randomUUID()}.jpg"
            
            // Create a reference to 'Users/userId/profile_timestamp_uuid.jpg'
            val storageRef = storage.reference.child("Users").child(userId).child(filename)
            
            // Upload the compressed image
            val uploadTask = storageRef.putBytes(compressedData)
            
            // Register observers to listen for when the upload is done or if it fails
            uploadTask.addOnFailureListener { exception ->
                // Handle unsuccessful uploads
                Log.e("ProfileFragment", "Failed to upload image: ${exception.message}")
                Toast.makeText(context, "Failed to upload image: ${exception.message}", Toast.LENGTH_SHORT).show()
                saveButton.isEnabled = true
                progressBar.visibility = GONE
            }.addOnSuccessListener { taskSnapshot ->
                // Get the download URL
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    val imageUrl = uri.toString()
                    Log.d("ProfileFragment", "Image uploaded successfully. URL: $imageUrl")
                    
                    // Now update the user data with the new image URL
                    val newName = editUserName.text.toString().trim()
                    val newEmail = editUserEmail.text.toString().trim()
                    updateUserData(userId, newName, newEmail, imageUrl)
                }.addOnFailureListener { exception ->
                    Log.e("ProfileFragment", "Failed to get download URL: ${exception.message}")
                    Toast.makeText(context, "Failed to get download URL: ${exception.message}", Toast.LENGTH_SHORT).show()
                    saveButton.isEnabled = true
                    progressBar.visibility = GONE
                }
            }.addOnProgressListener { taskSnapshot ->
                // Show upload progress
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                progressBar.progress = progress
            }
            
            // Clean up bitmaps
            originalBitmap.recycle()
            circularBitmap.recycle()
            
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error processing image: ${e.message}")
            Toast.makeText(context, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
            saveButton.isEnabled = true
            progressBar.visibility = GONE
        }
    }
    
    // Helper method to create a circular bitmap
    private fun createCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        
        // Create a square bitmap from the source image
        val squaredBitmap = Bitmap.createBitmap(bitmap, x, y, size, size)
        
        // Create a new bitmap with transparent background
        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val circularBitmap = Bitmap.createBitmap(size, size, config)
        
        // Draw the circular shape
        val canvas = Canvas(circularBitmap)
        val paint = Paint()
        val shader = BitmapShader(squaredBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.shader = shader
        paint.isAntiAlias = true
        
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        
        // Clean up the squared bitmap if it's different from the source
        if (squaredBitmap != bitmap) {
            squaredBitmap.recycle()
        }
        
        return circularBitmap
    }
    
    private fun updateUserData(userId: String, name: String, email: String, profilePictureUrl: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get current user data to preserve any fields we're not updating
                val currentUser = userRepository.getUserData(userId)
                
                // Create updated user object
                val updatedUser = User(
                    uid = userId,
                    name = name,
                    email = email,
                    profilePictureUrl = profilePictureUrl ?: currentUser?.profilePictureUrl
                )

                // Update Firestore and Room
                val success = userRepository.updateUser(updatedUser)
                if (success) {
                    Log.d("ProfileFragment", "Profile updated successfully")
                    Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                } else {
                    Log.e("ProfileFragment", "Failed to update profile")
                    Toast.makeText(context, "Failed to update profile", Toast.LENGTH_SHORT).show()
                    saveButton.isEnabled = true
                    progressBar.visibility = GONE
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error updating profile: ${e.message}")
                Toast.makeText(context, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
                saveButton.isEnabled = true
                progressBar.visibility = GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure bottom nav is visible when leaving the fragment
        bottomNav.visibility = VISIBLE
    }
}