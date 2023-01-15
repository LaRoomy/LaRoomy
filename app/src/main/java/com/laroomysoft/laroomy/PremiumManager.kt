package com.laroomysoft.laroomy

import android.content.Context
import android.util.Log
import java.time.LocalDate
import java.util.*

class PremiumManager(private val appContext: Context) {
    
    private val appProperty: ApplicationProperty
    get() = (appContext as ApplicationProperty)
    
    var isTestPeriodActive = false
    private set
    
    var userHasPurchased = false
    private set
    
    private val dateInfo = TestPeriodDateInfo()
    
    val remainingTestPeriodDays
    get() = this.dateInfo.getDaysLeftToExpiration()
    
    val isPremiumAppVersion
    get() = this.userHasPurchased || this.isTestPeriodActive
    
    fun checkPremiumAppStatus() : Boolean {
        if(verboseLog){
            Log.d("PremiumManager", "CHECK APP PURCHASE STATUS!")
        }
        // check if the app is already purchased
        val hasPurchased =
            appProperty.loadBooleanData(R.string.FileKey_PremVersion, R.string.DataKey_PurchaseDoneByUser, false)
        
        if(!hasPurchased){
            if(verboseLog){
                Log.d("PremiumManager", "User has not purchased. Check for Test-Period.")
            }
            this.userHasPurchased = false
            
            // check if this is the first usage
            val testPeriodStarted = appProperty.loadBooleanData(R.string.FileKey_PremVersion, R.string.DataKey_TestPeriodStarted, false)
            if(!testPeriodStarted){
                if(verboseLog){
                    Log.d("PremiumManager", "Test period not started")
                }
    
                // first usage: mark the test period as started
                appProperty.saveBooleanData(true, R.string.FileKey_PremVersion, R.string.DataKey_TestPeriodStarted)
                this.isTestPeriodActive = true
                
                // get the current date
                val calendar = Calendar.getInstance()
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val month = calendar.get(Calendar.MONTH)// the month is zero based
                val year = calendar.get(Calendar.YEAR)
                
                // save the date to class
                this.dateInfo.sDay = day
                this.dateInfo.sMonth = month
                this.dateInfo.sYear = year
                this.dateInfo.isValid = true
                
                // build the string to save
                val dateToSave = "${day};${month};${year};"
                
                // save the start-date
                appProperty.saveStringData(dateToSave, R.string.FileKey_PremVersion, R.string.DataKey_FirstUseDate)
                
            } else {
                if(verboseLog){
                    Log.d("PremiumManager", "Test period has started. Check the date")
                }
                // test period has started, check the date (and whether the period is over)
                
                // load the date string
                val savedDate = appProperty.loadSavedStringData(R.string.FileKey_PremVersion, R.string.DataKey_FirstUseDate, "invalid")
                if(savedDate != "invalid"){
                    try {
                        var day = ""
                        var month = ""
                        var year = ""
                        var recType = 0
    
                        // separate element data
                        savedDate.forEach {
                            if(it == ';'){
                                recType++
                            } else {
                                when (recType) {
                                    0 -> day += it
                                    1 -> month += it
                                    2 -> year += it
                                    else -> return@forEach
                                }
                            }
                        }
                        
                        // transform to int if valid
                        if(day.isNotEmpty() && month.isNotEmpty() && year.isNotEmpty()){
                            this.dateInfo.sDay = day.toInt()
                            this.dateInfo.sMonth = month.toInt()
                            this.dateInfo.sYear = year.toInt()
                            this.dateInfo.isValid = true
                            
                            this.isTestPeriodActive =
                                this.dateInfo.checkThirtyDaysPeriod()
                            
                            if(this.isTestPeriodActive) {
                                if(verboseLog){
                                    Log.d("PremiumManager", "The Date is in scope so the test-period is active.")
                                }
                                // this is an active test version so the user has premium status (currently)
                                return true
                            } else {
                                if(verboseLog){
                                    Log.d("PremiumManager", "The Date is out of scope, so the test-period is over!")
                                }
                            }
                        }
                    } catch(e: java.lang.Exception){
                        Log.e("PremiumManager", "Exception while trying to retrieve the start-date: $e")
                    }
                } else {
                    // string was not saved before, the only scenario where this can happen is on first execution
                    this.isTestPeriodActive = true
                }
            }
            
        } else {
            if(verboseLog){
                Log.d("PremiumManager", "User has purchased. Activate unlimited access.")
            }
            // user has purchased, set premium to true
            this.userHasPurchased = true
            this.isTestPeriodActive = false
            return true
        }
        return false
    }
    
    class TestPeriodDateInfo {
        var sDay = 1
        var sMonth = 1
        var sYear = 2000
        var isValid = false
        
        fun checkThirtyDaysPeriod() : Boolean {
            // get current date
            val calendar = Calendar.getInstance()
    
            // start date to object
            val endDate = LocalDate.of(this.sYear, this.sMonth + 1, this.sDay)
            // add 30 days to get the date where the period ends
            endDate.plusDays(30)
            // current date to object
            val curDate = LocalDate.of(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
            // determine if period is over
            return curDate <= endDate
        }
        
        fun getDaysLeftToExpiration() : Int {
            // get the current date
            val calendar = Calendar.getInstance()
            val curDay = calendar.get(Calendar.DAY_OF_MONTH)
            val curMonth = calendar.get(Calendar.MONTH) + 1 // the month is zero based, so increase
            val curYear = calendar.get(Calendar.YEAR)
    
            return if(this.checkThirtyDaysPeriod()){
                // start date to object
                val startDate = LocalDate.of(this.sYear, this.sMonth + 1, this.sDay)
                // cur date to object
                val curDate = LocalDate.of(curYear, curMonth, curDay)
                // get days (the period minus the expiration)
                (30 - startDate.until(curDate).days)
            } else {
                // period is over
                0
            }
        }
    }
}