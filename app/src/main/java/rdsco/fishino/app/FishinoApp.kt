package rdsco.fishino.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import rdsco.fishino.app.notify.MessageNotifier

class FishinoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MessageNotifier.createChannel(this)

        // Leaving the app for more than a minute signs the user out.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.sessionManager.onAppForeground()

            override fun onStop(owner: LifecycleOwner) = container.sessionManager.onAppBackground()
        })
    }
}
