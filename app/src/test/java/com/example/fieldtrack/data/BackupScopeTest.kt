package com.example.fieldtrack.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Android may copy to a new phone.
 *
 * Facts about one install must stay behind: they are wrong the moment they land on a
 * different handset. The records themselves (the database, the session, the settings
 * people chose) go with the person.
 */
class BackupScopeTest {

    private val installOnly = listOf("fieldtrack.device.xml", "fieldtrack.sync.xml", "fieldtrack.pull.xml", "clock_check.xml", "field_walk.xml", "clock_notifications.xml", "fieldtrack.push.xml")

    private fun xml(name: String) = File("src/main/res/xml/$name").readText()

    @Test fun `what describes one install is left behind, by both ways the platform backs up`() {
        for (file in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val text = xml(file)
            for (prefs in installOnly) assertTrue("$file must exclude $prefs", text.contains("""path="$prefs""""))
        }
    }

    @Test fun `a device to device move leaves them behind as well as a cloud restore`() {
        val text = xml("data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val body = text.substringAfter("<$section>").substringBefore("</$section>")
            for (prefs in installOnly) assertTrue("$section must exclude $prefs", body.contains("""path="$prefs""""))
        }
    }

    @Test fun `the records are never excluded`() {
        for (file in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val text = xml(file)
            assertTrue(!text.contains("domain=\"database\""))
            assertTrue(!text.contains("fieldtrack.session.xml"))
        }
    }
}
