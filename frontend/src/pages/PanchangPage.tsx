import { useState } from 'react';
import { BottomNav } from '../components/BottomNav';
import { Header } from '../components/Header';
import { PanchangCard } from '../components/PanchangCard';
import { useLang } from '../i18n';

/** Today in India as yyyy-mm-dd, whatever timezone the phone is set to. */
const todayIst = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());

/** "2026-10-14" + 1 -> "2026-10-15" (calendar days, no timezone involved). */
function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** The panchang tab: today's panchang, with buttons to step to the day before or after. */
export function PanchangPage() {
  const { t } = useLang();
  const today = todayIst();
  const [date, setDate] = useState(today);

  return (
    <>
      <Header />
      <main className="page page--with-nav page--nav-end">
        <div className="panchang-days">
          <button type="button" className="btn" onClick={() => setDate((d) => addDays(d, -1))}>
            ‹ {t.prevDay}
          </button>
          <button type="button" className="btn" onClick={() => setDate(today)} disabled={date === today}>
            {t.todayBtn}
          </button>
          <button type="button" className="btn" onClick={() => setDate((d) => addDays(d, 1))}>
            {t.nextDay} ›
          </button>
        </div>
        <PanchangCard date={date} />
      </main>
      <BottomNav />
    </>
  );
}
