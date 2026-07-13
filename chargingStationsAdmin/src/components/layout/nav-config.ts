import {
  BarChart3,
  BatteryCharging,
  CalendarClock,
  Cable,
  Coins,
  LayoutDashboard,
  Plug,
  Settings,
  Users,
  Zap,
  type LucideIcon,
} from 'lucide-react';
import type { Role } from '@/types/domain';

/** Все маршруты приложения (совпадают с путями в src/app/router.tsx). */
export type AppPath =
  | '/'
  | '/analytics'
  | '/stations'
  | '/connectors'
  | '/tariffs'
  | '/connector-types'
  | '/bookings'
  | '/transactions'
  | '/users'
  | '/settings';

export interface NavItem {
  to: AppPath;
  label: string;
  icon: LucideIcon;
  /** Роли, которым виден пункт. По умолчанию — все управляющие роли. */
  roles?: Role[];
}

export interface NavGroup {
  title: string;
  items: NavItem[];
}

const ALL: Role[] = ['ADMIN', 'SPECIALIST', 'CONTRACTOR'];

export const NAV: NavGroup[] = [
  {
    title: 'Обзор',
    items: [
      { to: '/', label: 'Дашборд', icon: LayoutDashboard, roles: ALL },
      { to: '/analytics', label: 'Аналитика', icon: BarChart3, roles: ALL },
    ],
  },
  {
    title: 'Инфраструктура',
    items: [
      { to: '/stations', label: 'Станции', icon: Zap, roles: ALL },
      { to: '/connectors', label: 'Коннекторы', icon: Cable, roles: ALL },
      { to: '/tariffs', label: 'Тарифы', icon: Coins, roles: ALL },
      { to: '/connector-types', label: 'Типы коннекторов', icon: Plug, roles: ['ADMIN', 'SPECIALIST'] },
    ],
  },
  {
    title: 'Операции',
    items: [
      { to: '/bookings', label: 'Брони', icon: CalendarClock, roles: ALL },
      { to: '/transactions', label: 'Зарядки', icon: BatteryCharging, roles: ALL },
    ],
  },
  {
    title: 'Администрирование',
    items: [
      { to: '/users', label: 'Пользователи', icon: Users, roles: ['ADMIN'] },
      { to: '/settings', label: 'Настройки', icon: Settings, roles: ALL },
    ],
  },
];

export function visibleNav(role: Role): NavGroup[] {
  return NAV.map((g) => ({
    ...g,
    items: g.items.filter((i) => !i.roles || i.roles.includes(role)),
  })).filter((g) => g.items.length > 0);
}
