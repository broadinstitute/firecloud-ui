package org.broadinstitute.dsde.firecloud.config
import Config.Users._

object UserPool {

  val allUsers: UserSet = UserSet(Owners.userMap ++ Students.userMap ++ Curators.userMap ++ AuthDomainUsers.userMap)

  /**
    * Chooses a user suitable for a generic test.
    * Users in Owners, Curators, AuthDomainUsers, and Students
    */
  def chooseAnyUser(n: Int = 1): Seq[Credentials] = {
    allUsers.getRandomCredentials(n)
  }

  /**
    * Chooses an admin user.
    */
  def chooseAdmin(n: Int = 1): Seq[Credentials] = {
    Admins.getRandomCredentials(n)
  }

  /**
    * Chooses a project owner.
    */
  def chooseProjectOwner(n: Int = 1): Seq[Credentials] = {
    Owners.getRandomCredentials(n)
  }

  /**
    * Chooses a curator.
    */
  def chooseCurator(n: Int = 1): Seq[Credentials] = {
    Curators.getRandomCredentials(n)
  }

  /**
    * Chooses a student.
    */
  def chooseStudent(n: Int = 1): Seq[Credentials] = {
    Students.getRandomCredentials(n)
  }

  /**
    * Chooses an auth domain user.
    */
  def chooseAuthDomainUser(n: Int = 1): Seq[Credentials] = {
    AuthDomainUsers.getRandomCredentials(n)
  }

  /**
    * Chooses a temp user.
    */
  def chooseTemp(n: Int = 1): Seq[Credentials] = {
    Temps.getRandomCredentials(n)
  }

}
