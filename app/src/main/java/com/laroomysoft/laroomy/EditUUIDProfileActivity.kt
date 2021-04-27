package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView

class EditUUIDProfileActivity : AppCompatActivity() {

    private lateinit var headerTextView: AppCompatTextView
    private lateinit var profileNameEditText: AppCompatEditText
    private lateinit var serviceUUIDEditText: AppCompatEditText
    private lateinit var characteristicUUIDEditText: AppCompatEditText
    private lateinit var deleteButton: AppCompatButton
    private lateinit var notificationTextView: AppCompatTextView

    private var mode = ""
    private var elementIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_u_u_i_d_profile)

        profileNameEditText = findViewById(R.id.editUUIDActivityProfileNameEditText)
        serviceUUIDEditText = findViewById(R.id.editUUIDActivityServiceUUIDEditText)
        characteristicUUIDEditText = findViewById(R.id.editUUIDActivityCharacteristicUUIDEditText)
        deleteButton = findViewById(R.id.editUUIDActivityDeleteButton)
        notificationTextView = findViewById(R.id.editUUIDProfileActivityNotificationTextView)

        mode = this.intent.getStringExtra("activity-mode") ?: "err"

        this.headerTextView = findViewById<AppCompatTextView>(R.id.editUUIDActivityHeaderTextView)
            .apply {
                when(mode){
                    "new" -> {
                        text = getString(R.string.EditUUIDProfileActivityHeaderNewModeText)
                    }
                    else -> {
                        if(mode.startsWith("edit")){
                            text = getString(R.string.EditUUIDProfileActivityHeaderEditModeText)
                            elementIndex = mode.elementAt(4).toInt()
                            deleteButton.text = getString(R.string.EditUUIDProfileActivityDeleteButtonText)
                        } else {
                            text = getString(R.string.EditUUIDProfileActivityModeError)
                        }
                    }
                }
            }


    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    fun notifyUser(message: String, colorID: Int){
        this.notificationTextView.setTextColor(getColor(colorID))
        this.notificationTextView.text = message
    }

    fun onEditUUIDActivityDeleteButtonClick(view: View) {}
    fun onEditUUIDActivitySaveButtonClick(view: View) {}
}