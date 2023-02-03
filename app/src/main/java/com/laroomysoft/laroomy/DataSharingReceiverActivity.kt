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
    
            if ((macReformatted != ERROR_INVALID_PARAMETER) && (passKeyDecrypted.isNotEmpty())) {
                // check a passkey exists for the mac address, if so, override it, otherwise skip
                val bindingManager = BindingDataManager(applicationContext)
                val bindingDataSet = bindingManager.lookUpForBindingData(macReformatted)
                var save = false
    
                if (bindingDataSet.passKey == ERROR_NOTFOUND) {
                    // there was no binding data saved before, so save it as sharing receiver
                    save = true
                } else {
                    // binding data was found for this mac address, so check if originator
                    if (bindingDataSet.generatedAsOriginator) {
                        // the invoked sharing link was generated on this specific device
                        // overriding it makes no sense because there remains no originator to release the binding
                        // so do nothing, but log and notify user
                        if (verboseLog) {
                            Log.w(
                                "KeySharing",
                                "The shared passkey was not saved, because it already exists with originator privileges"
                            )
                        }
                        (applicationContext as ApplicationProperty).logControl("W: The shared passkey was NOT saved, because it already exists with originator privileges.")
                        // notify user
                        notifyUser(
                            getString(R.string.DataSharingActivity_Message_YouAreOriginator),
                            R.color.normalTextColor
                        )
                    } else {
                        // there was already data saved for this mac address, but the key could be new, so check
                        if (bindingDataSet.passKey != passKeyDecrypted) {
                            // the passkey defers, so update
                            save = true
                        } else {
                            // notify user
                            notifyUser(
                                getString(R.string.DataSharingActivity_Message_DataAlreadySaved),
                                R.color.normalTextColor
                            )
                        }
                    }
                }
                // save if required:
                if (save) {
                    val bindingData = BindingData()
                    bindingData.macAddress = macReformatted
                    bindingData.passKey = passKeyDecrypted
                    bindingData.generatedAsOriginator = false
                    bindingManager.addOrUpdate(bindingData)
                    // notify user
                    notifyUser(
                        getString(R.string.DataSharingActivity_BindingDataSuccessfulSet),
                        R.color.normalTextColor
                    )
                }
            } else {
                // invalid data error: notify user
                notifyUser(
                    getString(R.string.DataSharingActivity_Error_BindingDataInvalidFormat),
                    R.color.ErrorColor
                )
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