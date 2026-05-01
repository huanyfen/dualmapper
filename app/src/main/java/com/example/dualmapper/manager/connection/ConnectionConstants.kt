package com.example.dualmapper.manager.connection

object ConnectionConstants {
    /** 默认 socket 连接超时 (毫秒) */
    const val DEFAULT_SOCKET_TIMEOUT = 5000L
    /** 服务器 socket accept 超时 (毫秒) */
    const val SERVER_ACCEPT_TIMEOUT = 15000L
    /** 心跳间隔 (毫秒) */
    const val HEARTBEAT_INTERVAL = 15000L
    /** 心跳丢失阈值 (连续未响应次数) */
    const val MISSED_HEARTBEAT_THRESHOLD = 3
    /** 重连基础延迟 (毫秒) */
    const val RECONNECT_BASE_DELAY = 3000L
    /** 最大重连尝试次数 */
    const val MAX_RECONNECT_ATTEMPTS = 5
    /** 单次会话最大总重连次数 */
    const val MAX_TOTAL_RECONNECT = 10
    /** LAN 广播间隔 (毫秒) */
    const val LAN_BROADCAST_INTERVAL = 2000L
    /** LAN 服务端口 */
    const val LAN_PORT = 21000
    /** LAN 广播端口 */
    const val LAN_BROADCAST_PORT = 21001
    /** LAN 服务标识 */
    const val LAN_SERVICE_ID = "DualMapperLAN"
    /** 蓝牙 UUID */
    const val BLUETOOTH_UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB"
    /** 远程默认端口 */
    const val REMOTE_PORT = 21000
}