package org.electroncash.electroncash3

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.matthewnelson.topl_service.TorServiceController
import io.matthewnelson.topl_service.lifecycle.BackgroundManager
import io.matthewnelson.topl_service.notification.ServiceNotification
import io.matthewnelson.topl_service_base.ServiceUtilities
import io.matthewnelson.topl_service_base.TorPortInfo
import io.matthewnelson.topl_service_base.TorServiceEventBroadcaster

private fun generateTorServiceNotificationBuilder(context: Context): ServiceNotification.Builder {
    return ServiceNotification.Builder(
        channelName = "TOPL-Android Demo",
        channelDescription = "TorOnionProxyLibrary-Android Demo",
        channelID = "TOPL-Android Demo",
        notificationID = 615
    )
        .setImageTorNetworkingEnabled(drawableRes = R.drawable.tor_stat_network_enabled)
        .setImageTorNetworkingDisabled(drawableRes = R.drawable.tor_stat_network_disabled)
        .setImageTorDataTransfer(drawableRes = R.drawable.tor_stat_network_dataxfer)
        .setImageTorErrors(drawableRes = R.drawable.tor_stat_notifyerr)
        .setVisibility(visibility = NotificationCompat.VISIBILITY_PRIVATE)
        .enableTorRestartButton(enable = true)
        .enableTorStopButton(enable = true)
        .showNotification(show = true)

        // Set the notification's contentIntent for when the user clicks the notification
        .also { builder ->
            context.applicationContext.packageManager
                ?.getLaunchIntentForPackage(context.applicationContext.packageName)
                ?.let { intent ->

                    // Set in your manifest for the launch activity so the intent won't launch
                    // a new activity over top of your already created activity if the app is
                    // open when the user clicks the notification:
                    //
                    // android:launchMode="singleInstance"
                    //
                    // For more info on launchMode and Activity Intent flags, see:
                    //
                    // https://medium.com/swlh/truly-understand-tasks-and-back-stack-intent-flags-of-activity-2a137c401eca

                    builder.setContentIntent(
                        PendingIntent.getActivity(
                            context.applicationContext,
                            0, // Your desired request code
                            intent,
                            0 // flags
                            // can also include a bundle if desired
                        )
                    )
                }
        }
}

private fun generateBackgroundManagerPolicy(): BackgroundManager.Builder.Policy {
    return BackgroundManager.Builder()

        // All available options present. Only 1 is able to be chosen.
        .respectResourcesWhileInBackground(secondsFrom5To45 = 20)
    //.runServiceInForeground(killAppIfTaskIsRemoved = true)
}

fun setupTorServices(application: Application) {
    TorServiceController.Builder(
        application = application,
        torServiceNotificationBuilder = generateTorServiceNotificationBuilder(application),
        backgroundManagerPolicy = generateBackgroundManagerPolicy(),
        buildConfigVersionCode = BuildConfig.VERSION_CODE,

        // Can instantiate directly here then access it from
        // TorServiceController.Companion.getTorSettings() and cast what's returned
        // as MyTorSettings
        defaultTorSettings = AppTorSettings(),

        // These should live somewhere in your module's assets directory,
        // ex: my-project/my-application-module/src/main/assets/common/geoip
        // ex: my-project/my-application-module/src/main/assets/common/geoip6
        geoipAssetPath = "common/geoip",
        geoip6AssetPath = "common/geoip6"
    )
        .setEventBroadcaster(eventBroadcaster = MyEventBroadcaster())
        .addTimeToDisableNetworkDelay(milliseconds = 1_000L)
        .addTimeToRestartTorDelay(milliseconds = 100L)
        .addTimeToStopServiceDelay(milliseconds = 100L)
        .disableStopServiceOnTaskRemoved(disable = false)
        .setBuildConfigDebug(buildConfigDebug = BuildConfig.DEBUG)

        .build()
}

class MyEventBroadcaster: TorServiceEventBroadcaster() {


    ///////////////////
    /// TorPortInfo ///
    ///////////////////
    private val _liveTorPortInfo = MutableLiveData<TorPortInfo>(null)
    val liveTorPortInfo: LiveData<TorPortInfo> = _liveTorPortInfo

    override fun broadcastPortInformation(torPortInfo: TorPortInfo) {
        _liveTorPortInfo.value = torPortInfo
    }

    override fun broadcastServiceLifecycleEvent(event: String, hashCode: Int) {
        broadcastLogMessage("NOTICE|TorService|LCE=$event - HashCode=$hashCode")
    }


    /////////////////
    /// Bandwidth ///
    /////////////////
    private var lastDownload = "0"
    private var lastUpload = "0"

    private val _liveBandwidth = MutableLiveData<String>(
        ServiceUtilities.getFormattedBandwidthString(0L, 0L)
    )
    val liveBandwidth: LiveData<String> = _liveBandwidth

    override fun broadcastBandwidth(bytesRead: String, bytesWritten: String) {
        if (bytesRead == lastDownload && bytesWritten == lastUpload) return

        lastDownload = bytesRead
        lastUpload = bytesWritten
        if (!liveBandwidth.hasActiveObservers()) return

        _liveBandwidth.value = ServiceUtilities.getFormattedBandwidthString(
            bytesRead.toLong(), bytesWritten.toLong()
        )
    }

    override fun broadcastDebug(msg: String) {
        broadcastLogMessage(msg)
    }

    override fun broadcastException(msg: String?, e: Exception) {
        if (msg.isNullOrEmpty()) return

        broadcastLogMessage(msg)
        e.printStackTrace()
    }


    ////////////////////
    /// Log Messages ///
    ////////////////////
    override fun broadcastLogMessage(logMessage: String?) {
        if (logMessage.isNullOrEmpty()) return

        val splitMsg = logMessage.split("|")
        if (splitMsg.size < 3) return

        Log.d("TOR-TEST", "${splitMsg[0]} | ${splitMsg[1]} | ${splitMsg[2]}")
    }

    override fun broadcastNotice(msg: String) {
        broadcastLogMessage(msg)
    }


    ///////////////////
    /// Tor's State ///
    ///////////////////
    inner class TorStateData(val state: String, val networkState: String)

    private var lastState = TorState.OFF
    private var lastNetworkState = TorNetworkState.DISABLED

    private val _liveTorState = MutableLiveData<TorStateData>(
        TorStateData(lastState, lastNetworkState)
    )
    val liveTorState: LiveData<TorStateData> = _liveTorState

    override fun broadcastTorState(@TorState state: String, @TorNetworkState networkState: String) {
        if (state == lastState && networkState == lastNetworkState) return

        lastState = state
        lastNetworkState = networkState
        _liveTorState.value = TorStateData(state, networkState)
    }
}