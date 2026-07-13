import { create } from 'zustand';
import { persist } from 'zustand/middleware';

type Theme = 'light' | 'dark';

interface ThemeState {
  theme: Theme;
  setTheme: (t: Theme) => void;
  toggle: () => void;
}

function apply(theme: Theme) {
  const root = document.documentElement;
  root.classList.toggle('dark', theme === 'dark');
}

export const useTheme = create<ThemeState>()(
  persist(
    (set, get) => ({
      theme: 'dark',
      setTheme(theme) {
        apply(theme);
        set({ theme });
      },
      toggle() {
        const next = get().theme === 'dark' ? 'light' : 'dark';
        apply(next);
        set({ theme: next });
      },
    }),
    {
      name: 'batenergy-admin-theme',
      onRehydrateStorage: () => (state) => {
        if (state) apply(state.theme);
      },
    },
  ),
);
