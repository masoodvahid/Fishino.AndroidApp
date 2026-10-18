# kotlinx.serialization and OkHttp ship their own consumer rules.
# BouncyCastle is used through its lightweight API only (no JCA provider),
# so R8 can strip everything else.
-dontwarn org.bouncycastle.**
