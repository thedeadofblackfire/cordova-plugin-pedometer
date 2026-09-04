import type { PluginListenerHandle } from '@capacitor/core';

/**
 * Internal step counter with an **autonomous** server sync.
 *
 * The plugin counts steps in a foreground service and pushes them to the configured URL on its own
 * schedule, **with the application closed** — that autonomy is the reason it exists. The app
 * configures the target (`configure`) and can read the local database back at any time
 * (`getEntries`); it never has to post the data itself.
 */

export type PedometerPermissionState = 'granted' | 'denied' | 'prompt' | 'prompt-with-rationale';

export interface PedometerPermissionStatus {
  /**
   * ACTIVITY_RECOGNITION. Reported as `granted` below Android 10, where the permission does not
   * exist — so the caller never sees a permission that can never be granted.
   */
  activity: PedometerPermissionState;
  /** POST_NOTIFICATIONS. Reported as `granted` below Android 13, for the same reason. */
  notifications: PedometerPermissionState;
}

/** Configuration of the autonomous sync, stored in the plugin's `settings` table. */
export interface PedometerConfig {
  /** identifies the user in the payload the service posts on its own */
  userId: string;
  /** absolute target URL, e.g. `https://startr-api.jebooj.com/v1/partners/dynafit` */
  apiUrl?: string;
  /** minutes between two autonomous syncs; Android clamps periodic work to 15 minutes minimum */
  syncIntervalMinutes?: number;
}

export interface PedometerNotificationStrings {
  /** title of the persistent notification, e.g. "Jebooj is counting your steps" */
  isCounting?: string;
  /** `%s` is replaced by the remaining step count */
  stepsToGoFormat?: string;
  yourProgressFormat?: string;
  goalReachedFormat?: string;
}

export interface PedometerStartOptions {
  /** steps already counted today server-side — the legacy `offset` of `startStepperUpdates` */
  startOffset?: number;
  goal?: number;
  notification?: PedometerNotificationStrings;
}

export interface StepsUpdateEvent {
  stepsToday: number;
  total: number;
  average: number;
  /** epoch ms */
  timestamp: number;
}

/**
 * One row of the local `steps` table.
 * `synced`: 0 pending, 1 queued for the current POST, 2 acknowledged by the server.
 */
export interface StepsEntry {
  id: number;
  /** epoch ms */
  date: number;
  steps: number;
  /** raw TYPE_STEP_COUNTER value, i.e. steps since the last boot */
  total?: number;
  /** `yyyy-dd-mm`, as written by the service */
  creationdate?: string;
  periodtime?: number;
  /** period range, `hour:minute` */
  startdate?: string;
  enddate?: string;
  lastupdate?: number;
  synced: number;
  synceddate?: number;
}

/** Filter of {@link PedometerPlugin.getEntries}. Every field is optional. */
export interface PedometerEntriesQuery {
  /** epoch ms, inclusive; omit for no lower bound */
  start?: number;
  /** epoch ms, inclusive; omit for no upper bound */
  end?: number;
  /** restrict to one sync state; `all` (default) returns every row, synced or not */
  synced?: 'pending' | 'queued' | 'synced' | 'all';
  limit?: number;
}

export type PedometerServiceStatus = 'running' | 'stopped' | 'unknown';

export interface PedometerSyncResult {
  /** rows pushed during this run (or acknowledged in total, for `getSyncStatus`) */
  sent: number;
  /** rows still waiting, e.g. because the device was offline */
  pending: number;
  /** epoch ms of the last acknowledged sync, `null` when nothing was ever pushed */
  lastSyncAt: number | null;
}

export interface PedometerPlugin {
  /** Whether the device exposes a step counter and the plugin can run. */
  isAvailable(): Promise<{ available: boolean; stepCounter: boolean }>;

  checkPermissions(): Promise<PedometerPermissionStatus>;
  requestPermissions(): Promise<PedometerPermissionStatus>;

  /**
   * Write the autonomous sync target into the plugin's SQLite settings.
   *
   * The service keeps posting to whatever is stored here, **even after the user logs out**, so call
   * this again whenever the user or the API base changes — otherwise the steps of the new user
   * would be attributed to the previous one.
   */
  configure(options: PedometerConfig): Promise<void>;
  getConfig(): Promise<PedometerConfig>;

  /** Start the foreground service and the periodic sync. Rejects if ACTIVITY_RECOGNITION is denied. */
  start(options?: PedometerStartOptions): Promise<void>;
  /** Stop the service and cancel the periodic sync. Recorded steps are kept. */
  stop(): Promise<void>;
  getStatus(): Promise<{ status: PedometerServiceStatus }>;

  setGoal(options: { goal: number }): Promise<void>;
  setNotificationStrings(options: PedometerNotificationStrings): Promise<void>;

  /** Steps of one day; `date` is the epoch ms of its **local** midnight. */
  getSteps(options: { date: number }): Promise<{ steps: number }>;
  getStepsByPeriod(options: { start: number; end: number }): Promise<{ steps: number }>;
  getLastEntries(options: { count: number }): Promise<{ entries: Array<{ date: number; steps: number }> }>;

  /**
   * Read the local rows over a date range, **synced or not**.
   *
   * The counterpart of the autonomous push: the service guarantees the data reaches the server
   * without the app, this lets a screen show what the device actually recorded — and what is still
   * pending.
   */
  getEntries(options?: PedometerEntriesQuery): Promise<{ entries: StepsEntry[] }>;

  /**
   * Force a flush now. Optional — the service syncs on its own schedule with the app closed; this
   * only shortens the wait while the user is looking at their steps.
   */
  sync(): Promise<PedometerSyncResult>;
  getSyncStatus(): Promise<PedometerSyncResult>;

  /** Android: open the battery optimisation dialog, so the service is not suspended. */
  openBatteryOptimizationSettings(): Promise<void>;

  /** Emitted while the app is in the foreground; the service keeps counting without it. */
  addListener(eventName: 'stepsUpdate', listenerFunc: (event: StepsUpdateEvent) => void): Promise<PluginListenerHandle>;

  removeAllListeners(): Promise<void>;
}
