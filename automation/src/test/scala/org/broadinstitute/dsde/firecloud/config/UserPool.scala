package org.broadinstitute.dsde.firecloud.config
import scala.util.Random

class UserPool {

  private val rnd = new Random
  private val allUsers = 

  /**
    * Chooses a user suitable for a generic test.
    * Users in Owners, Curators, AuthDomainUsers, and Students
    */
  def chooseAnyUser: Credentials = {

  }

  /**
    * Chooses an admin user.
    */
  def chooseAdmin: Credentials = {
    Config.Users.Admins.getRandomCredential
  }

  /**
    * Chooses a project owner.
    */
  def chooseProjectOwner: Credentials = {
    Config.Users.Owners.getRandomCredential
  }

  /**
    * Chooses a curator.
    */
  def chooseCurator: Credentials = {
    Config.Users.Curators.getRandomCredential
  }

  /**
    * Chooses a student.
    */
  def chooseStudent: Credentials = {
    Config.Users.Students.getRandomCredential
  }

}
