import io.ktor.http.*
import io.ktor.samples.kweet.*
import io.ktor.samples.kweet.dao.*
import io.ktor.samples.kweet.model.*
import io.ktor.server.testing.*
import io.mockk.*
import org.joda.time.*
import org.junit.Test
import kotlin.test.*

class KweetApplicationWithTrackCookiesTest {
    val dao = mockk<DAOFacade>(relaxed = true)
    val date = DateTime.parse("2010-01-01T00:00+00:00")

    @Test
    fun testLoginSuccessWithTracker() = testApp {
        val password = "mylongpassword"
        val passwordHash = hash(password)
        every { dao.user("test1", passwordHash) } returns User("test1", "test1@test.com", "test1", passwordHash)

        cookiesSession {
            handleRequest(HttpMethod.Post, "/login") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(listOf("userId" to "test1", "password" to password).formUrlEncode())
            }.apply {
                assertEquals(302, response.status()?.value)
                assertEquals("http://localhost/user/test1", response.headers["Location"])
                assertEquals(null, response.content)
            }

            handleRequest(HttpMethod.Get, "/").apply {
                assertTrue { response.content!!.contains("sign out") }
            }
        }
    }

    private fun testApp(callback: TestApplicationEngine.() -> Unit) {
        withTestApplication({ mainWithDependencies(dao) }) { callback() }
    }
}

private class CookieTrackerTestApplicationEngine(
    val engine: TestApplicationEngine,
    var trackedCookies: List<Cookie> = listOf()
)

private fun CookieTrackerTestApplicationEngine.handleRequest(
    method: HttpMethod,
    uri: String,
    setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall {
    return engine.handleRequest(method, uri) {
        val cookieValue = trackedCookies.map { encodeURLQueryComponent(it.name) + "=" + encodeURLQueryComponent(it.value) }.joinToString("; ")
        addHeader("Cookie", cookieValue)
        setup()
    }.apply {
        trackedCookies = response.headers.values("Set-Cookie").map { parseServerSetCookieHeader(it) }
    }
}

private fun TestApplicationEngine.cookiesSession(
    initialCookies: List<Cookie> = listOf(),
    callback: CookieTrackerTestApplicationEngine.() -> Unit
) {
    callback(CookieTrackerTestApplicationEngine(this, initialCookies))
}
