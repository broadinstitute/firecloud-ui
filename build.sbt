import Settings._

lazy val firecloudUiTests = project.in(file("automation"))
  .settings(rootSettings:_*)

version := "1.0"
