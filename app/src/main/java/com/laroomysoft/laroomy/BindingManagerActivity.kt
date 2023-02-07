package com.laroomysoft.laroomy

import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
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
        bindingDataElementListAdapter = BindingDataElementListAdapter(this.bindingDataManager)
    
        //bindingDataElementListAdapter = BindingDataElementListAdapter(this.bindingDataManager.bindingDataList)
        
        bindingDataElementListLayoutManager = LinearLayoutManager(this)
        bindingDataElementListView = findViewById<RecyclerView?>(R.id.bindingManagerActivityBindingDataListView).apply {
            setHasFixedSize(true)
            adapter = bindingDataElementListAdapter
            layoutManager = bindingDataElementListLayoutManager
        }
        
        // add swipe to delete callback
        val swipeHandler = object : SwipeToDeleteCallback(this){
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                try {
                    // get position to delete
                    val pos = viewHolder.absoluteAdapterPosition
                    // get the element at that position
                    val element =
                        (bindingDataElementListAdapter as BindingDataElementListAdapter).getAt(pos)
                    // only proceed if the data is valid
                    if (element.passKey.isNotEmpty()) {
                        // only show the dialog if the user is the originator of the device binding
                        // if the user is sharing-receiver, abstain from showing the dialog, but if the user is originator, only he can release the device binding
                        if (element.generatedAsOriginator) {
                            var resetItem = true
                            // start dialog
                            val dialog = AlertDialog.Builder(this@BindingManagerActivity)
                            dialog.setMessage(getString(R.string.BindingManagerActviity_ConfirmDelete_AsOriginator))
                            dialog.setPositiveButton(getString(R.string.GeneralString_Delete)) { dialogInterface: DialogInterface, _: Int ->
                                (bindingDataElementListAdapter as BindingDataElementListAdapter).removeAt(
                                    pos
                                )
                                resetItem = false
                                dialogInterface.dismiss()
                            }
                            dialog.setNegativeButton(getString(R.string.GeneralString_Cancel)) { dialogInterface: DialogInterface, _: Int ->
                                // reset the item state if it remains in the list
                                dialogInterface.dismiss()
                            }
                            dialog.setOnDismissListener {
                                if(resetItem){
                                    bindingDataElementListAdapter.notifyItemChanged(pos)
                                }
                            }
                            dialog.setTitle(element.deviceName)
                            dialog.setIcon(
                                AppCompatResources.getDrawable(
                                    bindingDataElementListView.context,
                                    R.drawable.ic_delete_36dp
                                )
                            )
                            dialog.create()
                            dialog.show()
                        } else {
                            (bindingDataElementListAdapter as BindingDataElementListAdapter).removeAt(
                                pos
                            )
                        }
                    }
                } catch (e: java.lang.Exception){
                    Log.e("BindingManagerActivity", "onSwipe: error: $e")
                }
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
    
    class BindingDataElementListAdapter(private val bindingDataManager: BindingDataManager)
        : RecyclerView.Adapter<BindingDataElementListAdapter.ViewHolder>() {
        
        class ViewHolder(val constraintLayout: ConstraintLayout) : RecyclerView.ViewHolder(constraintLayout)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.binding_data_list_element, parent, false) as ConstraintLayout
            
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val element = bindingDataManager.bindingDataList.elementAt(position)
            
            holder.constraintLayout.findViewById<AppCompatTextView>(R.id.bindingDataListElementDeviceNameTextView).apply {
                text = element.deviceName
            }
            holder.constraintLayout.findViewById<AppCompatTextView>(R.id.bindingDataListElementMacAddressTextView).apply {
                text = element.macAddress
            }
            holder.constraintLayout.findViewById<AppCompatTextView>(R.id.bindingDataListElementBindingTypeTextView).apply {
                text = when(element.generatedAsOriginator){
                    true -> {
                        holder.constraintLayout.context.getString(R.string.BindingManagerActivity_BindingTypeAsOriginator)
                    }
                    else -> {
                        holder.constraintLayout.context.getString(R.string.BindingManagerActivity_BindingTypeAsSharingReceiver)
                    }
                }
            }
        }
        
        override fun getItemCount(): Int {
            return bindingDataManager.bindingDataList.size
        }
        
        fun removeAt(position: Int){
            //bindingDataElementList.removeAt(position)
            val element = bindingDataManager.bindingDataList.elementAt(position)
            bindingDataManager.removeElement(element)
            notifyItemRemoved(position)
        }
        
        fun getAt(position: Int) : BindingData {
            return if(position < bindingDataManager.bindingDataList.size) {
                bindingDataManager.bindingDataList.elementAt(position)
            } else {
                BindingData()
            }
        }
    }
}