package com.tedflix.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager

class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TedflixLoading.create(this))
        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }, 280L)
    }
}
