package com.laroomysoft.laroomy

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.lang.Exception
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TextListPresenterActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {
    
    private val filterForDefault = 1
    private val filterForInfo = 2
    private val filterForWarning = 3
    private val filterForError = 4
    
    private var defaultFilter = false
    private var infoFilter = false
    private var warningFilter = false
    private var errorFilter = false
    
    
    private var mustReconnect = false
    private var relatedElementIndex = -1
    private var relatedUIAdapterIndex = -1
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE
    
    private lateinit var popUpWindow: PopupWindow

    private lateinit var headerTextView: AppCompatTextView
    private lateinit var backButton: AppCompatImageButton
    private lateinit var clearListButton: AppCompatImageButton
    private lateinit var exportListButton: AppCompatImageButton
    private lateinit var deviceSettingsButton: AppCompatImageButton
    private lateinit var filterListButton: AppCompatImageButton
    
    private lateinit var textPresenterList: RecyclerView
    private lateinit var textPresenterListAdapter: RecyclerView.Adapter<*>
    private lateinit var textPresenterListLayoutManager: RecyclerView.LayoutManager
    
    private lateinit var exportButtonAnimation: AnimatedVectorDrawable
    private lateinit var deleteButtonAnimation: AnimatedVectorDrawable
    private lateinit var filterListButtonAnimation: AnimatedVectorDrawable

    private var normalTextList = ArrayList<String>()
    private var filteredTextList = ArrayList<String>()
    private lateinit var filteredTextListPresenterAdapter: RecyclerView.Adapter<*>

    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false
    private var preventOnPauseExecutionInStandAloneMode = false
    private var buttonNormalizationRequired = false
    private var filterActive = false
    private var filterPopUpIsOpen = false
    
    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_list_presenter)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        // register back event
        this.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })

        // get the element ID + UI-Adapter Index
        relatedElementIndex = intent.getIntExtra("elementID", -1)
        relatedUIAdapterIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)

        // set the header-text to the property Name
        this.headerTextView = findViewById<AppCompatTextView>(R.id.textListPresenterHeaderTextView).apply {
            text =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                    relatedUIAdapterIndex
                ).elementText
        }

        // add back button functionality
        this.backButton = findViewById<AppCompatImageButton?>(R.id.textListPresenterBackButton).apply {
            setOnClickListener {
                handleBackEvent()
            }
        }
        
        // add settings button functionality (if applicable)
        this.deviceSettingsButton = findViewById<AppCompatImageButton?>(R.id.textListPresenterHeaderSettingsButton).apply {
            if(isStandAlonePropertyMode){
                visibility = View.VISIBLE
                setOnClickListener {
                    setImageResource(R.drawable.ic_settings_pressed_48dp)
                    buttonNormalizationRequired = true
                    // prevent connection suspension in onPause
                    preventOnPauseExecutionInStandAloneMode = true
                    // navigate to the device settings activity..
                    val intent = Intent(this@TextListPresenterActivity, DeviceSettingsActivity::class.java)
                    startActivity(intent)
                }
            }
        }

        // bind the callbacks of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get UI-Elements
        this.textPresenterList = findViewById(R.id.textListPresenterPresentationRecyclerView)
        
        // get clear button and add onClick handler
        this.clearListButton = findViewById<AppCompatImageButton?>(R.id.textListPresenterClearListButton).apply {
            
            deleteButtonAnimation = background as AnimatedVectorDrawable
            
            setOnClickListener {
                // animate
                deleteButtonAnimation.start()
                
                // if filter is active, release it
                if(filterActive){
                    releaseFilter()
                }
                
                // clear the list + notify
                runOnUiThread {
                    normalTextList.clear()
                    textPresenterListAdapter.notifyDataSetChanged()
                }
        
                // delete the str member of the complexProperty state object in the internal and the ui array (internal content of the list)
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedUIAdapterIndex).complexPropertyState.strValue =
                    ""
                ApplicationProperty.bluetoothConnectionManager.clearInternalPropertyStateStringValue(
                    relatedElementIndex
                )
            }
        }
        
        // get export button and add onClick listener
        this.exportListButton = findViewById<AppCompatImageButton?>(R.id.textListPresenterExportListButton).apply {
            exportButtonAnimation = background as AnimatedVectorDrawable
            
            setOnClickListener {
                // animate
                exportButtonAnimation.start()
                
                // format the list
                var listBuffer = ""
                if(filterActive){
                    filteredTextList.forEach {
                        listBuffer += when (it.elementAt(0)) {
                            'I' -> "INFO: "
                            'W' -> "WARNING: "
                            'E' -> "ERROR: "
                            else -> "NORMAL: "
                        }
                        listBuffer += "${it.removeRange(0, 1)}\r\n"
                    }
                } else {
                    normalTextList.forEach {
                        listBuffer += when (it.elementAt(0)) {
                            'I' -> "INFO: "
                            'W' -> "WARNING: "
                            'E' -> "ERROR: "
                            else -> "NORMAL: "
                        }
                        listBuffer += "${it.removeRange(0, 1)}\r\n"
                    }
                }
                // export
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, listBuffer)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, "Export")
                startActivity(shareIntent)
            }
        }
        
        // get filter button and add functionality
        this.filterListButton = findViewById<AppCompatImageButton?>(R.id.textListPresenterFilterListButton).apply {
            filterListButtonAnimation = background as AnimatedVectorDrawable
            
            setOnClickListener {
                filterListButtonAnimation.start()
                if(filterActive){
                    releaseFilter()
                } else {
                    showFilterPopUp()
                }
            }
        }

        val textListPresenterState = TextListPresenterState()
        textListPresenterState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(this.relatedUIAdapterIndex).complexPropertyState
        )

        this.normalTextList = textListPresenterState.textListBackgroundStack
        this.textPresenterListAdapter = TextPresenterListAdapter(this.normalTextList)
        this.textPresenterListLayoutManager = LinearLayoutManager(this)

        this.textPresenterList.apply {
            setHasFixedSize(true)
            adapter = textPresenterListAdapter
            layoutManager = textPresenterListLayoutManager
            scrollToPosition(normalTextList.size - 1)
        }
    }
    
    private fun handleBackEvent(){
        // check the mode and act in relation to it
        if(!isStandAlonePropertyMode) {
            // when the user navigates back, schedule a final complex-state request to make sure the saved state is the same as the current state
            (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
            (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
            (this.applicationContext as ApplicationProperty).complexUpdateIndex = this.relatedElementIndex
            // close activity
            finish()
            // only set slide transition if the activity was invoked from the deviceMainActivity
            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        } else {
            // this is stand-alone mode, so when back navigation occurs, the connection must be cleared
            ApplicationProperty.bluetoothConnectionManager.clear()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        if(!isStandAlonePropertyMode) {
            // NOT stand-alone mode:
            // if the following is true, onBackPressed was executed before and the connection must remain active
            // because this is a back navigation to the device main activity
            if (!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage) {
                if (verboseLog) {
                    Log.d(
                        "TextListPresenter:onPause",
                        "Text List Presenter Activity: The user left the app -> suspend connection"
                    )
                }
                // suspend connection and set indication-parameter
                this.mustReconnect = true
                expectedConnectionLoss = true
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
            }
        } else {
            // this is stand-alone property mode
            if(this.preventOnPauseExecutionInStandAloneMode){
                this.preventOnPauseExecutionInStandAloneMode = false
            } else {
                // normal onPause execution:
                if (verboseLog) {
                    Log.d(
                        "TextListPresenter:onPause",
                        "Text List Presenter (stand-alone-mode): The user left the app -> suspend connection"
                    )
                }
                // suspend connection and set indication-parameter
                this.mustReconnect = true
                this.expectedConnectionLoss = true
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d("TextListPresenter:onResume", "onResume executed in TextListPresenter Activity")
        }

        // reset parameter anyway
        this.expectedConnectionLoss = false
    
        // recover button if applicable
        if (isStandAlonePropertyMode && buttonNormalizationRequired) {
            buttonNormalizationRequired = false
            this.deviceSettingsButton.setImageResource(R.drawable.ic_settings_48dp)
        }
    
        when(ApplicationProperty.bluetoothConnectionManager.checkBluetoothEnabled()) {
            BLE_BLUETOOTH_PERMISSION_MISSING -> {
                // permission was revoked while app was in suspended state
                if(isStandAlonePropertyMode){
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
                finish()
            }
            BLE_IS_DISABLED -> {
                // bluetooth was disabled while app was in suspended state
                if(isStandAlonePropertyMode){
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
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
                        this.relatedElementIndex
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
                    if(!isStandAlonePropertyMode) {
                        // do not call clear() on the bleManager in normal mode, this corrupts the list on the device main page!
                        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                            true
                    } else {
                        // stand-alone-mode: here 'clear()' must be called - finish goes back to main activity directly
                        ApplicationProperty.bluetoothConnectionManager.clear()
                    }
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
    
    @SuppressLint("InflateParams")
    private fun showFilterPopUp(){
        if(!filterPopUpIsOpen){
            // set recycler alpha down
            this.textPresenterList.alpha = 0.2f
    
            (getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater).apply {
                layoutInflater.inflate(R.layout.text_list_presenter_filter_popup, null)
                    .apply {
                        
                        popUpWindow =
                            PopupWindow(
                                this,
                                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                                true
                            ).apply {
                    
                                // mark the popup as open
                                filterPopUpIsOpen = true
                    
                                // reset params if popup is dismissed
                                setOnDismissListener {
                                    filterPopUpIsOpen = false
                                    textPresenterList.alpha = 1f
                                }
                                // show the popup
                                showAtLocation(textPresenterList, Gravity.CENTER, 0, 0)
                    
                                // set apply button listener
                                this.contentView.findViewById<AppCompatButton>(R.id.textListPresenterFilterPopUpApplyButton)
                                    .apply {
                                        setOnClickListener {
                                
                                            val fList = ArrayList<Int>()
                                            contentView.findViewById<AppCompatCheckBox>(R.id.textListPresenterFilterPopUpCheckBox_Default)
                                                .apply {
                                                    if (isChecked) {
                                                        fList.add(filterForDefault)
                                                    }
                                                }
                                            contentView.findViewById<AppCompatCheckBox>(R.id.textListPresenterFilterPopUpCheckBox_Info)
                                                .apply {
                                                    if (isChecked) {
                                                        fList.add(filterForInfo)
                                                    }
                                                }
                                            contentView.findViewById<AppCompatCheckBox>(R.id.textListPresenterFilterPopUpCheckBox_Warning)
                                                .apply {
                                                    if (isChecked) {
                                                        fList.add(filterForWarning)
                                                    }
                                                }
                                            contentView.findViewById<AppCompatCheckBox>(R.id.textListPresenterFilterPopUpCheckBox_Error)
                                                .apply {
                                                    if (isChecked) {
                                                        fList.add(filterForError)
                                                    }
                                                }
                                            
                                            // apply filter
                                            filterElements(fList)
                                            
                                            // dismiss popup
                                            popUpWindow.dismiss()
                                        }
                                    }
                    
                                // set cancel button listener
                                this.contentView.findViewById<AppCompatButton>(R.id.textListPresenterFilterPopUpCancelButton)
                                    .apply {
                                        setOnClickListener {
                                            popUpWindow.dismiss()
                                        }
                                    }
                            }
                    }
            }
        }
    }
    
    private fun filterElements(filterList: ArrayList<Int>){
        // apply params from users radio-button selection
        filterList.forEach {
            when(it){
                filterForDefault -> defaultFilter = true
                filterForInfo -> infoFilter = true
                filterForWarning -> warningFilter = true
                filterForError -> errorFilter = true
            }
        }
        // check if filtering is necessary
        if(this.defaultFilter || this.infoFilter || this.warningFilter || this.errorFilter){
            if(this.defaultFilter && this.infoFilter && this.warningFilter && this.errorFilter){
                // if all is selected we need no filter, so do nothing
                return
            } else {
                this.filterActive = true
            }
        } else {
            // do nothing if no filter is selected
            return
        }
        // clear before use
        this.filteredTextList.clear()
    
        // apply filter rules
        this.normalTextList.forEach {
            when(it.elementAt(0)){
                'N' -> {
                    if(this.defaultFilter){
                        this.filteredTextList.add(it)
                    }
                }
                'I' -> {
                    if(this.infoFilter){
                        this.filteredTextList.add(it)
                    }
                }
                'W' -> {
                    if(this.warningFilter){
                        this.filteredTextList.add(it)
                    }
                }
                'E' -> {
                    if(this.errorFilter) {
                        this.filteredTextList.add(it)
                    }
                }
            }
        }
        
        // set the filtered adapter to the recycler
        this.filteredTextListPresenterAdapter = TextPresenterListAdapter(this.filteredTextList)
        this.textPresenterList.adapter = this.filteredTextListPresenterAdapter
        
        // set release filter image to filter-button
        this.filterListButton.setImageResource(R.drawable.ic_filter_list_off_36dp)
    }
    
    private fun releaseFilter(){
        // set the normal adapter
        this.textPresenterListAdapter = TextPresenterListAdapter(this.normalTextList)
        this.textPresenterList.adapter = this.textPresenterListAdapter
        
        // reset params
        this.defaultFilter = false
        this.infoFilter = false
        this.warningFilter = false
        this.errorFilter = false
        
        this.filterActive = false
        this.filteredTextList.clear()
        
        // normalize the filter-button image
        this.filterListButton.setImageResource(R.drawable.ic_filter_list_36dp)
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        if(verboseLog) {
            Log.d(
                "TextListPresenter:CSC",
                "Connection state changed in TextListPresenter Activity. New Connection state is: $state"
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
    
    override fun onConnectionEvent(eventID: Int) {
        if (eventID == BLE_MSC_EVENT_ID_RESUME_CONNECTION_STARTED) {
            notifyUser(
                NOTIFICATION_TYPE_WARNING,
                getString(R.string.GeneralMessage_resumingConnection)
            )
        }
    }
    
    override fun onConnectionError(errorID: Int) {
        super.onConnectionError(errorID)
        // if there is a connection failure -> navigate back
        when(errorID){
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE -> {
                if(!isStandAlonePropertyMode) {
                    (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                        true
                } else {
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
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
                if(!isStandAlonePropertyMode) {
                    (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                        true
                } else {
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
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
        return this.relatedElementIndex
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onComplexPropertyStateChanged(
        UIAdapterElementIndex: Int,
        newState: ComplexPropertyState
    ) {
        try {
            if (UIAdapterElementIndex == this.relatedUIAdapterIndex) {
                when (newState.valueOne) {
                    2 -> {
                        // clear the list
                        runOnUiThread {
                            this.normalTextList.clear()
                            this.textPresenterListAdapter.notifyDataSetChanged()
                        }
                    }
                    1 -> {
                        // add a new item
                        if (newState.strValue.isNotEmpty()) {
                            runOnUiThread {
                                this.normalTextList.add(newState.strValue)
                                if(this.filterActive) {
                                    var notifyInsertion = false
    
                                    when(newState.strValue.elementAt(0)){
                                        'N' -> {
                                            if(this.defaultFilter){
                                                this.filteredTextList.add(newState.strValue)
                                                notifyInsertion = true
                                            }
                                        }
                                        'I' -> {
                                            if(this.infoFilter){
                                                this.filteredTextList.add(newState.strValue)
                                                notifyInsertion = true
                                            }
                                        }
                                        'W' -> {
                                            if(this.warningFilter){
                                                this.filteredTextList.add(newState.strValue)
                                                notifyInsertion = true
                                            }
                                        }
                                        'E' -> {
                                            if(this.errorFilter) {
                                                this.filteredTextList.add(newState.strValue)
                                                notifyInsertion = true
                                            }
                                        }
                                    }
                                    if(notifyInsertion){
                                        this.filteredTextListPresenterAdapter.notifyItemInserted(this.filteredTextList.size - 1)
                                        this.textPresenterList.scrollToPosition(this.filteredTextList.size - 1)
                                    }
                                } else {
                                    this.textPresenterListAdapter.notifyItemInserted(this.normalTextList.size - 1)
                                    this.textPresenterList.scrollToPosition(this.normalTextList.size - 1)
                                }
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
        // else: do nothing: property reload is not supported in stand-alone mode
    }
    
    override fun onRemoteBackNavigationRequested() {
        if (!isStandAlonePropertyMode) {
            Executors.newSingleThreadScheduledExecutor().schedule({
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                
                finish()
                
                overridePendingTransition(
                    R.anim.finish_activity_slide_animation_in,
                    R.anim.finish_activity_slide_animation_out
                )
            }, 500, TimeUnit.MILLISECONDS)
        }
        // else: do nothing: back navigation to device main is not possible in stand-alone-mode
    }
    
    override fun onCloseDeviceRequested() {
        if(isStandAlonePropertyMode){
            ApplicationProperty.bluetoothConnectionManager.clear()
            finish()
        } else {
            (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
            (this.applicationContext as ApplicationProperty).closeDeviceRequested = true
            
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
        this.normalTextList.add(notiString)
        this.textPresenterListAdapter.notifyItemInserted(this.normalTextList.size - 1)
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
                        setImageResource(R.drawable.ic_info_24dp)
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