package com.thehbc.bilimusic.data.network.interceptor

import com.thehbc.bilimusic.data.local.AuthManager
import okhttp3.Interceptor
import okhttp3.Response

class CookieInterceptor(private val authManager: AuthManager) : Interceptor {
    companion object {
        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
        private const val WEB_REFERER = "https://www.bilibili.com/"
        private const val WEB_ORIGIN = "https://www.bilibili.com"
        private const val ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.8"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        
        // 如果没有 Cookie，直接放行
        val sessdata = authManager.currentSessdata
        val biliJct = authManager.currentBiliJct
        val dedeUserId = authManager.currentDedeUserId
        
        // 如果没有 buvid3，尝试拦截？不需要，这里作为统一的 cookie 构建
        // 但如果什么都没有，至少也要保证能发请求
        if (sessdata.isNullOrEmpty() && authManager.currentBuvid3.isNullOrEmpty()) {
            return chain.proceed(original)
        }
        
        val cookieBuilder = StringBuilder()
        
        // 注入 buvid 指纹
        val buvid3 = authManager.currentBuvid3
        val buvid4 = authManager.currentBuvid4
        if (!buvid3.isNullOrEmpty()) {
            cookieBuilder.append("buvid3=$buvid3;")
        }
        if (!buvid4.isNullOrEmpty()) {
            cookieBuilder.append(" buvid4=$buvid4;")
        }
        
        // 注入登录态
        if (!sessdata.isNullOrEmpty()) {
            cookieBuilder.append(" SESSDATA=$sessdata;")
        }
        if (!biliJct.isNullOrEmpty()) {
            cookieBuilder.append(" bili_jct=$biliJct;")
        }
        if (!dedeUserId.isNullOrEmpty()) {
            cookieBuilder.append(" DedeUserID=$dedeUserId;")
        }
        
        val requestBuilder = original.newBuilder()
            .header("Cookie", cookieBuilder.toString())
            // B站一些接口（特别是播放和用户空间）对 User-Agent 和 Referer 有强校验
            .header("User-Agent", WEB_USER_AGENT)
            .header("Referer", WEB_REFERER)
            .header("Origin", WEB_ORIGIN)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", ACCEPT_LANGUAGE)
            
        return chain.proceed(requestBuilder.build())
    }
}
