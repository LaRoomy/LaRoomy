package com.laroomysoft.laroomy

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ManageUUIDProfilesActivity : AppCompatActivity(), OnUUIDProfileListItemClickListener {

    private lateinit var uuidProfileListView: RecyclerView
    private lateinit var uuidProfileListAdapter: RecyclerView.Adapter<*>
    private lateinit var uuidProfileListViewManager: RecyclerView.LayoutManager

    private var internalBackNavigation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_u_u_i_d_profiles)

        this.uuidProfileListViewManager = LinearLayoutManager(this)
        this.uuidProfileListAdapter = ProfileListAdapter((applicationContext as ApplicationProperty).uuidManager.uUIDProfileList, this)

        this.uuidProfileListView = findViewById<RecyclerView>(R.id.uuidProfileActivityProfileList)
            .apply {
                setHasFixedSize(true)
                layoutManager = uuidProfileListViewManager
                adapter = uuidProfileListAdapter
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onResume() {
        super.onResume()

        if(internalBackNavigation){
            internalBackNavigation = false

            // TODO:
            // handle added/changed Profile

            // reload the list !!!!
        }
    }

    override fun onItemClicked(index: Int, data: UUIDProfile) {

        internalBackNavigation = true

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

    class ProfileListAdapter(
        private val profileList: ArrayList<UUIDProfile>,
        private val itemClickListener: OnUUIDProfileListItemClickListener) :
        RecyclerView.Adapter<ProfileListAdapter.ViewHolder>() {

        class ViewHolder(val linearLayout: LinearLayout) : RecyclerView.ViewHolder(linearLayout) {

            fun bind(data: UUIDProfile, listener: OnUUIDProfileListItemClickListener, position: Int){
                itemView.setOnClickListener{
                    listener.onItemClicked(position, data)
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

            holder.bind(profileList.elementAt(position), itemClickListener, position)
        }

        override fun getItemCount(): Int {
            return profileList.size
        }
    }
}