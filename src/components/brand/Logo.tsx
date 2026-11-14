// شعار وهوية تطبيق كروتي هوت سبوت
export function Logo({ size = 48 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-label="شعار كروتي هوت سبوت">
      <defs>
        <linearGradient id="lg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="var(--primary)" />
          <stop offset="1" stopColor="var(--foreground)" stopOpacity=".35" />
        </linearGradient>
      </defs>
      <rect x="4" y="4" width="56" height="56" rx="16" fill="url(#lg)" />
      <rect x="4.5" y="4.5" width="55" height="55" rx="15.5" fill="none" stroke="var(--primary-foreground)" strokeOpacity=".35" />
      <path d="M16 24v-6h6M42 18h6v6M48 40v6h-6M22 46h-6v-6" stroke="var(--primary-foreground)" strokeWidth="3" fill="none" strokeLinecap="round" />
      {/* أيقونة موجات الواي فاي بدلاً من رمز الشحن القديم */}
      <path d="M22 36c3-3 7-5 10-5s7 2 10 5M26 41c2-2 4-3 6-3s4 1 6 3M32 46h.01" stroke="var(--primary-foreground)" strokeWidth="3" fill="none" strokeLinecap="round" />
    </svg>
  );
}

export const BRAND = { name: "كروتي هوت سبوت", credit: "تطوير عبد العزيز حاتم · مفتوح المصدر" };
