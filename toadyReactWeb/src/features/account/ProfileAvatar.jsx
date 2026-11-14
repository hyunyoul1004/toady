import { useId } from 'react';
/**
 * 프로필 캐릭터. 이미지를 올리지 않고 TOADY가 준비한 캐릭터 중에서 고른다(서버는 키만 저장: TB_USER_PROFILE).
 * 모두 직접 그린 SVG라 외부 파일·저작권 걱정 없이 어떤 크기로도 선명하게 보인다.
 * 캐릭터를 고르지 않았거나 모르는 키면 닉네임 첫 글자 동그라미로 보여 준다.
 */
export const PROFILE_ICONS = [
  { key: 'toad', label: '두껍이', bg: '#dcfce7' },
  { key: 'house', label: '집콩이', bg: '#e0f2fe' },
  { key: 'cat', label: '고양이', bg: '#fef3c7' },
  { key: 'dog', label: '강아지', bg: '#ffedd5' },
  { key: 'rabbit', label: '토끼', bg: '#fce7f3' },
  { key: 'bear', label: '곰', bg: '#f5e8da' },
  { key: 'panda', label: '판다', bg: '#e5e7eb' },
  { key: 'penguin', label: '펭귄', bg: '#dbeafe' },
  { key: 'chick', label: '병아리', bg: '#fef9c3' },
  { key: 'fox', label: '여우', bg: '#ffe4d6' },
  { key: 'hamster', label: '햄스터', bg: '#fdf2e9' },
  { key: 'pig', label: '돼지', bg: '#ffe4e6' }
];
const BY_KEY = Object.fromEntries(PROFILE_ICONS.map(item => [item.key, item]));
export const isProfileIcon = key => Boolean(BY_KEY[key]);

const INK = '#2b2b2b';
function Eyes({ y = 36, gap = 7, r = 2.6 }) {
  return <>{[32 - gap, 32 + gap].map(x => <g key={x}><circle cx={x} cy={y} r={r} fill={INK} /><circle cx={x + 0.9} cy={y - 0.9} r={r * 0.36} fill="#fff" /></g>)}</>;
}
function Blush({ y = 41, gap = 12, color = '#fda4af' }) {
  return <>{[32 - gap, 32 + gap].map(x => <ellipse key={x} cx={x} cy={y} rx="3" ry="1.8" fill={color} opacity=".7" />)}</>;
}
const Smile = ({ y = 41, w = 3.2 }) => <path d={`M${32 - w} ${y} Q32 ${y + 3} ${32 + w} ${y}`} stroke={INK} strokeWidth="1.6" fill="none" strokeLinecap="round" />;

/** 캐릭터별 그림(64×64). 배경 원은 공통으로 그린다. */
const ART = {
  toad: <>
    <ellipse cx="32" cy="40" rx="20" ry="15" fill="#4ade80" />
    <circle cx="22" cy="27" r="7.5" fill="#4ade80" /><circle cx="42" cy="27" r="7.5" fill="#4ade80" />
    <circle cx="22" cy="27" r="4.6" fill="#fff" /><circle cx="42" cy="27" r="4.6" fill="#fff" />
    <circle cx="22.6" cy="27.6" r="2.6" fill={INK} /><circle cx="42.6" cy="27.6" r="2.6" fill={INK} />
    <circle cx="23.4" cy="26.6" r=".9" fill="#fff" /><circle cx="43.4" cy="26.6" r=".9" fill="#fff" />
    <circle cx="27" cy="45" r="1.4" fill="#22c55e" /><circle cx="38" cy="47" r="1.1" fill="#22c55e" /><circle cx="44" cy="42" r="1.2" fill="#22c55e" />
    <path d="M24 40 Q32 46 40 40" stroke={INK} strokeWidth="1.8" fill="none" strokeLinecap="round" />
    <Blush y={41} gap={13} />
  </>,
  house: <>
    <path d="M14 31 L32 15 L50 31 Z" fill="#f87171" />
    <rect x="18" y="29" width="28" height="22" rx="4" fill="#fff7ed" />
    <rect x="40" y="17" width="4.5" height="9" rx="1" fill="#ef4444" />
    <Eyes y={37} gap={6} r={2.3} /><Blush y={41} gap={10} /><Smile y={41} w={2.6} />
    <rect x="29" y="44" width="6" height="7" rx="1.5" fill="#fb923c" />
  </>,
  cat: <>
    <path d="M15 30 L18 13 L28 22 Z" fill="#fbbf24" /><path d="M49 30 L46 13 L36 22 Z" fill="#fbbf24" />
    <path d="M18.5 25 L19.5 17 L25 22 Z" fill="#fde68a" /><path d="M45.5 25 L44.5 17 L39 22 Z" fill="#fde68a" />
    <circle cx="32" cy="36" r="18" fill="#fbbf24" />
    <path d="M24 26 L27 29 M32 24 L32 28 M40 26 L37 29" stroke="#f59e0b" strokeWidth="1.6" strokeLinecap="round" />
    <Eyes y={36} /><Blush y={41} />
    <path d="M30.5 39.5 L33.5 39.5 L32 41 Z" fill="#f472b6" />
    <path d="M32 41 Q29.5 44 27.5 42 M32 41 Q34.5 44 36.5 42" stroke={INK} strokeWidth="1.3" fill="none" strokeLinecap="round" />
    <path d="M14 38 L22 39 M14 42 L22 41 M50 38 L42 39 M50 42 L42 41" stroke="#b45309" strokeWidth="1" strokeLinecap="round" opacity=".6" />
  </>,
  dog: <>
    <ellipse cx="16" cy="33" rx="6" ry="11" fill="#b45309" transform="rotate(18 16 33)" />
    <ellipse cx="48" cy="33" rx="6" ry="11" fill="#b45309" transform="rotate(-18 48 33)" />
    <circle cx="32" cy="35" r="17" fill="#fdba74" />
    <ellipse cx="32" cy="42" rx="9" ry="7" fill="#fff7ed" />
    <Eyes y={33} gap={7} />
    <ellipse cx="32" cy="39" rx="3" ry="2.2" fill={INK} />
    <path d="M32 41 L32 43.5 M28.5 44 Q32 46.5 35.5 44" stroke={INK} strokeWidth="1.4" fill="none" strokeLinecap="round" />
    <Blush y={40} gap={12} />
  </>,
  rabbit: <>
    <ellipse cx="24" cy="17" rx="5" ry="13" fill="#fff" stroke="#fbcfe8" strokeWidth="1.5" />
    <ellipse cx="40" cy="17" rx="5" ry="13" fill="#fff" stroke="#fbcfe8" strokeWidth="1.5" />
    <ellipse cx="24" cy="18" rx="2.2" ry="9" fill="#f9a8d4" /><ellipse cx="40" cy="18" rx="2.2" ry="9" fill="#f9a8d4" />
    <circle cx="32" cy="38" r="16" fill="#fff" stroke="#fbcfe8" strokeWidth="1.5" />
    <Eyes y={37} gap={6.5} r={2.4} /><Blush y={42} gap={11} />
    <path d="M30.6 41 L33.4 41 L32 42.4 Z" fill="#f472b6" />
    <path d="M32 42.4 Q30 45 28.5 43.5 M32 42.4 Q34 45 35.5 43.5" stroke={INK} strokeWidth="1.2" fill="none" strokeLinecap="round" />
  </>,
  bear: <>
    <circle cx="18" cy="21" r="7" fill="#a16207" /><circle cx="46" cy="21" r="7" fill="#a16207" />
    <circle cx="18" cy="21" r="3.5" fill="#ca8a04" /><circle cx="46" cy="21" r="3.5" fill="#ca8a04" />
    <circle cx="32" cy="36" r="18" fill="#a16207" />
    <ellipse cx="32" cy="42" rx="8.5" ry="6.5" fill="#fde68a" />
    <Eyes y={34} gap={7.5} />
    <ellipse cx="32" cy="39.5" rx="3" ry="2" fill={INK} />
    <path d="M29 43 Q32 45.5 35 43" stroke={INK} strokeWidth="1.4" fill="none" strokeLinecap="round" />
    <Blush y={40} gap={13} color="#fb7185" />
  </>,
  panda: <>
    <circle cx="18" cy="21" r="7" fill={INK} /><circle cx="46" cy="21" r="7" fill={INK} />
    <circle cx="32" cy="36" r="18" fill="#fff" stroke="#d1d5db" strokeWidth="1.2" />
    <ellipse cx="24.5" cy="35" rx="5" ry="6" fill={INK} transform="rotate(-20 24.5 35)" />
    <ellipse cx="39.5" cy="35" rx="5" ry="6" fill={INK} transform="rotate(20 39.5 35)" />
    <circle cx="25" cy="35" r="2" fill="#fff" /><circle cx="39" cy="35" r="2" fill="#fff" />
    <ellipse cx="32" cy="41" rx="2.6" ry="1.8" fill={INK} />
    <path d="M29 44 Q32 46.5 35 44" stroke={INK} strokeWidth="1.4" fill="none" strokeLinecap="round" />
    <Blush y={43} gap={13} />
  </>,
  penguin: <>
    <circle cx="32" cy="35" r="19" fill="#1e3a5f" />
    <path d="M32 24 C22 24 18 32 18 39 C18 47 25 52 32 52 C39 52 46 47 46 39 C46 32 42 24 32 24 Z" fill="#fff" />
    <Eyes y={36} gap={6.5} r={2.4} /><Blush y={41} gap={10.5} />
    <path d="M28.5 40 L35.5 40 L32 44 Z" fill="#f59e0b" />
  </>,
  chick: <>
    <path d="M30 17 Q28 11 32 13 Q34 9 35 15" stroke="#eab308" strokeWidth="2.4" fill="none" strokeLinecap="round" />
    <circle cx="32" cy="36" r="18" fill="#fde047" />
    <Eyes y={34} gap={7} /><Blush y={40} gap={12} />
    <path d="M28 39 L36 39 L32 43.5 Z" fill="#fb923c" />
    <path d="M14 40 Q10 36 13 33 M50 40 Q54 36 51 33" stroke="#eab308" strokeWidth="2.2" fill="none" strokeLinecap="round" />
  </>,
  fox: <>
    <path d="M14 32 L16 12 L28 22 Z" fill="#f97316" /><path d="M50 32 L48 12 L36 22 Z" fill="#f97316" />
    <path d="M17.5 25 L18.5 17 L24.5 22 Z" fill="#fff7ed" /><path d="M46.5 25 L45.5 17 L39.5 22 Z" fill="#fff7ed" />
    <circle cx="32" cy="35" r="17" fill="#f97316" />
    <path d="M16 38 Q24 54 32 47 Q40 54 48 38 Q40 44 32 44 Q24 44 16 38 Z" fill="#fff7ed" />
    <Eyes y={33} gap={7} />
    <ellipse cx="32" cy="42.5" rx="2.6" ry="1.8" fill={INK} />
    <Blush y={39} gap={12} />
  </>,
  hamster: <>
    <circle cx="19" cy="22" r="5" fill="#f4a261" /><circle cx="45" cy="22" r="5" fill="#f4a261" />
    <circle cx="19" cy="22" r="2.4" fill="#fbcfe8" /><circle cx="45" cy="22" r="2.4" fill="#fbcfe8" />
    <ellipse cx="32" cy="37" rx="19" ry="17" fill="#f4a261" />
    <ellipse cx="32" cy="42" rx="13" ry="10" fill="#fff7ed" />
    <circle cx="20" cy="42" r="5" fill="#fff7ed" /><circle cx="44" cy="42" r="5" fill="#fff7ed" />
    <Eyes y={34} gap={7} r={2.4} />
    <path d="M30.8 39 L33.2 39 L32 40.4 Z" fill="#f472b6" />
    <path d="M32 40.4 Q30.3 42.6 29 41.5 M32 40.4 Q33.7 42.6 35 41.5" stroke={INK} strokeWidth="1.2" fill="none" strokeLinecap="round" />
    <Blush y={43} gap={13} />
  </>,
  pig: <>
    <path d="M15 27 L17 15 L27 21 Z" fill="#f9a8d4" /><path d="M49 27 L47 15 L37 21 Z" fill="#f9a8d4" />
    <circle cx="32" cy="36" r="18" fill="#fbcfe8" />
    <Eyes y={32} gap={7.5} />
    <ellipse cx="32" cy="40" rx="7" ry="5" fill="#f9a8d4" />
    <ellipse cx="29.5" cy="40" rx="1.3" ry="1.8" fill="#be185d" /><ellipse cx="34.5" cy="40" rx="1.3" ry="1.8" fill="#be185d" />
    <path d="M29 46.5 Q32 48.5 35 46.5" stroke={INK} strokeWidth="1.3" fill="none" strokeLinecap="round" />
    <Blush y={42} gap={14} color="#f472b6" />
  </>
};

const INITIAL_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4'];
const initialColor = name => INITIAL_COLORS[[...String(name || '')].reduce((sum, ch) => sum + ch.charCodeAt(0), 0) % INITIAL_COLORS.length];

/**
 * 프로필 동그라미. icon이 준비된 캐릭터면 그림, 아니면 닉네임 첫 글자.
 * decorative가 아니면 스크린리더에 '○○ 프로필'로 읽힌다.
 */
export default function ProfileAvatar({ icon, name = '', size = 40, decorative = false, className = '' }) {
  const clipId = `pa-${useId().replace(/:/g, '')}`;
  const item = BY_KEY[icon];
  const label = decorative ? undefined : `${name || '회원'} 프로필`;
  if (!item) {
    const color = initialColor(name);
    return <span className={`profile-avatar is-initial ${className}`} role={decorative ? undefined : 'img'} aria-label={label} aria-hidden={decorative || undefined}
      style={{ width: size, height: size, fontSize: Math.round(size * 0.42), color, background: `${color}1f`, border: `1px solid ${color}33` }}>
      {[...String(name || '?')][0]}
    </span>;
  }
  // 캐릭터를 원 안으로 자른다. CSS clip-path는 그림 영역 기준이라 귀처럼 튀어나온 부분이 잘리므로 SVG clipPath(좌표 고정)를 쓴다.
  return <svg className={`profile-avatar ${className}`} width={size} height={size} viewBox="0 0 64 64" role={decorative ? undefined : 'img'} aria-label={label} aria-hidden={decorative || undefined}>
    <defs><clipPath id={clipId}><circle cx="32" cy="32" r="32" /></clipPath></defs>
    <circle cx="32" cy="32" r="32" fill={item.bg} />
    <g clipPath={`url(#${clipId})`}>{ART[item.key]}</g>
  </svg>;
}
