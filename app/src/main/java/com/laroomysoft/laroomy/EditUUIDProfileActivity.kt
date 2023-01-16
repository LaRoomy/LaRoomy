package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.*

class EditUUIDProfileActivity : AppCompatActivity() {

    private lateinit var headerTextView: AppCompatTextView
    private lateinit var profileNameEditText: AppCompatEditText
    private lateinit var serviceUUIDEditText: AppCompatEditText
    private lateinit var rxCharacteristicUUIDEditText: AppCompatEditText
    
    private lateinit var rxCharacteristicDescriptorTextView: AppCompatTextView
    private lateinit var txCharacteristicUUIDEditText: AppCompatEditText
    private lateinit var txCharacteristicContainer: ConstraintLayout
    
    private lateinit var deleteButton: AppCompatButton
    private lateinit var saveButton: AppCompatButton
    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var backButton: AppCompatImageButton
    private lateinit var useDistCharCheckBox: AppCompatCheckBox

    private var mode = ""
    private var elementIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_u_u_i_d_profile)
        
        // register onBackEvent handler
        this.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })

        profileNameEditText = findViewById(R.id.editUUIDActivityProfileNameEditText)
        serviceUUIDEditText = findViewById(R.id.editUUIDActivityServiceUUIDEditText)
        
        rxCharacteristicDescriptorTextView = findViewById(R.id.editUUIDActivityRXCharacteristicUUIDDescriptorTextView)
        rxCharacteristicUUIDEditText = findViewById(R.id.editUUIDActivityRXCharacteristicUUIDEditText)
        txCharacteristicUUIDEditText = findViewById(R.id.editUUIDActivityTXCharacteristicUUIDEditText)
        txCharacteristicContainer = findViewById(R.id.editUUIDActivityTXCharacteristicUUIDContainer)
        
        deleteButton = findViewById(R.id.editUUIDActivityDeleteButton)
        saveButton = findViewById(R.id.editUUIDActivitySaveButton)
        notificationTextView = findViewById(R.id.editUUIDProfileActivityNotificationTextView)
        backButton = findViewById(R.id.editUUIDActivityBackButton)
        useDistCharCheckBox = findViewById(R.id.editUUIDActivityUseDistinctCharUUIDsCheckBox)

        // add back button functionality
        backButton.setOnClickListener {
            handleBackEvent()
        }
        
        mode = this.intent.getStringExtra("activity-mode") ?: "err"

        this.headerTextView = findViewById(R.id.editUUIDActivityHeaderTextView)

        when (mode) {
            "new" -> {
                headerTextView.text = getString(R.string.EditUUIDProfileActivityHeaderNewModeText)
            }
            else -> {
                if (mode.startsWith("edit")) {

                    // set the ui-element text
                    headerTextView.text =
                        getString(R.string.EditUUIDProfileActivityHeaderEditModeText)
                    deleteButton.text = getString(R.string.EditUUIDProfileActivityDeleteButtonText)

                    // get the element index:
                    var strIndex = ""
                    mode.forEachIndexed { index, c ->
                        if (index > 3) {
                            strIndex += c
                        }
                    }
                    if (strIndex.isNotEmpty())
                        elementIndex = strIndex.toInt()

                    if(elementIndex < FIRST_USERPROFILE_INDEX){
                        // if the index is lower than 2 -> mark all as readonly and notify user
                        saveButton.isEnabled = false
                        deleteButton.isEnabled = false
                        notifyUser(getString(R.string.EditUUIDProfileActivityIsStaticProfileNotification), R.color.mode_accent_color)
                    }
                    // set the text to the existing data and adapt the UI
                    val profile =
                        (applicationContext as ApplicationProperty).uuidManager.uUIDProfileList.elementAt(elementIndex)
    
                    profileNameEditText.setText(profile.profileName)
                    serviceUUIDEditText.setText(profile.serviceUUID.toString())
                    rxCharacteristicUUIDEditText.setText(profile.rxCharacteristicUUID.toString())
    
                    // set the checkbox from param (must be inverted!)
                    useDistCharCheckBox.isChecked = !profile.useSingleCharacteristic
    
                    // hide the tx characteristic container if applicable
                    if (profile.useSingleCharacteristic) {
                        // if only one characteristic is used, the descriptor of the rx characteristic must be adapted
                        rxCharacteristicDescriptorTextView.text =
                            getString(R.string.EditUUIDProfileActivitySingleCharacteristicUUIDDescriptorText)
                        // hide the tx container
                        txCharacteristicContainer.visibility = View.GONE
                    } else {
                        txCharacteristicUUIDEditText.setText(profile.txCharacteristicUUID.toString())
                    }

                } else {
                    headerTextView.text = getString(R.string.EditUUIDProfileActivityModeError)
                }
            }
        }
    
        // add checkbox functionality
        useDistCharCheckBox.setOnCheckedChangeListener { _, b ->
            when(b){
                true -> {
                    // adapt the descriptor
                    rxCharacteristicDescriptorTextView.text =
                        getString(R.string.EditUUIDProfileActivityRXCharacteristicUUIDDescriptorText)
    
                    // show the tx container
                    txCharacteristicContainer.visibility = View.VISIBLE
                }
                else -> {
                    // adapt the descriptor
                    rxCharacteristicDescriptorTextView.text =
                        getString(R.string.EditUUIDProfileActivitySingleCharacteristicUUIDDescriptorText)
                    
                    // hide the tx container
                    txCharacteristicContainer.visibility = View.GONE
                }
            }
        }
    
    }
    
    private fun handleBackEvent(){
        finish()
    }

    fun notifyUser(message: String, colorID: Int) {
        if (message.isEmpty()) {
            notificationTextView.visibility = View.GONE
        } else {
            notificationTextView.visibility = View.VISIBLE
        }
        this.notificationTextView.setTextColor(getColor(colorID))
        this.notificationTextView.text = message
    }

    fun onEditUUIDActivityDeleteButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        val buttonAnimation = AnimationUtils.loadAnimation(applicationContext, R.anim.bounce)
        view.startAnimation(buttonAnimation)

        if(this.mode.startsWith("edit")){
            (applicationContext as ApplicationProperty).uuidManager.deleteExistingProfile(
                this.elementIndex
            )
        }
        // else : it's a new action, so discard all
        finish()
    }
    
    fun onEditUUIDActivitySaveButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        val buttonAnimation = AnimationUtils.loadAnimation(applicationContext, R.anim.bounce)
        view.startAnimation(buttonAnimation)
    
        val profile = UUIDProfile()
        profile.useSingleCharacteristic = !useDistCharCheckBox.isChecked
        profile.profileName = this.profileNameEditText.text.toString()
        profile.serviceUUID = UUID.fromString(
            this.convertToHyphenConformUUIDString(
                this.serviceUUIDEditText.text.toString()
            ))
        profile.rxCharacteristicUUID = UUID.fromString(
            this.convertToHyphenConformUUIDString(
                this.rxCharacteristicUUIDEditText.text.toString()
            ))
        profile.txCharacteristicUUID = if (profile.useSingleCharacteristic) {
            profile.rxCharacteristicUUID
        } else {
            UUID.fromString(
                this.convertToHyphenConformUUIDString(
                    this.txCharacteristicUUIDEditText.text.toString()
                ))
        }

        if(this.mode.startsWith("edit")){
            // this is an edit operation of an existing profile
            val result = (applicationContext as ApplicationProperty).uuidManager.changeExistingProfile(
                this.elementIndex,
                profile
            )
            when(result){
                CHANGE_SUCCESS -> finish()
                UUID_SERVICE_INVALID -> {
                    // notify user and do not finish
                    notifyUser(getString(R.string.EditUUIDProfileActivityInvalidServiceUUIDNotification), R.color.ErrorColor)
                }
                UUID_RX_CHAR_INVALID -> {
                    // notify user and do not finish
                    notifyUser(getString(R.string.EditUUIDProfileActivityInvalidRxCharUUIDNotification), R.color.ErrorColor)
                }
                UUID_TX_CHAR_INVALID -> {
                    // notify user and do not finish
                    notifyUser(getString(R.string.EditUUIDProfileActivityInvalidTxCharUUIDNotification), R.color.ErrorColor)
                }
                UUID_FORMAT_INVALID -> {
                    // notify user and do not finish
                    notifyUser(getString(R.string.EditUUIDProfileActivityInvalidUUIDNotification), R.color.ErrorColor)
                }
            }

        } else {
            //  this must be a new-action
            when ((applicationContext as ApplicationProperty).uuidManager.addNewProfile(profile)) {
                ADD_SUCCESS -> finish()
                UUID_SERVICE_INVALID -> {
                    // notify user and do not finish
                    notifyUser(getString(R.string.EditUUIDProfileActivityInvalidServiceUUIDNotification), R.color.ErrorColor)
                }
                UUID_RX_CHAR_INVALID -> {
                    // notify user and do not finish
                    notifyUser(getString(R.string.EditUUIDProfileActivityInvalidRxCharUUIDNotification), R.color.ErrorColor)
                }
                UUID_TX_CHAR_INVALID -> {
                    // notify user and do not finish
                    notifyUser(getString(R.string.EditUUIDProfileActivityInvalidTxCharUUIDNotification), R.color.ErrorColor)
                }
                UUID_FORMAT_INVALID -> {
                    // notify user and do not finish
                    notifyUser(
                        getString(R.string.EditUUIDProfileActivityInvalidUUIDNotification),
                        R.color.ErrorColor
                    )
                }
                PROFILE_NAME_ALREADY_EXIST -> {
                    // notify user and do not finish
                    notifyUser(
                        getString(R.string.EditUUIDProfileActivityProfileNameAlreadyExistNotification),
                        R.color.ErrorColor
                    )
                }
            }
        }
    }

    fun onEditUUIDActivityServiceUUIDDescriptorClick(@Suppress("UNUSED_PARAMETER") view: View) {
        this.serviceUUIDEditText.setText(getString(R.string.UUIDDefaultFill))
    }
    
    fun onEditUUIDActivityRXCharacteristicUUIDDescriptorClick(@Suppress("UNUSED_PARAMETER") view: View) {
        this.rxCharacteristicUUIDEditText.setText(getString(R.string.UUIDDefaultFill))
    }
    
    fun onEditUUIDActivityTXCharacteristicUUIDDescriptorClick(@Suppress("UNUSED_PARAMETER") view: View) {
        this.txCharacteristicUUIDEditText.setText(getString(R.string.UUIDDefaultFill))
    }
    
    private fun  convertToHyphenConformUUIDString(uuid: String) : String {
        var converted = ""
        uuid.forEachIndexed { index, c ->
            when(index){
                8 -> {
                    converted += '-'
                }
                13 -> {
                    converted += '-'
                }
                18 -> {
                    converted += '-'
                }
                23 -> {
                    converted += '-'
                }
                else -> {
                    converted += c
                }
            }
        }
        return converted
    }
}