import { useId } from 'react';

// The Stotrams lotus: five gold petals with a sun-ray halo. Petals are separated by a
// stroke in the colour of whatever sits behind the mark — pass that as `gap` so the
// separation reads on both the cream and the brown backgrounds.
//
// Static copies of the same drawing live in public/favicon.svg and public/logo.svg —
// keep them in sync if the shape changes.

const CENTER = { x: 100, y: 100 };
const RAYS = 16;

/** Mirrors an x coordinate across the vertical centre line. */
const mx = (x: number) => 200 - x;

const OUTER_LEFT = 'M97 154C62 160 36 144 24 112C50 104 84 120 97 154Z';
const INNER_LEFT = 'M97 152C64 142 50 110 55 74C80 84 95 110 97 152Z';
const OUTER_RIGHT = `M${mx(97)} 154C${mx(62)} 160 ${mx(36)} 144 ${mx(24)} 112C${mx(50)} 104 ${mx(84)} 120 ${mx(97)} 154Z`;
const INNER_RIGHT = `M${mx(97)} 152C${mx(64)} 142 ${mx(50)} 110 ${mx(55)} 74C${mx(80)} 84 ${mx(95)} 110 ${mx(97)} 152Z`;
const CENTER_PETAL = 'M100 42C128 70 131 114 100 154C69 114 72 70 100 42Z';

function rays() {
  return Array.from({ length: RAYS }, (_, i) => {
    const angle = (i / RAYS) * Math.PI * 2 - Math.PI / 2;
    const long = i % 2 === 0;
    const [r1, r2] = long ? [80, 94] : [83, 90];
    const cos = Math.cos(angle);
    const sin = Math.sin(angle);
    return {
      x1: +(CENTER.x + r1 * cos).toFixed(1),
      y1: +(CENTER.y + r1 * sin).toFixed(1),
      x2: +(CENTER.x + r2 * cos).toFixed(1),
      y2: +(CENTER.y + r2 * sin).toFixed(1),
    };
  });
}

interface Props {
  size?: number;
  /** Background colour behind the mark; used for the gaps between petals. */
  gap?: string;
  className?: string;
}

export function LotusMark({ size = 48, gap = '#FBF6EC', className }: Props) {
  const gradientId = useId();
  return (
    <svg className={className} width={size} height={size} viewBox="0 0 200 200" aria-hidden="true">
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#F6CD66" />
          <stop offset="1" stopColor="#DC9F38" />
        </linearGradient>
      </defs>
      <g stroke="#E3AE48" strokeWidth="3.5" strokeLinecap="round">
        {rays().map((r, i) => (
          <line key={i} {...r} />
        ))}
      </g>
      <g fill={`url(#${gradientId})`} stroke={gap} strokeWidth="4" strokeLinejoin="round">
        <path d={OUTER_LEFT} />
        <path d={OUTER_RIGHT} />
        <path d={INNER_LEFT} />
        <path d={INNER_RIGHT} />
        <path d={CENTER_PETAL} />
      </g>
    </svg>
  );
}
