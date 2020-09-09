package com.laroomysoft.laroomy

import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView

class InformationActivity : AppCompatActivity() {

    lateinit var versionTextView: AppCompatTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_information)

        val pInfo =
            packageManager.getPackageInfo(packageName, 0)

        val versionString = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            "${getString(R.string.InfoActivity_AppVersionEntry)}${pInfo.longVersionCode}"
        } else {
            "${getString(R.string.InfoActivity_AppVersionEntry)}${pInfo.versionCode}"
        }

        versionTextView = findViewById(R.id.infoActivityAppVersionTextView)
        versionTextView.text = versionString
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
}