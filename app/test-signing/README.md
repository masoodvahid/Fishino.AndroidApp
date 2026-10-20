# Test signing key — public on purpose

`fishino-test.jks` (password `fishino-test`, alias `fishino-test`) signs only
the **preview** build: package `rdsco.fishino.app.test`, label «فیشی‌نو (تست)».
It keeps test builds installable over each other, so the test app can update
itself from each GitHub release (fishino-test-x.y.z.apk) and testers keep
their data. The in-app updater only downloads assets of this repository's
releases, so the public key alone does not let anyone push an update.

Because it is public, it must **never** sign the real app (`rdsco.fishino.app`).
The official release key lives only in GitHub secrets (see the main README).
