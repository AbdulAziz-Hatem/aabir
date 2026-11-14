import { feedback } from "./feedback";
import { SpeechManager, showToast } from "./speech";
import type { Settings } from "./store";

export interface LoginResult {
  success: boolean;
  message: string;
}

export async function submitHotspotLogin(pin: string, settings: Settings): Promise<LoginResult> {
  const cleanPin = pin.replace(/\D/g, "");
  if (!cleanPin || cleanPin.length < settings.minLen) {
    feedback.playErrorTone();
    feedback.vibrateError();
    const errMsg = "رمز الكرت غير صالح أو غير مكتمل";
    showToast(errMsg, "error");
    SpeechManager.speak(errMsg, settings.voiceAnnounce, settings.selectedVoiceURI);
    return { success: false, message: errMsg };
  }

  const startMsg = `جارٍ تسجيل الدخول بالكود: ${cleanPin}`;
  showToast(startMsg, "info");
  SpeechManager.speak(startMsg, settings.voiceAnnounce, settings.selectedVoiceURI);

  const targets = [
    settings.gatewayIp.trim() || "10.0.0.1",
    "192.168.88.1",
    "172.16.0.1",
  ];

  // إزالة التكرار
  const uniqueTargets = Array.from(new Set(targets));

  let lastError = "";

  for (const host of uniqueTargets) {
    try {
      const url = `http://${host}/login`;
      const params = new URLSearchParams();
      params.append("username", cleanPin);
      params.append("password", cleanPin);
      params.append("dst", "http://connectivitycheck.gstatic.com/generate_204");

      const response = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
        },
        body: params.toString(),
        mode: "no-cors", // للعبور السلس دون اعتراضات CORS في بوابات الهوت سبوت
      });

      // في حال إرسال الطلب بنجاح
      feedback.playSuccessTone();
      feedback.vibrateSuccess();
      const successMsg = `تم تسجيل الدخول في الشبكة بنجاح! الكود: ${cleanPin}`;
      showToast(successMsg, "success");
      SpeechManager.speak(successMsg, settings.voiceAnnounce, settings.selectedVoiceURI);
      return { success: true, message: successMsg };
    } catch (e) {
      lastError = e instanceof Error ? e.message : "خطأ اتصال";
      continue;
    }
  }

  feedback.playErrorTone();
  feedback.vibrateError();
  const failMsg = `تعذر الاتصال ببوابة الراوتر (${lastError})، تأكد من اتصالك بالواي فاي`;
  showToast(failMsg, "error");
  SpeechManager.speak(failMsg, settings.voiceAnnounce, settings.selectedVoiceURI);
  return { success: false, message: failMsg };
}
