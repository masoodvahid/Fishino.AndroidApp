# Test signing key — public on purpose

`fishino-test.jks` (password `fishino-test`, alias `fishino-test`) signs only
the **preview** build: package `rdsco.fishino.app.test`, label «فیشی‌نو (تست)».
Its only job is to keep test builds installable over each other, so testers
keep their data between test releases.

Because it is public, it must **never** sign the real app (`rdsco.fishino.app`).
The official release key lives only in GitHub secrets (see the main README).
