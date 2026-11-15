/**
 * TOADY 로고: 두꺼비 심볼(이미지) + TOADY 글자(화면 글꼴).
 * 심볼은 여백 없이 잘라 둔 파일(public/brand/toady-mark.png, 108×96)이라 간격은 gap으로만 정한다.
 * size: 심볼 높이(px). 글자 크기·간격은 심볼 높이에 맞춰 함께 커진다.
 */
export default function BrandLogo({ size = 30 }) {
  return <span style={{ display: 'inline-flex', alignItems: 'center', gap: Math.round(size * 0.34), lineHeight: 1 }}>
    <img src="/brand/toady-mark.png" alt="" width={Math.round(size * 1.125)} height={size} style={{ display: 'block', height: size, width: 'auto' }} />
    <span style={{ fontWeight: 900, fontSize: Math.round(size * 0.74), letterSpacing: '-0.02em', color: '#1e293b' }}>TOADY</span>
  </span>;
}
