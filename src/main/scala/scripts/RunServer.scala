package scripts

import core.Server

object RunServer {
  def main(args: Array[String]): Unit = {
    val server = new Server()
    server.start()
  }
}