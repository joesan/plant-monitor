package com.inland24.plantmon.services.http

import com.inland24.plantmon.config.HTTPServiceConfig

trait HTTPService {
  def post[T](msg: T)
}
object HTTPService {

  final class ClusterBackend(host: String, port: Int) extends HTTPService {
    override def post[T](msg: T): Unit = ???
  }

  final class ZombieBackend extends HTTPService {
    override def post[T](msg: T): Unit = ???
  }

  def apply(httpConfig: HTTPServiceConfig): HTTPService = {
    if (httpConfig.isClusterEnabled)
      new ClusterBackend(httpConfig.host, httpConfig.port)
    else
      new ZombieBackend
  }
}