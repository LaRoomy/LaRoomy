package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.widget.AppCompatTextView

class EditUUIDProfileActivity : AppCompatActivity() {

    private lateinit var headerTextView: AppCompatTextView

    private var mode = ""
    private var elementIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_u_u_i_d_profile)

        mode = this.intent.getStringExtra("activity-mode") ?: "err"

        this.headerTextView = findViewById<AppCompatTextView>(R.id.editUUIDActivityHeaderTextView)
            .apply {
                when(mode){
                    "new" -> {
                        text = getString(R.string.EditUUIDProfileActivityHeaderNewModeText)
                    }
                    else -> {
                        if(mode.startsWith("edit")){
                            text = getString(R.string.EditUUIDProfileActivityHeaderEditModeText)
                            elementIndex = mode.elementAt(4).toInt()
                        } else {
                            text = getString(R.string.EditUUIDProfileActivityModeError)
                        }
                    }
                }
            }


    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}