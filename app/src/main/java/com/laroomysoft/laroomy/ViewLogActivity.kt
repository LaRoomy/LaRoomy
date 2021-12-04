package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ViewLogActivity : AppCompatActivity() {

    private lateinit var notificationTextView: AppCompatTextView

    private lateinit var logDataListView: RecyclerView
    private lateinit var logDataListAdapter: RecyclerView.Adapter<*>
    private lateinit var logDataListLayoutManager: RecyclerView.LayoutManager

    private var wasInvokedFromSettingsActivity = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_log)

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

    override fun onBackPressed() {
        super.onBackPressed()
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