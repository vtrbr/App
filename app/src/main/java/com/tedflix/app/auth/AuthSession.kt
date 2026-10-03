package com.tedflix.app.auth

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Sessão Tedflix: ID token e refresh token ficam cifrados no Android Keystore. */
object AuthSession {
    private const val TAG = "TedflixAuthSession"
    // User/Admin Server documentado em 02/10/2026. O Catalog Server continua separado.
    private const val AUTH_BASE = "https://servidores-ted-auth.onrender.com"
    const val MOVIE_API_BASE = "https://servidores-ted-auth-1.onrender.com/api"
    const val MOVIE_API_HOST = "servidores-ted-auth-1.onrender.com"
    private const val PREFS = "tedflix_auth_session"
    private const val TOKEN_KEY = "encrypted_access_token"
    private const val REFRESH_KEY = "encrypted_refresh_token"
    private const val KEY_ALIAS = "TedflixAuthBearerKey"
    private const val PROFILES_PREFS = "tedflix_saved_profiles"
    private const val PROFILES_KEY = "profiles_json"
    private const val ACTIVE_PROFILE_KEY = "active_profile_id"

    data class User(val id:String="", val email:String="", val username:String="", val expiresAt:String="", val accountStatus:String="", val daysRemaining:Int?=null, val createdAt:String="", val lastUsedAt:String="", val warning:JSONObject?=null, val name:String="", val role:String="", val emailVerified:Boolean?=null)
    data class Profile(val id:String, val name:String, val avatarSeed:String, val avatarStyle:String="fun-emoji", val email:String="", val username:String="", val isKids:Boolean=false, val isPrimary:Boolean=false)
    private data class StoredProfile(val id:String,val name:String,val avatarSeed:String,val avatarStyle:String,val email:String,val username:String,val isKids:Boolean,val isPrimary:Boolean,val user:User,val encryptedToken:String)
    data class Favorite(val filmeId:String,val titulo:String,val thumb:String,val adicionadoEm:String,val categoria:String="",val slug:String="",val tipo:String="")
    data class CatalogMatch(val filmeId:String,val titulo:String,val thumb:String,val categoria:String,val slug:String,val tipo:String)
    data class HistoryItem(val filmeId:String,val titulo:String,val tempo:String,val thumb:String,val ultimoAcesso:String,val categoria:String="",val slug:String="",val tipo:String="",val serieCategoria:String="",val serieSlug:String="",val positionSeconds:Long=0,val durationSeconds:Long=0)
    data class Result<T>(val ok:Boolean,val value:T?=null,val message:String="",val statusCode:Int=0)
    data class RawResponse(val statusCode:Int,val body:String,val contentType:String)
    private lateinit var appContext:Context
    fun init(context:Context){ appContext=context.applicationContext }
    fun hasToken()=token()?.isNotBlank()==true
    fun token():String? = readSecret(TOKEN_KEY)
    private fun refreshToken():String? = readSecret(REFRESH_KEY)
    private fun readSecret(key:String):String? { if(!::appContext.isInitialized)return null; val raw=appContext.getSharedPreferences(PREFS,0).getString(key,null)?:return null; return try{decrypt(raw)}catch(_:Throwable){null} }
    fun saveLogin(accessToken:String,user:User?,refresh:String?=null){ if(accessToken.isBlank())return; val e=appContext.getSharedPreferences(PREFS,0).edit().putString(TOKEN_KEY,encrypt(accessToken)); if(!refresh.isNullOrBlank())e.putString(REFRESH_KEY,encrypt(refresh)); e.putString("user_id",user?.id.orEmpty()).putString("user_email",user?.email.orEmpty()).putString("user_name",user?.username.orEmpty()).putString("user_real_name",user?.name.orEmpty()).putString("expires_at",user?.expiresAt.orEmpty()).putString("account_status",user?.accountStatus.orEmpty()).putInt("days_remaining",user?.daysRemaining?:-1).putString("created_at",user?.createdAt.orEmpty()).putString("last_used_at",user?.lastUsedAt.orEmpty()).putString("role",user?.role.orEmpty()).apply() }
    fun cachedUser():User? { if(!hasToken())return null; val p=appContext.getSharedPreferences(PREFS,0); return User(p.getString("user_id","").orEmpty(),p.getString("user_email","").orEmpty(),p.getString("user_name","").orEmpty(),p.getString("expires_at","").orEmpty(),p.getString("account_status","").orEmpty(),p.getInt("days_remaining",-1).takeIf{it>=0},p.getString("created_at","").orEmpty(),p.getString("last_used_at","").orEmpty(),name=p.getString("user_real_name","").orEmpty(),role=p.getString("role","").orEmpty()) }
    fun clear(){ if(!::appContext.isInitialized)return; appContext.getSharedPreferences(PREFS,0).edit().clear().apply(); appContext.getSharedPreferences(PROFILES_PREFS,0).edit().clear().apply() }
    fun profiles():List<Profile> = readStoredProfiles().map{it.public()}
    fun activeProfileId():String { val p=appContext.getSharedPreferences(PROFILES_PREFS,0); val id=p.getString(ACTIVE_PROFILE_KEY,"").orEmpty(); return readStoredProfiles().firstOrNull{it.id==id}?.id?:readStoredProfiles().firstOrNull()?.id.orEmpty() }
    fun restoreActiveProfileIfNeeded()=hasToken()
    fun ensureCurrentProfile(defaultName:String,defaultAvatarSeed:String):Profile? { val existing=readStoredProfiles().firstOrNull(); if(existing!=null)return existing.public(); return null }

    fun register(name:String,email:String,password:String):Result<User>{
        if(name.isBlank()||email.isBlank()||password.isBlank())return Result(false,message="Preencha nome, e-mail e senha.")
        return authRequest("/api/auth/register",JSONObject().put("name",name.trim()).put("email",email.trim()).put("password",password),true)
    }
    fun login(email:String,password:String):Result<User>{
        if(email.isBlank()||password.isBlank())return Result(false,message="Informe e-mail e senha.")
        return authRequest("/api/auth/login",JSONObject().put("email",email.trim()).put("password",password),true)
    }
    private fun authRequest(path:String,body:JSONObject,save:Boolean):Result<User>{ return try{ val r=rawRequest("POST",path,body.toString(),false); val j=parseObject(r.body); if(r.statusCode !in 200..299)return Result(false,message=serverMessage(j,r.statusCode),statusCode=r.statusCode); val auth=j.optJSONObject("auth")?:j; val token=auth.optString("idToken").ifBlank{j.optString("idToken")}.ifBlank{j.optString("token")}; if(token.isBlank())return Result(false,message="O servidor não devolveu uma sessão válida.",statusCode=r.statusCode); val baseUser=parseUser(j.optJSONObject("account")?:j.optJSONObject("user"))?:User(email=body.optString("email"),name=body.optString("name")); val sub=j.optJSONObject("subscription"); val user=if(sub!=null)baseUser.copy(expiresAt=baseUser.expiresAt.ifBlank{sub.optString("expiresAt")},accountStatus=baseUser.accountStatus.ifBlank{sub.optString("status")})else baseUser; saveLogin(token,user,auth.optString("refreshToken").ifBlank{j.optString("refreshToken")}); syncProfiles(j); Result(true,user,statusCode=r.statusCode) }catch(e:Throwable){Result(false,message=friendlyNetworkError(e))} }
    private fun syncProfiles(j:JSONObject){ val arr=j.optJSONArray("profiles")?:j.optJSONObject("data")?.optJSONArray("profiles")?:return; val token=token()?:return; val user=cachedUser()?:User(); val list=mutableListOf<StoredProfile>(); for(i in 0 until arr.length()){val p=arr.optJSONObject(i)?:continue; val av=p.optJSONObject("avatar"); val seed=av?.optString("seed").orEmpty().ifBlank{p.optString("avatarSeed").ifBlank{"tedflix-avatar-${i+1}"}}; list+=StoredProfile(p.optString("id").ifBlank{p.optString("profileId")},p.optString("name").ifBlank{"Meu perfil"},seed,av?.optString("style").orEmpty().ifBlank{p.optString("avatarStyle").ifBlank{"fun-emoji"}},user.email,p.optString("username"),p.optBoolean("isKids",false),p.optBoolean("isPrimary",false),user,encrypt(token))}; if(list.isNotEmpty())saveStoredProfiles(list,list.firstOrNull{it.isPrimary}?.id?:list.first().id) else appContext.getSharedPreferences(PROFILES_PREFS,0).edit().clear().apply() }
    fun refreshProfiles():Result<List<Profile>> { return try { val r=rawRequest("GET","/api/profiles",null,true); val j=parseObject(r.body); if(r.statusCode !in 200..299)return Result(false,message=serverMessage(j,r.statusCode),statusCode=r.statusCode); syncProfiles(j); Result(true,profiles(),statusCode=r.statusCode) } catch(e:Throwable) { Result(false,message=friendlyNetworkError(e)) } }
    fun activateProfile(id:String):Result<Profile>{ val p=readStoredProfiles().firstOrNull{it.id==id}?:return Result(false,message="Perfil não encontrado."); return try{val selected=rawRequest("POST","/api/profiles/${Uri.encode(id)}/select", "{}",true); val selectedJson=parseObject(selected.body); if(selected.statusCode !in 200..299)return Result(false,message=serverMessage(selectedJson,selected.statusCode),statusCode=selected.statusCode); val accessResponse=rawRequest("GET","/api/profiles/${Uri.encode(id)}/access",null,true); val accessJson=parseObject(accessResponse.body); if(accessResponse.statusCode !in 200..299)return Result(false,message=serverMessage(accessJson,accessResponse.statusCode),statusCode=accessResponse.statusCode); val access=accessJson.optJSONObject("access")?:return Result(false,message="A API não devolveu o estado de acesso do perfil.",statusCode=accessResponse.statusCode); if(!access.optBoolean("allowed",false))return Result(false,message="Este perfil não tem acesso liberado no momento.",statusCode=accessResponse.statusCode); appContext.getSharedPreferences(PROFILES_PREFS,0).edit().putString(ACTIVE_PROFILE_KEY,id).apply(); Result(true,p.public(),statusCode=accessResponse.statusCode)}catch(e:Throwable){Result(false,message=friendlyNetworkError(e))} }
    fun createProfile(code:String,email:String,password:String,name:String,avatarSeed:String):Result<Profile> = createRemoteProfile(name,false,"fun-emoji",avatarSeed)
    fun createRemoteProfile(name:String,isKids:Boolean,style:String,seed:String="",username:String=""):Result<Profile>{ val body=JSONObject().put("name",name).put("isKids",isKids).put("avatar",JSONObject().put("style",style).put("seed",seed.ifBlank{name})); if(username.isNotBlank())body.put("username",username); return try{val r=rawRequest("POST","/api/profiles",body.toString(),true); val j=parseObject(r.body); if(r.statusCode !in 200..299)return Result(false,message=serverMessage(j,r.statusCode),statusCode=r.statusCode); val p=j.optJSONObject("profile")?:j.optJSONObject("createdProfile")?:j; val token=token()?:return Result(false,message="Sessão expirada."); val sp=StoredProfile(p.optString("id").ifBlank{p.optString("profileId")},p.optString("name").ifBlank{name},p.optJSONObject("avatar")?.optString("seed").orEmpty().ifBlank{seed.ifBlank{name}},p.optJSONObject("avatar")?.optString("style").orEmpty().ifBlank{style},cachedUser()?.email.orEmpty(),p.optString("username").ifBlank{username},p.optBoolean("isKids",isKids),p.optBoolean("isPrimary",false),cachedUser()?:User(),encrypt(token)); val all=readStoredProfiles()+sp; saveStoredProfiles(all,sp.id); Result(true,sp.public(),statusCode=r.statusCode)}catch(e:Throwable){Result(false,message=friendlyNetworkError(e))} }
    fun updateProfile(id:String,name:String,avatarSeed:String):Boolean{ val old=readStoredProfiles().firstOrNull{it.id==id}?:return false; return try{val body=JSONObject().put("name",name.ifBlank{old.name}).put("avatar",JSONObject().put("style",old.avatarStyle).put("seed",avatarSeed.ifBlank{old.avatarSeed})); val r=rawRequest("PATCH","/api/profiles/${Uri.encode(id)}",body.toString(),true); if(r.statusCode !in 200..299)return false; saveStoredProfiles(readStoredProfiles().map{if(it.id==id)it.copy(name=name.ifBlank{it.name},avatarSeed=avatarSeed.ifBlank{it.avatarSeed})else it},activeProfileId());true}catch(_:Throwable){false} }
    fun deleteProfile(id:String):Boolean{ val p=readStoredProfiles().firstOrNull{it.id==id}?:return false;if(p.isPrimary)return false; return try{val r=rawRequest("DELETE","/api/profiles/${Uri.encode(id)}",null,true);if(r.statusCode !in 200..299)return false;saveStoredProfiles(readStoredProfiles().filterNot{it.id==id},readStoredProfiles().firstOrNull{it.id!=id}?.id.orEmpty());true}catch(_:Throwable){false} }
    fun updateProfileFull(id:String,name:String,username:String,style:String,seed:String,isKids:Boolean):Result<JSONObject>{ val result=authenticatedJson("PATCH","/api/profiles/${Uri.encode(id)}",JSONObject().put("name",name).put("username",username).put("isKids",isKids).put("avatar",JSONObject().put("style",style).put("seed",seed)).toString()); if(result.ok){ val current=readStoredProfiles(); saveStoredProfiles(current.map{if(it.id==id)it.copy(name=name,username=username,avatarStyle=style,avatarSeed=seed,isKids=isKids)else it},activeProfileId()) }; return result }
    fun verify():Result<User> = profile()
    fun profile():Result<User> = authenticatedJson("GET","/api/auth/me").map{parseUser(it.optJSONObject("account")?:it)?:cachedUser()?:User()}
    fun status():Result<JSONObject> = authenticatedJson("GET","/api/subscription")
    fun forgotPassword(email:String)=authenticatedJson("POST","/api/auth/forgot-password",JSONObject().put("email",email).toString())

    private fun profilePath(suffix:String)="/api/profiles/${Uri.encode(activeProfileId())}$suffix"
    fun listFavorites():Result<List<Favorite>> = authenticatedJson("GET",profilePath("/favorites")).map{j->val a=j.optJSONArray("favorites")?:j.optJSONArray("favoritos")?:JSONArray();buildList{for(i in 0 until a.length()){val x=a.optJSONObject(i)?:continue;add(Favorite(x.optString("contentId").ifBlank{x.optString("filmeId")},x.optString("title").ifBlank{x.optString("titulo")},x.optString("thumb").ifBlank{x.optString("imagem")},x.optString("createdAt").ifBlank{x.optString("adicionadoEm")},x.optString("categoria"),x.optString("slug"),x.optString("contentType").ifBlank{x.optString("tipo")}))}}}
    fun toggleFavorite(filmeId:String,titulo:String,thumb:String,contentType:String="movie")=authenticatedJson("POST",profilePath("/favorites/toggle"),JSONObject().put("contentId",filmeId).put("title",titulo).put("thumb",thumb).put("contentType",contentType).toString()).map{it.optBoolean("favorited",it.optBoolean("favorito"))}
    fun continueWatching():Result<List<HistoryItem>> = authenticatedJson("GET",profilePath("/history")).map{j->val a=j.optJSONArray("history")?:j.optJSONArray("items")?:j.optJSONArray("resultados")?:JSONArray();buildList{for(i in 0 until a.length()){val x=a.optJSONObject(i)?:continue;val pos=x.optLong("positionSeconds",x.optLong("tempoSeconds",0));val dur=x.optLong("durationSeconds",0);add(HistoryItem(x.optString("contentId").ifBlank{x.optString("filmeId")},x.optString("title").ifBlank{x.optString("titulo")},x.optString("tempo").ifBlank{pos.toString()},x.optString("thumb").ifBlank{x.optString("imagem")},x.optString("updatedAt"),x.optString("categoria"),x.optString("slug"),x.optString("contentType").ifBlank{x.optString("tipo")},positionSeconds=pos,durationSeconds=dur))}}}
    fun resolveCatalogItem(query:String,filmeId:String=""):Result<CatalogMatch?> = authenticatedJson("GET","/api/buscar/${Uri.encode(query.ifBlank{filmeId})}").map{j->val a=j.optJSONArray("resultados")?:JSONArray();val candidates=(0 until a.length()).mapNotNull{a.optJSONObject(it)};val x=candidates.firstOrNull{it.optString("contentId")==filmeId||it.optString("filmeId")==filmeId||it.optString("link_assistir").contains(filmeId)}?:candidates.firstOrNull()?:return@map null;val link=x.optString("link_assistir").substringBefore("?").trimEnd('/');val parts=link.split("/").filter{it.isNotBlank()};val cat=x.optString("categoria").ifBlank{parts.getOrNull(parts.size-2).orEmpty()};val sl=x.optString("slug").ifBlank{parts.lastOrNull().orEmpty()};CatalogMatch(x.optString("contentId").ifBlank{x.optString("filmeId").ifBlank{sl}},x.optString("titulo"),x.optString("thumb").ifBlank{x.optString("imagem")},cat,sl,x.optString("tipo"))}
    fun saveProgress(filmeId:String,titulo:String,tempo:String,thumb:String,durationSeconds:Long=0,categoria:String="",slug:String="",tipo:String="",serieCategoria:String="",serieSlug:String=""):Result<JSONObject>{val p=tempo.split(":").mapNotNull{it.toLongOrNull()};val pos=when(p.size){3->p[0]*3600+p[1]*60+p[2];2->p[0]*60+p[1];1->p[0];else->0};return authenticatedJson("PUT",profilePath("/progress"),JSONObject().put("contentId",filmeId).put("title",titulo).put("thumb",thumb).put("contentType",if(tipo.contains("epis",true))"episode" else "movie").put("positionSeconds",pos).put("durationSeconds",durationSeconds).toString())}
    fun logout():Result<JSONObject>{clear();return Result(true,JSONObject(),statusCode=200)}

    fun proxyMovieRequest(request:WebResourceRequest):RawResponse?{val u=request.url;if(u.host!=MOVIE_API_HOST||!u.path.orEmpty().startsWith("/api/"))return null;return try{rawRequest(request.method.ifBlank{"GET"},u.encodedPath.orEmpty()+u.encodedQuery.orEmpty().let{if(it.isBlank())"" else "?$it"},null,true)}catch(e:Throwable){RawResponse(599,JSONObject().put("error",friendlyNetworkError(e)).toString(),"application/json")}}
    fun proxyExternalImageRequest(request:WebResourceRequest):WebResourceResponse? {
        val u=request.url
        val host=u.host.orEmpty().lowercase()
        val path=u.path.orEmpty().lowercase()
        val allowed=host=="novelasflix.video" || host.endsWith(".novelasflix.video") || host=="image.tmdb.org" || host=="api.dicebear.com"
        if(!allowed || request.method.uppercase()!="GET") return null
        return try {
            val c=(URL(u.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout=12000; readTimeout=20000; instanceFollowRedirects=true; useCaches=true
                setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android) Tedflix/1.0")
                setRequestProperty("Accept","image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                if(host.contains("novelasflix")) setRequestProperty("Referer","https://novelasflix.video/")
            }
            val code=c.responseCode
            if(code !in 200..299){c.disconnect();return null}
            val bytes=c.inputStream.use{it.readBytes()}
            val type=c.contentType?.substringBefore(';')?.ifBlank{null} ?: when {
                path.endsWith(".webp")->"image/webp"
                path.endsWith(".png")->"image/png"
                else->"image/jpeg"
            }
            c.disconnect()
            WebResourceResponse(type,null,200,"OK",mapOf("Cache-Control" to "public, max-age=3600"),ByteArrayInputStream(bytes))
        } catch(_:Throwable){null}
    }
    fun toWebResourceResponse(r:RawResponse)=WebResourceResponse(r.contentType.substringBefore(';').ifBlank{"application/json"},"UTF-8",r.statusCode.coerceIn(100,599),"OK",mapOf("Cache-Control" to "no-store","Pragma" to "no-cache"),ByteArrayInputStream(r.body.toByteArray(StandardCharsets.UTF_8)))
    private fun <T>Result<JSONObject>.map(f:(JSONObject)->T):Result<T> = if(ok)Result(true,f(value?:JSONObject()),statusCode=statusCode) else Result(false,message=message,statusCode=statusCode)
    private fun authenticatedJson(method:String,path:String,body:String?=null):Result<JSONObject>{return try{val r=rawRequest(method,path,body,true);val j=parseObject(r.body);if(r.statusCode in 200..299)Result(true,j,statusCode=r.statusCode)else{if(r.statusCode==401||r.statusCode==403)clear();Result(false,message=serverMessage(j,r.statusCode),statusCode=r.statusCode)}}catch(e:Throwable){Result(false,message=friendlyNetworkError(e))}}
    fun rawRequest(method:String,path:String,body:String?=null,includeBearer:Boolean):RawResponse{val url=if(path.startsWith("http"))path else if(path.startsWith("/api/")){if(path.startsWith("/api/filmes")||path.startsWith("/api/series")||path.startsWith("/api/animacoes")||path.startsWith("/api/genero")||path.startsWith("/api/buscar")||path.startsWith("/api/home"))"https://$MOVIE_API_HOST$path" else "$AUTH_BASE$path"}else "$AUTH_BASE$path";val c=(URL(url).openConnection() as HttpURLConnection).apply{requestMethod=method.uppercase();connectTimeout=15000;readTimeout=30000;instanceFollowRedirects=true;useCaches=false;setRequestProperty("Accept","application/json");setRequestProperty("Cache-Control","no-store");if(body!=null){doOutput=true;setRequestProperty("Content-Type","application/json; charset=utf-8")};if(includeBearer)token()?.let{setRequestProperty("Authorization","Bearer $it")}};return try{if(body!=null)c.outputStream.use{it.write(body.toByteArray(StandardCharsets.UTF_8))};val s=c.responseCode;val stream=if(s in 200..399)c.inputStream else c.errorStream;RawResponse(s,stream?.bufferedReader(StandardCharsets.UTF_8)?.use{it.readText()}.orEmpty(),c.contentType?:"application/json")}finally{c.disconnect()}}
    private fun parseObject(s:String)=try{if(s.trim().startsWith("{"))JSONObject(s)else JSONObject()}catch(_:Throwable){JSONObject()}
    private fun parseUser(j:JSONObject?):User?{if(j==null)return null;return User(j.optString("uid").ifBlank{j.optString("id")},j.optString("email"),j.optString("username").ifBlank{j.optString("name")},j.optString("expiresAt").ifBlank{j.optString("subscriptionExpiresAt")},j.optString("status").ifBlank{j.optString("accountStatus")},j.optInt("daysRemaining",-1).takeIf{it>=0},j.optString("createdAt"),j.optString("lastLoginAt"),name=j.optString("name"),role=j.optString("role"),emailVerified=if(j.has("emailVerified"))j.optBoolean("emailVerified")else null)}
    private fun serverMessage(j:JSONObject,c:Int)=j.optString("error").ifBlank{j.optString("message")}.ifBlank{when(c){400->"Confira os dados informados.";401->"Sessão inválida ou credenciais incorretas.";403->"Acesso negado para esta conta.";404->"Recurso não encontrado.";409->"E-mail ou username já utilizado.";429->"Muitas tentativas. Aguarde alguns minutos.";in 500..599->"Servidor indisponível no momento.";else->"Não foi possível concluir a operação."}}
    private fun friendlyNetworkError(e:Throwable)="Não foi possível conectar ao servidor. Verifique sua internet."
    private fun readStoredProfiles():List<StoredProfile>{if(!::appContext.isInitialized)return emptyList();return try{val a=JSONArray(appContext.getSharedPreferences(PROFILES_PREFS,0).getString(PROFILES_KEY,"[]"));buildList{for(i in 0 until a.length()){val x=a.optJSONObject(i)?:continue;val enc=x.optString("encryptedToken");if(enc.isBlank())continue;add(StoredProfile(x.optString("id"),x.optString("name").ifBlank{"Meu perfil"},x.optString("avatarSeed").ifBlank{"tedflix-avatar-01"},x.optString("avatarStyle").ifBlank{"fun-emoji"},x.optString("email"),x.optString("username"),x.optBoolean("isKids"),x.optBoolean("isPrimary"),User(email=x.optString("email"),username=x.optString("username")),enc))}}}catch(_:Throwable){emptyList()}}
    private fun saveStoredProfiles(ps:List<StoredProfile>,active:String){val a=JSONArray();ps.forEach{p->a.put(JSONObject().put("id",p.id).put("name",p.name).put("avatarSeed",p.avatarSeed).put("avatarStyle",p.avatarStyle).put("email",p.email).put("username",p.username).put("isKids",p.isKids).put("isPrimary",p.isPrimary).put("encryptedToken",p.encryptedToken))};appContext.getSharedPreferences(PROFILES_PREFS,0).edit().putString(PROFILES_KEY,a.toString()).putString(ACTIVE_PROFILE_KEY,active).apply()}
    private fun StoredProfile.public()=Profile(id,name,avatarSeed,avatarStyle,email,username,isKids,isPrimary)
    private fun key():SecretKey{val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};val old=ks.getKey(KEY_ALIAS,null) as? SecretKey;if(old!=null)return old;val g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey()}
    private fun encrypt(v:String):String{val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(c.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(v.toByteArray()),Base64.NO_WRAP)}
    private fun decrypt(v:String):String{val p=v.split(":",limit=2);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));return String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)))}
}
