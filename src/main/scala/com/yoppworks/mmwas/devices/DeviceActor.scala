package com.yoppworks.mmwas.devices

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingAdapter

trait ActorLoggingUtility {
  def log: LoggingAdapter
  
  def debug(msg: ⇒ String): Unit = {
    log.debug(msg)
  }
  def error(action: ⇒ String, xcptn: ⇒ Throwable): Unit = {
    log.error(s"While $action: ${xcptn.getClass.getName}: ${xcptn.getMessage}")
  }
  
}
/** Unit Tests For Device */
trait DeviceActor extends Actor with ActorLogging with ActorLoggingUtility
