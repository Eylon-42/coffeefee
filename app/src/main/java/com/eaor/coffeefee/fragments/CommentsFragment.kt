package com.eaor.coffeefee.fragments

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.Toolbar
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.CommentsAdapter
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.models.Comment
import com.eaor.coffeefee.repositories.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.eaor.coffeefee.MainActivity
import android.app.AlertDialog
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.eaor.coffeefee.viewmodels.CommentsViewModel
import java.util.Collections
import android.content.Context
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.lifecycleScope
import com.eaor.coffeefee.utils.ItemSpacingDecoration
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.SetOptions
import androidx.navigation.fragment.navArgs

class CommentsFragment : Fragment() {
    private lateinit var commentsRecyclerView: RecyclerView
    private lateinit var commentEditText: EditText
    private lateinit var postCommentButton: ImageButton
    private lateinit var bottomNav: View
    private lateinit var commentsAdapter: CommentsAdapter
    private lateinit var commentsViewModel: CommentsViewModel
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository
    
    // BroadcastReceiver for profile updates
    private val profileUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.eaor.coffeefee.PROFILE_UPDATED") {
                val userId = intent.getStringExtra("userId")
                Log.d("CommentsFragment", "Received profile update broadcast for user: $userId")
                
                if (userId != null) {
                    // Refresh the comments to show updated user data
                    refreshUserDataInComments(userId)
                }
            }
        }
    }
    
    private var postId: String? = null
    private var postOwnerId: String? = null
    private var postTitle: String? = null
    
    // Add method to broadcast comment changes
    private fun broadcastCommentChange(action: String, postId: String, commentCount: Int) {
        try {
            val intent = Intent(action).apply {
                putExtra("postId", postId)
                putExtra("commentCount", commentCount)
            }
            requireContext().sendBroadcast(intent)
            Log.d("CommentsFragment", "Broadcast sent: $action for post $postId with count $commentCount")
        } catch (e: Exception) {
            Log.e("CommentsFragment", "Error broadcasting comment change: ${e.message}")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize ViewModel with factory that provides application context
        commentsViewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(requireActivity().application))[CommentsViewModel::class.java]
        
        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        // Initialize Room database
        val appDatabase = AppDatabase.getDatabase(requireContext())
        
        // Initialize repositories
        val userDao = appDatabase.userDao()
        userRepository = UserRepository(userDao, db)
        
        // Initialize CommentRepository - new addition for Room caching
        val commentDao = appDatabase.commentDao()
        val commentRepository = com.eaor.coffeefee.repositories.CommentRepository(
            commentDao,
            db,
            userRepository
        )
        
        // Get arguments from bundle
        arguments?.let {
            postId = it.getString("postId")
            postOwnerId = it.getString("postOwnerId")
            postTitle = it.getString("postTitle")
        }
        
        // Initialize ViewModel with post ID and repositories
        postId?.let { 
            commentsViewModel.initialize(it)
            commentsViewModel.initializeRepositories(commentRepository, userRepository)
        }
        
        // Register for profile update events with the required flag for Android 13+
        try {
            val intentFilter = IntentFilter("com.eaor.coffeefee.PROFILE_UPDATED")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(profileUpdateReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                requireContext().registerReceiver(profileUpdateReceiver, intentFilter)
            }
            Log.d("CommentsFragment", "Registered broadcast receiver for profile updates")
        } catch (e: Exception) {
            Log.e("CommentsFragment", "Error registering receiver: ${e.message}")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_comments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        setupObservers()
        
        // Setup profile change listener
        setupProfileChangeListener()
    }
    
    private fun setupViews(view: View) {
        commentsRecyclerView = view.findViewById(R.id.commentsRecyclerView)
        commentEditText = view.findViewById(R.id.commentEditText)
        postCommentButton = view.findViewById(R.id.postCommentButton)

        // Set up RecyclerView with fixed size for better performance
        commentsRecyclerView.layoutManager = LinearLayoutManager(context)
        commentsRecyclerView.setHasFixedSize(true)
        
        // Add spacing between comment items using our custom decoration
        val spacing = resources.getDimensionPixelSize(R.dimen.comment_spacing)
        commentsRecyclerView.addItemDecoration(ItemSpacingDecoration(spacing, true))

        Log.d("CommentsFragment", "Setting up CommentsAdapter")
        // Initialize adapter with mutable list
        commentsAdapter = CommentsAdapter(
            comments = mutableListOf(),
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            onCommentDelete = { comment -> confirmDeleteComment(comment.id) },
            onCommentEdit = { comment -> showEditCommentDialog(comment) }
        )
        commentsRecyclerView.adapter = commentsAdapter
        
        // Set large item view cache size to prevent recycling issues
        commentsRecyclerView.setItemViewCacheSize(50)
        
        // Prevent RecyclerView from losing its state
        commentsRecyclerView.isNestedScrollingEnabled = false

        // Setup toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // Set title safely handling null
        val title = if (postTitle.isNullOrEmpty()) "Comments" else "Comments on $postTitle"
        view.findViewById<TextView>(R.id.toolbarTitle).text = title

        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            // Set refresh flags before navigating back
            com.eaor.coffeefee.GlobalState.triggerRefreshAfterCommentChange()
            findNavController().navigateUp()
        }

        postCommentButton.setOnClickListener {
            val commentText = commentEditText.text.toString().trim()
            if (commentText.isNotEmpty()) {
                commentsViewModel.addComment(commentText)
                commentEditText.text.clear()
                hideKeyboard()
                // Set refresh flags after adding comment
                com.eaor.coffeefee.GlobalState.triggerRefreshAfterCommentChange()
            }
        }

        // Initialize bottom nav
        bottomNav = requireActivity().findViewById(R.id.bottom_nav)
        bottomNav.visibility = View.GONE

        // Set up keyboard visibility listener
        view.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            // Check if the fragment is attached to the activity
            if (isAdded) {
                bottomNav = requireActivity().findViewById(R.id.bottom_nav)
                bottomNav.visibility = if (keypadHeight > 150) View.GONE else View.VISIBLE
            }
        }
    }
    
    private fun setupObservers() {
        // Observe comments LiveData
        commentsViewModel.comments.observe(viewLifecycleOwner) { comments ->
            Log.d("CommentsFragment", "==== RECEIVED COMMENTS FROM VIEWMODEL ====")
            Log.d("CommentsFragment", "Received ${comments.size} comments from ViewModel")
            
            if (comments.isEmpty()) {
                Log.d("CommentsFragment", "Empty comments list received from ViewModel")
            } else {
                // Debug each comment
                comments.forEachIndexed { index, comment ->
                    Log.d("CommentsFragment", "Comment[$index]: id=${comment.id}, text='${comment.text}', user=${comment.userName}")
                }
            }
            
            // Update adapter with comments
            commentsAdapter.updateComments(comments.toMutableList())
            
            // Scroll to the most recent comment (usually at position 0 since sorted DESC)
            if (comments.isNotEmpty()) {
                commentsRecyclerView.scrollToPosition(0)
            }
            
            // Note: We don't broadcast from here anymore to avoid double broadcasting
            // Comment count updates are handled exclusively by the commentCount observer below
        }
        
        // Observe comment count separately to ensure broadcasts are sent only once per change
        commentsViewModel.commentCount.observe(viewLifecycleOwner) { count ->
            postId?.let {
                Log.d("CommentsFragment", "Broadcasting comment count change: $count for post $it")
                
                // Broadcast comment count changes
                broadcastCommentChange("com.eaor.coffeefee.COMMENT_UPDATED", it, count)
                
                // Also update MainActivity if available
                if (activity is MainActivity) {
                    (activity as MainActivity).updateCommentCount(it, count)
                }
            }
        }
        
        // Observe loading state
        commentsViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Show/hide loading indicator if needed
            Log.d("CommentsFragment", "Loading state changed: $isLoading")
        }
        
        // Observe error messages
        commentsViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Log.e("CommentsFragment", "Error from ViewModel: $it")
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        }
        
        // We're using LiveData throughout the app for reactive UI updates.
        // This pattern follows MVVM architecture principles where the UI observes
        // the ViewModel's LiveData and updates automatically when data changes.
        
        // Observe auth changes to refresh user data
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            auth.currentUser?.let { user ->
                Log.d("CommentsFragment", "Auth state changed, refreshing user: ${user.uid}")
                commentsAdapter.refreshUser(user.uid)
            }
        }
    }

    private fun setupProfileChangeListener() {
        // Listen for profile updates in UserRepository
        // This will be triggered whenever the current user changes their profile
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // Get initial user data
                    val user = userRepository.getUserData(currentUser.uid)
                    
                    // Observe auth state changes which may indicate profile updates
                    auth.addAuthStateListener { firebaseAuth ->
                        val updatedUser = firebaseAuth.currentUser
                        if (updatedUser != null && updatedUser.uid == currentUser.uid) {
                            // Check if profile info has changed
                            val newDisplayName = updatedUser.displayName
                            val newPhotoUrl = updatedUser.photoUrl?.toString()
                            
                            if (newDisplayName != user?.name || newPhotoUrl != user?.profilePhotoUrl) {
                                Log.d("CommentsFragment", "Detected profile change, refreshing comments")
                                commentsViewModel.refreshComments()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CommentsFragment", "Error setting up profile change listener: ${e.message}")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Unregister the broadcast receiver to prevent memory leaks
        try {
            requireContext().unregisterReceiver(profileUpdateReceiver)
            Log.d("CommentsFragment", "Unregistered broadcast receiver")
        } catch (e: Exception) {
            Log.e("CommentsFragment", "Error unregistering receiver: ${e.message}")
        }
        
        // Clean up any references
        if (::commentsAdapter.isInitialized) {
            // Clear any references in adapter
            commentsAdapter.updateComments(mutableListOf())
        }
    }
    
    private fun confirmDeleteComment(commentId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Comment")
            .setMessage("Are you sure you want to delete this comment?")
            .setPositiveButton("Delete") { _, _ ->
                commentsViewModel.deleteComment(commentId)
                // Set refresh flags after deleting comment
                com.eaor.coffeefee.GlobalState.triggerRefreshAfterCommentChange()
                // After deleting comment, broadcast will be sent through the comments observer
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showEditCommentDialog(comment: Comment) {
        try {
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_comment, null)
            
            val editText = dialogView.findViewById<EditText>(R.id.editCommentText)
            editText.setText(comment.text)
            
            // Request focus and show keyboard
            editText.requestFocus()
            
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Edit Comment")
                .setView(dialogView)
                .setPositiveButton("Save", null) // Set to null initially
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    hideKeyboard()
                }
                .create()
            
            // Show the dialog first
            dialog.show()
            
            // Then override the click listener to prevent automatic dismissal
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { 
                val newText = editText.text.toString().trim()
                if (newText.isEmpty()) {
                    Toast.makeText(context, "Comment cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    commentsViewModel.updateComment(comment.id, newText)
                    dialog.dismiss()
                    hideKeyboard()
                }
            }
            
            // Show keyboard
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            
        } catch (e: Exception) {
            Log.e("CommentsFragment", "Error showing edit dialog: ${e.message}")
            Toast.makeText(context, "Error opening edit dialog", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun hideKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        view?.let { imm?.hideSoftInputFromWindow(it.windowToken, 0) }
    }
    
    /**
     * Refresh comments when a user's profile data has changed
     */
    private fun refreshUserDataInComments(userId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get fresh user data from repository
                val updatedUser = userRepository.getUserData(userId, forceRefresh = true)
                
                if (updatedUser != null) {
                    Log.d("CommentsFragment", "Got updated user data: name=${updatedUser.name}, photo=${updatedUser.profilePhotoUrl}")
                    
                    // Update adapter with new user data
                    commentsAdapter.updateUserData(
                        userId = userId,
                        userName = updatedUser.name,
                        userPhotoUrl = updatedUser.profilePhotoUrl
                    )
                }
                
                // Also tell the ViewModel to refresh comments with latest user data
                commentsViewModel.refreshComments()
            } catch (e: Exception) {
                Log.e("CommentsFragment", "Error refreshing user data: ${e.message}")
            }
        }
    }
} 