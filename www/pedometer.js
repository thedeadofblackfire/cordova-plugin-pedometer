var exec = require("cordova/exec");

var Pedometer = function () {
    this.name = "Pedometer";
};

Pedometer.prototype.startStepperUpdates = function (offset, onSuccess, onError, options) {
    offset = parseInt(offset) || 0;
    options = options || {};
    exec(onSuccess, onError, "Pedometer", "startStepperUpdates", [offset, options]);
};

Pedometer.prototype.stopStepperUpdates = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "stopStepperUpdates", []);
};


Pedometer.prototype.isStepCountingAvailable = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "isStepCountingAvailable", []);
};

Pedometer.prototype.isDistanceAvailable = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "isDistanceAvailable", []);
};

Pedometer.prototype.isFloorCountingAvailable = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "isFloorCountingAvailable", []);
};

Pedometer.prototype.startPedometerUpdates = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "startPedometerUpdates", []);
};

Pedometer.prototype.stopPedometerUpdates = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "stopPedometerUpdates", []);
};

Pedometer.prototype.queryData = function (onSuccess, onError, options) {
    exec(onSuccess, onError, "Pedometer", "queryData", [options]);
};

Pedometer.prototype.startService = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "startService", []);
};

Pedometer.prototype.startServiceSilent = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "startServiceSilent", []);
};

Pedometer.prototype.stopService = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "stopService", []);
};

Pedometer.prototype.statusService = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "statusService", []);
};

Pedometer.prototype.deviceCanCountSteps = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "deviceCanCountSteps", []);
};

Pedometer.prototype.deviceCheckPermissions = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "deviceCheckPermissions", []);
};

Pedometer.prototype.sync = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "sync", []);
};

Pedometer.prototype.rollback = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "rollback", []);
};

Pedometer.prototype.reset = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "reset", []);
};

Pedometer.prototype.clean = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "clean", []);
};

Pedometer.prototype.setConfig = function (onSuccess, onError, options) {
    exec(onSuccess, onError, "Pedometer", "setConfig", [options]);
};

Pedometer.prototype.debug = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "debug", []);
};

// inspired from cordova-plugin-stepper
Pedometer.prototype.setNotificationLocalizedStrings = function (keyValueObj, onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "setNotificationLocalizedStrings", [keyValueObj]);
};

Pedometer.prototype.setGoal = function (num, onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "setGoal", [num]);
};

Pedometer.prototype.getSteps = function (date, onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "getSteps", [date]);
};

Pedometer.prototype.getStepsByPeriod = function (start, end, onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "getStepsByPeriod", [start, end]);
};

Pedometer.prototype.getLastEntries = function (num, onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "getLastEntries", [num]);
};

module.exports = new Pedometer();
