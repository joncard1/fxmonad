package fxmonad

import scalafx.application.JFXApp3
import scalafx.application.Platform
import java.util.concurrent.CountDownLatch

// TODO: I'd prefer to switch to scalatest
class TextFieldControlProxySpec extends munit.FunSuite {

    override def beforeAll(): Unit = Platform.startup(() => {})
    override def afterAll(): Unit = Platform.exit()

    test("TextFieldControlProxy should do stuff") {
        val latch = new CountDownLatch(1)
        var testPassed = false

        Platform.runLater(() => {
            try {
                val expectedStyle = "expectedstyle"
                val proxy = TextFieldProxy()
                proxy.style() = expectedStyle
                assertEquals(proxy.changes.size, 1)
                assertEquals(proxy.changes.head.propertyName, "style")
                assertEquals(proxy.changes.head.newVal.asInstanceOf[String], expectedStyle)
                testPassed = true
            } finally {
                latch.countDown()
            }
        })

        latch.await()
        assert(testPassed)
    }
}
