package com.laroomysoft.laroomy

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), OnDeviceListItemClickListener, BLEConnectionManager.BleEventCallback {

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
    private lateinit var headerSeparator: View
    private lateinit var noContentContainer: ConstraintLayout
    private lateinit var popUpWindow: PopupWindow

    //private var buttonNormalizationRequired = false

    private var addButtonNormalizationRequired = false
    private var preventListSelection = false
    private var preventMenuButtonDoubleExecution = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)

        (applicationContext as ApplicationProperty).uuidManager = UUIDManager(applicationContext)
        (applicationContext as ApplicationProperty).addedDevices = AddedDevices(applicationContext)

        // get UI Elements
        this.addDeviceButton = findViewById(R.id.mainActivityAddDeviceImageButton)
        this.bottomSeparator = findViewById(R.id.mainActivityBottomSeparatorView)
        this.headerSeparator = findViewById(R.id.mainActivityHeaderSeparatorView)
        this.noContentContainer = findViewById(R.id.mainActivityNoContentContainer)

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

                val pos =
                    viewHolder.absoluteAdapterPosition

                (availableDevicesViewAdapter as AvailableDevicesListAdapter).removeAt(pos)//viewHolder.position)
                (applicationContext as ApplicationProperty).addedDevices.removeAt(pos)//viewHolder.position)

                if((applicationContext as ApplicationProperty).addedDevices.devices.isEmpty()){
                    availableDevicesRecyclerView.visibility = View.GONE
                    noContentContainer.visibility = View.VISIBLE
                }
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(availableDevicesRecyclerView)

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

        // control bottom separator in alignment to the screen orientation
        bottomSeparator.visibility = when(this.resources.configuration.orientation){
            Configuration.ORIENTATION_LANDSCAPE -> View.GONE
            else -> View.VISIBLE
        }

        // check if bluetooth is enabled
        ApplicationProperty.bluetoothConnectionManager.checkBluetoothEnabled(this)


        // normalize controls on back navigation
        if(addButtonNormalizationRequired){
            addButtonNormalizationRequired = false
            addDeviceButton.setImageResource(R.drawable.ic_add_white_36dp)
        }

        // realign context objects in the bluetooth manager, if this is called after a back-navigation from the Loading-Activity or so...
        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)

        // show or hide the no-content hint if the list is empty or not
        if(this.availableDevices.isEmpty()){
            this.availableDevicesRecyclerView.visibility = View.GONE
            this.noContentContainer.visibility = View.VISIBLE
        } else {
            this.availableDevicesRecyclerView.visibility = View.VISIBLE
            this.noContentContainer.visibility = View.GONE
        }

        if((applicationContext as ApplicationProperty).mainActivityListElementWasAdded){
            (applicationContext as ApplicationProperty).mainActivityListElementWasAdded = false
            this.availableDevicesViewAdapter.notifyItemInserted(this.availableDevices.size - 1)
        }

        // reset the selection in the recyclerView
        this.resetSelectionInDeviceListView()
    }

//    override fun onPause() {
//        super.onPause()
//
//        // TODO: if the loading activity fails, the main activity must be re-initiated
//
//        //finish()// questionable!!!!!!!!!!!!!!!
//
//    }

    override fun onItemClicked(index: Int, data: LaRoomyDevicePresentationModel) {

        if(!preventListSelection) {

            setItemColors(
                index,
                R.color.fullWhiteTextColor,
                R.drawable.my_devices_list_element_selected_background
            )

            val intent =
                Intent(this@MainActivity, LoadingActivity::class.java)
            intent.putExtra("DeviceListIndex", index)
            startActivity(intent)

            //overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    @SuppressLint("InflateParams")
    fun onMainActivityMenuButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        // if the popup is open, exit
        if(preventMenuButtonDoubleExecution){
            return
        }

        // shade the background
        this.availableDevicesRecyclerView.alpha = 0.2f
        this.noContentContainer.alpha = 0.2f
        findViewById<AppCompatImageButton>(R.id.mainActivityHamburgerButton).setImageResource(R.drawable.ic_menu_yellow_36dp)

        // block the invocation of a list element during the popup lifecycle
        this.preventListSelection = true
        // block the double execution of the menu button
        this.preventMenuButtonDoubleExecution = true

        val layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        val headerViewPos = intArrayOf(0, 0)
        this.headerSeparator.getLocationInWindow(headerViewPos)

        val popUpView =
            layoutInflater.inflate(R.layout.main_activity_popup_flyout, null)
        this.popUpWindow =
            PopupWindow(
                popUpView,
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                true
            )

        this.popUpWindow.animationStyle = R.style.sideSlidePopUpAnimationStyle

        this.popUpWindow.setOnDismissListener {
            // normalize the button and the main activity alpha, but do it with delay (500 ms for the popup animation time!)
            Executors.newSingleThreadScheduledExecutor().schedule({
                availableDevicesRecyclerView.alpha = 1f
                noContentContainer.alpha = 1f
                findViewById<AppCompatImageButton>(R.id.mainActivityHamburgerButton).setImageResource(
                    R.drawable.ic_menu_white_36dp
                )
                preventListSelection = false
                preventMenuButtonDoubleExecution = false
            }, 300, TimeUnit.MILLISECONDS)
        }

        this.popUpWindow.showAtLocation(
            this.availableDevicesRecyclerView,
            Gravity.NO_GRAVITY, 0, headerViewPos.elementAt(1) + 5
        )
    }

    private fun notifyUser(message: String){
//        val notificationView = findViewById<TextView>(R.id.MA_UserNotificationView)
//        notificationView.text = message
//
//        when(type){
//            ERROR_MESSAGE -> notificationView.setTextColor(getColor(R.color.ErrorColor))
//            WARNING_MESSAGE -> notificationView.setTextColor(getColor(R.color.WarningColor))
//            INFO_MESSAGE -> notificationView.setTextColor(getColor(R.color.InfoColor))
//            else -> notificationView.setTextColor(getColor(R.color.InfoColor))
//        }

        val dialog = AlertDialog.Builder(this)
        dialog.setMessage(message)
        dialog.setIcon(R.drawable.ic_announcement_white_36dp)
        dialog.setTitle(R.string.MA_UserNotificationDialogTitle)
        dialog.setPositiveButton(R.string.GeneralString_OK) { dialogInterface: DialogInterface, _: Int ->
            dialogInterface.dismiss()
        }
        dialog.create()
        dialog.show()

    }

    private fun setItemColors(index: Int, textColorID: Int, backGroundDrawable: Int){
        val ll = availableDevicesViewManager.findViewByPosition(index) as? ConstraintLayout
        //val leftView = ll?.findViewById<View>(R.id.leftBorderView)
        //val rightView = ll?.findViewById<View>(R.id.rightBorderView)

        val textView = ll?.findViewById<AppCompatTextView>(R.id.deviceNameTextView)

        //leftView?.setBackgroundColor(getColor(colorID))
        //rightView?.setBackgroundColor(getColor(colorID))

        textView?.setTextColor(getColor(textColorID))

        ll?.background = AppCompatResources.getDrawable(this, backGroundDrawable)


    }

    private fun resetSelectionInDeviceListView(){
        for(x in 0 until this.availableDevices.size){
            setItemColors(x, R.color.fullWhiteTextColor, R.drawable.my_devices_list_element_background)
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
        notifyUser(message)
    }

    fun onMainActivityAddDeviceButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        addDeviceButton.setImageResource(R.drawable.ic_add_yellow_36dp)
        addButtonNormalizationRequired = true

        val intent = Intent(this@MainActivity, AddDeviceActivity::class.java)
        startActivity(intent)
    }

    fun onMainActivityPopUpButtonClick(view: View) {

        // the background only changes if the dismiss() method of the popup will be executed async or not in that method!
        //(view as ConstraintLayout).background = AppCompatResources.getDrawable(this.popUpWindow.contentView.context, R.drawable.main_activity_popup_selected_item_background)

        when (view.id) {
            R.id.mainActivityPopUpElement_HelpContainer -> {
                val intent = Intent(this@MainActivity, AppHelpActivity::class.java)
                startActivity(intent)
            }
            R.id.mainActivityPopUpElement_SettingsContainer -> {
                val intent = Intent(this@MainActivity, AppSettingsActivity::class.java)
                startActivity(intent)
            }
            R.id.mainActivityPopUpElement_InfoContainer -> {
                val intent = Intent(this@MainActivity, InformationActivity::class.java)
                startActivity(intent)
            }
            R.id.mainActivityPopUpElement_ShowLogContainer -> {
                val intent = Intent(this@MainActivity, ViewLogActivity::class.java)
                startActivity(intent)
            }
        }
        try {
            this.popUpWindow.dismiss()
        } catch(e: Exception){}
    }

    fun noMainActivityNoContentContainerClick(@Suppress("UNUSED_PARAMETER")view: View) {

        val intent = Intent(this@MainActivity, AddDeviceActivity::class.java)
        startActivity(intent)
    }
}
