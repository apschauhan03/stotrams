import { useEffect, useState } from 'react';
import { fetchPanchang } from '../api/client';
import type { Lang, Panchang } from '../api/types';
import { useLang } from '../i18n';

// "आज का पंचांग": today's tithi, paksha, month, samvat, sunrise and sunset, in big plain text
// for older readers. The backend sends codes (tithi 1–30, month 0–11); the words live here.

const TITHI: Record<Lang, string[]> = {
  hi: ['प्रतिपदा', 'द्वितीया', 'तृतीया', 'चतुर्थी', 'पंचमी', 'षष्ठी', 'सप्तमी', 'अष्टमी', 'नवमी', 'दशमी',
    'एकादशी', 'द्वादशी', 'त्रयोदशी', 'चतुर्दशी'],
  en: ['Pratipada', 'Dwitiya', 'Tritiya', 'Chaturthi', 'Panchami', 'Shashthi', 'Saptami', 'Ashtami', 'Navami',
    'Dashami', 'Ekadashi', 'Dwadashi', 'Trayodashi', 'Chaturdashi'],
};
const PURNIMA: Record<Lang, string> = { hi: 'पूर्णिमा', en: 'Purnima' };
const AMAVASYA: Record<Lang, string> = { hi: 'अमावस्या', en: 'Amavasya' };

const MONTH: Record<Lang, string[]> = {
  hi: ['चैत्र', 'वैशाख', 'ज्येष्ठ', 'आषाढ़', 'श्रावण', 'भाद्रपद', 'आश्विन', 'कार्तिक', 'मार्गशीर्ष', 'पौष', 'माघ', 'फाल्गुन'],
  en: ['Chaitra', 'Vaishakha', 'Jyeshtha', 'Ashadha', 'Shravana', 'Bhadrapada', 'Ashwin', 'Kartik', 'Margashirsha',
    'Pausha', 'Magha', 'Phalguna'],
};

/** Index 1 = Monday … 7 = Sunday, as the backend sends it. */
const WEEKDAY: Record<Lang, string[]> = {
  hi: ['', 'सोमवार', 'मंगलवार', 'बुधवार', 'गुरुवार', 'शुक्रवार', 'शनिवार', 'रविवार'],
  en: ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'],
};

/** The nine forms of Maa Durga, one per Navratri day. */
const NAVADURGA: Record<Lang, string[]> = {
  hi: ['माँ शैलपुत्री', 'माँ ब्रह्मचारिणी', 'माँ चंद्रघंटा', 'माँ कूष्मांडा', 'माँ स्कंदमाता', 'माँ कात्यायनी',
    'माँ कालरात्रि', 'माँ महागौरी', 'माँ सिद्धिदात्री'],
  en: ['Maa Shailputri', 'Maa Brahmacharini', 'Maa Chandraghanta', 'Maa Kushmanda', 'Maa Skandamata',
    'Maa Katyayani', 'Maa Kalaratri', 'Maa Mahagauri', 'Maa Siddhidatri'],
};
const DAY_ORDINAL_HI = ['पहला', 'दूसरा', 'तीसरा', 'चौथा', 'पाँचवाँ', 'छठा', 'सातवाँ', 'आठवाँ', 'नौवाँ'];

const L = {
  hi: {
    title: 'आज का पंचांग',
    titleOther: 'पंचांग',
    loading: 'पंचांग आ रहा है…',
    unavailable: 'पंचांग अभी नहीं आ सका। थोड़ी देर बाद फिर देखें।',
    tithi: 'तिथि',
    paksha: 'पक्ष',
    month: 'मास',
    day: 'वार',
    samvat: 'विक्रम संवत',
    sunrise: 'सूर्योदय',
    sunset: 'सूर्यास्त',
    shukla: 'शुक्ल पक्ष',
    krishna: 'कृष्ण पक्ष',
    adhik: 'अधिक',
    till: (time: string) => `${time} तक`,
    tomorrow: 'कल',
    place: 'नई दिल्ली के सूर्योदय के अनुसार। आपके शहर में समय थोड़ा अलग हो सकता है।',
    navratri: (kind: 'sharad' | 'chaitra', day: number) =>
      `${kind === 'sharad' ? 'शारदीय' : 'चैत्र'} नवरात्रि का ${DAY_ORDINAL_HI[day - 1]} दिन — ${NAVADURGA.hi[day - 1]}`,
    ekadashi: 'एकादशी व्रत का दिन',
    purnima: 'पूर्णिमा का दिन',
    amavasya: 'अमावस्या का दिन',
  },
  en: {
    title: "Today's Panchang",
    titleOther: 'Panchang',
    loading: 'Loading panchang…',
    unavailable: "The panchang couldn't load right now. Please try again in a little while.",
    tithi: 'Tithi',
    paksha: 'Paksha',
    month: 'Month',
    day: 'Day',
    samvat: 'Vikram Samvat',
    sunrise: 'Sunrise',
    sunset: 'Sunset',
    shukla: 'Shukla Paksha',
    krishna: 'Krishna Paksha',
    adhik: 'Adhik',
    till: (time: string) => `till ${time}`,
    tomorrow: 'tomorrow',
    place: 'As per sunrise in New Delhi. Times in your city may differ slightly.',
    navratri: (kind: 'sharad' | 'chaitra', day: number) =>
      `${kind === 'sharad' ? 'Sharad' : 'Chaitra'} Navratri, day ${day} — ${NAVADURGA.en[day - 1]}`,
    ekadashi: 'Ekadashi vrat',
    purnima: 'Purnima (full moon)',
    amavasya: 'Amavasya (new moon)',
  },
};

function tithiName(lang: Lang, n: number): string {
  if (n === 15) return PURNIMA[lang];
  if (n === 30) return AMAVASYA[lang];
  return TITHI[lang][(n - 1) % 15];
}

/** Hour and minute in IST, from an ISO time with offset. */
function istParts(iso: string): { h: number; m: number; day: string } {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Kolkata',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(iso));
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return { h: Number(get('hour')), m: Number(get('minute')), day: `${get('year')}-${get('month')}-${get('day')}` };
}

/** "सुबह 6:21" / "6:21 AM": a time as people say it. */
function sayTime(lang: Lang, iso: string): string {
  const { h, m } = istParts(iso);
  const mm = String(m).padStart(2, '0');
  const h12 = h % 12 === 0 ? 12 : h % 12;
  if (lang === 'en') return `${h12}:${mm} ${h < 12 ? 'AM' : 'PM'}`;
  const period = h >= 4 && h < 12 ? 'सुबह' : h >= 12 && h < 16 ? 'दोपहर' : h >= 16 && h < 19 ? 'शाम' : 'रात';
  return `${period} ${h12}:${mm}`;
}

function sayDate(lang: Lang, iso: string): string {
  return new Date(`${iso}T12:00:00`).toLocaleDateString(lang === 'hi' ? 'hi-IN' : 'en-IN', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}

/** `date` yyyy-mm-dd; omitted = today. */
export function PanchangCard({ date }: { date?: string }) {
  const { lang } = useLang();
  const [p, setP] = useState<Panchang | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setFailed(false);
    fetchPanchang(date)
      .then((res) => !cancelled && (res ? setP(res) : setFailed(true)))
      .catch(() => !cancelled && setFailed(true));
    return () => {
      cancelled = true;
    };
  }, [date]);

  const t = L[lang];
  if (failed && !p) return <p className="feed-status">{t.unavailable}</p>;
  if (!p) return <p className="feed-status">{t.loading}</p>;
  const isToday = p.date === new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());
  const shukla = p.tithi <= 15;
  const ends = istParts(p.tithiEndsAt);
  const endsTime = sayTime(lang, p.tithiEndsAt);
  const endsLabel =
    ends.day === p.date ? t.till(endsTime) : lang === 'hi' ? t.till(`${t.tomorrow} ${endsTime}`) : `${t.till(endsTime)} ${t.tomorrow}`;

  const special =
    p.navratri !== null
      ? t.navratri(p.navratri, p.tithi)
      : p.tithi === 11 || p.tithi === 26
        ? t.ekadashi
        : p.tithi === 15
          ? t.purnima
          : p.tithi === 30
            ? t.amavasya
            : null;

  return (
    <section className="panchang" aria-labelledby="panchang-title">
      <header className="panchang__head">
        <h2 id="panchang-title" className="panchang__title">
          🪔 {isToday ? t.title : t.titleOther}
        </h2>
        <p className="panchang__date">
          {WEEKDAY[lang][p.weekday]}, {sayDate(lang, p.date)}
        </p>
      </header>

      {special && <p className="panchang__special">{special}</p>}

      <dl className="panchang__list">
        <div className="panchang__row panchang__row--main">
          <dt>{t.tithi}</dt>
          <dd>
            {tithiName(lang, p.tithi)}
            <span className="panchang__sub">{endsLabel}</span>
          </dd>
        </div>
        <div className="panchang__row">
          <dt>{t.paksha}</dt>
          <dd>{shukla ? t.shukla : t.krishna}</dd>
        </div>
        <div className="panchang__row">
          <dt>{t.month}</dt>
          <dd>
            {p.adhik ? `${t.adhik} ` : ''}
            {MONTH[lang][p.month]}
          </dd>
        </div>
        <div className="panchang__row">
          <dt>{t.day}</dt>
          <dd>{WEEKDAY[lang][p.weekday]}</dd>
        </div>
        <div className="panchang__row">
          <dt>{t.samvat}</dt>
          <dd>{p.vikramSamvat}</dd>
        </div>
        <div className="panchang__row">
          <dt>☀️ {t.sunrise}</dt>
          <dd>{sayTime(lang, p.sunrise)}</dd>
        </div>
        <div className="panchang__row">
          <dt>🌇 {t.sunset}</dt>
          <dd>{sayTime(lang, p.sunset)}</dd>
        </div>
      </dl>

      <p className="panchang__note">{t.place}</p>
    </section>
  );
}
