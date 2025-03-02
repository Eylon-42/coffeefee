package com.eaor.coffeefee.fragments

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

class ProfileFragment : Fragment() {
    private lateinit var bottomNav: View
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var editUserEmail: EditText
    private lateinit var userNameTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        // Initialize views
        bottomNav = requireActivity().findViewById(R.id.bottom_nav)
        editUserEmail = view.findViewById(R.id.editUserEmail)
        userNameTextView = view.findViewById(R.id.userName)
        
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
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Set email
            editUserEmail.setText(currentUser.email)

            // Get user name from Firestore
            db.collection("Users")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("name")
                        if (name != null) {
                            userNameTextView.text = name
                        } else {
                            userNameTextView.text = "User"
                        }
                    } else {
                        userNameTextView.text = "User"
                        Toast.makeText(context, "No user data found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    userNameTextView.text = "User"
                    Toast.makeText(
                        context,
                        "Error loading user data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } else {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure bottom nav is visible when leaving the fragment
        bottomNav.visibility = View.VISIBLE
    }
}