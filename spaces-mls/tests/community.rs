use saito_spaces_mls::exchange;
use serde_json::{json, Value};
use base64::{engine::general_purpose::STANDARD, Engine};

#[test]
fn confirmed_rejection_recovers_the_pending_group_before_a_different_admission() {
    let owner = call(None, json!({"op":"new", "identity":"owner"}));
    let member = call(None, json!({"op":"new", "identity":"existing"}));
    let rejected = call(None, json!({"op":"new", "identity":"not-admitted"}));
    let later = call(None, json!({"op":"new", "identity":"admitted-later"}));
    let owner = call(Some(&owner), json!({"op":"create", "group":"rejection/channel"}));
    let owner = call(Some(&owner), json!({"op":"add", "key_package":member["key_package"]}));
    let member = call(Some(&member), json!({"op":"join", "wire":owner["welcome"]}));
    let attempt = call(Some(&owner), json!({"op":"add", "key_package":rejected["key_package"]}));
    let rejected = call(Some(&rejected), json!({"op":"join", "wire":attempt["welcome"]}));
    // Only a known rejection permits cancellation. The serialized pending
    // state keeps library-owned state instead of rewinding an emitted sender.
    let pending = json!({"state":attempt["pending_state"]});
    let retry = call(Some(&pending), json!({"op":"cancel"}));
    assert_eq!(retry["epoch"], 1);
    assert_eq!(retry["members"], json!(["owner", "existing"]));
    let accepted = call(Some(&retry), json!({"op":"add", "key_package":later["key_package"]}));
    let member = call(Some(&member), json!({"op":"receive", "wire":accepted["wire"]}));
    let later = call(Some(&later), json!({"op":"join", "wire":accepted["welcome"]}));
    let sent = call(Some(&accepted), json!({"op":"send", "text":"Only the accepted membership"}));
    assert_eq!(call(Some(&member), json!({"op":"receive", "wire":sent["wire"]}))["text"], "Only the accepted membership");
    assert_eq!(call(Some(&later), json!({"op":"receive", "wire":sent["wire"]}))["text"], "Only the accepted membership");
    assert!(exchange(&json!({"op":"receive", "state":rejected["state"], "wire":sent["wire"]}).to_string()).is_err());
}

/// A fresh serialized state crosses the public interface on every operation.
/// No provider, signer, group or storage implementation is mocked.
fn call(state: Option<&Value>, mut request: Value) -> Value {
    if let Some(state) = state {
        request["state"] = state["state"].clone();
    }
    serde_json::from_str(&exchange(&request.to_string()).expect("MLS operation succeeds"))
        .expect("versioned response")
}

#[test]
fn admitted_independent_identity_reads_encrypted_content_after_serialized_state_reload() {
    let alice = call(None, json!({"op":"new", "identity":"SaitoSpaces/v1:owner:device-a"}));
    let bob = call(None, json!({"op":"new", "identity":"SaitoSpaces/v1:member:device-b"}));
    let alice = call(Some(&alice), json!({"op":"create", "group":"community/general"}));
    let added = call(Some(&alice), json!({"op":"add", "key_package":bob["key_package"]}));
    let bob = call(Some(&bob), json!({"op":"join", "wire":added["welcome"]}));
    let sent = call(Some(&added), json!({"op":"send", "text":"A real private community"}));
    let received = call(Some(&bob), json!({"op":"receive", "wire":sent["wire"]}));
    assert_eq!(received["text"], "A real private community");
    assert_eq!(received["sender"], "SaitoSpaces/v1:owner:device-a");
    assert_eq!(received["members"].as_array().unwrap().len(), 2);
    assert!(received["archive_key"] == sent["archive_key"], "Members derive the same archive key");
    assert_eq!(received["epoch"], 1);
    let wire = STANDARD.decode(sent["wire"].as_str().unwrap()).unwrap();
    assert!(!wire.windows(24).any(|part| part == b"A real private community"));
}

#[test]
fn removed_identity_cannot_read_future_content_that_the_remaining_member_reads() {
    let owner = call(None, json!({"op":"new", "identity":"owner"}));
    let old = call(None, json!({"op":"new", "identity":"departing"}));
    let remaining = call(None, json!({"op":"new", "identity":"remaining"}));
    let owner = call(Some(&owner), json!({"op":"create", "group":"private/channel"}));
    let owner = call(Some(&owner), json!({"op":"add", "key_package":old["key_package"]}));
    let old = call(Some(&old), json!({"op":"join", "wire":owner["welcome"]}));
    let owner = call(Some(&owner), json!({"op":"add", "key_package":remaining["key_package"]}));
    let old = call(Some(&old), json!({"op":"receive", "wire":owner["wire"]}));
    let remaining = call(Some(&remaining), json!({"op":"join", "wire":owner["welcome"]}));
    let owner = call(Some(&owner), json!({"op":"remove", "identity":"departing"}));
    let remaining = call(Some(&remaining), json!({"op":"receive", "wire":owner["wire"]}));
    let sent = call(Some(&owner), json!({"op":"send", "text":"After removal"}));
    let received = call(Some(&remaining), json!({"op":"receive", "wire":sent["wire"]}));
    assert_eq!(received["text"], "After removal");
    assert_eq!(received["members"], json!(["owner", "remaining"]));
    assert!(received["archive_key"] == sent["archive_key"], "Remaining member gets the new epoch key");
    assert!(old["archive_key"] != sent["archive_key"], "Old archive key excludes the new epoch");
    let denied = exchange(&json!({"op":"receive", "state":old["state"], "wire":sent["wire"]}).to_string());
    assert!(denied.is_err(), "Old identity cannot decrypt actual subsequent ciphertext");
}

#[test]
fn authenticated_group_exposes_the_actual_public_signer_for_each_bound_credential() {
    let owner = call(None, json!({"op":"new", "identity":"wallet-a/device-a"}));
    let member = call(None, json!({"op":"new", "identity":"wallet-b/device-b"}));
    assert!(owner["signer"].as_str().is_some(), "Expose the public signer without reading secret snapshot internals");
    let group = call(Some(&owner), json!({"op":"create", "group":"bound/channel"}));
    let added = call(Some(&group), json!({"op":"add", "key_package":member["key_package"]}));
    let joined = call(Some(&member), json!({"op":"join", "wire":added["welcome"]}));
    assert_eq!(joined["member_keys"]["wallet-a/device-a"], owner["signer"]);
    assert_eq!(joined["member_keys"]["wallet-b/device-b"], member["signer"]);
    assert_eq!(joined["member_keys"], added["member_keys"]);
}
