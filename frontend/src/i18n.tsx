import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import type { Lang } from './api/types';

const STRINGS = {
  hi: {
    appName: 'स्तोत्रम्',
    tagline: 'रोज़ की दिव्य प्रेरणा',
    morning: 'सुप्रभात',
    night: 'शुभ रात्रि',
    share: 'WhatsApp पर भेजें',
    preparing: 'तैयार हो रहा है…',
    download: 'फ़ोटो सेव करें',
    downloaded: 'फ़ोटो सेव हो गई। अब WhatsApp खोलकर भेजें।',
    close: 'बंद करें',
    previous: 'पिछला चित्र',
    next: 'अगला चित्र',
    openImage: 'चित्र खोलें',
    loading: 'चित्र आ रहे हैं…',
    empty: 'आज के चित्र जल्द आ रहे हैं 🙏',
    error: 'चित्र नहीं आ सके। इंटरनेट जाँचें और फिर से कोशिश करें।',
    retry: 'फिर से कोशिश करें',
    end: 'आज के लिए इतना ही 🙏 कल फिर आइए',
    shareText: '🙏 स्तोत्रम् से भेजा गया',
    today: (day: string, deity: string) => `आज ${day} है — ${deity} का दिन`,
    switchLang: 'English',
    demo: 'डेमो मोड: सर्वर नहीं चल रहा, नमूना चित्र दिखाए जा रहे हैं।',
    footer: 'स्तोत्रम् पूरी तरह मुफ़्त है, बिना विज्ञापन। हम कभी पैसे, OTP या बैंक जानकारी नहीं माँगते।',
  },
  en: {
    appName: 'Stotrams',
    tagline: 'Daily divine inspiration',
    morning: 'Good Morning',
    night: 'Good Night',
    share: 'Send on WhatsApp',
    preparing: 'Getting ready…',
    download: 'Save photo',
    downloaded: 'Photo saved. Now open WhatsApp and send it.',
    close: 'Close',
    previous: 'Previous image',
    next: 'Next image',
    openImage: 'Open image',
    loading: 'Loading images…',
    empty: "Today's images are coming soon 🙏",
    error: 'Could not load images. Check your internet and try again.',
    retry: 'Try again',
    end: "That's all for today 🙏 Come back tomorrow",
    shareText: '🙏 Sent from Stotrams',
    today: (day: string, deity: string) => `Today is ${day} — the day of ${deity}`,
    switchLang: 'हिंदी',
    demo: 'Demo mode: backend not running, showing sample images.',
    footer: 'Stotrams is completely free, with no ads. We will never ask for money, OTPs or bank details.',
  },
};

export type Strings = (typeof STRINGS)['hi'];

/** Traditional weekday deities — the homepage greets with today's. */
const WEEKDAY_DEITY: Record<Lang, [day: string, deity: string][]> = {
  hi: [
    ['रविवार', 'सूर्य देव'],
    ['सोमवार', 'भगवान शिव'],
    ['मंगलवार', 'हनुमान जी'],
    ['बुधवार', 'गणेश जी'],
    ['गुरुवार', 'भगवान विष्णु'],
    ['शुक्रवार', 'माँ लक्ष्मी'],
    ['शनिवार', 'शनि देव'],
  ],
  en: [
    ['Sunday', 'Surya Dev'],
    ['Monday', 'Lord Shiva'],
    ['Tuesday', 'Hanuman Ji'],
    ['Wednesday', 'Ganesh Ji'],
    ['Thursday', 'Lord Vishnu'],
    ['Friday', 'Maa Lakshmi'],
    ['Saturday', 'Shani Dev'],
  ],
};

export function todayLine(lang: Lang, date = new Date()): string {
  const [day, deity] = WEEKDAY_DEITY[lang][date.getDay()];
  return STRINGS[lang].today(day, deity);
}

const STORAGE_KEY = 'stotram.lang';

function initialLang(): Lang {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'hi' || saved === 'en') return saved;
  } catch {
    // storage blocked — fall through to the default
  }
  return 'hi';
}

interface LangContextValue {
  lang: Lang;
  t: Strings;
  toggleLang: () => void;
}

const LangContext = createContext<LangContextValue | null>(null);

export function LangProvider({ children }: { children: ReactNode }) {
  const [lang, setLang] = useState<Lang>(initialLang);

  useEffect(() => {
    document.documentElement.lang = lang;
    document.title = `${STRINGS[lang].appName} · ${STRINGS[lang].tagline}`;
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // not fatal: the choice just won't persist
    }
  }, [lang]);

  const value: LangContextValue = {
    lang,
    t: STRINGS[lang],
    toggleLang: () => setLang((l) => (l === 'hi' ? 'en' : 'hi')),
  };
  return <LangContext.Provider value={value}>{children}</LangContext.Provider>;
}

export function useLang(): LangContextValue {
  const ctx = useContext(LangContext);
  if (!ctx) throw new Error('useLang must be used inside <LangProvider>');
  return ctx;
}
