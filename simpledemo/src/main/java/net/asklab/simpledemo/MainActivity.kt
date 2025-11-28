package net.asklab.simpledemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import net.asklab.caption.CaptionBarUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        CaptionBarUtils.setWindowTitle(
            window = window,
            titleText = "Simple Demo App",
            captionColor = getColor(android.R.color.white),
            titleTextColor = getColor(android.R.color.black),
        )
    }
}
