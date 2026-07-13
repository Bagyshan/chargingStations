import { create } from 'zustand';

export type ToastKind = 'success' | 'error' | 'info';

export interface ToastItem {
  id: number;
  kind: ToastKind;
  title: string;
  description?: string;
}

interface ToastState {
  toasts: ToastItem[];
  push: (t: Omit<ToastItem, 'id'>) => void;
  dismiss: (id: number) => void;
}

let seq = 1;

export const useToasts = create<ToastState>((set) => ({
  toasts: [],
  push(t) {
    const id = seq++;
    set((s) => ({ toasts: [...s.toasts, { id, ...t }] }));
    setTimeout(() => set((s) => ({ toasts: s.toasts.filter((x) => x.id !== id) })), 4200);
  },
  dismiss(id) {
    set((s) => ({ toasts: s.toasts.filter((x) => x.id !== id) }));
  },
}));

/** Удобные хелперы для вызова тостов вне React. */
export const toast = {
  success: (title: string, description?: string) =>
    useToasts.getState().push({ kind: 'success', title, description }),
  error: (title: string, description?: string) =>
    useToasts.getState().push({ kind: 'error', title, description }),
  info: (title: string, description?: string) =>
    useToasts.getState().push({ kind: 'info', title, description }),
};
