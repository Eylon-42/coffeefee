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
import com.squareup.picasso.Picasso

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private lateinit var bottomNav: View
    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository
    private lateinit var editUserName: EditText
    private lateinit var editUserEmail: EditText
    private lateinit var editUserPhotoUrl: EditText // New EditText for photo URL
    private lateinit var profileImageView: ImageView
    private lateinit var saveButton: Button
    private var selectedImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            profileImageView.setImageURI(it)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize Firebase and Repository
        auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val userDao = AppDatabase.getDatabase(requireContext()).userDao()
        userRepository = UserRepository(userDao, db)
        
        // Initialize views
        bottomNav = requireActivity().findViewById(R.id.bottom_nav)
        editUserName = view.findViewById(R.id.editUserName)
        editUserEmail = view.findViewById(R.id.editUserEmail)
        profileImageView = view.findViewById(R.id.profileImage)
        editUserPhotoUrl = view.findViewById(R.id.editUserPhotoUrl)
        saveButton = view.findViewById(R.id.saveButton)
        
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Edit Profile"
        
        // Setup back button
        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }

        // Load user data
        loadUserData()

        // Set up keyboard visibility listener
        view.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            // If keyboard is showing (height > 150), hide bottom nav
            bottomNav.visibility = if (keypadHeight > 150) View.GONE else View.VISIBLE
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        profileImageView.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        saveButton.setOnClickListener {
            saveUserData()
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val user = userRepository.getUserData(currentUser.uid)
                    user?.let {
                        editUserName.setText(it.name)
                        editUserEmail.setText(it.email)
                        editUserPhotoUrl.setText(it.profilePictureUrl)

                        // Load profile picture if exists
                        it.profilePictureUrl?.let { url ->
                            Picasso.get()
                                .load(url)
                                .placeholder(R.drawable.ic_profile) // Placeholder image
                                .error(R.drawable.ic_error) // Error image
                                .into(profileImageView)
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveUserData() {
        val currentUser = auth.currentUser ?: return
        val newName = editUserName.text.toString()
        val newEmail = editUserEmail.text.toString()
        val photoUrl = view?.findViewById<EditText>(R.id.editUserPhotoUrl)?.text.toString()

        if (newName.isBlank()) {
            editUserName.error = "Name cannot be empty"
            return
        }

        saveButton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Create updated user object
                val updatedUser = User(
                    uid = currentUser.uid,
                    name = newName,
                    email = newEmail,
                    profilePictureUrl = photoUrl // Store the URL here
                )

                // Update Firestore and Room
                val success = userRepository.updateUser(updatedUser)
                if (success) {
                    Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    requireActivity().onBackPressed()
                } else {
                    Toast.makeText(context, "Failed to update profile", Toast.LENGTH_SHORT).show()
                    saveButton.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
                saveButton.isEnabled = true
            }
        }
    }

    private fun convertImageToBase64(uri: Uri): String {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream) // Adjust quality as needed
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure bottom nav is visible when leaving the fragment
        bottomNav.visibility = View.VISIBLE
    }
}