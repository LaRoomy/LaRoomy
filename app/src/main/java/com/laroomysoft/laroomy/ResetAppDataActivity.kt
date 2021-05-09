package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.widget.AppCompatCheckBox

class ResetAppDataActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener {

    private lateinit var selectAllCheckBox: AppCompatCheckBox
    private lateinit var resetAppSettingsCheckBox: AppCompatCheckBox
    private lateinit var resetBindingDataCheckBox: AppCompatCheckBox
    private lateinit var resetUUIDProfilesCheckBox: AppCompatCheckBox
    private lateinit var resetDefaultBindingKeyCheckBox: AppCompatCheckBox

    private var blockInternalOnCheckExecution = false

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

    fun resetAppActivityOnCancelButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }
    fun resetAppActivityOnResetButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        // TODO: launch dialog

    }

    fun notifyUser(message: String){

        // TODO: create notification textView and implement error messages, like no data selected

    }
}