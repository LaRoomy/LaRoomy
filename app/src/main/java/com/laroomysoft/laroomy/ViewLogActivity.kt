package com.laroomysoft.laroomy

import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ViewLogActivity : AppCompatActivity() {

    private lateinit var notificationTextView: AppCompatTextView

    private lateinit var logDataListView: RecyclerView
    private lateinit var logDataListAdapter: RecyclerView.Adapter<*>
    private lateinit var logDataListLayoutManager: RecyclerView.LayoutManager
    private lateinit var backButton: AppCompatImageButton
    private lateinit var scrollUpButton: ImageButton
    private lateinit var scrollDownButton: ImageButton
    private lateinit var exportButton: ImageButton
    
    private lateinit var exportButtonAnimation: AnimatedVectorDrawable
    private lateinit var scrollUpButtonAnimation: AnimatedVectorDrawable
    private lateinit var scrollDownButtonAnimation: AnimatedVectorDrawable

    private var wasInvokedFromSettingsActivity = false

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
        scrollUpButton = findViewById<ImageButton?>(R.id.viewLogActivityScrollUpImageButton).apply {
            
            scrollUpButtonAnimation = background as AnimatedVectorDrawable
            
            if(noContent){
                isEnabled = false
            }
            
            setOnClickListener {
                scrollUpButtonAnimation.start()
                logDataListView.scrollToPosition(0)
            }
        }
        scrollDownButton = findViewById<ImageButton?>(R.id.viewLogActivityScrollDownImageButton).apply {
            
            scrollDownButtonAnimation = background as AnimatedVectorDrawable
            
            if(noContent){
                isEnabled = false
            }
            
            setOnClickListener {
                scrollDownButtonAnimation.start()
                logDataListView.scrollToPosition(logDataListAdapter.itemCount - 1)
            }
        }
        exportButton = findViewById<ImageButton?>(R.id.viewLogActivityExportImageButton).apply {
            
            exportButtonAnimation = background as AnimatedVectorDrawable
            
            if(noContent){
                isEnabled = false
            }
            
            setOnClickListener {
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
        
        this.wasInvokedFromSettingsActivity = intent.getBooleanExtra("wasInvokedFromSettingsActivity", false)

        this.notificationTextView = findViewById(R.id.viewLogActivityNotificationTextView)

        this.logDataListAdapter = LogDataListAdapter((applicationContext as ApplicationProperty).connectionLog)
        this.logDataListLayoutManager = LinearLayoutManager(this)

        this.logDataListView = findViewById<RecyclerView>(R.id.viewLogActivityLogDataListView).apply {
            setHasFixedSize(true)
            adapter = logDataListAdapter
            layoutManager = logDataListLayoutManager
        }

        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_EnableLog)) {
            if ((applicationContext as ApplicationProperty).connectionLog.size <= 1) {
                notifyUser(
                    getString(R.string.ViewLogActivity_NotificationNoLogDataAvailable),
                    R.color.warningLightColor
                )
            } else {
                notifyUser(
                    (applicationContext as ApplicationProperty).logRecordingTime,
                    R.color.goldAccentColor
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