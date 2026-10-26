package com.github.demandss.dayz_workbench_tool.layout_editor

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File

/**
 * The folder of an installed DayZ for the tests that read its real scripts: the one in the variable DAYZ_GAME_DIR, or the usual
 * folder of Steam. When there is none the test is skipped, and the report says so, instead of passing without checking anything.
 */
internal fun installedGame(): String {
    val found =
        listOfNotNull(System.getenv("DAYZ_GAME_DIR"), "C:/Program Files (x86)/Steam/steamapps/common/DayZ")
            .firstOrNull { File(it, "dta/scripts.pbo").isFile }
    assumeTrue(found != null, "no installed DayZ found: set DAYZ_GAME_DIR to its folder to run this test")
    return found!!
}
