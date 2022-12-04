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

