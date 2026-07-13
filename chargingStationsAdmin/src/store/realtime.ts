import { create } from 'zustand';

export type RealtimeStatus = 'off' | 'connecting' | 'live';

interface RealtimeState {
  status: RealtimeStatus;
  lastEventAt: number | null;
  events: number;
  setStatus: (s: RealtimeStatus) => void;
  markEvent: () => void;
}

export const useRealtimeStore = create<RealtimeState>((set) => ({
  status: 'off',
  lastEventAt: null,
  events: 0,
  setStatus: (status) => set({ status }),
  markEvent: () => set((s) => ({ lastEventAt: Date.now(), events: s.events + 1 })),
}));
