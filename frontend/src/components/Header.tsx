import { useEffect, useState } from 'react';
import { isDemoMode, onDemoModeChange } from '../api/client';
import { todayLine, useLang } from '../i18n';
import { LotusMark } from './LotusMark';

/** Header colour — passed to the mark so its petal gaps match. Keep in sync with --brand. */
const HEADER_BG = '#3B1A0E';

export function Header() {
  const { lang, t, toggleLang } = useLang();

  return (
    <header className="site-header">
      <div className="site-header__row">
        <div className="brand">
          <LotusMark size={56} gap={HEADER_BG} className="brand__mark" />
          <div className="brand__text">
            <h1 className={`brand__name brand__name--${lang}`}>{t.appName}</h1>
            <p className={`brand__tagline brand__tagline--${lang}`}>{t.tagline}</p>
          </div>
        </div>
        <button type="button" className="lang-toggle" onClick={toggleLang} lang={lang === 'hi' ? 'en' : 'hi'}>
          {t.switchLang}
        </button>
      </div>
      <p className="today-line">
        <LotusMark size={22} gap={HEADER_BG} /> {todayLine(lang)}
      </p>
      <DemoBanner />
    </header>
  );
}

function DemoBanner() {
  const { t } = useLang();
  const [demo, setDemo] = useState(isDemoMode);
  useEffect(() => onDemoModeChange(setDemo), []);
  if (!demo) return null;
  return <p className="demo-banner">{t.demo}</p>;
}
