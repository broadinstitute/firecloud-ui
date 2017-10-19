package org.broadinstitute.dsde.firecloud.config
import Config.Users._

object UserPool {

  val allUsers: UserSet = UserSet(Owners.userMap ++ Students.userMap ++ Curators.userMap ++ AuthDomainUsers.userMap)

  /**
    * Chooses a user suitable for a generic test.
    * Users in Owners, Curators, AuthDomainUsers, and Students
    */
  def chooseAnyUser: Credentials = chooseAnyUsers(1).head

  def chooseAnyUsers(n: Int): Seq[Credentials] = {
    allUsers.getRandomCredentials(n)
  }

  /**
    * Chooses an admin user.
    */
  def chooseAdmin: Credentials = chooseAdmins(1).head

  def chooseAdmins(n: Int): Seq[Credentials] = {
    Admins.getRandomCredentials(n)
  }

  /**
    * Chooses a project owner.
    */
  def chooseProjectOwner: Credentials = chooseProjectOwners(1).head

  def chooseProjectOwners(n: Int): Seq[Credentials] = {
    Owners.getRandomCredentials(n)
  }

  /**
    * Chooses a curator.
    */
  def chooseCurator: Credentials = chooseCurators(1).head

  def chooseCurators(n: Int): Seq[Credentials] = {
    Curators.getRandomCredentials(n)
  }

  /**
    * Chooses a student.
    */
  def chooseStudent: Credentials = chooseStudents(1).head

  def chooseStudents(n: Int): Seq[Credentials] = {
    Students.getRandomCredentials(n)
  }

  /**
    * Chooses an auth domain user.
    */
  def chooseAuthDomainUser: Credentials = chooseAuthDomainUsers(1).head

  def chooseAuthDomainUsers(n: Int): Seq[Credentials] = {
    AuthDomainUsers.getRandomCredentials(n)
  }

  /**
    * Chooses a temp user.
    */
  def chooseTemp: Credentials = chooseTemps(1).head

  def chooseTemps(n: Int): Seq[Credentials] = {
    Temps.getRandomCredentials(n)
  }

}
