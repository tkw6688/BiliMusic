package com.thehbc.bilimusic.data.network.interceptor

import com.thehbc.bilimusic.data.local.AuthManager
import com.thehbc.bilimusic.data.network.api.WbiSign
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation
import java.net.URLEncoder
import java.security.MessageDigest

class WbiInterceptor(private val authManager: AuthManager) : Interceptor {

    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        // 检查是否有 @WbiSign 注解
        val invocation = request.tag(Invocation::class.java)
        val hasWbiSign = invocation?.method()?.getAnnotation(WbiSign::class.java) != null

        if (!hasWbiSign) {
            return chain.proceed(request)
        }

        // 需要 Wbi 签名
        val keys = authManager.getWbiKeysSync()
        val imgKey = keys.first
        val subKey = keys.second

        if (imgKey.isNullOrEmpty() || subKey.isNullOrEmpty()) {
            // 没有 key 无法签名，直接放行（可能会被 API 拒绝）
            return chain.proceed(request)
        }

        val mixinKey = getMixinKey(imgKey, subKey)
        val wts = (System.currentTimeMillis() / 1000).toString()

        val originalUrl = request.url
        val queryNames = originalUrl.queryParameterNames

        // 收集所有参数并加入 wts
        val paramsMap = mutableMapOf<String, String>()
        for (name in queryNames) {
            paramsMap[name] = originalUrl.queryParameter(name) ?: ""
        }
        paramsMap["wts"] = wts

        // 排序并过滤特殊字符
        val sortedParams = paramsMap.entries.sortedBy { it.key }
        val queryBuilder = StringBuilder()

        for (entry in sortedParams) {
            val key = entry.key
            // 过滤特殊字符 !'()* 
            val value = entry.value.replace(Regex("[!'()*]"), "")
            if (queryBuilder.isNotEmpty()) {
                queryBuilder.append("&")
            }
            queryBuilder.append(URLEncoder.encode(key, "UTF-8").replace("+", "%20"))
            queryBuilder.append("=")
            queryBuilder.append(URLEncoder.encode(value, "UTF-8").replace("+", "%20"))
        }

        val rawQuery = queryBuilder.toString()
        val wRid = md5(rawQuery + mixinKey)

        // 构造新的 URL
        val newUrl = originalUrl.newBuilder()
            .addQueryParameter("wts", wts)
            .addQueryParameter("w_rid", wRid)
            .build()

        request = request.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(request)
    }

    private fun getMixinKey(imgKey: String, subKey: String): String {
        val rawKey = imgKey + subKey
        val sb = StringBuilder()
        for (i in 0 until 32) {
            if (i < mixinKeyEncTab.size && mixinKeyEncTab[i] < rawKey.length) {
                sb.append(rawKey[mixinKeyEncTab[i]])
            }
        }
        return sb.toString()
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
