package com.wingmanc2.core.algorithms.nlp

import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.ParsedQuery
import com.wingmanc2.core.model.QueryPredicate
import com.wingmanc2.core.model.QueryPredicate.AffiliationIs
import com.wingmanc2.core.model.QueryPredicate.KindIs
import com.wingmanc2.core.model.QueryPredicate.Near
import com.wingmanc2.core.model.QueryPredicate.SeenWithin
import com.wingmanc2.core.model.QueryPredicate.Text
import com.wingmanc2.core.model.QueryPredicate.VerifiedIs
import javax.inject.Inject
import kotlin.math.ceil

class QueryParser @Inject constructor() {

    fun parse(raw: String): ParsedQuery {
        val stream = TokenStream(Lexer.tokenize(raw))
        val predicates = LinkedHashSet<QueryPredicate>()
        while (stream.hasNext) {
            val token = stream.next()
            val predicate = when (token) {
                in PROXIMITY -> stream.anchor()?.let { Near(it, DEFAULT_RADIUS_M) }
                "within", Lexer.LESS_THAN -> stream.bounded()
                in LOOKBACK -> stream.lookback()
                in RECENCY -> SeenWithin(DEFAULT_RECENT_MIN)
                in NEGATIONS -> stream.negated()
                in SCOPES -> stream.skipScopeLabel()
                in STOPWORDS -> null
                else -> VOCABULARY[token] ?: textTerm(token)
            }
            if (predicate != null) predicates += predicate
        }
        return ParsedQuery(raw, predicates.sortedBy(::rank))
    }

    private fun TokenStream.bounded(): QueryPredicate? {
        val quantity = quantity() ?: return null
        if (skip("of") || skip("from") || skip("to")) {
            val meters = quantity.meters ?: return null
            return anchor()?.let { Near(it, meters) }
        }
        return quantity.minutes?.let(::seenWithin)
    }

    private fun TokenStream.lookback(): QueryPredicate? {
        quantity()?.let { return it.minutes?.let(::seenWithin) }
        val unit = peek() ?: return null
        val minutes = Quantity(1.0, unit).minutes?.takeIf { unit.length > 1 } ?: return null
        next()
        return seenWithin(minutes)
    }

    private fun TokenStream.negated(): QueryPredicate? {
        val status = VOCABULARY[peek() ?: return null] as? VerifiedIs ?: return null
        next()
        return VerifiedIs(!status.verified)
    }

    private fun TokenStream.skipScopeLabel(): QueryPredicate? {
        peek()?.takeIf { it.first().isDigit() || EntityKeys.isKeySuffix(it) }?.let { next() }
        return null
    }

    private fun seenWithin(minutes: Double) = SeenWithin(ceil(minutes).toInt().coerceAtLeast(1))

    private fun textTerm(token: String): QueryPredicate? = when {
        EntityKeys.looksLikeKey(token) -> Text(EntityKeys.normalize(token))
        token.length < 2 || token.first().isDigit() -> null
        else -> Text(token)
    }

    private fun rank(predicate: QueryPredicate): Int = when (predicate) {
        is KindIs -> 0
        is AffiliationIs -> 1
        is VerifiedIs -> 2
        is Near -> 3
        is SeenWithin -> 4
        is Text -> 5
    }

    private companion object {
        const val DEFAULT_RADIUS_M = 500.0
        const val DEFAULT_RECENT_MIN = 15

        val PROXIMITY = setOf("near", "around", "nearby", "by", "close", "beside", "at")
        val LOOKBACK = setOf("last", "past", "previous", "prior")
        val RECENCY = setOf("recent", "recently", "fresh", "new", "latest")
        val NEGATIONS = setOf("not", "non", "never")
        val SCOPES = setOf("sector", "area", "ao", "aoi", "grid", "box")
        val STOPWORDS = setOf(
            "show", "me", "all", "any", "the", "a", "an", "find", "list", "get", "display", "give", "with", "and", "or",
            "of", "in", "on", "for", "to", "is", "are", "that", "which", "what", "where", "who", "there", "from", "please",
            "entities", "entity", "items", "everything", "ago", "seen", "detected", "spotted", "reported", "only", "currently",
        )

        val VOCABULARY: Map<String, QueryPredicate> = buildMap {
            fun bind(predicate: QueryPredicate, vararg words: String) = words.forEach { put(it, predicate) }
            bind(KindIs(EntityKind.EMITTER), "emitter", "emitters", "radar", "radars", "rf", "transmitter", "transmitters", "jammer", "jammers", "signal", "signals")
            bind(KindIs(EntityKind.TRACK), "track", "tracks", "contact", "contacts", "target", "targets", "vehicle", "vehicles")
            bind(KindIs(EntityKind.REPORT), "report", "reports", "spot", "spots", "spotrep", "spotreps", "sighting", "sightings", "observation", "observations")
            bind(KindIs(EntityKind.ZONE), "zone", "zones", "nfz", "nfzs", "no-fly", "geofence", "geofences", "restricted")
            bind(KindIs(EntityKind.SENSOR), "sensor", "sensors", "receiver", "receivers", "rx")
            bind(KindIs(EntityKind.ASSET), "asset", "assets", "uas", "uav", "uavs", "drone", "drones", "wingman", "wingmen")
            bind(KindIs(EntityKind.CLASS), "class", "classes", "category", "categories")
            bind(AffiliationIs(Affiliation.HOSTILE), "hostile", "hostiles", "enemy", "enemies", "red", "threat", "threats", "adversary", "opfor")
            bind(AffiliationIs(Affiliation.FRIEND), "friendly", "friendlies", "friend", "friends", "blue", "own", "ours", "blufor")
            bind(AffiliationIs(Affiliation.UNKNOWN), "unknown", "unknowns", "unidentified", "unid")
            bind(AffiliationIs(Affiliation.NEUTRAL), "neutral", "neutrals", "civilian", "civilians", "civ", "white")
            bind(VerifiedIs(false), "unverified", "unconfirmed", "suspect", "suspected", "unvalidated", "uncorroborated", "tentative")
            bind(VerifiedIs(true), "verified", "confirmed", "validated", "corroborated")
        }
    }
}
