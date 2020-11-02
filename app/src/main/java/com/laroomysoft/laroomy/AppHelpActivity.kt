package com.laroomysoft.laroomy

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class AppHelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_help)


    }

    fun onGotoBluetoothButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        startActivity(intent)
    }
}