package com.laroomysoft.laroomy

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ManageUUIDProfilesActivity : AppCompatActivity(), OnUUIDProfileListItemClickListener {

    private lateinit var uuidProfileListView: RecyclerView
    private lateinit var uuidProfileListAdapter: RecyclerView.Adapter<*>
    private lateinit var uuidProfileListViewManager: RecyclerView.LayoutManager
    private lateinit var backButton: AppCompatImageButton

    private var internalBackNavigation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_u_u_i_d_profiles)
        
        // register onBackPressed event
        this.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })

        // add back button functionality
        this.backButton = findViewById(R.id.uuidProfileActivityBackButton)
        this.backButton.setOnClickListener {
            handleBackEvent()
        }

        this.uuidProfileListViewManager = LinearLayoutManager(this)
        this.uuidProfileListAdapter = ProfileListAdapter((applicationContext as ApplicationProperty).uuidManager.uUIDProfileList, this)

        this.uuidProfileListView = findViewById<RecyclerView>(R.id.uuidProfileActivityProfileList)
            .apply {
                setHasFixedSize(true)
                layoutManager = uuidProfileListViewManager
                adapter = uuidProfileListAdapter
        }
    }
    
    private fun handleBackEvent(){
        finish()
        overridePendingTransition(R.anim.finish_activity_slide_animation_in, R.anim.finish_activity_slide_animation_out)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()

        if(internalBackNavigation){
            internalBackNavigation = false
            this.uuidProfileListAdapter.notifyDataSetChanged()
            this.resetListItemColor()
        }
    }

    override fun onItemClicked(index: Int, data: UUIDProfile) {

        internalBackNavigation = true

        setListItemBackgroundDrawable(index, R.drawable.manage_uuid_profiles_list_element_selected_background)

        val intent = Intent(this@ManageUUIDProfilesActivity, EditUUIDProfileActivity::class.java)
        intent.putExtra("activity-mode", "edit$index")
        startActivity(intent)
    }

    fun onAddUUIDProfileButtonClick(@Suppress("UNUSED_PARAMETER") view: View){

        internalBackNavigation = true

        val intent = Intent(this@ManageUUIDProfilesActivity, EditUUIDProfileActivity::class.java)
        intent.putExtra("activity-mode", "new")
        startActivity(intent)

    }

    private fun setListItemBackgroundDrawable(index: Int, resourceID: Int){
        val ll = this.uuidProfileListViewManager.findViewByPosition(index) as? LinearLayout
        ll?.setBackgroundResource(resourceID)
    }

    private fun resetListItemColor(){
        for(x in 0 until uuidProfileListAdapter.itemCount){
            setListItemBackgroundDrawable(x, R.drawable.manage_uuid_profiles_list_element_background)
        }
    }

    class ProfileListAdapter(
        private val profileList: ArrayList<UUIDProfile>,
        private val itemClickListener: OnUUIDProfileListItemClickListener) :
        RecyclerView.Adapter<ProfileListAdapter.ViewHolder>() {

        class ViewHolder(val linearLayout: LinearLayout) : RecyclerView.ViewHolder(linearLayout) {

            fun bind(data: UUIDProfile, listener: OnUUIDProfileListItemClickListener){
                itemView.setOnClickListener{
                    listener.onItemClicked(bindingAdapterPosition, data)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.uuid_profile_list_element, parent, false) as LinearLayout

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.linearLayout.findViewById<AppCompatTextView>(R.id.profileListItemProfileNameTextView).text =
                profileList.elementAt(position).profileName

            holder.bind(profileList.elementAt(position), itemClickListener)
        }

        override fun getItemCount(): Int {
            return profileList.size
        }
    }
}