// محرك التغذية الصوتية واللمسية المخصصة لإمكانية الوصول
class FeedbackEngine {
  private ctx: AudioContext | null = null;

  private initCtx() {
    if (!this.ctx && typeof window !== "undefined") {
      const AudioCtx =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (AudioCtx) {
        this.ctx = new AudioCtx();
      }
    }
    if (this.ctx && this.ctx.state === "suspended") {
      void this.ctx.resume();
    }
  }

  // نغمة كورد النجاح (C5 -> E5 -> G5)
  playSuccessTone() {
    try {
      this.initCtx();
      if (!this.ctx) return;
      const now = this.ctx.currentTime;
      const freqs = [523.25, 659.25, 783.99]; // C5, E5, G5
      freqs.forEach((freq, idx) => {
        const osc = this.ctx!.createOscillator();
        const gain = this.ctx!.createGain();
        osc.type = "sine";
        osc.frequency.value = freq;
        gain.gain.setValueAtTime(0.25, now + idx * 0.08);
        gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.08 + 0.28);
        osc.connect(gain);
        gain.connect(this.ctx!.destination);
        osc.start(now + idx * 0.08);
        osc.stop(now + idx * 0.08 + 0.28);
      });
    } catch {}
  }

  // نغمة الخطأ (تردد هابط حاسم من 280Hz إلى 110Hz)
  playErrorTone() {
    try {
      this.initCtx();
      if (!this.ctx) return;
      const now = this.ctx.currentTime;
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = "sawtooth";
      osc.frequency.setValueAtTime(280, now);
      osc.frequency.exponentialRampToValueAtTime(110, now + 0.35);
      gain.gain.setValueAtTime(0.25, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.35);
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      osc.start(now);
      osc.stop(now + 0.35);
    } catch {}
  }

  // نبضة رادار الكاميرا الدورية (تكتكة ناعمة تُشعر الكفيف ببحث الكاميرا)
  playRadarTick() {
    try {
      this.initCtx();
      if (!this.ctx) return;
      const now = this.ctx.currentTime;
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = "sine";
      osc.frequency.value = 880;
      gain.gain.setValueAtTime(0.06, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.035);
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      osc.start(now);
      osc.stop(now + 0.035);
    } catch {}
  }

  // اهتزاز النجاح: نبضتان ناعمتان متتاليتان
  vibrateSuccess() {
    if (typeof navigator !== "undefined" && navigator.vibrate) {
      navigator.vibrate([90, 60, 160]);
    }
  }

  // اهتزاز الفشل: ثلاث نبضات قوية متقطعة
  vibrateError() {
    if (typeof navigator !== "undefined" && navigator.vibrate) {
      navigator.vibrate([240, 80, 240, 80, 240]);
    }
  }

  // اهتزاز التقاط الكود المبدئي: نبضة خفيفة وسريعة
  vibrateFound() {
    if (typeof navigator !== "undefined" && navigator.vibrate) {
      navigator.vibrate(70);
    }
  }
}

export const feedback = new FeedbackEngine();
