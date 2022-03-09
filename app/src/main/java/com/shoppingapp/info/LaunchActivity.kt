package com.shoppingapp.info

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import com.shoppingapp.info.utils.ShoppingAppSessionManager


@SuppressLint("CustomSplashScreen")
class LaunchActivity : AppCompatActivity() {

    companion object{
        const val ONE_SECOND = 1000L
        const val THREE_SECOND = 3000L
    }

    private lateinit var appSessionManager: ShoppingAppSessionManager


    private lateinit var timer: CountDownTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)

        appSessionManager = ShoppingAppSessionManager(this)


        timer = object : CountDownTimer(THREE_SECOND, ONE_SECOND){
            override fun onTick(p0: Long) {

            }

            override fun onFinish() {
               isRememberOn()
            }

        }
        timer.start()


    }



    fun isRememberOn(){
        if (appSessionManager.isRememberMeOn()){
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }else{
            val intent = Intent(this, RegistrationActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }
    }





}

