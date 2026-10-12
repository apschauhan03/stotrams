import type { CategorySlug } from '../api/types';
import { useLang } from '../i18n';

interface Props {
  value: CategorySlug;
  onChange: (category: CategorySlug) => void;
}

export function CategoryTabs({ value, onChange }: Props) {
  const { t } = useLang();
  const tabs: { slug: CategorySlug; label: string; icon: string }[] = [
    { slug: 'good-morning', label: t.morning, icon: '🌅' },
    { slug: 'good-night', label: t.night, icon: '🌙' },
  ];

  return (
    <nav className="tabs" role="tablist">
      {tabs.map((tab) => (
        <button
          key={tab.slug}
          type="button"
          role="tab"
          aria-selected={value === tab.slug}
          className={`tab${value === tab.slug ? ' tab--active' : ''}`}
          onClick={() => onChange(tab.slug)}
        >
          <span aria-hidden="true">{tab.icon}</span> {tab.label}
        </button>
      ))}
    </nav>
  );
}
