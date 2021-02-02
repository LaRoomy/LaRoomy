package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView

class UnlockControlActivity : AppCompatActivity() {

    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var lockStatusTextView: AppCompatTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unlock_control)

        // get view parameter
        this.notificationTextView = findViewById(R.id.ucNotificationTextView)
        this.lockStatusTextView = findViewById(R.id.ucLockConditionStatusTextView)



    }






    fun onLockImageClick(view: View) {

        // TODO: send lock command

    }

    fun onNumPadButtonClick(view: View){

        // TODO!

        when(view.id){
            R.id.ucOneButton -> {}
            R.id.ucTwoButton -> {}
            else -> {}
        }

    }

    fun notifyUser(message: String, colorID: Int){

    }
}