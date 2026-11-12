package com.xbmarket.mobile

import android.app.Application
import com.xbmarket.mobile.data.Account
import com.xbmarket.mobile.data.CloudSync
import com.xbmarket.mobile.data.Prefs

class XbMarketApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Account.init(this)
        CloudSync.start()
    }
}
