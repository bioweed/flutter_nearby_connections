package com.nankai.flutter_nearby_connections

import android.app.Activity
import android.content.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat.startForegroundService
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.Strategy
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.lang.Exception
import kotlin.system.exitProcess


const val initNearbyService = "init_nearby_service"
const val startAdvertisingPeer = "start_advertising_peer"
const val startBrowsingForPeers = "start_browsing_for_peers"

const val stopAdvertisingPeer = "stop_advertising_peer"
const val stopBrowsingForPeers = "stop_browsing_for_peers"

const val invitePeer = "invite_peer"
const val disconnectPeer = "disconnect_peer"

const val sendMessage = "send_message"

const val INVOKE_CHANGE_STATE_METHOD = "invoke_change_state_method"
const val INVOKE_MESSAGE_RECEIVE_METHOD = "invoke_message_receive_method"

const val NEARBY_RUNNING = "nearby_running"

/** FlutterNearbyConnectionsPlugin */
class FlutterNearbyConnectionsPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private val TAG = "FNC-FNCPlugin"
    private lateinit var channel: MethodChannel
    private var locationHelper: LocationHelper? = null
    private lateinit var activity: Activity
    private var binding: ActivityPluginBinding? = null
    private var callbackUtils: CallbackUtils? = null

    private var localDeviceName: String? = null
    private var strategy: Strategy? = null
    private var connectionsClient: ConnectionsClient? = null
    private var serviceBindManager: ServiceBindManager? = null
    private var isBind = false

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "onAttachedToEngine")
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, viewTypeId)
        channel.setMethodCallHandler(this)
    }

    companion object {
        private const val viewTypeId = "flutter_nearby_connections"

        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), viewTypeId)
            channel.setMethodCallHandler(FlutterNearbyConnectionsPlugin())
        }
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            initNearbyService -> {
                Log.d(TAG, "initNearbyService")
                callbackUtils = CallbackUtils(channel, activity)
                connectionsClient = Nearby.getConnectionsClient(activity)
                serviceBindManager = ServiceBindManager(activity, channel, callbackUtils!!)
                serviceBindManager!!.bindService()

                localDeviceName = if (call.argument<String>("deviceName").isNullOrEmpty())
                    Build.MANUFACTURER + " " + Build.MODEL
                else
                    call.argument<String>("deviceName")!!

                strategy = when (call.argument<Int>("strategy")) {
                    0 -> Strategy.P2P_CLUSTER
                    1 -> Strategy.P2P_STAR
                    else -> Strategy.P2P_POINT_TO_POINT
                }
                Log.d(TAG, "strategy: $strategy")

                locationHelper?.requestLocationPermission(result)
            }
            startAdvertisingPeer -> {
                Log.d(TAG, "startAdvertisingPeer")
                serviceBindManager!!.mService?.startAdvertising(strategy!!, localDeviceName!!)
                result.success(true)
            }
            startBrowsingForPeers -> {
                Log.d(TAG, "startBrowsingForPeers")
                serviceBindManager!!.mService?.startDiscovery(strategy!!)
                result.success(true)
            }
            stopAdvertisingPeer -> {
                Log.d(TAG, "stopAdvertisingPeer")
                serviceBindManager!!.mService?.stopAdvertising()
                serviceBindManager!!.unbindService()
                result.success(true)
            }
            stopBrowsingForPeers -> {
                Log.d(TAG, "stopBrowsingForPeers")
                serviceBindManager!!.mService?.stopDiscovery()
                serviceBindManager!!.unbindService()
                result.success(true)
            }
            invitePeer -> {
                Log.d(TAG, "invitePeer")
                val deviceId = call.argument<String>("deviceId")
                val displayName = call.argument<String>("deviceName")
                serviceBindManager!!.mService?.connect(deviceId!!, displayName!!)
                result.success(true)
            }
            disconnectPeer -> {
                Log.d(TAG, "disconnectPeer")
                val deviceId = call.argument<String>("deviceId")
                serviceBindManager!!.mService?.disconnect(deviceId!!)
                callbackUtils!!.updateStatus(deviceId = deviceId!!, state = notConnected)
                callbackUtils!!.invokeChangeState()
                result.success(true)
            }
            sendMessage -> {
                Log.d(TAG, "sendMessage")
                val deviceId = call.argument<String>("deviceId")
                val message = call.argument<String>("message")
                serviceBindManager!!.mService?.sendStringPayload(deviceId!!, message!!)
                result.success(true)
            }
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "onDetachedFromEngine")
        channel.setMethodCallHandler(null)
        serviceBindManager?.mService?.stopAdvertising()
        serviceBindManager?.mService?.stopDiscovery()
        serviceBindManager?.unbindService()
        locationHelper = null
        exitProcess(0)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        Log.d(TAG, "onAttachedToActivity")
        this.binding = binding
        activity = binding.activity
        locationHelper = LocationHelper(binding.activity)
        locationHelper?.let {
            binding.addActivityResultListener(it)
            binding.addRequestPermissionsResultListener(it)
        }
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        Log.d(TAG, "onReattachedToActivityForConfigChanges")
    }

    override fun onDetachedFromActivity() {
        Log.d(TAG, "onDetachedFromActivity")
        locationHelper?.let {
            binding?.removeRequestPermissionsResultListener(it)
            binding?.removeActivityResultListener(it)
        }
        binding = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        Log.d(TAG, "onDetachedFromActivityForConfigChanges")
    }
}
