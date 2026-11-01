import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.Timer;
import Toybox.WatchUi;

enum PhoneStatus {
    STATUS_IDLE,
    STATUS_SENDING,
    STATUS_RINGING,
    STATUS_ERROR
}

const ACK_TIMEOUT_MS = 5000;

class FindMyPhoneApp extends Application.AppBase {
    var status as PhoneStatus = STATUS_IDLE;
    var errorText as String = "";

    private var _ackTimer as Timer.Timer or Null = null;

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary?) as Void {
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
    }

    function onStop(state as Dictionary?) as Void {
        stopAckTimer();
        Communications.registerForPhoneAppMessages(null);
    }

    function getInitialView() as [Views] or [Views, InputDelegates] {
        return [new FindMyPhoneView(), new FindMyPhoneDelegate()];
    }

    // Sends "ring" (or "stop" if the phone is already ringing).
    function toggleRing() as Void {
        var cmd = (status == STATUS_RINGING) ? Protocol.CMD_STOP : Protocol.CMD_RING;
        setStatus(STATUS_SENDING, "");
        Communications.transmit({ Protocol.KEY_CMD => cmd }, null, new SendListener());
        stopAckTimer();
        _ackTimer = new Timer.Timer();
        _ackTimer.start(method(:onAckTimeout), ACK_TIMEOUT_MS, false);
    }

    // Reply from the phone app.
    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        stopAckTimer();
        var data = msg.data;
        var state = null;
        if (data instanceof Dictionary) {
            state = data[Protocol.KEY_STATE];
        }
        if (Protocol.STATE_RINGING.equals(state)) {
            setStatus(STATUS_RINGING, "");
        } else if (Protocol.STATE_STOPPED.equals(state)) {
            setStatus(STATUS_IDLE, "");
        } else {
            setStatus(STATUS_ERROR, "Unexpected reply");
        }
    }

    function onAckTimeout() as Void {
        if (status == STATUS_SENDING) {
            setStatus(STATUS_ERROR, "No reply from phone");
        }
    }

    function onSendError() as Void {
        stopAckTimer();
        setStatus(STATUS_ERROR, "Can't reach phone");
    }

    function setStatus(s as PhoneStatus, text as String) as Void {
        status = s;
        errorText = text;
        WatchUi.requestUpdate();
    }

    private function stopAckTimer() as Void {
        if (_ackTimer != null) {
            _ackTimer.stop();
            _ackTimer = null;
        }
    }
}

class SendListener extends Communications.ConnectionListener {
    function initialize() {
        ConnectionListener.initialize();
    }

    function onComplete() as Void {
        // Delivered to the phone; now wait for its reply (see onPhoneMessage).
    }

    function onError() as Void {
        (Application.getApp() as FindMyPhoneApp).onSendError();
    }
}
