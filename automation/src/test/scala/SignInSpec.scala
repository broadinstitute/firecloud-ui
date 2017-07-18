import org.broadinstitute.dsde.firecloud.auth.{AuthToken, AuthTokens}
import org.broadinstitute.dsde.firecloud.pages.WebBrowserSpec
import org.broadinstitute.dsde.firecloud.{CleanUp, Config}
import org.scalatest._

class SignInSpec extends FreeSpec with WebBrowserSpec with CleanUp {

  implicit val authToken: AuthToken = AuthTokens.harry

  "A user" - {
    "with a registered account" - {

      "should be able to log out and log in as another user" in withWebDriver { implicit driver =>
        val listPageAsUser1 = signIn(Config.Users.harry)
        listPageAsUser1.signOut()
        val listPageAsUser2 = signIn(Config.Users.ron)
        assert(listPageAsUser2.getUser().startsWith(Config.Users.ron.email))
      }

      "should be able to log in and out multiple times as multiple users" in withWebDriver { implicit driver =>
        var listPageAsUser1 = signIn(Config.Users.harry)
        listPageAsUser1.signOut()
        val listPageAsUser2 = signIn(Config.Users.ron)
        listPageAsUser2.signOut()
        listPageAsUser1 = signIn(Config.Users.harry)
        assert(listPageAsUser1.getUser().startsWith(Config.Users.harry.email))
      }
    }

  }
}
