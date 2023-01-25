package com.laroomysoft.laroomy

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ViewLogActivity : AppCompatActivity() {
    
    private val filterForInfo = 2
    private val filterForWarning = 3
    private val filterForError = 4
    
    private var infoFilter = false
    private var warningFilter = false
    private var errorFilter = false
    
    private lateinit var notificationTextView: AppCompatTextView

    private lateinit var logDataListView: RecyclerView
    private lateinit var logDataListAdapter: RecyclerView.Adapter<*>
    private lateinit var logDataListLayoutManager: RecyclerView.LayoutManager
    private lateinit var backButton: AppCompatImageButton
    private lateinit var scrollUpButton: AppCompatImageButton
    private lateinit var scrollDownButton: AppCompatImageButton
    private lateinit var exportButton: AppCompatImageButton
    private lateinit var filterButton: AppCompatImageButton
    
    private lateinit var popUpWindow: PopupWindow
    
    private val filteredLogList = ArrayList<String>()
    private lateinit var filteredLogListAdapter: RecyclerView.Adapter<*>
    
    private lateinit var exportButtonAnimation: AnimatedVectorDrawable
    private lateinit var scrollUpButtonAnimation: AnimatedVectorDrawable
    private lateinit var scrollDownButtonAnimation: AnimatedVectorDrawable
    private lateinit var filterButtonAnimation: AnimatedVectorDrawable

    private var wasInvokedFromSettingsActivity = false
    
    private var filterActive = false
    private var filterPopUpIsOpen = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_log)

        // register back event
        this.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })
        
        // add back button functionality
        backButton = findViewById<AppCompatImageButton?>(R.id.viewLogActivityBackButton).apply {
            setOnClickListener {
                handleBackEvent()
            }
        }
        
        val noContent = (applicationContext as ApplicationProperty).connectionLog.size == 0
        
        // add tool button functionalities
        scrollUpButton = findViewById<AppCompatImageButton?>(R.id.viewLogActivityScrollUpImageButton).apply {
            scrollUpButtonAnimation =
                background as AnimatedVectorDrawable
            if(noContent){
                isEnabled = false
            }
            setOnClickListener {
                // start animation
                scrollUpButtonAnimation.start()
                logDataListView.scrollToPosition(0)
            }
        }
        scrollDownButton = findViewById<AppCompatImageButton?>(R.id.viewLogActivityScrollDownImageButton).apply {
            scrollDownButtonAnimation =
                background as AnimatedVectorDrawable
            if(noContent){
                isEnabled = false
            }
            setOnClickListener {
                // start animation
                scrollDownButtonAnimation.start()
                logDataListView.scrollToPosition(logDataListAdapter.itemCount - 1)
            }
        }
        exportButton = findViewById<AppCompatImageButton?>(R.id.viewLogActivityExportImageButton).apply {
            exportButtonAnimation =
                background as AnimatedVectorDrawable
            if(noContent){
                isEnabled = false
            }
            setOnClickListener {
                // start animation
                exportButtonAnimation.start()
                var textBuffer = ""
                (applicationContext as ApplicationProperty).connectionLog.forEach {
                    textBuffer += it
                    textBuffer += "\r\n"
                }
                // share
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, textBuffer)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)
            }
        }
        this.filterButton = findViewById<AppCompatImageButton?>(R.id.viewLogActivityFilterListButton).apply {
            filterButtonAnimation =
                background as AnimatedVectorDrawable
            if(noContent){
                isEnabled = false
            }
            // set click listener
            setOnClickListener {
                // start animation
                filterButtonAnimation.start()
                if(filterActive){
                    releaseFilter()
                } else {
                    showFilterPopUp()
                }
            }
        }
        
        // get intent params
        this.wasInvokedFromSettingsActivity = intent.getBooleanExtra("wasInvokedFromSettingsActivity", false)
        
        // init recyclerView and Adapter
        this.notificationTextView = findViewById(R.id.viewLogActivityNotificationTextView)
        this.logDataListAdapter = LogDataListAdapter((applicationContext as ApplicationProperty).connectionLog)
        this.logDataListLayoutManager = LinearLayoutManager(this)

        this.logDataListView = findViewById<RecyclerView>(R.id.viewLogActivityLogDataListView).apply {
            setHasFixedSize(true)
            adapter = logDataListAdapter
            layoutManager = logDataListLayoutManager
        }

        // set header text (recTime, noLogData, ..)
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_EnableLog)) {
            if ((applicationContext as ApplicationProperty).connectionLog.size <= 1) {
                notifyUser(
                    getString(R.string.ViewLogActivity_NotificationNoLogDataAvailable),
                    R.color.warningLightColor
                )
            } else {
                notifyUser(
                    (applicationContext as ApplicationProperty).logRecordingTime,
                    R.color.mode_accent_color
                )
            }
        } else {
            notifyUser(
                getString(R.string.ViewLogActivity_NotificationLoggingIsDisabled),
                R.color.warningLightColor
            )
        }
    }
    
    private fun handleBackEvent(){
        finish()
        if(wasInvokedFromSettingsActivity) {
            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        }
    }
    
    @SuppressLint("InflateParams")
    private fun showFilterPopUp(){
        if(!filterPopUpIsOpen){
            // set recycler alpha down
            this.logDataListView.alpha = 0.2f
        
            (getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater).apply {
                layoutInflater.inflate(R.layout.view_log_filter_popup, null)
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
                                    logDataListView.alpha = 1f
                                }
                                // show the popup
                                showAtLocation(logDataListView, Gravity.CENTER, 0, 0)
                            
                                // set apply button listener
                                this.contentView.findViewById<AppCompatButton>(R.id.viewLogFilterPopUpApplyButton)
                                    .apply {
                                        setOnClickListener {
                                        
                                            val fList = ArrayList<Int>()
                                            contentView.findViewById<AppCompatCheckBox>(R.id.viewLogFilterPopUpCheckBox_Info)
                                                .apply {
                                                    if (isChecked) {
                                                        fList.add(filterForInfo)
                                                    }
                                                }
                                            contentView.findViewById<AppCompatCheckBox>(R.id.viewLogFilterPopUpCheckBox_Warning)
                                                .apply {
                                                    if (isChecked) {
                                                        fList.add(filterForWarning)
                                                    }
                                                }
                                            contentView.findViewById<AppCompatCheckBox>(R.id.viewLogFilterPopUpCheckBox_Error)
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
                                this.contentView.findViewById<AppCompatButton>(R.id.viewLogFilterPopUpCancelButton)
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
                filterForInfo -> infoFilter = true
                filterForWarning -> warningFilter = true
                filterForError -> errorFilter = true
            }
        }
        // check if filtering is necessary
        if(this.infoFilter || this.warningFilter || this.errorFilter){
            if(this.infoFilter && this.warningFilter && this.errorFilter){
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
        this.filteredLogList.clear()
    
        // apply filter rules
        (applicationContext as ApplicationProperty).connectionLog.forEach {
            when (it.elementAt(0)) {
                'I' -> {
                    if (this.infoFilter) {
                        this.filteredLogList.add(it)
                    }
                }
                'W' -> {
                    if (this.warningFilter) {
                        this.filteredLogList.add(it)
                    }
                }
                'E' -> {
                    if (this.errorFilter) {
                        this.filteredLogList.add(it)
                    }
                }
            }
        }
    
        // set the filtered adapter to the recycler
        this.filteredLogListAdapter =
            LogDataListAdapter(this.filteredLogList)
        this.logDataListView.adapter = this.filteredLogListAdapter
    
        // set release filter image to filter-button
        this.filterButton.setImageResource(R.drawable.ic_filter_list_off_36dp)
    }
    
    private fun releaseFilter(){
        // set the normal adapter
        this.logDataListAdapter =
            LogDataListAdapter((applicationContext as ApplicationProperty).connectionLog)
        this.logDataListView.adapter = this.logDataListAdapter
    
        // reset params
        this.infoFilter = false
        this.warningFilter = false
        this.errorFilter = false
    
        this.filterActive = false
        this.filteredLogList.clear()
    
        // normalize the filter-button image
        this.filterButton.setImageResource(R.drawable.ic_filter_list_36dp)
    }

    private fun notifyUser(message: String, colorID: Int){
        this.notificationTextView.setTextColor(getColor(colorID))
        this.notificationTextView.text = message
    }

    class LogDataListAdapter(private val logDataList: ArrayList<String>)
        : RecyclerView.Adapter<LogDataListAdapter.ViewHolder>() {

        class ViewHolder(val linearLayout: LinearLayout) : RecyclerView.ViewHolder(linearLayout)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.log_data_list_element, parent, false) as LinearLayout

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.linearLayout.findViewById<AppCompatTextView>(R.id.logListItemContentTextView).apply {
                text = logDataList[position]

                when (text.elementAt(0)) {
                    'E' -> {
                        setTextColor(holder.linearLayout.context.getColor(R.color.errorLightColor))
                    }
                    'W' -> {
                        setTextColor(holder.linearLayout.context.getColor(R.color.warningLightColor))
                    }
                    else -> {
                        setTextColor(holder.linearLayout.context.getColor(R.color.normalTextColor))
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return logDataList.size
        }
    }
}