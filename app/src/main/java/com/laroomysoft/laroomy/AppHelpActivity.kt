package com.laroomysoft.laroomy

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.transition.Visibility
import android.util.Log
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout

class AppHelpActivity : AppCompatActivity() {

    // connect device topic
    private lateinit var connectDeviceExpandImageView: AppCompatImageView
    private lateinit var connectDeviceContentContainer: ConstraintLayout
    private lateinit var deviceBindingExpandImageView: AppCompatImageView
    private lateinit var deviceBindingContentContainer: ConstraintLayout
    private lateinit var backButton: AppCompatImageButton


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_help)

        // add back button functionality
        this.backButton = findViewById(R.id.appHelpActivityBackButton)
        this.backButton.setOnClickListener {
            this.onBackPressed()
        }


        // connect device topic elements
        this.connectDeviceExpandImageView = findViewById(R.id.appHelpActivityConnectDeviceExpandImageView)
        this.connectDeviceContentContainer = findViewById(R.id.appHelpActivityConnectDeviceContentView)

        this.deviceBindingExpandImageView = findViewById(R.id.appHelpActivityDeviceBindingExpandImageView)
        this.deviceBindingContentContainer = findViewById(R.id.appHelpActivityDeviceBindingContentContainer)
    }

    fun onGotoBluetoothButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        startActivity(intent)
    }

    fun onTopicClick(view: View){

        when(view.id){
            R.id.appHelpActivityConnectDeviceContainer -> {
                when(connectDeviceContentContainer.visibility){
                    View.GONE -> {
                        connectDeviceContentContainer.visibility = View.VISIBLE
                        connectDeviceExpandImageView.setImageResource(R.drawable.ic_expand_arrow_up_sq32_vect)
                    } else -> {
                        connectDeviceContentContainer.visibility = View.GONE
                        connectDeviceExpandImageView.setImageResource(R.drawable.ic_expand_arrow_right_sq32_vect)
                    }
                }
            }
            R.id.appHelpActivityDeviceBindingContainer -> {
                when(deviceBindingContentContainer.visibility){
                    View.GONE -> {
                        deviceBindingContentContainer.visibility = View.VISIBLE
                        deviceBindingExpandImageView.setImageResource(R.drawable.ic_expand_arrow_up_sq32_vect)
                    } else -> {
                        deviceBindingContentContainer.visibility = View.GONE
                        deviceBindingExpandImageView.setImageResource(R.drawable.ic_expand_arrow_right_sq32_vect)
                    }
                }
            }
            else -> {
                // id not processed
                Log.w("M:HA:onTopicClick", "Warning: ID not processed - ID: ${view.id}")
            }
        }
    }
}