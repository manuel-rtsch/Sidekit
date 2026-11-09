//! PCM sink. Decoded S16LE stereo is queued for the Kotlin AudioTrack drainer.
//!
//! The bounded channel is deliberate: when Kotlin stops draining, the librespot decode thread
//! blocks on `send` instead of racing ahead and allocating without limit.

use librespot_playback::audio_backend::{Open, Sink, SinkResult};
use librespot_playback::config::AudioFormat;
use librespot_playback::convert::Converter;
use librespot_playback::decoder::AudioPacket;
use std::sync::mpsc::SyncSender;

pub const CHANNELS: u16 = 2;
pub const SAMPLE_RATE: u32 = 44_100;

pub struct PcmSink {
    tx: SyncSender<Vec<u8>>,
}

impl PcmSink {
    pub fn new(tx: SyncSender<Vec<u8>>) -> Self {
        Self { tx }
    }
}

impl Open for PcmSink {
    fn open(_device: Option<String>, _format: AudioFormat) -> Self {
        unreachable!("PcmSink is constructed with its channel")
    }
}

impl Sink for PcmSink {
    fn write(&mut self, packet: AudioPacket, converter: &mut Converter) -> SinkResult<()> {
        let pcm = match packet {
            AudioPacket::Samples(samples) => {
                let s16 = converter.f64_to_s16(&samples);
                let mut bytes = Vec::with_capacity(s16.len() * 2);
                for sample in s16 {
                    bytes.extend_from_slice(&sample.to_le_bytes());
                }
                bytes
            }
            AudioPacket::Raw(bytes) => bytes,
        };
        // A disconnected receiver means playback is shutting down; dropping is correct.
        let _ = self.tx.send(pcm);
        Ok(())
    }
}
