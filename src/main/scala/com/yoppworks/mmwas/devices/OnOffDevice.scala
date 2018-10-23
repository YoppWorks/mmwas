package com.yoppworks.mmwas.devices

import akka.NotUsed
import akka.stream.scaladsl.Source

sealed trait OnOffDevice extends DeviceSource[Int] {
  val deviceName: String= "OnOff"
}

case class MouseWindowOnOffDevice(input: ScreenInput)
  extends OnOffDevice {
  val maxValue: Int = 1
  val minValue: Int = 0
  override def dataSource : Source[Int, NotUsed] = {
    input.mouseWindowSource.map {
      case MouseEntered ⇒
        1
      case MouseExited =>
        0
    }
  }
}