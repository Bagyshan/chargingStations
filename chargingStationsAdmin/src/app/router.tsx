import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  redirect,
} from '@tanstack/react-router';
import { AppShell } from '@/components/layout/app-shell';
import { LoginPage } from '@/features/auth/login-page';
import { DashboardPage } from '@/features/dashboard/dashboard-page';
import { AnalyticsPage } from '@/features/analytics/analytics-page';
import { StationsPage } from '@/features/stations/stations-page';
import { ConnectorsPage } from '@/features/connectors/connectors-page';
import { TariffsPage } from '@/features/tariffs/tariffs-page';
import { ConnectorTypesPage } from '@/features/connector-types/connector-types-page';
import { BookingsPage } from '@/features/bookings/bookings-page';
import { TransactionsPage } from '@/features/transactions/transactions-page';
import { UsersPage } from '@/features/users/users-page';
import { SettingsPage } from '@/features/settings/settings-page';
import { useAuth } from '@/store/auth';

const rootRoute = createRootRoute({ component: () => <Outlet /> });

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  component: LoginPage,
  beforeLoad: () => {
    if (useAuth.getState().account) throw redirect({ to: '/' });
  },
});

// Пейслесс-лейаут: защищает всё приложение и рендерит оболочку.
const appRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: 'app',
  component: AppShell,
  beforeLoad: () => {
    if (!useAuth.getState().account) throw redirect({ to: '/login' });
  },
});

const p = () => appRoute;

const routeTree = rootRoute.addChildren([
  loginRoute,
  appRoute.addChildren([
    createRoute({ getParentRoute: p, path: '/', component: DashboardPage }),
    createRoute({ getParentRoute: p, path: '/analytics', component: AnalyticsPage }),
    createRoute({ getParentRoute: p, path: '/stations', component: StationsPage }),
    createRoute({ getParentRoute: p, path: '/connectors', component: ConnectorsPage }),
    createRoute({ getParentRoute: p, path: '/tariffs', component: TariffsPage }),
    createRoute({ getParentRoute: p, path: '/connector-types', component: ConnectorTypesPage }),
    createRoute({ getParentRoute: p, path: '/bookings', component: BookingsPage }),
    createRoute({ getParentRoute: p, path: '/transactions', component: TransactionsPage }),
    createRoute({ getParentRoute: p, path: '/users', component: UsersPage }),
    createRoute({ getParentRoute: p, path: '/settings', component: SettingsPage }),
  ]),
]);

export const router = createRouter({
  routeTree,
  defaultPreload: 'intent',
});

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
