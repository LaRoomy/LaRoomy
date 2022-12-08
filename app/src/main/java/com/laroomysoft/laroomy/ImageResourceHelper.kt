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
            45 -> R.drawable.ic_45_arrow_up
            46 -> R.drawable.ic_46_arrow_right
            47 -> R.drawable.ic_47_arrow_down
            48 -> R.drawable.ic_48_arrow_left
            49 -> R.drawable.ic_49_up_down
            50 -> R.drawable.ic_50_right_left
            51 -> R.drawable.ic_51_turn_right
            52 -> R.drawable.ic_52_turn_left
            53 -> R.drawable.ic_53_location
            54 -> R.drawable.ic_54_direction
            55 -> R.drawable.ic_55_destination
            56 -> R.drawable.ic_56_navigator
            57 -> R.drawable.ic_57_set_gear
            58 -> R.drawable.ic_58_set_tool
            59 -> R.drawable.ic_59_human_setup
            60 -> R.drawable.ic_60_tools
            61 -> R.drawable.ic_61_tool_circle
            62 -> R.drawable.ic_62_sound_on
            63 -> R.drawable.ic_63_sound_off
            64 -> R.drawable.ic_64_play
            65 -> R.drawable.ic_65_pause
            66 -> R.drawable.ic_66_stop
            67 -> R.drawable.ic_67_notification
            68 -> R.drawable.ic_68_noti_text
            69 -> R.drawable.ic_69_noti_excl
            70 -> R.drawable.ic_70_noti_bell
            71 -> R.drawable.ic_71_noti_bell_off
            72 -> R.drawable.ic_72_reload
            73 -> R.drawable.ic_73_sync
            74 -> R.drawable.ic_74_undo
            75 -> R.drawable.ic_75_redo
            76 -> R.drawable.ic_76_add
            77 -> R.drawable.ic_77_subtract
            78 -> R.drawable.ic_78_open
            79 -> R.drawable.ic_79_close
            80 -> R.drawable.ic_80_share
            81 -> R.drawable.ic_81_search
            82 -> R.drawable.ic_82_delete
            83 -> R.drawable.ic_83_sun
            84 -> R.drawable.ic_84_sun_cloud
            85 -> R.drawable.ic_85_cloud_rain
            86 -> R.drawable.ic_86_cloud
            87 -> R.drawable.ic_87_thunderstorm
            88 -> R.drawable.ic_88_cloud_snow
            89 -> R.drawable.ic_89_wind
            90 -> R.drawable.ic_90_fog
            91 -> R.drawable.ic_91_freeze
            92 -> R.drawable.ic_92_auto_ctrl
            93 -> R.drawable.ic_93_manual_ctrl
            94 -> R.drawable.ic_94_slider_adjust
            95 -> R.drawable.ic_95_touch
            96 -> R.drawable.ic_96_plant
            97 -> R.drawable.ic_97_plant_light
            98 -> R.drawable.ic_98_plant_water
            99 -> R.drawable.ic_99_plant_irri
            100 -> R.drawable.ic_100_eco_mode
            101 -> R.drawable.ic_101_plant_care
            102 -> R.drawable.ic_102_plant_sensor
            103 -> R.drawable.ic_103_eco_light
            104 -> R.drawable.ic_104_green_energy
            105 -> R.drawable.ic_105_recycle
            106 -> R.drawable.ic_106_flash
            107 -> R.drawable.ic_107_pow_save
            108 -> R.drawable.ic_108_solar_power
            109 -> R.drawable.ic_109_wind_power
            110 -> R.drawable.ic_110_standby
            111 -> R.drawable.ic_111_wallplug
            112 -> R.drawable.ic_112_plug
            113 -> R.drawable.ic_113_power_unit
            114 -> R.drawable.ic_114_fuse_box
            115 -> R.drawable.ic_115_sensor
            116 -> R.drawable.ic_116_motion_sense
            117 -> R.drawable.ic_117_level_sensor
            118 -> R.drawable.ic_118_temp_sense
            119 -> R.drawable.ic_119_gravi_sensor
            120 -> R.drawable.ic_120_dist_sense
            121 -> R.drawable.ic_121_door_sensor
            122 -> R.drawable.ic_122_window_sensor
            
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

