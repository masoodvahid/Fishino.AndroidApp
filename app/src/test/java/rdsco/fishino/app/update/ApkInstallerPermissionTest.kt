package rdsco.fishino.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkInstallerPermissionTest {
    @Test
    fun missingManifestPermissionCannotCrashTheApp() {
        assertFalse(checkInstallPermission {
            throw SecurityException("Need to declare android.permission.REQUEST_INSTALL_PACKAGES to call this api")
        })
    }

    @Test
    fun permissionDenialRemainsFalse() {
        assertFalse(checkInstallPermission { false })
    }

    @Test
    fun grantedPermissionRemainsTrue() {
        assertTrue(checkInstallPermission { true })
    }

    @Test(expected = IllegalStateException::class)
    fun unrelatedFailuresAreNotHidden() {
        checkInstallPermission { throw IllegalStateException("Unexpected package manager failure") }
    }
}
