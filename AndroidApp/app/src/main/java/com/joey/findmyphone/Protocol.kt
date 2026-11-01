package com.joey.findmyphone

/** Message protocol shared with WatchApp/source/Protocol.mc. Keep both in sync. */
object Protocol {
    // Watch -> phone
    const val KEY_CMD = "cmd"
    const val CMD_RING = "ring"
    const val CMD_STOP = "stop"

    // Phone -> watch
    const val KEY_STATE = "state"
    const val STATE_RINGING = "ringing"
    const val STATE_STOPPED = "stopped"
}
