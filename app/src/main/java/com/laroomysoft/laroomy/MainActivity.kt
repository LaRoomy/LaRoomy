package com.laroomysoft.laroomy

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.w3c.dom.Text

class MainActivity : AppCompatActivity(), OnDeviceListItemClickListener, BLEConnectionManager.BleEventCallback {

    private var availableDevices = ArrayList<LaRoomyDevicePresentationModel>()
    get() {
        field.clear()
        field = ApplicationProperty.bluetoothConnectionManager.bondedLaRoomyDevices
        return field
    }

    private lateinit var availableDevicesRecyclerView: RecyclerView
    private lateinit var availableDevicesViewAdapter: RecyclerView.Adapter<*>
    private lateinit var availableDevicesViewManager: RecyclerView.LayoutManager

    private var buttonNormalizationRequired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@MainActivity, this)

        (applicationContext as ApplicationProperty).uuidManager = UUIDManager(applicationContext)

        this.availableDevicesViewManager = LinearLayoutManager(this)

        this.availableDevicesViewAdapter = AvailableDevicesListAdapter(this.availableDevices, this)

        this.availableDevicesRecyclerView =
            findViewById<RecyclerView>(R.id.AvailableDevicesListView)
                .apply {
                    setHasFixedSize(true)
                    layoutManager = availableDevicesViewManager
                    adapter = availableDevicesViewAdapter
                }

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

        ApplicationProperty.bluetoothConnectionManager.checkBluetoothEnabled(this)

        if(this.buttonNormalizationRequired){

            findViewById<AppCompatImageButton>(R.id.generalSettingsButton).setImageResource(R.drawable.main_settings_norm)
            findViewById<AppCompatImageButton>(R.id.informationImageButton).setImageResource(R.drawable.main_info_norm)
            findViewById<AppCompatImageButton>(R.id.helpImageButton).setImageResource(R.drawable.main_help_norm)

            this.buttonNormalizationRequired = false
        }



        // ! realign context objects in the bluetooth manager, if this is called after a back-navigation from the Loading-Activity or so...

        // update the device-list
        updateAvailableDevices()


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
        intent.putExtra("BondedDeviceIndex", index)
        startActivity(intent)
    }

    fun onReloadImageButtonClick(view: View){

        val reloadButton = view as AppCompatImageButton
        reloadButton.setImageResource(R.drawable.main_reload_pushed)

        this.updateAvailableDevices()

        Handler(Looper.getMainLooper()).postDelayed({
            reloadButton.setImageResource(R.drawable.main_reload_norm)

        },700)

    }

    fun onHelpImageButtonClick(view: View){

        this.buttonNormalizationRequired = true
        (view as AppCompatImageButton).setImageResource(R.drawable.main_help_pushed)

        val intent = Intent(this@MainActivity, AppHelpActivity::class.java)
        startActivity(intent)
    }

    fun onInfoImageButtonClick(view: View){

        this.buttonNormalizationRequired = true
        (view as AppCompatImageButton).setImageResource(R.drawable.main_info_pushed)

        val intent = Intent(this@MainActivity, InformationActivity::class.java)
        startActivity(intent)
    }

    fun onSettingsImageButtonClick(view: View){

        this.buttonNormalizationRequired = true
        (view as AppCompatImageButton).setImageResource(R.drawable.main_settings_pushed)

        val intent = Intent(this@MainActivity, AppSettingsActivity::class.java)
        startActivity(intent)
    }

    private fun notifyUser(message: String, type: Int){
        val notificationView = findViewById<TextView>(R.id.MA_UserNotificationView)
        notificationView.text = message

        when(type){
            ERROR_MESSAGE -> notificationView.setTextColor(getColor(R.color.ErrorColor))
            WARNING_MESSAGE -> notificationView.setTextColor(getColor(R.color.WarningColor))
            INFO_MESSAGE -> notificationView.setTextColor(getColor(R.color.InfoColor))
            else -> notificationView.setTextColor(getColor(R.color.InfoColor))
        }
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
        this.availableDevices = ApplicationProperty.bluetoothConnectionManager.bondedLaRoomyDevices
        if(this.availableDevices.size == 0){
            // TODO
            //findViewById<TextView>(R.id.AvailableDevicesTextView).text = getString(R.string.MA_NoAvailableDevices)
        }
        else {
            // TODO
            //findViewById<TextView>(R.id.AvailableDevicesTextView).text = getString(R.string.MA_AvailableDevicesPresentationTextViewText)
        }
        this.availableDevicesViewAdapter.notifyDataSetChanged()
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

        class DSLRViewHolder(val linearLayout: LinearLayout) :
            RecyclerView.ViewHolder(linearLayout){

            fun bind(data: LaRoomyDevicePresentationModel, deviceListItemClick: OnDeviceListItemClickListener, position: Int){
                itemView.setOnClickListener{
                    deviceListItemClick.onItemClicked(position, data)
                }
            }
        }


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DSLRViewHolder {

            val linearLayout =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.available_device_list_item, parent, false) as LinearLayout

            return DSLRViewHolder(linearLayout)
        }

        override fun onBindViewHolder(holder: DSLRViewHolder, position: Int) {
            holder.linearLayout.findViewById<TextView>(R.id.deviceNameTextView).text = laRoomyDevListAdapter[position].name

            // currently there is only one image. In the future there must be implemented multiple images for multiple device-types
            // TODO: set the appropriate image for the device type

            holder.bind(laRoomyDevListAdapter[position], deviceListItemClickListener, position)
        }

        override fun getItemCount(): Int {
            return laRoomyDevListAdapter.size
        }
    }

    // Interface methods:
    override fun onComponentError(message: String) {
        notifyUser(message, ERROR_MESSAGE)
    }
}
