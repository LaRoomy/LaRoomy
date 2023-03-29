package com.laroomysoft.laroomy

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), OnDeviceListItemClickListener {

    private val mainActivityNormalState = 0
    private val mainActivityBluetoothNotEnabledState = 1
    private val mainActivityBluetoothPermissionMissingState = 2
    
    private val appProperty
    get() = (this.applicationContext as ApplicationProperty)

    private var availableDevices = ArrayList<LaRoomyDevicePresentationModel>()
    get() {
        field.clear()

        for (device in (applicationContext as ApplicationProperty).addedDevices.devices) {
            field.add(device.toPresentationModel((applicationContext as ApplicationProperty).isPremiumAppVersion))
        }
        return field
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if(verboseLog){
                Log.d("MA:requestPermissionLauncher", "Permission-Request result is: $isGranted")
            }
            this.bluetoothPermissionGranted = isGranted
            this.grantPermissionRequestReclined = !isGranted    // prevent a request loop in onResume
            // if the permission dialog is dismissed, the onResume method is invoked again, and bluetooth enabled state is checked
            // when the permission request is reclined, the UI must go in missing-permission-state, and prevent the invocation
            // of the permission request launcher in onResume, otherwise there will be an infinite loop between onResume and permission launcher
        }

    private var requestEnableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if(it.resultCode == Activity.RESULT_CANCELED){
                // the user reclined the request to enable bluetooth
                this.activatingBluetoothReclined = true
                if(verboseLog){
                    Log.d("MA:BluetoothEnableRequest", "User reclined the request to enable Bluetooth.")
                }
            } else {
                if(verboseLog){
                    Log.d("MA:onResume", "User has accepted the request to enable Bluetooth.")
                }
                this.isBluetoothEnabled = true
                this.activatingBluetoothReclined = false
            }
        }

    private lateinit var availableDevicesRecyclerView: RecyclerView
    private lateinit var availableDevicesViewAdapter: RecyclerView.Adapter<*>
    private lateinit var availableDevicesViewManager: RecyclerView.LayoutManager

    private lateinit var addDeviceButton: AppCompatImageButton
    private lateinit var bottomSeparator: View
    private lateinit var headerSeparator: View
    private lateinit var noContentContainer: ConstraintLayout
    private lateinit var noContentImageView: AppCompatImageView
    private lateinit var noContentTextView: AppCompatTextView
    private lateinit var popUpWindow: PopupWindow
    private lateinit var moreInfoLink: AppCompatTextView
    private lateinit var hideLink: AppCompatTextView
    private lateinit var actionBanner: ConstraintLayout
    private lateinit var actionTextView: AppCompatTextView
    private lateinit var actionBannerImage: AppCompatImageView

    private var addButtonNormalizationRequired = false
    private var preventListSelection = false
    private var preventMenuButtonDoubleExecution = false
    private var bluetoothPermissionGranted = true

    private var activatingBluetoothReclined = false
    private var grantPermissionRequestReclined = false

    private var isBluetoothEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // first check if the design was overridden by the user and change it if necessary
        val userDesignSelection = (applicationContext as ApplicationProperty).loadSavedStringData(
            R.string.FileKey_AppSettings,
            R.string.DataKey_DesignSelection,
            getString(R.string.DefaultValue_DesignSelection)
        )
        if (userDesignSelection != getString(R.string.DefaultValue_DesignSelection)) {
            if (userDesignSelection == "light") {
                AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO)
            } else {
                // userDesignSelection == "dark"
                AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)
            }
        }
        
        // do system initialization
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContentView(R.layout.activity_main)
    
        // init classes
        (applicationContext as ApplicationProperty).uuidManager = UUIDManager(applicationContext)
        (applicationContext as ApplicationProperty).addedDevices = AddedDevices(applicationContext)

        // get UI Elements
        this.addDeviceButton = findViewById(R.id.mainActivityAddDeviceImageButton)
        this.bottomSeparator = findViewById(R.id.mainActivityBottomSeparatorView)
        this.headerSeparator = findViewById(R.id.mainActivityHeaderSeparatorView)
        this.noContentContainer = findViewById(R.id.mainActivityNoContentContainer)
        this.noContentImageView = findViewById(R.id.mainActivityNoContentImageView)
        this.noContentTextView = findViewById(R.id.mainActivityNoContentTextView)
        this.actionBanner = findViewById(R.id.mainActivityPremiumActionBanner)
        this.actionTextView = findViewById(R.id.mainActivityPremiumBannerActionTextView)
        this.actionBannerImage = findViewById(R.id.mainActivityPremiumActionBannerImage)
        
        // add more info link listener
        this.moreInfoLink = findViewById<AppCompatTextView?>(R.id.mainActivityPremiumActionBannerMoreInfoLink).apply {
            setOnClickListener {
                onActionBannerMoreInfoLinkClicked()
            }
        }
        // add hide link listener
        this.hideLink = findViewById<AppCompatTextView?>(R.id.mainActivityPremiumActionBannerHideLink).apply {
            setOnClickListener {
                onActionBannerHideLinkClicked()
            }
        }
        
        // save UI Mode to application property
        (applicationContext as ApplicationProperty).isNightMode =
            when (this.addDeviceButton.context.resources.configuration.uiMode.and(Configuration.UI_MODE_NIGHT_MASK)) {
                Configuration.UI_MODE_NIGHT_YES -> true
                else -> false
            }
        
        // init the premium manager and check if the user has purchased
        (applicationContext as ApplicationProperty).premiumManager.checkPremiumAppStatus()

        // init recycler view
        this.availableDevicesViewManager = LinearLayoutManager(this)
        this.availableDevicesViewAdapter = AvailableDevicesListAdapter(this.availableDevices, this, this.appProperty)
        this.availableDevicesRecyclerView =
            findViewById<RecyclerView>(R.id.AvailableDevicesListView)
                .apply {
                    setHasFixedSize(true)
                    layoutManager = availableDevicesViewManager
                    adapter = availableDevicesViewAdapter
                }
        // define a swipe handler for the recycler view
        val swipeHandler = object : SwipeToDeleteCallback(this){
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // get item position in adapter
                val pos =
                    viewHolder.absoluteAdapterPosition
                // get mac address of item
                val mac = (applicationContext as ApplicationProperty).addedDevices.getMacAddressAt(pos)
                // remove the item in the recycler view
                (availableDevicesViewAdapter as AvailableDevicesListAdapter).removeAt(pos)
                // remove the saved device from the added devices
                (applicationContext as ApplicationProperty).addedDevices.removeAt(pos)
                // remove the saved property data for the device (if it was saved)
                PropertyCacheManager(applicationContext).removePCacheEntry(mac)

                if((applicationContext as ApplicationProperty).addedDevices.devices.isEmpty()){
                    availableDevicesRecyclerView.visibility = View.GONE
                    noContentContainer.visibility = View.VISIBLE
                }
            }
        }
        // attach the swipe handler to the recycler view
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(availableDevicesRecyclerView)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // the app was brought to foreground by the sharingReceiverActivity
        // if there was an activity on top of the stack, it was killed without clearing data, so here we make sure the bleManager is cleared
        ApplicationProperty.bluetoothConnectionManager.clear()
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog){
            Log.d("MA:onResume", "onResume executed in MainActivity")
        }
        // update the global app params
        (applicationContext as ApplicationProperty).onResume()
        
        // reset property related control parameter
        (applicationContext as ApplicationProperty).resetPropertyControlParameter()

        // control bottom separator in alignment to the screen orientation
        bottomSeparator.visibility = when (this.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> View.GONE
            else -> View.VISIBLE
        }
        
        // check if this was a back navigation
        var wasBackNavigation = false
        if((applicationContext as ApplicationProperty).isBackNavigationToMain){
            (applicationContext as ApplicationProperty).isBackNavigationToMain = false
            wasBackNavigation = true
        }

        // check bluetooth connect permission:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if(verboseLog){
                Log.d("MA:onResume", "This is Android 12 or higher, so permission checkup is required")
            }

            if(!this.grantPermissionRequestReclined) {// prevent a successive permission request, if the user has reclined
                this.bluetoothPermissionGranted = if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    if(verboseLog){
                        Log.d("MA:onResume", "Permission is not granted. Execution permission request to user.")
                    }
                    this.requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    false
                } else {
                    if(verboseLog){
                        Log.d("MA:onResume", "Permission is granted.")
                    }
                    true
                }
            }
        }

        // proceed after permission checkup
        if(this.bluetoothPermissionGranted) {
            if(verboseLog){
                Log.d("MA:onResume", "Check bluetooth enabled state")
            }
            // check if bluetooth is enabled
            when (ApplicationProperty.bluetoothConnectionManager.checkBluetoothEnabled()) {
                BLE_BLUETOOTH_PERMISSION_MISSING -> {
                    this.bluetoothPermissionGranted = false
                    if(verboseLog){
                        Log.e("MA:onResume", "Unexpected error while check bluetooth enabled state!")
                    }
                }
                BLE_IS_ENABLED -> {
                    if(verboseLog){
                        Log.d("MA:onResume", "Bluetooth is enabled!")
                    }
                    this.bluetoothPermissionGranted = true
                    this.isBluetoothEnabled = true
                    this.setUIState(mainActivityNormalState)
                    if(!wasBackNavigation){
                        autoConnect()
                    }
                }
                BLE_IS_DISABLED -> {
                    if(verboseLog){
                        Log.d("MA:onResume", "Bluetooth is disabled!")
                    }
                    this.bluetoothPermissionGranted = true

                    // the permission is ok, but is bluetooth enabled?
                    if (!this.activatingBluetoothReclined) {
                        if(verboseLog){
                            Log.d("MA:onResume", "Launching request to enable Bluetooth.")
                        }
                        // the request to enable bluetooth was not previously displayed, so do it now
                        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        requestEnableBluetoothLauncher.launch(intent)
                    } else {
                        if(verboseLog){
                            Log.d("MA:onResume", "Request to enable Bluetooth was previously reclined. Setting UI-State to Bluetooth-Disabled-State!")
                        }
                        // set UI State to bluetooth disabled, because the user reclined the request to enable
                        this.setUIState(mainActivityBluetoothNotEnabledState)
                        this.isBluetoothEnabled = false
                    }
                }
            }
        } else {
            // bluetooth permission was rejected, so set the UI to missing-permission-state
            this.setUIState(mainActivityBluetoothPermissionMissingState)
        }

        // normalize controls on back navigation
        if (addButtonNormalizationRequired) {
            addButtonNormalizationRequired = false
            addDeviceButton.setImageResource(R.drawable.ic_add_36dp)
        }

        // show or hide the no-content hint if the list is empty or not, but only if bluetooth is enabled, otherwise this must be displayed
        if(isBluetoothEnabled) {
            if (this.availableDevices.isEmpty()) {
                this.availableDevicesRecyclerView.visibility = View.GONE
                this.noContentContainer.visibility = View.VISIBLE
            } else {
                this.availableDevicesRecyclerView.visibility = View.VISIBLE
                this.noContentContainer.visibility = View.GONE
            }
        }

        // this param is set by the add-device-activity to add the new item on back-navigation
        if ((applicationContext as ApplicationProperty).mainActivityListElementWasAdded) {
            (applicationContext as ApplicationProperty).mainActivityListElementWasAdded =
                false
            this.availableDevicesViewAdapter.notifyItemInserted(this.availableDevices.size - 1)
        }
    
        // show / hide the action banner and set the content based on the current premium status (testVersion/unPaid/Premium)
        this.controlActionBannerVisibleState()
    
        // reset the selection in the recyclerView
        this.resetSelectionInDeviceListView()
    }

    override fun onItemClicked(index: Int, data: LaRoomyDevicePresentationModel) {
        if(verboseLog){
            Log.d("MainActivity", "List Element Clicked at index: $index with name: ${data.name} and address: ${data.address}")
        }
        // check non-premium condition
        if(!appProperty.premiumManager.isPremiumAppVersion && index > NON_PREMIUM_MAX_DEVICE_LIST_INDEX){
            // the user has not purchased and test period is over
            // so bounce the premium banner and do nothing else
            val bannerAnimation = AnimationUtils.loadAnimation(applicationContext, R.anim.bounce)
            this.actionBanner.startAnimation(bannerAnimation)
        } else {
            // normal execution
            if (!preventListSelection) {
                setItemColors(
                    index,
                    R.color.max_contrast_text_color,
                    R.drawable.my_devices_list_element_selected_background
                )
        
                val intent =
                    Intent(this@MainActivity, LoadingActivity::class.java)
                intent.putExtra("DeviceListIndex", index)
                startActivity(intent)
        
                // set param for auto-connect function
                (applicationContext as ApplicationProperty).isBackNavigationToMain = true
            }
        }
    }
    
    private fun onActionBannerMoreInfoLinkClicked(){
        // this link is always the same and opens the more info page
        val intent = Intent(this@MainActivity, PremiumInfoActivity::class.java)
        startActivity(intent)
    }
    
    private fun onActionBannerHideLinkClicked(){
        if(this.appProperty.premiumManager.isTestPeriodActive){
            // when the hide link is clicked while the test-period is active, the user wants to hide the banner
            this.actionBanner.apply {
                // hide the banner
                visibility = View.GONE
            }
            // save the users choice (the banner is not shown again before the test-period is over)
            this.appProperty.saveBooleanData(true, R.string.FileKey_PremVersion, R.string.DataKey_HideMainActivityTestBanner)
        } else {
            // the link is clicked in a non-purchased app-state, so this is the PURCHASE button
            // -> start the app store billing process
            
            // TODO: implement the billing process here
        }
    }
    
    private fun controlActionBannerVisibleState(){
        this.actionBanner.apply {
            if(appProperty.premiumManager.userHasPurchased){
                // the user has purchased, so hide the banner and do nothing else
                this.visibility = View.GONE
            } else {
                // the user has not purchased, so this must be unpaid or test version
                if ((applicationContext as ApplicationProperty).premiumManager.isTestPeriodActive) {
                    // test period is active
                    if (!(applicationContext as ApplicationProperty).loadBooleanData(
                            R.string.FileKey_PremVersion,
                            R.string.DataKey_HideMainActivityTestBanner,
                            false
                        )
                    ) {
                        // the user has NOT pressed hide in a previous execution
                        // so show the banner
                        this.visibility = View.VISIBLE
                    }
                    // else : user has pressed hide, so hide the banner until the period is over
                } else {
                    // must be unpaid version
                    // >> change hide button text to 'purchase'
                    hideLink.apply {
                        text = getString(R.string.MainActivity_ActionBanner_ButtonTextPurchase)
                    }
                    // change action text
                    actionTextView.apply {
                        text = getString(R.string.MainActivity_ActionBannerText_UnpaidVersion)
                    }
                    // change action image
                    actionBannerImage.apply {
                        setImageResource(R.drawable.ic_no_premium_48dp)
                    }
                    // show banner
                    this.visibility = View.VISIBLE
                }
            }
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
        findViewById<AppCompatImageButton>(R.id.mainActivityHamburgerButton).setImageResource(R.drawable.ic_menu_pressed_36dp)

        // block the invocation of a list element during the popup lifecycle
        this.preventListSelection = true
        // block the double execution of the menu button
        this.preventMenuButtonDoubleExecution = true

        val layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        val headerViewPos = intArrayOf(0, 0)
        this.headerSeparator.getLocationInWindow(headerViewPos)

        val layoutToUse = if ((applicationContext as ApplicationProperty).loadBooleanData(
                R.string.FileKey_AppSettings,
                R.string.DataKey_EnableLog
            )
        ) {
            if(appProperty.premiumManager.isPremiumAppVersion) {
                R.layout.main_activity_popup_flyout
            } else {
                R.layout.main_activity_popup_without_log_flyout
            }
        } else {
            R.layout.main_activity_popup_without_log_flyout
        }

        val popUpView =
            layoutInflater.inflate(layoutToUse, null)

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
        
        // register close button event
        this.popUpWindow.contentView.findViewById<AppCompatImageButton>(R.id.mainActivityPopUpCancelButton).apply {
            setOnClickListener {
                try {
                    this@MainActivity.popUpWindow.dismiss()
                } catch (e: java.lang.Exception){
                    Log.e("MainActivity", "Exception in PopUp Window: ${e.message}")
                }
            }
        }
    }

    /*
    private fun notifyUser(message: String){
        val dialog = AlertDialog.Builder(this)
        dialog.setMessage(message)
        dialog.setIcon(R.drawable.ic_announcement_36dp)
        dialog.setTitle(R.string.MA_UserNotificationDialogTitle)
        dialog.setPositiveButton(R.string.GeneralString_OK) { dialogInterface: DialogInterface, _: Int ->
            dialogInterface.dismiss()
        }
        dialog.create()
        dialog.show()
    }
    */
    
    private fun setUIState(state: Int){
        when(state){
            mainActivityNormalState -> {
                this.availableDevicesRecyclerView.visibility = View.VISIBLE
                this.noContentContainer.visibility = View.GONE

                this.noContentImageView.setImageResource(R.drawable.ic_list_add_48dp)
                this.noContentTextView.text = getString(R.string.MA_NoContentUserHintText)
                this.noContentTextView.textSize = 22F
            }
            mainActivityBluetoothPermissionMissingState -> {
                this.availableDevicesRecyclerView.visibility = View.GONE
                this.noContentContainer.visibility = View.VISIBLE

                this.noContentImageView.setImageResource(R.drawable.ic_remove_circle_48dp)
                this.noContentTextView.text = getString(R.string.MA_BluetoothPermissionMissionHintText)
                this.noContentTextView.textSize = 18F
            }
            mainActivityBluetoothNotEnabledState -> {
                this.availableDevicesRecyclerView.visibility = View.GONE
                this.noContentContainer.visibility = View.VISIBLE

                this.noContentImageView.setImageResource(R.drawable.ic_bluetooth_disabled_48dp)
                this.noContentTextView.text = getString(R.string.MA_BluetoothNotEnabledHintText)
                this.noContentTextView.textSize = 18F
            }
        }
    }

    private fun setItemColors(index: Int, textColorID: Int, backGroundDrawable: Int){
        val ll = availableDevicesViewManager.findViewByPosition(index) as? ConstraintLayout
        val textView = ll?.findViewById<AppCompatTextView>(R.id.deviceNameTextView)
        textView?.setTextColor(getColor(textColorID))
        ll?.background = AppCompatResources.getDrawable(this, backGroundDrawable)
    }

    private fun resetSelectionInDeviceListView(){
        for(x in 0 until this.availableDevices.size){
            if(appProperty.premiumManager.isPremiumAppVersion || x <= NON_PREMIUM_MAX_DEVICE_LIST_INDEX) {
                setItemColors(
                    x,
                    R.color.max_contrast_text_color,
                    R.drawable.my_devices_list_element_background
                )
            } else {
                setItemColors(
                    x,
                    R.color.disabledTextColor,
                    R.drawable.my_devices_list_element_background
                )
            }
        }
    }
    
    private fun autoConnect(){
        if ((applicationContext as ApplicationProperty).loadBooleanData(
                R.string.FileKey_AppSettings,
                R.string.DataKey_AutoConnect
            )
        ) {
            if(verboseLog){
                Log.d("MainActivity", "AutoConnect: active!")
            }
            // check if there are more than one device
            when (this.availableDevices.size) {
                0 -> {
                    // no devices - do nothing
                    if(verboseLog){
                        Log.d("MainActivity", "AutoConnect: no devices - nothing to do")
                    }
                }
                1 -> {
                    // there is only one available device -> connect to it!
                    if(verboseLog){
                        Log.d("MainActivity", "AutoConnect: Only one device - connecting to (${this.availableDevices.elementAt(0).name}")
                    }
                    
                    //Handler(Looper.getMainLooper()).postDelayed({
                        
                        val intent = Intent(this@MainActivity, LoadingActivity::class.java)
                        intent.putExtra("DeviceListIndex", 0)
                        startActivity(intent)
    
                    // set param for auto-connect function
                    (applicationContext as ApplicationProperty).isBackNavigationToMain = true
    
    
                    //}, 1000)
                }
                else -> {
                    // try to connect to the last successful connected device
                    if (ApplicationProperty.bluetoothConnectionManager.isLastAddressValid) {
                        if(verboseLog){
                            
                            Log.d("MainActivity", "AutoConnect: Trying to connect to last device!")
                        }
    
    
                        //Handler(Looper.getMainLooper()).postDelayed({
                            
                            // move forward with delay...!
                            val intent = Intent(this@MainActivity, LoadingActivity::class.java)
                            intent.putExtra("DeviceListIndex", -2)// -2 means: connect to the last device
                            startActivity(intent)
    
                        // set param for auto-connect function
                        (applicationContext as ApplicationProperty).isBackNavigationToMain = true
    
    
                        //}, 1000)
                        
                    } else {
                        if(verboseLog){
                            Log.d("MainActivity", "AutoConnect: Last device address invalid - skip!")
                        }
                    }
                }
            }
        }
    }

    class AvailableDevicesListAdapter(
        private val laRoomyDevListAdapter : ArrayList<LaRoomyDevicePresentationModel>,
        private val deviceListItemClickListener: OnDeviceListItemClickListener,
        private val appProp: ApplicationProperty
    ) : RecyclerView.Adapter<AvailableDevicesListAdapter.DSLRViewHolder>() {

        class DSLRViewHolder(val constraintLayout: ConstraintLayout) :
            RecyclerView.ViewHolder(constraintLayout){
    
            fun bind(data: LaRoomyDevicePresentationModel, deviceListItemClick: OnDeviceListItemClickListener){
                itemView.setOnClickListener{
                    deviceListItemClick.onItemClicked(bindingAdapterPosition, data)
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
            
            val premiumCondition =
                (position <= NON_PREMIUM_MAX_DEVICE_LIST_INDEX) || appProp.premiumManager.isPremiumAppVersion
            
            // set the device-name
            holder.constraintLayout.findViewById<AppCompatTextView>(R.id.deviceNameTextView).apply {
                text = laRoomyDevListAdapter.elementAt(position).name
                if(!premiumCondition){
                    setTextColor(holder.constraintLayout.context.getColor(R.color.disabledTextColor))
                } else {
                    setTextColor(holder.constraintLayout.context.getColor(R.color.max_contrast_text_color))
                }
            }
            
            // set the appropriate image for the device type
            holder.constraintLayout.findViewById<AppCompatImageView>(R.id.myDevicesListElementImageView).apply {
                if(premiumCondition) {
                    setImageResource(laRoomyDevListAdapter.elementAt(position).image)
                } else {
                    setImageResource(R.drawable.ic_181_bluetooth_dis)
                }
            }
            holder.bind(laRoomyDevListAdapter.elementAt(position), deviceListItemClickListener)
        }

        override fun getItemCount(): Int {
            return laRoomyDevListAdapter.size
        }

        fun removeAt(position: Int){
            laRoomyDevListAdapter.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun onMainActivityAddDeviceButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        if(this.bluetoothPermissionGranted) {

            addDeviceButton.setImageResource(R.drawable.ic_add_pressed_36dp)
            addButtonNormalizationRequired = true

            val intent = Intent(this@MainActivity, AddDeviceActivity::class.java)
            startActivity(intent)
    
            // set param for auto-connect function
            (applicationContext as ApplicationProperty).isBackNavigationToMain = true
        }
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
    
        // set param for auto-connect function
        (applicationContext as ApplicationProperty).isBackNavigationToMain = true
    
        try {
            this.popUpWindow.dismiss()
        } catch(e: Exception){
            Log.e("MainActivity", "M: onMainActivityPopUpButtonClick -> exception occurred: $e")
        }
    }

    fun onMainActivityNoContentContainerClick(@Suppress("UNUSED_PARAMETER")view: View) {

        if(verboseLog){
            Log.d("MA:noContentContainerClicked", "The no content container was clicked. Execution the appropriate action.")
        }

        if(this.isBluetoothEnabled && this.bluetoothPermissionGranted) {
            // start add activity
            val intent = Intent(this@MainActivity, AddDeviceActivity::class.java)
            startActivity(intent)
            // set param for auto-connect function
            (applicationContext as ApplicationProperty).isBackNavigationToMain = true
        } else {
            // if bluetooth permission is not granted the UI is expected to be in grant-permission-request state
            if(!this.bluetoothPermissionGranted){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                    this.grantPermissionRequestReclined = false

                    try {
                        // goto application settings
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri: Uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
    
                        // set param for auto-connect function
                        (applicationContext as ApplicationProperty).isBackNavigationToMain = true
                        
                    } catch (e: java.lang.Exception){
                        Log.e("noContentContainerClicked", "Exception occurred while trying to launch settings app. E: $e")
                    }
                }
            } else {
                // if bluetooth is not enabled the UI is expected to be in activate bluetooth request mode
                if(!this.isBluetoothEnabled) {
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    requestEnableBluetoothLauncher.launch(intent)
                }
            }
        }
    }
}
