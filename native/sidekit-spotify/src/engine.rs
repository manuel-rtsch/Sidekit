//! One process-wide librespot session and player, guarded independently of the JNI callers.

use crate::audio::{PcmSink, CHANNELS, SAMPLE_RATE};
use librespot_core::authentication::Credentials;
use librespot_core::session::Session;
use librespot_core::SpotifyUri;
use librespot_playback::config::{Bitrate, PlayerConfig};
use librespot_playback::mixer::VolumeGetter;
use librespot_playback::player::{Player, PlayerEventChannel};
use std::sync::atomic::{AtomicU16, Ordering};
use std::sync::mpsc::{sync_channel, Receiver, SyncSender};
use std::sync::{Arc, Mutex, OnceLock};
use std::time::Duration;

struct Volume(AtomicU16);

struct SharedVolume(Arc<Volume>);

impl VolumeGetter for SharedVolume {
    fn attenuation_factor(&self) -> f64 {
        let volume = self.0 .0.load(Ordering::Relaxed).min(65_535) as f64 / 65_535.0;
        volume * volume
    }
}

struct State {
    session: Option<Session>,
    player: Option<Arc<Player>>,
    events: Option<PlayerEventChannel>,
    volume: Arc<Volume>,
}

impl Default for State {
    fn default() -> Self {
        Self {
            session: None,
            player: None,
            events: None,
            volume: Arc::new(Volume(AtomicU16::new(65_535))),
        }
    }
}

static RUNTIME: OnceLock<tokio::runtime::Runtime> = OnceLock::new();
static STATE: OnceLock<Mutex<State>> = OnceLock::new();
static AUDIO_RX: OnceLock<Mutex<Receiver<Vec<u8>>>> = OnceLock::new();

fn runtime() -> &'static tokio::runtime::Runtime {
    RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .worker_threads(2)
            .build()
            .expect("tokio runtime")
    })
}

fn state() -> &'static Mutex<State> {
    STATE.get_or_init(|| Mutex::new(State::default()))
}

fn audio_rx() -> &'static Mutex<Receiver<Vec<u8>>> {
    AUDIO_RX.get_or_init(|| {
        let (tx, rx) = sync_channel::<Vec<u8>>(64);
        AUDIO_TX.set(tx).ok();
        Mutex::new(rx)
    })
}

static AUDIO_TX: OnceLock<SyncSender<Vec<u8>>> = OnceLock::new();

pub fn init() -> Result<(), String> {
    // Force the audio channel (and its sender) to exist before the player starts.
    let _ = audio_rx();
    Ok(())
}

pub fn connect(access_token: &str) -> Result<(), String> {
    disconnect();
    let rt = runtime();
    let _guard = rt.enter();
    let session = Session::new(Default::default(), None);
    rt.block_on(session.connect(Credentials::with_access_token(access_token), false))
        .map_err(|e| format!("SESSION_CONNECT_FAILED: {e}"))?;

    let mut guard = state().lock().map_err(|_| "STATE_LOCK".to_string())?;
    guard.session = Some(session);
    Ok(())
}

pub fn disconnect() {
    if let Ok(mut guard) = state().lock() {
        if let Some(player) = guard.player.take() {
            player.stop();
        }
        guard.events = None;
        if let Some(session) = guard.session.take() {
            session.shutdown();
        }
    }
}

pub fn create_player() -> Result<(), String> {
    let mut guard = state().lock().map_err(|_| "STATE_LOCK".to_string())?;
    if guard.player.is_some() {
        return Ok(());
    }
    let session = guard
        .session
        .clone()
        .ok_or_else(|| "SESSION_NOT_CONNECTED".to_string())?;

    let _ = audio_rx();
    let tx = AUDIO_TX
        .get()
        .ok_or_else(|| "AUDIO_UNINITIALISED".to_string())?
        .clone();
    let volume = guard.volume.clone();
    let config = PlayerConfig {
        bitrate: Bitrate::Bitrate320,
        position_update_interval: Some(Duration::from_millis(500)),
        ..PlayerConfig::default()
    };
    let player = Player::new(config, session, Box::new(SharedVolume(volume)), move || {
        Box::new(PcmSink::new(tx))
    });
    guard.events = Some(player.get_player_event_channel());
    guard.player = Some(player);
    Ok(())
}

pub fn load(uri: &str, position_ms: u32, autoplay: bool) -> Result<(), String> {
    let uri = SpotifyUri::from_uri(uri).map_err(|e| format!("INVALID_URI: {e}"))?;
    if !uri.is_playable() {
        return Err("NOT_PLAYABLE".to_string());
    }
    let player = {
        let guard = state().lock().map_err(|_| "STATE_LOCK".to_string())?;
        guard
            .player
            .clone()
            .ok_or_else(|| "PLAYER_NOT_CREATED".to_string())?
    };
    player.load(uri, autoplay, position_ms);
    Ok(())
}

pub fn play() -> Result<(), String> {
    player()?.play();
    Ok(())
}

pub fn pause() -> Result<(), String> {
    player()?.pause();
    Ok(())
}

pub fn stop() -> Result<(), String> {
    player()?.stop();
    Ok(())
}

pub fn seek(position_ms: u32) -> Result<(), String> {
    player()?.seek(position_ms);
    Ok(())
}

pub fn set_volume(volume: f32) {
    let level = (volume.clamp(0.0, 1.0) * 65_535.0) as u16;
    if let Ok(guard) = state().lock() {
        guard.volume.0.store(level, Ordering::Relaxed);
    }
}

pub fn read_audio(timeout: Duration) -> Option<Vec<u8>> {
    audio_rx().lock().ok()?.recv_timeout(timeout).ok()
}

pub fn audio_format() -> String {
    format!("{{\"format\":\"s16le\",\"sampleRate\":{SAMPLE_RATE},\"channels\":{CHANNELS}}}")
}

pub fn poll_event() -> Option<String> {
    use librespot_playback::player::PlayerEvent;
    use serde_json::json;

    let mut guard = state().lock().ok()?;
    let events = guard.events.as_mut()?;
    let event = events.try_recv().ok()?;
    let value = match event {
        PlayerEvent::Stopped { track_id, .. } => json!({"type": "stopped", "uri": uri(&track_id)}),
        PlayerEvent::Loading {
            track_id,
            position_ms,
            ..
        } => {
            json!({"type": "loading", "uri": uri(&track_id), "positionMs": position_ms})
        }
        PlayerEvent::Playing {
            track_id,
            position_ms,
            ..
        } => {
            json!({"type": "playing", "uri": uri(&track_id), "positionMs": position_ms})
        }
        PlayerEvent::Paused {
            track_id,
            position_ms,
            ..
        } => {
            json!({"type": "paused", "uri": uri(&track_id), "positionMs": position_ms})
        }
        PlayerEvent::EndOfTrack { track_id, .. } => {
            json!({"type": "ended", "uri": uri(&track_id)})
        }
        PlayerEvent::Unavailable { track_id, .. } => {
            json!({"type": "unavailable", "uri": uri(&track_id)})
        }
        PlayerEvent::PositionChanged { position_ms, .. }
        | PlayerEvent::PositionCorrection { position_ms, .. }
        | PlayerEvent::Seeked { position_ms, .. } => {
            json!({"type": "position", "positionMs": position_ms})
        }
        PlayerEvent::VolumeChanged { volume } => json!({"type": "volume", "volume": volume}),
        PlayerEvent::ShuffleChanged { shuffle } => json!({"type": "shuffle", "shuffle": shuffle}),
        PlayerEvent::RepeatChanged { context, track } => {
            json!({"type": "repeat", "context": context, "track": track})
        }
        PlayerEvent::AutoPlayChanged { auto_play } => {
            json!({"type": "autoplay", "autoplay": auto_play})
        }
        _ => json!({"type": "other"}),
    };
    Some(value.to_string())
}

fn uri(value: &SpotifyUri) -> String {
    format!(
        "spotify:{}:{}",
        value.item_type(),
        value.to_id().unwrap_or_default()
    )
}

fn player() -> Result<Arc<Player>, String> {
    let guard = state().lock().map_err(|_| "STATE_LOCK".to_string())?;
    guard
        .player
        .clone()
        .ok_or_else(|| "PLAYER_NOT_CREATED".to_string())
}

#[cfg(test)]
mod tests {
    use super::audio_format;

    #[test]
    fn audio_format_is_interleaved_s16_stereo() {
        let format = audio_format();
        assert!(format.contains("\"format\":\"s16le\""));
        assert!(format.contains("\"sampleRate\":44100"));
        assert!(format.contains("\"channels\":2"));
    }
}
