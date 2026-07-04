package com.thehbc.bilimusic.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bili_settings")

object BiliDataStore {
    val SESSDATA = stringPreferencesKey("sessdata")
    val BILI_JCT = stringPreferencesKey("bili_jct")
    val DEDE_USER_ID = stringPreferencesKey("dede_user_id")
    
    val IMG_KEY = stringPreferencesKey("img_key")
    val SUB_KEY = stringPreferencesKey("sub_key")
    val WBI_UPDATE_TIME = longPreferencesKey("wbi_update_time")
    
    val UNAME = stringPreferencesKey("uname")
    val UID = longPreferencesKey("uid")
    val FACE = stringPreferencesKey("face")
    
    val BUVID3 = stringPreferencesKey("buvid3")
    val BUVID4 = stringPreferencesKey("buvid4")
}

class AuthManager(private val context: Context) {
    
    val sessdataFlow: Flow<String?> = context.dataStore.data.map { it[BiliDataStore.SESSDATA] }
    val biliJctFlow: Flow<String?> = context.dataStore.data.map { it[BiliDataStore.BILI_JCT] }
    
    val unameFlow: Flow<String?> = context.dataStore.data.map { it[BiliDataStore.UNAME] }
    val uidFlow: Flow<Long?> = context.dataStore.data.map { it[BiliDataStore.UID] }
    val faceFlow: Flow<String?> = context.dataStore.data.map { it[BiliDataStore.FACE] }
    
    // Cookie 拦截器可用的同步读取方法（如果在协程外部，可能需要特殊处理，但最好由拦截器持有最新状态）
    var currentSessdata: String? = null
        private set
    var currentBiliJct: String? = null
        private set
    var currentDedeUserId: String? = null
        private set
        
    var currentBuvid3: String? = null
        private set
    var currentBuvid4: String? = null
        private set
        
    init {
        runBlocking {
            val prefs = context.dataStore.data.first()
            currentSessdata = prefs[BiliDataStore.SESSDATA]
            currentBiliJct = prefs[BiliDataStore.BILI_JCT]
            currentDedeUserId = prefs[BiliDataStore.DEDE_USER_ID]
            currentBuvid3 = prefs[BiliDataStore.BUVID3]
            currentBuvid4 = prefs[BiliDataStore.BUVID4]
        }
    }
        
    suspend fun saveCookies(sessdata: String, biliJct: String, dedeUserId: String) {
        context.dataStore.edit { prefs ->
            prefs[BiliDataStore.SESSDATA] = sessdata
            prefs[BiliDataStore.BILI_JCT] = biliJct
            prefs[BiliDataStore.DEDE_USER_ID] = dedeUserId
        }
        currentSessdata = sessdata
        currentBiliJct = biliJct
        currentDedeUserId = dedeUserId
    }
    
    suspend fun saveUserInfo(uname: String, uid: Long, face: String?) {
        context.dataStore.edit { prefs ->
            prefs[BiliDataStore.UNAME] = uname
            prefs[BiliDataStore.UID] = uid
            if (face != null) {
                prefs[BiliDataStore.FACE] = face
            } else {
                prefs.remove(BiliDataStore.FACE)
            }
        }
    }
    
    suspend fun clearCookies() {
        context.dataStore.edit { prefs ->
            prefs.remove(BiliDataStore.SESSDATA)
            prefs.remove(BiliDataStore.BILI_JCT)
            prefs.remove(BiliDataStore.DEDE_USER_ID)
            prefs.remove(BiliDataStore.UNAME)
            prefs.remove(BiliDataStore.UID)
            prefs.remove(BiliDataStore.FACE)
            prefs.remove(BiliDataStore.BUVID3)
            prefs.remove(BiliDataStore.BUVID4)
        }
        currentSessdata = null
        currentBiliJct = null
        currentDedeUserId = null
        currentBuvid3 = null
        currentBuvid4 = null
    }
    
    suspend fun saveWbiKeys(imgKey: String, subKey: String) {
        context.dataStore.edit { prefs ->
            prefs[BiliDataStore.IMG_KEY] = imgKey
            prefs[BiliDataStore.SUB_KEY] = subKey
            prefs[BiliDataStore.WBI_UPDATE_TIME] = System.currentTimeMillis()
        }
    }
    
    fun getWbiKeysSync(): Pair<String?, String?> {
        return runBlocking {
            val prefs = context.dataStore.data.first()
            Pair(prefs[BiliDataStore.IMG_KEY], prefs[BiliDataStore.SUB_KEY])
        }
    }

    suspend fun saveBuvids(buvid3: String, buvid4: String) {
        context.dataStore.edit { prefs ->
            prefs[BiliDataStore.BUVID3] = buvid3
            prefs[BiliDataStore.BUVID4] = buvid4
        }
        currentBuvid3 = buvid3
        currentBuvid4 = buvid4
    }

    fun generateRandomBuvid3(): String {
        val chars = "0123456789abcdef"
        return buildString {
            repeat(32) { append(chars.random()) }
        }
    }
}
