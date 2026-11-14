// مدير الإشعارات الصوتية وقارئات الشاشة
export type ToastCallback = (message: string, type: "success" | "error" | "info") => void;

let toastListener: ToastCallback | null = null;

export function registerToastListener(callback: ToastCallback) {
  toastListener = callback;
  return () => {
    toastListener = null;
  };
}

export function showToast(message: string, type: "success" | "error" | "info" = "info") {
  if (toastListener) {
    toastListener(message, type);
  }
  // تحديث عنصر الإعلان الصوتي في شجرة DOM لقارئ الشاشة
  if (typeof document !== "undefined") {
    const liveRegion = document.getElementById("a11y-announcer");
    if (liveRegion) {
      liveRegion.textContent = message;
    }
  }
}

export class SpeechManager {
  static getVoices(): SpeechSynthesisVoice[] {
    if (typeof window === "undefined" || !window.speechSynthesis) return [];
    return window.speechSynthesis.getVoices();
  }

  static speak(text: string, enabled: boolean, voiceURI?: string) {
    if (!enabled || typeof window === "undefined" || !window.speechSynthesis) return;

    try {
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.rate = 1.0;
      utterance.pitch = 1.0;

      const voices = this.getVoices();
      if (voiceURI) {
        const found = voices.find((v) => v.voiceURI === voiceURI);
        if (found) utterance.voice = found;
      } else {
        // البحث التلقائي عن صوت عربي إن وُجد
        const arVoice = voices.find((v) => v.lang.startsWith("ar"));
        if (arVoice) {
          utterance.voice = arVoice;
        }
      }

      window.speechSynthesis.speak(utterance);
    } catch {}
  }
}
