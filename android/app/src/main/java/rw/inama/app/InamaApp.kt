package rw.inama.app

import android.app.Application
import rw.inama.app.di.AppContainer

class InamaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
