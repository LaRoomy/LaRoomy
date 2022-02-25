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

            if(verboseLog) {
                Log.d(
                    "M:DSA:onCreate",
                    "onCreate in DataSharingActivity executed. Data decrypted: Mac Address: $macReformatted   PassKey: $passKeyDecrypted"
                )
            }

            if((applicationContext as ApplicationProperty).getCurrentUsedPasskey() == passKeyDecrypted){
                // the executor of the sharing link is the originator, so do not save the passkey as shared passkey, since it equals the main-key
                if(verboseLog){
                    Log.w("KeySharing", "The shared passkey was not saved, because it equals the key in use")
                }
                (applicationContext as ApplicationProperty).logControl("W: The shared passkey was NOT saved, because it equals the key in use.")
                // notify user
                notifyUser(getString(R.string.DataSharingActivity_Message_YouAreOriginator), R.color.normalTextColor)
            } else {

                // TODO: look if the mac-address is saved before, check if the key changed and notify the user respectively! (key updated / key already saved!)

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
                    notifyUser(
                        getString(R.string.DataSharingActivity_Error_BindingDataInvalidFormat),
                        R.color.ErrorColor
                    )
                }
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
            //flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP // old
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP

            // NOTE: CLEAR_TOP closes all other activities on the top of the main activity
            // https://developer.android.com/guide/components/activities/tasks-and-back-stack#ManagingTasks

        }
        startActivity(intent)
        finish()
    }
}