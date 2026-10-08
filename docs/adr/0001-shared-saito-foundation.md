# Shared foundation for Saito features

The owner intends to add other Saito modules, such as Saito Stack, after building
Red Square and Chat. Keep wallet identity and backend connectivity reusable,
with distinct native feature boundaries for each Saito application, so a new
feature does not require rewriting that foundation.

Use the included Red Square and Chat features to establish and verify these
boundaries. The trade-off is some upfront boundary design in exchange for
avoiding coupling the shared infrastructure to one application. Concrete build
module boundaries and integration interfaces remain specification decisions.
