package com.laroomysoft.laroomy

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout

class AppHelpActivity : AppCompatActivity() {

    // connect device topic

    private lateinit var theConceptShortExplainedContentContainer: ConstraintLayout
    private lateinit var theConceptShortExplainedImageView: AppCompatImageView

    private lateinit var connectDeviceContentContainer: ConstraintLayout
    private lateinit var connectDeviceExpandImageView: AppCompatImageView

    private lateinit var deviceBindingContentContainer: ConstraintLayout
    private lateinit var deviceBindingExpandImageView: AppCompatImageView

    private lateinit var connectionProcessContentContainer: ConstraintLayout
    private lateinit var connectionProcessImageView: AppCompatImageView

    private lateinit var uuidProfileContentContainer: ConstraintLayout
    private lateinit var uuidProfileImageView: AppCompatImageView

    private lateinit var appResetContentContainer: ConstraintLayout
    private lateinit var appResetImageView: AppCompatImageView

    private lateinit var backButton: AppCompatImageButton


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_help)
    
        // register onBackPressed event
        this.onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })
    
        // add back button functionality
        this.backButton = findViewById<AppCompatImageButton?>(R.id.appHelpActivityBackButton).apply {
            setOnClickListener {
                handleBackEvent()
            }
        }

        // connect device topic elements

        this.theConceptShortExplainedContentContainer = findViewById(R.id.appHelpActivityTheConceptShortExplainedContentContainer)
        this.theConceptShortExplainedImageView = findViewById(R.id.appHelpActivityTheConceptShortExplainedImageView)

        this.connectDeviceExpandImageView = findViewById(R.id.appHelpActivityConnectDeviceExpandImageView)
        this.connectDeviceContentContainer = findViewById(R.id.appHelpActivityConnectDeviceContentContainer)

        this.deviceBindingExpandImageView = findViewById(R.id.appHelpActivityDeviceBindingExpandImageView)
        this.deviceBindingContentContainer = findViewById(R.id.appHelpActivityDeviceBindingContentContainer)

        this.connectionProcessContentContainer = findViewById(R.id.appHelpActivityConnectionProcessContentContainer)
        this.connectionProcessImageView = findViewById(R.id.appHelpActivityConnectionProcessImageView)

        this.uuidProfileContentContainer = findViewById(R.id.appHelpActivityUUIDProfilesContentContainer)
        this.uuidProfileImageView = findViewById(R.id.appHelpActivityUUIDProfilesImageView)

        this.appResetContentContainer = findViewById(R.id.appHelpActivityAppResetContentContainer)
        this.appResetImageView = findViewById(R.id.appHelpActivityAppResetImageView)
    }
    
    private fun handleBackEvent(){
        finish()
    }
    
    fun onTopicClick(view: View){

        when(view.id){
            R.id.appHelpActivityTheConceptShortExplainedContainer -> {
                when(theConceptShortExplainedContentContainer.visibility){
                    View.GONE -> {
                        theConceptShortExplainedContentContainer.visibility = View.VISIBLE
                        theConceptShortExplainedImageView.setImageResource(R.drawable.ic_expand_arrow_up_sq32_vect)
                    }
                    else -> {
                        theConceptShortExplainedContentContainer.visibility = View.GONE
                        theConceptShortExplainedImageView.setImageResource(R.drawable.ic_expand_arrow_right_sq32_vect)
                    }
                }
            }
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
            R.id.appHelpActivityConnectionProcessContainer -> {
                when(connectionProcessContentContainer.visibility){
                    View.GONE -> {
                        connectionProcessContentContainer.visibility = View.VISIBLE
                        connectionProcessImageView.setImageResource(R.drawable.ic_expand_arrow_up_sq32_vect)
                    }
                    else -> {
                        connectionProcessContentContainer.visibility = View.GONE
                        connectionProcessImageView.setImageResource(R.drawable.ic_expand_arrow_right_sq32_vect)
                    }
                }
            }
            R.id.appHelpActivityUUIDProfilesContainer -> {
                when(uuidProfileContentContainer.visibility){
                    View.GONE -> {
                        uuidProfileContentContainer.visibility = View.VISIBLE
                        uuidProfileImageView.setImageResource(R.drawable.ic_expand_arrow_up_sq32_vect)
                    }
                    else -> {
                        uuidProfileContentContainer.visibility = View.GONE
                        uuidProfileImageView.setImageResource(R.drawable.ic_expand_arrow_right_sq32_vect)
                    }
                }
            }
            R.id.appHelpActivityAppResetContainer -> {
                when(appResetContentContainer.visibility){
                    View.GONE -> {
                        appResetContentContainer.visibility = View.VISIBLE
                        appResetImageView.setImageResource(R.drawable.ic_expand_arrow_up_sq32_vect)
                    }
                    else -> {
                        appResetContentContainer.visibility = View.GONE
                        appResetImageView.setImageResource(R.drawable.ic_expand_arrow_right_sq32_vect)
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