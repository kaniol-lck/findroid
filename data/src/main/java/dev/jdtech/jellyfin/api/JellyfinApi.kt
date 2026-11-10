package dev.jdtech.jellyfin.api

import android.content.Context
import android.os.Build
import dev.jdtech.jellyfin.data.BuildConfig
import dev.jdtech.jellyfin.settings.domain.Constants
import java.util.UUID
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.extensions.authenticationApi
import org.jellyfin.sdk.api.client.extensions.brandingApi
import org.jellyfin.sdk.api.client.extensions.deviceApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.suggestionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.trickPlayApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userDataApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.api.client.extensions.videoApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo

/**
 * Jellyfin API class using org.jellyfin.sdk:jellyfin-core
 *
 * @param androidContext The context
 * @param socketTimeout The socket timeout
 * @constructor Creates a new [JellyfinApi] instance
 */
class JellyfinApi(
    androidContext: Context,
    requestTimeout: Long = Constants.NETWORK_DEFAULT_REQUEST_TIMEOUT,
    connectTimeout: Long = Constants.NETWORK_DEFAULT_CONNECT_TIMEOUT,
    socketTimeout: Long = Constants.NETWORK_DEFAULT_SOCKET_TIMEOUT,
) {
    val jellyfin = createJellyfin {
        clientInfo =
            ClientInfo(
                name =
                    androidContext.applicationInfo
                        .loadLabel(androidContext.packageManager)
                        .toString(),
                version = BuildConfig.VERSION_NAME,
            )
        context = androidContext
        // 默认的 androidDevice() 直接拿 Settings.Secure.ANDROID_ID 当 DeviceId。
        // 同一台设备上的其它 Jellyfin 客户端（官方 App 也是这个值）会因此被服务端
        // 当成同一台设备，登录时把彼此的 access token 顶掉。这里换成只属于本 App 的 id。
        deviceInfo = DeviceInfo(id = ourDeviceId(androidContext), name = Build.MODEL)
    }
    val api =
        jellyfin.createApi(
            httpClientOptions =
                HttpClientOptions(
                    requestTimeout = requestTimeout.toDuration(DurationUnit.MILLISECONDS),
                    connectTimeout = connectTimeout.toDuration(DurationUnit.MILLISECONDS),
                    socketTimeout = socketTimeout.toDuration(DurationUnit.MILLISECONDS),
                )
        )
    var userId: UUID? = null

    val brandingApi = api.brandingApi
    val authenticationApi = api.authenticationApi
    val deviceApi = api.deviceApi
    val libraryApi = api.libraryApi
    val mediaInfoApi = api.mediaInfoApi
    val mediaSegmentApi = api.mediaSegmentApi
    val sessionApi = api.sessionApi
    val showApi = api.showApi
    val suggestionApi = api.suggestionApi
    val systemApi = api.systemApi
    val trickPlayApi = api.trickPlayApi
    val userApi = api.userApi
    val userDataApi = api.userDataApi
    val videoApi = api.videoApi
    val userViewApi = api.userViewApi

    companion object {
        @Volatile private var INSTANCE: JellyfinApi? = null

        fun getInstance(
            context: Context,
            requestTimeout: Long = Constants.NETWORK_DEFAULT_REQUEST_TIMEOUT,
            connectTimeout: Long = Constants.NETWORK_DEFAULT_CONNECT_TIMEOUT,
            socketTimeout: Long = Constants.NETWORK_DEFAULT_SOCKET_TIMEOUT,
        ): JellyfinApi {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance =
                        JellyfinApi(
                            androidContext = context.applicationContext,
                            requestTimeout = requestTimeout,
                            connectTimeout = connectTimeout,
                            socketTimeout = socketTimeout,
                        )
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}

/**
 * [device-id-fix] 本 App 独占的设备 id。
 *
 * Jellyfin 用 DeviceId 区分设备，一台设备只保留一个 access token；SDK 默认把
 * ANDROID_ID 当 DeviceId，于是官方客户端和本 App 在同一台机器上会互相把对方踢下线。
 * 这里生成一个只属于本 App 的随机 id 并持久化，重启/升级都不变。
 */
private fun ourDeviceId(context: Context): String {
    val prefs = context.getSharedPreferences("findroid_device_id", Context.MODE_PRIVATE)
    prefs.getString("id", null)?.let { return it }
    val id = "fd" + UUID.randomUUID().toString().replace("-", "").take(14)
    prefs.edit().putString("id", id).apply()
    return id
}
