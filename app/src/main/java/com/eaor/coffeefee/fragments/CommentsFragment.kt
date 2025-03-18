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
import com.eaor.coffeefee.models.Comment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.eaor.coffeefee.MainActivity
import android.app.AlertDialog
import android.widget.Toast
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.SetOptions

class CommentsFragment : Fragment() {
    private lateinit var commentsRecyclerView: RecyclerView
    private lateinit var commentEditText: EditText
    private lateinit var postCommentButton: ImageButton
    private val commentsList = mutableListOf<Comment>()
    private lateinit var bottomNav: View
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var postId: String? = null
    private var editingComment: Comment? = null
    private lateinit var commentsAdapter: CommentsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        postId = arguments?.getString("postId")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_comments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup views
        setupViews(view)
        // Start listening for comments immediately
        loadComments()
    }

    private fun setupViews(view: View) {
        commentsRecyclerView = view.findViewById(R.id.commentsRecyclerView)
        commentEditText = view.findViewById(R.id.commentEditText)
        postCommentButton = view.findViewById(R.id.postCommentButton)

        commentsRecyclerView.layoutManager = LinearLayoutManager(context)

        // Initialize adapter with mutable list
        commentsAdapter = CommentsAdapter(
            comments = mutableListOf(), // Change to empty mutable list
            currentUserId = auth.currentUser?.uid ?: "",
            onCommentDelete = { comment -> deleteComment(comment) },
            onCommentEdit = { comment -> showEditCommentDialog(comment) }
        )
        commentsRecyclerView.adapter = commentsAdapter

        // Setup toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Comments"

        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }

        postCommentButton.setOnClickListener {
            val commentText = commentEditText.text.toString().trim()
            if (commentText.isNotEmpty()) {
                if (editingComment != null) {
                    updateComment(editingComment!!, commentText)
                } else {
                    postComment(commentText)
                }
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

        // Add this line to prevent recycling issues
        commentsRecyclerView.setItemViewCacheSize(20)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure bottom nav is visible when leaving the fragment
        if (isAdded) {
            bottomNav.visibility = View.VISIBLE
        }
    }

    private fun loadComments() {
        postId?.let { id ->
            db.collection("Comments")
                .whereEqualTo("postId", id)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("CommentsFragment", "Error listening for comments: ${error.message}")
                        return@addSnapshotListener
                    }

                    val comments = snapshot?.documents?.mapNotNull { doc ->
                        Comment(
                            id = doc.id,
                            postId = doc.getString("postId") ?: return@mapNotNull null,
                            userId = doc.getString("userId") ?: return@mapNotNull null,
                            text = doc.getString("text") ?: return@mapNotNull null,
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    } ?: emptyList()

                    Log.d("CommentsFragment", "Loaded ${comments.size} comments")
                    updateCommentCount(comments.size)
                    commentsAdapter.updateComments(comments)
                }
        }
    }

    private fun updateCommentCount(count: Int) {
        postId?.let { id ->
            val postRef = db.collection("Posts").document(id)
            postRef.set(mapOf("commentCount" to count), SetOptions.merge())
                .addOnSuccessListener {
                    if (isAdded) {
                        (requireActivity() as MainActivity).updateFeedCommentCount(postId!!, count)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CommentsFragment", "Error updating comment count: ${e.message}")
                }
        }
    }

    private fun postComment(commentText: String) {
        if (commentText.trim().isEmpty()) return
        
        val currentUser = auth.currentUser ?: return
        postId?.let { id ->
            val newComment = Comment(
                id = db.collection("Comments").document().id,
                postId = id,
                userId = currentUser.uid,
                text = commentText.trim(),
                timestamp = System.currentTimeMillis()
            )

            db.collection("Comments")
                .document(newComment.id)
                .set(newComment.toMap())
                .addOnSuccessListener {
                    commentEditText.text.clear()
                    loadComments()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error posting comment: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun deleteComment(comment: Comment) {
        db.collection("Comments").document(comment.id)
            .delete()
            .addOnSuccessListener {
                loadComments()
                Toast.makeText(context, "Comment deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error deleting comment: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CommentsFragment", "Error deleting comment: ${e.message}")
            }
    }

    private fun showEditCommentDialog(comment: Comment) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_comment, null)
        
        val editText = dialogView.findViewById<EditText>(R.id.editCommentText)
        editText.setText(comment.text)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Comment")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val newText = editText.text.toString().trim()
                if (newText.isNotEmpty()) {
                    updateComment(comment, newText)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun updateComment(comment: Comment, newText: String) {
        db.collection("Comments").document(comment.id)
            .update("text", newText)
            .addOnSuccessListener {
                loadComments()
                Toast.makeText(context, "Comment updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error updating comment: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
} 