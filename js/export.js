// Video export — records the canvas + audio into a WebM file using
// MediaRecorder. Plays the track from the start while recording so the
// exported video is the full synced show.
"use strict";

class Exporter {
  constructor(canvas, audioEngine) {
    this.canvas = canvas;
    this.audio = audioEngine;
    this.recorder = null;
    this.chunks = [];
    this.recording = false;
  }

  start(onStop) {
    if (this.recording) return;
    const canvasStream = this.canvas.captureStream(60);
    const stream = new MediaStream([
      ...canvasStream.getVideoTracks(),
      ...this.audio.streamDest.stream.getAudioTracks(),
    ]);

    const mime = ["video/webm;codecs=vp9,opus", "video/webm;codecs=vp8,opus", "video/webm"]
      .find(m => MediaRecorder.isTypeSupported(m)) || "";

    this.chunks = [];
    this.recorder = new MediaRecorder(stream, {
      mimeType: mime,
      videoBitsPerSecond: 12_000_000,
    });
    this.recorder.ondataavailable = e => { if (e.data.size) this.chunks.push(e.data); };
    this.recorder.onstop = () => {
      this.recording = false;
      const blob = new Blob(this.chunks, { type: "video/webm" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "poopdog-rave-visuals.webm";
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 10_000);
      if (onStop) onStop();
    };
    this.recorder.start(250);
    this.recording = true;
  }

  stop() {
    if (this.recorder && this.recording) this.recorder.stop();
  }
}
