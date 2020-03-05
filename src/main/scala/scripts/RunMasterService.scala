package scripts

import core.MasterService

object RunMasterService {
  def main(args: Array[String]): Unit = {
    val masterService = new MasterService()
    masterService.start()
  }
}