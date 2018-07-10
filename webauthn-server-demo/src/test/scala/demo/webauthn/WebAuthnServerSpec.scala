package demo.webauthn

import java.util
import java.security.KeyPair
import java.time.Instant
import java.util.Optional
import java.util.concurrent.TimeUnit

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.yubico.scala.util.JavaConverters._
import com.yubico.u2f.attestation.Attestation
import com.yubico.u2f.attestation.Transport
import com.yubico.u2f.data.messages.key.util.U2fB64Encoding
import com.yubico.webauthn.data.RegistrationResult
import com.yubico.webauthn.data.Basic
import com.yubico.webauthn.data.AuthenticationExtensionsClientInputs
import com.yubico.webauthn.data.ArrayBuffer
import com.yubico.webauthn.data.CollectedClientData
import com.yubico.webauthn.data.RelyingPartyIdentity
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor
import com.yubico.webauthn.data.AssertionRequest
import com.yubico.webauthn.data.PublicKeyCredentialRequestOptions
import com.yubico.webauthn.data.RegisteredCredential
import com.yubico.webauthn.rp.RegistrationTestData
import com.yubico.webauthn.test.TestAuthenticator
import com.yubico.webauthn.util.BinaryUtil
import com.yubico.webauthn.util.WebAuthnCodecs
import demo.webauthn.data.CredentialRegistration
import demo.webauthn.data.RegistrationRequest
import demo.webauthn.data.RegistrationResponse
import demo.webauthn.json.ScalaJackson
import org.junit.Assert.assertNotNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.when
import org.scalatest.FunSpec
import org.scalatest.Matchers

import scala.collection.JavaConverters._


class WebAuthnServerSpec extends FunSpec with Matchers {

  private val jsonMapper = new ScalaJackson().get
  private val username = "foo-user"
  private val displayName = "Foo User"
  private val credentialNickname = "My Lovely Credential"
  private val requireResidentKey = false
  private val requestId = "request1"
  private val rpId = RelyingPartyIdentity(id = "localhost", name = "Test party")
  private val origins = List("localhost").asJava

  describe("WebAuthnServer") {

    describe("registration") {

      it("has a start method whose output can be serialized to JSON.") {
        val server = newServer
        val request = server.startRegistration(username, displayName, credentialNickname, requireResidentKey)
        val json = jsonMapper.writeValueAsString(request.right.get)

        json should not be null
      }

      it("has a finish method which accepts and outputs JSON.") {
        val requestId = "request1"

        val server = newServerWithRegistrationRequest(RegistrationTestData.FidoU2f.BasicAttestation)

        val response = new RegistrationResponse(
          requestId,
          RegistrationTestData.FidoU2f.BasicAttestation.response
        )

        val authenticationAttestationResponseJson = """{"attestationObject":"v2hhdXRoRGF0YVikSZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2NBAAAFOQABAgMEBQYHCAkKCwwNDg8AIIjjhj6nH3qL2QF3tkUogilFykuaXjJTw35O4m-0NSX0pSJYIA5Nt8eYkLco-NQfKPXaA6dD9UfX_SHaYo-L-YQb78HsAyYBAiFYIOuzRl1o1Hem2jVRYhjkbSeIydhqLln9iltAgsDYjXRTIAFjZm10aGZpZG8tdTJmZ2F0dFN0bXS_Y3g1Y59ZAekwggHlMIIBjKADAgECAgIFOTAKBggqhkjOPQQDAjBqMSYwJAYDVQQDDB1ZdWJpY28gV2ViQXV0aG4gdW5pdCB0ZXN0cyBDQTEPMA0GA1UECgwGWXViaWNvMSIwIAYDVQQLDBlBdXRoZW50aWNhdG9yIEF0dGVzdGF0aW9uMQswCQYDVQQGEwJTRTAeFw0xODA5MDYxNzQyMDBaFw0xODA5MDYxNzQyMDBaMGcxIzAhBgNVBAMMGll1YmljbyBXZWJBdXRobiB1bml0IHRlc3RzMQ8wDQYDVQQKDAZZdWJpY28xIjAgBgNVBAsMGUF1dGhlbnRpY2F0b3IgQXR0ZXN0YXRpb24xCzAJBgNVBAYTAlNFMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEJ-8bFED9TnFhaArujgB0foNaV4gQIulP1mC5DO1wvSByw4eOyXujpPHkTw9y5e5J2J3N9coSReZJgBRpvFzYD6MlMCMwIQYLKwYBBAGC5RwBAQQEEgQQAAECAwQFBgcICQoLDA0ODzAKBggqhkjOPQQDAgNHADBEAiB4bL25EH06vPBOVnReObXrS910ARVOLJPPnKNoZbe64gIgX1Rg5oydH45zEMEVDjNPStwv6Z3nE_isMeY-szlQhv3_Y3NpZ1hHMEUCIQDBs1nbSuuKQ6yoHMQoRp8eCT_HZvR45F_aVP6qFX_wKgIgMCL58bv-crkLwTwiEL9ibCV4nDYM-DZuW5_BFCJbcxn__w","clientDataJSON":"eyJjaGFsbGVuZ2UiOiJBQUVCQWdNRkNBMFZJamRaRUdsNVlscyIsIm9yaWdpbiI6ImxvY2FsaG9zdCIsInR5cGUiOiJ3ZWJhdXRobi5jcmVhdGUiLCJ0b2tlbkJpbmRpbmciOnsic3RhdHVzIjoic3VwcG9ydGVkIn19"}"""
        val publicKeyCredentialJson = s"""{"id":"iOOGPqcfeovZAXe2RSiCKUXKS5peMlPDfk7ib7Q1JfQ","response":${authenticationAttestationResponseJson},"clientExtensionResults":{},"type":"public-key"}"""
        val responseJson = s"""{"requestId":"${requestId}","credential":${publicKeyCredentialJson}}"""

        val request = server.finishRegistration(responseJson)
        val json = jsonMapper.writeValueAsString(request.right.get)

        json should not be null
      }

    }

    describe("authentication") {

      // These values were generated using TestAuthenticator.makeCredentialExample(TestAuthenticator.createCredential())
      val authenticatorData: ArrayBuffer = BinaryUtil.fromHex("49960de5880e8c687434170f6476605b8fe4aeb9a28632c7995cf3ba831d97630100000539").get
      val clientDataJson: String = """{"challenge":"AAEBAgMFCA0VIjdZEGl5Yls","origin":"localhost","hashAlgorithm":"SHA-256","type":"webauthn.get","tokenBinding":{"status":"supported"}}"""
      val credentialId: ArrayBuffer = RegistrationTestData.FidoU2f.BasicAttestation.response.rawId
      val signature: ArrayBuffer = BinaryUtil.fromHex("30450221008d478e4c24894d261c7fd3790363ba9687facf4dd1d59610933a2c292cffc3d902205069264c167833d239d6af4c7bf7326c4883fb8c3517a2c86318aa3060d8b441").get

      // These values are defined by the attestationObject and clientDataJson above
      val clientData = CollectedClientData(WebAuthnCodecs.json.readTree(clientDataJson))
      val clientDataJsonBytes: ArrayBuffer = clientDataJson.getBytes("UTF-8").toVector
      val challenge: ArrayBuffer = U2fB64Encoding.decode(clientData.challenge).toVector
      val credentialKey: KeyPair = TestAuthenticator.importEcKeypair(
        privateBytes = BinaryUtil.fromHex("308193020100301306072a8648ce3d020106082a8648ce3d0301070479307702010104206a88f478910df685bc0cfcc2077e64fb3a8ba770fb23fbbcd1f6572ce35cf360a00a06082a8648ce3d030107a14403420004d8020a2ec718c2c595bb890fcdaf9b81cc742118efdbb8812ac4a9dd5ace2990ec22a48faf1544df0fe5fe0e2e7a69720e63a83d7f46aa022f1323eaf7967762").get,
        publicBytes = BinaryUtil.fromHex("3059301306072a8648ce3d020106082a8648ce3d03010703420004d8020a2ec718c2c595bb890fcdaf9b81cc742118efdbb8812ac4a9dd5ace2990ec22a48faf1544df0fe5fe0e2e7a69720e63a83d7f46aa022f1323eaf7967762").get
      )

      it("has a start method whose output can be serialized to JSON.") {
        val server = newServerWithUser(RegistrationTestData.FidoU2f.BasicAttestation)
        val request = server.startAuthentication(Optional.of(RegistrationTestData.FidoU2f.BasicAttestation.userId.name))
        val json = jsonMapper.writeValueAsString(request.right.get)

        json should not be null
      }

      it("has a finish method which accepts and outputs JSON.") {
        val server = newServerWithAuthenticationRequest(RegistrationTestData.FidoU2f.BasicAttestation)
        val authenticatorAssertionResponseJson = s"""{"authenticatorData":"${U2fB64Encoding.encode(authenticatorData.toArray)}","signature":"${U2fB64Encoding.encode(signature.toArray)}","clientDataJSON":"${U2fB64Encoding.encode(clientDataJsonBytes.toArray)}"}"""
        val publicKeyCredentialJson = s"""{"id":"${U2fB64Encoding.encode(credentialId.toArray)}","response":${authenticatorAssertionResponseJson}}"""
        val responseJson = s"""{"requestId":"${requestId}","credential":${publicKeyCredentialJson}}"""
        val request = server.finishAuthentication(responseJson)
        val json = jsonMapper.writeValueAsString(request.right.get)

        json should not be null
      }

      def newServerWithAuthenticationRequest(testData: RegistrationTestData) = {
        val assertionRequests: Cache[String, AssertionRequest] = newCache()

        assertionRequests.put(requestId, new AssertionRequest(
          requestId,
          Some(testData.userId.name).asJava,
          new PublicKeyCredentialRequestOptions(
            challenge = challenge,
            rpId = Some(rpId.id).asJava
          )
        ))

        val userStorage = makeUserStorage(testData)
        when(userStorage.getUserHandleForUsername(testData.userId.name)).thenReturn(Some(testData.userId.idBase64).asJava)
        when(userStorage.lookup(testData.response.id, testData.userId.idBase64)).thenReturn(Some(new RegisteredCredential(
          testData.response.rawId,
          credentialKey.getPublic,
          0,
          testData.userId.id
        )).asJava)

        new WebAuthnServer(userStorage, newCache(), assertionRequests, rpId, origins)
      }
    }

  }

  private def newServer = new WebAuthnServer

  private def newServerWithUser(testData: RegistrationTestData) = {
    val userStorage: RegistrationStorage = makeUserStorage(testData)

    new WebAuthnServer(userStorage, newCache(), newCache(), rpId, origins)
  }

  private def makeUserStorage(testData: RegistrationTestData) = {
    val userStorage = mock(classOf[RegistrationStorage])

    val registrations = util.Arrays.asList(CredentialRegistration.builder
      .signatureCount(testData.response.response.attestation.authenticatorData.signatureCounter)
      .username(testData.request.user.name)
      .userIdentity(testData.request.user)
      .credentialNickname(credentialNickname)
      .registrationTime(Instant.parse("2018-07-06T15:07:15Z"))
      .registration(new RegistrationResult(
        keyId = PublicKeyCredentialDescriptor(id = testData.response.rawId),
        attestationTrusted = false,
        attestationType = Basic,
        attestationMetadata = Some(new Attestation(
          "metadataIdentifier",
          Map("vendor" -> "properties").asJava,
          Map("device" -> "properties").asJava,
          Set(Transport.USB).asJava)
        ).asJava,
        publicKeyCose = testData.response.response.parsedAuthenticatorData.attestationData.get.credentialPublicKey.toArray,
        warnings = List.empty
      ))
      .build
    )

    when(userStorage.getRegistrationsByUsername(testData.userId.name)).thenReturn(registrations)

    userStorage
  }

  private def newServerWithRegistrationRequest(testData: RegistrationTestData) = {
    val registrationRequests: Cache[String, RegistrationRequest] = newCache()

    registrationRequests.put(requestId, new RegistrationRequest(
      testData.userId.name,
      credentialNickname,
      requestId,
      testData.request
    ))

    new WebAuthnServer(new InMemoryRegistrationStorage, registrationRequests, newCache(), rpId, origins)
  }

  private def newCache[K <: Object, V <: Object](): Cache[K, V] =
    CacheBuilder.newBuilder()
      .maximumSize(100)
      .expireAfterAccess(10, TimeUnit.MINUTES)
      .build()

}