package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BindingManagerActivity : AppCompatActivity() {
    
    private lateinit var bindingDataElementListView: RecyclerView
    private lateinit var bindingDataElementListAdapter: RecyclerView.Adapter<*>
    private lateinit var bindingDataElementListLayoutManager: RecyclerView.LayoutManager
    
    private lateinit var backButton: AppCompatImageButton
    
    private lateinit var bindingDataManager: BindingDataManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_binding_manager)
    
        // register onBackPressed event
        this.onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })
        
        // init binding data manager
        bindingDataManager = BindingDataManager(applicationContext)
    
        // get backButton and add functionality
        backButton = findViewById<AppCompatImageButton?>(R.id.bindingManagerActivityBackButton).apply {
            setOnClickListener {
                handleBackEvent()
            }
        }
        
        // initialize recycler view
        bindingDataElementListAdapter = BindingDataElementListAdapter(this.bindingDataManager.bindingDataList)
        bindingDataElementListLayoutManager = LinearLayoutManager(this)
        bindingDataElementListView = findViewById<RecyclerView?>(R.id.bindingManagerActivityBindingDataListView).apply {
            setHasFixedSize(true)
            adapter = bindingDataElementListAdapter
            layoutManager = bindingDataElementListLayoutManager
        }
        
        // add swipe to delete callback
        val swipeHandler = object : SwipeToDeleteCallback(this){
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.absoluteAdapterPosition
                val element = (bindingDataElementListAdapter as BindingDataElementListAdapter).elementAt(pos)
                (bindingDataElementListAdapter as BindingDataElementListAdapter).removeAt(pos)
                bindingDataManager.removeElement(element)
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(bindingDataElementListView)
    }
    
    private fun handleBackEvent(){
        // process 'onBackPressed' actions
        finish()
        overridePendingTransition(R.anim.finish_activity_slide_animation_in, R.anim.finish_activity_slide_animation_out)
    }
    
    class BindingDataElementListAdapter(private val bindingDataElementList: ArrayList<BindingData>)
        : RecyclerView.Adapter<BindingDataElementListAdapter.ViewHolder>() {
        
        class ViewHolder(val constraintLayout: ConstraintLayout) : RecyclerView.ViewHolder(constraintLayout)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.binding_data_list_element, parent, false) as ConstraintLayout
            
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val element = bindingDataElementList.elementAt(position)
            
            holder.constraintLayout.findViewById<AppCompatTextView>(R.id.bindingDataListElementDeviceNameTextView).apply {
                text = element.deviceName
            }
            holder.constraintLayout.findViewById<AppCompatTextView>(R.id.bindingDataListElementMacAddressTextView).apply {
                text = element.macAddress
            }
            holder.constraintLayout.findViewById<AppCompatTextView>(R.id.bindingDataListElementBindingTypeTextView).apply {
                text = when(element.generatedAsOriginator){
                    true -> {
                        if((holder.constraintLayout.context.applicationContext as ApplicationProperty).getCurrentUsedPasskey() == element.passKey){
                            holder.constraintLayout.context.getString(R.string.BindingManagerActivity_BindingTypeAsOriginator)
                        } else {
                            holder.constraintLayout.context.getString(R.string.BindingManagerActivity_BindingTypeDeprecatedOriginator)
                        }
                    }
                    else -> {
                        holder.constraintLayout.context.getString(R.string.BindingManagerActivity_BindingTypeAsSharingReceiver)
                    }
                }
            }
        }
        
        override fun getItemCount(): Int {
            return bindingDataElementList.size
        }
        
        fun removeAt(position: Int){
            bindingDataElementList.removeAt(position)
            notifyItemRemoved(position)
        }
        
        fun elementAt(position: Int) : BindingData {
            return if(position < bindingDataElementList.size) {
                bindingDataElementList[position]
            } else {
                BindingData()
            }
        }
    }
}