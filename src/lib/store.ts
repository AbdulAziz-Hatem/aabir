import { useEffect, useState } from "react";

export type Accent = "blue" | "silver" | "black";

export type Settings = {
  gatewayIp: string;
  autoLoginOnScan: boolean;
  voiceAnnounce: boolean;
  selectedVoiceURI: string;
  radarSound: boolean;
  minLen: number;
  maxLen: number;
  groupBy: number;
  theme: "dark" | "light";
  accent: Accent;
};

export type HistoryItem = { pin: string; code: string; at: number };

export const DEFAULTS: Settings = {
  gatewayIp: "10.0.0.1",
  autoLoginOnScan: true,
  voiceAnnounce: true,
  selectedVoiceURI: "",
  radarSound: true,
  minLen: 4, // مرونة كاملة تبدأ من 4 خانات
  maxLen: 16, // وتصل حتى 16 خانة
  groupBy: 4,
  theme: "dark",
  accent: "blue",
};

function useLocal<T>(key: string, init: T) {
  const [v, setV] = useState<T>(init);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    try {
      const raw = localStorage.getItem(key);
      if (raw) setV({ ...(init as object), ...JSON.parse(raw) } as T);
      if (raw && Array.isArray(init)) setV(JSON.parse(raw));
    } catch {
      setV(init);
    }
    setReady(true);
  }, [key]);

  useEffect(() => {
    if (ready) localStorage.setItem(key, JSON.stringify(v));
  }, [key, v, ready]);

  return [v, setV] as const;
}

export const useSettings = () => useLocal<Settings>("karty.settings", DEFAULTS);
export const useHistory = () => useLocal<HistoryItem[]>("karty.history", []);

export const buildCode = (s: Settings, pin: string) => `http://${s.gatewayIp}/login?u=${pin}`;

export type RecognizedLine = { text: string; height: number };

function pinCandidates(text: string, s: Settings) {
  const cleaned = text
    .replace(/[Oo]/g, "0")
    .replace(/[Il|]/g, "1")
    .replace(/[Ss]/g, "5")
    .replace(/B/g, "8");

  // استخراج كافة المتواليات الرقمية
  const runs = cleaned.match(/[\d][\d\s-]{2,}[\d]/g) ?? [];

  return runs
    .map((r) => r.replace(/\D/g, ""))
    .filter((digits) => {
      // 1. التحقق من الحدود العامة (المرنة)
      if (digits.length < s.minLen || digits.length > s.maxLen) return false;

      // 2. استبعاد أرقام الهواتف النقالة في اليمن (70, 71, 73, 77, 78) وأرقام الثابت (01 إلى 07)
      if (/^(77|78|73|71|70|01|02|03|04|05|06|07)\d{6,8}$/.test(digits)) return false;

      // 3. استبعاد أرقام خدمة العملاء وبوابات الاستعلام الشائعة
      if (/^(101|102|107|501|601|701|800)\d*$/.test(digits)) return false;

      // 4. استبعاد أرقام السيريال الطويلة جداً التي ترافق الباركود
      if (digits.length > 15) return false;

      return true;
    });
}

export function extractPin(text: string, s: Settings): string | null {
  return pinCandidates(text, s).sort((a, b) => b.length - a.length)[0] ?? null;
}

export function extractPinFromLines(lines: RecognizedLine[], s: Settings): string | null {
  const candidates = lines.flatMap((line) =>
    pinCandidates(line.text, s).map((digits) => ({ digits, height: line.height })),
  );

  // إعطاء الأولوية للخط الأكبر حجماً (خانة الخدش)، ثم للطول المناسب
  candidates.sort((a, b) => b.height - a.height || b.digits.length - a.digits.length);
  return candidates[0]?.digits ?? null;
}

export const groupPin = (pin: string, n: number) =>
  n > 0 ? pin.replace(new RegExp(`(\\d{${n}})(?=\\d)`, "g"), "$1 ") : pin;
