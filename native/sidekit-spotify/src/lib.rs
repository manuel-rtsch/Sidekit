//! JNI surface for Sidetracks. All state lives in `engine`; these functions only translate.

mod audio;
mod catalog;
mod engine;

use jni::objects::{JObject, JString};
use jni::sys::{jboolean, jbyteArray, jfloat, jlong, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::time::Duration;

fn string(env: &mut JNIEnv, value: &JString) -> Option<String> {
    env.get_string(value).ok().map(Into::into)
}

fn catalog_result(env: &mut JNIEnv, result: Result<String, String>) -> jni::sys::jstring {
    let value = match result {
        Ok(value) => value,
        Err(error) => serde_json::json!({ "error": error }).to_string(),
    };
    env.new_string(value)
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeInit(
    _env: JNIEnv,
    _this: JObject,
) -> jboolean {
    #[cfg(target_os = "android")]
    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Info),
    );
    if engine::init().is_ok() {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeSetTmpDir(
    mut env: JNIEnv,
    _this: JObject,
    path: JString,
) {
    if let Some(path) = string(&mut env, &path) {
        engine::set_tmp_dir(&path);
    }
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeConnect(
    mut env: JNIEnv,
    _this: JObject,
    token: JString,
) -> jboolean {
    match string(&mut env, &token).map(|token| engine::connect(&token)) {
        Some(Ok(())) => JNI_TRUE,
        _ => JNI_FALSE,
    }
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeSetAccessToken(
    mut env: JNIEnv,
    _this: JObject,
    token: JString,
) -> jni::sys::jboolean {
    let Some(token) = string(&mut env, &token) else {
        return JNI_FALSE;
    };
    bool_result(engine::set_access_token(&token))
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeDisconnect(
    _env: JNIEnv,
    _this: JObject,
) {
    engine::disconnect();
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeCreatePlayer(
    _env: JNIEnv,
    _this: JObject,
) -> jboolean {
    if engine::create_player().is_ok() {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeLoad(
    mut env: JNIEnv,
    _this: JObject,
    uri: JString,
    position_ms: jlong,
    autoplay: jboolean,
) -> jboolean {
    match string(&mut env, &uri)
        .map(|uri| engine::load(&uri, position_ms.max(0) as u32, autoplay == JNI_TRUE))
    {
        Some(Ok(())) => JNI_TRUE,
        _ => JNI_FALSE,
    }
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativePlay(
    _env: JNIEnv,
    _this: JObject,
) -> jboolean {
    bool_result(engine::play())
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativePause(
    _env: JNIEnv,
    _this: JObject,
) -> jboolean {
    bool_result(engine::pause())
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeStop(
    _env: JNIEnv,
    _this: JObject,
) -> jboolean {
    bool_result(engine::stop())
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeSeek(
    _env: JNIEnv,
    _this: JObject,
    position_ms: jlong,
) -> jboolean {
    bool_result(engine::seek(position_ms.max(0) as u32))
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeSetVolume(
    _env: JNIEnv,
    _this: JObject,
    volume: jfloat,
) {
    engine::set_volume(volume);
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeReadAudio(
    env: JNIEnv,
    _this: JObject,
    timeout_ms: jlong,
) -> jbyteArray {
    let timeout = Duration::from_millis(timeout_ms.max(0) as u64);
    match engine::read_audio(timeout) {
        Some(bytes) => match env.byte_array_from_slice(&bytes) {
            Ok(array) => array.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeAudioFormat(
    env: JNIEnv,
    _this: JObject,
) -> jni::sys::jstring {
    match env.new_string(engine::audio_format()) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativePollEvent(
    env: JNIEnv,
    _this: JObject,
) -> jni::sys::jstring {
    match engine::poll_event() {
        Some(event) => match env.new_string(event) {
            Ok(value) => value.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeCatalogLibrary(
    mut env: JNIEnv,
    _this: JObject,
    filter: JString,
) -> jni::sys::jstring {
    let Some(filter) = string(&mut env, &filter) else {
        return std::ptr::null_mut();
    };
    let result = engine::with_session_and_token(|session, token| {
        engine::run(catalog::library(session, token, &filter))
    });
    catalog_result(&mut env, result)
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeCatalogCollection(
    mut env: JNIEnv,
    _this: JObject,
    kind: JString,
    id: JString,
) -> jni::sys::jstring {
    let (Some(kind), Some(id)) = (string(&mut env, &kind), string(&mut env, &id)) else {
        return std::ptr::null_mut();
    };
    let result = engine::with_session_and_token(|session, token| {
        engine::run(catalog::collection(session, token, &kind, &id))
    });
    catalog_result(&mut env, result)
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeCatalogArtist(
    mut env: JNIEnv,
    _this: JObject,
    id: JString,
) -> jni::sys::jstring {
    let Some(id) = string(&mut env, &id) else {
        return std::ptr::null_mut();
    };
    let result = engine::with_session_and_token(|session, token| {
        engine::run(catalog::artist(session, token, &id))
    });
    catalog_result(&mut env, result)
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeCatalogAlbumsBy(
    mut env: JNIEnv,
    _this: JObject,
    id: JString,
) -> jni::sys::jstring {
    let Some(id) = string(&mut env, &id) else {
        return std::ptr::null_mut();
    };
    let result = engine::with_session_and_token(|session, token| {
        engine::run(catalog::albums_by(session, token, &id))
    });
    catalog_result(&mut env, result)
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeCatalogTopTracks(
    mut env: JNIEnv,
    _this: JObject,
    id: JString,
) -> jni::sys::jstring {
    let Some(id) = string(&mut env, &id) else {
        return std::ptr::null_mut();
    };
    let result = engine::with_session_and_token(|session, token| {
        engine::run(catalog::top_tracks(session, token, &id))
    });
    catalog_result(&mut env, result)
}

#[no_mangle]
pub extern "system" fn Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_nativeCatalogSearch(
    mut env: JNIEnv,
    _this: JObject,
    query: JString,
    scopes: JString,
    limit: jlong,
) -> jni::sys::jstring {
    let (Some(query), Some(scopes)) = (string(&mut env, &query), string(&mut env, &scopes)) else {
        return std::ptr::null_mut();
    };
    let scopes: Vec<String> = serde_json::from_str(&scopes).unwrap_or_default();
    let result = engine::with_session_and_token(|session, token| {
        engine::run(catalog::search(
            session,
            token,
            &query,
            limit as i32,
            &scopes,
        ))
    });
    catalog_result(&mut env, result)
}

fn bool_result(result: Result<(), String>) -> jboolean {
    if result.is_ok() {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}
