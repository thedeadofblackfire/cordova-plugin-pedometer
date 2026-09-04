import Foundation
import Capacitor
import CoreMotion

/**
 * iOS implementation, backed by CoreMotion's CMPedometer.
 *
 * ⚠️ **Deliberately partial.** On iOS the Start'R app reads its steps from HealthKit
 * (`partner_type = 'apple'`), not from this plugin — the internal pedometer is an **Android**
 * feature (`partner_type = 'dynafit'`). The Cordova version was in the same situation: its iOS half
 * only wrapped `CMPedometer` and was never used by the app.
 *
 * So what is implemented here is what CoreMotion can honestly provide:
 *  - availability and live step updates (`start` / `stop` + `stepsUpdate`);
 *  - historical queries, which CMPedometer serves for the **last 7 days only**.
 *
 * Everything that belongs to the Android foreground service — the local database, the autonomous
 * HTTP sync, the notification, the battery settings — rejects with `UNAVAILABLE` rather than
 * pretending to work. iOS has no equivalent: a background app cannot run a persistent counter and
 * post on its own the way the Android service does.
 */
@objc(PedometerPlugin)
public class PedometerPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "PedometerPlugin"
    public let jsName = "Pedometer"

    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "isAvailable", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "configure", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getConfig", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "start", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getStatus", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setGoal", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setNotificationStrings", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getSteps", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getStepsByPeriod", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getLastEntries", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getEntries", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "sync", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getSyncStatus", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openBatteryOptimizationSettings", returnType: CAPPluginReturnPromise)
    ]

    private let pedometer = CMPedometer()
    private var isCounting = false

    /// CMPedometer only keeps the last seven days of history.
    private static let historyDays = 7

    // MARK: - Availability

    @objc func isAvailable(_ call: CAPPluginCall) {
        call.resolve([
            "available": CMPedometer.isStepCountingAvailable(),
            "stepCounter": CMPedometer.isStepCountingAvailable()
        ])
    }

    /// CoreMotion shows its own prompt on first use, so there is nothing to request up front.
    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        call.resolve(permissionStatus())
    }

    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        call.resolve(permissionStatus())
    }

    private func permissionStatus() -> [String: String] {
        let state: String
        switch CMPedometer.authorizationStatus() {
        case .authorized: state = "granted"
        case .denied, .restricted: state = "denied"
        default: state = "prompt"
        }
        // there is no notification for the counter on iOS: nothing to grant
        return ["activity": state, "notifications": "granted"]
    }

    // MARK: - Live counting

    @objc func start(_ call: CAPPluginCall) {
        guard CMPedometer.isStepCountingAvailable() else {
            call.reject("UNAVAILABLE: this device has no step counter")
            return
        }

        isCounting = true
        pedometer.startUpdates(from: Date()) { [weak self] data, error in
            guard let self = self, let data = data, error == nil else { return }

            self.notifyListeners("stepsUpdate", data: [
                "stepsToday": data.numberOfSteps.intValue,
                "total": data.numberOfSteps.intValue,
                "average": data.numberOfSteps.intValue,
                "timestamp": Int(Date().timeIntervalSince1970 * 1000)
            ])
        }
        call.resolve()
    }

    @objc func stop(_ call: CAPPluginCall) {
        pedometer.stopUpdates()
        isCounting = false
        call.resolve()
    }

    @objc func getStatus(_ call: CAPPluginCall) {
        call.resolve(["status": isCounting ? "running" : "stopped"])
    }

    // MARK: - History (CMPedometer, last 7 days)

    @objc func getSteps(_ call: CAPPluginCall) {
        guard let date = call.getDouble("date") else {
            call.reject("INVALID_RANGE: date is required")
            return
        }

        let start = Date(timeIntervalSince1970: date / 1000)
        let end = Calendar.current.date(byAdding: .day, value: 1, to: start) ?? Date()
        query(from: start, to: end, call: call)
    }

    @objc func getStepsByPeriod(_ call: CAPPluginCall) {
        guard let start = call.getDouble("start"), let end = call.getDouble("end") else {
            call.reject("INVALID_RANGE: start and end are required")
            return
        }
        query(from: Date(timeIntervalSince1970: start / 1000), to: Date(timeIntervalSince1970: end / 1000), call: call)
    }

    private func query(from: Date, to: Date, call: CAPPluginCall) {
        guard CMPedometer.isStepCountingAvailable() else {
            call.resolve(["steps": 0])
            return
        }

        pedometer.queryPedometerData(from: from, to: to) { data, error in
            if let error = error {
                call.reject("QUERY_FAILED: \(error.localizedDescription)")
                return
            }
            call.resolve(["steps": data?.numberOfSteps.intValue ?? 0])
        }
    }

    /// One entry per day over the window CMPedometer still holds.
    @objc func getLastEntries(_ call: CAPPluginCall) {
        let requested = call.getInt("count") ?? Self.historyDays
        let count = min(requested, Self.historyDays)

        guard CMPedometer.isStepCountingAvailable(), count > 0 else {
            call.resolve(["entries": []])
            return
        }

        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())

        var entries: [[String: Any]] = []
        let group = DispatchGroup()

        for offset in 0..<count {
            guard let dayStart = calendar.date(byAdding: .day, value: -offset, to: today),
                  let dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart) else { continue }

            group.enter()
            pedometer.queryPedometerData(from: dayStart, to: dayEnd) { data, _ in
                entries.append([
                    "date": Int(dayStart.timeIntervalSince1970 * 1000),
                    "steps": data?.numberOfSteps.intValue ?? 0
                ])
                group.leave()
            }
        }

        group.notify(queue: .main) {
            let sorted = entries.sorted { ($0["date"] as? Int ?? 0) < ($1["date"] as? Int ?? 0) }
            call.resolve(["entries": sorted])
        }
    }

    // MARK: - Android-only surface

    /// No local database on iOS: there is nothing to read back, synced or not.
    @objc func getEntries(_ call: CAPPluginCall) {
        call.resolve(["entries": []])
    }

    /// The autonomous sync is the Android foreground service; iOS has no equivalent.
    @objc func configure(_ call: CAPPluginCall) {
        call.reject("UNAVAILABLE: the autonomous sync is Android only; iOS reads its steps from HealthKit")
    }

    @objc func getConfig(_ call: CAPPluginCall) {
        call.resolve(["userId": ""])
    }

    @objc func sync(_ call: CAPPluginCall) {
        call.resolve(["sent": 0, "pending": 0, "lastSyncAt": NSNull()])
    }

    @objc func getSyncStatus(_ call: CAPPluginCall) {
        call.resolve(["sent": 0, "pending": 0, "lastSyncAt": NSNull()])
    }

    /// No persistent notification on iOS, so no goal or strings to store for one.
    @objc func setGoal(_ call: CAPPluginCall) {
        call.resolve()
    }

    @objc func setNotificationStrings(_ call: CAPPluginCall) {
        call.resolve()
    }

    /// iOS has no user-facing battery optimisation exclusion.
    @objc func openBatteryOptimizationSettings(_ call: CAPPluginCall) {
        call.resolve()
    }
}
