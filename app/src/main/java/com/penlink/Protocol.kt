package com.penlink

/**
 * Protokol (satu koneksi WebSocket):
 *  TEXT  (JSON)  : auth / ok / err / cfg / ping / pong
 *  BINARY client->host : [1][n] + n * sample(19 byte, big-endian)
 *        sample = action(1) tool(1) buttons(1) x(f32) y(f32) pressure(f32) tilt(f32)
 *        x,y dinormalisasi 0..1 terhadap area gambar host; pressure 0..1
 *  BINARY host->client : [2] + JPEG
 */
object Protocol {
    const val DEFAULT_PORT = 8080
    const val MSG_INPUT: Byte = 1
    const val MSG_FRAME: Byte = 2
    const val SAMPLE_SIZE = 19

    const val A_DOWN = 0; const val A_UP = 1; const val A_MOVE = 2; const val A_CANCEL = 3
    const val A_HOVER_ENTER = 4; const val A_HOVER_MOVE = 5; const val A_HOVER_EXIT = 6

    const val TOOL_FINGER = 0; const val TOOL_STYLUS = 1; const val TOOL_ERASER = 2
    const val BTN_PRIMARY = 1; const val BTN_SECONDARY = 2
}
