package app.myownware.perseid

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupPolicyTest {
    @Test
    fun legacyBackupExcludesPrivateStorage() {
        val root = readRules("backup_rules")
        assertEquals(privateDomains, exclusions(root))
    }

    @Test
    fun cloudBackupAndDeviceTransferExcludePrivateStorage() {
        val root = readRules("data_extraction_rules")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val rules = root.getElementsByTagName(section).item(0) as Element
            assertEquals(privateDomains, exclusions(rules))
        }
    }

    private fun readRules(name: String): Element {
        val file = File("src/main/res/xml/$name.xml")
        return DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    private fun exclusions(rules: Element): Set<String> {
        val entries = rules.getElementsByTagName("exclude")
        return (0 until entries.length)
            .map { entries.item(it) as Element }
            .filter { it.getAttribute("path") == "." }
            .map { it.getAttribute("domain") }
            .toSet()
    }

    private val privateDomains =
        setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )
}
