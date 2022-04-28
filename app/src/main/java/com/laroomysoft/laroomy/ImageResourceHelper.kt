package com.laroomysoft.laroomy

fun resourceIdForImageId(imageID: Int): Int {
    return when (imageID) {
        -1 -> R.drawable.ic_00_image_error_state_vect       // done
        0 -> R.drawable.ic_00_image_error_state_vect        // done
        1 -> R.drawable.ic_01_placeholder_image_vect        // done
        2 -> R.drawable.ic_02_adjust_vect                   // done
        3 -> R.drawable.ic_03_adjust_2_blue_white_vect      // done
        4 -> R.drawable.ic_04_lightbulb_white_vect          // done
        5 -> R.drawable.ic_05_lightbulb_blue_white_vect     // done
        6 -> R.drawable.ic_06_lightbulb_blue_white_vect     // done
        7 -> R.drawable.ic_07_sun_yellow_vect               // done
        8 -> R.drawable.ic_08_sun_white_vect                // done
        9 -> R.drawable.ic_09_sun_blue_vect                 // done
        10 -> R.drawable.ic_10_clock_running_blue_white_vect// done
        11 -> R.drawable.ic_11_clock_reload_blue_white_vect // done
        12 -> R.drawable.ic_12_time_setup_white_vect        // done
        13 -> R.drawable.ic_13_property_group_blue_white_vect// done
        14 -> R.drawable.ic_14_reload_turnleft_blue_vect    // done
        15 -> R.drawable.ic_15_reload_turnleft_white_vect   // done
        16 -> R.drawable.ic_16_reload_turnright_white_vect  // done - with errors (scaling of the stroke width)
        17 -> R.drawable.ic_17_segmented_circle_blue_white_vect // done
        18 -> R.drawable.ic_18_sync_white_vect              // done
        19 -> R.drawable.ic_19_sync_blue_white_vect         // done
        20 -> R.drawable.ic_20_scale_up_blue_white_vect     // done
        21 -> R.drawable.ic_21_bars_indifferent_white_vect  // done
        22 -> R.drawable.ic_22_increasing_bars_blue_vect    // done
        23 -> R.drawable.ic_23_level_100p_white_vect        // done
        24 -> R.drawable.ic_24_level_75p_vect               // done
        25 -> R.drawable.ic_25_level_50p_vect               // done
        26 -> R.drawable.ic_26_level_25p_vect               // done
        27 -> R.drawable.ic_27_level_0p_vect                // done
        28 -> R.drawable.ic_28_warning_blue_white_vect      // done
        29 -> R.drawable.ic_29_warning_white_vect           // done
        30 -> R.drawable.ic_30_warning_yellow_vect          // done
        31 -> R.drawable.ic_31_warning_red_vect             // done
        32 -> R.drawable.ic_32_settings_blue_white_vect     // done
        33 -> R.drawable.ic_33_settings1_white_vect         // done
        34 -> R.drawable.ic_34_settings1_blue_vect          // done
        35 -> R.drawable.ic_35_settings2_blue_white_vect    // done
        36 -> R.drawable.ic_36_settings3_blue_white_vect    // done
        37 -> R.drawable.ic_37_conjunction_blue_white_vect  // done
        38 -> R.drawable.ic_38_human_setup1_blue_white_vect // done
        39 -> R.drawable.ic_39_human_setup2_blue_white_vect // done
        40 -> R.drawable.ic_40_star_blue_vect               // done
        41 -> R.drawable.ic_41_checkmark_blue_vect          // done
        42 -> R.drawable.ic_42_star_white_vect              // done
        43 -> R.drawable.ic_43_checkmark_white_vect         // done
        44 -> R.drawable.ic_44_question_circle_white_vect   // done
        45 -> R.drawable.ic_45_question_circle_blue_vect    // done
        46 -> R.drawable.ic_46_trash_blue_white_vect        // done
        47 -> R.drawable.ic_47_lock_opened_blue_white_vect  // done
        48 -> R.drawable.ic_48_lock_closed_blue_white_vect  // done
        49 -> R.drawable.ic_49_location_blue_white_vect     // done
        50 -> R.drawable.ic_50_printer_blue_white_vect      // done
        51 -> R.drawable.ic_51_listing_items_blue_white_vect// done
        52 -> R.drawable.ic_52_world_blue_white_vect        // done
        53 -> R.drawable.ic_53_globe_blue_white_vect        // done
        54 -> R.drawable.ic_54_battery_loading_blue_white_vect// done
        55 -> R.drawable.ic_55_battery_100p_blue_white_vect // done
        56 -> R.drawable.ic_56_battery_75p_blue_white_vect  // done
        57 -> R.drawable.ic_57_battery_50p_blue_white_vect  // done
        58 -> R.drawable.ic_58_battery_25p_blue_white_vect  // done
        59 -> R.drawable.ic_59_battery_empty_white_vect     // done
        60 -> R.drawable.ic_60_battery_dead_red_vect        // done
        61 -> R.drawable.ic_61_rectangle_empty_white_vect   // done
        62 -> R.drawable.ic_62_rectangle_checked_blue_white_vect// done
        63 -> R.drawable.ic_63_rectangle_crossed_blue_white_vect// done
        64 -> R.drawable.ic_64_circle_empty_white_vect      // done
        65 -> R.drawable.ic_65_circle_checked_blue_white_vect// done
        66 -> R.drawable.ic_66_circle_crossed_blue_white_vect// done
        67 -> R.drawable.ic_67_cloud_white_vect             // done
        68 -> R.drawable.ic_68_cloud_blue_white_vect        // done
        69 -> R.drawable.ic_69_home_blue_vect               // done
        70 -> R.drawable.ic_70_home_white_vect              // done
        71 -> R.drawable.ic_71_home_blue_white_vect         // done
        72 -> R.drawable.ic_72_share_blue_white_vect        // done
        73 -> R.drawable.ic_73_wifi_blue_white_vect         // done
        74 -> R.drawable.ic_74_calculator_blue_white_vect   // done
        75 -> R.drawable.ic_75_people_blue_white_vect       // done
        76 -> R.drawable.ic_76_search_blue_white_vect       // done
        77 -> R.drawable.ic_77_hierachy_blue_white_vect     // done
        78 -> R.drawable.ic_78_doublehelix_blue_white_vect  // done
        79 -> R.drawable.ic_79_at_blue_vect                 // done
        80 -> R.drawable.ic_80_at_white_vect                // done
        81 -> R.drawable.ic_81_circle_1_blue_vect           // done -
        82 -> R.drawable.ic_82_circle_2_blue_vect           // done
        83 -> R.drawable.ic_83_circle_3_blue_vect           // done
        84 -> R.drawable.ic_84_circle_1_white_vect          // done
        85 -> R.drawable.ic_85_circle_2_white_vect          // done
        86 -> R.drawable.ic_86_circle_3_white_vect          // done
        87 -> R.drawable.ic_87_arrow_up_white_vect          // done
        88 -> R.drawable.ic_88_arrow_right_white_vect       // done
        89 -> R.drawable.ic_89_arrow_down_white_vect        // done
        90 -> R.drawable.ic_90_arrow_left_white_vect        // done
        91 -> R.drawable.ic_91_arrow_up_blue_vect           // done
        92 -> R.drawable.ic_92_arrow_right_blue_vect        // done
        93 -> R.drawable.ic_93_arrow_down_blue_vect         // done
        94 -> R.drawable.ic_94_arrow_left_blue_vect         // done
        95 -> R.drawable.ic_95_tv_white_vect                // done -
        96 -> R.drawable.ic_96_tv_blue_white_vect           // done
        97 -> R.drawable.ic_97_arrows_up_down_blue_white_vect// done
        98 -> R.drawable.ic_98_arrows_right_left_blue_white_vect// done
        99 -> R.drawable.ic_99_hand_white_vect              // done
        100 -> R.drawable.ic_100_hand_blue_white_vect       // done
        101 -> R.drawable.ic_101_stop_sign_vect             // done
        102 -> R.drawable.ic_102_shield_power_blue_white_vect// done
        103 -> R.drawable.ic_103_shield_ok_blue_white_vect  // done
        104 -> R.drawable.ic_104_shield_exclamation_blue_white_vect// done
        105 -> R.drawable.ic_105_info_circle_blue_white_vect// done
        106 -> R.drawable.ic_106_info_circle_white_vect     // done
        107 -> R.drawable.ic_107_key_white_vect             // done
        108 -> R.drawable.ic_108_key_blue_vect        // done
        109 -> R.drawable.ic_109_undo_white                 // done
        110 -> R.drawable.ic_110_redo_white                 // done
        111 -> R.drawable.ic_111_flash_blue_vect            // done
        112 -> R.drawable.ic_112_flash_yellow_vect          // done
        113 -> R.drawable.ic_113_add_white_vect             // done
        114 -> R.drawable.ic_114_add_blue_vect              // done
        115 -> R.drawable.ic_115_rgb_circles_vect           // done
        116 -> R.drawable.ic_116_rgb_slider_adjust_vect     // done
        117 -> R.drawable.ic_117_rgb_point_circle_vect      // done
        118 -> R.drawable.ic_118_tool_circle_blue_white_vect// done
        119 -> R.drawable.ic_119_tools_blue_white_vect      // done
        120 -> R.drawable.ic_120_slider_adjust_blue_white_vect// done
        121 -> R.drawable.ic_121_subtract_white_vect        // done
        122 -> R.drawable.ic_122_subtract_blue_vect         // done
        123 -> R.drawable.ic_123_brightness_white_vect      // done
        124 -> R.drawable.ic_124_enumeration_point_white_vect// done
        125 -> R.drawable.ic_125_enumeration_point_blue_vect // done
        126 -> R.drawable.ic_126_sleep_white_vect           // done
        127 -> R.drawable.ic_127_navigator_blue_white_vect  // done

        // TODO: implement all new image resources here...

        else -> R.drawable.ic_00_image_error_state_vect
    }
}

fun resourceIdForDisabledImageId(imageID: Int): Int {
    return when (imageID) {
        -1 -> R.drawable.ic_00_dis_image_error_state_vect
        0 -> R.drawable.ic_00_dis_image_error_state_vect
        1 -> R.drawable.ic_01_dis_placeholder_image_vect
        2 -> R.drawable.ic_02_dis_adjust_vect
        3 -> R.drawable.ic_03_dis_adjust_2_blue_white_vect
        4 -> R.drawable.ic_04_dis_lightbulb_white_vect
        5 -> R.drawable.ic_05_dis_lightbulb_blue_white_vect
        6 -> R.drawable.ic_06_dis_lightbulb_blue_white_vect
        7 -> R.drawable.ic_0x_sun_disabled_vect
        8 -> R.drawable.ic_0x_sun_disabled_vect
        9 -> R.drawable.ic_0x_sun_disabled_vect
        10 -> R.drawable.ic_10_dis_clock_running_blue_white_vect
        11 -> R.drawable.ic_11_dis_clock_reload_blue_white_vect
        12 -> R.drawable.ic_12_dis_time_setup_white_vect
        13 -> R.drawable.ic_13_dis_property_group_blue_white_vect
        14 -> R.drawable.ic_14_dis_reload_turnleft_blue_vect
        15 -> R.drawable.ic_15_dis_reload_turnleft_white_vect
        16 -> R.drawable.ic_16_dis_reload_turnright_white_vect  // done - with errors (scaling of the stroke width)
        17 -> R.drawable.ic_17_dis_segmented_circle_blue_white_vect
        18 -> R.drawable.ic_18_dis_sync_white_vect
        19 -> R.drawable.ic_19_dis_sync_blue_white_vect
        20 -> R.drawable.ic_20_dis_scale_up_blue_white_vect
        21 -> R.drawable.ic_21_dis_bars_indifferent_white_vect
        22 -> R.drawable.ic_22_dis_increasing_bars_blue_vect
        23 -> R.drawable.ic_23_dis_level_100p_white_vect
        24 -> R.drawable.ic_24_dis_level_75p_vect
        25 -> R.drawable.ic_25_dis_level_50p_vect
        26 -> R.drawable.ic_26_dis_level_25p_vect
        27 -> R.drawable.ic_27_dis_level_0p_vect
        28 -> R.drawable.ic_28_dis_warning_blue_white_vect
        29 -> R.drawable.ic_29_dis_warning_white_vect
        30 -> R.drawable.ic_30_dis_warning_yellow_vect
        31 -> R.drawable.ic_31_dis_warning_red_vect
        32 -> R.drawable.ic_32_dis_settings_blue_white_vect
        33 -> R.drawable.ic_33_dis_settings1_white_vect
        34 -> R.drawable.ic_34_dis_settings1_blue_vect
        35 -> R.drawable.ic_35_dis_settings2_blue_white_vect
        36 -> R.drawable.ic_36_dis_settings3_blue_white_vect
        37 -> R.drawable.ic_37_dis_conjunction_blue_white_vect
        38 -> R.drawable.ic_38_dis_human_setup1_blue_white_vect
        39 -> R.drawable.ic_39_dis_human_setup2_blue_white_vect
        40 -> R.drawable.ic_40_dis_star_blue_vect
        41 -> R.drawable.ic_41_dis_checkmark_blue_vect
        42 -> R.drawable.ic_42_dis_star_white_vect
        43 -> R.drawable.ic_43_dis_checkmark_white_vect
        44 -> R.drawable.ic_44_dis_question_circle_white_vect
        45 -> R.drawable.ic_45_dis_question_circle_blue_vect
        46 -> R.drawable.ic_46_dis_trash_blue_white_vect
        47 -> R.drawable.ic_47_dis_lock_opened_blue_white_vect
        48 -> R.drawable.ic_48_dis_lock_closed_blue_white_vect
        49 -> R.drawable.ic_49_dis_location_blue_white_vect
        50 -> R.drawable.ic_50_dis_printer_blue_white_vect
        51 -> R.drawable.ic_51_dis_listing_items_blue_white_vect
        52 -> R.drawable.ic_52_dis_world_blue_white_vect
        53 -> R.drawable.ic_53_dis_globe_blue_white_vect
        54 -> R.drawable.ic_54_dis_battery_loading_blue_white_vect
        55 -> R.drawable.ic_55_dis_battery_100p_blue_white_vect
        56 -> R.drawable.ic_56_dis_battery_75p_blue_white_vect
        57 -> R.drawable.ic_57_dis_battery_50p_blue_white_vect
        58 -> R.drawable.ic_58_dis_battery_25p_blue_white_vect
        59 -> R.drawable.ic_59_dis_battery_empty_white_vect
        60 -> R.drawable.ic_60_dis_battery_dead_red_vect
        61 -> R.drawable.ic_61_dis_rectangle_empty_white_vect
        62 -> R.drawable.ic_62_dis_rectangle_checked_blue_white_vect
        63 -> R.drawable.ic_63_dis_rectangle_crossed_blue_white_vect
        64 -> R.drawable.ic_64_dis_circle_empty_white_vect
        65 -> R.drawable.ic_65_dis_circle_checked_blue_white_vect
        66 -> R.drawable.ic_66_dis_circle_crossed_blue_white_vect
        67 -> R.drawable.ic_67_dis_cloud_white_vect
        68 -> R.drawable.ic_68_dis_cloud_blue_white_vect
        69 -> R.drawable.ic_69_dis_home_blue_vect
        70 -> R.drawable.ic_70_dis_home_white_vect
        71 -> R.drawable.ic_71_dis_home_blue_white_vect
        72 -> R.drawable.ic_72_dis_share_blue_white_vect
        73 -> R.drawable.ic_73_dis_wifi_blue_white_vect
        74 -> R.drawable.ic_74_dis_calculator_blue_white_vect
        75 -> R.drawable.ic_75_dis_people_blue_white_vect
        76 -> R.drawable.ic_76_dis_search_blue_white_vect
        77 -> R.drawable.ic_77_dis_hierachy_blue_white_vect
        78 -> R.drawable.ic_78_dis_doublehelix_blue_white_vect
        79 -> R.drawable.ic_79_dis_at_blue_vect
        80 -> R.drawable.ic_80_dis_at_white_vect
        81 -> R.drawable.ic_81_dis_circle_1_blue_vect
        82 -> R.drawable.ic_82_dis_circle_2_blue_vect
        83 -> R.drawable.ic_83_dis_circle_3_blue_vect
        84 -> R.drawable.ic_84_dis_circle_1_white_vect
        85 -> R.drawable.ic_85_dis_circle_2_white_vect
        86 -> R.drawable.ic_86_dis_circle_3_white_vect
        87 -> R.drawable.ic_87_dis_arrow_up_white_vect
        88 -> R.drawable.ic_88_dis_arrow_right_white_vect
        89 -> R.drawable.ic_89_dis_arrow_down_white_vect
        90 -> R.drawable.ic_90_dis_arrow_left_white_vect
        91 -> R.drawable.ic_91_dis_arrow_up_blue_vect
        92 -> R.drawable.ic_92_dis_arrow_right_blue_vect
        93 -> R.drawable.ic_93_dis_arrow_down_blue_vect
        94 -> R.drawable.ic_94_dis_arrow_left_blue_vect
        95 -> R.drawable.ic_95_dis_tv_white_vect
        96 -> R.drawable.ic_96_dis_tv_blue_white_vect
        97 -> R.drawable.ic_97_dis_arrows_up_down_blue_white_vect
        98 -> R.drawable.ic_98_dis_arrows_right_left_blue_white_vect
        99 -> R.drawable.ic_99_dis_hand_white_vect
        100 -> R.drawable.ic_100_dis_hand_blue_white_vect
        101 -> R.drawable.ic_101_dis_stop_sign_vect
        102 -> R.drawable.ic_102_dis_shield_power_blue_white_vect
        103 -> R.drawable.ic_103_dis_shield_ok_blue_white_vect
        104 -> R.drawable.ic_104_dis_shield_exclamation_blue_white_vect
        105 -> R.drawable.ic_105_dis_info_circle_blue_white_vect
        106 -> R.drawable.ic_106_dis_info_circle_white_vect
        107 -> R.drawable.ic_107_dis_key_white_vect
        108 -> R.drawable.ic_108_dis_key_blue_vect
        109 -> R.drawable.ic_109_dis_undo_white
        110 -> R.drawable.ic_110_dis_redo_white
        111 -> R.drawable.ic_111_dis_flash_blue_vect
        112 -> R.drawable.ic_112_dis_flash_yellow_vect
        113 -> R.drawable.ic_113_dis_add_white_vect
        114 -> R.drawable.ic_114_dis_add_blue_vect
        115 -> R.drawable.ic_115_dis_rgb_circles_vect
        116 -> R.drawable.ic_116_dis_rgb_slider_adjust_vect
        117 -> R.drawable.ic_117_dis_rgb_point_circle_vect
        118 -> R.drawable.ic_118_dis_tool_circle_blue_white_vect
        119 -> R.drawable.ic_119_dis_tools_blue_white_vect
        120 -> R.drawable.ic_120_dis_slider_adjust_blue_white_vect
        121 -> R.drawable.ic_121_dis_subtract_white_vect
        122 -> R.drawable.ic_122_dis_subtract_blue_vect
        123 -> R.drawable.ic_123_dis_brightness_white_vect
        124 -> R.drawable.ic_124_dis_enumeration_point_white_vect
        125 -> R.drawable.ic_125_dis_enumeration_point_blue_vect
        126 -> R.drawable.ic_126_dis_sleep_white_vect
        127 -> R.drawable.ic_127_dis_navigator_blue_white_vect

        // TODO: implement all new disabled image resources here...

        else -> R.drawable.ic_00_dis_image_error_state_vect
    }
}

