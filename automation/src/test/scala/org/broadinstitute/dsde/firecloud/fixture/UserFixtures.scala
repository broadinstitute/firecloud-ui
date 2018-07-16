package org.broadinstitute.dsde.firecloud.fixture

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.scalatest.concurrent.{Eventually, ScaledTimeSpans}
import org.broadinstitute.dsde.firecloud.page.AuthenticatedPage
import org.broadinstitute.dsde.firecloud.page.user.{RegistrationPage, SignInPage}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.workbench.config.Credentials
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.openqa.selenium.WebDriver
import org.scalatest.TestSuite
import org.broadinstitute.dsde.workbench.service.util.Retry.retry

import scala.concurrent.duration._

trait UserFixtures extends CleanUp with ScaledTimeSpans with Eventually { self: WebBrowserSpec with TestSuite =>

  /**
    * "Signs in" to FireCloud with an access token, bypassing the Google sign-in flow. Assumes the
    * user is already registered so hands the test code a ready WorkspaceListPage.
    */
  def withSignIn(user: Credentials)
                (testCode: WorkspaceListPage => Any)(implicit webDriver: WebDriver): Unit = {
    withSignIn(user, new WorkspaceListPage)(testCode)
  }

  /**
    * Signs in to FireCloud using the Google sign-in flow. Assumes the user is already registered so
    * hands the test code a ready WorkspaceListPage.
    */
  def withSignInReal(user: Credentials)
                    (testCode: WorkspaceListPage => Any)(implicit webDriver: WebDriver): Unit = {
    withSignInReal(user, new WorkspaceListPage)(testCode)
  }

  /**
    * Signs in to FireCloud using the Google sign-in flow. Returns a ready RegistrationPage.
    */
  def withSignInNewUserReal(user: Credentials)
                           (testCode: RegistrationPage => Any)(implicit webDriver: WebDriver): Unit = {
    withSignInReal(user, new RegistrationPage)(testCode)
  }


  private def withSignIn[T <: AuthenticatedPage](user: Credentials, page: T)
                                                (testCode: T => Any)
                                                (implicit webDriver: WebDriver): Unit = {
    withSignIn(user, {
      // workaround for failed forceSignedIn
      var counter = 0
      retry(Seq.fill(2)(1.seconds)) ({
        new SignInPage(FireCloudConfig.FireCloud.baseUrl).open
        executeScript(s"window.forceSignedIn('${user.makeAuthToken().value}')")
        if (counter > 0) logger.warn(s"Retrying forceSignedIn. $counter")
        counter +=1
        try {
          page.awaitReady()
          Some(page)
        } catch {
          case _: Throwable =>
            None
        }
      })
    }, page, testCode)
  }

  private def withSignInReal[T <: AuthenticatedPage](user: Credentials, page: T)
                                                    (testCode: T => Any)
                                                    (implicit webDriver: WebDriver): Unit = {
    withSignIn(user, {
      new SignInPage(FireCloudConfig.FireCloud.baseUrl).open.signIn(user.email, user.password)
    }, page, testCode)
  }

  private def withSignIn[T <: AuthenticatedPage](user: Credentials, signIn: => Unit,
                                                         page: T, testCode: T => Any)
                                                        (implicit webDriver: WebDriver): Unit = {
    signIn
    await ready page
    logger.info(s"Login user: ${user.email}. URL: ${page}")

    // Don't try/finally here to prevent sign-out before capturing a failure screenshot
    testCode(page)

    try page.signOut() catch nonFatalAndLog(s"ERROR logging out user: ${user.email}")
  }

//  def makeTempDownloadDirectory(): String = {
//    /*
//     * This might work some day if docker permissions get straightened out... or it might not be
//     * needed. For now, we instead `chmod 777` the directory in run-tests.sh.
//    new File("chrome").mkdirs()
//    val downloadPath = Files.createTempDirectory(Paths.get("chrome"), "downloads")
//    val permissions = Set(PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE)
//    Files.setPosixFilePermissions(downloadPath, permissions.asJava)
//    downloadPath.toString
//     */
//
//    val downloadPath = s"chrome/downloads/${makeRandomId(5)}"
//    val dir = new File(downloadPath)
//    dir.deleteOnExit()
//    dir.mkdirs()
//    val path = dir.toPath
//    logger.info(s"mkdir: $path")
//    val permissions = Set(
//      PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE,
//      PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
//      PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE)
//    import scala.collection.JavaConverters._
//    Files.setPosixFilePermissions(path, permissions.asJava)
//    path.toString
//  }
//
//  /**
//    * Downloads the metadata currently being viewed.
//    *
//    * If downloadPath is given, the file is given a timestamped name and moved from that location
//    * into the "downloads" directory off the current working directory. This serves two purposes:
//    *
//    * 1. Archiving the file for later inspection when tests fail
//    * 2. Keeping the browser download directory clean so that it doesn't auto-rename subsequent
//    * downloads with the same filename
//    *
//    * @param downloadPath the directory where the browser saves downloaded files
//    * @return the relative path to the moved download file, or None if downloadPath was not given
//    */
//  def downloadFile(downloadPath: Option[String] = None, fileName: String, downloadLink: Link, inputForm: Option[CssSelectorQuery]): Option[String] = synchronized {
//
//    def archiveDownloadedFile(sourcePath: String): String = {
//      // wait up to 10 seconds for file exist
//      val f = new File(sourcePath)
//      println("FILE: " + f.getAbsoluteFile)
//      eventually {
//        assert(f.exists(), s"Timed out (10 seconds) waiting for file $f")
//      }
//
//      val date = DateTimeFormatter.ofPattern(dateFormatPatter).format(LocalDateTime.now())
//      val destFile = new File(sourcePath).getName + s".$date"
//      val destPath = s"downloads/$destFile"
//      Util.moveFile(sourcePath, destPath)
//      logger.info(s"Moved file. sourcePath: $sourcePath, destPath: $destPath")
//      destPath
//    }
//
//    downloadLink.awaitEnabled()
//
//    /*
//     * Downloading a file will open another window while the download is in progress and
//     * automatically close it when the download is complete.
//     */
//    // await condition (windowHandles.size == 1, 30)
//    // .submit call takess care waiting for a new window
//
//    inputForm match {
//      case None => {
//        println("download link click")
//        downloadLink.doClick()
//      }
//      case Some(form) => {
//        logger.info(s"form: ${form.queryString}")
//        find(form).get.underlying.submit()
//      }
//    }
//
//    for {
//      path <- downloadPath
//    } yield archiveDownloadedFile(s"$path/$fileName")
//  }
//
//  lazy val dateFormatPatter = "HH:mm:ss:N" // with nano seconds

}
