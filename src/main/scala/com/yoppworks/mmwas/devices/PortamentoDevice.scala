package com.yoppworks.mmwas.devices

import scala.concurrent.duration._
import akka.NotUsed
import akka.stream.scaladsl.Source

sealed trait PortamentoDevice extends DeviceSource[Int] {
  val deviceName: String = "Volume"
}

case class MousePressedPortamentoDevice(
  input: ScreenInput
) extends PortamentoDevice {
  val maxValue: Int = 1000
  val minValue: Int = -1000
  val slideTime: Long = 2.seconds.toMillis
  var lastLeft: Long = 0
  var lastRight: Long = 0

  /* The MIDI specification stipulates that pitch bend be a 14-bit value,
     where zero is maximum downward bend, 16383 is maximum upward bend,
     and 8192 is the center (no pitch bend). The actual amount of pitch
     change is not specified; it can be changed by a pitch-bend
     sensitivity setting. However, the General MIDI specification
     says that the default range should be two semitones up and down
     from center. It is possible that the underlying synthesizer
     does not support this MIDI message. In order to verify that
     setPitchBend was successful, use getPitchBend.
   */

  def dataSource: Source[Int, NotUsed] = {
    input.mouseInputSource
      .map { event =>
        val now = System.currentTimeMillis()
        event match {
          case LeftMouseDown(when: Long) ⇒
            lastLeft = when
          case LeftMouseUp(_: Long) ⇒
            lastLeft = 0
          case RightMouseDown(when: Long) ⇒
            lastRight = when
          case RightMouseUp(_: Long) ⇒
            lastRight = 0
        }
        val down = if (lastLeft > 0) {
          -Math.max(now - lastLeft, slideTime) * maxValue / slideTime
        } else {
          0
        }
        val up = if (lastRight > 0) {
          Math.max(now - lastRight, slideTime) * maxValue / slideTime
        } else {
          0
        }
        Math.max(minValue, Math.min(maxValue, (down + up).toInt))
      }
      .filterNot { _ == 0 }
      .map { portamento =>
        ( portamento + 2000) * 16383 / 4000
      }
  }
}
