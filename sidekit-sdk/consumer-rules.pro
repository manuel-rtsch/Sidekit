# The SDK's public API is ordinary Kotlin/Compose code. The accessibility service and any future
# manifest components are referenced by name from a consumer's manifest, which R8 keeps through
# the Android manifest keep rules. Add explicit -keep rules here only for API reached by
# reflection.
