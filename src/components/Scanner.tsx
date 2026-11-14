import { useCallback, useEffect, useRef, useState } from "react";
import { Flashlight, FlashlightOff, X, Loader2, ScanLine } from "lucide-react";
import { initializeOcr, recognizeImage } from "@/lib/ocr";
import { extractPin, extractPinFromLines, type Settings } from "@/lib/store";
import { feedback } from "@/lib/feedback";
import { SpeechManager } from "@/lib/speech";

type State = "starting" | "ready" | "scanning" | "found" | "error";

export function Scanner({
  settings,
  onClose,
  onPin,
}: {
  settings: Settings;
  onClose: () => void;
  onPin: (p: string) => void;
}) {
  const video = useRef<HTMLVideoElement>(null);
  const stream = useRef<MediaStream | null>(null);
  const busy = useRef(false);
  const [state, setState] = useState<State>("starting");
  const [ocrStatus, setOcrStatus] = useState<"loading" | "ready" | "error">("loading");
  const [msg, setMsg] = useState("جارٍ تشغيل الكاميرا...");
  const [torch, setTorch] = useState(false);
  const [torchOk, setTorchOk] = useState(false);

  const initOcr = useCallback(async () => {
    setOcrStatus("loading");
    setMsg("جارٍ تجهيز محرك القراءة محلياً...");
    try {
      await initializeOcr((status, progress) => {
        const percent = Math.round(progress * 100);
        setMsg(`تجهيز المحرك: ${percent}%`);
      });
      setOcrStatus("ready");
      setMsg("وجّه الكاميرا نحو خانة الكرت المخدوشة");
      SpeechManager.speak("الكاميرا جاهزة، وجّه الهاتف نحو الكرت", settings.voiceAnnounce, settings.selectedVoiceURI);
    } catch {
      setOcrStatus("error");
      setMsg("محرك القراءة غير متوفر - اضغط لإعادة المحاولة");
      feedback.playErrorTone();
      feedback.vibrateError();
    }
  }, [settings.voiceAnnounce, settings.selectedVoiceURI]);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const s = await navigator.mediaDevices.getUserMedia({
          video: {
            facingMode: { ideal: "environment" },
            width: { ideal: 1920 },
            height: { ideal: 1080 },
          },
          audio: false,
        });
        if (!alive) return s.getTracks().forEach((t) => t.stop());
        stream.current = s;
        if (video.current) {
          video.current.srcObject = s;
          await video.current.play();
        }
        const track = s.getVideoTracks()[0];
        const caps = (track?.getCapabilities?.() ?? {}) as { torch?: boolean };
        const hasTorch = !!caps.torch;
        setTorchOk(hasTorch);

        // تشغيل الفلاش تلقائياً للمساعدة في إيضاح أرقام الخدش
        if (hasTorch) {
          try {
            await track.applyConstraints({ advanced: [{ torch: true } as MediaTrackConstraintSet] });
            setTorch(true);
          } catch {}
        }

        setState("ready");
        void initOcr();
      } catch (e) {
        if (alive) {
          setState("error");
          setMsg(
            e instanceof Error && e.name === "NotAllowedError"
              ? "تم رفض إذن الكاميرا"
              : "الكاميرا غير متوفرة",
          );
          feedback.playErrorTone();
          feedback.vibrateError();
        }
      }
    })();
    return () => {
      alive = false;
      stream.current?.getTracks().forEach((t) => t.stop());
    };
  }, [initOcr]);

  const toggleTorch = async () => {
    const t = stream.current?.getVideoTracks()[0];
    if (!t) return;
    try {
      await t.applyConstraints({ advanced: [{ torch: !torch } as MediaTrackConstraintSet] });
      setTorch(!torch);
    } catch {
      setTorchOk(false);
    }
  };

  const scan = useCallback(async () => {
    const v = video.current;
    if (!v || busy.current || !v.videoWidth || ocrStatus !== "ready") return;
    busy.current = true;
    setState("scanning");
    setMsg("جارٍ فحص الرمز...");

    if (settings.radarSound) {
      feedback.playRadarTick();
    }

    try {
      const cw = v.videoWidth * 0.88,
        ch = v.videoHeight * 0.30;
      const c = document.createElement("canvas");
      c.width = cw;
      c.height = ch;
      const ctx = c.getContext("2d")!;
      ctx.filter = "grayscale(1) contrast(1.7)";
      ctx.drawImage(v, (v.videoWidth - cw) / 2, (v.videoHeight - ch) / 2, cw, ch, 0, 0, cw, ch);
      const recognized = await recognizeImage(c);
      const pin = recognized.lines?.length
        ? extractPinFromLines(recognized.lines, settings)
        : extractPin(recognized.text, settings);

      if (pin) {
        setState("found");
        setMsg(`تم رصد الكود: ${pin}`);
        feedback.vibrateFound();
        setTimeout(() => onPin(pin), 400);
      } else {
        setState("ready");
        setMsg("لم يتم رصد رمز صالح - حافظ على ثبات الهاتف");
      }
    } catch (error) {
      setState("error");
      const detail = error instanceof Error ? error.message.slice(0, 48) : "فشل التعرف";
      setMsg(`خطأ في القراءة: ${detail}`);
    }
    busy.current = false;
  }, [settings, onPin, ocrStatus]);

  // حلقة المسح التلقائي المستمر كل 1.3 ثانية
  useEffect(() => {
    if (state !== "ready" || ocrStatus !== "ready") return;
    const id = setTimeout(scan, 1300);
    return () => clearTimeout(id);
  }, [state, ocrStatus, scan]);

  const frameColor =
    state === "found"
      ? "border-success"
      : state === "error"
        ? "border-destructive"
        : "border-primary";

  return (
    <div className="fixed inset-0 z-40 bg-scrim" role="dialog" aria-modal="true" aria-label="شاشة مسح كروت الواي فاي">
      <video
        ref={video}
        playsInline
        muted
        className="absolute inset-0 h-full w-full object-cover"
        aria-hidden="true"
      />
      <div className="absolute inset-0 flex items-center justify-center pointer-events-none" aria-hidden="true">
        <div
          className={`relative aspect-[3.2/1] w-[88%] max-w-md rounded-2xl border-2 ${frameColor} shadow-[0_0_0_9999px_var(--scrim)] transition-colors`}
        >
          {(state === "ready" || state === "scanning") && (
            <div className="scanline absolute inset-x-2 h-0.5 rounded bg-primary" />
          )}
        </div>
      </div>

      {/* الشريط العلوي */}
      <div className="absolute inset-x-0 top-0 flex items-center justify-between p-4 pt-[max(1rem,env(safe-area-inset-top))]">
        <button onClick={onClose} className="glass-btn h-12 w-12" aria-label="إغلاق الكاميرا والعودة للرئيسية">
          <X size={22} />
        </button>
        <span className="glass-btn px-4 py-2 text-sm font-medium" role="status" aria-live="polite">
          {msg}
        </span>
        <button
          onClick={toggleTorch}
          disabled={!torchOk}
          aria-label={torch ? "إطفاء فلاش الكاميرا" : "تشغيل فلاش الكاميرا"}
          className={`glass-btn h-12 w-12 ${torch ? "!bg-primary !text-primary-foreground" : ""} disabled:opacity-40`}
        >
          {torch ? <Flashlight size={22} /> : <FlashlightOff size={22} />}
        </button>
      </div>

      {/* الشريط السفلي */}
      <div className="absolute inset-x-0 bottom-0 flex flex-col items-center gap-3 p-6 pb-[max(1.5rem,env(safe-area-inset-bottom))]">
        <button
          onClick={ocrStatus === "error" ? initOcr : scan}
          disabled={
            state === "scanning" ||
            state === "starting" ||
            state === "found" ||
            ocrStatus === "loading"
          }
          className="btn-primary flex items-center gap-2 px-8 py-4 text-base font-semibold disabled:opacity-60"
          aria-label="التقاط فوري للكود الآن"
        >
          {state === "scanning" || state === "starting" || ocrStatus === "loading" ? (
            <Loader2 className="animate-spin" size={20} />
          ) : (
            <ScanLine size={20} />
          )}
          {state === "scanning"
            ? "جارٍ فحص الكود..."
            : state === "starting"
              ? "تشغيل الكاميرا..."
              : ocrStatus === "loading"
                ? "تجهيز المحرك..."
                : ocrStatus === "error"
                  ? "إعادة محاولة المحرك"
                  : state === "error"
                    ? "إعادة المحاولة"
                    : "التقاط فوري الآن"}
        </button>
        <button onClick={onClose} className="text-sm font-medium text-overlay-foreground/80 underline p-2" aria-label="الرجوع وكتابة الكود يدوياً">
          إدخال الكود يدوياً
        </button>
      </div>
    </div>
  );
}
