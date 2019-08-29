# Frequently Asked Questions

## Tapir is throwing `ClassCastException` at runtime while handling requests!

You most likely have functions that take an `Endpoint[I, E, O, S]`, but isn't taking an implicit `ParamsAsArgs[I]` or `ParamsAsArgs[O]` instance. See [github](https://github.com/softwaremill/tapir/issues/132#issuecomment-500458354) for an example fix.
