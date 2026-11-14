import { createFileRoute } from "@tanstack/react-router";
import { lazy, Suspense, useCallback, useEffect, useState } from "react";
import {
  Camera,
  Copy,
  Wifi,
  Settings as Cog,
  History,
  Moon,
  Sun,
  Check,
  Trash2,
  RotateCcw,
  Volume2,
  VolumeX,
  Radio,
  Globe,
  RefreshCw,
  Play,
} from "lucide-react";
import { Splash } from "@/components/brand/Splash";
import { Logo, BRAND } from "@/components/brand/Logo";
import {
  DEFAULTS,
  buildCode,
  groupPin,
  useHistory,
  useSettings,
  type Accent,
} from "@/lib/store";
import { feedback } from "@/lib/feedback";
import { SpeechManager, registerToastListener, showToast } from "@/lib/speech";
import { submitHotspotLogin } from "@/lib/hotspot";

const Scanner = lazy(() =>
  import("@/components/Scanner").then((m) => ({ default: m.Scanner })),
);

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "كروتي هوت سبوت — ماسح كروت الواي فاي والدخول التلقائي" },
      {
        name: "description",
        content:
          "تطبيق مفتوح المصدر لمسح كروت شبكات ميكروتك وتسجيل الدخول التلقائي بدون إنترنت وبدعم كامل لقارئات الشاشة.",
      },
    ],
  }),
  component: App,
});

type Tab = "home" | "history" | "settings";

function App() {
  const [splash, setSplash] = useState(true);
  const [s, setS] = useSettings();
  const [hist, setHist] = useHistory();
  const [tab, setTab] = useState<Tab>("home");
  const [scanning, setScanning] = useState(false);
  const [pin, setPin] = useState("");
  const [loadingLogin, setLoadingLogin] = useState(false);
  const [copied, setCopied] = useState(false);
  const [toastMsg, setToastMsg] = useState<{ text: string; type: "success" | "error" | "info" } | null>(null);
  const [availableVoices, setAvailableVoices] = useState<SpeechSynthesisVoice[]>([]);

  useEffect(() => {
    const r = document.documentElement;
    r.classList.toggle("dark", s.theme === "dark");
    r.setAttribute("data-accent", s.accent);
    r.setAttribute("dir", "rtl");
    r.setAttribute("lang", "ar");
  }, [s.theme, s.accent]);

  // تسجيل مستمع رسائل الـ Toast
  useEffect(() => {
    return registerToastListener((message, type) => {
      setToastMsg({ text: message, type });
      const timer = setTimeout(() => setToastMsg(null), 4000);
      return () => clearTimeout(timer);
    });
  }, []);

  // جلب أصوات الـ TTS المتاحة
  useEffect(() => {
    const updateVoices = () => {
      const v = SpeechManager.getVoices();
      if (v.length > 0) setAvailableVoices(v);
    };
    updateVoices();
    if (typeof window !== "undefined" && window.speechSynthesis) {
      window.speechSynthesis.onvoiceschanged = updateVoices;
    }
  }, []);

  const endSplash = useCallback(() => setSplash(false), []);
  const digits = pin.replace(/\D/g, "");
  const valid = digits.length >= s.minLen && digits.length <= s.maxLen;

  const recordHistory = (p: string) => {
    const code = buildCode(s, p);
    setHist((h) => [{ pin: p, code, at: Date.now() }, ...h.filter((x) => x.pin !== p)].slice(0, 50));
  };

  const executeLogin = async (p: string) => {
    setLoadingLogin(true);
    recordHistory(p);
    try {
      await submitHotspotLogin(p, s);
    } finally {
      setLoadingLogin(false);
    }
  };

  // عند التقاط الرمز من الكاميرا
  const handlePinFound = (detectedPin: string) => {
    setPin(detectedPin);
    setScanning(false);
    setTab("home");

    if (s.autoLoginOnScan) {
      void executeLogin(detectedPin);
    } else {
      feedback.playSuccessTone();
      feedback.vibrateFound();
      showToast(`تم التقاط الكود: ${detectedPin}`, "info");
      SpeechManager.speak(`تم التقاط الكود ${detectedPin}، اضغط زر الدخول للمتابعة`, s.voiceAnnounce, s.selectedVoiceURI);
    }
  };

  const copyPin = async () => {
    try {
      await navigator.clipboard.writeText(digits);
      setCopied(true);
      recordHistory(digits);
      showToast(`تم نسخ الكود ${digits}`, "info");
      setTimeout(() => setCopied(false), 1500);
    } catch {}
  };

  return (
    <div className="min-h-screen bg-background text-foreground app-bg select-none" dir="rtl">
      {/* منطقة الإعلان الصوتي الدائمة لقارئات الشاشة */}
      <div id="a11y-announcer" className="sr-only" aria-live="assertive" aria-atomic="true"></div>

      {splash && <Splash onDone={endSplash} />}

      {/* نافذة التوست المنبثقة المرئية والصوتية */}
      {toastMsg && (
        <div
          role="alert"
          aria-live="assertive"
          className={`fixed top-4 inset-x-4 z-50 mx-auto max-w-sm rounded-2xl p-4 shadow-xl border backdrop-blur-md transition-all animate-[fadeDown_.3s_both] ${
            toastMsg.type === "success"
              ? "bg-emerald-950/90 border-emerald-500/50 text-emerald-100"
              : toastMsg.type === "error"
                ? "bg-rose-950/90 border-rose-500/50 text-rose-100"
                : "bg-slate-900/90 border-slate-700 text-slate-100"
          }`}
        >
          <div className="flex items-center gap-3">
            {toastMsg.type === "success" && <Check className="text-emerald-400 shrink-0" size={20} />}
            {toastMsg.type === "error" && <Wifi className="text-rose-400 shrink-0" size={20} />}
            {toastMsg.type === "info" && <Radio className="text-sky-400 shrink-0" size={20} />}
            <p className="text-sm font-medium leading-relaxed">{toastMsg.text}</p>
          </div>
        </div>
      )}

      {scanning && (
        <Suspense fallback={null}>
          <Scanner
            settings={s}
            onClose={() => setScanning(false)}
            onPin={handlePinFound}
          />
        </Suspense>
      )}

      <div className="mx-auto flex min-h-screen max-w-md flex-col px-5 pb-28 pt-[max(1.25rem,env(safe-area-inset-top))]">
        {/* الترويسة العلوية */}
        <header className="flex items-center justify-between py-3 border-b border-border/40">
          <div className="flex items-center gap-3">
            <Logo size={42} />
            <div>
              <h1 className="font-display text-lg font-bold leading-none">{BRAND.name}</h1>
              <p className="text-xs text-muted-foreground mt-0.5">{BRAND.credit}</p>
            </div>
          </div>
          <button
            className="glass-btn h-11 w-11 flex items-center justify-center"
            aria-label={s.theme === "dark" ? "التبديل إلى المظهر الفاتح" : "التبديل إلى المظهر الداكن"}
            onClick={() => setS({ ...s, theme: s.theme === "dark" ? "light" : "dark" })}
          >
            {s.theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
          </button>
        </header>

        {/* لسان الصفحة الرئيسية */}
        {tab === "home" && (
          <main className="mt-4 flex flex-col gap-5 animate-[fadeUp_.4s_both]">
            <button
              onClick={() => setScanning(true)}
              className="glass-card group flex flex-col items-center gap-3 p-8 text-center transition active:scale-[.98] border-2 border-primary/20 hover:border-primary/50"
              aria-label="فتح الكاميرا لمسح كرت الواي فاي الآن"
            >
              <span className="flex h-20 w-20 items-center justify-center rounded-3xl bg-primary text-primary-foreground shadow-glow transition group-hover:scale-105">
                <Camera size={36} />
              </span>
              <span className="font-display text-xl font-bold">امسح كرت الواي فاي</span>
              <span className="text-xs text-muted-foreground">
                تعرف فوري محلي بدون إنترنت · معالجة ذكية للخدش
              </span>
            </button>

            <section className="glass-card p-5">
              <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground block mb-1">
                رمز كرت الواي فاي (PIN)
              </label>
              <input
                inputMode="numeric"
                dir="ltr"
                value={groupPin(digits, s.groupBy)}
                onChange={(e) => setPin(e.target.value)}
                placeholder="امسح بالكاميرا أو اكتب الكود"
                aria-label="حقل إدخال رمز الكرت المكون من أرقام"
                className="mt-1 w-full text-center rounded-xl border bg-input/40 px-4 py-3 font-mono text-2xl tracking-widest outline-none focus:ring-2 focus:ring-primary"
              />
              <p
                className={`mt-2 text-xs text-center font-medium ${
                  digits && !valid ? "text-destructive" : "text-muted-foreground"
                }`}
              >
                {digits.length > 0
                  ? `${digits.length} أرقام · الطول المتوقع (${s.minLen} إلى ${s.maxLen})`
                  : `يتطلب من ${s.minLen} إلى ${s.maxLen} أرقام`}
              </p>

              <div className="mt-5 flex flex-col gap-3">
                <button
                  disabled={!valid || loadingLogin}
                  onClick={() => executeLogin(digits)}
                  aria-label="تسجيل الدخول المباشر في شبكة الواي فاي ميكروتك"
                  className="btn-primary flex items-center justify-center gap-2 py-4 text-base font-bold disabled:opacity-40"
                >
                  {loadingLogin ? <RefreshCw className="animate-spin" size={20} /> : <Wifi size={20} />}
                  تسجيل الدخول في الشبكة
                </button>

                <button
                  disabled={!digits}
                  onClick={copyPin}
                  aria-label="نسخ رمز الكرت إلى الحافظة"
                  className="glass-btn py-3 flex items-center justify-center gap-2 font-medium"
                >
                  {copied ? <Check size={18} className="text-emerald-400" /> : <Copy size={18} />}
                  {copied ? "تم النسخ بنجاح" : "نسخ الكود"}
                </button>
              </div>
            </section>
          </main>
        )}

        {/* لسان السجل */}
        {tab === "history" && (
          <main className="mt-4 flex flex-col gap-3 animate-[fadeUp_.4s_both]">
            <div className="flex items-center justify-between">
              <h2 className="font-display text-xl font-bold">سجل الكروت المشحونة</h2>
              {hist.length > 0 && (
                <button
                  onClick={() => setHist([])}
                  aria-label="مسح كامل سجل الكروت"
                  className="text-sm text-destructive flex items-center gap-1 p-2 font-medium"
                >
                  <Trash2 size={16} /> مسح الكل
                </button>
              )}
            </div>
            {hist.length === 0 && (
              <p className="glass-card p-8 text-center text-sm text-muted-foreground font-medium">
                لا توجد كروت مشحونة في السجل حتى الآن.
              </p>
            )}
            {hist.map((h) => (
              <div
                key={h.at}
                className="glass-card flex items-center justify-between p-4"
              >
                <div>
                  <p className="font-mono text-lg font-bold tracking-wider" dir="ltr">
                    {h.pin}
                  </p>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    {new Date(h.at).toLocaleDateString("ar-YE", {
                      weekday: "short",
                      day: "numeric",
                      month: "short",
                      hour: "numeric",
                      minute: "numeric",
                    })}
                  </p>
                </div>
                <button
                  onClick={() => {
                    setPin(h.pin);
                    setTab("home");
                    void executeLogin(h.pin);
                  }}
                  aria-label={`إعادة تسجيل الدخول بالكود ${h.pin}`}
                  className="btn-primary h-10 w-10 !p-0 flex items-center justify-center rounded-xl"
                >
                  <RefreshCw size={16} />
                </button>
              </div>
            ))}
          </main>
        )}

        {/* لسان الإعدادات المتقدمة وإمكانية الوصول */}
        {tab === "settings" && (
          <main className="mt-4 flex flex-col gap-5 animate-[fadeUp_.4s_both]">
            <h2 className="font-display text-xl font-bold">الإعدادات وإمكانية الوصول</h2>

            {/* قسم الصوت وقارئ الشاشة */}
            <section className="glass-card p-5 flex flex-col gap-4">
              <h3 className="text-sm font-bold text-primary flex items-center gap-2">
                <Volume2 size={18} /> التنبيهات الصوتية وقارئ الشاشة
              </h3>

              {/* مفتاح التبديل الرئيسي */}
              <div className="flex items-center justify-between py-1">
                <div>
                  <p className="font-semibold text-sm">الإشعارات الصوتية الناطقة (TTS)</p>
                  <p className="text-xs text-muted-foreground">نطق حالة الكاميرا والكرت ونتيجة تسجيل الدخول</p>
                </div>
                <button
                  role="switch"
                  aria-checked={s.voiceAnnounce}
                  onClick={() => setS({ ...s, voiceAnnounce: !s.voiceAnnounce })}
                  className={`w-14 h-8 flex items-center rounded-full p-1 transition-colors ${
                    s.voiceAnnounce ? "bg-primary justify-end" : "bg-muted justify-start"
                  }`}
                  aria-label="تفعيل أو تعطيل التنبيهات الصوتية الناطقة"
                >
                  <span className="bg-white w-6 h-6 rounded-full shadow-md block"></span>
                </button>
              </div>

              {/* قائمة اختيار الصوت المفضل */}
              {s.voiceAnnounce && availableVoices.length > 0 && (
                <div className="flex flex-col gap-1.5 pt-2 border-t border-border/40">
                  <label htmlFor="voice-select" className="text-xs font-semibold text-muted-foreground">
                    محرك وصوت القراءة المفضل
                  </label>
                  <select
                    id="voice-select"
                    value={s.selectedVoiceURI}
                    onChange={(e) => setS({ ...s, selectedVoiceURI: e.target.value })}
                    className="rounded-xl border bg-input/40 px-3 py-2.5 text-sm font-medium outline-none focus:ring-2 focus:ring-primary text-foreground"
                    aria-label="اختر صوت تحويل النص إلى كلام المفضل لديك"
                  >
                    <option value="">تلقائي (الصوت العربي الافتراضي للمنظومة)</option>
                    {availableVoices.map((v) => (
                      <option key={v.voiceURI} value={v.voiceURI}>
                        {v.name} ({v.lang})
                      </option>
                    ))}
                  </select>

                  <button
                    onClick={() =>
                      SpeechManager.speak(
                        "أهلاً بك، تم تفعيل محرك الصوت لتطبيق كروتي هوت سبوت بنجاح.",
                        true,
                        s.selectedVoiceURI
                      )
                    }
                    className="glass-btn mt-2 py-2.5 text-xs flex items-center justify-center gap-2 font-medium"
                    aria-label="تجربة نطق الصوت المختار"
                  >
                    <Play size={14} /> تجربة الصوت المختار
                  </button>
                </div>
              )}

              {/* رادار الكاميرا الصوتي */}
              <div className="flex items-center justify-between pt-2 border-t border-border/40">
                <div>
                  <p className="font-semibold text-sm">صوت رادار الكاميرا</p>
                  <p className="text-xs text-muted-foreground">تكتكة ناعمة كل ثانية تشعر الكفيف ببحث الكاميرا</p>
                </div>
                <button
                  role="switch"
                  aria-checked={s.radarSound}
                  onClick={() => setS({ ...s, radarSound: !s.radarSound })}
                  className={`w-14 h-8 flex items-center rounded-full p-1 transition-colors ${
                    s.radarSound ? "bg-primary justify-end" : "bg-muted justify-start"
                  }`}
                  aria-label="تفعيل أو تعطيل صوت رادار الكاميرا التكتكي"
                >
                  <span className="bg-white w-6 h-6 rounded-full shadow-md block"></span>
                </button>
              </div>

              {/* أزرار فحص النغمات والاهتزاز */}
              <div className="grid grid-cols-2 gap-2 pt-2 border-t border-border/40">
                <button
                  onClick={() => {
                    feedback.playSuccessTone();
                    feedback.vibrateSuccess();
                    showToast("تجربة: نغمة واهتزاز النجاح", "success");
                  }}
                  className="glass-btn py-2.5 text-xs font-semibold flex items-center justify-center gap-1.5"
                  aria-label="تجربة نغمة واهتزاز النجاح"
                >
                  <Check size={14} className="text-emerald-400" /> فحص النجاح
                </button>
                <button
                  onClick={() => {
                    feedback.playErrorTone();
                    feedback.vibrateError();
                    showToast("تجربة: نغمة واهتزاز الفشل", "error");
                  }}
                  className="glass-btn py-2.5 text-xs font-semibold flex items-center justify-center gap-1.5"
                  aria-label="تجربة نغمة واهتزاز الفشل"
                >
                  <Wifi size={14} className="text-rose-400" /> فحص الفشل
                </button>
              </div>
            </section>

            {/* قسم إعدادات راوتر ميكروتك */}
            <section className="glass-card p-5 flex flex-col gap-4">
              <h3 className="text-sm font-bold text-primary flex items-center gap-2">
                <Globe size={18} /> إعدادات شبكة ميكروتك (Hotspot)
              </h3>

              <div className="flex items-center justify-between">
                <div>
                  <p className="font-semibold text-sm">تسجيل الدخول التلقائي فور المسح</p>
                  <p className="text-xs text-muted-foreground">إرسال الكود للراوتر بمجرد التقاطه دون نقر</p>
                </div>
                <button
                  role="switch"
                  aria-checked={s.autoLoginOnScan}
                  onClick={() => setS({ ...s, autoLoginOnScan: !s.autoLoginOnScan })}
                  className={`w-14 h-8 flex items-center rounded-full p-1 transition-colors ${
                    s.autoLoginOnScan ? "bg-primary justify-end" : "bg-muted justify-start"
                  }`}
                  aria-label="تفعيل أو تعطيل التسجيل التلقائي المباشر فور المسح"
                >
                  <span className="bg-white w-6 h-6 rounded-full shadow-md block"></span>
                </button>
              </div>

              <div className="flex flex-col gap-1.5 pt-2 border-t border-border/40">
                <label className="text-xs font-semibold text-muted-foreground">
                  عنوان بوابة الراوتر (Gateway IP)
                </label>
                <input
                  type="text"
                  dir="ltr"
                  value={s.gatewayIp}
                  onChange={(e) => setS({ ...s, gatewayIp: e.target.value })}
                  className="rounded-xl border bg-input/40 px-3 py-2.5 font-mono text-sm outline-none focus:ring-2 focus:ring-primary text-center"
                  placeholder="10.0.0.1"
                  aria-label="أدخل عنوان الآي بي الخاص ببوابة شبكة ميكروتك"
                />
                <div className="flex gap-2 mt-1">
                  {["10.0.0.1", "192.168.88.1", "172.16.0.1"].map((ip) => (
                    <button
                      key={ip}
                      onClick={() => setS({ ...s, gatewayIp: ip })}
                      className="glass-btn flex-1 py-1.5 text-xs font-mono"
                    >
                      {ip}
                    </button>
                  ))}
                </div>
              </div>
            </section>

            {/* قسم معايير كرت الواي فاي */}
            <section className="glass-card p-5 grid grid-cols-2 gap-3">
              <label className="flex flex-col gap-1 text-xs text-muted-foreground">
                الحد الأدنى لطول الكود
                <input
                  type="number"
                  dir="ltr"
                  value={String(s.minLen)}
                  onChange={(e) => setS({ ...s, minLen: +e.target.value || 1 })}
                  className="rounded-lg border bg-input/40 px-3 py-2 font-mono text-base text-foreground outline-none focus:ring-2 focus:ring-primary text-center"
                />
              </label>

              <label className="flex flex-col gap-1 text-xs text-muted-foreground">
                الحد الأقصى لطول الكود
                <input
                  type="number"
                  dir="ltr"
                  value={String(s.maxLen)}
                  onChange={(e) => setS({ ...s, maxLen: +e.target.value || 1 })}
                  className="rounded-lg border bg-input/40 px-3 py-2 font-mono text-base text-foreground outline-none focus:ring-2 focus:ring-primary text-center"
                />
              </label>
            </section>

            <button
              onClick={() => setS({ ...DEFAULTS, theme: s.theme })}
              className="glass-btn py-3 font-semibold flex items-center justify-center gap-2"
              aria-label="استعادة كافة الإعدادات الافتراضية"
            >
              <RotateCcw size={16} /> استعادة الإعدادات الافتراضية
            </button>
          </main>
        )}
      </div>

      {/* شريط التنقل السفلي */}
      <nav className="fixed inset-x-0 bottom-0 z-30 mx-auto max-w-md p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
        <div className="glass-card flex justify-around p-2" role="tablist">
          {(
            [
              ["home", Camera, "المسح"],
              ["history", History, "السجل"],
              ["settings", Cog, "الإعدادات"],
            ] as const
          ).map(([k, IconComp, label]) => (
            <button
              key={k}
              role="tab"
              aria-selected={tab === k}
              onClick={() => setTab(k)}
              className={`flex flex-1 flex-col items-center gap-1 rounded-xl py-2 text-xs font-bold transition ${
                tab === k
                  ? "bg-primary text-primary-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <IconComp size={20} />
              {label}
            </button>
          ))}
        </div>
      </nav>
    </div>
  );
}
