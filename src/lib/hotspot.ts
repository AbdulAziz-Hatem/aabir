import { feedback } from "./feedback";
import { SpeechManager, showToast } from "./speech";
import type { Settings } from "./store";

export interface LoginResult {
  success: boolean;
  message: string;
}

// مجس التحقق الحقيقي من تدفق الإنترنت (Canary Probe)
async function verifyInternetAccess(): Promise<boolean> {
  const probes = [
    "http://connectivitycheck.gstatic.com/generate_204",
    "http://cp.cloudflare.com/",
    "http://neverssl.com/online-check",
  ];

  for (const probe of probes) {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 2200);

      const res = await fetch(probe, {
        method: "GET",
        cache: "no-store",
        signal: controller.signal,
      });
      clearTimeout(timeoutId);

      // في ميكروتك: إذا كان النت محظوراً أو الكرت خطأ، يقوم الراوتر بتحويل الطلب 302
      // أو يرجع صفحة HTML بكود 200.
      // إذا نجح الدخول وتدفق النت، يرجع مجس غوغل كود 204 No Content الصافي
      if (res.status === 204) {
        return true;
      }
    } catch {
      continue;
    }
  }
  return false;
}

export async function submitHotspotLogin(pin: string, settings: Settings): Promise<LoginResult> {
  const cleanPin = pin.replace(/\D/g, "");
  if (!cleanPin || cleanPin.length < 4) {
    feedback.playErrorTone();
    feedback.vibrateError();
    const errMsg = "رمز الكرت غير صالح أو قصير جداً";
    showToast(errMsg, "error");
    SpeechManager.speak(errMsg, settings.voiceAnnounce, settings.selectedVoiceURI);
    return { success: false, message: errMsg };
  }

  const startMsg = `جارٍ فحص وإرسال الكود: ${cleanPin}`;
  showToast(startMsg, "info");
  SpeechManager.speak(`تم رصد الكود ${cleanPin}، جارٍ تسجيل الدخول في الشبكة...`, settings.voiceAnnounce, settings.selectedVoiceURI);

  const targets = [
    settings.gatewayIp.trim() || "10.0.0.1",
    "192.168.88.1",
    "172.16.0.1",
  ];
  const uniqueTargets = Array.from(new Set(targets));

  // أنماط إرسال ميكروتك:
  // 1. النمط اليمني الشائع: حقل username فقط (كلمة المرور فارغة)
  // 2. نمط التطابق: username = password
  const payloads = [
    { username: cleanPin, password: "" },
    { username: cleanPin, password: cleanPin },
  ];

  for (const host of uniqueTargets) {
    for (const payload of payloads) {
      try {
        const url = `http://${host}/login`;
        const params = new URLSearchParams();
        params.append("username", payload.username);
        if (payload.password) {
          params.append("password", payload.password);
        }
        params.append("dst", "http://connectivitycheck.gstatic.com/generate_204");

        await fetch(url, {
          method: "POST",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded",
          },
          body: params.toString(),
          mode: "no-cors",
        });

        // مهلة 500ms لتحديث جدار حماية ميكروتك والـ IP Binding
        await new Promise((resolve) => setTimeout(resolve, 500));

        // الفحص الصارم: هل فُتح الإنترنت الحقيقي؟
        const isOnline = await verifyInternetAccess();

        if (isOnline) {
          feedback.playSuccessTone();
          feedback.vibrateSuccess();
          const successMsg = `تم تفعيل الإنترنت بنجاح! كود الكرت: ${cleanPin}`;
          showToast(successMsg, "success");
          SpeechManager.speak(successMsg, settings.voiceAnnounce, settings.selectedVoiceURI);
          return { success: true, message: successMsg };
        }
      } catch {
        continue;
      }
    }
  }

  // إذا فشل مجس الإنترنت بعد كل المحاولات
  feedback.playErrorTone();
  feedback.vibrateError();
  const failMsg = "فشل تفعيل الإنترنت؛ قد يكون الكرت غير صحيح أو تم استخدامه مسبقاً";
  showToast(failMsg, "error");
  SpeechManager.speak(failMsg, settings.voiceAnnounce, settings.selectedVoiceURI);
  return { success: false, message: failMsg };
}
