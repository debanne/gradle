package Gradle_Check_TestCoverageCrossVersionFullJava7Linux

import Gradle_Check_TestCoverageCrossVersionFullJava7Linux.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "a7a97af7-0804-4a13-9eaa-ba14a084433a"
    extId = "Gradle_Check_TestCoverageCrossVersionFullJava7Linux"
    parentId = "Gradle_Check_Stage7"
    name = "Test Coverage - Cross-version Full Java7 Linux"

    buildType(Gradle_Check_TestCoverageCrossVersionFullJava7Linux_1)
})