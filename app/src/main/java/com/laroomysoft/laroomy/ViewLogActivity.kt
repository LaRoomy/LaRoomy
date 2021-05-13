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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_log)

        this.notificationTextView = findViewById(R.id.viewLogActivityNotificationTextView)

        this.logDataListAdapter = LogDataListAdapter((applicationContext as ApplicationProperty).connectionLog)
        this.logDataListLayoutManager = LinearLayoutManager(this)

        this.logDataListView = findViewById<RecyclerView>(R.id.viewLogActivityLogDataListView).apply {
            setHasFixedSize(true)
            adapter = logDataListAdapter
            layoutManager = logDataListLayoutManager
        }

        if((applicationContext as ApplicationProperty).connectionLog.size <= 1){
            notifyUser(getString(R.string.ViewLogActivity_NotificationNoLogDataAvailable), R.color.InfoColor)
        } else {
            notifyUser((applicationContext as ApplicationProperty).logRecordingTime, R.color.goldAccentColor)
        }
    }

    private fun notifyUser(message: String, colorID: Int){
        this.notificationTextView.setTextColor(getColor(colorID))
        this.notificationTextView.text = message
    }

    class LogDataListAdapter(val logDataList: ArrayList<String>)
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

                // TODO: set error/info/warning color?
            }
        }

        override fun getItemCount(): Int {
            return logDataList.size
        }
    }
}