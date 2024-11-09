package com.myapp.usecase.check

import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.restassured.RestAssured
import io.restassured.specification.RequestSpecification
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.junit.jupiter.Testcontainers

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import static com.myapp.usecase.check.CheckCarFeature.ACCIDENT_FREE
import static com.myapp.usecase.check.CheckCarFeature.MAINTENANCE
import static com.myapp.usecase.check.MaintenanceScore.POOR
import static io.micronaut.http.HttpStatus.*
import static io.micronaut.http.MediaType.APPLICATION_JSON
import static io.restassured.RestAssured.given
import static java.util.Arrays.asList
import static org.hamcrest.CoreMatchers.is
import static org.hamcrest.CoreMatchers.notNullValue

@Testcontainers(disabledWithoutDocker = true)
class CheckCarQueryControllerTest {

  @RegisterExtension
  static WireMockExtension wireMock = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock"))
      .build();

  static CHECK_PATH = "/check"

  Map<String, Object> getProperties() {
    Map.of(
        "micronaut.http.services.insurance.url", wireMock.baseUrl(),
        "micronaut.http.services.maintenance.url", wireMock.baseUrl()
    )
  }

  @Test
  void shouldCheckCarFeatures() {
    given:
    def server = ApplicationContext.run(EmbeddedServer.class, getProperties())
    RestAssured.port = server.getPort()

    def vinToCheck = "existing_vin"
    def request = createRequest(vinToCheck)

    when:
    def response = request.post(CHECK_PATH)

    then:
    response.then().
        statusCode(OK.code).
        body("vin", is(vinToCheck)).
        body("requestId", notNullValue()).
        body("maintenanceScore", is(POOR.name()))
  }

  @Test
  void shouldNotCheckCarFeaturesIfVinIsMissing() {
    given:
    def server = ApplicationContext.run(EmbeddedServer.class, getProperties())
    RestAssured.port = server.getPort()

    def request = createRequest("")

    when:
    def response = request.post(CHECK_PATH)

    then:
    response.then().statusCode(BAD_REQUEST.code);
  }

  @Test
  void shouldNotCheckCarFeaturesIfVinNotFound() {
    given:
    def server = ApplicationContext.run(EmbeddedServer.class, getProperties())
    RestAssured.port = server.getPort()

    def vinToCheck = "non_existing_vin"
    def request = createRequest(vinToCheck)

    when:
    def response = request.post(CHECK_PATH)

    then:
    response.then().statusCode(NOT_FOUND.code);
  }

  @Test
  void shouldNotCheckCarFeaturesIf3rdPartyServiceIsDown() {
    given:
    def server = ApplicationContext.run(EmbeddedServer.class, getProperties())
    RestAssured.port = server.getPort()

    def vinToCheck = "vin_error_500"
    def request = createRequest(vinToCheck)

    when:
    def response = request.post(CHECK_PATH)

    then:
    response.then().statusCode(SERVICE_UNAVAILABLE.code);
  }

  @Test
  void shouldNotCheckCarFeaturesIfFeaturesAreMissing() {
    given:
    def server = ApplicationContext.run(EmbeddedServer.class, getProperties())
    RestAssured.port = server.getPort()

    def vinToCheck = "existing_vin"
    def bodyRequest = new CheckCarRequest(
        vinToCheck, new HashSet<CheckCarFeature>()
    )

    def request = given()
        .header("Content-Type", APPLICATION_JSON).body(bodyRequest)

    when:
    def response = request.post(CHECK_PATH)

    then:
    response.then().statusCode(BAD_REQUEST.code);
  }

  private static RequestSpecification createRequest(String vinToCheck) {
    def bodyRequest = new CheckCarRequest(
        vinToCheck, new HashSet<CheckCarFeature>(asList(ACCIDENT_FREE, MAINTENANCE))
    )

    return given().header("Content-Type", APPLICATION_JSON)
        .body(bodyRequest)
  }
}
