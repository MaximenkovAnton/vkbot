package com.simarel.vkbot

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

@QuarkusTest
class HealthTest {

    @Test
    fun `health is up`() {
        given().get("/q/health").then().statusCode(200).body("status", equalTo("UP"))
    }

    @Test
    fun `readiness is up`() {
        given().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"))
    }

    @Test
    fun `liveness is up`() {
        given().get("/q/health/live").then().statusCode(200).body("status", equalTo("UP"))
    }
}
