package dev.evo.elasticmagic

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Controls dynamic field mapping setting.
 * See: https://www.elastic.co/guide/en/elasticsearch/reference/current/dynamic-field-mapping.html
 */
enum class Dynamic : ToValue {
    TRUE, FALSE, STRICT, RUNTIME;

    override fun toValue(): String = name.toLowerCase()
}

open class BaseField : FieldOperations {
    private lateinit var name: String
    private lateinit var qualifiedName: String

    override fun getFieldName(): String = name

    override fun getQualifiedFieldName(): String {
        // Metadata fields are not bound
        return if (::qualifiedName.isInitialized) {
            qualifiedName
        } else {
            name
        }
    }

    @Suppress("FunctionName")
    internal open fun _setFieldName(fieldName: String) {
        if (::name.isInitialized) {
            throw IllegalStateException(
                "Field [$fieldName] has already been initialized as [$name]")
        }
        name = fieldName
    }

    @Suppress("FunctionName")
    internal open fun _bindToParent(parent: Named) {
        if (::qualifiedName.isInitialized) {
            throw IllegalStateException(
                "Field [${getFieldName()}] has already been bound as [${getQualifiedFieldName()}]"
            )
        }
        val parentQualifiedFieldName = parent.getQualifiedFieldName()
        qualifiedName = if (parentQualifiedFieldName.isNotEmpty()) {
            "${parentQualifiedFieldName}.${getFieldName()}"
        } else {
            getFieldName()
        }
    }
}

/**
 * Represents field in an Elasticsearch document.
 */
open class Field<out T, V>(
    internal val name: String? = null,
    val type: FieldType<T, V>,
    val params: Params,
) : BaseField() {
    open fun getFieldType(): FieldType<*, V> = type

    fun getMappingParams(): Params = params

    fun <F: SubFields<V>> subFields(factory: () -> F): SubFields.SubFieldsDelegate<T, V, F> {
        return SubFields.SubFieldsDelegate(this, factory)
    }
}

open class SimpleField<V>(
    name: String? = null,
    type: SimpleFieldType<V>,
    params: Params,
) : Field<Nothing, V>(name, type, params)

class JoinField(
    name: String? = null,
    type: JoinType,
    relations: Map<String, List<String>>,
    params: Params,
) : SimpleField<Join>(name, type, Params(params, "relations" to relations)) {

    inner class Parent(private val name: String) : FieldOperations {
        override fun getFieldName(): String {
            return name
        }

        override fun getQualifiedFieldName(): String {
            return "${this@JoinField.getQualifiedFieldName()}#$name"
        }
    }

    private val parentFields = relations.keys.associateWith { parentFieldName ->
        Parent(parentFieldName)
    }
    
    fun parent(name: String): FieldOperations {
        return parentFields[name]
            ?: throw IllegalArgumentException(
                "Unknown parent relation: $name, possible relations: ${parentFields.keys}"
            )
    }
}

/**
 * Base class for any types which hold set of fields:
 * https://www.elastic.co/guide/en/elasticsearch/reference/7.10/mapping.html
 */
abstract class FieldSet {
    @Suppress("PropertyName")
    internal val _fields: ArrayList<Field<*, *>> = ArrayList()

    val fields: Map<String, Field<*, *>> by lazy {
        _fields.associateBy(Field<*, *>::getFieldName)
    }

    fun <T> field(
        name: String?,
        type: SimpleFieldType<T>,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): SimpleField<T> {
        @Suppress("NAME_SHADOWING")
        val params = Params(
            params,
            "doc_values" to docValues,
            "index" to index,
            "store" to store,
        )
        return SimpleField(name, type, params)
    }
    fun <T> field(
        type: SimpleFieldType<T>,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): SimpleField<T> {
        return field(
            null, type,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun boolean(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): SimpleField<Boolean> {
        return field(
            name, BooleanType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun int(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): SimpleField<Int> {
        return field(
            name, IntType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun long(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): SimpleField<Long> {
        return field(
            name, LongType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun float(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): SimpleField<Float> {
        return field(
            name, FloatType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun double(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): SimpleField<Double> {
        return field(
            name, DoubleType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun keyword(
        name: String? = null,
        normalizer: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): SimpleField<String> {
        @Suppress("NAME_SHADOWING")
        val params = Params(
            params,
            "normalizer" to normalizer,
        )

        return field(
            name, KeywordType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun text(
        name: String? = null,
        index: Boolean? = null,
        indexOptions: String? = null,
        store: Boolean? = null,
        norms: Boolean? = null,
        boost: Double? = null,
        analyzer: String? = null,
        searchAnalyzer: String? = null,
        params: Params? = null,
    ): SimpleField<String> {
        @Suppress("NAME_SHADOWING")
        val params = Params(
            params,
            "index_options" to indexOptions,
            "norms" to norms,
            "boost" to boost,
            "analyzer" to analyzer,
            "search_analyzer" to searchAnalyzer,
        )
        return field(
            name, TextType,
            index = index,
            store = store,
            params = params,
        )
    }
    fun join(
        name: String? = null,
        relations: Map<String, List<String>>,
        eagerGlobalOrdinals: Boolean? = null,
    ): JoinField {
        val params = Params(
            "eager_global_ordinals" to eagerGlobalOrdinals,
        )
        // TODO: relation sub-fields
        return JoinField(name, JoinType, relations, params = params)
    }

    operator fun <T> SimpleField<T>.provideDelegate(
        thisRef: FieldSet, prop: KProperty<*>
    ): ReadOnlyProperty<FieldSet, SimpleField<T>> = FieldProperty(this, thisRef, prop)

    operator fun JoinField.provideDelegate(
        thisRef: FieldSet, prop: KProperty<*>
    ): ReadOnlyProperty<FieldSet, JoinField> = FieldProperty(this, thisRef, prop)

    class FieldProperty<F: SimpleField<T>, T>(
        private val field: F, fieldSet: FieldSet, prop: KProperty<*>
    ) : ReadOnlyProperty<FieldSet, F> {
        init {
            field._setFieldName(field.name ?: prop.name)
            fieldSet._fields.add(field)
        }

        override fun getValue(thisRef: FieldSet, property: KProperty<*>): F {
            return field
        }
    }
}

/**
 * Represents Elasticsearch multi-fields:
 * https://www.elastic.co/guide/en/elasticsearch/reference/7.10/multi-fields.html
 */
abstract class SubFields<V> : FieldSet(), FieldOperations {
    private lateinit var _field: FieldWrapper<V>

    fun getFieldType(): FieldType<*, V> = _field.getFieldType()

    override fun getFieldName(): String = _field.getFieldName()

    override fun getQualifiedFieldName(): String = _field.getQualifiedFieldName()

    private fun bindFields() {
        for (subField in _fields) {
            subField._bindToParent(this)
        }
    }

    class SubFieldsDelegate<out T, V, F: SubFields<V>>(
        val field: Field<T, V>,
        private val subFieldsFactory: () -> F,
    ) {
        operator fun provideDelegate(
            thisRef: BaseDocument, prop: KProperty<*>
        ): ReadOnlyProperty<BaseDocument, F> {
            val fieldName = field.name ?: prop.name
            val subFields = subFieldsFactory()
            if (subFields::_field.isInitialized) {
                throw IllegalStateException(
                    "[$fieldName] sub-fields has already been initialized as " +
                            "[${subFields._field.getFieldName()}] sub-fields"
                )
            }

            val subFieldsType = SubFieldsType(field.getFieldType())
            val field = FieldWrapper(fieldName, subFields, subFieldsType, field.params)
            subFields._field = field
            if (thisRef is Document) {
                field._bindToParent(object : Named {
                    override fun getFieldName(): String = ""
                    override fun getQualifiedFieldName(): String = ""
                })
            }

            thisRef._fields.add(field)

            return ReadOnlyProperty { _, _ -> subFields }
        }
    }

    internal class FieldWrapper<V>(
        name: String,
        val subFields: SubFields<*>,
        internal val subFieldsType: SubFieldsType<V>,
        params: Params,
    ) : SimpleField<V>(name, subFieldsType, params) {
        init {
            _setFieldName(name)
        }

        override fun getFieldType(): FieldType<*, V> {
            return subFieldsType.type
        }

        override fun _bindToParent(parent: Named) {
            super._bindToParent(parent)
            subFields.bindFields()
        }
    }
}

abstract class BaseDocument : FieldSet() {
    fun <T: SubDocument> `object`(
        name: String?,
        factory: () -> T,
        enabled: Boolean? = null,
        params: Params = Params(),
    ): SubDocument.SubDocumentProperty<T> {
        @Suppress("NAME_SHADOWING")
        val params = Params(
            params,
            "enabled" to enabled,
        )
        return SubDocument.SubDocumentProperty(name, ObjectType(), params, factory)
    }
    fun <T: SubDocument> `object`(
        factory: () -> T,
        enabled: Boolean? = null,
        params: Params = Params(),
    ): SubDocument.SubDocumentProperty<T> {
        return `object`(null, factory, enabled, params)
    }
    fun <T: SubDocument> obj(
        name: String?,
        factory: () -> T,
        enabled: Boolean? = null,
        params: Params = Params(),
    ): SubDocument.SubDocumentProperty<T> {
        return `object`(name, factory, enabled, params)
    }
    fun <T: SubDocument> obj(
        factory: () -> T,
        enabled: Boolean? = null,
        params: Params = Params(),
    ): SubDocument.SubDocumentProperty<T> {
        return `object`(factory, enabled, params)
    }

    fun <T: SubDocument> nested(
        name: String?, factory: () -> T, params: Params = Params()
    ): SubDocument.SubDocumentProperty<T> {
        return SubDocument.SubDocumentProperty(name, NestedType(), params, factory)
    }
    fun <T: SubDocument> nested(
        factory: () -> T, params: Params = Params()
    ): SubDocument.SubDocumentProperty<T> {
        return nested(null, factory, params)
    }
}

/**
 * Represents Elasticsearch sub-document.
 */
abstract class SubDocument : BaseDocument(), FieldOperations {
    private lateinit var _field: FieldWrapper

    fun getFieldType(): FieldType<*, BaseDocSource> = _field.type

    override fun getFieldName(): String = _field.getFieldName()

    override fun getQualifiedFieldName(): String = _field.getQualifiedFieldName()

    private fun bindFields() {
        for (subField in _fields) {
            subField._bindToParent(this)
        }
    }

    class SubDocumentProperty<T: SubDocument>(
        private val name: String?,
        private val type: ObjectType<T, BaseDocSource>,
        private val params: Params,
        private val subDocumentFactory: () -> T,
    ) {
        operator fun provideDelegate(
            thisRef: BaseDocument, prop: KProperty<*>
        ): ReadOnlyProperty<BaseDocument, T> {
            val fieldName = name ?: prop.name
            val subDocument = subDocumentFactory()
            if (subDocument::_field.isInitialized) {
                throw IllegalStateException(
                    "Field [$fieldName] has already been initialized as [${subDocument._field.getFieldName()}]"
                )
            }

            val field = FieldWrapper(fieldName, subDocument, type, params)
            subDocument._field = field
            if (thisRef is Document) {
                field._bindToParent(object : Named {
                    override fun getFieldName(): String = ""
                    override fun getQualifiedFieldName(): String = ""
                })
            }

            thisRef._fields.add(field)

            return ReadOnlyProperty { _, _ -> subDocument }
        }
    }

    internal class FieldWrapper(
        name: String,
        val subDocument: SubDocument,
        type: ObjectType<*, BaseDocSource>,
        params: Params,
    ) : Field<SubDocument, BaseDocSource>(name, type, params) {
        init {
            _setFieldName(name)
        }

        override fun _bindToParent(parent: Named) {
            super._bindToParent(parent)
            subDocument.bindFields()
        }
    }
}

/**
 * Metadata fields:
 * https://www.elastic.co/guide/en/elasticsearch/reference/7.10/mapping-fields.html
 */
open class MetaFields : FieldSet() {
    val id by MetaField("_id", KeywordType)
    val type by MetaField("_type", KeywordType)
    val index by MetaField("_index", KeywordType)

    open val routing by RoutingField()

    open val fieldNames by FieldNamesField()
    val ignored by MetaField("_ignored", KeywordType)

    open val source by SourceField()
    open val size by SizeField()

    // TODO: Could we get rid of overriding provideDelegate operator?

    open class MetaField<V>(
        name: String, type: SimpleFieldType<V>, params: Params = Params()
    ) : SimpleField<V>(
        name, type, params
    ) {
        open operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, MetaField<V>> = FieldProperty(this, thisRef, prop)
    }

    class RoutingField(
        val required: Boolean? = null,
    ) : MetaField<String>("_routing", KeywordType, Params("required" to required)) {
        override operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, RoutingField> = FieldProperty(this, thisRef, prop)
    }

    class FieldNamesField(
        enabled: Boolean? = null,
    ) : MetaField<String>("_field_names", KeywordType, Params("enabled" to enabled)) {
        override operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, FieldNamesField> = FieldProperty(this, thisRef, prop)
    }

    // TODO: What type should the source field be?
    class SourceField(
        enabled: Boolean? = null,
        includes: List<String>? = null,
        excludes: List<String>? = null,
    ) : MetaField<String>(
        "_source",
        KeywordType,
        Params("enabled" to enabled, "includes" to includes, "excludes" to excludes)
    ) {
        override operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, SourceField> = FieldProperty(this, thisRef, prop)
    }

    class SizeField(
        enabled: Boolean? = null,
    ) : MetaField<Long>("_size", LongType, Params("enabled" to enabled)) {
        override operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, SizeField> = FieldProperty(this, thisRef, prop)
    }
}

/**
 * Base class for describing a top level Elasticsearch document.
 */
abstract class Document : BaseDocument() {
    open val meta = MetaFields()

    open val dynamic: Dynamic? = null
}

fun mergeDocuments(vararg docs: Document): Document {
    require(docs.isNotEmpty()) {
        "Nothing to merge, document list is empty"
    }

    if (docs.size == 1) {
        return docs[0]
    }

    val expectedMeta = docs[0].meta
    val expectedDocName = docs[0]::class.simpleName
    val expectedMetaFieldsByName = expectedMeta.fields
    for (doc in docs.slice(1 until docs.size)) {
        val metaFields = doc.meta.fields
        checkMetaFields(doc::class.simpleName, metaFields, expectedDocName, expectedMetaFieldsByName)
    }

    return object : Document() {
        override val meta = expectedMeta

        init {
            _fields.addAll(mergeFieldSets(docs.toList()))
        }
    }
}

private fun mergeFieldSets(fieldSets: List<FieldSet>): List<Field<*, *>> {
    val mergedFields = mutableListOf<Field<*, *>>()
    val mergedFieldsByName = mutableMapOf<String, Int>()
    for (fields in fieldSets) {
        for (field in fields._fields) {
            val fieldName = field.getFieldName()
            val mergedFieldIx = mergedFieldsByName[fieldName]
            if (mergedFieldIx == null) {
                mergedFieldsByName[fieldName] = mergedFields.size
                mergedFields.add(field)
                continue
            }
            val expectedField = mergedFields[mergedFieldIx]

            // Merge sub fields
            // One document can have sub fields but another does not
            val subFields = (field as? SubFields.FieldWrapper)?.subFields
            val expectedSubFields = (expectedField as? SubFields.FieldWrapper)?.subFields
            if (subFields != null || expectedSubFields != null) {
                checkFieldsIdentical(field, expectedField)

                val templateField = if (subFields != null) {
                    field
                } else {
                    expectedField
                }

                val mergedSubFields = object : SubFields<Any?>() {
                    init {
                        _fields.addAll(mergeFieldSets(listOfNotNull(expectedSubFields, subFields)))
                    }

                    override fun getFieldName(): String = expectedField.getFieldName()
                }
                mergedFields[mergedFieldIx] = SubFields.FieldWrapper(
                    templateField.getFieldName(),
                    mergedSubFields,
                    templateField.type as SubFieldsType<*>,
                    templateField.getMappingParams()
                )

                continue
            }

            // Merge sub documents
            val subDocument = (field as? SubDocument.FieldWrapper)?.subDocument
            if (subDocument != null) {
                checkFieldsIdentical(field, expectedField)

                val expectedSubDocument = (expectedField as? SubDocument.FieldWrapper)?.subDocument
                requireNotNull(expectedSubDocument) {
                    "$fieldName are differ by sub document presence"
                }
                val mergedSubDocument = object : SubDocument() {
                    init {
                        _fields.addAll(mergeFieldSets(listOf(expectedSubDocument, subDocument)))
                    }

                    override fun getFieldName(): String = expectedField.getFieldName()
                }
                mergedFields[mergedFieldIx] = SubDocument.FieldWrapper(
                    expectedField.getFieldName(),
                    mergedSubDocument,
                    expectedField.getFieldType() as ObjectType<*, BaseDocSource>,
                    expectedField.getMappingParams()
                )

                continue
            }

            checkFieldsIdentical(field, expectedField)
        }
    }

    return mergedFields
}

private fun checkMetaFields(
    docName: String?,
    metaFields: Map<String, Field<*, *>>,
    expectedDocName: String?,
    expectedMetaFields: Map<String, Field<*, *>>
) {
    for (expectedFieldName in expectedMetaFields.keys) {
        require(expectedFieldName in metaFields) {
            "$expectedDocName has meta field $expectedFieldName but $docName does not"
        }
    }
    for ((metaFieldName, metaField) in metaFields) {
        val expectedMetaField = expectedMetaFields[metaFieldName]
        requireNotNull(expectedMetaField) {
            "$docName has meta field $metaFieldName but $expectedDocName does not"
        }
        checkFieldsIdentical(metaField, expectedMetaField)
    }
}

private fun checkFieldsIdentical(
    field: Field<*, *>, expected: Field<*, *>,
) {
    val fieldName = field.getFieldName()
    val expectedName = expected.getFieldName()
    require(fieldName == expectedName) {
        "Different field names: $fieldName != $expectedName"
    }

    val fieldType = field.getFieldType()
    val expectedType = expected.getFieldType()
    require(fieldType::class == expectedType::class) {
        "$fieldName has different field types: $fieldType != $expectedType"
    }

    require(field.params == expected.params) {
        "${field.name} has different field params: ${field.params} != ${expected.params}"
    }
}