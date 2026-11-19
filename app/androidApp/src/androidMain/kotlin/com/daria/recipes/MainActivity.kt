package com.daria.recipes

import MainView
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import navigation.DeepLinks

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Only on a fresh start: after a rotation or process restore the intent is the same one
        // that already opened the recipe, and re-queuing it would push the recipe again.
        if (savedInstanceState == null) handleLink(intent)

        setContent {
            MainView()
        }
    }

    // A recipe link tapped while the app is already open (launchMode="singleTop").
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        val url = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        DeepLinks.handleUrl(url)
    }
}
