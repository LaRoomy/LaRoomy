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

        // get the view(s)
        notificationTextView = findViewById(R.id.dataSharingActivityNotificationTextView)

        // TODO: read the intent data when the user opens this app with a shared link -> retrieve the key-value pair and save it!

        // get the intent-data and read the URL Parameter
        val intentEXT = intent
        if(intentEXT.action == Intent.ACTION_VIEW){
            val uri: Uri? = intentEXT.data
            val devId = uri?.getQueryParameter("devid")
            val bData = uri?.getQueryParameter("bdata")
            // decrypt the parameter
            val macDecrypted = decryptString(devId ?: "")
            val macReformatted = encryptStringToMacAddress(macDecrypted)
            val passKeyDecrypted = decryptString(bData ?: "")

            // temp for testing
            notifyUser("MAC: $macReformatted   PassKey: $passKeyDecrypted", R.color.goldAccentColor)

            if((macReformatted != ERROR_INVALID_PARAMETER)&&(passKeyDecrypted.isNotEmpty())){
                // data ok - TODO: save it!

                // TODO: notify user!

            } else {
                // error - TODO: notify user!
            }

        }

        // TODO: save the data, check the data and notify user!

    }

    private fun notifyUser(message: String, colorID: Int){
        notificationTextView.setTextColor(getColor(colorID))
        notificationTextView.text = message
    }
}