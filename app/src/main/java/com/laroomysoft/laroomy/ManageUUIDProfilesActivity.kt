package com.laroomysoft.laroomy

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ManageUUIDProfilesActivity : AppCompatActivity(), OnUUIDProfileListItemClickListener {

    private lateinit var uuidProfileListView: RecyclerView
    private lateinit var uuidProfileListAdapter: RecyclerView.Adapter<*>
    private lateinit var uuidProfileListViewManager: RecyclerView.LayoutManager
    private lateinit var backButton: AppCompatImageButton
    private lateinit var addButton: AppCompatImageButton
    private lateinit var noPremiumBanner: ConstraintLayout

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
        
        // add >add< button functionality
        this.addButton = findViewById<AppCompatImageButton?>(R.id.uuidProfileActivityAddButton).apply {
            if((applicationContext as ApplicationProperty).premiumManager.isPremiumAppVersion) {
                setOnClickListener {
                    onAddUUIDProfileButtonClick()
                }
            } else {
                // is not premium, disable button
                this.isEnabled = false
                // set disabled image
                setImageResource(R.drawable.ic_add_disabled_36dp)
            }
        }
        
        // control no premium banner visibility
        this.noPremiumBanner = findViewById<ConstraintLayout?>(R.id.uuidProfileActivityNoPremiumBanner).apply {
            visibility = if((applicationContext as ApplicationProperty).premiumManager.isPremiumAppVersion){
                View.GONE
            } else {
                View.VISIBLE
            }
        }

        this.uuidProfileListViewManager = LinearLayoutManager(this)
        this.uuidProfileListAdapter =
            ProfileListAdapter(
                (applicationContext as ApplicationProperty).uuidManager.uUIDProfileList,
                this,
                (applicationContext as ApplicationProperty).premiumManager.isPremiumAppVersion,
                UUID_MANAGER_FIRST_USERPROFILE_INDEX
            )

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
        if((!(applicationContext as ApplicationProperty).premiumManager.isPremiumAppVersion) && (index >= UUID_MANAGER_FIRST_USERPROFILE_INDEX)){
            // invalid action, user has not purchased and this is not a static profile, so the page could not be opened
            // execute bounce animation on the banner to indicate that to the user
            val bannerAnimation = AnimationUtils.loadAnimation(applicationContext, R.anim.bounce)
            this.noPremiumBanner.startAnimation(bannerAnimation)
        } else {
            internalBackNavigation = true
    
            setListItemBackgroundDrawable(
                index,
                R.drawable.manage_uuid_profiles_list_element_selected_background
            )
    
            val intent =
                Intent(this@ManageUUIDProfilesActivity, EditUUIDProfileActivity::class.java)
            intent.putExtra("activity-mode", "edit$index")
            startActivity(intent)
        }
    }

    private fun onAddUUIDProfileButtonClick(){

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
        private val itemClickListener: OnUUIDProfileListItemClickListener,
        private val isPremium: Boolean,
        private val firstUserProfileIndex: Int) :
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
            val element =
                profileList.elementAt(position)
            
            if(isPremium) {
                holder.linearLayout.findViewById<AppCompatTextView>(R.id.profileListItemProfileNameTextView)
                    .apply {
                        text = element.profileName
                    }
                if (!element.isLocked) {
                    holder.linearLayout.findViewById<AppCompatImageView>(R.id.profileListItemProfileImage)
                        .apply {
                            setImageResource(R.drawable.link_inclined_36dp)
                        }
                }
            } else {
                // is not premium, if the user has not purchased, set item to non-premium state except this is a static profile
                val isUserProfile = (position >= firstUserProfileIndex)
    
                holder.linearLayout.findViewById<AppCompatTextView>(R.id.profileListItemProfileNameTextView)
                    .apply {
                        // if this is a user profile -> set disabled text color
                        if (isUserProfile) {
                            setTextColor(this.context.getColor(R.color.disabledTextColor))
                        }
                        // set item text always
                        text = element.profileName
                    }
                // if this is a user profile -> set disabled image
                if(isUserProfile) {
                    holder.linearLayout.findViewById<AppCompatImageView>(R.id.profileListItemProfileImage)
                        .apply {
                            setImageResource(R.drawable.link_inclined_disabled_36dp)
                        }
                }
            }
            holder.bind(profileList.elementAt(position), itemClickListener)
        }

        override fun getItemCount(): Int {
            return profileList.size
        }
    }
}