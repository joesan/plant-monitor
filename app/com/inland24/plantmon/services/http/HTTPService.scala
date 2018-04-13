package com.inland24.plantsim.services.http

trait HTTPService {
  def post[T](msg: T)
}
object HTTPService {

  final class ClusterBackend extends HTTPService {
    override def post[T](msg: T): Unit = ???
  }

  final class ZombieBackend extends HTTPService {
    override def post[T](msg: T): Unit = ???
  }
}