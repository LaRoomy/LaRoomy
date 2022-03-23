package com.laroomysoft.laroomy

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.lang.Exception
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TextListPresenter : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE

    private lateinit var headerTextView: AppCompatTextView
    private lateinit var backButton: AppCompatImageButton
    private lateinit var clearListButton: AppCompatImageButton

    private lateinit var textPresenterList: RecyclerView
    private lateinit var textPresenterListAdapter: RecyclerView.Adapter<*>
    private lateinit var textPresenterListLayoutManager: RecyclerView.LayoutManager

    private var textList = ArrayList<String>()

    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false


    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_list_presenter)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // get the element ID + UI-Adapter Index
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)

        // set the header-text to the property Name
        this.headerTextView = findViewById<AppCompatTextView>(R.id.textListPresenterHeaderTextView).apply {
            text =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                    relatedGlobalElementIndex
                ).elementText
        }

        // add back button functionality
        this.backButton = findViewById(R.id.textListPresenterBackButton)
        this.backButton.setOnClickListener {
            this.onBackPressed()
        }

        // bind the callbacks of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)


        // get UI-Elements
        this.textPresenterList = findViewById(R.id.textListPresenterPresentationRecyclerView)
        this.clearListButton = findViewById(R.id.textListPresenterClearListButton)

        // add clear list button onClick handler
        this.clearListButton.setOnClickListener {
            // clear the list + notify
            runOnUiThread {
                this.textList.clear()
                this.textPresenterListAdapter.notifyDataSetChanged()
            }

            // delete the str member of the complexProperty state object in the internal and the ui array (internal content of the list)
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(this.relatedGlobalElementIndex).complexPropertyState.strValue = ""
            ApplicationProperty.bluetoothConnectionManager.clearInternalPropertyStateStringValue(this.relatedElementID)
        }

        val textListPresenterState = TextListPresenterState()
        textListPresenterState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(this.relatedGlobalElementIndex).complexPropertyState
        )

        this.textList = textListPresenterState.textListBackgroundStack
        this.textPresenterListAdapter = TextPresenterListAdapter(this.textList)
        this.textPresenterListLayoutManager = LinearLayoutManager(this)

        this.textPresenterList.apply {
            setHasFixedSize(true)
            adapter = textPresenterListAdapter
            layoutManager = textPresenterListLayoutManager
        }

    }

    override fun onBackPressed() {
        super.onBackPressed()
        // when the user navigates back, schedule a final complex-state request to make sure the saved state is the same as the current state
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
        (this.applicationContext as ApplicationProperty).complexUpdateIndex = this.relatedElementID

        // close activity
        finish()
        if(!isStandAlonePropertyMode) {
            // only set slide transition if the activity was invoked from the deviceMainActivity
            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // if this is not called due to a back-navigation, the user must have left the app
        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            if(verboseLog) {
                Log.d(
                    "TextListPresenter:onPause",
                    "TextListPresenter Activity: The user closes the app -> suspend connection"
                )
            }
            // suspend connection and set indication-parameter
            this.expectedConnectionLoss = true
            this.mustReconnect = true
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()
        }
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d("TextListPresenter:onResume", "onResume executed in TextListPresenter Activity")
        }

        // reset parameter anyway
        this.expectedConnectionLoss = false

        when(ApplicationProperty.bluetoothConnectionManager.checkBluetoothEnabled()) {
            BLE_BLUETOOTH_PERMISSION_MISSING -> {
                // permission was revoked while app was in suspended state
                finish()
            }
            BLE_IS_DISABLED -> {
                // bluetooth was disabled while app was in suspended state
                finish()
            }
            else -> {
                ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
                ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

                // reconnect to the device if necessary (if the user has left the application)
                if (this.mustReconnect) {
                    if (verboseLog) {
                        Log.d("TextListPresenter:onResume", "The connection was suspended -> try to reconnect")
                    }
                    ApplicationProperty.bluetoothConnectionManager.resumeConnection()
                    this.mustReconnect = false
                } else {
                    // notify the remote device of the invocation of this property-page
                    ApplicationProperty.bluetoothConnectionManager.notifyComplexPropertyPageInvoked(
                        this.relatedElementID
                    )
                }
            }
        }
    }

    private fun connectionLossAlertDialog(){
        runOnUiThread {
            val dialog = AlertDialog.Builder(this)
            dialog.setMessage(R.string.GeneralString_UnexpectedConnectionLossMessage)
            dialog.setTitle(R.string.GeneralString_ConnectionLossDialogTitle)
            dialog.setPositiveButton(R.string.GeneralString_OK) { dialogInterface: DialogInterface, _: Int ->
                // try to reconnect
                this.propertyStateUpdateRequired = true
                ApplicationProperty.bluetoothConnectionManager.resumeConnection()
                dialogInterface.dismiss()
            }
            dialog.setNegativeButton(R.string.GeneralString_Cancel) { dialogInterface: DialogInterface, _: Int ->
                // cancel action
                Executors.newSingleThreadScheduledExecutor().schedule({
                    // NOTE: do not call clear() on the bleManager, this corrupts the list on the device main page!
                    (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                    finish()
                    if(!isStandAlonePropertyMode) {
                        // only set slide transition if the activity was invoked from the deviceMainActivity
                        overridePendingTransition(
                            R.anim.finish_activity_slide_animation_in,
                            R.anim.finish_activity_slide_animation_out
                        )
                    }
                }, 300, TimeUnit.MILLISECONDS)
                dialogInterface.dismiss()
            }
            dialog.create()
            dialog.show()
        }
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        if(verboseLog) {
            Log.d(
                "StringInterrogator:CSC",
                "Connection state changed in String Interrogator Activity. New Connection state is: $state"
            )
        }
        if(state){
            notifyUser(NOTIFICATION_TYPE_INFO, getString(R.string.GeneralMessage_reconnected))

            if(this.propertyStateUpdateRequired){
                this.propertyStateUpdateRequired = false
                Executors.newSingleThreadScheduledExecutor().schedule({
                    ApplicationProperty.bluetoothConnectionManager.updatePropertyStates()
                }, TIMEFRAME_PROPERTY_STATE_UPDATE_ON_RECONNECT, TimeUnit.MILLISECONDS)
            }
        } else {
            notifyUser(NOTIFICATION_TYPE_WARNING, getString(R.string.GeneralMessage_connectionSuspended))

            if(!expectedConnectionLoss){
                // unexpected loss of connection
                if(verboseLog){
                    Log.d("onConnectionStateChanged", "Unexpected loss of connection in TextListPresenter Activity.")
                }
                (applicationContext as ApplicationProperty).logControl("W: Unexpected loss of connection. Remote device not reachable.")
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
                this.connectionLossAlertDialog()
            }
        }
    }

    override fun onConnectionError(errorID: Int) {
        super.onConnectionError(errorID)
        // if there is a connection failure -> navigate back
        when(errorID){
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE -> {
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                finish()
                if(!isStandAlonePropertyMode) {
                    // only set slide transition if the activity was invoked from the deviceMainActivity
                    overridePendingTransition(
                        R.anim.finish_activity_slide_animation_in,
                        R.anim.finish_activity_slide_animation_out
                    )
                }
            }
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_DEVICE_NOT_REACHABLE -> {
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                finish()
                if(!isStandAlonePropertyMode) {
                    // only set slide transition if the activity was invoked from the deviceMainActivity
                    overridePendingTransition(
                        R.anim.finish_activity_slide_animation_in,
                        R.anim.finish_activity_slide_animation_out
                    )
                }
            }
        }
    }

    override fun onRemoteUserMessage(deviceHeaderData: DeviceInfoHeaderData) {
        super.onRemoteUserMessage(deviceHeaderData)
        // display here as list element-notification
        val type = when(deviceHeaderData.type){
            '0' -> NOTIFICATION_TYPE_INFO
            '1' -> NOTIFICATION_TYPE_WARNING
            '2' -> NOTIFICATION_TYPE_ERROR
            else -> NOTIFICATION_TYPE_INFO
        }
        notifyUser(type, deviceHeaderData.message)
    }

    override fun getCurrentOpenComplexPropPagePropertyIndex(): Int {
        return this.relatedElementID
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onComplexPropertyStateChanged(
        UIAdapterElementIndex: Int,
        newState: ComplexPropertyState
    ) {
        try {
            if (UIAdapterElementIndex == this.relatedGlobalElementIndex) {
                when (newState.valueOne) {
                    0 -> {
                        // clear the list
                        runOnUiThread {
                            this.textList.clear()
                            this.textPresenterListAdapter.notifyDataSetChanged()
                        }
                    }
                    2 -> {
                        // add a new item
                        if (newState.strValue.isNotEmpty()) {
                            runOnUiThread {
                                this.textList.add(newState.strValue)
                                this.textPresenterListAdapter.notifyItemInserted(this.textList.size - 1)
                                this.textPresenterList.scrollToPosition(this.textList.size - 1)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception){
            Log.e("TextListPresenter", "Exception in onComplexPropertyStateChanged: $e")
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
        ApplicationProperty.bluetoothConnectionManager.uIAdapterList[UIAdapterElementIndex].hasChanged = true
    }

    override fun onPropertyInvalidated() {
        if(!isStandAlonePropertyMode) {
            (this.applicationContext as ApplicationProperty).propertyInvalidatedOnSubPage = true
            (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true

            finish()

            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        }
    }

    private fun notifyUser(notificationType: Int, message: String){
        // in this activity the notification is not displayed in a separate textView, instead it is added as list-element
        val notiString =  when(notificationType){
            NOTIFICATION_TYPE_INFO -> {
                "I$message"
            }
            NOTIFICATION_TYPE_WARNING -> {
                "W$message"
            }
            NOTIFICATION_TYPE_ERROR -> {
                "E$message"
            }
            else -> {
                "N$message"
            }
        }
        this.textList.add(notiString)
        this.textPresenterListAdapter.notifyItemInserted(this.textList.size - 1)
    }

    class TextPresenterListAdapter(private val textPresenterList: ArrayList<String>)
        : RecyclerView.Adapter<TextPresenterListAdapter.ViewHolder>() {

        class ViewHolder(val linearLayout: LinearLayout) : RecyclerView.ViewHolder(linearLayout)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.text_list_presenter_list_element, parent, false) as LinearLayout

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            // set element text
            holder.linearLayout.findViewById<AppCompatTextView>(R.id.textListPresenterListElementTextView).apply {
                text = textPresenterList[position].removeRange(0,1)

                when (textPresenterList[position].elementAt(0)) {
                    'N' -> {
                        setTextColor(holder.linearLayout.context.getColor(R.color.normalTextColor))
                    }
                    'I' -> {
                        setTextColor(holder.linearLayout.context.getColor(R.color.normalTextColor))
                    }
                    'W' -> {
                        setTextColor(holder.linearLayout.context.getColor(R.color.warningLightColor))
                    }
                    'E' -> {
                        setTextColor(holder.linearLayout.context.getColor(R.color.errorLightColor))
                    }
                    else -> {
                        setTextColor(holder.linearLayout.context.getColor(R.color.normalTextColor))
                    }
                }
            }
            // set element image
            holder.linearLayout.findViewById<AppCompatImageView>(R.id.textListPresenterListElementImageView).apply {
                when (textPresenterList[position].elementAt(0)) {
                    'N' -> {
                        setImageResource(R.drawable.ic_textlistpresenternone)
                    }
                    'I' -> {
                        setImageResource(R.drawable.ic_info_white_24dp)
                    }
                    'W' -> {
                        setImageResource(R.drawable.ic_textlistpresenterwarning)
                    }
                    'E' -> {
                        setImageResource(R.drawable.ic_textlistpresentererror)
                    }
                    else -> {
                        setImageResource(R.drawable.ic_textlistpresenternone)
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return textPresenterList.size
        }
    }

}