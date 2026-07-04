package com.thehbc.bilimusic.data.network.api

import com.thehbc.bilimusic.data.network.model.FavFolderListResponse
import com.thehbc.bilimusic.data.network.model.FavResourceListResponse
import com.thehbc.bilimusic.data.network.model.NavResponse
import com.thehbc.bilimusic.data.network.model.PlayUrlResponse
import com.thehbc.bilimusic.data.network.model.QRCodePollResponse
import com.thehbc.bilimusic.data.network.model.QRCodeResponse
import com.thehbc.bilimusic.data.network.model.VideoDetailResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 标记该接口在发送前需要被 WbiInterceptor 拦截并进行 Wbi 签名计算
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class WbiSign

interface BiliApiService {

    // ==========================================
    // 登录与导航
    // ==========================================

    @GET("x/web-interface/nav")
    suspend fun getNavInfo(): NavResponse

    @GET("x/frontend/finger/spi")
    suspend fun getFingerSpi(): com.thehbc.bilimusic.data.network.model.SpiResponse

    @GET("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
    suspend fun getQRCode(): QRCodeResponse

    @GET("https://passport.bilibili.com/x/passport-login/web/qrcode/poll")
    suspend fun pollQRCode(@Query("qrcode_key") qrcodeKey: String): retrofit2.Response<QRCodePollResponse>

    // ==========================================
    // 收藏夹
    // ==========================================

    @GET("x/v3/fav/folder/created/list-all")
    suspend fun getCreatedFavFolders(
        @Query("up_mid") upMid: Long,
        @Query("type") type: Int = 2 // 0:全部 2:视频
    ): FavFolderListResponse

    @GET("x/v3/fav/folder/collected/list")
    suspend fun getCollectedFavFolders(
        @Query("up_mid") upMid: Long,
        @Query("pn") pageNum: Int = 1,
        @Query("ps") pageSize: Int = 20,
        @Query("platform") platform: String = "web"
    ): FavFolderListResponse

    @GET("x/v3/fav/resource/list")
    suspend fun getFavResources(
        @Query("media_id") mediaId: Long,
        @Query("pn") pageNum: Int = 1,
        @Query("ps") pageSize: Int = 20,
        @Query("platform") platform: String = "web"
    ): FavResourceListResponse

    // ==========================================
    // 视频信息与播放流
    // ==========================================

    @WbiSign
    @GET("x/web-interface/wbi/view")
    suspend fun getVideoDetail(
        @Query("bvid") bvid: String
    ): VideoDetailResponse

    @WbiSign
    @GET("x/player/wbi/playurl")
    suspend fun getPlayUrl(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int = 64,
        @Query("fnver") fnver: Int = 0,
        @Query("fnval") fnval: Int = 4048, // 包含 DASH 流
        @Query("fourk") fourk: Int = 1,
        @Query("from_client") fromClient: String = "BROWSER",
        @Query("gaia_source") gaiaSource: String = "view-card",
        @Query("is_main_page") isMainPage: Boolean = false,
        @Query("need_fragment") needFragment: Boolean = false,
        @Query("isGaiaAvoided") isGaiaAvoided: Boolean = true,
        @Query("voice_balance") voiceBalance: Int = 1,
        @Query("web_location") webLocation: Int = 1315873
    ): PlayUrlResponse
}
