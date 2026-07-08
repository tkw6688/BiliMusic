package com.thehbc.bilimusic.data.network.model

import com.google.gson.annotations.SerializedName

// ==========================================
// 登录相关数据模型
// ==========================================

data class SpiResponse(
    val code: Int,
    val message: String,
    val data: SpiData?
)

data class SpiData(
    val b_3: String?,
    val b_4: String?
)

data class NavResponse(
    val code: Int,
    val message: String,
    val data: NavData?
)

data class NavData(
    val isLogin: Boolean,
    val uname: String?,
    val mid: Long?,
    val face: String?,
    val wbi_img: WbiImg?
)

data class WbiImg(
    val img_url: String?,
    val sub_url: String?
)

data class QRCodeResponse(
    val code: Int,
    val message: String,
    val data: QRCodeData?
)

data class QRCodeData(
    val url: String?,
    val qrcode_key: String?
)

data class QRCodePollResponse(
    val code: Int,
    val message: String,
    val data: QRCodePollData?
)

data class QRCodePollData(
    val code: Int,
    val message: String?,
    val url: String?
)

// ==========================================
// 收藏夹相关数据模型
// ==========================================

data class FavFolderListResponse(
    val code: Int,
    val message: String,
    val data: FavFolderList?
)

data class FavFolderList(
    val list: List<FavFolder>?
)

data class FavFolder(
    val id: Long,
    val title: String?,
    val media_count: Int,
    val attr: Int
)

data class FavResourceListResponse(
    val code: Int,
    val message: String,
    val data: FavResourceList?
)

data class FavResourceList(
    val info: FavInfo?,
    val medias: List<FavMedia>?,
    val has_more: Boolean
)

data class FavInfo(
    val title: String?,
    val media_count: Int
)

data class FavMedia(
    val id: Long,
    val bvid: String?,
    val title: String?,
    val cover: String?,
    val duration: Int?,
    val upper: FavUpper?,
    val ugc: FavUgc?,
    val page: Int? // 视频分P总数
)

data class FavUgc(
    val first_cid: Long?
)

data class FavUpper(
    val mid: Long,
    val name: String?,
    val face: String?
)

// ==========================================
// 视频基本信息数据模型
// ==========================================

data class VideoDetailResponse(
    val code: Int,
    val message: String,
    val data: VideoDetail?
)

data class VideoDetail(
    val bvid: String?,
    val aid: Long?,
    val title: String?,
    val desc: String?,
    val pic: String?,
    val owner: VideoOwner?,
    val stat: VideoStat?,
    val pages: List<VideoPage>?,
    val cid: Long?,
    val duration: Long,
    val pubdate: Long,
    val ctime: Long,
    val videos: Int,
    val ugc_season: UgcSeason?
)

data class VideoOwner(
    val mid: Long,
    val name: String?,
    val face: String?
)

data class VideoStat(
    val view: Int,
    val danmaku: Int,
    val reply: Int,
    val favorite: Int,
    val coin: Int,
    val share: Int,
    val like: Int
)

data class VideoPage(
    val cid: Long,
    val page: Int,
    val part: String?,
    val duration: Long
)

data class UgcSeason(
    val sections: List<UgcSection>?
)

data class UgcSection(
    val episodes: List<UgcEpisode>?
)

data class UgcEpisode(
    val bvid: String?,
    val aid: Long?,
    val cid: Long?,
    val title: String?,
    val arc: VideoDetail?
)

// ==========================================
// 视频流数据模型
// ==========================================

data class PlayUrlResponse(
    val code: Int,
    val message: String,
    val data: PlayUrlData?
)

data class PlayUrlData(
    val dash: Dash?,
    val durl: List<DurlStream>?
)

data class DurlStream(
    val order: Int,
    val length: Long,
    val size: Long,
    val url: String?,
    val backup_url: List<String>?
)

data class Dash(
    val video: List<DashStream>?,
    val audio: List<DashStream>?,
    val flac: Flac?,
    val dolby: Dolby?
)

data class DashStream(
    val id: Int,
    @SerializedName("baseUrl") val baseUrl: String?,
    @SerializedName("base_url") val base_url: String?,
    val backupUrl: List<String>?,
    val bandwidth: Int,
    val codecs: String?,
    val width: Int?,
    val height: Int?,
    val frameRate: String?,
    val mimeType: String?
)

data class Flac(
    val display: Boolean,
    val audio: DashStream?
)

data class Dolby(
    val type: Int,
    val audio: List<DashStream>?
)
