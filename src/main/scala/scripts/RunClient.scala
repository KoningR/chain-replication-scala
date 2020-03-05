package scripts

import core.Client

object RunClient {
  def main(args: Array[String]): Unit = {
    val client = new Client()
    client.start()
  }
}
