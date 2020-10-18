package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.WebView

class AppHelpActivity : AppCompatActivity() {

    lateinit var helpWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_help)

        this.helpWebView = findViewById(R.id.helpWebView)
        // TODO: change the url to the help-page of the laroomy-website
        this.helpWebView.loadUrl("https://www.laroomy.de")
    }
}