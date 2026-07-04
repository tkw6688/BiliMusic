package com.thehbc.bilimusic.ui.auth

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.thehbc.bilimusic.data.local.AuthManager
import com.thehbc.bilimusic.data.network.api.BiliApiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class QRCodeReady(val qrBitmap: Bitmap, val qrcodeKey: String, val message: String = "请使用哔哩哔哩App扫码") : AuthState()
    data class Success(val uname: String, val uid: Long) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val apiService: BiliApiService,
    private val authManager: AuthManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var pollJob: Job? = null

    init {
        // App 启动时，如果已有 Cookie，主动去 B 站验证并更新用户基本信息和 Wbi 密钥
        viewModelScope.launch {
            // 等待 AuthManager 从 DataStore 加载完成
            delay(500) 
            
            // 每次启动都去获取/刷新官方签发的真实设备指纹 (避免使用之前版本随机生成的假指纹导致 412)
            fetchAndSaveBuvid()
            
            if (!authManager.currentSessdata.isNullOrEmpty()) {
                fetchNavInfoAndComplete()
            }
        }
    }

    private suspend fun fetchAndSaveBuvid() {
        try {
            val response = apiService.getFingerSpi()
            if (response.code == 0 && response.data != null) {
                val b3 = response.data.b_3 ?: ""
                val b4 = response.data.b_4 ?: ""
                if (b3.isNotEmpty()) {
                    authManager.saveBuvids(b3, b4)
                    return
                }
            }
        } catch (e: Exception) {
            // 网络异常导致请求 SPI 失败，走本地随机降级
        }
        
        // 本地随机降级方案 (参考 wiliwili)
        val randomBuvid = authManager.generateRandomBuvid3()
        authManager.saveBuvids(randomBuvid, "")
    }

    fun generateQRCode() {
        viewModelScope.launch {
            _authState.update { AuthState.Loading }
            try {
                val response = apiService.getQRCode()
                if (response.code == 0 && response.data != null) {
                    val url = response.data.url ?: ""
                    val qrcodeKey = response.data.qrcode_key ?: ""
                    val bitmap = createQRCodeBitmap(url)
                    
                    _authState.update { AuthState.QRCodeReady(bitmap, qrcodeKey) }
                    startPolling(qrcodeKey)
                } else {
                    _authState.update { AuthState.Error(response.message) }
                }
            } catch (e: Exception) {
                _authState.update { AuthState.Error("网络错误: ${e.message}") }
            }
        }
    }

    private fun startPolling(qrcodeKey: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(3000) // 每3秒轮询一次
                try {
                    val response = apiService.pollQRCode(qrcodeKey)
                    val body = response.body()
                    if (body != null && body.code == 0) {
                        val dataCode = body.data?.code ?: -1
                        when (dataCode) {
                            0 -> {
                                // 扫码成功，提取 Set-Cookie
                                val headers = response.headers()
                                val cookies = headers.values("Set-Cookie")
                                var sessdata = ""
                                var biliJct = ""
                                var dedeUserId = ""
                                
                                for (cookie in cookies) {
                                    if (cookie.startsWith("SESSDATA=")) {
                                        sessdata = cookie.substringAfter("SESSDATA=").substringBefore(";")
                                    }
                                    if (cookie.startsWith("bili_jct=")) {
                                        biliJct = cookie.substringAfter("bili_jct=").substringBefore(";")
                                    }
                                    if (cookie.startsWith("DedeUserID=")) {
                                        dedeUserId = cookie.substringAfter("DedeUserID=").substringBefore(";")
                                    }
                                }
                                
                                // 保存 Cookie
                                authManager.saveCookies(sessdata, biliJct, dedeUserId)
                                
                                // 获取用户信息并保存 Wbi 密钥
                                fetchNavInfoAndComplete()
                                break
                            }
                            86038 -> {
                                _authState.update { AuthState.Error("二维码已失效，请重新生成") }
                                break
                            }
                            86090 -> {
                                val current = _authState.value
                                if (current is AuthState.QRCodeReady) {
                                    _authState.update { current.copy(message = "已扫码，请在手机端确认") }
                                }
                            }
                            else -> {
                                // 86101 未扫码，继续轮询
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 轮询过程中的网络错误暂时忽略，继续轮询
                }
            }
        }
    }

    fun loginWithCookie(cookieString: String) {
        viewModelScope.launch {
            _authState.update { AuthState.Loading }
            try {
                var sessdata = ""
                var biliJct = ""
                var dedeUserId = ""

                val parts = cookieString.split(";")
                for (part in parts) {
                    val kv = part.trim()
                    if (kv.startsWith("SESSDATA=")) sessdata = kv.substringAfter("SESSDATA=")
                    if (kv.startsWith("bili_jct=")) biliJct = kv.substringAfter("bili_jct=")
                    if (kv.startsWith("DedeUserID=")) dedeUserId = kv.substringAfter("DedeUserID=")
                }

                if (sessdata.isEmpty()) {
                    _authState.update { AuthState.Error("Cookie中未找到SESSDATA") }
                    return@launch
                }

                // 暂存 Cookie
                authManager.saveCookies(sessdata, biliJct, dedeUserId)

                // 验证并完成
                fetchNavInfoAndComplete()
            } catch (e: Exception) {
                _authState.update { AuthState.Error("解析异常: ${e.message}") }
            }
        }
    }

    private suspend fun fetchNavInfoAndComplete() {
        try {
            val navResponse = apiService.getNavInfo()
            if (navResponse.code == 0 && navResponse.data?.isLogin == true) {
                // 保存 Wbi 密钥
                navResponse.data.wbi_img?.let { wbi ->
                    val imgKey = wbi.img_url?.substringAfterLast("/")?.substringBefore(".png") ?: ""
                    val subKey = wbi.sub_url?.substringAfterLast("/")?.substringBefore(".png") ?: ""
                    authManager.saveWbiKeys(imgKey, subKey)
                }

                val uname = navResponse.data.uname ?: "未知用户"
                val uid = navResponse.data.mid ?: 0L
                val face = navResponse.data.face

                authManager.saveUserInfo(uname, uid, face)
                _authState.update { AuthState.Success(uname, uid) }
            } else if (navResponse.code == -101 || navResponse.data?.isLogin == false) {
                _authState.update { AuthState.Error("Cookie无效或已过期，请重新登录") }
                authManager.clearCookies()
            } else {
                // 其他业务错误（如 -412 风控等），不要清理 Cookie
                _authState.update { AuthState.Error("验证异常: ${navResponse.message}") }
            }
        } catch (e: Exception) {
            // 网络异常导致请求失败，绝对不能清理 Cookie，否则断网冷启动会掉登录状态
            _authState.update { AuthState.Error("网络异常，跳过在线验证: ${e.message}") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    private fun createQRCodeBitmap(content: String): Bitmap {
        val size = 512
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    companion object {
        fun provideFactory(apiService: BiliApiService, authManager: AuthManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(apiService, authManager) as T
                }
            }
    }
}
