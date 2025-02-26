package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

interface ToValue {
    fun toValue(): Any
}

interface Expression : SearchQueryCompiler.Visitable {
    fun children(): Iterator<Expression>? {
        return null
    }

    fun reduce(): Expression? {
        return this
    }
}

internal inline fun Expression.collect(process: (Expression) -> Unit) {
    val stack = ArrayList<Expression>()
    stack.add(this)

    while (true) {
        val currentExpression = stack.removeLastOrNull() ?: break
        process(currentExpression)

        val children = currentExpression.children()
        if (children != null) {
            for (child in children) {
                stack.add(child)
            }
        }
    }
}

interface NamedExpression : Expression {
    val name: String

    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.obj(name) {
            visit(this, compiler)
        }
    }

    fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler)
}

interface QueryExpression : NamedExpression {
    override fun reduce(): QueryExpression? {
        return this
    }
}

@Suppress("UNUSED")
data class NodeHandle<T: QueryExpressionNode<T>>(val name: String? = null)

abstract class QueryExpressionNode<T: QueryExpressionNode<T>>(
    val handle: NodeHandle<T>,
) : QueryExpression {
    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        toQueryExpression().visit(ctx, compiler)
    }

    override fun reduce(): QueryExpression? {
        return toQueryExpression().reduce()
    }

    abstract fun toQueryExpression(): QueryExpression
}

class Term(
    val field: Named,
    val term: Any,
    val boost: Double? = null,
) : QueryExpression {
    override val name = "term"

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        if (boost != null) {
            ctx.obj(field.getQualifiedFieldName()) {
                field("value", term)
                field("boost", boost)
            }
        } else {
            ctx.field(field.getQualifiedFieldName(), term)
        }
    }
}

class Terms(
    val field: Named,
    val terms: List<Any>,
    val boost: Double? = null,
) : QueryExpression {
    override val name = "terms"

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.array(field.getQualifiedFieldName()) {
            compiler.visit(this, terms)
        }
        if (boost != null) {
            ctx.field("boost", boost)
        }
    }
}

class Exists(
    val field: Named,
) : QueryExpression {
    override val name = "exists"

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.field("field", field.getQualifiedFieldName())
    }
}

class Range(
    val field: Named,
    val gt: Any? = null,
    val gte: Any? = null,
    val lt: Any? = null,
    val lte: Any? = null,
) : QueryExpression {
    override val name = "range"

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.obj(field.getQualifiedFieldName()) {
            if (gt != null) {
                field("gt", gt)
            }
            if (gte != null) {
                field("gte", gte)
            }
            if (lt != null) {
                field("lt", lt)
            }
            if (lte != null) {
                field("lte", lte)
            }
        }
    }
}

class Match(
    val field: Named,
    val query: String,
    val analyzer: String? = null,
    val minimumShouldMatch: Any? = null,
    val params: Params? = null,
) : QueryExpression {
    override val name = "match"

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        val params = Params(
            params,
            "analyzer" to analyzer,
            "minimum_should_match" to minimumShouldMatch,
        )
        if (params.isEmpty()) {
            ctx.field(field.getQualifiedFieldName(), query)
        } else {
            ctx.obj(field.getQualifiedFieldName()) {
                compiler.visit(this, params)
            }
        }
    }
}

class MatchAll : QueryExpression {
    override val name = "match_all"

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {}
}

class MultiMatch(
    val query: String,
    val fields: List<Named>,
    val type: Type? = null,
    val boost: Double? = null,
    val params: Params? = null,
) : QueryExpression {
    override val name = "multi_match"

    enum class Type : ToValue {
        BEST_FIELDS, MOST_FIELDS, CROSS_FIELDS, PHRASE, PHRASE_PREFIX;

        override fun toValue(): Any {
            return name.toLowerCase()
        }
    }

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.field("query", query)
        ctx.array("fields") {
            compiler.visit(this, fields.map(Named::getQualifiedFieldName))
        }
        ctx.fieldIfNotNull("type", type?.toValue())
        ctx.fieldIfNotNull("boost", boost)
        if (!params.isNullOrEmpty()) {
            compiler.visit(ctx, params)
        }
    }
}

class Ids(
    val values: List<String>,
) : QueryExpression {
    override val name = "ids"

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.array("values") {
            compiler.visit(this, values)
        }
    }
}

class Nested(
    val path: Named,
    val query: QueryExpression,
    val scoreMode: ScoreMode? = null,
    val ignoreUnmapped: Boolean? = null,
) : QueryExpression {
    override val name = "nested"

    enum class ScoreMode : ToValue {
        AVG, MAX, MIN, NONE, SUM;

        override fun toValue() = name.toLowerCase()
    }

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.field("path", path.getQualifiedFieldName())
        ctx.obj("query") {
            compiler.visit(this, query)
        }
        ctx.fieldIfNotNull("score_mode", scoreMode?.toValue())
        ctx.fieldIfNotNull("ignore_unmapped", ignoreUnmapped)
    }
}

class Script(
    val spec: Spec,
    val lang: String? = null,
    val params: Params = Params(),
) : NamedExpression {
    override val name: String = "script"

    constructor(
        source: String? = null,
        id: String? = null,
        lang: String? = null,
        params: Params = Params(),
    ) : this(Spec(source, id), lang, params)

    sealed class Spec : Expression {
        class Source(val source: String) : Spec(), Expression {
            override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
                ctx.field("source", source)
            }
        }

        class Id(val id: String) : Spec(), Expression {
            override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
                ctx.field("id", id)
            }
        }

        companion object {
            internal operator fun invoke(source: String?, id: String?): Spec {
                return when {
                    source == null && id == null -> {
                        throw IllegalArgumentException(
                            "Both source and id are missing"
                        )
                    }
                    source != null && id != null -> {
                        throw IllegalArgumentException(
                            "Only source or id allowed, not both"
                        )
                    }
                    source != null -> Source(source)
                    id != null -> Id(id)
                    else -> {
                        error("Unreachable")
                    }
                }
            }
        }
    }

    companion object {
        fun Source(source: String): Spec.Source = Spec.Source(source)
        fun Id(id: String): Spec.Id = Spec.Id(id)
    }

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        when (spec) {
            is Spec.Source -> ctx.field("source", spec.source)
            is Spec.Id -> ctx.field("id", spec.id)
        }
        if (lang != null) {
            ctx.field("lang", lang)
        }
        if (params.isNotEmpty()) {
            ctx.obj("params") {
                compiler.visit(this, params)
            }
        }
    }
}

interface BoolExpression : QueryExpression {
    val filter: List<QueryExpression>
    val should: List<QueryExpression>
    val must: List<QueryExpression>
    val mustNot: List<QueryExpression>

    override fun children(): Iterator<QueryExpression> = iterator {
        yieldAll(filter)
        yieldAll(should)
        yieldAll(must)
        yieldAll(mustNot)
    }
}

class Bool(
    override val filter: List<QueryExpression> = emptyList(),
    override val should: List<QueryExpression> = emptyList(),
    override val must: List<QueryExpression> = emptyList(),
    override val mustNot: List<QueryExpression> = emptyList(),
    val minimumShouldMatch: Any? = null,
) : BoolExpression {
    override val name = "bool"

    companion object {
        fun filter(vararg expressions: QueryExpression) = Bool(filter = expressions.toList())
        fun should(vararg expressions: QueryExpression) = Bool(should = expressions.toList())
        fun must(vararg expressions: QueryExpression) = Bool(must = expressions.toList())
        fun mustNot(vararg expressions: QueryExpression) = Bool(mustNot = expressions.toList())
    }

    override fun reduce(): QueryExpression? {
        val filter = filter.mapNotNull { it.reduce() }
        val should = should.mapNotNull { it.reduce() }
        val must = must.mapNotNull { it.reduce() }
        val mustNot = mustNot.mapNotNull { it.reduce() }
        return when {
            filter.isEmpty() && should.isEmpty() && must.isEmpty() && mustNot.isEmpty() -> {
                null
            }
            filter.isEmpty() && should.size == 1 && must.isEmpty() && mustNot.isEmpty() -> {
                should[0]
            }
            filter.isEmpty() && should.isEmpty() && must.size == 1 && mustNot.isEmpty() -> {
                must[0]
            }
            else -> {
                Bool(
                    filter = filter,
                    should = should,
                    must = must,
                    mustNot = mustNot,
                    minimumShouldMatch = minimumShouldMatch,
                )
            }
        }
    }

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.fieldIfNotNull("minimum_should_match", minimumShouldMatch)
        if (!filter.isNullOrEmpty()) {
            ctx.array("filter") {
                compiler.visit(this, filter)
            }
        }
        if (!should.isNullOrEmpty()) {
            ctx.array("should") {
                compiler.visit(this, should)
            }
        }
        if (!must.isNullOrEmpty()) {
            ctx.array("must") {
                compiler.visit(this, must)
            }
        }
        if (!mustNot.isNullOrEmpty()) {
            ctx.array("must_not") {
                compiler.visit(this, mustNot)
            }
        }
    }
}

class BoolNode(
    handle: NodeHandle<BoolNode>,
    filter: List<QueryExpression> = emptyList(),
    should: List<QueryExpression> = emptyList(),
    must: List<QueryExpression> = emptyList(),
    mustNot: List<QueryExpression> = emptyList(),
    private val minimumShouldMatch: Any? = null,
) : QueryExpressionNode<BoolNode>(handle), BoolExpression {
    override val name: String = "bool"

    override var filter: MutableList<QueryExpression> = filter.toMutableList()
    override var should: MutableList<QueryExpression> = should.toMutableList()
    override var must: MutableList<QueryExpression> = must.toMutableList()
    override var mustNot: MutableList<QueryExpression> = mustNot.toMutableList()

    override fun toQueryExpression(): Bool {
        return Bool(
            filter = filter,
            should = should,
            must = must,
            mustNot = mustNot,
            minimumShouldMatch = minimumShouldMatch,
        )
    }
}

interface DisMaxExpression : QueryExpression {
    val queries: List<QueryExpression>

    override fun children(): Iterator<Expression> = iterator {
        yieldAll(queries)
    }
}

class DisMax(
    override val queries: List<QueryExpression>,
    val tieBreaker: Double? = null,
) : DisMaxExpression {
    override val name = "dis_max"

    override fun reduce(): QueryExpression? {
        return when {
            queries.isEmpty() -> null
            queries.size == 1 -> queries[0]
            else -> this
        }
    }

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.array("queries") {
            compiler.visit(this, queries)
        }
        ctx.fieldIfNotNull("tie_breaker", tieBreaker)
    }
}

class DisMaxNode(
    handle: NodeHandle<DisMaxNode>,
    queries: List<QueryExpression> = emptyList(),
    var tieBreaker: Double? = null,
) : DisMaxExpression, QueryExpressionNode<DisMaxNode>(handle) {
    override val name = "dis_max"

    override var queries: MutableList<QueryExpression> = queries.toMutableList()

    override fun toQueryExpression(): DisMax {
        return DisMax(
            queries = queries,
            tieBreaker = tieBreaker
        )
    }
}

interface FunctionScoreExpression : QueryExpression {
    val functions: List<FunctionScore.Function>

    override fun children(): Iterator<Expression> = iterator {
        yieldAll(functions)
    }
}

class FunctionScore(
    val query: QueryExpression? = null,
    override val functions: List<Function>,
    val boost: Double? = null,
    val scoreMode: ScoreMode? = null,
    val boostMode: BoostMode? = null,
    val minScore: Double? = null,
) : FunctionScoreExpression {
    enum class ScoreMode : ToValue {
        MULTIPLY, SUM, AVG, FIRST, MAX, MIN;

        override fun toValue(): Any = name.toLowerCase()
    }
    enum class BoostMode : ToValue {
        MULTIPLY, REPLACE, SUM, AVG, MAX, MIN;

        override fun toValue(): Any = name.toLowerCase()
    }

    override val name = "function_score"

    abstract class Function : Expression {
        abstract val filter: QueryExpression?

        override fun children(): Iterator<Expression>? {
            val filter = filter
            if (filter != null) {
                return iterator { yield(filter) }
            }
            return null
        }

        fun reduceFilter(): QueryExpression? {
            return filter?.reduce()
        }

        protected inline fun accept(
            ctx: Serializer.ObjectCtx,
            compiler: SearchQueryCompiler,
            block: Serializer.ObjectCtx.() -> Unit
        ) {
            val fn = filter
            if (fn != null) {
                ctx.obj("filter") {
                    compiler.visit(this, fn)
                }
            }
            ctx.block()
        }
    }

    data class Weight(
        val weight: Double,
        override val filter: QueryExpression?,
    ) : Function() {
        override fun reduce(): Expression {
            return copy(
                filter = reduceFilter()
            )
        }

        override fun accept(
            ctx: Serializer.ObjectCtx,
            compiler: SearchQueryCompiler
        ) {
            super.accept(ctx, compiler) {
                field("weight", weight)
            }
        }
    }

    data class FieldValueFactor(
        val field: Named,
        val factor: Double? = null,
        val missing: Double? = null,
        val modifier: String? = null,
        override val filter: QueryExpression? = null,
    ) : Function() {
        override fun reduce(): Expression {
            return copy(
                filter = reduceFilter()
            )
        }

        override fun accept(
            ctx: Serializer.ObjectCtx, compiler:
            SearchQueryCompiler
        ) {
            super.accept(ctx, compiler) {
                ctx.obj("field_value_factor") {
                    field("field", field.getQualifiedFieldName())
                    fieldIfNotNull("factor", factor)
                    fieldIfNotNull("missing", missing)
                    fieldIfNotNull("modifier", modifier)
                }
            }
        }
    }

    class ScriptScore(
        val script: Script,
        override val filter: QueryExpression? = null,
    ) : Function() {
        override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
            ctx.obj("script_score") {
                compiler.visit(this, script)
            }
        }
    }

    override fun reduce(): QueryExpression? {
        val query = query?.reduce()
        if (functions.isEmpty() && minScore == null) {
            return query?.reduce()
        }
        return this
    }

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        if (query != null) {
            ctx.obj("query") {
                compiler.visit(this, query)
            }
        }
        ctx.fieldIfNotNull("boost", boost)
        ctx.fieldIfNotNull("score_mode", scoreMode?.toValue())
        ctx.fieldIfNotNull("boost_mode", boostMode?.toValue())
        ctx.array("functions") {
            compiler.visit(this, functions)
        }
    }
}

class FunctionScoreNode(
    handle: NodeHandle<FunctionScoreNode>,
    var query: QueryExpression?,
    var boost: Double? = null,
    var scoreMode: FunctionScore.ScoreMode? = null,
    var boostMode: FunctionScore.BoostMode? = null,
    var minScore: Double? = null,
    functions: List<FunctionScore.Function> = emptyList(),
) : QueryExpressionNode<FunctionScoreNode>(handle), FunctionScoreExpression {
    override val name: String = "function_score"

    override var functions: MutableList<FunctionScore.Function> = functions.toMutableList()

    override fun toQueryExpression(): QueryExpression {
        return FunctionScore(
            query = query,
            boost = boost,
            scoreMode = scoreMode,
            boostMode = boostMode,
            minScore = minScore,
            functions = functions,
        )
    }
}

// TODO: geo distance
class Sort(
    val by: By,
    val order: Order? = null,
    val mode: Mode? = null,
    val numericType: NumericType? = null,
    val missing: Missing? = null,
    val unmappedType: String? = null,
    val nested: Nested? = null,
) : Expression {
    constructor(
        field: Named? = null,
        scriptType: String? = null,
        script: Script? = null,
        order: Order? = null,
        mode: Mode? = null,
        numericType: NumericType? = null,
        missing: Missing? = null,
        unmappedType: String? = null,
        nested: Nested? = null,
    ) : this(
        by = By(field, scriptType, script),
        order = order,
        mode = mode,
        numericType = numericType,
        missing = missing,
        unmappedType = unmappedType,
        nested = nested,
    )

    sealed class By {
        class Field(val field: Named) : By()
        class Script(val type: String, val script: dev.evo.elasticmagic.Script) : By()

        companion object {
            internal operator fun invoke(
                field: Named?, scriptType: String?, script: dev.evo.elasticmagic.Script?
            ): By {
                return when {
                    field != null && script != null -> throw IllegalArgumentException(
                        "Only field or script are allowed, not both"
                    )
                    field == null && script == null -> throw IllegalArgumentException(
                        "One of field or script are required"
                    )
                    field != null -> Field(field)
                    script != null -> {
                        if (scriptType != null) {
                            Script(scriptType, script)
                        } else {
                            throw IllegalArgumentException(
                                "script requires scriptType"
                            )
                        }
                    }
                    else -> error("Unreachable")
                }
            }
        }
    }

    enum class Order : ToValue {
        ASC, DESC;

        override fun toValue(): Any {
            return name.toLowerCase()
        }
    }

    enum class Mode : ToValue {
        MIN, MAX, SUM, AVG, MEDIAN;

        override fun toValue(): Any {
            return name.toLowerCase()
        }
    }

    enum class NumericType : ToValue {
        DOUBLE, LONG, DATE, DATE_NANOS;

        override fun toValue(): Any {
            return name.toLowerCase()
        }
    }

    sealed class Missing : ToValue {
        object Last : Missing()
        object First : Missing()
        class Value(val value: Any) : Missing()

        override fun toValue(): Any {
            return when (this) {
                Last -> "_last"
                First -> "_first"
                is Value -> value
            }
        }
    }

    class Nested(
        val path: Named,
        val filter: QueryExpression? = null,
        val maxChildren: Int? = null,
        val nested: Nested? = null,
    ) : Expression {
        override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
            ctx.field("path", path.getQualifiedFieldName())
            if (filter != null) {
                ctx.obj("filter") {
                    compiler.visit(this, filter)
                }
            }
            ctx.fieldIfNotNull("max_children", maxChildren)
            if (nested != null) {
                ctx.obj("nested") {
                    compiler.visit(this, nested)
                }
            }
        }
    }

    internal fun simplifiedName(): String? {
        if (
            by is By.Field &&
            order == null &&
            mode == null &&
            numericType == null &&
            missing == null &&
            unmappedType == null
        ) {
            return by.field.getQualifiedFieldName()
        }
        return null
    }

    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        val params = Params(
            "order" to order,
            "mode" to mode,
            "numeric_type" to numericType,
            "missing" to missing,
            "unmapped_type" to unmappedType,
            "nested" to nested,
        )
        when (by) {
            is By.Field -> {
                ctx.obj(by.field.getQualifiedFieldName()) {
                    compiler.visit(this, params)
                }
            }
            is By.Script -> {
                ctx.obj("_script") {
                    field("type", by.type)
                    compiler.visit(this, by.script)
                    compiler.visit(this, params)
                }
            }
        }
    }
}

abstract class Rescore(
    val windowSize: Int? = null,
) : NamedExpression {
    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        super.accept(ctx, compiler)
        ctx.fieldIfNotNull("window_size", windowSize)
    }
}

class QueryRescore(
    val query: QueryExpression,
    val queryWeight: Double? = null,
    val rescoreQueryWeight: Double? = null,
    val scoreMode: ScoreMode? = null,
    windowSize: Int? = null,
) : Rescore(windowSize) {
    override val name = "query"

    enum class ScoreMode : ToValue {
        TOTAL, MULTIPLY, AVG, MAX, MIN;

        override fun toValue() = name.toLowerCase()
    }

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.obj("rescore_query") {
            compiler.visit(this, query)
        }
        ctx.fieldIfNotNull("query_weight", queryWeight)
        ctx.fieldIfNotNull("rescore_query_weight", rescoreQueryWeight)
        ctx.fieldIfNotNull("score_mode", scoreMode?.toValue())
    }
}
