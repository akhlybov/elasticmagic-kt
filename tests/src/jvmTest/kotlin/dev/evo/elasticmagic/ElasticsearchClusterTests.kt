package dev.evo.elasticmagic

import dev.evo.elasticmagic.json.compile.JsonCompilerProvider
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

import io.kotest.matchers.shouldBe

import io.ktor.client.engine.cio.CIO

import kotlinx.coroutines.runBlocking

import kotlin.test.Test

class ElasticsearchClusterTests {
    object FactorsDoc : Document() {
        val partition by int()
        val companyId by keyword("company_id")
        val clickPrice by float("click_price")
    }

    @Test
    fun test() = runBlocking {
        val transport = ElasticsearchKtorTransport(
            "http://es6-stg-prom-lb.prom.dev-cloud.evo.:9200",
            CIO.create {}
        )
        val cluster = ElasticsearchCluster(transport, JsonCompilerProvider)
        // val index = cluster["ua_trunk_catalog"]
        val index = cluster["adv_ua_weight_factors"]

        val query = SearchQuery {
            functionScore(
                query = null,
                functions = listOf(
                    fieldValueFactor(
                        FactorsDoc.clickPrice,
                        missing = 0.0
                    )
                )
            )
        }
            .filter(FactorsDoc.partition.eq(17))
            .filter(FactorsDoc.clickPrice.gt(2.2))
        println(JsonCompilerProvider.searchQuery.compile(query).body)

        val searchResult = index.search(query)
        println(searchResult)

        1 shouldBe 2
    }
}