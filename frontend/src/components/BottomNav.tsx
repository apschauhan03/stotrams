import { NavLink } from 'react-router-dom';
import { useLang } from '../i18n';

/** The two places in the app, always at the bottom of the screen, with big icons and words. */
export function BottomNav() {
  const { t } = useLang();
  const tabs = [
    { to: '/', icon: '🖼️', label: t.navFeed },
    { to: '/panchang', icon: '🪔', label: t.navPanchang },
  ];
  return (
    <nav className="bottom-nav" aria-label="Main">
      {tabs.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end
          className={({ isActive }) => `bottom-nav__item${isActive ? ' bottom-nav__item--active' : ''}`}
        >
          <span className="bottom-nav__icon" aria-hidden="true">
            {tab.icon}
          </span>
          <span className="bottom-nav__label">{tab.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
