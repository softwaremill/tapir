# 12. Extracting schema as a module

Date: 2022-05-16

## Context

As part of making the `core` module smaller, we've been considering splitting out `schema` to a separate module, on which `core` would depend. This would not only make `core` leaner, but also potentially allow others to use `schema` without depending on the entire `core`.

## Decision

Doing such a move would require removing `Part`, `FileRange`, enumeration validator helper methods out of the companion objects of `Schema` and `Validator`; or - adding a dependency on `sttp-model` to the extracted project. The first would decrease the "programmer experience" while using tapir, the second would make the whole operation impractical (as one of the goals was to create a module with a smaller number of dependencies).

As benefits are not immediately apparent, and there are no known use-cases for an extracted `schema` module, this change was not introduced.