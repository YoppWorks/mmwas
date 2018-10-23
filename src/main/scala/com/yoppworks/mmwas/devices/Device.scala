package com.yoppworks.mmwas.devices

import akka.NotUsed
import akka.stream.{ Materializer, OverflowStrategy }
import akka.stream.scaladsl.Source

/** Unit Tests For Device */
trait Device {
  def deviceName: String
  def unprovision(): Unit
}


trait InputDevice[+MT] extends Device {
  def readOneValue: MT
}

trait DeviceSource[+ValueType] extends Device {
  def dataSource: Source[ValueType, NotUsed]
  def maxValue: ValueType
  def minValue: ValueType
  def unprovision(): Unit = {}
}

case class ActorBasedDataSource[MT](name: String, bufferSize: Int = 0)(
  implicit mat: Materializer) {
  val (dataActor, dataSource) = Source
    .actorRef[MT](bufferSize, OverflowStrategy.dropHead)
    .named(name)
    .preMaterialize
}
