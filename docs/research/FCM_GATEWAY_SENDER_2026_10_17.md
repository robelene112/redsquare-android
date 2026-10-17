# FCM gateway sender observation, 2026-10-17

Functional API research for open[#42](https://github.com/robelene112/redsquare-android/issues/42),
supporting#32. Read the current[owned gateway contract](../PUSH_GATEWAY.md),
[October16 FID observation](FCM_ANDROID_API_2026_10_16.md) and
[October15 prerequisites](NATIVE_CLOSED_APP_PUSH_REQUIREMENTS_2026_10_15.md).
This note adds sender facts, not another Android client investigation. Only this
file was written. No credentials, accounts, IAM, Firebase project configuration,
live delivery, deployment, application source, tests or CI were touched.

## Observed HTTP contract

Send with `POST https://fcm.googleapis.com/v1/projects/{targetProjectId}/messages:send`,
JSON content type and `Authorization: Bearer <OAuth access token>`. The method
accepts a project ID or numeric project number. `validate_only` validates without
delivery. The required permission on that target project is
`cloudmessaging.messages.create`; either messaging or cloud-platform OAuth scope
is accepted. Use the narrower documented
`https://www.googleapis.com/auth/firebase.messaging` scope for this sender.
[REST method](https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages/send).

The independently read public[FCM discovery schema](https://fcm.googleapis.com/$discovery/rest?version=v1)
reports revision`20261013`. `Message.fid` targets a Firebase Installation ID;
`token` is deprecated and temporarily accepts FIDs. `data` values are strings.
`android.priority` has `NORMAL`/`HIGH` enum values; normal is the default for data
messages. `android.ttl` is a duration string ending in`s`, rounded down to whole
seconds, with four weeks maximum/default. `name` is output-only, shaped
`projects/*/messages/{message_id}`. `android.data` overrides top-level data, so
omit it here. These are schema observations, not a successful Google send.

**Proposed minimum request**, using the existing opaque-wake client contract:

```json
{
  "message": {
    "fid": "<current registered installation FID>",
    "data": {
      "version": "1",
      "wake": "<opaque 64 lowercase hexadecimal characters>"
    },
    "android": {
      "priority": "HIGH",
      "ttl": "3600s"
    }
  }
}
```

The example's one-hour TTL is illustrative, not an agreed retention policy.
Do not add a notification object, Android notification template, sender text,
wallet, backend URL or private content. The exact two data keys are the app
contract. This preserves local resolution, verification and publication controls.
Keep TTL no longer than the remaining resolver-record lifetime. Omit a collapse
key for individual event references unless later reconciliation explicitly
recovers references discarded by collapse. These are implementation inferences,
not additional Firebase guarantees.

High priority attempts prompt delivery, including a sleeping device, but is
intended for time-sensitive content that produces a visible notification.
Repeated invisible outcomes can cause deprioritization, evaluated separately
per app instance using seven days of behavior. A filtered or stale wake can
legitimately produce no alert, so local policy must still win. High priority
cannot promise delivery or successful follow-up retrieval.
[Android priority guidance](https://firebase.google.com/docs/cloud-messaging/android-message-priority).

## Acceptance, failures and retries

A returned message ID means FCM accepted the message for delivery, not that the
device received it or displayed anything. TTL ranges from0 to2,419,200seconds;
TTL0 discards messages that cannot be delivered immediately. A missing TTL uses
four weeks, which must not silently exceed the gateway reference's retention.
[Message lifetime](https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-lifespan).

| Response | Documented meaning and sender consequence |
| --- | --- |
| `400 INVALID_ARGUMENT` | Invalid request, possibly target, package, size, fields or TTL. Correct the request; do not assume every400 means an obsolete FID. |
| `404 UNREGISTERED` | App instance is no longer registered. Stop sending to that recorded target. |
| `403 SENDER_ID_MISMATCH` | Sender does not match the target's allowed sender. Configuration/association failure. |
| `429 QUOTA_EXCEEDED` | Rate quota exceeded. Slow down with exponential backoff; message/topic quota guidance specifies an initial delay of at least one minute. |
| `503 UNAVAILABLE` | Retry with exponential backoff and honor `Retry-After`; add jitter across sends. |
| `500 INTERNAL` | A server error, retry with backoff. |

Inspect structured error details rather than treating all HTTP failures as an
expired registration. The error guide distinguishes `google.rpc.BadRequest`,
FCM-specific errors and quota failures.
[FCM error codes](https://firebase.google.com/docs/cloud-messaging/error-codes).

The separate scale guide says to allow at least10seconds before a send timeout,
abort rather than retry400/401/403/404, honor429`Retry-After` or use60seconds if
missing, and use exponential backoff with jitter. It recommends at least10seconds
between failed attempts and a finite deadline for stale work. These conservative
minimums reconcile its guidance with the error table's shorter illustrative503
sequence. A generic404 is not by itself proof of `UNREGISTERED`.
[Retry guidance](https://firebase.google.com/docs/cloud-messaging/scale-fcm#handling_retries).

**Idempotency inference:** the observed send-request schema exposes no application
idempotency key and the message name is output-only. A lost response leaves the
outcome uncertain. Retry the same retained opaque wake, not a newly allocated
logical event, and deduplicate on the device through the existing verified
receiver. Persist the event/send outcome before advancing an observer cursor.
Do not describe FCM sends as exactly-once or delivery receipts. An obsolete-target
response must be applied conditionally to the exact target/binding generation
that was sent, so it cannot erase a later renewed FID.

## JVM17 authentication dependency and inputs

The current[GoogleCredentials reference](https://docs.cloud.google.com/java/docs/reference/google-auth-library/latest/com.google.auth.oauth2.GoogleCredentials)
is1.54.0. `getApplicationDefault()` searches environment-selected credential
configuration, local Cloud SDK credentials and supported hosted identities.
`createScoped(...)` returns appropriately scoped credentials where supported.
`getProjectId()` can be null, and a credential's project is not necessarily the
target project. Therefore supply the target project explicitly. No ADC lookup
was performed during this investigation.

For a plain JVM HTTP sender,
`GoogleCredentials.getApplicationDefault().createScoped(listOf(messagingScope))`
is the supported API shape. Obtain current authorization with
`getRequestMetadata(sendUri)`, which ensures a current access token and returns
bearer metadata. Alternatively `refreshIfExpired()` precedes reading the cached
access token. Do not merely cache the initial token indefinitely.
[OAuth2Credentials reference](https://docs.cloud.google.com/java/docs/reference/google-auth-library/latest/com.google.auth.oauth2.OAuth2Credentials).

Public[Maven metadata](https://repo.maven.apache.org/maven2/com/google/auth/google-auth-library-oauth2-http/maven-metadata.xml)
reports latest/release`1.54.0`, lastUpdated`20260930213139`.
The[1.54.0 published POM](https://repo.maven.apache.org/maven2/com/google/auth/google-auth-library-oauth2-http/1.54.0/google-auth-library-oauth2-http-1.54.0.pom)
confirms `com.google.auth:google-auth-library-oauth2-http:1.54.0`, inheriting
`google-auth-library-parent:1.54.0`. This is a candidate dependency, not a resolved
or compiled project change. Kotlin/JVM17 can call these Java APIs; Google's
[Java support policy](https://docs.cloud.google.com/java/docs/supported-java-versions)
explicitly includes Java17.

The[old authentication repository](https://github.com/googleapis/google-auth-library-java)
is archived and says its content/history moved to
[google-cloud-java/google-auth-library-java](https://github.com/googleapis/google-cloud-java/tree/main/google-auth-library-java).
Its old latest release page is therefore not the current version authority.

Minimum external inputs for a real sender are an authorized **target Firebase
project ID**, FCM API availability, and an externally supplied **ADC identity**
with the method's permission and OAuth scope. A current **registered FID** must
come from the corresponding configured app/project. Android public options and
an API key do not replace server OAuth credentials. A different sender project
is supported if its identity has the target project's permission; do not assume
the credential project is the send destination.
[FCM authorization](https://firebase.google.com/docs/cloud-messaging/send/v1-api).

These observations do not establish that David has supplied those inputs or
that this VPS can authenticate. Keep the existing owner setup question pending.
No account creation, key download, IAM change, credential inspection or live
message send is implied. Owned HTTP fixtures can verify the outgoing FID,
payload, bounded response handling and retry decisions before live setup, but
cannot prove actual cloud delivery, lock/Doze behavior or Android acceptance.
