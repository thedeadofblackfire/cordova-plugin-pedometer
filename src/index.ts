import { registerPlugin } from '@capacitor/core';

import type { PedometerPlugin } from './definitions';

const Pedometer = registerPlugin<PedometerPlugin>('Pedometer', {
  web: () => import('./web').then((m) => new m.PedometerWeb()),
});

export * from './definitions';
export { Pedometer };
