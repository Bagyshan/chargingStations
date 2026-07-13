import { Pencil, Plug, Plus, Trash2, Zap } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useConnectorTypes } from '@/api/hooks';
import { formatKw } from '@/lib/format';

export function ConnectorTypesPage() {
  const types = useConnectorTypes();

  return (
    <div className="space-y-6">
      <PageHeader
        title="Типы коннекторов"
        description="Справочник типов разъёмов, используемых на станциях"
        actions={
          <Button variant="accent">
            <Plus className="size-4" />
            Добавить тип
          </Button>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {types.isLoading
          ? Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-40 rounded-xl" />)
          : types.data?.map((t) => (
              <Card key={t.id} className="group transition-shadow hover:shadow-md">
                <CardContent className="p-5">
                  <div className="flex items-start justify-between">
                    <div className="flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
                      <Plug className="size-6" />
                    </div>
                    <div className="flex gap-1 opacity-0 transition-opacity group-hover:opacity-100">
                      <Button variant="ghost" size="iconSm">
                        <Pencil className="size-4" />
                      </Button>
                      <Button variant="ghost" size="iconSm">
                        <Trash2 className="size-4 text-danger" />
                      </Button>
                    </div>
                  </div>
                  <div className="mt-4 text-lg font-semibold">{t.connectorTypeName}</div>
                  <div className="mt-0.5 font-mono text-xs text-muted-foreground">{t.code}</div>
                  <div className="mt-4 flex items-center justify-between border-t border-border pt-3 text-sm">
                    <span className="inline-flex items-center gap-1.5 text-muted-foreground">
                      <Zap className="size-4 text-accent" />
                      до {formatKw(t.maxPowerKw)}
                    </span>
                    <span className="font-medium">{t.connectorsCount} шт.</span>
                  </div>
                </CardContent>
              </Card>
            ))}
      </div>
    </div>
  );
}
