package com.laroomysoft.laroomy

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.lang.invoke.ConstantCallSite

@SuppressLint("RestrictedApi")
class MainActivity : AppCompatActivity(), OnDeviceListItemClickListener, BLEConnectionManager.BleEventCallback, MenuBuilder.Callback {

    private var availableDevices = ArrayList<LaRoomyDevicePresentationModel>()
    get() {
        field.clear()

        for (device in (applicationContext as ApplicationProperty).addedDevices.devices) {
            val dev = LaRoomyDevicePresentationModel()
            dev.address = device.macAddress
            dev.name = device.name
            dev.image = deviceImageFromName(device.name)

            field.add(dev)
        }
        return field
    }

    private lateinit var availableDevicesRecyclerView: RecyclerView
    private lateinit var availableDevicesViewAdapter: RecyclerView.Adapter<*>
    private lateinit var availableDevicesViewManager: RecyclerView.LayoutManager

    private lateinit var addDeviceButton: AppCompatImageButton
    private lateinit var bottomSeparator: View

    //private var buttonNormalizationRequired = false

    private var addButtonNormalizationRequired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ApplicationProperty.bluetoothConnectionManager.reAlignContextReferences(this@MainActivity, this)

        (applicationContext as ApplicationProperty).uuidManager = UUIDManager(applicationContext)
        (applicationContext as ApplicationProperty).addedDevices = AddedDevices(applicationContext)

        // get UI Elements
        this.addDeviceButton = findViewById(R.id.mainActivityAddDeviceImageButton)
        this.bottomSeparator = findViewById(R.id.mainActivityBottomSeparatorView)

        this.availableDevicesViewManager = LinearLayoutManager(this)

        this.availableDevicesViewAdapter = AvailableDevicesListAdapter(this.availableDevices, this)

        this.availableDevicesRecyclerView =
            findViewById<RecyclerView>(R.id.AvailableDevicesListView)
                .apply {
                    setHasFixedSize(true)
                    layoutManager = availableDevicesViewManager
                    adapter = availableDevicesViewAdapter
                }

        val swipeHandler = object : SwipeToDeleteCallback(this){
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                (availableDevicesViewAdapter as AvailableDevicesListAdapter).removeAt(viewHolder.position)
                (applicationContext as ApplicationProperty).addedDevices.removeAt(viewHolder.position)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(availableDevicesRecyclerView)

        // TODO: if there are no devices, present the user a call to action (add your first device...!)


        // save a default passkey if no one is saved (this should only be executed once per installation)
        if((applicationContext as ApplicationProperty).loadSavedStringData(R.string.FileKey_AppSettings, R.string.DataKey_DefaultRandomBindingPasskey) == ERROR_NOTFOUND){
            val defaultKey = createRandomPasskey(10)
            (applicationContext as ApplicationProperty).saveStringData(defaultKey, R.string.FileKey_AppSettings, R.string.DataKey_DefaultRandomBindingPasskey)
        }

        // schedule the auto-connect process if required
        if ((applicationContext as ApplicationProperty).loadBooleanData(
                R.string.FileKey_AppSettings,
                R.string.DataKey_AutoConnect
            )
        ) {
            // check if there are more than one bonded device
            when (this.availableDevices.size) {
                0 -> {
                    // do nothing
                }
                1 -> {
                    // there is only one available device -> connect to it!
                    Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this@MainActivity, LoadingActivity::class.java)
                    intent.putExtra("BondedDeviceIndex", 0)
                    startActivity(intent)
                    }, 1000)
                }
                else -> {
                    // try to connect to the last successful connected device
                    if (ApplicationProperty.bluetoothConnectionManager.isLastAddressValid) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            // move forward with delay...!
                            val intent = Intent(this@MainActivity, LoadingActivity::class.java)
                            intent.putExtra("BondedDeviceIndex", -2)// -2 means: connect to the last device
                            startActivity(intent)

                        }, 1000)
                    }
                }
            }
        }
    }

//    override fun onStart() {
//        super.onStart()
//
//        //ApplicationProperty.bluetoothConnectionManger.checkBluetoothEnabled()
//    }

    override fun onResume() {
        super.onResume()

        bottomSeparator.visibility = when(this.resources.configuration.orientation){
            Configuration.ORIENTATION_LANDSCAPE -> View.GONE
            else -> View.VISIBLE
        }


        ApplicationProperty.bluetoothConnectionManager.checkBluetoothEnabled(this)

//        if(this.buttonNormalizationRequired){
//
//            findViewById<AppCompatImageButton>(R.id.generalSettingsButton).setImageResource(R.drawable.main_settings_norm)
//            findViewById<AppCompatImageButton>(R.id.informationImageButton).setImageResource(R.drawable.main_info_norm)
//            findViewById<AppCompatImageButton>(R.id.helpImageButton).setImageResource(R.drawable.main_help_norm)
//
//            this.buttonNormalizationRequired = false
//        }

        if(addButtonNormalizationRequired){
            addButtonNormalizationRequired = false
            addDeviceButton.setImageResource(R.drawable.ic_add_white_36dp)
        }



        // ! realign context objects in the bluetooth manager, if this is called after a back-navigation from the Loading-Activity or so...

        ApplicationProperty.bluetoothConnectionManager.reAlignContextReferences(this@MainActivity, this)


        // update the device-list
        //updateAvailableDevices()

        if((applicationContext as ApplicationProperty).mainActivityListElementWasAdded){
            (applicationContext as ApplicationProperty).mainActivityListElementWasAdded = false
            this.availableDevicesViewAdapter.notifyItemInserted(this.availableDevices.size - 1)
        }

        this.resetSelectionInDeviceListView()


        //reset the selected color
    }

    override fun onPause() {
        super.onPause()

        // TODO: if the loading activity fails, the main activity must be re-initiated

        //finish()// questionable!!!!!!!!!!!!!!!
    }

    override fun onItemClicked(index: Int, data: LaRoomyDevicePresentationModel) {

//        val ll = availableDevicesViewManager.findViewByPosition(index) as? LinearLayout
//        val leftView = ll?.findViewById<View>(R.id.leftBorderView)
//        val rightView = ll?.findViewById<View>(R.id.rightBorderView)
//
//        leftView?.setBackgroundColor(getColor(R.color.selectedSeparatorColor))
//        rightView?.setBackgroundColor(getColor(R.color.selectedSeparatorColor))

        setItemColor(index, R.color.selectedSeparatorColor)

        //ll?.setBackgroundColor(getColor(R.color.colorPrimary))
        //ll?.setBackgroundColor()

        val intent = Intent(this@MainActivity, LoadingActivity::class.java)
        intent.putExtra("DeviceListIndex", index)
        startActivity(intent)
        //overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

//    fun onReloadImageButtonClick(view: View){
//
//        val reloadButton = view as AppCompatImageButton
//        reloadButton.setImageResource(R.drawable.main_reload_pushed)
//
//        this.updateAvailableDevices()
//
//        Handler(Looper.getMainLooper()).postDelayed({
//            reloadButton.setImageResource(R.drawable.main_reload_norm)
//
//        },700)
//
//    }

//    fun onHelpImageButtonClick(view: View){
//
//        this.buttonNormalizationRequired = true
//        (view as AppCompatImageButton).setImageResource(R.drawable.main_help_pushed)
//
//        val intent = Intent(this@MainActivity, AppHelpActivity::class.java)
//        startActivity(intent)
//    }

//    fun onInfoImageButtonClick(view: View){
//
//        this.buttonNormalizationRequired = true
//        (view as AppCompatImageButton).setImageResource(R.drawable.main_info_pushed)
//
//        val intent = Intent(this@MainActivity, InformationActivity::class.java)
//        startActivity(intent)
//    }

//    fun onSettingsImageButtonClick(view: View){
//
//        this.buttonNormalizationRequired = true
//        (view as AppCompatImageButton).setImageResource(R.drawable.main_settings_pushed)
//
//        val intent = Intent(this@MainActivity, AppSettingsActivity::class.java)
//        startActivity(intent)
//    }

    fun onMainActivityMenuButtonClick(view: View) {

        this.availableDevicesRecyclerView.alpha = 0.2f
        findViewById<AppCompatImageButton>(R.id.mainActivityHamburgerButton).setImageResource(R.drawable.outline_menu_yellow_36dp)

        val menuBuilder = MenuBuilder(this)
        menuBuilder.setCallback(this)

        val popupMenu =
            PopupMenu(this, view)

        if ((applicationContext as ApplicationProperty).loadBooleanData(
                R.string.FileKey_AppSettings,
                R.string.DataKey_EnableLog
            )
        ) {
            popupMenu.menuInflater.inflate(R.menu.main_activity_popup_menu_withlog, menuBuilder)
        } else {
            popupMenu.menuInflater.inflate(R.menu.main_activity_popup_menu_nolog, menuBuilder)
        }


        val menuPopupHelper = MenuPopupHelper(this, menuBuilder, view)
        menuPopupHelper.setForceShowIcon(true)
        menuPopupHelper.setOnDismissListener {
            this.availableDevicesRecyclerView.alpha = 1f
            findViewById<AppCompatImageButton>(R.id.mainActivityHamburgerButton).setImageResource(R.drawable.ic_menu_white_36dp)
        }
        menuPopupHelper.show()
    }

    override fun onMenuItemSelected(menu: MenuBuilder, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.helpMenuItem -> {
                val intent = Intent(this@MainActivity, AppHelpActivity::class.java)
                startActivity(intent)
            }
            R.id.settingsMenuItem -> {
                val intent = Intent(this@MainActivity, AppSettingsActivity::class.java)
                startActivity(intent)
            }
            R.id.informationMenuItem -> {
                val intent = Intent(this@MainActivity, InformationActivity::class.java)
                startActivity(intent)
            }
            R.id.showLogMenuItem -> {
                val intent = Intent(this@MainActivity, ViewLogActivity::class.java)
                startActivity(intent)
            }
            R.id.reloadListMenuItem -> {
                updateAvailableDevices()
            }
        }
        return true
    }

    override fun onMenuModeChange(menu: MenuBuilder) {}


    private fun notifyUser(message: String, type: Int){
//        val notificationView = findViewById<TextView>(R.id.MA_UserNotificationView)
//        notificationView.text = message
//
//        when(type){
//            ERROR_MESSAGE -> notificationView.setTextColor(getColor(R.color.ErrorColor))
//            WARNING_MESSAGE -> notificationView.setTextColor(getColor(R.color.WarningColor))
//            INFO_MESSAGE -> notificationView.setTextColor(getColor(R.color.InfoColor))
//            else -> notificationView.setTextColor(getColor(R.color.InfoColor))
//        }
    }

    private fun setItemColor(index: Int, colorID: Int){
        val ll = availableDevicesViewManager.findViewByPosition(index) as? LinearLayout
        //val leftView = ll?.findViewById<View>(R.id.leftBorderView)
        //val rightView = ll?.findViewById<View>(R.id.rightBorderView)

        val textView = ll?.findViewById<TextView>(R.id.deviceNameTextView)

        //leftView?.setBackgroundColor(getColor(colorID))
        //rightView?.setBackgroundColor(getColor(colorID))

        textView?.setTextColor(getColor(colorID))

    }

    private fun updateAvailableDevices(){

        //this.availableDevices = ApplicationProperty.bluetoothConnectionManager.bondedLaRoomyDevices

        //this.availableDevicesViewAdapter = AvailableDevicesListAdapter(this.availableDevices, this)

       //this.availableDevicesViewAdapter.notifyDataSetChanged()

        //this.availableDevices.clear()

//        val devList = ArrayList<LaRoomyDevicePresentationModel>()
//
//        for (device in (applicationContext as ApplicationProperty).addedDevices.devices) {
//            val dev = LaRoomyDevicePresentationModel()
//            dev.address = device.macAddress
//            dev.name = device.name
//
//            devList.add(dev)
//        }
//
//        this.availableDevices = devList



        if(this.availableDevices.size == 0){
            // TODO
            //findViewById<TextView>(R.id.AvailableDevicesTextView).text = getString(R.string.MA_NoAvailableDevices)
        }
        else {
            // TODO
            //findViewById<TextView>(R.id.AvailableDevicesTextView).text = getString(R.string.MA_AvailableDevicesPresentationTextViewText)
        }
        //this.availableDevicesViewAdapter.notifyDataSetChanged()
        this.resetSelectionInDeviceListView()
    }

    private fun resetSelectionInDeviceListView(){
        for(x in 0 until this.availableDevices.size){
            setItemColor(x, R.color.separatorColor)
        }
    }

    class AvailableDevicesListAdapter(
        private val laRoomyDevListAdapter : ArrayList<LaRoomyDevicePresentationModel>,
        private val deviceListItemClickListener: OnDeviceListItemClickListener
    ) : RecyclerView.Adapter<AvailableDevicesListAdapter.DSLRViewHolder>() {

        class DSLRViewHolder(val constraintLayout: ConstraintLayout) :
            RecyclerView.ViewHolder(constraintLayout){

            fun bind(data: LaRoomyDevicePresentationModel, deviceListItemClick: OnDeviceListItemClickListener, position: Int){
                itemView.setOnClickListener{
                    deviceListItemClick.onItemClicked(position, data)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DSLRViewHolder {

            val constraintLayout =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.my_devices_list_element, parent, false) as ConstraintLayout

            return DSLRViewHolder(constraintLayout)
        }

        override fun onBindViewHolder(holder: DSLRViewHolder, position: Int) {
            // set the device-name
            holder.constraintLayout.findViewById<TextView>(R.id.deviceNameTextView).text = laRoomyDevListAdapter[position].name
            // set the appropriate image for the device type
            holder.constraintLayout.findViewById<ImageView>(R.id.myDevicesListElementImageView).setImageResource(laRoomyDevListAdapter[position].image)

            holder.bind(laRoomyDevListAdapter[position], deviceListItemClickListener, position)
        }

        override fun getItemCount(): Int {
            return laRoomyDevListAdapter.size
        }

        fun removeAt(position: Int){
            laRoomyDevListAdapter.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    // Interface methods:
    override fun onComponentError(message: String) {
        notifyUser(message, ERROR_MESSAGE)
    }

    fun onMainActivityAddDeviceButtonClick(view: View) {

        addDeviceButton.setImageResource(R.drawable.outline_add_yellow_36dp)
        addButtonNormalizationRequired = true

        val intent = Intent(this@MainActivity, AddDeviceActivity::class.java)
        startActivity(intent)
    }

}
