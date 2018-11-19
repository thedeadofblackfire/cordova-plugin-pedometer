
var exec = require("cordova/exec");

var Pedometer = function () {
    this.name = "Pedometer";
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

Pedometer.prototype.stopService = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "stopService", []);
};

Pedometer.prototype.deviceCanCountSteps = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "deviceCanCountSteps", []);
};

Pedometer.prototype.sync = function (onSuccess, onError) {
    exec(onSuccess, onError, "Pedometer", "sync", []);
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

module.exports = new Pedometer();
