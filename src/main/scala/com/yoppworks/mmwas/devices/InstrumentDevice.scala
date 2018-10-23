package com.yoppworks.mmwas.devices

import akka.NotUsed
import akka.stream.scaladsl.Source

trait InstrumentDevice extends DeviceSource[Int] {
  val deviceName: String = "Timbre"
}

case class MouseWheelRotatedInstrumentDevice(
  input: ScreenInput
) extends InstrumentDevice {
  val maxValue: Int = 1000
  val minValue: Int = 0
  private var value: Int = 0

  override def dataSource: Source[Int, NotUsed] = {
    input.mouseWheelRotatedSource.map { rotation: MouseWheelRotated =>
      value += rotation.rotation
      Math.abs(value) % 127
    }
  }
}
