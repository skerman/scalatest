#!/bin/sh
exec scala "$0" "$@"
!#

/*
 * This script converts java source files that define tag classes into
 * scala files that can be used to generate scaladocs.
 *
 * The resulting scala files won't work for actually running code, but
 * they're close enough to get into the scaladocs.
 *
 * The script rewrites six files in src/main/java/org/scalatest
 *  
 *   DoNotDiscover.java
 *   Ignore.java
 *   Finders.java
 *   TagAnnotation.java
 *   WrapWith.java
 *   tags/Slow.java
 *
 * It copies them into target/docsrc/org/scalatest, removing java annotations
 * and converting them into similar scala code and preserving their header
 * comments so those make it into the scaladocs.
 */

import java.io.File
import java.io.PrintWriter
import java.util.regex.Pattern
import scala.io.Source

val srcDir    = "src/main/java/org/scalatest"
val docsrcDir = "target/docsrc/org/scalatest"

//
// Splits java file's contents into two pieces: a top and body.
// The top contains the first scaladoc encountered plus
// everything else up through the declared class's name. The
// body contains the following curly braces and their contents.
//
def parseContents(className: String, text: String): (String, String) = {
  val topDocPat = Pattern.compile("""(?s)^(.*?/\*\*.*?\*/)(.*)$""")
  val topDocMat = topDocPat.matcher(text)

  topDocMat.find()

  val bodyPat = Pattern.compile("""(?sm)(.*? @interface """ + className +
                                """) *(\{.*\})""")
  val bodyMat = bodyPat.matcher(topDocMat.group(2))

  bodyMat.find()
  (topDocMat.group(1) + bodyMat.group(1), bodyMat.group(2))
}

//
// Constructs a modified class body where the java declaration of the value()
// method, where present, is replaced by a scala version.
//
def genNewBody(body: String): String = {
  val matcher =
    Pattern.compile("""(?m)^\s*(.*?) *value\(\);""").matcher(body)

  if (matcher.find()) {
    val valueType = matcher.group(1)

    val newValueType =
      valueType match {
        case "Class<? extends Suite>" => "Class[_ <: Suite]"
        case "String"                 => "String"
        case "String[]"               => "Array[String]"
        case _ =>
          throw new RuntimeException("unexpected valueType [" +
                                     valueType + "]")
    }

    val buf = new StringBuffer
    matcher.appendReplacement(buf, " def value(): "+ newValueType)
    matcher.appendTail(buf)

    buf.toString
  }
  else ""
}

//
// Processes source code above the body.  If code contains scaladoc it
// splits that out and processes the code above and below it separately.
//
def genNewTop(top: String): String = {
  val matcher = Pattern.compile("""(?s)^(.*?)(/\*\*.*?\*/)(.*)$""").matcher(top)

  if (matcher.find()) {
    val code = matcher.group(1)
    val comment = matcher.group(2)
    val remainder = matcher.group(3)

    processCode(code) + comment + genNewTop(remainder)
  }
  else {
    processCode(top)
  }
}

//
// Removes java code in order to make it palatable to scaladoc processor.
//
def processCode(text: String): String = {
  text.replaceAll("""@Retention\(.*?\)""",  "")
      .replaceAll("""@Target\(.*?\)""",     "")
      .replaceAll("""@TagAnnotation.*\)""", "")
      .replaceAll("""@TagAnnotation""",     "")
      .replaceAll("""@Inherited""",         "")
      .replaceAll("""public *@interface""", "")
      .replaceAll("""(?m)^import.*$""",     "")
}

def main() {
  println("docjavatags.scala: porting java tag files to scala")

  val filenames = Set("DoNotDiscover.java",
                      "Ignore.java",
                      "Finders.java",
                      "TagAnnotation.java",
                      "WrapWith.java",
                      "tags/ChromeBrowser.java",
                      "tags/FirefoxBrowser.java",
                      "tags/HtmlUnitBrowser.java",
                      "tags/InternetExplorerBrowser.java",
                      "tags/SafariBrowser.java",
                      "tags/Slow.java",
                      "tags/CPU.java",
                      "tags/Disk.java",
                      "tags/Network.java",
                      "tags/Retryable.java"
  )

  for (filename <- filenames) {
    val contents  = Source.fromFile(srcDir +"/"+ filename).mkString
    val className =
      filename.replaceFirst("""^.*/""", "").replaceFirst("""\.java$""", "")

    val (top, body) = parseContents(className, contents)

    val newTop = genNewTop(top)
    val newBody = genNewBody(body)
    val newContents =
      newTop
        .replaceFirst(className + "$",
                      "trait "+ className +
                      " extends java.lang.annotation.Annotation "+ newBody +
                      "\n")

    if (filename.contains("/")) {
      val newDir =
        new File(docsrcDir +"/"+ filename.replaceFirst("""/.*$""", ""))
      val result = newDir.mkdirs()
    }

    val newFile =
      new PrintWriter(docsrcDir +"/"+
                      filename.replaceFirst("""\.java$""", ".scala"))
    newFile.print(newContents)
    if (filename == "TagAnnotation.java")
      newFile.print("{ def value: String}")
    newFile.close()
  }
}

main()
