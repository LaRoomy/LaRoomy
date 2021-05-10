package com.laroomysoft.laroomy

import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatTextView

class ResetAppDataActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener, DialogInterface.OnDismissListener {

    private lateinit var selectAllCheckBox: AppCompatCheckBox
    private lateinit var resetAppSettingsCheckBox: AppCompatCheckBox
    private lateinit var resetBindingDataCheckBox: AppCompatCheckBox
    private lateinit var resetUUIDProfilesCheckBox: AppCompatCheckBox
    private lateinit var resetDefaultBindingKeyCheckBox: AppCompatCheckBox

    private lateinit var notificationTextView: AppCompatTextView

    private var blockInternalOnCheckExecution = false
    private var dataResetConfirmedByUser = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_app_data)

        this.selectAllCheckBox = findViewById(R.id.resetAppActivitySelectAllCheckBox)
        this.selectAllCheckBox.setOnCheckedChangeListener(this)

        this.resetAppSettingsCheckBox = findViewById(R.id.resetAppActivityResetAppSettingsCheckBox)
        this.resetAppSettingsCheckBox.setOnCheckedChangeListener(this)

        this.resetBindingDataCheckBox = findViewById(R.id.resetAppActivityResetBindingDataCheckBox)
        this.resetBindingDataCheckBox.setOnCheckedChangeListener(this)

        this.resetUUIDProfilesCheckBox = findViewById(R.id.resetAppActivityResetUUIDProfilesCheckBox)
        this.resetUUIDProfilesCheckBox.setOnCheckedChangeListener(this)

        this.resetDefaultBindingKeyCheckBox = findViewById(R.id.resetAppActivityResetDefaultBindingKeyCheckBox)
        this.resetDefaultBindingKeyCheckBox.setOnCheckedChangeListener(this)

        this.notificationTextView = findViewById(R.id.resetAppActivityNotificationTextView)
    }

    override fun onCheckedChanged(p0: CompoundButton?, p1: Boolean) {
        when (p0?.id) {
            R.id.resetAppActivitySelectAllCheckBox -> {

                if (!this.blockInternalOnCheckExecution) {

                    if (p1) {
                        this.resetAppSettingsCheckBox.isChecked = true
                        this.resetBindingDataCheckBox.isChecked = true
                        this.resetUUIDProfilesCheckBox.isChecked = true
                        this.resetDefaultBindingKeyCheckBox.isChecked = true
                    } else {
                        this.resetAppSettingsCheckBox.isChecked = false
                        this.resetBindingDataCheckBox.isChecked = false
                        this.resetUUIDProfilesCheckBox.isChecked = false
                        this.resetDefaultBindingKeyCheckBox.isChecked = false
                    }
                } else {
                    this.blockInternalOnCheckExecution = false
                }
            }
            else -> {
                if (this.resetDefaultBindingKeyCheckBox.isChecked && this.resetAppSettingsCheckBox.isChecked && this.resetBindingDataCheckBox.isChecked && this.resetUUIDProfilesCheckBox.isChecked) {
                    this.selectAllCheckBox.isChecked = true
                } else {
                    if (this.selectAllCheckBox.isChecked) {
                        this.blockInternalOnCheckExecution = true
                        this.selectAllCheckBox.isChecked = false
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface?) {
        if (this.dataResetConfirmedByUser) {
            // reset data and quit activity
            resetData()
            finish()
        }
    }

    fun resetAppActivityOnCancelButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }
    fun resetAppActivityOnResetButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        if(this.resetAppSettingsCheckBox.isChecked || this.resetBindingDataCheckBox.isChecked || this.resetUUIDProfilesCheckBox.isChecked || this.resetDefaultBindingKeyCheckBox.isChecked){
            val dialog = AlertDialog.Builder(this)
            dialog.setTitle(getString(R.string.ResetAppActivity_DialogTitle))
            dialog.setMessage(getString(R.string.ResetAppActivity_DialogMessage))
            dialog.setOnDismissListener(this)
            dialog.setPositiveButton(getString(R.string.ResetAppActivity_DialogPositiveButtonText)) { dialogInterface: DialogInterface, _: Int ->
                this.dataResetConfirmedByUser = true
                dialogInterface.dismiss()
            }
            dialog.setNegativeButton(getString(R.string.ResetAppActivity_DialogNegativeButtonText)) { dialogInterface: DialogInterface, _: Int ->
                this.dataResetConfirmedByUser = false
                dialogInterface.dismiss()
            }
            dialog.create()
            dialog.show()

        } else {
            notifyUser(getString(R.string.ResetAppActivity_NoDataSelected))
        }
    }

    private fun resetData(){
        if(this.resetAppSettingsCheckBox.isChecked){
            (applicationContext as ApplicationProperty).deleteFileWithFileKey(R.string.FileKey_AppSettings)
        }
        if(this.resetBindingDataCheckBox.isChecked){
            // if there are another future binding-data, clear it here!
            val bindingPairManager = BindingPairManager(this.applicationContext)
            bindingPairManager.clearAll()
        }
        if(this.resetUUIDProfilesCheckBox.isChecked){
            try {
                (applicationContext as ApplicationProperty).uuidManager.clearAllUserProfiles()

            } catch(ue: UninitializedPropertyAccessException){
                Log.e("M:ResetData", "Reset error: UUIDManager not initialized")
            }
        }
        if(this.resetDefaultBindingKeyCheckBox.isChecked){
            val newKey = createRandomPasskey(COMMON_PASSKEY_LENGTH)
            if(newKey.isNotEmpty()){
                (applicationContext as ApplicationProperty).saveStringData(newKey, R.string.FileKey_AppSettings, R.string.DataKey_DefaultRandomBindingPasskey)
            } else {
                Log.e("M:ResetData", "Reset error: generating random passkey failed.")
            }
        }
    }

    fun notifyUser(message: String){
        this.notificationTextView.text = message
    }
}