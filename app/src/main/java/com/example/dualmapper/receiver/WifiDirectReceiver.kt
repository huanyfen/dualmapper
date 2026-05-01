package com.example.dualmapper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import com.example.dualmapper.manager.connection.WifiDirectManager

class WifiDirectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = WifiDirectManager.getInstance(context)
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_CONNECTION_STATE_CHANGED_ACTION -> {
                manager.requestConnectionInfo { info ->
                    manager.handleConnectionInfo(info)
                }
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                manager.onPeersChanged()
            }
        }
    }
}