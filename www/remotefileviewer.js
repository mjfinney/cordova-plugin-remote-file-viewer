
var exec = require('cordova/exec');
var channel = require('cordova/channel');
var modulemapper = require('cordova/modulemapper');

function RemoteFileViewer() {
  this.channels = {
    'update': channel.create('update'),
    'error' : channel.create('error'),
    'success' : channel.create('success'),
  };
}

RemoteFileViewer.prototype = {
  _eventHandler: function (event) {
    if (event && (event.type in this.channels)) {
      this.channels[event.type].fire(event);
    }
  },
  addEventListener: function (eventname,f) {
    if (eventname in this.channels) {
      this.channels[eventname].subscribe(f);
    }
  },
  removeEventListener: function(eventname, f) {
    if (eventname in this.channels) {
      this.channels[eventname].unsubscribe(f);
    }
  }
};

module.exports = function(strUrl, callbacks) {

  var rfv = new RemoteFileViewer();

  callbacks = callbacks || {};
  for (var callbackName in callbacks) {
    rfv.addEventListener(callbackName, callbacks[callbackName]);
  }

  var cb = function(eventname) {
    rfv._eventHandler(eventname);
  };

  exec(cb, cb, "RemoteFileViewer", "open", [strUrl]);
  return rfv;
};
