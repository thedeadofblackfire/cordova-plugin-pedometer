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
  /**
   * Title while the sensor warms up (fewer than `titleWarmupSteps` steps today: service just
   * started, or a new day), e.g. "En route 🏆 🥇". Also the static title when no dynamic title
   * format is set — the Cordova behaviour.
   */
  isCounting?: string;
  /**
   * Dynamic title with a daily goal — the only line visible while the notification is collapsed:
   * `%1$s` steps today, `%2$s` daily goal, e.g. "%1$s / %2$s pas aujourd'hui". `""` goes back to
   * the static `isCounting` title.
   */
  titleFormat?: string;
  /** title once the daily goal is reached, same placeholders; falls back to `titleFormat` */
  titleGoalReachedFormat?: string;
  /** title without a daily goal: `%s` steps today, e.g. "%s pas aujourd'hui"; `""` = static title */
  titleNoGoalFormat?: string;
  /** steps today below which the title stays `isCounting` (warm-up); default 10 */
  titleWarmupSteps?: number;
  /**
   * With a daily goal: `%s` is replaced by the remaining step count.
   * Also the text without a goal when `stepsTodayFormat` is not set (legacy behaviour).
   */
  stepsToGoFormat?: string;
  /** shown as is (not formatted) until the first step value is known */
  yourProgressFormat?: string;
  /** daily goal reached: `%s` is replaced by today's steps */
  goalReachedFormat?: string;
  /** no daily goal: `%s` is replaced by today's steps, e.g. "%s pas aujourd'hui" */
  stepsTodayFormat?: string;
  /**
   * Second line (expanded notification) while a challenge runs:
   * `%1$s` challenge name, `%2$s` steps, `%3$s` challenge goal — e.g. "%1$s : %2$s / %3$s pas".
   */
  challengeFormat?: string;
}

/**
 * Multi-day challenge goal, shown as a second line of the Android notification while
 * `start <= now <= end`.
 */
export interface PedometerChallengeGoal {
  id?: string;
  name?: string;
  /** steps to reach over the whole challenge */
  goal: number;
  /** epoch ms */
  start: number;
  /** epoch ms */
  end: number;
  /**
   * Challenge total known by the server at `baseAt`. The notification shows it plus the steps
   * recorded locally since. Omitted: the local steps between `start` and `end`.
   */
  baseSteps?: number;
  /** epoch ms at which `baseSteps` was read; defaults to now */
  baseAt?: number;
}

export interface PedometerGoals {
  /** daily goal (steps of the current local day), 0 when there is none */
  dailyGoal: number;
  stepsToday: number;
  /** the stored challenge, `active` false once it ended; null when none */
  challenge: (PedometerChallengeGoal & { steps: number; active: boolean }) | null;
}

export interface PedometerStartOptions {
  /** steps already counted today server-side — the legacy `offset` of `startStepperUpdates` */
  startOffset?: number;
  /** daily goal, see `setGoal`; omitted keeps the stored one */
  goal?: number;
  notification?: PedometerNotificationStrings;
}

export interface StepsUpdateEvent {
  stepsToday: number;
  total: number;
  average: number;
  /** epoch ms */
  timestamp: number;
  /** Android: daily goal, 0 when there is none */
  dailyGoal?: number;
  /** Android: steps of the running challenge, absent when none runs */
  challengeSteps?: number;
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

/** Result of {@link PedometerPlugin.exportDatabase}. */
export interface PedometerDatabaseExport {
  /** e.g. `steps-20260925-203000.db` */
  name: string;
  /** absolute path in the app cache */
  path: string;
  /** `file://` URI — can be passed to `@capacitor/share` (`files: [uri]`) */
  uri: string;
  /** bytes */
  size: number;
}

/** Android battery optimisation state (Doze / App Standby exemption). */
export interface PedometerBatteryStatus {
  /**
   * `PowerManager.isIgnoringBatteryOptimizations()`. When true, the system also lets the service
   * restart itself in the foreground after its process was killed (START_STICKY, task removal) —
   * the exemption that keeps the counting permanent. Always `true` below Android 6 and on the web.
   */
  ignoring: boolean;
  /** an OEM-specific screen (Xiaomi, Huawei, Oppo…) exists, see `openBatteryOptimizationSettings` */
  oemSettingsAvailable: boolean;
}

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

  /**
   * Daily goal: steps of the current **local** day, the progress bar of the Android notification.
   * It starts again from 0 at local midnight on its own. `0` (or the legacy `1`) removes it.
   * The notification is redrawn immediately.
   */
  setGoal(options: { goal: number }): Promise<void>;
  /** Multi-day challenge goal, coexisting with the daily goal. Call again with a fresher `baseSteps`. */
  setChallengeGoal(options: PedometerChallengeGoal): Promise<void>;
  clearChallengeGoal(): Promise<void>;
  getGoals(): Promise<PedometerGoals>;
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

  /**
   * Android, debug: snapshot of the local SQLite database (`steps.db`, tables `steps` and `settings`)
   * into the app cache, WAL checkpointed first. Only the latest export is kept.
   */
  exportDatabase(): Promise<PedometerDatabaseExport>;

  /** Android: whether the app is exempt from battery optimisations. */
  getBatteryOptimizationStatus(): Promise<PedometerBatteryStatus>;

  /**
   * Android: ask the system to exempt the app from battery optimisations — the standard
   * "Allow the app to always run in the background?" dialog
   * (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). Resolves with the state once the dialog is
   * closed; falls back to the system list when the dialog is not available.
   */
  requestIgnoreBatteryOptimizations(): Promise<PedometerBatteryStatus>;

  /**
   * Android: open the OEM "protected apps / autostart" screen when the manufacturer has one
   * (Xiaomi, Huawei, Oppo…), otherwise the system battery optimisation list.
   */
  openBatteryOptimizationSettings(): Promise<void>;

  /** Emitted while the app is in the foreground; the service keeps counting without it. */
  addListener(eventName: 'stepsUpdate', listenerFunc: (event: StepsUpdateEvent) => void): Promise<PluginListenerHandle>;

  removeAllListeners(): Promise<void>;
}
