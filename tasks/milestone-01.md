# Milestone 01: Stabilize schema and example specs

- [ ] Copy the current XSD into `spec/xsd/BinaryMessageSchema.xsd`.
- [ ] Copy the current example spec into `spec/examples/example-message-spec.xml`.
- [ ] Fix the duplicated checksum `alg` attribute.
- [ ] Add concrete `blobArray` and `blobVector` definitions if missing.
- [ ] Revalidate the example spec against the repaired XSD.
- [ ] Add at least three additional focused example specs:
  - [ ] bit-field heavy example
  - [ ] vector/countField example
  - [ ] string/blob/checksum example
- [ ] Document semantic rules not enforced by XSD.
