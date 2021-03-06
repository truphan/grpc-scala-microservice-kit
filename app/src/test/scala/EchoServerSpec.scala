package mu.node.echod

import java.util.Calendar

import mu.node.echo.SendMessageRequest
import grpc.{AccessTokenCallCredentials, EchoClient}
import mu.node.echod.models.UserContext
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.duration._

class EchoServerSpec extends BaseSpec with BeforeAndAfterAll {

  val jwtSigningKey = loadPkcs8PrivateKey(
    pathForTestResourcePath(config.getString("jwt.signing-key")))
  val echoServiceStub = EchoClient.buildServiceStub(config, fileForTestResourcePath)

  override def beforeAll(): Unit = {
    echoServer.start()
  }

  override def afterAll(): Unit = {
    echoServer.shutdown()
  }

  "The echod gRPC server" when {

    "sent a valid, authenticated SendMessageRequest" should {
      "reply back with the Message" in {
        val userId = "8d5921be-8f85-11e6-ae22-56b6b6499611"
        val futureExpiry = Calendar.getInstance().getTimeInMillis + Duration(5, MINUTES).toMillis
        val jwt = UserContext(userId).toJwt(futureExpiry, jwtSigningKey)
        val sendMessage = echoServiceStub
          .withCallCredentials(new AccessTokenCallCredentials(jwt))
          .send(SendMessageRequest("hello"))
        whenReady(sendMessage) { reply =>
          reply.messageId.nonEmpty shouldBe true
          reply.senderId shouldEqual userId
          reply.content shouldEqual "hello"
        }
      }
    }

    "sent an unauthenticated SendMessageRequest" should {
      "return an exception indicating that the call was unauthenticated" in {
        val sendMessage = echoServiceStub.send(SendMessageRequest("test"))
        whenReady(sendMessage.failed) { ex =>
          ex shouldBe a[Exception]
          ex.getMessage shouldEqual "UNAUTHENTICATED"
        }
      }
    }

    "sent a SendMessageRequest with an expired access token" should {
      "reply back with the Message" in {
        val userId = "8d5921be-8f85-11e6-ae22-56b6b6499611"
        val lapsedExpiry = Calendar.getInstance().getTimeInMillis - Duration(5, MINUTES).toMillis
        val jwt = UserContext(userId).toJwt(lapsedExpiry, jwtSigningKey)
        val sendMessage = echoServiceStub
          .withCallCredentials(new AccessTokenCallCredentials(jwt))
          .send(SendMessageRequest("hello"))
        whenReady(sendMessage.failed) { ex =>
          ex shouldBe a[Exception]
          ex.getMessage shouldEqual "UNAUTHENTICATED"
        }
      }
    }

    "sent a SendMessageRequest with an invalid access token" should {
      "return an exception indicating that the call was unauthenticated" in {
        val sendMessage = echoServiceStub
          .withCallCredentials(new AccessTokenCallCredentials("bad jwt"))
          .send(SendMessageRequest("hello"))
        whenReady(sendMessage.failed) { ex =>
          ex shouldBe a[Exception]
          ex.getMessage shouldEqual "UNAUTHENTICATED"
        }
      }
    }
  }
}
