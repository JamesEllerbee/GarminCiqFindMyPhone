import Toybox.Lang;

// Message protocol shared with AndroidApp/.../Protocol.kt. Keep both in sync.
module Protocol {
    // Watch -> phone
    const KEY_CMD = "cmd";
    const CMD_RING = "ring";
    const CMD_STOP = "stop";

    // Phone -> watch
    const KEY_STATE = "state";
    const STATE_RINGING = "ringing";
    const STATE_STOPPED = "stopped";
}
