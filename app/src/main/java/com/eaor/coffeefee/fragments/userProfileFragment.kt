package com.eaor.coffeefee.fragments

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.AuthActivity
import com.eaor.coffeefee.R
import com.eaor.coffeefee.viewmodels.ProfileViewModel
import com.eaor.coffeefee.adapters.FeedAdapter
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.models.FeedItem
import com.eaor.coffeefee.repository.FeedRepository
import com.eaor.coffeefee.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.eaor.coffeefee.data.User
import com.squareup.picasso.Picasso
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.NetworkPolicy
import com.eaor.coffeefee.utils.CircleTransform

import com.eaor.coffeefee.repositories.CoffeeShopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.QuerySnapshot
import com.eaor.coffeefee.models.CoffeeShop
import com.google.firebase.firestore.Query
import android.view.Menu
import android.graphics.Typeface
import java.util.*
import java.text.SimpleDateFormat
import java.util.Locale

class UserProfileFragment : Fragment() {
    private lateinit var viewModel: ProfileViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userRepository: UserRepository
    private lateinit var feedRepository: FeedRepository
    private val repository = CoffeeShopRepository.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var recyclerView: RecyclerView
    private lateinit var feedAdapter: FeedAdapter
    private val userPosts = mutableListOf<FeedItem>()
    private lateinit var noPostsMessage: TextView
    private lateinit var userName: TextView
    private lateinit var userEmail: TextView
    private lateinit var userAvatar: ImageView
    private lateinit var editButton: ImageButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        
        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val userDao = AppDatabase.getDatabase(requireContext()).userDao()
        userRepository = UserRepository(userDao, db)
        
        // Initialize FeedRepository
        val feedItemDao = AppDatabase.getDatabase(requireContext()).feedItemDao()
        feedRepository = FeedRepository(feedItemDao, db)
        viewModel.setRepository(feedRepository)
        
        // Setup views
        setupViews(view)
    }
    
    private fun setupViews(view: View) {
        // Set up toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Profile"
        view.findViewById<ImageButton>(R.id.backButton).visibility = View.GONE

        // Initialize views
        userName = view.findViewById(R.id.userName)
        userEmail = view.findViewById(R.id.userEmail)
        userAvatar = view.findViewById(R.id.userAvatar)
        recyclerView = view.findViewById(R.id.postsRecyclerView)
        editButton = view.findViewById(R.id.editButton)
        noPostsMessage = view.findViewById(R.id.noPostsMessage)
        val joinDate = view.findViewById<TextView>(R.id.joinDate)
        
        // Set up edit button with dropdown menu
        editButton.setOnClickListener {
            showDropdownMenu(it)
        }
        
        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Initialize FeedAdapter
        feedAdapter = FeedAdapter(
            mutableListOf(),
            onMoreInfoClick = { post ->
                // Navigate to coffee shop details
                val bundle = Bundle().apply {
                    putString("name", post.location?.name ?: "Unknown Location")
                    putString("description", "No description available")
                    putFloat("latitude", post.location?.latitude?.toFloat() ?: 0f)
                    putFloat("longitude", post.location?.longitude?.toFloat() ?: 0f)
                    putString("photoUrl", post.photoUrl)
                    putString("postId", post.id)
                    
                    // Add source fragment ID
                    putInt("source_fragment_id", R.id.userProfileFragment)
                }
                findNavController().navigate(R.id.action_userProfileFragment_to_coffeeFragment, bundle)
            },
            onCommentClick = { post ->
                val bundle = Bundle().apply {
                    putString("postId", post.id)
                }
                findNavController().navigate(R.id.action_userProfileFragment_to_commentsFragment, bundle)
            },
            showOptionsMenu = true
        )
        
        // Add a post options menu click listener
        feedAdapter.setPostOptionsClickListener { view, position ->
            val currentPosts = viewModel.userPosts.value ?: listOf()
            if (position < currentPosts.size) {
                showPostOptionsMenu(view, currentPosts[position])
            }
        }
        
        recyclerView.adapter = feedAdapter
        
        // Set up observers
        setupObservers()
        
        // Load current user's data
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val user = userRepository.getUserData(currentUser.uid)
                    
                    if (user != null) {
                        // Set user data from repository
                        userName.text = user.name
                        userEmail.text = user.email
                        
                        // Set join date (using account creation time if available)
                        val joinDateString = if (currentUser.metadata != null) {
                            val creationTimestamp = currentUser.metadata?.creationTimestamp ?: 0
                            val date = Date(creationTimestamp)
                            val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                            dateFormat.format(date)
                        } else {
                            "Unknown"
                        }
                        joinDate.text = joinDateString
                        
                        // Load profile image using Picasso with no caching
                        if (!user.profilePhotoUrl.isNullOrEmpty()) {
                            Picasso.get()
                                .load(user.profilePhotoUrl)
                                .transform(CircleTransform())
                                .placeholder(R.drawable.default_avatar)
                                .error(R.drawable.default_avatar)
                                .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                                .networkPolicy(NetworkPolicy.NO_CACHE)
                                .into(userAvatar)
                        } else {
                            userAvatar.setImageResource(R.drawable.default_avatar)
                        }
                    } else {
                        // No user found, fallback to basic auth data
                        userName.text = currentUser.displayName ?: "User"
                        userEmail.text = currentUser.email ?: ""
                        
                        // Load default avatar using Picasso for consistency
                        Picasso.get()
                            .load(R.drawable.default_avatar)
                            .into(userAvatar)
                        
                        // Set a default join date
                        joinDate.text = "New member"
                    }
                    
                    // Load user posts
                    viewModel.loadUserPosts(currentUser.uid)
                } catch (e: Exception) {
                    Log.e("UserProfileFragment", "Error loading user data: ${e.message}")
                    Toast.makeText(context, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
                    
                    // Try to fetch posts even if user data fails
                    viewModel.loadUserPosts(currentUser.uid)
                }
            }
        }
    }
    
    private fun setupObservers() {
        // Observe posts
        viewModel.userPosts.observe(viewLifecycleOwner) { posts ->
            if (posts.isEmpty()) {
                noPostsMessage.visibility = View.VISIBLE
                noPostsMessage.text = "No posts available"
            } else {
                noPostsMessage.visibility = View.GONE
                feedAdapter.updateItems(posts.toMutableList())
            }
        }
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Show loading indicator if needed
        }
        
        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showPostOptionsMenu(view: View, post: FeedItem) {
        val popupMenu = PopupMenu(requireContext(), view)
        val inflater: MenuInflater = popupMenu.menuInflater
        inflater.inflate(R.menu.post_options_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit_post -> {
                    val bundle = Bundle().apply {
                        putString("postId", post.id)
                        putString("description", post.experienceDescription)
                        putString("photoUrl", post.photoUrl)
                        putString("locationName", post.location?.name)
                        putDouble("latitude", post.location?.latitude ?: 0.0)
                        putDouble("longitude", post.location?.longitude ?: 0.0)
                    }
                    findNavController().navigate(R.id.action_userProfileFragment_to_editPostFragment, bundle)
                    true
                }
                R.id.action_delete_post -> {
                    showDeleteConfirmationDialog(post.id)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }
    
    private fun showDeleteConfirmationDialog(postId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deletePost(postId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateEmptyStateVisibility(posts: List<FeedItem>) {
        if (posts.isEmpty()) {
            noPostsMessage.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            noPostsMessage.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // We don't need the toolbar menu, using our own button
        // setHasOptionsMenu(true)
    }

    private fun showDropdownMenu(view: View) {
        val popupMenu = PopupMenu(requireContext(), view)
        val inflater: MenuInflater = popupMenu.menuInflater
        inflater.inflate(R.menu.profile_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit_profile -> {
                    findNavController().navigate(R.id.action_userProfileFragment_to_profileFragment)
                    true
                }
                R.id.action_logout -> {
                    auth.signOut()
                    Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    val intent = Intent(requireContext(), AuthActivity::class.java)
                    startActivity(intent)
                    requireActivity().finish()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    override fun onResume() {
        super.onResume()
        
        // Refresh user data and posts when coming back to this fragment
        if (::viewModel.isInitialized) {
            Log.d("UserProfileFragment", "onResume: Refreshing user data and posts")
            
            // Reload user data
            val currentUser = auth.currentUser
            if (currentUser != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // Force a refresh from Firestore by setting forceRefresh=true
                        val user = userRepository.getUserData(currentUser.uid, forceRefresh = true)
                        
                        if (user != null) {
                            // Update UI with fresh user data
                            userName.text = user.name
                            userEmail.text = user.email
                            
                            // Update profile image if it exists - using Picasso with no caching
                            if (!user.profilePhotoUrl.isNullOrEmpty()) {
                                Picasso.get()
                                    .load(user.profilePhotoUrl)
                                    .transform(CircleTransform())
                                    .placeholder(R.drawable.default_avatar)
                                    .error(R.drawable.default_avatar)
                                    .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                                    .networkPolicy(NetworkPolicy.NO_CACHE)
                                    .into(userAvatar)
                            }
                            
                            // Directly update adapter with new user data
                            if (::feedAdapter.isInitialized) {
                                feedAdapter.updateUserData(
                                    userId = user.uid,
                                    userName = user.name,
                                    userPhotoUrl = user.profilePhotoUrl
                                )
                            }
                            
                            Log.d("UserProfileFragment", "Refreshed user data: ${user.name}, ${user.profilePhotoUrl}")
                        }
                    } catch (e: Exception) {
                        Log.e("UserProfileFragment", "Error refreshing user data: ${e.message}")
                    }
                }
            }
            
            // Refresh posts with the latest data
            viewModel.loadUserData()
            viewModel.loadUserPosts()
        }
    }
}
