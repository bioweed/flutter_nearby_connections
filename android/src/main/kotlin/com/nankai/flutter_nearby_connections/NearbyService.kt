package com.nankai.flutter_nearby_connections

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState.ACTION_STOP
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*


const val NOTIFICATION_ID = 101
const val CHANNEL_ID = "channel"

class NearbyService : Service() {
    private val TAG = "FNC-NearbyService"
    private val binder: IBinder = LocalBinder(this)
    private lateinit var callbackUtils: CallbackUtils
    private var connectionsClient: ConnectionsClient? = null
    private lateinit var serviceID: String

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, getNotification())
        }
        serviceID = packageName.substringAfterLast('.')
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    fun initService(callbackUtils: CallbackUtils) {
        Log.d(TAG, "initService")
        connectionsClient = Nearby.getConnectionsClient(this)
        this@NearbyService.callbackUtils = callbackUtils
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun sendStringPayload(endpointId: String, str: String) {
        Log.d(TAG, "sendStringPayload ${str.length} ${str.toByteArray().size} $str -> $endpointId")
        connectionsClient!!.sendPayload(endpointId, Payload.fromBytes(str.toByteArray()))
    }

    fun startAdvertising(strategy: Strategy, deviceName: String) {
        Log.d(TAG, "startAdvertising(); serviceID: $serviceID")
        connectionsClient!!.startAdvertising(
            deviceName, serviceID, callbackUtils.connectionLifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        )
    }

    fun startDiscovery(strategy: Strategy) {
        Log.d(TAG, "startDiscovery(); serviceID: $serviceID")
        connectionsClient!!.startDiscovery(
            serviceID, callbackUtils.endpointDiscoveryCallback,
            DiscoveryOptions.Builder().setStrategy(strategy).build()
        )
    }

    fun stopDiscovery() {
        Log.d(TAG, "stopDiscovery()")
        connectionsClient?.stopDiscovery()
    }

    fun stopAdvertising() {
        Log.d(TAG, "stopAdvertising()")
        connectionsClient?.stopAdvertising()
    }

    fun disconnect(endpointId: String) {
        Log.d(TAG, "disconnect $endpointId")
        connectionsClient!!.disconnectFromEndpoint(endpointId)
    }

    fun connect(endpointId: String, displayName: String) {
        Log.d(TAG, "connect $endpointId | $displayName")
        connectionsClient!!.requestConnection(
            displayName,
            endpointId,
            callbackUtils.connectionLifecycleCallback
        )
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        stopAdvertising()
        stopDiscovery()
        connectionsClient?.stopAllEndpoints() 
    }

    private fun getNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nearby Service")
            .setContentText("Wi-Fi Direct")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()
    }
}

internal class LocalBinder(private val nearbyService: NearbyService) : Binder() {
    val service: NearbyService
        get() = nearbyService
}