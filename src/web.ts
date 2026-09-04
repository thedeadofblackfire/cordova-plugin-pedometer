import { WebPlugin } from '@capacitor/core';

import type {
  PedometerConfig,
  PedometerEntriesQuery,
  PedometerNotificationStrings,
  PedometerPermissionStatus,
  PedometerPlugin,
  PedometerServiceStatus,
  PedometerStartOptions,
  PedometerSyncResult,
  StepsEntry,
} from './definitions';

/**
 * Web implementation — there is no step counter and no background service in a browser.
 *
 * Every method resolves with an empty, honest answer rather than throwing: a screen can call the
 * plugin unconditionally and simply see `available: false`. Only `start()` rejects, because
 * silently doing nothing there would let the UI believe the pedometer is running.
 */
export class PedometerWeb extends WebPlugin implements PedometerPlugin {
  private static readonly UNAVAILABLE = 'Pedometer is only available on a device.';

  async isAvailable(): Promise<{ available: boolean; stepCounter: boolean }> {
    return { available: false, stepCounter: false };
  }

  async checkPermissions(): Promise<PedometerPermissionStatus> {
    return { activity: 'denied', notifications: 'denied' };
  }

  async requestPermissions(): Promise<PedometerPermissionStatus> {
    return { activity: 'denied', notifications: 'denied' };
  }

  async configure(_options: PedometerConfig): Promise<void> {
    // no-op: nothing to configure without a native service
  }

  async getConfig(): Promise<PedometerConfig> {
    return { userId: '' };
  }

  async start(_options?: PedometerStartOptions): Promise<void> {
    throw this.unavailable(PedometerWeb.UNAVAILABLE);
  }

  async stop(): Promise<void> {
    // no-op
  }

  async getStatus(): Promise<{ status: PedometerServiceStatus }> {
    return { status: 'unknown' };
  }

  async setGoal(_options: { goal: number }): Promise<void> {
    // no-op
  }

  async setNotificationStrings(_options: PedometerNotificationStrings): Promise<void> {
    // no-op
  }

  async getSteps(_options: { date: number }): Promise<{ steps: number }> {
    return { steps: 0 };
  }

  async getStepsByPeriod(_options: { start: number; end: number }): Promise<{ steps: number }> {
    return { steps: 0 };
  }

  async getLastEntries(_options: { count: number }): Promise<{ entries: Array<{ date: number; steps: number }> }> {
    return { entries: [] };
  }

  async getEntries(_options?: PedometerEntriesQuery): Promise<{ entries: StepsEntry[] }> {
    return { entries: [] };
  }

  async sync(): Promise<PedometerSyncResult> {
    return { sent: 0, pending: 0, lastSyncAt: null };
  }

  async getSyncStatus(): Promise<PedometerSyncResult> {
    return { sent: 0, pending: 0, lastSyncAt: null };
  }

  async openBatteryOptimizationSettings(): Promise<void> {
    // no-op
  }
}
