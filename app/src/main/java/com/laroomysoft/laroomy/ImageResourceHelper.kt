package com.laroomysoft.laroomy

fun resourceIdForImageId(imageID: Int, elementType: Int, isPremiumVersion: Boolean): Int {
    
    // if the user has paid or this is a free trial, use all images
    return if(isPremiumVersion) {
        when (imageID) {
            1 -> R.drawable.ic_01_house
            2 -> R.drawable.ic_02_garage
            3 -> R.drawable.ic_03_garden
            4 -> R.drawable.ic_04_lightbulb
            5 -> R.drawable.ic_05_light_setup
            6 -> R.drawable.ic_06_stand_lamp
            7 -> R.drawable.ic_07_ceil_lamp
            8 -> R.drawable.ic_08_led
            9 -> R.drawable.ic_09_brightness
            10 -> R.drawable.ic_10_day_mode
            11 -> R.drawable.ic_11_night_mode
            12 -> R.drawable.ic_12_time
            13 -> R.drawable.ic_13_time_frame
            14 -> R.drawable.ic_14_time_setup
            15 -> R.drawable.ic_15_clock_reload
            16 -> R.drawable.ic_16_date
            17 -> R.drawable.ic_17_date_time
            18 -> R.drawable.ic_18_rgb_slider
            19 -> R.drawable.ic_19_rgb_circles
            20 -> R.drawable.ic_20_rgb_flower
            21 -> R.drawable.ic_21_lock_closed
            22 -> R.drawable.ic_22_lock_opened
            23 -> R.drawable.ic_23_key
            24 -> R.drawable.ic_24_time_lock
            25 -> R.drawable.ic_25_access_ctrl
            26 -> R.drawable.ic_26_lock_retry
            27 -> R.drawable.ic_27_safe
            28 -> R.drawable.ic_28_password
            29 -> R.drawable.ic_29_wifi_pwd
            30 -> R.drawable.ic_30_unlock_ctrl
            31 -> R.drawable.ic_31_batt_loading
            32 -> R.drawable.ic_32_batt_100p
            33 -> R.drawable.ic_33_batt_75p
            34 -> R.drawable.ic_34_batt_50p
            35 -> R.drawable.ic_35_batt_25p
            36 -> R.drawable.ic_36_batt_empty
            37 -> R.drawable.ic_37_batt_pow_save
            38 -> R.drawable.ic_38_level_100
            39 -> R.drawable.ic_39_level_75
            40 -> R.drawable.ic_40_level_50
            41 -> R.drawable.ic_41_level_25
            42 -> R.drawable.ic_42_level_0
            43 -> R.drawable.ic_43_level_adjust
            44 -> R.drawable.ic_44_meter
            
            151 -> R.drawable.ic_151_enum_point
            
            180 -> R.drawable.ic_180_group
        
            // TODO: implement all new image resources here...
        
            else -> commonResourceID(elementType)
        }
    } else {
        // otherwise use the default images
        commonResourceID(elementType)
    }
}

fun resourceIdForDisabledImageId(imageID: Int, elementType: Int, isPremiumVersion: Boolean): Int {
    // if the user has paid or this is a free trial, use all images
    return if(isPremiumVersion) {
        when (imageID) {
            
            151 -> R.drawable.ic_151_dis_enum_point
            
            180 -> R.drawable.ic_180_dis_group
        
        
            // TODO: implement all new disabled image resources here...
        
            else -> commonDisabledResourceID(elementType)
        }
    } else {
        commonDisabledResourceID(elementType)
    }
}

fun commonResourceID(elementType: Int): Int {
    // return the common image for the type
    return when(elementType){
        GROUP_ELEMENT -> R.drawable.ic_180_group
        else -> R.drawable.ic_151_enum_point
    }
}

fun commonDisabledResourceID(elementType: Int): Int {
    // return the common disabled image for the type
    return when(elementType){
        GROUP_ELEMENT -> R.drawable.ic_180_dis_group
        else -> R.drawable.ic_151_dis_enum_point
    }
}

