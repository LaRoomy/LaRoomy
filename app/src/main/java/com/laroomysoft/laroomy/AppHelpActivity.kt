package com.laroomysoft.laroomy

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class AppHelpActivity : AppCompatActivity() {

    lateinit var helpWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_help)

        this.helpWebView = findViewById(R.id.helpWebView)

        // set cache mode
        this.helpWebView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

        // TODO: change the url to the help-page of the laroomy-website
        this.helpWebView.loadUrl("https://laroomy.de")
    }
}