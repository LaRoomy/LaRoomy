package com.laroomysoft.laroomy

import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout

class InformationActivity : AppCompatActivity() {

    //lateinit var versionTextView: AppCompatTextView

    private lateinit var privacyProtectionSubContainer: ConstraintLayout
    private lateinit var termsOfServiceSubContainer: ConstraintLayout
    private lateinit var creditsSubContainer: ConstraintLayout
    private lateinit var docuAndResourcesSubContainer: ConstraintLayout
    private lateinit var imprintAndContactSubContainer: ConstraintLayout
    private lateinit var disclaimerSubContainer: ConstraintLayout
    private lateinit var webSiteLinkTextView: AppCompatTextView
    private lateinit var apiSiteLinkTextView: AppCompatTextView

    lateinit var backButton: AppCompatImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_information)

        this.privacyProtectionSubContainer = findViewById(R.id.infoActivityPrivacyPolicySubContainer)
        this.termsOfServiceSubContainer = findViewById(R.id.infoActivityTermsOfServiceSubContainer)
        this.creditsSubContainer = findViewById(R.id.infoActivityCreditsSubContainer)
        this.docuAndResourcesSubContainer = findViewById(R.id.infoActivityDocuAndResourcesSubContainer)
        this.imprintAndContactSubContainer = findViewById(R.id.infoActivityImprintAndContactSubContainer)
        this.disclaimerSubContainer = findViewById(R.id.infoActivityDisclaimerSubContainer)

        // add back button functionality
        this.backButton = findViewById<AppCompatImageButton?>(R.id.infoActivityBackButton).apply {
            setOnClickListener {
                finish()
            }
        }
        
        // add website link functionality
        this.webSiteLinkTextView = findViewById<AppCompatTextView?>(R.id.infoActivityDocuWebsiteLinkTextView).apply {
            setOnClickListener {
                val openUrl = Intent(ACTION_VIEW)
                openUrl.data = Uri.parse("https://www.laroomy.com/")
                startActivity(openUrl)
            }
        }
        
        // add api website link functionality
        this.apiSiteLinkTextView = findViewById<AppCompatTextView?>(R.id.infoActivityDocuApiLinkTextView).apply {
            setOnClickListener {
                val openUrl = Intent(ACTION_VIEW)
                openUrl.data = Uri.parse("https://api.laroomy.com/")
                startActivity(openUrl)
            }
        }
    }

    fun onMailToLinkClick(@Suppress("UNUSED_PARAMETER") view: View){
        val openUrl = Intent(ACTION_VIEW)
        openUrl.data = Uri.parse("mailto:info@laroomy.de")
        startActivity(openUrl)
    }

    fun onGooglePlayServiceLinkClick(@Suppress("UNUSED_PARAMETER") view: View){
        val openUrl = Intent(ACTION_VIEW)
        openUrl.data = Uri.parse("https://policies.google.com/terms")
        startActivity(openUrl)
    }

    fun onPrivacyProtectionFieldClick(@Suppress("UNUSED_PARAMETER")view: View) {
        when {
            (this.privacyProtectionSubContainer.visibility == View.VISIBLE) -> {
                this.privacyProtectionSubContainer.visibility = View.GONE
            }
            else -> {
                this.privacyProtectionSubContainer.visibility = View.VISIBLE
            }
        }
    }

    fun onTermsOfServiceFieldClick(@Suppress("UNUSED_PARAMETER")view: View) {
        when{
            (this.termsOfServiceSubContainer.visibility == View.VISIBLE) -> {
                this.termsOfServiceSubContainer.visibility = View.GONE
            }
            else -> {
                this.termsOfServiceSubContainer.visibility = View.VISIBLE
            }
        }
    }

    fun onCreditsFieldClick(@Suppress("UNUSED_PARAMETER")view: View) {
        when(this.creditsSubContainer.visibility){
            View.GONE -> {
                this.creditsSubContainer.visibility = View.VISIBLE
            }
            else -> {
                this.creditsSubContainer.visibility = View.GONE
            }
        }
    }

    fun onYBQLinkClick(@Suppress("UNUSED_PARAMETER")view: View) {
        val openUrl = Intent(ACTION_VIEW)
        openUrl.data = Uri.parse("https://github.com/ybq/Android-SpinKit")
        startActivity(openUrl)

    }

    fun onQuadFlaskLinkClick(@Suppress("UNUSED_PARAMETER")view: View) {
        val openUrl = Intent(ACTION_VIEW)
        openUrl.data = Uri.parse("https://github.com/QuadFlask/colorpicker")
        startActivity(openUrl)
    }

    fun onRamotionSourceLinkClick(@Suppress("UNUSED_PARAMETER")view: View) {
        val openUrl = Intent(ACTION_VIEW)
        openUrl.data = Uri.parse("https://github.com/Ramotion/fluid-slider-android")
        startActivity(openUrl)
    }

    fun onRamotionWebsiteLinkClick(@Suppress("UNUSED_PARAMETER")view: View) {
        val openUrl = Intent(ACTION_VIEW)
        openUrl.data = Uri.parse("https://www.ramotion.com/")
        startActivity(openUrl)
    }

    fun onDocuAndResourcesFieldClick(@Suppress("UNUSED_PARAMETER")view: View) {
        when(this.docuAndResourcesSubContainer.visibility){
            View.GONE -> {
                this.docuAndResourcesSubContainer.visibility = View.VISIBLE
            }
            else -> {
                this.docuAndResourcesSubContainer.visibility = View.GONE
            }
        }
    }

    fun onImprintAndContactFieldClick(@Suppress("UNUSED_PARAMETER")view: View) {
        when(this.imprintAndContactSubContainer.visibility){
            View.GONE -> {
                this.imprintAndContactSubContainer.visibility = View.VISIBLE
            }
            else -> {
                this.imprintAndContactSubContainer.visibility = View.GONE
            }
        }
    }

    fun onDisclaimerFieldClick(@Suppress("UNUSED_PARAMETER")view: View) {
        when(this.disclaimerSubContainer.visibility){
            View.GONE -> {
                this.disclaimerSubContainer.visibility = View.VISIBLE
            }
            else -> {
                this.disclaimerSubContainer.visibility = View.GONE
            }
        }
    }
}