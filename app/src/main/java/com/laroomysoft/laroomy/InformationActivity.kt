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

    lateinit var versionTextView: AppCompatTextView
    private lateinit var privacyProtectionSubContainer: ConstraintLayout
    private lateinit var termsOfServiceSubContainer: ConstraintLayout
    private lateinit var creditsSubContainer: ConstraintLayout
    lateinit var backButton: AppCompatImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_information)

        this.privacyProtectionSubContainer = findViewById(R.id.infoActivityPrivacyPolicySubContainer)
        this.termsOfServiceSubContainer = findViewById(R.id.infoActivityTermsOfServiceSubContainer)
        this.creditsSubContainer = findViewById(R.id.infoActivityCreditsSubContainer)

        // add back button functionality
        this.backButton = findViewById(R.id.infoActivityBackButton)
        this.backButton.setOnClickListener {
            this.onBackPressed()
        }

//        val pInfo =
//            packageManager.getPackageInfo(packageName, 0)
//
//        @Suppress("DEPRECATION") val versionString = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
//            "${getString(R.string.InfoActivity_AppVersionEntry)} ${pInfo.longVersionCode}"
//        } else {
//            "${getString(R.string.InfoActivity_AppVersionEntry)} ${pInfo.versionCode}"
//        }
//
//        versionTextView = findViewById(R.id.infoActivityAppVersionTextView)
//        versionTextView.text = versionString
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
}