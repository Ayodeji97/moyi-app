package com.moyi.app.api.wire

import com.moyi.app.api.models.EntryResponse
import com.moyi.app.api.models.LockedEntryResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * `TodayResponse.partnerEntry` and `DayResponse.partnerEntry`: the contract's
 * `oneOf` of two GENERATED shapes (or null), told apart by `status`.
 *
 * **Why this one hand-written file exists** (docs/backend-contract-gaps.md, row 10):
 * OpenAPI Generator 7.26 emits the `oneOf` as a sealed class with no subclasses,
 * so every non-null `partnerEntry` throws on decode, and kotlinx polymorphism
 * cannot send several discriminator values to one class either. `build.gradle.kts`
 * maps the inline schema here (`schemaMappings`), and this type only *chooses*
 * between the generated models. It declares no field of its own: every field the
 * app reads is still a generated one, so it is a dispatcher, not a DTO.
 *
 * The mapping is the contract's `discriminator.mapping`, exactly:
 * `SUBMITTED`, `REVEALED`, `DELETED` to [EntryResponse], `LOCKED` to
 * [LockedEntryResponse]. Any other value is a
 * [SerializationException], so it surfaces as a body that did not parse, never a
 * guess about what an unknown shape may show.
 *
 * Delete this file when the backend gives each variant its own schema (row 10's
 * suggested fix) and the generator can produce real subclasses.
 */
@Serializable(with = PartnerEntryWireSerializer::class)
sealed interface PartnerEntryWire {
    /** The entry read in full, or its tombstone (`DELETED`, no text). */
    data class Entry(val entry: EntryResponse) : PartnerEntryWire

    /** Not revealed yet: the author and nothing else (BR-8). */
    data class Locked(val locked: LockedEntryResponse) : PartnerEntryWire
}

internal object PartnerEntryWireSerializer : KSerializer<PartnerEntryWire> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.moyi.app.api.wire.PartnerEntryWire")

    override fun deserialize(decoder: Decoder): PartnerEntryWire {
        val json = decoder as? JsonDecoder ?: throw SerializationException("partnerEntry is JSON only")
        val element = json.decodeJsonElement() as? JsonObject ?: throw SerializationException("partnerEntry is not an object")
        return when (val status = element["status"]?.jsonPrimitive?.contentOrNull) {
            "SUBMITTED", "REVEALED", "DELETED" ->
                PartnerEntryWire.Entry(json.json.decodeFromJsonElement(EntryResponse.serializer(), element))
            "LOCKED" -> PartnerEntryWire.Locked(json.json.decodeFromJsonElement(LockedEntryResponse.serializer(), element))
            // Never echo the value: a body is not a log line, but nor is it ours to repeat.
            else -> throw SerializationException("partnerEntry has an unknown status${if (status == null) " (none)" else ""}")
        }
    }

    override fun serialize(encoder: Encoder, value: PartnerEntryWire) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("partnerEntry is JSON only")
        val element = when (value) {
            is PartnerEntryWire.Entry -> json.json.encodeToJsonElement(EntryResponse.serializer(), value.entry)
            is PartnerEntryWire.Locked -> json.json.encodeToJsonElement(LockedEntryResponse.serializer(), value.locked)
        }
        json.encodeJsonElement(element)
    }
}
