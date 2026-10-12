import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import '@fontsource/noto-sans-devanagari/400.css';
import '@fontsource/noto-sans-devanagari/600.css';
import '@fontsource/libre-baskerville/700.css';
import '@fontsource/tiro-devanagari-hindi/devanagari-400.css';
import './styles.css';
import { LangProvider } from './i18n';
import { AdminPage } from './pages/AdminPage';
import { FeedPage } from './pages/FeedPage';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <LangProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/admin/*" element={<AdminPage />} />
          <Route path="*" element={<FeedPage />} />
        </Routes>
      </BrowserRouter>
    </LangProvider>
  </StrictMode>,
);
