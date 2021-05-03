package com.laroomysoft.laroomy

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView

class DataSharingReceiverActivity : AppCompatActivity() {

    private lateinit var notificationTextView: AppCompatTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_sharing_receiver)

        notificationTextView = findViewById(R.id.dataSharingActivityNotificationTextView)

        // TODO: read the intent data when the user opens this app with a shared link -> retrieve the key-value pair and save it!

        // test section

        var testString = ""

        val intentEXT = intent
        if(intentEXT.action == Intent.ACTION_VIEW){
            val uri: Uri? = intentEXT.data
            val str1 = uri?.getQueryParameter("devid")
            val str2 = uri?.getQueryParameter("bdata")
            testString = "Data received. devid= $str1 and bdata= $str2"
        }

        if(testString.isNotEmpty()){
            findViewById<AppCompatTextView>(R.id.setupActivityAutoConnectHintTextView).apply {
                text = testString
            }
        }



        // test section end


        // TODO: save the data, check the data and notify user!

    }
}