# CRUD contract transition

`CrudResource`, `CrudService`, and `CrudMapper` accept independent create,
update, and response types. Requests and responses do not need to extend
`BaseDto`. Update identity is passed separately from the request body.

Existing `BaseResource`, `BaseService`, and `IMapper` remain compatibility
wrappers with their original generic arguments. The legacy service sets the
URL ID on the DTO and dispatches to the existing single-argument update method,
including endpoint-specific overrides. Existing validation, null handling,
status defaults, response envelopes, and profile selection are unchanged.
The blockchain endpoint tests exercise this complete compatibility path.

For migrated update requests, initialize an `UpdateField<T>` with `absent()`
and assign `UpdateField.supplied(value)` in a `@JsonSetter` for the ordinary
JSON property. Jackson invokes that setter for explicit null but does not invoke
it for omission. The mapper can call `ifSupplied(entity::setField)` to preserve
an omitted value and apply a supplied value, including null. Validate supplied
values according to the endpoint's rules before mapping. The wrapper is an
internal representation, not a new JSON object format; do not expose it directly
as the JSON property.

This is opt-in infrastructure. It does not impose partial-update semantics or
new field rules on existing endpoints. Migrate each consumer's DTOs, validation,
and mappings in its owning ticket. The independent-contract HTTP fixture in
`EndpointProfileTests` demonstrates omission, explicit null, and replacement
without migrating a production endpoint prematurely.
