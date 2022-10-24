package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BindingManagerActivity : AppCompatActivity() {
    
    private lateinit var bindingDataElementListView: RecyclerView
    private lateinit var bindingDataElementListAdapter: RecyclerView.Adapter<*>
    private lateinit var bindingDataElementListLayoutManager: RecyclerView.LayoutManager
    
    private lateinit var backButton: AppCompatImageButton
    
    private val bindingPairManager = BindingPairManager(applicationContext)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_binding_manager)
    
        // register onBackPressed event
        this.onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })
    
        // get backButton and add functionality
        backButton = findViewById<AppCompatImageButton?>(R.id.bindingManagerActivityBackButton).apply {
            setOnClickListener {
                handleBackEvent()
            }
        }
        
        // initialize recycler view
        bindingDataElementListAdapter = BindingDataElementListAdapter(this.bindingPairManager.bindingPairs)
        bindingDataElementListLayoutManager = LinearLayoutManager(this)
        bindingDataElementListView = findViewById<RecyclerView?>(R.id.bindingManagerActivityBindingDataListView).apply {
            setHasFixedSize(true)
            adapter = bindingDataElementListAdapter
            layoutManager = bindingDataElementListLayoutManager
        }
        
    }
    
    private fun handleBackEvent(){
        // process 'onBackPressed' actions
        finish()
        overridePendingTransition(R.anim.finish_activity_slide_animation_in, R.anim.finish_activity_slide_animation_out)
    }
    
    class BindingDataElementListAdapter(private val bindingDataElementList: ArrayList<BindingPair>)
        : RecyclerView.Adapter<BindingDataElementListAdapter.ViewHolder>() {
        
        class ViewHolder(val constraintLayout: ConstraintLayout) : RecyclerView.ViewHolder(constraintLayout)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.binding_data_list_element, parent, false) as ConstraintLayout
            
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.constraintLayout.findViewById<AppCompatTextView>(R.id.bindingDataListElementDeviceNameTextView).apply {
            
            }
            holder.constraintLayout.findViewById<AppCompatTextView>(R.id.bindingDataListElementMacAddressTextView).apply {
            
            }
            holder.constraintLayout.findViewById<AppCompatTextView>(R.id.bindingDataListElementBindingTypeTextView).apply {
            
            }
        }
        
        override fun getItemCount(): Int {
            return bindingDataElementList.size
        }
    }
    
}