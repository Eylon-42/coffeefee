package com.eaor.coffeefee.fragments

import android.graphics.Rect
import android.os.Bundle
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

class CommentsFragment : Fragment() {
    private lateinit var commentsRecyclerView: RecyclerView
    private lateinit var commentEditText: EditText
    private lateinit var postCommentButton: ImageButton
    private val commentsList = mutableListOf<Comment>()
    private lateinit var bottomNav: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_comments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Comments"

        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }

        commentsRecyclerView = view.findViewById(R.id.commentsRecyclerView)
        commentEditText = view.findViewById(R.id.commentEditText)
        postCommentButton = view.findViewById(R.id.postCommentButton)

        commentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapter = CommentsAdapter(commentsList)
        commentsRecyclerView.adapter = adapter

        postCommentButton.setOnClickListener {
            val commentText = commentEditText.text.toString()
            if (commentText.isNotEmpty()) {
                postComment(commentText)
            }
        }

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

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure bottom nav is visible when leaving the fragment
        if (isAdded) {
            bottomNav.visibility = View.VISIBLE
        }
    }

    private fun postComment(commentText: String) {
        val newComment = Comment(
            id = generateUniqueId(),
            postId = "1", // Example post ID
            userName = "Name", // Placeholder user name
            text = commentText,
            timestamp = System.currentTimeMillis()
        )
        commentsList.add(newComment)
        commentsRecyclerView.adapter?.notifyDataSetChanged()
        commentEditText.text.clear()
    }

    private fun generateUniqueId(): String {
        return System.currentTimeMillis().toString()
    }
} 