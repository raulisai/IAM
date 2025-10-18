package com.example.iam

import android.content.Context
import android.webkit.JavascriptInterface
import com.example.iam.network.AuthManager

class WebAppInterface(private val context: Context) {

    @JavascriptInterface
    fun saveAuthToken(token: String, userId: String) {
        AuthManager.init(context)
        AuthManager.saveAuthToken(token, userId)
    }
}
