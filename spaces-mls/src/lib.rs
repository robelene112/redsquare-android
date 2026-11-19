//! The owned MLS interface shared by the Android and host clients.
//! State is secret and must be durably encrypted by the caller before transmission.

use base64::{engine::general_purpose::STANDARD, Engine};
use openmls::prelude::*;
use openmls::prelude::tls_codec::{Deserialize as TlsDeserialize, Serialize as TlsSerialize};
use openmls_basic_credential::SignatureKeyPair;
use openmls_rust_crypto::OpenMlsRustCrypto;
use openmls_traits::OpenMlsProvider;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

const SUITE: Ciphersuite = Ciphersuite::MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519;
const MAX_INPUT: usize = 8 * 1024 * 1024;

// Self-describing, versioned storage, as required by OpenMLS0.9. This envelope
// never goes to the network. Android owns the authenticated at-rest transaction.
#[derive(Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Snapshot {
    version: u8,
    library: String,
    identity: String,
    signer: String,
    group: Option<String>,
    values: Vec<(String, String)>,
}

struct Client {
    provider: OpenMlsRustCrypto,
    signer: SignatureKeyPair,
    identity: String,
    group: Option<MlsGroup>,
}

fn field<'a>(value: &'a Value, name: &str) -> Result<&'a str, &'static str> {
    value.get(name).and_then(Value::as_str).ok_or("Invalid MLS input")
}

fn decode(value: &str) -> Result<Vec<u8>, &'static str> {
    STANDARD.decode(value).map_err(|_| "Invalid MLS encoding")
}

impl Client {
    fn new(identity: &str) -> Result<Self, &'static str> {
        if identity.is_empty() || identity.len() > 256 || identity.chars().any(char::is_control) {
            return Err("Invalid MLS identity");
        }
        let provider = OpenMlsRustCrypto::default();
        let signer = SignatureKeyPair::new(SUITE.signature_algorithm()).map_err(|_| "MLS keys unavailable")?;
        signer.store(provider.storage()).map_err(|_| "MLS keys unavailable")?;
        Ok(Self { provider, signer, identity: identity.to_owned(), group: None })
    }

    fn credential(&self) -> CredentialWithKey {
        CredentialWithKey {
            credential: BasicCredential::new(self.identity.as_bytes().to_vec()).into(),
            signature_key: self.signer.to_public_vec().into(),
        }
    }

    fn restore(encoded: &str) -> Result<Self, &'static str> {
        let snapshot: Snapshot = serde_json::from_slice(&decode(encoded)?).map_err(|_| "MLS state needs recovery")?;
        if snapshot.version != 1 || snapshot.library != "openmls-0.9.0" || snapshot.values.len() > 100_000 {
            return Err("Unsupported MLS storage version");
        }
        let provider = OpenMlsRustCrypto::default();
        {
            let mut values = provider.storage().values.write().map_err(|_| "MLS storage unavailable")?;
            for (key, value) in snapshot.values {
                if values.insert(decode(&key)?, decode(&value)?).is_some() {
                    return Err("Invalid duplicate MLS storage key");
                }
            }
        }
        let signer = SignatureKeyPair::read(provider.storage(), &decode(&snapshot.signer)?, SUITE.signature_algorithm())
            .ok_or("MLS signing key needs recovery")?;
        let group = snapshot.group.map(|id| {
            MlsGroup::load(provider.storage(), &GroupId::from_slice(&decode(&id)?))
                .map_err(|_| "MLS group needs recovery")?.ok_or("MLS group needs recovery")
        }).transpose()?;
        if let Some(group) = &group {
            let own = group.own_leaf_node().ok_or("MLS membership unavailable")?;
            if own.credential().serialized_content() != snapshot.identity.as_bytes()
                || own.signature_key().as_slice() != signer.public()
            {
                return Err("MLS identity does not match stored membership");
            }
        }
        Ok(Self { provider, signer, identity: snapshot.identity, group })
    }

    fn snapshot(&self) -> Result<String, &'static str> {
        let values = self.provider.storage().values.read().map_err(|_| "MLS storage unavailable")?;
        let mut values: Vec<_> = values.iter().map(|(key, value)| (STANDARD.encode(key), STANDARD.encode(value))).collect();
        values.sort_unstable_by(|a, b| a.0.cmp(&b.0));
        let snapshot = Snapshot {
            version: 1, library: "openmls-0.9.0".into(), identity: self.identity.clone(),
            signer: STANDARD.encode(self.signer.public()),
            group: self.group.as_ref().map(|g| STANDARD.encode(g.group_id().as_slice())), values,
        };
        let bytes = serde_json::to_vec(&snapshot).map_err(|_| "MLS storage unavailable")?;
        if bytes.len() > MAX_INPUT / 2 { return Err("MLS storage capacity reached"); }
        Ok(STANDARD.encode(bytes))
    }
}

/// One serialized input, one isolated operation and one replacement state.
/// The caller commits state plus its original outbound wire atomically before
/// transmission. A failed call supplies no replacement state or send permission.
pub fn exchange(request: &str) -> Result<String, &'static str> {
    if request.len() > MAX_INPUT { return Err("MLS input too large"); }
    let request: Value = serde_json::from_str(request).map_err(|_| "Invalid MLS input")?;
    let op = field(&request, "op")?;
    let mut client = if op == "new" { Client::new(field(&request, "identity")?)? }
        else { Client::restore(field(&request, "state")?)? };
    let mut response = json!({"version":1, "identity":client.identity, "signer":STANDARD.encode(client.signer.public())});
    match op {
        "status" => {},
        "new" => {
            let package = KeyPackage::builder().build(SUITE, &client.provider, &client.signer, client.credential())
                .map_err(|_| "MLS key package unavailable")?;
            response["key_package"] = STANDARD.encode(package.key_package().tls_serialize_detached()
                .map_err(|_| "MLS encoding failed")?).into();
        }
        "create" => {
            if client.group.is_some() { return Err("MLS group already exists"); }
            let id = field(&request, "group")?;
            if id.is_empty() || id.len() > 256 { return Err("Invalid MLS group"); }
            let config = MlsGroupCreateConfig::builder().ciphersuite(SUITE).use_ratchet_tree_extension(true).build();
            client.group = Some(MlsGroup::new_with_group_id(&client.provider, &client.signer, &config,
                GroupId::from_slice(id.as_bytes()), client.credential()).map_err(|_| "MLS creation failed")?);
        }
        "add" => {
            let package = KeyPackageIn::tls_deserialize_exact(decode(field(&request, "key_package")?)?)
                .map_err(|_| "Invalid MLS key package")?
                .validate(client.provider.crypto(), ProtocolVersion::Mls10).map_err(|_| "Invalid MLS key package")?;
            // RFC9420 requires the application to bound KeyPackage validity,
            // in addition to checking that it is valid at the current time.
            if !package.life_time().has_acceptable_range() {
                return Err("MLS key package lifetime exceeds the supported range");
            }
            let group = client.group.as_mut().ok_or("MLS group unavailable")?;
            let (commit, welcome, _) = group.add_members(&client.provider, &client.signer, &[package])
                .map_err(|_| "MLS admission failed")?;
            response["wire"] = STANDARD.encode(commit.tls_serialize_detached().map_err(|_| "MLS encoding failed")?).into();
            response["welcome"] = STANDARD.encode(welcome.tls_serialize_detached().map_err(|_| "MLS encoding failed")?).into();
            group.merge_pending_commit(&client.provider).map_err(|_| "MLS admission failed")?;
        }
        "remove" => {
            let identity = field(&request, "identity")?;
            let group = client.group.as_mut().ok_or("MLS group unavailable")?;
            let targets: Vec<_> = group.members().filter(|m| m.credential.serialized_content() == identity.as_bytes())
                .map(|m| m.index).collect();
            if targets.len() != 1 || targets[0] == group.own_leaf_index() { return Err("Invalid MLS removal"); }
            let (commit, _, _) = group.remove_members(&client.provider, &client.signer, &targets)
                .map_err(|_| "MLS removal failed")?;
            response["wire"] = STANDARD.encode(commit.tls_serialize_detached().map_err(|_| "MLS encoding failed")?).into();
            group.merge_pending_commit(&client.provider).map_err(|_| "MLS removal failed")?;
        }
        "join" => {
            if client.group.is_some() { return Err("MLS group already exists"); }
            let message = MlsMessageIn::tls_deserialize_exact(decode(field(&request, "wire")?)?)
                .map_err(|_| "Invalid MLS welcome")?;
            let MlsMessageBodyIn::Welcome(welcome) = message.extract() else { return Err("Invalid MLS welcome"); };
            let config = MlsGroupJoinConfig::builder().use_ratchet_tree_extension(true).build();
            client.group = Some(StagedWelcome::new_from_welcome(&client.provider, &config, welcome, None)
                .map_err(|_| "MLS admission unavailable")?.into_group(&client.provider)
                .map_err(|_| "MLS admission unavailable")?);
        }
        "send" => {
            let text = field(&request, "text")?;
            if text.is_empty() || text.len() > 64 * 1024 { return Err("Invalid MLS content length"); }
            let group = client.group.as_mut().ok_or("MLS group unavailable")?;
            let message = group.create_message(&client.provider, &client.signer, text.as_bytes())
                .map_err(|_| "MLS message unavailable")?;
            response["wire"] = STANDARD.encode(message.tls_serialize_detached().map_err(|_| "MLS encoding failed")?).into();
        }
        "receive" => {
            let message = MlsMessageIn::tls_deserialize_exact(decode(field(&request, "wire")?)?)
                .map_err(|_| "Invalid MLS message")?.try_into_protocol_message().map_err(|_| "Invalid MLS message")?;
            let group = client.group.as_mut().ok_or("MLS group unavailable")?;
            let processed = group.process_message(&client.provider, message).map_err(|_| "MLS message unavailable")?;
            response["sender"] = std::str::from_utf8(processed.credential().serialized_content())
                .map_err(|_| "Invalid MLS sender")?.into();
            match processed.into_content() {
                ProcessedMessageContent::ApplicationMessage(message) => {
                    response["text"] = String::from_utf8(message.into_bytes()).map_err(|_| "Invalid MLS content")?.into();
                }
                ProcessedMessageContent::StagedCommitMessage(commit) => {
                    group.merge_staged_commit(&client.provider, *commit).map_err(|_| "MLS commit unavailable")?;
                }
                _ => return Err("Unsupported MLS message kind"),
            }
        }
        _ => return Err("Unsupported MLS operation"),
    }
    if let Some(group) = &client.group {
        response["group"] = std::str::from_utf8(group.group_id().as_slice()).map_err(|_| "Invalid MLS group")?.into();
        response["epoch"] = group.epoch().as_u64().into();
        response["members"] = group.members().map(|m| String::from_utf8(m.credential.serialized_content().to_vec())
            .map(Value::String).map_err(|_| "Invalid MLS member")).collect::<Result<Vec<_>, _>>()?.into();
        let mut member_keys = serde_json::Map::new();
        for member in group.members() {
            let identity = String::from_utf8(member.credential.serialized_content().to_vec()).map_err(|_| "Invalid MLS member")?;
            if member_keys.insert(identity, STANDARD.encode(member.signature_key).into()).is_some() {
                return Err("Ambiguous MLS member identity");
            }
        }
        response["member_keys"] = member_keys.into();
        response["archive_key"] = STANDARD.encode(group.export_secret(client.provider.crypto(),
            "SaitoSpacesArchiveV1", group.group_id().as_slice(), 32).map_err(|_| "MLS archive keys unavailable")?).into();
    }
    response["state"] = client.snapshot()?.into();
    serde_json::to_string(&response).map_err(|_| "MLS response unavailable")
}

/// JNI retains no process-global session. A native failure cannot return a
/// fabricated new state. Exception messages contain static codes only.
#[no_mangle]
pub extern "system" fn Java_nl_davidengelen_redsquare_spaces_NativeSpaceMls_exchange(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    request: jni::objects::JString,
) -> jni::sys::jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let request: String = env.get_string(&request).map_err(|_| "MLS input unavailable")?.into();
        exchange(&request)
    }));
    match result {
        Ok(Ok(response)) => match env.new_string(response) {
            Ok(value) => value.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        failure => {
            let message = match failure { Ok(Err(code)) => code, _ => "MLS operation unavailable" };
            if !env.exception_check().unwrap_or(true) {
                let _ = env.throw_new("java/lang/IllegalStateException", message);
            }
            std::ptr::null_mut()
        }
    }
}
