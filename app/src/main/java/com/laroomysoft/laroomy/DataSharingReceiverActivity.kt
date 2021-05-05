package com.laroomysoft.laroomy

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.widget.AppCompatTextView

class DataSharingReceiverActivity : AppCompatActivity() {

    private lateinit var notificationTextView: AppCompatTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_sharing_receiver)

        // get the view(s)
        notificationTextView = findViewById(R.id.dataSharingActivityNotificationTextView)

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

            Log.d("M:DSA:onCreate", "onCreate in DataSharingActivity executed. Data decrypted: Mac Address: $macReformatted   PassKey: $passKeyDecrypted")

            if ((macReformatted != ERROR_INVALID_PARAMETER) && (passKeyDecrypted.isNotEmpty())) {
                // data valid: save the data
                val pair = BindingPair()
                pair.macAddress = macReformatted
                pair.passKey = passKeyDecrypted
                val bindingPairManager = BindingPairManager(applicationContext)
                bindingPairManager.add(pair)
                // notify user
                notifyUser(
                    getString(R.string.DataSharingActivity_BindingDataSuccessfulSet),
                    R.color.normalTextColor
                )

            } else {
                // error: notify user
                notifyUser(getString(R.string.DataSharingActivity_Error_BindingDataInvalidFormat), R.color.ErrorColor)
            }
        }
    }

    private fun notifyUser(message: String, colorID: Int){
        notificationTextView.setTextColor(getColor(colorID))
        notificationTextView.text = message
    }

    fun onDataSharingActivityGoToMainButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {
        // navigate back to App...
        val intent: Intent = Intent(this@DataSharingReceiverActivity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }
}