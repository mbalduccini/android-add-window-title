package com.example.titledemo

import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import com.example.captionbardemo.caption.CaptionBarUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val binding = CaptionBarUtils.setWindowTitle(
            window = window,
            titleText = "Original Title",
            captionColor = getColor(android.R.color.white),
            titleTextColor = getColor(android.R.color.black),
        )

        findViewById<Button>(R.id.change_title_button).setOnClickListener {
            binding.setTitle("Modified Title")
        }
    }
}
