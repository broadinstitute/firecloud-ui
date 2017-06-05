import Settings._

lazy val root = project.in(file("."))
  .settings(rootSettings:_*)

name := "root"

version := "1.0"

scalaVersion := "2.11.8"