package com.yoppworks.mmwas.devices

import akka.NotUsed
import akka.stream.scaladsl.Source

sealed trait VolumeDevice extends DeviceSource[Int] {
  val deviceName: String = "Volume"
}

case class MousePositionVolumeDevice(input: ScreenInput, output:ScreenOutput) extends VolumeDevice {
  val maxValue: Int = output.getWidth
  val minValue: Int = 0

  def dataSource: Source[Int, NotUsed] = {
    input
      .mousePositionSource
      .map {
        case MouseRelocated(x, _) =>
          Math.max(maxValue - x, 0) * 96 / maxValue + 31
      }
      .filter { volume => volume >= 0 && volume < 128 }
      .mapMaterializedValue { _ => NotUsed }
  }
}
